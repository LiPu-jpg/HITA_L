package com.stupidtree.hitax.ui.snatch

import android.content.Context
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.stupidtree.hitax.data.repository.EASRepository

/**
 * WorkManager Worker that fires one selectCourse attempt.
 * Inputs:
 *   KEY_COURSE_ID  — the p_id value from queryKxrw
 *   KEY_POOL_CODE  — optional, defaults to "bx-b-b"
 *   KEY_YEAR_CODE  — term year code
 *   KEY_TERM_CODE  — term semester code
 *   KEY_YEAR_NAME  — display label (used to reconstruct TermItem)
 *   KEY_TERM_NAME  — display label
 */
class CourseSnatchWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        const val KEY_COURSE_ID = "course_id"
        const val KEY_POOL_CODE = "pool_code"
        const val KEY_YEAR_CODE = "year_code"
        const val KEY_TERM_CODE = "term_code"
        const val KEY_YEAR_NAME = "year_name"
        const val KEY_TERM_NAME = "term_name"
        const val KEY_RESULT = "result"
    }

    override fun doWork(): Result {
        val courseId = inputData.getString(KEY_COURSE_ID) ?: return Result.failure()
        val poolCode = inputData.getString(KEY_POOL_CODE) ?: "bx-b-b"
        val yearCode = inputData.getString(KEY_YEAR_CODE) ?: return Result.failure()
        val termCode = inputData.getString(KEY_TERM_CODE) ?: return Result.failure()
        val yearName = inputData.getString(KEY_YEAR_NAME) ?: ""
        val termName = inputData.getString(KEY_TERM_NAME) ?: ""

        val repo = EASRepository.getInstance(applicationContext as android.app.Application)
        val token = repo.getEasToken()
        if (!token.isLogin()) {
            return Result.failure(Data.Builder().putString(KEY_RESULT, "未登录教务系统").build())
        }

        val term = com.stupidtree.hitax.data.model.eas.TermItem(yearCode, yearName, termCode, termName)

        // Synchronous call via the EASource directly (Worker runs on background thread)
        val easSource = com.stupidtree.hitax.data.source.web.eas.EASource()
        val liveData = easSource.selectCourse(token, term, courseId, poolCode)

        // Block until the result is posted (LiveData posts on main thread, so we observe inline)
        var message = "请求超时"
        var success = false
        val latch = java.util.concurrent.CountDownLatch(1)
        val observer = androidx.lifecycle.Observer<com.stupidtree.component.data.DataState<String>> { state ->
            if (state != null) {
                success = state.state == com.stupidtree.component.data.DataState.STATE.SUCCESS
                val detail = (state.message ?: state.data ?: "").trim()
                message = if (detail.isNotBlank()) detail else if (success) "成功" else "失败"
                latch.countDown()
            }
        }
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.post { liveData.observeForever(observer) }
        val completed = latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
        mainHandler.post { liveData.removeObserver(observer) }
        if (!completed) {
            success = false
        }

        val output = androidx.work.Data.Builder()
            .putString(KEY_RESULT, message)
            .build()

        return if (success) Result.success(output) else Result.failure(output)
    }
}
