package com.stupidtree.hitax.ui.snatch

import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.android.material.snackbar.Snackbar
import com.stupidtree.component.data.DataState
import com.stupidtree.hitax.R
import com.stupidtree.hitax.databinding.ActivityCourseSnatchBinding
import com.stupidtree.hitax.ui.eas.EASActivity
import com.stupidtree.hitax.ui.widgets.PopUpCalendarPicker
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class CourseSnatchActivity : EASActivity<CourseSnatchViewModel, ActivityCourseSnatchBinding>() {

    private val workTag = "course_snatch"
    private val selectedTime = Calendar.getInstance()
    private val handledWorkIds = mutableSetOf<UUID>()

    override fun getViewModelClass(): Class<CourseSnatchViewModel> = CourseSnatchViewModel::class.java

    override fun initViewBinding(): ActivityCourseSnatchBinding =
        ActivityCourseSnatchBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setToolbarActionBack(binding.toolbar)
    }

    override fun refresh() {
        viewModel.loadTerms()
    }

    override fun initViews() {
        super.initViews()

        viewModel.termsLiveData.observe(this) { data ->
            if (data.state == DataState.STATE.SUCCESS) {
                val terms = data.data ?: return@observe
                val current = terms.firstOrNull { it.isCurrent } ?: terms.firstOrNull()
                viewModel.selectedTerm.value = current
                viewModel.loadAvailableCourses()
            }
        }

        binding.btnStart.setOnClickListener { startSnatching() }
        binding.scheduleTimeContainer.setOnClickListener { pickScheduleTime() }

        binding.btnStop.setOnClickListener {
            WorkManager.getInstance(this).cancelAllWorkByTag(workTag)
            binding.statusText.setText(R.string.snatch_status_idle)
            appendLog("已停止抢课任务")
        }

        updateScheduleLabel()
        observeWorkState()
    }

    private fun startSnatching() {
        val courseCode = binding.courseIdInput.text?.toString()?.trim().orEmpty()
        val courseId = resolveCourseId(courseCode)
        if (courseId.isNullOrBlank()) {
            Snackbar.make(binding.root, R.string.snatch_course_id_required, Snackbar.LENGTH_SHORT).show()
            return
        }
        if (selectedTime.timeInMillis <= System.currentTimeMillis()) {
            Snackbar.make(binding.root, R.string.snatch_schedule_invalid, Snackbar.LENGTH_SHORT).show()
            return
        }

        val token = viewModel.getEasToken()
        if (!token.isLogin()) {
            Snackbar.make(binding.root, R.string.not_log_in, Snackbar.LENGTH_SHORT).show()
            return
        }
        val selectedTerm = viewModel.selectedTerm.value
        if (selectedTerm == null) {
            Snackbar.make(binding.root, R.string.snatch_no_term, Snackbar.LENGTH_SHORT).show()
            return
        }

        val inputData = workDataOf(
            CourseSnatchWorker.KEY_COURSE_ID to courseId,
            CourseSnatchWorker.KEY_YEAR_CODE to selectedTerm.yearCode,
            CourseSnatchWorker.KEY_TERM_CODE to selectedTerm.termCode,
            CourseSnatchWorker.KEY_YEAR_NAME to selectedTerm.yearName,
            CourseSnatchWorker.KEY_TERM_NAME to selectedTerm.termName,
        )

        val now = System.currentTimeMillis()
        val requests = (0 until 5).map { index ->
            OneTimeWorkRequestBuilder<CourseSnatchWorker>()
                .setInputData(inputData)
                .setInitialDelay(selectedTime.timeInMillis - now + index * 10_000L, TimeUnit.MILLISECONDS)
                .addTag(workTag)
                .build()
        }

        WorkManager.getInstance(this).cancelAllWorkByTag(workTag)
        WorkManager.getInstance(this).enqueueUniqueWork(
            workTag,
            ExistingWorkPolicy.REPLACE,
            requests
        )

        handledWorkIds.clear()
        binding.statusText.setText(R.string.snatch_status_running)
        appendLog(
            String.format(
                Locale.getDefault(),
                "已设置定时发包，课程代码: %s，开始时间: %04d-%02d-%02d %02d:%02d，共 5 次",
                courseCode,
                selectedTime.get(Calendar.YEAR),
                selectedTime.get(Calendar.MONTH) + 1,
                selectedTime.get(Calendar.DAY_OF_MONTH),
                selectedTime.get(Calendar.HOUR_OF_DAY),
                selectedTime.get(Calendar.MINUTE),
            )
        )
    }

    private fun observeWorkState() {
        WorkManager.getInstance(this)
            .getWorkInfosByTagLiveData(workTag)
            .observe(this) { infos ->
                if (infos.isNullOrEmpty()) {
                    binding.statusText.setText(R.string.snatch_status_idle)
                    return@observe
                }
                if (infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }) {
                    binding.statusText.setText(R.string.snatch_status_running)
                }
                infos.forEach { info ->
                    if (!info.state.isFinished || !handledWorkIds.add(info.id)) {
                        return@forEach
                    }
                    when (info.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            val result = info.outputData.getString(CourseSnatchWorker.KEY_RESULT) ?: "成功"
                            appendLog("发包完成: $result")
                            binding.lastResultText.text = result
                        }
                        WorkInfo.State.FAILED -> {
                            val result = info.outputData.getString(CourseSnatchWorker.KEY_RESULT) ?: "失败"
                            appendLog("发包失败: $result")
                            binding.lastResultText.text = result
                        }
                        WorkInfo.State.CANCELLED -> appendLog("已取消一条抢课任务")
                        else -> Unit
                    }
                }
                if (infos.all { it.state.isFinished }) {
                    binding.statusText.setText(R.string.snatch_status_idle)
                }
            }
    }

    private fun pickScheduleTime() {
        PopUpCalendarPicker().setInitValue(selectedTime.timeInMillis)
            .setOnConfirmListener(object : PopUpCalendarPicker.OnConfirmListener {
                override fun onConfirm(c: Calendar) {
                    selectedTime.set(Calendar.YEAR, c.get(Calendar.YEAR))
                    selectedTime.set(Calendar.MONTH, c.get(Calendar.MONTH))
                    selectedTime.set(Calendar.DAY_OF_MONTH, c.get(Calendar.DAY_OF_MONTH))
                    TimePickerDialog(
                        this@CourseSnatchActivity,
                        { _, hour, minute ->
                            selectedTime.set(Calendar.HOUR_OF_DAY, hour)
                            selectedTime.set(Calendar.MINUTE, minute)
                            selectedTime.set(Calendar.SECOND, 0)
                            updateScheduleLabel()
                        },
                        selectedTime.get(Calendar.HOUR_OF_DAY),
                        selectedTime.get(Calendar.MINUTE),
                        DateFormat.is24HourFormat(this@CourseSnatchActivity),
                    ).show()
                }
            }).show(supportFragmentManager, "snatch_time")
    }

    private fun updateScheduleLabel() {
        binding.scheduleTimeValue.text = String.format(
            Locale.getDefault(),
            "%04d-%02d-%02d %02d:%02d",
            selectedTime.get(Calendar.YEAR),
            selectedTime.get(Calendar.MONTH) + 1,
            selectedTime.get(Calendar.DAY_OF_MONTH),
            selectedTime.get(Calendar.HOUR_OF_DAY),
            selectedTime.get(Calendar.MINUTE),
        )
    }

    private fun resolveCourseId(courseCode: String): String? {
        val term = viewModel.selectedTerm.value ?: return null
        val coursesState = viewModel.availableCoursesLiveData.value
        if (coursesState == null || coursesState.state != DataState.STATE.SUCCESS) {
            viewModel.loadAvailableCourses()
            Snackbar.make(binding.root, R.string.snatch_course_list_loading, Snackbar.LENGTH_SHORT).show()
            return null
        }
        val list = coursesState?.data ?: emptyList()
        val normalized = com.stupidtree.hitax.utils.CourseCodeUtils.normalize(courseCode) ?: courseCode
        val matched = list.firstOrNull {
            val code = it.code?.trim().orEmpty()
            code.equals(normalized, ignoreCase = true)
        }
        if (matched?.key.isNullOrBlank()) {
            Snackbar.make(binding.root, R.string.snatch_course_code_not_found, Snackbar.LENGTH_SHORT).show()
            return null
        }
        return matched?.key
    }

    private fun appendLog(msg: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val current = binding.logText.text?.toString() ?: ""
        val lines = current.lines().takeLast(49)  // keep last 50 lines
        binding.logText.text = (lines + listOf("[$ts] $msg")).joinToString("\n")
    }
}
