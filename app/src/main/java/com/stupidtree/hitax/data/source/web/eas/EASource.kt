package com.stupidtree.hitax.data.source.web.eas

import android.annotation.SuppressLint
import android.util.Base64
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.stupidtree.component.data.DataState
import com.stupidtree.hitax.data.model.eas.CourseItem
import com.stupidtree.hitax.data.model.eas.CourseScoreItem
import com.stupidtree.hitax.data.model.eas.EASToken
import com.stupidtree.hitax.data.model.eas.ExamItem
import com.stupidtree.hitax.data.model.eas.ScoreQueryResult
import com.stupidtree.hitax.data.model.eas.ScoreSummary
import com.stupidtree.hitax.data.model.eas.TermItem
import com.stupidtree.hitax.data.model.timetable.TermSubject
import com.stupidtree.hitax.data.model.timetable.TimeInDay
import com.stupidtree.hitax.data.model.timetable.TimePeriodInDay
import com.stupidtree.hitax.data.source.web.service.EASService
import com.stupidtree.hitax.ui.eas.classroom.BuildingItem
import com.stupidtree.hitax.ui.eas.classroom.ClassroomItem
import com.stupidtree.hitax.utils.JsonUtils
import com.stupidtree.hitax.utils.CourseCodeUtils
import org.json.JSONObject
import org.jsoup.Connection
import org.jsoup.Jsoup
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.RSAPublicKeySpec
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.crypto.Cipher

/**
 * 教务系统 API 数据源 —— 新版接口 (mjw.hitsz.edu.cn/incoSpringBoot)
 *
 * 认证方式：
 *  - 登录三步之前用 Basic Auth = "Basic aW5jb246MTIzNDU="
 *  - 登录成功后所有请求用 Authorization: bearer <access_token>
 *  - route / JSESSIONID cookie 由 Jsoup session 维护；每次新建请求时手动注入已保存的 cookies
 */
class EASource internal constructor() : EASService {

    private val hostName = "https://mjw.hitsz.edu.cn/incoSpringBoot"
    private val basicAuth = "Basic aW5jb246MTIzNDU="
    private val timeout = 15000

    // ---------------------------------------------------------------- 公共头
    private fun baseHeaders(authorization: String, rolecode: String = "06"): Map<String, String> =
        mapOf(
            "authorization" to authorization,
            "rolecode" to rolecode,
            "_lang" to "cn",
            "Accept" to "*/*",
            "Connection" to "keep-alive",
            "User-Agent" to "Mozilla/5.0 (Linux; Android 15; V2183A Build/AP3A.240905.015.A2; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/144.0.7559.132 Mobile Safari/537.36 uni-app Html5Plus/1.0 (Immersed/38.0)"
        )

    /** Form POST（登录流程，session 级别，维护 cookies） */
    private fun formPost(
        session: Connection,
        path: String,
        authorization: String,
        rolecode: String = "06",
        data: Map<String, String> = emptyMap()
    ): Connection.Response {
        val req = session.newRequest("$hostName$path")
            .headers(baseHeaders(authorization, rolecode))
            .timeout(timeout)
            .ignoreContentType(true)
            .ignoreHttpErrors(true)
            .method(Connection.Method.POST)
        data.forEach { (k, v) -> req.data(k, v) }
        return req.execute()
    }

    /** JSON POST（登录后） */
    private fun jsonPost(
        token: EASToken,
        path: String,
        body: String,
        rolecode: String = "06"
    ): Connection.Response {
        return Jsoup.newSession()
            .url("$hostName$path")
            .headers(baseHeaders("bearer ${token.accessToken}", rolecode))
            .header("Content-Type", "application/json")
            .cookies(token.cookies)
            .requestBody(body)
            .timeout(timeout)
            .ignoreContentType(true)
            .ignoreHttpErrors(true)
            .method(Connection.Method.POST)
            .execute()
    }

    /** Form POST（登录后，带已存 cookies） */
    private fun authedFormPost(
        token: EASToken,
        path: String,
        data: Map<String, String> = emptyMap()
    ): Connection.Response {
        val req = Jsoup.newSession()
            .url("$hostName$path")
            .headers(baseHeaders("bearer ${token.accessToken}"))
            .cookies(token.cookies)
            .timeout(timeout)
            .ignoreContentType(true)
            .ignoreHttpErrors(true)
            .method(Connection.Method.POST)
        data.forEach { (k, v) -> req.data(k, v) }
        return req.execute()
    }

    private fun buildCookieMap(vararg responses: Connection.Response): HashMap<String, String> {
        val cookies = HashMap<String, String>()
        responses.forEach { resp ->
            resp.cookies().forEach { (k, v) -> cookies[k] = v }
        }
        return cookies
    }

    private fun buildTokenFromPayload(
        payload: JSONObject?,
        username: String,
        password: String,
        cookies: Map<String, String>
    ): EASToken? {
        val accessToken = payload?.optString("access_token")
        if (accessToken.isNullOrEmpty()) return null
        val token = EASToken()
        token.accessToken = accessToken
        token.refreshToken = payload.optString("refresh_token")
        token.username = username
        token.password = password
        token.cookies = HashMap(cookies)
        val dataObj = payload.optJSONObject("data")
        val infoObj = payload.optJSONObject("info")
        token.stutype =
            if (dataObj?.optString("pylx") == "1") EASToken.TYPE.UNDERGRAD else EASToken.TYPE.GRAD
        token.phone = dataObj?.optString("lxdh")
        token.name = dataObj?.optString("yhxm") ?: infoObj?.optString("xm")
        token.school = dataObj?.optString("bmmc")
        token.stuId = infoObj?.optString("yhdm")
        return token
    }

    private fun parseRsaPublicKeysFromRaskey(body: String): List<PublicKey> {
        val keys = mutableListOf<PublicKey>()
        val jo = JsonUtils.getJsonObject(body) ?: return keys
        val keyBase64 = jo.optString("CLIENT_RSA_EXPONENT")
        if (keyBase64.isNotBlank()) {
            try {
                val keyBytes = Base64.decode(keyBase64, Base64.DEFAULT)
                val keySpec = X509EncodedKeySpec(keyBytes)
                keys.add(KeyFactory.getInstance("RSA").generatePublic(keySpec))
            } catch (_: Exception) {
                // fall back to modulus/exponent parsing below
            }
        }
        val modulusRaw = jo.optString("CLIENT_RSA_MODULUS")
        val exponentRaw = jo.optString("CLIENT_RSA_EXPONENT")
        if (modulusRaw.isBlank() || exponentRaw.isBlank()) return keys
        val candidates = listOf(
            parseBigInteger(modulusRaw, preferHex = true) to parseBigInteger(exponentRaw, preferHex = true),
            parseBigInteger(modulusRaw, preferHex = false) to parseBigInteger(exponentRaw, preferHex = false)
        )
        for ((modulus, exponent) in candidates) {
            if (modulus == null || exponent == null) continue
            runCatching {
                val spec = RSAPublicKeySpec(modulus, exponent)
                keys.add(KeyFactory.getInstance("RSA").generatePublic(spec))
            }.getOrNull()
        }
        return keys
    }

    private fun parseBigInteger(raw: String, preferHex: Boolean): BigInteger? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("0x", ignoreCase = true)) {
            return runCatching { BigInteger(trimmed.substring(2), 16) }.getOrNull()
        }
        val isNumeric = trimmed.all { it.isDigit() }
        if (preferHex) {
            if (isNumeric && trimmed.length <= 5) {
                // Common exponent "10001" usually means 0x10001 (65537)
                return runCatching { BigInteger(trimmed, 16) }.getOrNull()
            }
            return runCatching { BigInteger(trimmed, 16) }.getOrNull()
        }
        return runCatching { BigInteger(trimmed, if (isNumeric) 10 else 16) }.getOrNull()
    }

    private fun encryptPasswordWithRsa(password: String, publicKey: PublicKey): String? {
        return runCatching {
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        }.getOrNull()
    }

    // ================================================================ 登录
    override fun login(
        username: String,
        password: String,
        code: String?
    ): LiveData<DataState<EASToken>> {
        val res = MutableLiveData<DataState<EASToken>>()
        Thread {
            try {
                val session = Jsoup.newSession()
                    .headers(
                        mapOf(
                            "_lang" to "cn",
                            "Accept" to "*/*",
                            "User-Agent" to "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/144.0 Mobile Safari/537.36 uni-app",
                            "Accept-Encoding" to "gzip",
                            "Connection" to "Keep-Alive"
                        )
                    )

                // 步骤1：获取 route cookie
                val rsaResp = formPost(session, "/component/queryApplicationSetting/rsa", basicAuth, rolecode = "01")
                // 步骤2：获取 RSA 参数（维持 session 一致）
                val raskeyResp = formPost(session, "/c_raskey", basicAuth, rolecode = "06")
                val rsaPublicKeys = parseRsaPublicKeysFromRaskey(raskeyResp.body())
                val rolecodes = listOf("06", "01")

                // 步骤3：LDAP 登录（明文优先，失败再尝试加密）
                var payload: JSONObject? = null
                var token: EASToken? = null
                for (role in rolecodes) {
                    val ldapResp = formPost(
                        session, "/authentication/ldap", basicAuth, rolecode = role,
                        data = mapOf("username" to username, "password" to password)
                    )
                    payload = JsonUtils.getJsonObject(ldapResp.body())
                    token = buildTokenFromPayload(
                        payload,
                        username,
                        password,
                        buildCookieMap(rsaResp, raskeyResp, ldapResp)
                    )
                    if (token != null) break
                }
                if (token == null && rsaPublicKeys.isNotEmpty()) {
                    loop@ for (role in rolecodes) {
                        for (publicKey in rsaPublicKeys) {
                            val encrypted = encryptPasswordWithRsa(password, publicKey) ?: continue
                            val ldapResp = formPost(
                                session, "/authentication/ldap", basicAuth, rolecode = role,
                                data = mapOf("username" to username, "password" to encrypted)
                            )
                            payload = JsonUtils.getJsonObject(ldapResp.body())
                            token = buildTokenFromPayload(
                                payload,
                                username,
                                password,
                                buildCookieMap(rsaResp, raskeyResp, ldapResp)
                            )
                            if (token != null) break@loop
                        }
                    }
                }
                if (token == null) {
                    res.postValue(
                        DataState(
                            DataState.STATE.FETCH_FAILED,
                            payload?.optString("msg") ?: "登录失败"
                        )
                    )
                    return@Thread
                }

                res.postValue(DataState(token, DataState.STATE.SUCCESS))
            } catch (e: Exception) {
                e.printStackTrace()
                res.postValue(DataState(DataState.STATE.FETCH_FAILED, e.message))
            }
        }.start()
        return res
    }

    // ================================================================ 检查登录状态
    override fun loginCheck(token: EASToken): LiveData<DataState<Pair<Boolean, EASToken>>> {
        val res = MutableLiveData<DataState<Pair<Boolean, EASToken>>>()
        Thread {
            try {
                val resp = authedFormPost(token, "/app/commapp/queryxnxqlist")
                val jo = JsonUtils.getJsonObject(resp.body())
                val valid = jo?.optInt("code", -1) == 200
                res.postValue(DataState(Pair(valid, token)))
            } catch (e: Exception) {
                res.postValue(DataState(DataState.STATE.FETCH_FAILED))
            }
        }.start()
        return res
    }

    // ================================================================ 学年学期列表
    override fun getAllTerms(token: EASToken): LiveData<DataState<List<TermItem>>> {
        val res = MutableLiveData<DataState<List<TermItem>>>()
        Thread {
            val terms = arrayListOf<TermItem>()
            try {
                val resp = authedFormPost(token, "/app/commapp/queryxnxqlist")
                val jo = JsonUtils.getJsonObject(resp.body())
                val content = jo?.optJSONArray("content")
                if (content == null) {
                    res.postValue(DataState(DataState.STATE.NOT_LOGGED_IN))
                    return@Thread
                }
                for (i in 0 until content.length()) {
                    val item = content.optJSONObject(i) ?: continue
                    val term = TermItem(
                        yearCode = item.optString("XN"),
                        yearName = item.optString("XN"),
                        termCode = item.optString("XQ"),
                        termName = item.optString("XNXQMC")
                    )
                    term.name = buildTermDisplayName(term.yearName, term.termName)
                    term.isCurrent = item.optString("SFDQXQ") == "1"
                    terms.add(term)
                }
                res.postValue(DataState(terms, DataState.STATE.SUCCESS))
            } catch (e: Exception) {
                e.printStackTrace()
                res.postValue(DataState(DataState.STATE.FETCH_FAILED))
            }
        }.start()
        return res
    }

    // ================================================================ 学期开始日期
    @SuppressLint("SimpleDateFormat")
    override fun getStartDate(token: EASToken, term: TermItem): LiveData<DataState<Calendar>> {
        val res = MutableLiveData<DataState<Calendar>>()
        Thread {
            try {
                val calendar = Calendar.getInstance()
                calendar.firstDayOfWeek = Calendar.MONDAY
                // 用第1周课表矩阵接口获取第1天日期
                val weekBody = """{"xn":"${term.yearCode}","xq":"${term.termCode}","zc":"1","type":"json"}"""
                val weekResp = jsonPost(token, "/app/Kbcx/query", weekBody)
                val weekJo = JsonUtils.getJsonObject(weekResp.body())
                val contentArr = weekJo?.optJSONArray("content")
                val df = SimpleDateFormat("yyyy-MM-dd")
                if (contentArr != null) {
                    for (i in 0 until contentArr.length()) {
                        val obj = contentArr.optJSONObject(i) ?: continue
                        val rqList = obj.optJSONArray("rqList") ?: continue
                        val firstDay = rqList.optJSONObject(0) ?: continue
                        val rq = firstDay.optString("RQ")
                        if (rq.isNotEmpty()) {
                            val parsed = df.parse(rq)
                            if (parsed != null) calendar.timeInMillis = parsed.time
                        }
                        break
                    }
                }
                res.postValue(DataState(calendar))
            } catch (e: Exception) {
                e.printStackTrace()
                res.postValue(DataState(DataState.STATE.FETCH_FAILED, e.message))
            }
        }.start()
        return res
    }

    // ================================================================ 已选课程
    override fun getSubjectsOfTerm(
        token: EASToken,
        term: TermItem
    ): LiveData<DataState<MutableList<TermSubject>>> {
        val res = MutableLiveData<DataState<MutableList<TermSubject>>>()
        Thread {
            val result: MutableList<TermSubject> = ArrayList()
            try {
                val pylxRaw = token.getStudentType()
                val pylxPad = pylxRaw.padStart(2, '0')
                val pylxCandidates = linkedSetOf(pylxRaw, pylxPad)
                val roleCandidates = listOf("01", "06")
                val xnxqCandidates = linkedSetOf(
                    term.getCode(),
                    "${term.yearCode}-${term.termCode}",
                    term.yearCode + term.termCode.padStart(2, '0'),
                    "${term.yearCode}-${term.termCode.padStart(2, '0')}"
                )
                val xkfsCandidates = listOf("yixuan", "")
                var yxkc: org.json.JSONArray? = null
                for (roleHeader in roleCandidates) {
                    for (pylx in pylxCandidates) {
                        for (xnxq in xnxqCandidates) {
                            for (xkfs in xkfsCandidates) {
                                val body =
                                    """{"RoleCode":"$roleHeader","p_pylx":"$pylx","p_xn":"${term.yearCode}","p_xq":"${term.termCode}","p_xnxq":"$xnxq","p_gjz":"","p_kc_gjz":"","p_xkfsdm":"$xkfs"}"""
                                val resp = jsonPost(
                                    token,
                                    "/app/Xsxk/queryYxkc?_lang=zh_CN",
                                    body,
                                    rolecode = roleHeader
                                )
                                val jo = JsonUtils.getJsonObject(resp.body())
                                val list = extractYxkcList(jo)
                                if (list != null && list.length() > 0) {
                                    yxkc = list
                                    break
                                }
                            }
                            if (yxkc != null) break
                        }
                        if (yxkc != null) break
                    }
                    if (yxkc != null) break
                }
                yxkc?.let {
                    for (i in 0 until it.length()) {
                        val subject = it.optJSONObject(i) ?: continue
                        val s = TermSubject()
                        val rawCode = subject.optString("kcdm")
                        s.code = CourseCodeUtils.normalize(rawCode) ?: rawCode
                        s.name = subject.optString("kcmc", "")
                        s.school = subject.optString("kkyxmc")
                        s.teacher = extractSelectedTeacher(subject)
                        s.credit = subject.optString("xf").toFloatOrNull() ?: 0f
                        s.key = subject.optString("id")
                        s.field = optStringFirst(subject, listOf("kclbmc", "KCLBMC"))
                        s.selectCategory = optStringFirst(
                            subject,
                            listOf("rwlxmc", "RWLXMC", "xkfsmc", "XKFSMC", "xklbmc", "XKLBMC")
                        )
                        s.nature = optStringFirst(subject, listOf("kcxzmc", "KCXZMC"))
                        when (subject.optString("kcxzmc")) {
                            "必修" -> s.type = TermSubject.TYPE.COM_A
                            "限选" -> s.type = TermSubject.TYPE.OPT_A
                            "任选" -> s.type = TermSubject.TYPE.OPT_B
                        }
                        val rwlxmc = subject.optString("rwlxmc", "")
                        if (rwlxmc.contains("MOOC", ignoreCase = true))
                            s.type = TermSubject.TYPE.MOOC
                        result.add(s)
                    }
                }
                res.postValue(DataState(result))
            } catch (e: Exception) {
                e.printStackTrace()
                res.postValue(DataState(DataState.STATE.FETCH_FAILED, e.message))
            }
        }.start()
        return res
    }

    // ================================================================ 周课表（使用 querykbrcbyday 按天查询，有完整节次信息）
    override fun getTimetableOfTerm(
        term: TermItem,
        token: EASToken
    ): LiveData<DataState<List<CourseItem>>> {
        val res = MutableLiveData<DataState<List<CourseItem>>>()
        Thread {
            val result: MutableList<CourseItem> = ArrayList()
            try {
                // 获取学期开始日期
                val startDateCal = getStartDateSync(token, term)
                val startDate = startDateCal.timeInMillis
                
                // 获取学期总周数（默认18周）
                val weekCount = 18
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                
                // 按天查询整个学期
                for (week in 1..weekCount) {
                    for (dow in 1..7) {
                        // 计算日期
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = startDate
                        cal.add(Calendar.WEEK_OF_YEAR, week - 1)
                        cal.add(Calendar.DAY_OF_WEEK, dow - cal.get(Calendar.DAY_OF_WEEK) + 1)
                        val dateStr = sdf.format(cal.time)
                        
                        // 调用按天查询接口（有完整节次信息）
                        val resp = authedFormPost(
                            token, "/app/kbrcbyapp/querykbrcbyday",
                            mapOf("nyr" to dateStr)
                        )
                        val jo = JsonUtils.getJsonObject(resp.body())
                        val contentArr = jo?.optJSONArray("content") ?: continue
                        
                        // 解析当天的课程
                        for (i in 0 until contentArr.length()) {
                            val item = contentArr.optJSONObject(i) ?: continue
                            val kbrcArr = item.optJSONArray("kbrc") ?: continue
                            val ksjc = item.optInt("KSJC", 0)  // 开始节次
                            val jsjc = item.optInt("JSJC", 0)  // 结束节次
                            
                            for (j in 0 until kbrcArr.length()) {
                                val kc = kbrcArr.optJSONObject(j) ?: continue
                                val name = kc.optString("KCMC", "").trim()
                                if (name.isEmpty()) continue
                                
                                val code = CourseCodeUtils.normalize(kc.optString("KCDM")) ?: ""
                                val classroom = kc.optString("CDMC", "")
                                
                                // 合并同课同时间
                                val existing = result.find {
                                    it.name == name && it.dow == dow && it.begin == ksjc
                                }
                                if (existing != null) {
                                    if (!existing.weeks.contains(week)) {
                                        existing.weeks.add(week)
                                    }
                                    if (existing.classroom.isNullOrBlank() && classroom.isNotBlank()) {
                                        existing.classroom = classroom
                                    }
                                } else if (dow > 0 && ksjc > 0) {
                                    val courseItem = CourseItem()
                                    courseItem.name = name
                                    courseItem.code = code
                                    courseItem.dow = dow
                                    courseItem.begin = ksjc
                                    courseItem.last = jsjc - ksjc + 1
                                    courseItem.classroom = classroom
                                    courseItem.weeks.add(week)
                                    result.add(courseItem)
                                }
                            }
                        }
                    }
                }
                
                // 对周次进行排序
                result.forEach { it.weeks.sort() }
                
                res.postValue(DataState(result, DataState.STATE.SUCCESS))
            } catch (e: Exception) {
                e.printStackTrace()
                res.postValue(DataState(DataState.STATE.FETCH_FAILED, e.message))
            }
        }.start()
        return res
    }
    
    /** 同步获取学期开始日期 */
    @SuppressLint("SimpleDateFormat")
    private fun getStartDateSync(token: EASToken, term: TermItem): Calendar {
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.MONDAY
        try {
            // 用第1周课表矩阵接口获取第1天日期
            val weekBody = """{"xn":"${term.yearCode}","xq":"${term.termCode}","zc":"1","type":"json"}"""
            val weekResp = jsonPost(token, "/app/Kbcx/query", weekBody)
            val weekJo = JsonUtils.getJsonObject(weekResp.body())
            val contentArr = weekJo?.optJSONArray("content")
            val df = SimpleDateFormat("yyyy-MM-dd")
            if (contentArr != null) {
                for (i in 0 until contentArr.length()) {
                    val obj = contentArr.optJSONObject(i) ?: continue
                    val rqList = obj.optJSONArray("rqList") ?: continue
                    val firstDay = rqList.optJSONObject(0) ?: continue
                    val rq = firstDay.optString("RQ")
                    if (rq.isNotEmpty()) {
                        val parsed = df.parse(rq)
                        if (parsed != null) calendar.timeInMillis = parsed.time
                    }
                    break
                }
            }
        } catch (_: Exception) { }
        return calendar
    }

    private fun extractClassroom(kbxx: String): String? {
        val tokens = Regex("\\[([^\\]]+)]")
            .findAll(kbxx)
            .mapNotNull { it.groupValues.getOrNull(1)?.trim() }
            .filter { it.isNotBlank() }
            .toList()
        // 教务返回通常把地点放在最后一个方括号里
        return tokens.lastOrNull()
    }

    private fun extractTeacher(kc: org.json.JSONObject, courseName: String?, kbxx: String): String? {
        val keys = listOf("SKJS", "JSXM", "RKJS", "JS", "JSMC", "JSMC1", "JSMC2")
        for (key in keys) {
            val v = kc.optString(key, "").trim()
            if (v.isNotEmpty()) return v
        }
        val lines = kbxx.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.contains("[") && it.contains("]") }
        val cleaned = if (courseName.isNullOrBlank()) {
            lines
        } else {
            lines.filterNot { it.replace(" ", "") == courseName.replace(" ", "") }
        }
        if (cleaned.isNotEmpty()) {
            val raw = cleaned.joinToString(" / ")
            return raw.replace(Regex("^(教师|老师|Teacher)[:： ]*"), "").trim()
        }
        return null
    }

    private fun extractSelectedTeacher(subject: org.json.JSONObject): String? {
        val keys = listOf(
            "DGJSMC", "dgjsmc",
            "SKJS", "SKJSXM", "SKJSMC", "SKJSXX",
            "JSXM", "JSMC", "RKJS", "JS",
            "skjs", "skjsxm", "skjsmc", "skjsxx",
            "jsxm", "jsmc", "rkjs", "js"
        )
        for (key in keys) {
            val v = subject.optString(key, "").trim()
            if (v.isNotEmpty()) return v
        }
        return null
    }

    private fun optStringFirst(subject: org.json.JSONObject, keys: List<String>): String {
        for (key in keys) {
            val v = subject.optString(key, "").trim()
            if (v.isNotEmpty()) return v
        }
        return ""
    }

    private fun extractYxkcList(jo: JSONObject?): org.json.JSONArray? {
        if (jo == null) return null
        val keys = listOf("yxkcList", "content", "data", "rows", "list")
        for (key in keys) {
            val arr = jo.optJSONArray(key)
            if (arr != null && arr.length() > 0) return arr
        }
        val dataObj = jo.optJSONObject("data")
        if (dataObj != null) {
            for (key in keys) {
                val arr = dataObj.optJSONArray(key)
                if (arr != null && arr.length() > 0) return arr
            }
        }
        val contentObj = jo.optJSONObject("content")
        if (contentObj != null) {
            val it = contentObj.keys()
            while (it.hasNext()) {
                val key = it.next()
                val arr = contentObj.optJSONArray(key) ?: continue
                if (arr.length() == 0) continue
                if (key.contains("yxkc", ignoreCase = true)) {
                    return arr
                }
                val first = arr.optJSONObject(0)
                if (first == null) return arr
                if (first.has("kcmc") || first.has("kcdm") || first.has("dgjsmc") || first.has("DGJSMC")) {
                    return arr
                }
                val scanLimit = minOf(arr.length(), 5)
                for (i in 1 until scanLimit) {
                    val obj = arr.optJSONObject(i) ?: continue
                    if (obj.has("kcmc") || obj.has("kcdm") || obj.has("dgjsmc") || obj.has("DGJSMC")) {
                        return arr
                    }
                }
            }
        }
        return null
    }

    private fun buildTermDisplayName(yearName: String?, termName: String?): String {
        val year = yearName?.trim().orEmpty()
        val term = termName?.trim().orEmpty()
        if (year.isEmpty()) return term
        if (term.isEmpty()) return year
        return if (term.contains(year)) term else "$year $term"
    }

    // ================================================================ 课表时间结构
    @SuppressLint("SimpleDateFormat")
    override fun getScheduleStructure(
        term: TermItem,
        isUndergraduate: Boolean?,
        token: EASToken
    ): LiveData<DataState<MutableList<TimePeriodInDay>>> {
        val res = MutableLiveData<DataState<MutableList<TimePeriodInDay>>>()
        Thread {
            try {
                val kbBody = """{"xn":"${term.yearCode}","xq":"${term.termCode}","zc":"1","type":"json"}"""
                val kbResp = jsonPost(token, "/app/Kbcx/query", kbBody)
                val kbJo = JsonUtils.getJsonObject(kbResp.body())
                val contentArr = kbJo?.optJSONArray("content")
                val df = SimpleDateFormat("HH:mm", Locale.getDefault())
                val slots: MutableList<TimePeriodInDay?> = mutableListOf()
                if (contentArr != null) {
                    for (i in 0 until contentArr.length()) {
                        val obj = contentArr.optJSONObject(i) ?: continue
                        val jcList = obj.optJSONArray("jcList") ?: continue
                        var maxPeriod = 0
                        for (j in 0 until jcList.length()) {
                            val jc = jcList.optJSONObject(j) ?: continue
                            val jsjc = jc.optInt("JSJC", 0)
                            if (jsjc > maxPeriod) maxPeriod = jsjc
                        }
                        if (maxPeriod <= 0) continue
                        val defaults = defaultScheduleStructure()
                        if (maxPeriod <= defaults.size) {
                            res.postValue(DataState(defaults.take(maxPeriod).toMutableList()))
                            return@Thread
                        }
                        while (slots.size < maxPeriod) slots.add(null)
                        for (j in 0 until jcList.length()) {
                            val jc = jcList.optJSONObject(j) ?: continue
                            val ks = jc.optInt("KSJC", 0)
                            val js = jc.optInt("JSJC", 0)
                            val count = js - ks + 1
                            val sj = jc.optString("SJ", "")
                            val parts = sj.split("—", "–", "-")
                            if (count <= 0 || parts.size < 2) continue
                            try {
                                val start = df.parse(parts[0].trim()) ?: continue
                                val end = df.parse(parts[1].trim()) ?: continue
                                val totalMinutes = ((end.time - start.time) / 60000L).toInt()
                                if (totalMinutes <= 0) continue
                                val perSlot = totalMinutes / count
                                if (perSlot <= 0) continue
                                for (idx in 0 until count) {
                                    val slotStart = (start.time / 60000L).toInt() + perSlot * idx
                                    val slotEnd = if (idx == count - 1) {
                                        (end.time / 60000L).toInt()
                                    } else {
                                        (start.time / 60000L).toInt() + perSlot * (idx + 1)
                                    }
                                    val from = TimeInDay(slotStart / 60, slotStart % 60)
                                    val to = TimeInDay(slotEnd / 60, slotEnd % 60)
                                    val pos = ks - 1 + idx
                                    if (pos in slots.indices) {
                                        slots[pos] = TimePeriodInDay(from, to)
                                    }
                                }
                            } catch (_: Exception) { }
                        }
                        if (slots.any { it != null }) break
                    }
                }
                val result = if (slots.isNotEmpty() && slots.all { it != null }) {
                    slots.map { it!! }.toMutableList()
                } else {
                    val defaults = defaultScheduleStructure()
                    val size = maxOf(slots.size, defaults.size)
                    val filled = MutableList(size) { idx ->
                        slots.getOrNull(idx) ?: defaults.getOrNull(idx) ?: defaults.last()
                    }
                    filled
                }
                res.postValue(DataState(result))
            } catch (e: Exception) {
                e.printStackTrace()
                res.postValue(DataState(defaultScheduleStructure()))
            }
        }.start()
        return res
    }

    @SuppressLint("SimpleDateFormat")
    private fun defaultScheduleStructure(): MutableList<TimePeriodInDay> {
        val slots = listOf(
            "08:30" to "09:20", "09:25" to "10:15",
            "10:30" to "11:20", "11:25" to "12:15",
            "14:00" to "14:50", "14:55" to "15:45",
            "16:00" to "16:50", "16:55" to "17:45",
            "18:45" to "19:35", "19:40" to "20:30",
            "20:45" to "21:35", "21:40" to "22:30"
        )
        val df = SimpleDateFormat("HH:mm")
        return slots.map { (s, e) ->
            val from = Calendar.getInstance().also { c -> c.timeInMillis = df.parse(s)!!.time }
            val to = Calendar.getInstance().also { c -> c.timeInMillis = df.parse(e)!!.time }
            TimePeriodInDay(TimeInDay(from), TimeInDay(to))
        }.toMutableList()
    }

    // ================================================================ 成绩
    override fun getPersonalScores(
        term: TermItem,
        token: EASToken,
        testType: EASService.TestType
    ): LiveData<DataState<List<CourseScoreItem>>> {
        val res = MutableLiveData<DataState<List<CourseScoreItem>>>()
        Thread {
            val result: MutableList<CourseScoreItem> = ArrayList()
            try {
                val qzqmFlag = when (testType) {
                    EASService.TestType.NORMAL -> "qm"
                    EASService.TestType.RESIT  -> "qz"
                    else -> "qm"
                }
                val body = """{"xn":"${term.yearCode}","xq":"${term.termCode}","qzqmFlag":"$qzqmFlag","type":"json"}"""
                val resp = jsonPost(token, "/app/cjgl/xscjList?_lang=zh_CN", body)
                val jo = JsonUtils.getJsonObject(resp.body())
                val content = jo?.optJSONArray("content")
                content?.let {
                    for (i in 0 until it.length()) {
                        val tp = it.optJSONObject(i) ?: continue
                        val item = CourseScoreItem()
                        item.courseCode = tp.optString("kcdm")
                        item.courseName = tp.optString("kcmc")
                        item.credits = tp.optString("xf").toIntOrNull() ?: 0
                        item.finalScores = tp.optString("zf").toIntOrNull() ?: -1
                        item.courseProperty = tp.optString("kcxz")
                        item.courseCategory = tp.optString("kclb", tp.optString("kclbmc", ""))
                        item.termName = tp.optString("xnxq", term.yearCode + term.termCode)
                        item.assessMethod = tp.optString("khfs", "")
                        result.add(item)
                    }
                    res.postValue(DataState(result))
                } ?: run {
                    res.postValue(DataState(DataState.STATE.FETCH_FAILED))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                res.postValue(DataState(DataState.STATE.FETCH_FAILED, e.message))
            }
        }.start()
        return res
    }

    fun getPersonalScoresWithSummary(
        term: TermItem,
        token: EASToken,
        testType: EASService.TestType
    ): LiveData<DataState<ScoreQueryResult>> {
        val res = MutableLiveData<DataState<ScoreQueryResult>>()
        Thread {
            val result: MutableList<CourseScoreItem> = ArrayList()
            try {
                val qzqmFlag = when (testType) {
                    EASService.TestType.NORMAL -> "qm"
                    EASService.TestType.RESIT  -> "qz"
                    else -> "qm"
                }
                val body = """{"xn":"${term.yearCode}","xq":"${term.termCode}","qzqmFlag":"$qzqmFlag","type":"json"}"""
                val resp = jsonPost(token, "/app/cjgl/xscjList?_lang=zh_CN", body)
                val jo = JsonUtils.getJsonObject(resp.body())
                val content = jo?.optJSONArray("content")
                content?.let {
                    for (i in 0 until it.length()) {
                        val tp = it.optJSONObject(i) ?: continue
                        val item = CourseScoreItem()
                        item.courseCode = tp.optString("kcdm")
                        item.courseName = tp.optString("kcmc")
                        item.credits = tp.optString("xf").toIntOrNull() ?: 0
                        item.finalScores = tp.optString("zf").toIntOrNull() ?: -1
                        item.courseProperty = tp.optString("kcxz")
                        item.courseCategory = tp.optString("kclb", tp.optString("kclbmc", ""))
                        item.termName = tp.optString("xnxq", term.yearCode + term.termCode)
                        item.assessMethod = tp.optString("khfs", "")
                        result.add(item)
                    }
                    val summary = fetchScoreSummary(token) ?: extractScoreSummary(jo)
                    res.postValue(DataState(ScoreQueryResult(result, summary)))
                } ?: run {
                    res.postValue(DataState(DataState.STATE.FETCH_FAILED))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                res.postValue(DataState(DataState.STATE.FETCH_FAILED, e.message))
            }
        }.start()
        return res
    }

    private fun fetchScoreSummary(token: EASToken): ScoreSummary? {
        return try {
            val body = """{"type":"json","ksxnxq":"-1-1","jsxnxq":"-1-1","pylx":"${token.getStudentType()}"}"""
            val resp = jsonPost(token, "/app/cjgl/xfj", body, rolecode = "06")
            val jo = JsonUtils.getJsonObject(resp.body())
            val xfj = jo?.optJSONObject("content")?.optJSONObject("xfj") ?: return null
            val gpa = xfj.optString("XFJ", "").trim()
            val rank = xfj.optString("RANK", "").trim()
            val total = xfj.optString("ZYZRS", "").trim()
            if (gpa.isBlank() && rank.isBlank() && total.isBlank()) null
            else ScoreSummary(gpa = gpa, rank = rank, total = total)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractScoreSummary(jo: JSONObject?): ScoreSummary? {
        if (jo == null) return null
        val gpaKeys = listOf("xfjd", "pjxfjd", "avgxfjd", "gpa", "GPA", "xuefenji")
        val rankKeys = listOf("pm", "rank", "paiming", "pmj")
        val totalKeys = listOf("pmrs", "rank_total", "total", "zrs", "rs")
        val objects = buildList {
            add(jo)
            jo.optJSONObject("summary")?.let { add(it) }
            jo.optJSONObject("statistics")?.let { add(it) }
            jo.optJSONObject("data")?.let { add(it) }
            jo.optJSONObject("extra")?.let { add(it) }
        }
        val gpa = objects.firstNotNullOfOrNull { findFirstString(it, gpaKeys) }.orEmpty()
        val rank = objects.firstNotNullOfOrNull { findFirstString(it, rankKeys) }.orEmpty()
        val total = objects.firstNotNullOfOrNull { findFirstString(it, totalKeys) }.orEmpty()
        if (gpa.isBlank() && rank.isBlank() && total.isBlank()) return null
        return ScoreSummary(gpa = gpa, rank = rank, total = total)
    }

    private fun findFirstString(obj: JSONObject, keys: List<String>): String? {
        for (key in keys) {
            val value = obj.optString(key, "").trim()
            if (value.isNotEmpty()) return value
        }
        return null
    }

    // ================================================================ 教学楼列表
    override fun getTeachingBuildings(token: EASToken): LiveData<DataState<List<BuildingItem>>> {
        val result = MutableLiveData<DataState<List<BuildingItem>>>()
        Thread {
            val res = mutableListOf<BuildingItem>()
            try {
                val resp = authedFormPost(token, "/app/commapp/queryjxllist")
                val jo = JsonUtils.getJsonObject(resp.body())
                val content = jo?.optJSONArray("content")
                for (i in 0 until (content?.length() ?: 0)) {
                    val item = content?.optJSONObject(i) ?: continue
                    val b = BuildingItem()
                    b.name = item.optString("MC")
                    b.id = item.optString("DM")
                    res.add(b)
                }
                result.postValue(DataState(res))
            } catch (e: Exception) {
                result.postValue(DataState(DataState.STATE.FETCH_FAILED))
            }
        }.start()
        return result
    }

    // ================================================================ 空教室查询（按天）
    override fun queryEmptyClassroom(
        token: EASToken,
        term: TermItem,
        building: BuildingItem,
        weeks: List<String>
    ): LiveData<DataState<List<ClassroomItem>>> {
        val result = MutableLiveData<DataState<List<ClassroomItem>>>()
        Thread {
            val resMap = linkedMapOf<String, ClassroomItem>()
            try {
                val week = (weeks.firstOrNull()?.trim() ?: "").ifBlank { "1" }
                val dates = resolveWeekDates(token, term, week)
                val fallbackDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(Calendar.getInstance().time)
                val datePairs = if (dates.isNotEmpty()) {
                    dates.mapIndexed { index, d -> index + 1 to d }
                } else {
                    listOf(1 to fallbackDate)
                }

                for ((dow, date) in datePairs) {
                    val resp = authedFormPost(
                        token, "/app/kbrcbyapp/querycdzyxx",
                        mapOf("nyr" to date, "jxl" to building.id)
                    )
                    val jo = JsonUtils.getJsonObject(resp.body())
                    val content = jo?.optJSONArray("content")
                    for (i in 0 until (content?.length() ?: 0)) {
                        val item = content?.optJSONObject(i) ?: continue
                        val name = item.optString("CDMC").trim()
                        if (name.isBlank()) continue
                        val classroom = resMap.getOrPut(name) {
                            ClassroomItem().apply {
                                this.name = name
                                this.id = name
                            }
                        }
                        for (dj in 1..6) {
                            val v = item.optString("DJ$dj", "0").trim()
                            if (v.isEmpty() || v == "0") continue
                            val start = (dj - 1) * 2 + 1
                            val end = start + 1
                            for (period in start..end) {
                                val scheduleJson = JSONObject()
                                scheduleJson.put("XQJ", dow)
                                scheduleJson.put("XJ", period)
                                scheduleJson.put("PKBJ", "占用")
                                classroom.scheduleList.add(scheduleJson)
                            }
                        }
                    }
                }
                result.postValue(DataState(resMap.values.toList()))
            } catch (e: Exception) {
                e.printStackTrace()
                result.postValue(DataState(DataState.STATE.FETCH_FAILED))
            }
        }.start()
        return result
    }

    private fun resolveWeekDates(token: EASToken, term: TermItem, week: String): List<String> {
        return try {
            val kbBody = """{"xn":"${term.yearCode}","xq":"${term.termCode}","zc":"$week","type":"json"}"""
            val kbResp = jsonPost(token, "/app/Kbcx/query", kbBody)
            val kbJo = JsonUtils.getJsonObject(kbResp.body())
            val contentArr = kbJo?.optJSONArray("content") ?: return emptyList()
            for (i in 0 until contentArr.length()) {
                val obj = contentArr.optJSONObject(i) ?: continue
                val rqList = obj.optJSONArray("rqList") ?: continue
                val dates = mutableListOf<String>()
                for (j in 0 until rqList.length()) {
                    val rq = rqList.optJSONObject(j)?.optString("RQ")?.trim().orEmpty()
                    if (rq.isNotBlank()) dates.add(rq)
                }
                if (dates.isNotEmpty()) return dates
            }
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }


    // ================================================================ 考试信息（暂未找到新接口）
    override fun getExamItems(token: EASToken): LiveData<DataState<List<ExamItem>>> {
        val res = MutableLiveData<DataState<List<ExamItem>>>()
        Thread {
            // TODO: 补充新版考试查询接口
            res.postValue(DataState(emptyList(), DataState.STATE.SUCCESS))
        }.start()
        return res
    }

    // ================================================================ 选课（加入购物车 = 实际选课动作）
    fun selectCourse(
        token: EASToken,
        term: TermItem,
        courseId: String,
        poolCode: String = "bx-b-b"
    ): LiveData<DataState<String>> {
        val res = MutableLiveData<DataState<String>>()
        Thread {
            try {
                val body = """{"RoleCode":"01","p_pylx":"${token.getStudentType()}","p_xn":"${term.yearCode}","p_xq":"${term.termCode}","p_xkfsdm":"$poolCode","p_xktjz":"rwtjzyx","p_id":"$courseId"}"""
                val resp = jsonPost(token, "/app/Xsxk/addGouwuche?_lang=zh_CN", body)
                if (resp.statusCode() >= 400) {
                    val snippet = resp.body().take(240)
                    res.postValue(DataState(DataState.STATE.FETCH_FAILED, "HTTP ${resp.statusCode()}: $snippet"))
                    return@Thread
                }
                val jo = JsonUtils.getJsonObject(resp.body())
                val content = jo?.optJSONObject("content")
                val jg = content?.optString("jg", "-1") ?: "-1"
                val rawMsg = content?.optString("message")
                var msg = if (!rawMsg.isNullOrBlank()) rawMsg else jo?.optString("msg", "").orEmpty()
                if (rawMsg.isNullOrBlank() && msg.trim() == "操作失败") {
                    msg = ""
                }
                val message = buildString {
                    if (jg.isNotBlank()) append("jg=").append(jg)
                    if (msg.isNotBlank()) {
                        if (isNotEmpty()) append("，")
                        append(msg)
                    }
                    if (isEmpty()) append("未知错误")
                }
                if (jg != "-1") {
                    res.postValue(DataState(message, DataState.STATE.SUCCESS))
                } else {
                    res.postValue(DataState(DataState.STATE.FETCH_FAILED, message))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                res.postValue(DataState(DataState.STATE.FETCH_FAILED, e.message))
            }
        }.start()
        return res
    }

    // ================================================================ 查询可选课程列表
    fun getAvailableCourses(
        token: EASToken,
        term: TermItem,
        poolCode: String = "bx-b-b",
        page: Int = 1,
        pageSize: Int = 20
    ): LiveData<DataState<List<TermSubject>>> {
        val res = MutableLiveData<DataState<List<TermSubject>>>()
        Thread {
            val result = mutableListOf<TermSubject>()
            try {
                val body = """{"RoleCode":"01","p_pylx":"${token.getStudentType()}","p_xn":"${term.yearCode}","p_xq":"${term.termCode}","p_xnxq":"${term.getCode()}","p_gjz":"","p_kc_gjz":"","p_xkfsdm":"$poolCode","pageNum":$page,"pageSize":$pageSize}"""
                val resp = jsonPost(token, "/app/Xsxk/queryKxrw?_lang=zh_CN", body)
                val jo = JsonUtils.getJsonObject(resp.body())
                val list = jo?.optJSONArray("yxkcList")
                list?.let {
                    for (i in 0 until it.length()) {
                        val item = it.optJSONObject(i) ?: continue
                        val s = TermSubject()
                        val rawCode = item.optString("kcdm")
                        s.code = CourseCodeUtils.normalize(rawCode) ?: rawCode
                        s.name = item.optString("kcmc", "")
                        s.school = item.optString("kkyxmc")
                        s.credit = item.optString("xf").toFloatOrNull() ?: 0f
                        s.key = item.optString("id")  // p_id 用于选课
                        s.field = optStringFirst(item, listOf("kclbmc", "KCLBMC"))
                        result.add(s)
                    }
                }
                res.postValue(DataState(result))
            } catch (e: Exception) {
                e.printStackTrace()
                res.postValue(DataState(DataState.STATE.FETCH_FAILED, e.message))
            }
        }.start()
        return res
    }
}
