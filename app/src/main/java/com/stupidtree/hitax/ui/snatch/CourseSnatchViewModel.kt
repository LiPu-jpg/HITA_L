package com.stupidtree.hitax.ui.snatch

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.stupidtree.component.data.DataState
import com.stupidtree.component.data.Trigger
import com.stupidtree.hitax.data.model.eas.EASToken
import com.stupidtree.hitax.data.model.eas.TermItem
import com.stupidtree.hitax.data.model.timetable.TermSubject
import com.stupidtree.hitax.data.repository.EASRepository
import com.stupidtree.hitax.ui.eas.EASViewModel
import com.stupidtree.hitax.utils.LiveDataUtils

class CourseSnatchViewModel(application: Application) : EASViewModel(application) {

    private val easRepository = EASRepository.getInstance(application)

    val selectedTerm = MutableLiveData<TermItem?>()

    private val termsController = MutableLiveData<Trigger>()
    val termsLiveData: LiveData<DataState<List<TermItem>>> = termsController.switchMap {
        if (it.isActioning) easRepository.getAllTerms()
        else LiveDataUtils.getMutableLiveData(DataState(DataState.STATE.NOTHING))
    }

    private val availableCoursesController = MutableLiveData<Trigger>()
    val availableCoursesLiveData: LiveData<DataState<List<TermSubject>>> = availableCoursesController.switchMap {
        val term = selectedTerm.value ?: return@switchMap LiveDataUtils.getMutableLiveData(
            DataState(DataState.STATE.NOTHING)
        )
        easRepository.getAvailableCourses(term)
    }

    fun getEasToken(): EASToken = easRepository.getEasToken()

    fun loadTerms() {
        termsController.value = Trigger.actioning
    }

    fun loadAvailableCourses() {
        availableCoursesController.value = Trigger.actioning
    }
}
