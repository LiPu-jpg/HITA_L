package com.stupidtree.hitax.data.model

import com.google.gson.annotations.SerializedName

data class GitHubRelease(
    @SerializedName("tag_name")
    val tagName: String? = null,
    val name: String? = null,
    val prerelease: Boolean? = null,
    val draft: Boolean? = null,
    @SerializedName("html_url")
    val htmlUrl: String? = null,
    val body: String? = null
)
