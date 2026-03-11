package com.stupidtree.hitax.data.source.web.service

import androidx.lifecycle.LiveData
import com.stupidtree.hitax.data.model.GitHubRelease
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface GitHubService {
    @GET("/repos/{owner}/{repo}/releases")
    fun listReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 20
    ): LiveData<List<GitHubRelease>?>
}
