package com.stupidtree.hitax.ui.resource

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.stupidtree.component.data.DataState
import com.stupidtree.hitax.data.model.resource.CourseResourceItem
import com.stupidtree.hitax.data.repository.HoaRepository

class CourseResourceSearchViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = HoaRepository.getInstance()
    private val queryLiveData = MutableLiveData<String>()

    val resultsLiveData: LiveData<DataState<List<CourseResourceItem>>> = queryLiveData.switchMap {
        repository.searchCourses(it)
    }

    fun search(query: String) {
        if (query.isBlank()) return
        queryLiveData.value = query.trim()
    }
}