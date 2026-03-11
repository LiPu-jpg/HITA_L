package com.stupidtree.hitax.utils

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.stupidtree.component.data.DataState
import com.stupidtree.hitax.data.model.resource.CourseResourceItem
import com.stupidtree.hitax.data.repository.HoaRepository

object CourseResourceLinker {
    fun openReadme(
        context: Context,
        owner: LifecycleOwner,
        courseCodeRaw: String?,
        courseNameRaw: String?,
    ) {
        val normalizedCode = CourseCodeUtils.normalize(courseCodeRaw)
            ?: CourseCodeUtils.normalize(courseNameRaw)
        val normalizedName = CourseNameUtils.normalize(courseNameRaw)

        val queries = mutableListOf<String>()
        if (!normalizedCode.isNullOrBlank()) queries.add(normalizedCode)
        if (!normalizedName.isNullOrBlank() && normalizedName != normalizedCode) queries.add(normalizedName)

        if (queries.isEmpty()) {
            openFallback(context, normalizedCode, normalizedName, courseCodeRaw, courseNameRaw)
            return
        }

        searchSequentially(
            context,
            owner,
            queries,
            0,
            normalizedCode,
            normalizedName,
            courseCodeRaw,
            courseNameRaw,
        )
    }

    private fun searchSequentially(
        context: Context,
        owner: LifecycleOwner,
        queries: List<String>,
        index: Int,
        normalizedCode: String?,
        normalizedName: String?,
        courseCodeRaw: String?,
        courseNameRaw: String?,
    ) {
        val query = queries.getOrNull(index)
        if (query.isNullOrBlank()) {
            openFallback(context, normalizedCode, normalizedName, courseCodeRaw, courseNameRaw)
            return
        }
        val liveData = HoaRepository.getInstance().searchCourses(query)
        val observer = object : Observer<DataState<List<CourseResourceItem>>> {
            override fun onChanged(state: DataState<List<CourseResourceItem>>) {
                liveData.removeObserver(this)
                if (state.state == DataState.STATE.SUCCESS) {
                    val match = selectBestMatch(state.data.orEmpty(), normalizedCode, normalizedName)
                    if (match != null) {
                        val displayName = match.courseName.ifBlank {
                            match.courseCode.ifBlank { normalizedName ?: courseNameRaw ?: match.repoName }
                        }
                        val displayCode = match.courseCode.ifBlank {
                            normalizedCode ?: courseCodeRaw ?: match.repoName
                        }
                        ActivityUtils.startCourseReadmeActivity(
                            context,
                            repoName = match.repoName,
                            courseName = displayName,
                            courseCode = displayCode,
                            repoType = match.repoType.ifBlank { "normal" },
                        )
                        return
                    }
                }
                if (index + 1 < queries.size) {
                    searchSequentially(
                        context,
                        owner,
                        queries,
                        index + 1,
                        normalizedCode,
                        normalizedName,
                        courseCodeRaw,
                        courseNameRaw,
                    )
                } else {
                    openFallback(context, normalizedCode, normalizedName, courseCodeRaw, courseNameRaw)
                }
            }
        }
        liveData.observe(owner, observer)
    }

    private fun selectBestMatch(
        items: List<CourseResourceItem>,
        normalizedCode: String?,
        normalizedName: String?,
    ): CourseResourceItem? {
        val code = normalizedCode?.trim()?.lowercase()
        val nameKey = CourseNameUtils.normalizeKey(normalizedName)
        fun nameMatches(raw: String?): Boolean {
            if (nameKey.isBlank()) return false
            val key = CourseNameUtils.normalizeKey(raw)
            if (key.isBlank()) return false
            return key == nameKey || key.contains(nameKey) || nameKey.contains(key)
        }
        if (!code.isNullOrBlank()) {
            items.firstOrNull { it.courseCode.equals(code, ignoreCase = true) }?.let { return it }
            items.firstOrNull { it.repoName.equals(code, ignoreCase = true) }?.let { return it }
        }
        if (nameKey.isNotBlank()) {
            items.firstOrNull { nameMatches(it.courseName) }?.let { return it }
            items.firstOrNull { it.aliases.any { alias -> nameMatches(alias) } }
                ?.let { return it }
        }
        return if (items.size == 1) items.first() else null
    }

    private fun openFallback(
        context: Context,
        normalizedCode: String?,
        normalizedName: String?,
        courseCodeRaw: String?,
        courseNameRaw: String?,
    ) {
        val displayCode = normalizedCode ?: courseCodeRaw ?: ""
        val displayName = normalizedName ?: courseNameRaw ?: displayCode
        val repoName = when {
            displayCode.isNotBlank() && displayName.isNotBlank() -> {
                if (displayName.equals(displayCode, ignoreCase = true)) {
                    displayCode
                } else {
                    "${displayCode}-${displayName}"
                }
            }
            displayCode.isNotBlank() -> displayCode
            else -> displayName.ifBlank { "new-course" }
        }
        ActivityUtils.startCourseReadmeActivity(
            context,
            repoName = repoName,
            courseName = displayName,
            courseCode = displayCode.ifBlank { repoName },
            repoType = "normal",
        )
    }
}
