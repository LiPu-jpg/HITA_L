package com.stupidtree.hitax.ui.about

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.stupidtree.component.data.DataState
import com.stupidtree.component.data.Trigger
import com.stupidtree.hitax.BuildConfig
import com.stupidtree.hitax.data.repository.EASRepository
import com.stupidtree.hitax.data.repository.StaticRepository
import com.stupidtree.hitax.data.repository.UpdateRepository
import com.stupidtree.hitax.utils.LiveDataUtils
import com.stupidtree.stupiduser.data.model.CheckUpdateResult
import com.stupidtree.stupiduser.data.repository.LocalUserRepository
import com.stupidtree.stupiduser.data.repository.ManagerRepository

class AboutViewModel(application: Application) : AndroidViewModel(application) {
    private val staticRepo = StaticRepository.getInstance(application)
    private val localUserRepository = LocalUserRepository.getInstance(application)
    private val managerRepository = ManagerRepository.getInstance(application)
    private val updateRepository = UpdateRepository.getInstance(application)

    private val refreshController = MutableLiveData<Trigger>()

    val aboutPageLiveData = refreshController.switchMap{
        return@switchMap staticRepo.getAboutPage()
    }

    private val checkUpdateTrigger = MutableLiveData<Long>()
    val checkUpdateResult = checkUpdateTrigger.switchMap{
        updateRepository.checkUpdateFromGitHub(
            currentVersionName = BuildConfig.VERSION_NAME,
            updateUrl = BuildConfig.UPDATE_URL,
            allowPrerelease = BuildConfig.UPDATE_ALLOW_PRERELEASE
        )?.let { github ->
            return@switchMap github
        }
        buildLocalUpdateResult(it)?.let { local ->
            return@switchMap LiveDataUtils.getMutableLiveData(local)
        }
        if (localUserRepository.getLoggedInUser().isValid()) {
            return@switchMap managerRepository.checkUpdate(
                localUserRepository.getLoggedInUser().token?:"",
                it,
                EASRepository.getInstance(application).getEasToken().stuId
            )
        } else {
            return@switchMap LiveDataUtils.getMutableLiveData(DataState(DataState.STATE.NOT_LOGGED_IN))
        }
    }


    fun refresh() {
        refreshController.value = Trigger.actioning
    }

    fun checkForUpdate(versionCode: Long) {
        checkUpdateTrigger.value = versionCode
    }

    private fun buildLocalUpdateResult(currentCode: Long): DataState<CheckUpdateResult>? {
        val url = BuildConfig.UPDATE_URL.trim()
        if (url.isBlank()) return null
        val result = CheckUpdateResult().apply {
            latestVersionCode = BuildConfig.UPDATE_VERSION_CODE
            latestVersionName = BuildConfig.UPDATE_VERSION_NAME
            latestUrl = url
            updateLog = BuildConfig.UPDATE_LOG
            shouldUpdate = latestVersionCode > currentCode
        }
        return DataState(result)
    }

}
