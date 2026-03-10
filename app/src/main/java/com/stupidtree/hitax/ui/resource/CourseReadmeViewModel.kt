package com.stupidtree.hitax.ui.resource

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.stupidtree.component.data.DataState
import com.stupidtree.hitax.data.model.resource.CourseReadmeData
import com.stupidtree.hitax.data.repository.HoaRepository

class CourseReadmeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = HoaRepository.getInstance()
    private val repoNameLiveData = MutableLiveData<String>()

    val readmeLiveData: LiveData<DataState<CourseReadmeData>> = repoNameLiveData.switchMap {
        repository.getCourseReadme(it)
    }

    fun load(repoName: String) {
        repoNameLiveData.value = repoName
    }
}