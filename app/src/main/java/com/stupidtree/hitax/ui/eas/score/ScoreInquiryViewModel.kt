package com.stupidtree.hitax.ui.eas.score

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import com.stupidtree.component.data.DataState
import com.stupidtree.component.data.MTransformations
import com.stupidtree.component.data.Trigger
import com.stupidtree.hitax.data.model.eas.CourseScoreItem
import com.stupidtree.hitax.data.model.eas.ScoreSummary
import com.stupidtree.hitax.data.model.eas.TermItem
import com.stupidtree.hitax.data.repository.EASRepository
import com.stupidtree.hitax.data.source.web.service.EASService
import com.stupidtree.hitax.ui.eas.EASViewModel

class ScoreInquiryViewModel(application: Application) : EASViewModel(application) {
    /**
     * 仓库区
     */
    private val easRepository = EASRepository.getInstance(application)

    /**
     * LiveData区
     */
    private val pageController = MutableLiveData<Trigger>()

    val termsLiveData : LiveData<DataState<List<TermItem>>> = pageController.switchMap {
        return@switchMap easRepository.getAllTerms().map { state ->
            val data = state.data
            if (state.state != DataState.STATE.SUCCESS || data.isNullOrEmpty()) {
                return@map state
            }
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            val minYear = currentYear - 4
            val filtered = data.filter { term ->
                val startYear = parseStartYear(term.yearCode)
                startYear == null || startYear >= minYear
            }
            val finalList = if (filtered.isNotEmpty()) filtered else data
            DataState(finalList, state.state)
        }
    }

    val selectedTermLiveData: MutableLiveData<TermItem> = MutableLiveData()
    val selectedTestTypeLiveData: MutableLiveData<EASService.TestType> = MutableLiveData()

    private val scoresWithSummaryLiveData =
        MTransformations.switchMap(selectedTermLiveData, selectedTestTypeLiveData) {
            return@switchMap easRepository.getPersonalScoresWithSummary(it.first, it.second)
        }

    val scoresLiveData: LiveData<DataState<List<CourseScoreItem>>> =
        scoresWithSummaryLiveData.map { state ->
            DataState(state.data?.items ?: emptyList(), state.state)
        }

    val scoreSummaryLiveData: LiveData<ScoreSummary?> =
        scoresWithSummaryLiveData.map { it.data?.summary }

    /**
     * 方法区
     */
    fun startRefresh() {
        pageController.value = Trigger.actioning
    }

    private fun parseStartYear(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val match = Regex("(\\d{4})").find(raw) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()
    }

}
