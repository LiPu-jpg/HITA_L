package com.stupidtree.hitax.data.source.web

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.stupidtree.component.data.DataState
import com.stupidtree.component.web.BaseWebSource
import com.stupidtree.hitax.data.model.GitHubRelease
import com.stupidtree.hitax.data.source.web.service.GitHubService

class GitHubWebSource(context: Context) : BaseWebSource<GitHubService>(
    context,
    baseUrl = "https://api.github.com/"
) {
    override fun getServiceClass(): Class<GitHubService> {
        return GitHubService::class.java
    }

    fun listReleases(owner: String, repo: String): LiveData<DataState<List<GitHubRelease>>> {
        return service.listReleases(owner, repo).map { input ->
            if (input != null) {
                return@map DataState(input)
            }
            DataState(DataState.STATE.FETCH_FAILED)
        }
    }

    companion object {
        private var instance: GitHubWebSource? = null

        fun getInstance(context: Context): GitHubWebSource {
            synchronized(GitHubWebSource::class.java) {
                if (instance == null) {
                    instance = GitHubWebSource(context.applicationContext)
                }
                return instance!!
            }
        }
    }
}
