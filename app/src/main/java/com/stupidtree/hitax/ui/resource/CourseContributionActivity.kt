package com.stupidtree.hitax.ui.resource

import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.view.isVisible
import com.google.android.material.snackbar.Snackbar
import com.stupidtree.component.data.DataState
import com.stupidtree.hitax.R
import com.stupidtree.hitax.data.model.resource.CourseStructureSummary
import com.stupidtree.hitax.data.model.resource.CourseSummary
import com.stupidtree.hitax.databinding.ActivityCourseContributionBinding
import com.stupidtree.hitax.ui.widgets.PopUpCalendarPicker
import com.stupidtree.style.base.BaseActivity
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

class CourseContributionActivity :
    BaseActivity<CourseContributionViewModel, ActivityCourseContributionBinding>() {

    private enum class ContributionMode {
        NORMAL_TEACHER_REVIEW,
        NORMAL_SECTION_APPEND,
        MULTI_COURSE_REVIEW,
        MULTI_TEACHER_REVIEW,
        MULTI_SECTION_APPEND,
    }

    private lateinit var repoName: String
    private lateinit var courseName: String
    private lateinit var courseCode: String
    private lateinit var repoType: String
    private var selectedMode: ContributionMode? = null
    private var selectedCourse: CourseSummary? = null
    private val selectedDate: Calendar = Calendar.getInstance()
    private var submitObserverBound = false

    override fun getViewModelClass(): Class<CourseContributionViewModel> =
        CourseContributionViewModel::class.java

    override fun initViewBinding(): ActivityCourseContributionBinding =
        ActivityCourseContributionBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setToolbarActionBack(binding.toolbar)
        applyStatusBarInsets()
    }

    override fun initViews() {
        repoName = intent.getStringExtra("repoName") ?: ""
        courseName = intent.getStringExtra("courseName") ?: repoName
        courseCode = intent.getStringExtra("courseCode") ?: repoName
        repoType = intent.getStringExtra("repoType") ?: "normal"
        binding.toolbar.title = getString(R.string.course_contribution_title)
        binding.repoName.text = "$courseCode · $courseName"

        binding.typeLayout.setOnClickListener { showModePicker() }
        binding.courseLayout.setOnClickListener { showCoursePicker() }
        binding.teacherLayout.setOnClickListener { showTeacherPicker() }
        binding.authorDateLayout.setOnClickListener { pickDateTime() }
        binding.submitButton.setOnClickListener { submit() }

        updateDateLabel()

        viewModel.structureLiveData.observe(this) { state ->
            binding.progress.isVisible = false
            if (state.state == DataState.STATE.SUCCESS) {
                val summary = state.data ?: return@observe
                repoType = summary.repoType.ifBlank { repoType }
                if (repoType == "multi-project" && selectedCourse == null) {
                    selectedCourse = summary.courses.firstOrNull()
                    binding.courseValue.text = selectedCourse?.name?.ifBlank { selectedCourse?.code } ?: ""
                }
                if (repoType != "multi-project" && binding.teacherInput.text.isNullOrBlank()) {
                    summary.teachers.firstOrNull()?.let { binding.teacherInput.setText(it) }
                }
                applyDefaultModeIfNeeded(summary)
            } else {
                Snackbar.make(binding.root, state.message ?: getString(R.string.course_resource_failed), Snackbar.LENGTH_LONG).show()
            }
        }

        if (!submitObserverBound) {
            submitObserverBound = true
            viewModel.submitLiveData.observe(this) { state ->
                binding.progress.isVisible = false
                if (state.state == DataState.STATE.SUCCESS) {
                    Snackbar.make(binding.root, getString(R.string.course_contribution_success, state.data ?: ""), Snackbar.LENGTH_LONG).show()
                } else {
                    Snackbar.make(binding.root, state.message ?: getString(R.string.course_resource_failed), Snackbar.LENGTH_LONG).show()
                }
            }
        }
        updateModeVisibility()
    }

    override fun onStart() {
        super.onStart()
        binding.progress.isVisible = true
        viewModel.load(repoName)
    }

    private fun showModePicker() {
        val labels = if (repoType == "multi-project") {
            listOf(
                getString(R.string.course_contribution_mode_course_review),
                getString(R.string.course_contribution_mode_multi_teacher_review),
                getString(R.string.course_contribution_mode_multi_section_append),
            )
        } else {
            listOf(
                getString(R.string.course_contribution_mode_teacher_review),
                getString(R.string.course_contribution_mode_section_append),
            )
        }
        val values = if (repoType == "multi-project") {
            listOf(
                ContributionMode.MULTI_COURSE_REVIEW,
                ContributionMode.MULTI_TEACHER_REVIEW,
                ContributionMode.MULTI_SECTION_APPEND,
            )
        } else {
            listOf(
                ContributionMode.NORMAL_TEACHER_REVIEW,
                ContributionMode.NORMAL_SECTION_APPEND,
            )
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setItems(labels.toTypedArray()) { _, which ->
                selectedMode = values[which]
                binding.typeValue.text = labels[which]
                updateModeVisibility()
            }
            .show()
    }

    private fun showCoursePicker() {
        val courses = viewModel.structureLiveData.value?.data?.courses ?: emptyList()
        if (courses.isEmpty()) return
        val labels = courses.map { it.name.ifBlank { it.code } }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setItems(labels.toTypedArray()) { _, which ->
                selectedCourse = courses[which]
                binding.courseValue.text = labels[which]
                binding.teacherValue.setText("")
                binding.sectionValue.setText("")
            }
            .show()
    }

    private fun showTeacherPicker() {
        val labels = when (selectedMode) {
            ContributionMode.MULTI_TEACHER_REVIEW -> selectedCourse?.teachers
            else -> emptyList()
        } ?: emptyList()
        if (labels.isEmpty()) return
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setItems(labels.toTypedArray()) { _, which -> binding.teacherValue.setText(labels[which]) }
            .show()
    }

    private fun updateModeVisibility() {
        val mode = selectedMode
        binding.courseLayout.isVisible = mode == ContributionMode.MULTI_COURSE_REVIEW ||
            mode == ContributionMode.MULTI_TEACHER_REVIEW ||
            mode == ContributionMode.MULTI_SECTION_APPEND
        binding.teacherLayout.isVisible = mode == ContributionMode.MULTI_TEACHER_REVIEW
        binding.teacherInputLayout.isVisible = mode == ContributionMode.NORMAL_TEACHER_REVIEW
        binding.sectionLayout.isVisible = mode == ContributionMode.NORMAL_SECTION_APPEND ||
            mode == ContributionMode.MULTI_SECTION_APPEND
        binding.topicLayout.isVisible = mode == ContributionMode.MULTI_COURSE_REVIEW
    }

    private fun pickDateTime() {
        PopUpCalendarPicker().setInitValue(selectedDate.timeInMillis)
            .setOnConfirmListener(object : PopUpCalendarPicker.OnConfirmListener {
                override fun onConfirm(c: Calendar) {
                    selectedDate.set(Calendar.YEAR, c.get(Calendar.YEAR))
                    selectedDate.set(Calendar.MONTH, c.get(Calendar.MONTH))
                    selectedDate.set(Calendar.DAY_OF_MONTH, c.get(Calendar.DAY_OF_MONTH))
                    TimePickerDialog(
                        this@CourseContributionActivity,
                        { _, hour, minute ->
                            selectedDate.set(Calendar.HOUR_OF_DAY, hour)
                            selectedDate.set(Calendar.MINUTE, minute)
                            updateDateLabel()
                        },
                        selectedDate.get(Calendar.HOUR_OF_DAY),
                        selectedDate.get(Calendar.MINUTE),
                        DateFormat.is24HourFormat(this@CourseContributionActivity),
                    ).show()
                }
            }).show(supportFragmentManager, "pick_date")
    }

    private fun updateDateLabel() {
        binding.authorDateValue.text = String.format(
            Locale.getDefault(),
            "%04d-%02d",
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH) + 1,
        )
    }

    private fun applyStatusBarInsets() {
        val target = binding.root
        val originalTop = target.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(target) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = originalTop + bars.top)
            insets
        }
    }

    private fun applyDefaultModeIfNeeded(summary: CourseStructureSummary) {
        if (selectedMode != null) {
            return
        }
        selectedMode = if (repoType == "multi-project") {
            ContributionMode.MULTI_COURSE_REVIEW
        } else {
            ContributionMode.NORMAL_SECTION_APPEND
        }
        binding.typeValue.text = getModeLabel(selectedMode!!)
        updateModeVisibility()
    }

    private fun getModeLabel(mode: ContributionMode): String {
        return when (mode) {
            ContributionMode.NORMAL_TEACHER_REVIEW -> getString(R.string.course_contribution_mode_teacher_review)
            ContributionMode.NORMAL_SECTION_APPEND -> getString(R.string.course_contribution_mode_section_append)
            ContributionMode.MULTI_COURSE_REVIEW -> getString(R.string.course_contribution_mode_course_review)
            ContributionMode.MULTI_TEACHER_REVIEW -> getString(R.string.course_contribution_mode_multi_teacher_review)
            ContributionMode.MULTI_SECTION_APPEND -> getString(R.string.course_contribution_mode_multi_section_append)
        }
    }

    private fun submit() {
        val mode = selectedMode ?: run {
            Snackbar.make(binding.root, R.string.course_contribution_pick_type, Snackbar.LENGTH_SHORT).show()
            return
        }
        val content = binding.contentInput.text?.toString()?.trim().orEmpty()
        val authorName = binding.authorNameInput.text?.toString()?.trim().orEmpty()
        val authorLink = binding.authorLinkInput.text?.toString()?.trim().orEmpty()
        val authorDate = binding.authorDateValue.text?.toString()?.trim().orEmpty()

        if (authorName.isBlank()) {
            Snackbar.make(binding.root, R.string.course_contribution_fill_author, Snackbar.LENGTH_SHORT).show()
            return
        }

        val author = JSONObject()
        author.put("name", authorName)
        author.put("link", authorLink)
        author.put("date", authorDate)

        val ops = JSONArray()
        when (mode) {
            ContributionMode.NORMAL_TEACHER_REVIEW -> {
                if (content.isBlank()) {
                    Snackbar.make(binding.root, R.string.course_contribution_fill_content, Snackbar.LENGTH_SHORT).show()
                    return
                }
                val teacher = binding.teacherInput.text?.toString()?.trim().orEmpty()
                if (teacher.isBlank()) {
                    Snackbar.make(binding.root, R.string.course_contribution_pick_teacher, Snackbar.LENGTH_SHORT).show()
                    return
                }
                ops.put(JSONObject().apply {
                    put("op", "add_lecturer_review")
                    put("lecturer_name", teacher)
                    put("content", content)
                    put("author", author)
                })
            }
            ContributionMode.NORMAL_SECTION_APPEND -> {
                val section = binding.sectionValue.text?.toString()?.trim().orEmpty()
                    .ifBlank {
                        viewModel.structureLiveData.value?.data?.appendTargets?.firstOrNull().orEmpty()
                    }
                if (section.isBlank()) {
                    Snackbar.make(binding.root, R.string.course_contribution_pick_section, Snackbar.LENGTH_SHORT).show()
                    return
                }
                if (content.isBlank()) {
                    Snackbar.make(binding.root, R.string.course_contribution_fill_content, Snackbar.LENGTH_SHORT).show()
                    return
                }
                ops.put(JSONObject().apply {
                    put("op", "append_section_item")
                    put("section", section)
                    put("item", JSONObject().apply {
                        put("content", content)
                        put("author", author)
                    })
                })
            }
            ContributionMode.MULTI_COURSE_REVIEW -> {
                val course = selectedCourse ?: run {
                    Snackbar.make(binding.root, R.string.course_contribution_pick_course, Snackbar.LENGTH_SHORT).show()
                    return
                }
                val topic = binding.topicInput.text?.toString()?.trim().orEmpty()
                if (content.isBlank()) {
                    Snackbar.make(binding.root, R.string.course_contribution_fill_content, Snackbar.LENGTH_SHORT).show()
                    return
                }
                ops.put(JSONObject().apply {
                    put("op", "append_course_review")
                    put("course_name", course.name)
                    put("topic", topic.ifBlank { getString(R.string.course_contribution_topic) })
                    put("content", content)
                    put("author", author)
                })
            }
            ContributionMode.MULTI_TEACHER_REVIEW -> {
                val course = selectedCourse ?: run {
                    Snackbar.make(binding.root, R.string.course_contribution_pick_course, Snackbar.LENGTH_SHORT).show()
                    return
                }
                val teacher = binding.teacherValue.text?.toString()?.trim().orEmpty()
                if (teacher.isBlank()) {
                    Snackbar.make(binding.root, R.string.course_contribution_pick_teacher, Snackbar.LENGTH_SHORT).show()
                    return
                }
                if (content.isBlank()) {
                    Snackbar.make(binding.root, R.string.course_contribution_fill_content, Snackbar.LENGTH_SHORT).show()
                    return
                }
                ops.put(JSONObject().apply {
                    put("op", "add_course_teacher_review")
                    put("course_name", course.name)
                    put("teacher_name", teacher)
                    put("content", content)
                    put("author", author)
                })
            }
            ContributionMode.MULTI_SECTION_APPEND -> {
                val course = selectedCourse ?: run {
                    Snackbar.make(binding.root, R.string.course_contribution_pick_course, Snackbar.LENGTH_SHORT).show()
                    return
                }
                val section = binding.sectionValue.text?.toString()?.trim().orEmpty()
                if (section.isBlank()) {
                    Snackbar.make(binding.root, R.string.course_contribution_pick_section, Snackbar.LENGTH_SHORT).show()
                    return
                }
                if (content.isBlank()) {
                    Snackbar.make(binding.root, R.string.course_contribution_fill_content, Snackbar.LENGTH_SHORT).show()
                    return
                }
                ops.put(JSONObject().apply {
                    put("op", "append_course_section_item")
                    put("course_name", course.name)
                    put("section_title", section)
                    put("item", JSONObject().apply {
                        put("content", content)
                        put("author", author)
                    })
                })
            }
        }

        binding.progress.isVisible = true
        viewModel.submit(repoName, courseCode, courseName, repoType, ops)
    }
}
