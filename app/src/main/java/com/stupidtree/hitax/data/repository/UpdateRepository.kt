package com.stupidtree.hitax.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.stupidtree.component.data.DataState
import com.stupidtree.hitax.data.model.GitHubRelease
import com.stupidtree.hitax.data.source.web.GitHubWebSource
import com.stupidtree.stupiduser.data.model.CheckUpdateResult
import java.util.Locale

class UpdateRepository private constructor(context: Context) {
    private val githubWebSource = GitHubWebSource.getInstance(context)

    fun checkUpdateFromGitHub(
        currentVersionName: String,
        updateUrl: String,
        allowPrerelease: Boolean
    ): LiveData<DataState<CheckUpdateResult>>? {
        val repo = parseGitHubRepo(updateUrl) ?: return null
        return githubWebSource.listReleases(repo.owner, repo.repo).map { state ->
            if (state.state != DataState.STATE.SUCCESS) {
                return@map DataState(state.state)
            }
            val releases = state.data ?: return@map DataState(DataState.STATE.FETCH_FAILED)
            val current = ParsedVersion.parse(currentVersionName)
                ?: return@map DataState(DataState.STATE.FETCH_FAILED)
            val best = selectBestRelease(releases, allowPrerelease)
                ?: return@map DataState(DataState.STATE.FETCH_FAILED)
            val latestVersion = ParsedVersion.parse(best.tagName ?: best.name)
                ?: return@map DataState(DataState.STATE.FETCH_FAILED)

            val updateLogText = buildString {
                if (best.prerelease == true) {
                    append("预发布版本\n")
                }
                if (!best.body.isNullOrBlank()) {
                    append(best.body.trim())
                }
            }.trim()

            val result = CheckUpdateResult().apply {
                latestVersionName = latestVersion.displayName()
                latestVersionCode = latestVersion.toVersionCode()
                latestUrl = best.htmlUrl ?: updateUrl
                updateLog = updateLogText
                shouldUpdate = latestVersion > current
            }
            DataState(result)
        }
    }

    private fun selectBestRelease(
        releases: List<GitHubRelease>,
        allowPrerelease: Boolean
    ): GitHubRelease? {
        val candidates = releases.filter { release ->
            release.draft != true && (allowPrerelease || release.prerelease != true)
        }
        var best: GitHubRelease? = null
        var bestVersion: ParsedVersion? = null
        for (release in candidates) {
            val version = ParsedVersion.parse(release.tagName ?: release.name) ?: continue
            if (bestVersion == null || version > bestVersion) {
                bestVersion = version
                best = release
            }
        }
        if (best == null || bestVersion == null) {
            return null
        }
        return best
    }

    private fun parseGitHubRepo(url: String): RepoInfo? {
        if (url.isBlank()) return null
        val match = Regex("github\\.com/([^/]+)/([^/]+)").find(url.lowercase(Locale.ROOT))
            ?: return null
        val owner = match.groupValues[1]
        var repo = match.groupValues[2]
        if (repo.endsWith(".git")) repo = repo.removeSuffix(".git")
        return RepoInfo(owner, repo)
    }

    private data class RepoInfo(val owner: String, val repo: String)

    private data class ParsedVersion(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val preLabel: String?,
        val preNum: Int?
    ) : Comparable<ParsedVersion> {
        private fun preLabelRank(label: String): Int {
            return when (label.lowercase(Locale.ROOT)) {
                "alpha", "a" -> 1
                "beta", "b" -> 2
                "rc" -> 3
                else -> 4
            }
        }

        override fun compareTo(other: ParsedVersion): Int {
            if (major != other.major) return major.compareTo(other.major)
            if (minor != other.minor) return minor.compareTo(other.minor)
            if (patch != other.patch) return patch.compareTo(other.patch)

            val thisPre = preLabel
            val otherPre = other.preLabel
            if (thisPre == null && otherPre == null) return 0
            if (thisPre == null) return 1
            if (otherPre == null) return -1

            val labelCmp = preLabelRank(thisPre).compareTo(preLabelRank(otherPre))
            if (labelCmp != 0) return labelCmp
            val nameCmp = thisPre.compareTo(otherPre)
            if (nameCmp != 0) return nameCmp

            val a = preNum ?: 0
            val b = other.preNum ?: 0
            return a.compareTo(b)
        }

        fun displayName(): String {
            val base = "$major.$minor.$patch"
            return if (preLabel == null) {
                base
            } else {
                if (preNum != null) "$base-$preLabel.$preNum" else "$base-$preLabel"
            }
        }

        fun toVersionCode(): Long {
            val base = major * 1_000_000 + minor * 1_000 + patch
            val suffix = if (preLabel == null) {
                999
            } else {
                val rank = preLabelRank(preLabel)
                val num = preNum?.coerceIn(0, 99) ?: 0
                rank * 100 + num
            }
            return base.toLong() * 1000 + suffix
        }

        companion object {
            private val coreRegex = Regex("(\\d+)\\.(\\d+)(?:\\.(\\d+))?")
            private val preRegex = Regex("-([0-9A-Za-z]+)(?:[\\.-]?(\\d+))?")

            fun parse(input: String?): ParsedVersion? {
                if (input.isNullOrBlank()) return null
                val core = coreRegex.find(input) ?: return null
                val major = core.groupValues[1].toIntOrNull() ?: return null
                val minor = core.groupValues[2].toIntOrNull() ?: return null
                val patch = core.groupValues[3].toIntOrNull() ?: 0
                val rest = input.substring(core.range.last + 1)
                val pre = preRegex.find(rest)
                val label = pre?.groupValues?.getOrNull(1)?.lowercase(Locale.ROOT)
                val num = pre?.groupValues?.getOrNull(2)?.toIntOrNull()
                return ParsedVersion(major, minor, patch, label, num)
            }
        }
    }

    companion object {
        private var instance: UpdateRepository? = null

        fun getInstance(context: Context): UpdateRepository {
            synchronized(UpdateRepository::class.java) {
                if (instance == null) {
                    instance = UpdateRepository(context.applicationContext)
                }
                return instance!!
            }
        }
    }
}
