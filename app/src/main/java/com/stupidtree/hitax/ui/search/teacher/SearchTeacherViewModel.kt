package com.stupidtree.hitax.ui.search.teacher

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.stupidtree.component.data.DataState
import com.stupidtree.hitax.data.repository.HoaRepository
import com.stupidtree.hitax.ui.search.BaseSearchResultViewModel
import com.stupidtree.hitax.ui.search.SearchTrigger

class SearchTeacherViewModel(application: Application) : BaseSearchResultViewModel<TeacherSearched>(
    application
) {
    private val hoaRepository = HoaRepository.getInstance()
    override fun doSearch(trigger: SearchTrigger): LiveData<DataState<List<TeacherSearched>>> {
        val query = trigger.text.trim()
        return hoaRepository.searchCourses(query).map { state ->
            if (state.state != DataState.STATE.SUCCESS) {
                return@map DataState<List<TeacherSearched>>(state.state, state.message)
            }
            val queryLower = query.lowercase()
            val mapped = mutableListOf<TeacherSearched>()
            val seen = hashSetOf<String>()
            for (course in state.data ?: emptyList()) {
                val matchedTeachers = course.teachers.filter {
                    it.isNotBlank() && it.lowercase().contains(queryLower)
                }
                for (teacher in matchedTeachers) {
                    val key = "${teacher.trim()}|${course.repoName.trim()}"
                    if (!seen.add(key)) {
                        continue
                    }
                    mapped.add(
                        TeacherSearched().apply {
                            name = teacher.trim()
                            repoName = course.repoName
                            repoType = course.repoType
                            courseName = course.courseName
                            courseCode = course.courseCode
                            department = listOf(course.courseName, course.courseCode)
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")
                        }
                    )
                }
            }
            DataState(mapped, DataState.STATE.SUCCESS)
        }
    }
}
