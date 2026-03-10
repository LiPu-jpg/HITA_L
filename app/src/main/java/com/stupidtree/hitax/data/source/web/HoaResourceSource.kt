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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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

    fun searchCourses(query: String): LiveData<DataState<List<CourseResourceItem>>> {
        val result = MutableLiveData<DataState<List<CourseResourceItem>>>()
        Thread {
            try {
                val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())
                val response = withHeaders(Jsoup.connect("$baseUrl/v1/courses/search?q=$encoded&limit=100"))
                    .method(Connection.Method.GET)
                    .execute()
                if (response.statusCode() >= 400) {
                    result.postValue(DataState(DataState.STATE.FETCH_FAILED, response.body()))
                    return@Thread
                }
                val arr = JSONArray(response.body())
                val items = mutableListOf<CourseResourceItem>()
                for (index in 0 until arr.length()) {
                    val obj = arr.optJSONObject(index) ?: continue
                    items.add(
                        CourseResourceItem(
                            repoName = obj.optString("repo_name"),
                            courseCode = obj.optString("course_code"),
                            courseName = obj.optString("course_name"),
                            repoType = obj.optString("repo_type", "normal"),
                            teachers = jsonArrayToTeacherList(obj.optJSONArray("teachers")),
                            aliases = jsonArrayToList(obj.optJSONArray("aliases")),
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
                val encoded = URLEncoder.encode(repoName, StandardCharsets.UTF_8.toString())
                val response = withHeaders(Jsoup.connect("$baseUrl/v1/courses/readme?repo_name=$encoded"))
                    .method(Connection.Method.GET)
                    .execute()
                if (response.statusCode() >= 400) {
                    result.postValue(DataState(DataState.STATE.FETCH_FAILED, response.body()))
                    return@Thread
                }
                val obj = JSONObject(response.body())
                result.postValue(
                    DataState(
                        CourseReadmeData(
                            source = obj.optString("source"),
                            markdown = obj.optString("readme_md"),
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
                val encoded = URLEncoder.encode(repoName, StandardCharsets.UTF_8.toString())
                val response = withHeaders(Jsoup.connect("$baseUrl/v1/courses/structure?repo_name=$encoded"))
                    .method(Connection.Method.GET)
                    .execute()
                if (response.statusCode() >= 400) {
                    result.postValue(DataState(DataState.STATE.FETCH_FAILED, response.body()))
                    return@Thread
                }
                val obj = JSONObject(response.body())
                val summary = obj.optJSONObject("summary") ?: JSONObject()
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
                val body = JSONObject()
                body.put("repo_name", repoName)
                body.put("course_code", courseCode)
                body.put("course_name", courseName)
                body.put("repo_type", repoType)
                body.put("ops", ops)
                val response = withHeaders(Jsoup.connect("$baseUrl/v1/courses/submit_ops"))
                    .header("Content-Type", "application/json")
                    .requestBody(body.toString())
                    .method(Connection.Method.POST)
                    .execute()
                val res = JSONObject(response.body())
                if (response.statusCode() >= 400) {
                    result.postValue(DataState(DataState.STATE.FETCH_FAILED, res.optString("detail", response.body())))
                    return@Thread
                }
                val prUrl = res.optString("pr_url")
                val status = res.optString("status")
                result.postValue(DataState(prUrl.ifBlank { status }, DataState.STATE.SUCCESS))
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
