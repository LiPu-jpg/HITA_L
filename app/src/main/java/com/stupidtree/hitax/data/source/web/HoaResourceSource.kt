package com.stupidtree.hitax.data.source.web

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.stupidtree.component.data.DataState
import com.stupidtree.hitax.BuildConfig
import com.stupidtree.hitax.data.model.resource.CourseReadmeData
import com.stupidtree.hitax.data.model.resource.CourseResourceItem
import com.stupidtree.hitax.data.model.resource.CourseSectionSummary
import com.stupidtree.hitax.data.model.resource.CourseStructureSummary
import com.stupidtree.hitax.data.model.resource.CourseSummary
import com.stupidtree.hitax.data.model.resource.ValidateReadmeResult
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Connection
import org.jsoup.Jsoup
import java.util.UUID

object HoaResourceSource {
    private val baseUrl = BuildConfig.HOA_BASE_URL.removeSuffix("/")
    private val apiKey = BuildConfig.HOA_API_KEY
    private const val timeout = 15000

    private fun withHeaders(req: Connection): Connection {
        req.ignoreContentType(true)
            .ignoreHttpErrors(true)
            .timeout(timeout)
            .header("Accept", "application/json")
            .header("User-Agent", "HITA_L/1.2.1")
        if (apiKey.isNotBlank()) {
            req.header("X-Api-Key", apiKey)
        }
        return req
    }

    /**
     * Extract campus from repo_name prefix.
     * repo_name format: "campus/COURSE_CODE" or "campus/repo_name"
     * Campus values: shenzhen, harbin, weihai
     */
    private fun extractCampus(repoName: String): String {
        val parts = repoName.split("/")
        return if (parts.isNotEmpty()) {
            val campus = parts[0].lowercase()
            when (campus) {
                "shenzhen", "harbin", "weihai" -> campus
                else -> "shenzhen" // Default fallback
            }
        } else {
            "shenzhen" // Default fallback
        }
    }

    /**
     * Extract course_code from repo_name.
     * repo_name format: "campus/COURSE_CODE" or "campus/repo_name"
     */
    private fun extractCourseCode(repoName: String): String {
        val parts = repoName.split("/")
        return if (parts.size >= 2) {
            parts[1].uppercase()
        } else {
            repoName.uppercase()
        }
    }

    fun searchCourses(query: String): LiveData<DataState<List<CourseResourceItem>>> {
        val result = MutableLiveData<DataState<List<CourseResourceItem>>>()
        Thread {
            try {
                val requestBody = JSONObject()
                requestBody.put("keyword", query.trim())
                requestBody.put("campus", "") // Empty for all campuses

                val response = withHeaders(Jsoup.connect("$baseUrl/v1/courses:search"))
                    .header("Content-Type", "application/json")
                    .requestBody(requestBody.toString())
                    .method(Connection.Method.POST)
                    .execute()

                if (response.statusCode() >= 400) {
                    result.postValue(DataState(DataState.STATE.FETCH_FAILED, response.body()))
                    return@Thread
                }

                val resObj = JSONObject(response.body())
                if (!resObj.optBoolean("ok", false)) {
                    val error = resObj.optJSONObject("error")
                    result.postValue(DataState(DataState.STATE.FETCH_FAILED, error?.optString("message", "Unknown error")))
                    return@Thread
                }

                val data = resObj.optJSONObject("data")
                val resultsArr = data?.optJSONArray("results") ?: JSONArray()
                val items = mutableListOf<CourseResourceItem>()

                for (index in 0 until resultsArr.length()) {
                    val obj = resultsArr.optJSONObject(index) ?: continue
                    val org = obj.optString("org")
                    val repo = obj.optString("repo")
                    val repoName = if (org.isNotBlank() && repo.isNotBlank()) {
                        "$org/$repo"
                    } else {
                        repo
                    }

                    items.add(
                        CourseResourceItem(
                            repoName = repoName,
                            courseCode = obj.optString("code"),
                            courseName = obj.optString("name"),
                            repoType = "normal", // Default type, not provided in new API
                            teachers = emptyList(), // Not provided in new API
                            aliases = emptyList(), // Not provided in new API
                        )
                    )
                }
                result.postValue(DataState(items, DataState.STATE.SUCCESS))
            } catch (e: Exception) {
                result.postValue(DataState(DataState.STATE.FETCH_FAILED, e.message))
            }
        }.start()
        return result
    }

    fun getCourseReadme(repoName: String): LiveData<DataState<CourseReadmeData>> {
        val result = MutableLiveData<DataState<CourseReadmeData>>()
        Thread {
            try {
                val campus = extractCampus(repoName)
                val courseCode = extractCourseCode(repoName)

                val requestBody = JSONObject()
                val target = JSONObject()
                target.put("campus", campus)
                target.put("course_code", courseCode)
                requestBody.put("target", target)

                val response = withHeaders(Jsoup.connect("$baseUrl/v1/course:read"))
                    .header("Content-Type", "application/json")
                    .requestBody(requestBody.toString())
                    .method(Connection.Method.POST)
                    .execute()

                if (response.statusCode() >= 400) {
                    result.postValue(DataState(DataState.STATE.FETCH_FAILED, response.body()))
                    return@Thread
                }

                val resObj = JSONObject(response.body())
                if (!resObj.optBoolean("ok", false)) {
                    val error = resObj.optJSONObject("error")
                    result.postValue(DataState(DataState.STATE.FETCH_FAILED, error?.optString("message", "Unknown error")))
                    return@Thread
                }

                val data = resObj.optJSONObject("data")
                val resultData = data?.optJSONObject("result")

                result.postValue(
                    DataState(
                        CourseReadmeData(
                            source = resultData?.optString("readme_toml", "") ?: "",
                            markdown = resultData?.optString("readme_md", "") ?: "",
                        ),
                        DataState.STATE.SUCCESS,
                    )
                )
            } catch (e: Exception) {
                result.postValue(DataState(DataState.STATE.FETCH_FAILED, e.message))
            }
        }.start()
        return result
    }

    fun getCourseStructure(repoName: String): LiveData<DataState<CourseStructureSummary>> {
        val result = MutableLiveData<DataState<CourseStructureSummary>>()
        Thread {
            try {
                val campus = extractCampus(repoName)
                val courseCode = extractCourseCode(repoName)

                val requestBody = JSONObject()
                val target = JSONObject()
                target.put("campus", campus)
                target.put("course_code", courseCode)
                requestBody.put("target", target)
                requestBody.put("include_toml", false)

                val response = withHeaders(Jsoup.connect("$baseUrl/v1/course:read"))
                    .header("Content-Type", "application/json")
                    .requestBody(requestBody.toString())
                    .method(Connection.Method.POST)
                    .execute()

                if (response.statusCode() >= 400) {
                    result.postValue(DataState(DataState.STATE.FETCH_FAILED, response.body()))
                    return@Thread
                }

                val resObj = JSONObject(response.body())
                if (!resObj.optBoolean("ok", false)) {
                    val error = resObj.optJSONObject("error")
                    result.postValue(DataState(DataState.STATE.FETCH_FAILED, error?.optString("message", "Unknown error")))
                    return@Thread
                }

                val data = resObj.optJSONObject("data")
                val resultData = data?.optJSONObject("result") ?: JSONObject()
                val summary = resultData.optJSONObject("summary") ?: JSONObject()
                val meta = summary.optJSONObject("meta") ?: JSONObject()
                val sectionsObj = summary.optJSONObject("sections") ?: JSONObject()
                val normalSections = mutableListOf<CourseSectionSummary>()
                val appendTargets = linkedSetOf<String>()
                val sectionKeys = sectionsObj.keys()
                while (sectionKeys.hasNext()) {
                    val key = sectionKeys.next()
                    val sectionNode = sectionsObj.optJSONObject(key) ?: continue
                    val items = sectionNode.optJSONArray("items")
                    if (key == "sections") {
                        for (index in 0 until (items?.length() ?: 0)) {
                            val item = items?.optJSONObject(index) ?: continue
                            val label = item.optString("label")
                            val preview = item.optString("preview")
                            normalSections.add(
                                CourseSectionSummary(
                                    label = label,
                                    preview = preview,
                                )
                            )
                            if (label.isNotBlank()) {
                                appendTargets.add(label)
                            }
                        }
                    } else if ((items?.length() ?: 0) > 0) {
                        appendTargets.add(key)
                    }
                }
                if (appendTargets.isEmpty()) {
                    // Keep a safe fallback list for append-only posts on sparse templates.
                    appendTargets.addAll(
                        listOf("exam", "lab", "advice", "schedule", "course", "related_links", "misc", "online_resources")
                    )
                }

                val courses = mutableListOf<CourseSummary>()
                val coursesArr = summary.optJSONArray("courses")
                for (index in 0 until (coursesArr?.length() ?: 0)) {
                    val item = coursesArr?.optJSONObject(index) ?: continue
                    courses.add(
                        CourseSummary(
                            name = item.optString("name"),
                            code = item.optString("code"),
                            reviewTopics = jsonArrayToList(item.optJSONArray("review_topics")),
                            teachers = jsonArrayToTeacherList(item.optJSONArray("teachers")),
                            sections = jsonArrayToList(item.optJSONArray("sections")),
                        )
                    )
                }
                val teachers = jsonArrayToTeacherList(summary.optJSONArray("teachers"))
                val resolvedRepoType = meta.optString("repo_type").ifBlank {
                    if (courses.isNotEmpty()) "multi-project" else "normal"
                }

                result.postValue(
                    DataState(
                        CourseStructureSummary(
                            repoType = resolvedRepoType,
                            sections = normalSections,
                            courses = courses,
                            appendTargets = appendTargets.toList(),
                            teachers = teachers,
                        ),
                        DataState.STATE.SUCCESS,
                    )
                )
            } catch (e: Exception) {
                result.postValue(DataState(DataState.STATE.FETCH_FAILED, e.message))
            }
        }.start()
        return result
    }

    fun submitOps(
        repoName: String,
        courseCode: String,
        courseName: String,
        repoType: String,
        ops: JSONArray,
    ): LiveData<DataState<String>> {
        val result = MutableLiveData<DataState<String>>()
        Thread {
            try {
                val campus = extractCampus(repoName)
                val actualCourseCode = if (courseCode.isNotBlank()) courseCode else extractCourseCode(repoName)

                val requestBody = JSONObject()
                val target = JSONObject()
                target.put("campus", campus)
                target.put("course_code", actualCourseCode)
                if (courseName.isNotBlank()) {
                    target.put("course_name", courseName)
                }
                requestBody.put("target", target)
                requestBody.put("ops", ops)
                requestBody.put("idempotency_key", UUID.randomUUID().toString())

                val response = withHeaders(Jsoup.connect("$baseUrl/v1/course:submit"))
                    .header("Content-Type", "application/json")
                    .requestBody(requestBody.toString())
                    .method(Connection.Method.POST)
                    .execute()

                val resObj = JSONObject(response.body())
                if (response.statusCode() >= 400) {
                    val error = resObj.optJSONObject("error")
                    result.postValue(DataState(DataState.STATE.FETCH_FAILED, error?.optString("message", response.body())))
                    return@Thread
                }

                if (!resObj.optBoolean("ok", false)) {
                    val error = resObj.optJSONObject("error")
                    result.postValue(DataState(DataState.STATE.FETCH_FAILED, error?.optString("message", "Unknown error")))
                    return@Thread
                }

                val data = resObj.optJSONObject("data")
                val pr = data?.optJSONObject("pr")
                val prUrl = pr?.optString("url", "")

                result.postValue(DataState(prUrl ?: "", DataState.STATE.SUCCESS))
            } catch (e: Exception) {
                result.postValue(DataState(DataState.STATE.FETCH_FAILED, e.message))
            }
        }.start()
        return result
    }

    fun validateReadme(
        repoName: String,
        courseCode: String,
        courseName: String,
        repoType: String,
        readmeMd: String
    ): LiveData<DataState<ValidateReadmeResult>> {
        val result = MutableLiveData<DataState<ValidateReadmeResult>>()
        Thread {
            try {
                val body = JSONObject()
                body.put("repo_name", repoName)
                body.put("course_code", courseCode)
                body.put("course_name", courseName)
                body.put("repo_type", repoType)
                body.put("readme_md", readmeMd)
                val response = withHeaders(Jsoup.connect("$baseUrl/v1/courses/readme/validate"))
                    .header("Content-Type", "application/json")
                    .requestBody(body.toString())
                    .method(Connection.Method.POST)
                    .execute()
                val res = JSONObject(response.body())
                if (response.statusCode() >= 400) {
                    result.postValue(DataState(DataState.STATE.FETCH_FAILED, res.optString("detail", response.body())))
                    return@Thread
                }
                result.postValue(
                    DataState(
                        ValidateReadmeResult(
                            ok = res.optBoolean("ok", false),
                            toml = res.optString("toml"),
                            normalizedReadme = res.optString("normalized_readme_md")
                        ),
                        DataState.STATE.SUCCESS
                    )
                )
            } catch (e: Exception) {
                result.postValue(DataState(DataState.STATE.FETCH_FAILED, e.message))
            }
        }.start()
        return result
    }

    /**
     * Lookup PR state (new endpoint)
     * GET /v1/pr:lookup
     */
    fun lookupPr(org: String, repo: String, number: Int): LiveData<DataState<JSONObject>> {
        val result = MutableLiveData<DataState<JSONObject>>()
        Thread {
            try {
                val url = "$baseUrl/v1/pr:lookup?org=${java.net.URLEncoder.encode(org, "UTF-8")}&repo=${java.net.URLEncoder.encode(repo, "UTF-8")}&number=$number"
                val response = withHeaders(Jsoup.connect(url))
                    .method(Connection.Method.GET)
                    .execute()

                if (response.statusCode() >= 400) {
                    result.postValue(DataState(DataState.STATE.FETCH_FAILED, response.body()))
                    return@Thread
                }

                val resObj = JSONObject(response.body())
                if (!resObj.optBoolean("ok", false)) {
                    val error = resObj.optJSONObject("error")
                    result.postValue(DataState(DataState.STATE.FETCH_FAILED, error?.optString("message", "Unknown error")))
                    return@Thread
                }

                val data = resObj.optJSONObject("data")
                result.postValue(DataState(data, DataState.STATE.SUCCESS))
            } catch (e: Exception) {
                result.postValue(DataState(DataState.STATE.FETCH_FAILED, e.message))
            }
        }.start()
        return result
    }

    private fun jsonArrayToList(array: JSONArray?): List<String> {
        val result = mutableListOf<String>()
        for (index in 0 until (array?.length() ?: 0)) {
            val value = array?.opt(index)?.toString()?.trim().orEmpty()
            if (value.isNotBlank()) {
                result.add(value)
            }
        }
        return result
    }

    private fun jsonArrayToTeacherList(array: JSONArray?): List<String> {
        val raw = jsonArrayToList(array)
        val result = linkedSetOf<String>()
        for (item in raw) {
            splitTeacherNames(item).forEach { name ->
                if (name.isNotBlank()) result.add(name)
            }
        }
        return result.toList()
    }

    private fun splitTeacherNames(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(Regex("[,，、]"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}
