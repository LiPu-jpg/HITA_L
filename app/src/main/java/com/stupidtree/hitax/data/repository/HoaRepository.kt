package com.stupidtree.hitax.data.repository

import androidx.lifecycle.LiveData
import com.stupidtree.component.data.DataState
import com.stupidtree.hitax.data.model.resource.CourseReadmeData
import com.stupidtree.hitax.data.model.resource.CourseResourceItem
import com.stupidtree.hitax.data.model.resource.CourseStructureSummary
import com.stupidtree.hitax.data.model.resource.ValidateReadmeResult
import com.stupidtree.hitax.data.source.web.HoaResourceSource
import org.json.JSONArray

class HoaRepository private constructor() {
    fun searchCourses(query: String): LiveData<DataState<List<CourseResourceItem>>> {
        return HoaResourceSource.searchCourses(query)
    }

    fun getCourseReadme(repoName: String): LiveData<DataState<CourseReadmeData>> {
        return HoaResourceSource.getCourseReadme(repoName)
    }

    fun getCourseStructure(repoName: String): LiveData<DataState<CourseStructureSummary>> {
        return HoaResourceSource.getCourseStructure(repoName)
    }

    fun validateReadme(
        repoName: String,
        courseCode: String,
        courseName: String,
        repoType: String,
        readmeMd: String
    ): LiveData<DataState<ValidateReadmeResult>> {
        return HoaResourceSource.validateReadme(repoName, courseCode, courseName, repoType, readmeMd)
    }
    fun submitOps(
        repoName: String,
        courseCode: String,
        courseName: String,
        repoType: String,
        ops: JSONArray,
    ): LiveData<DataState<String>> {
        return HoaResourceSource.submitOps(repoName, courseCode, courseName, repoType, ops)
    }

    companion object {
        private var instance: HoaRepository? = null

        fun getInstance(): HoaRepository {
            if (instance == null) {
                instance = HoaRepository()
            }
            return instance!!
        }
    }
}