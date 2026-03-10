package com.stupidtree.hitax.ui.resource

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.stupidtree.component.data.DataState
import com.stupidtree.hitax.data.model.resource.CourseStructureSummary
import com.stupidtree.hitax.data.repository.HoaRepository
import org.json.JSONArray

class CourseContributionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = HoaRepository.getInstance()

    private val repoNameLiveData = MutableLiveData<String>()
    val structureLiveData: LiveData<DataState<CourseStructureSummary>> = repoNameLiveData.switchMap {
        repository.getCourseStructure(it)
    }

    private val submitRequestLiveData = MutableLiveData<SubmitRequest>()
    val submitLiveData: LiveData<DataState<String>> = submitRequestLiveData.switchMap {
        repository.submitOps(it.repoName, it.courseCode, it.courseName, it.repoType, it.ops)
    }

    fun load(repoName: String) {
        repoNameLiveData.value = repoName
    }

    fun submit(repoName: String, courseCode: String, courseName: String, repoType: String, ops: JSONArray) {
        submitRequestLiveData.value = SubmitRequest(repoName, courseCode, courseName, repoType, ops)
    }

    data class SubmitRequest(
        val repoName: String,
        val courseCode: String,
        val courseName: String,
        val repoType: String,
        val ops: JSONArray,
    )
}