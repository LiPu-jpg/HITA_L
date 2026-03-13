package com.stupidtree.hitax.data.repository

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import com.stupidtree.component.data.DataState
import com.stupidtree.hitax.data.AppDatabase
import com.stupidtree.hitax.data.model.eas.*
import com.stupidtree.hitax.data.model.timetable.EventItem
import com.stupidtree.hitax.data.model.timetable.TermSubject
import com.stupidtree.hitax.data.model.timetable.TimePeriodInDay
import com.stupidtree.hitax.data.model.timetable.Timetable
import com.stupidtree.hitax.data.source.preference.EasPreferenceSource
import com.stupidtree.hitax.data.source.preference.TimetablePreferenceSource
import com.stupidtree.hitax.data.source.web.eas.EASource
import com.stupidtree.hitax.data.source.web.service.EASService
import com.stupidtree.hitax.ui.eas.classroom.BuildingItem
import com.stupidtree.hitax.ui.eas.classroom.ClassroomItem
import com.stupidtree.hitax.utils.LiveDataUtils
import com.stupidtree.hitax.utils.TimeTools.getDateAtWOT
import com.stupidtree.hitax.utils.CourseCodeUtils
import com.stupidtree.sync.StupidSync
import com.stupidtree.sync.data.model.History
import java.sql.Timestamp
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class EASRepository internal constructor(application: Application) {
    private val appContext = application.applicationContext
    private val easService: EASService = EASource()
    private var easPreferenceSource = EasPreferenceSource.getInstance(application)
    private var eventItemDao = AppDatabase.getDatabase(application).eventItemDao()
    private var timetableDao = AppDatabase.getDatabase(application).timetableDao()
    private var subjectDao = AppDatabase.getDatabase(application).subjectDao()

    /**
     * 进行登录
     */
    fun login(username: String, password: String): LiveData<DataState<Boolean>> {
        return easService.login(username, password, null).map {
            if (it.state == DataState.STATE.SUCCESS) {
                it.data?.let { it1 -> easPreferenceSource.saveEasToken(it1) }
                return@map DataState(true, DataState.STATE.SUCCESS)
            }
            return@map DataState(false, it.state)
        }
    }

    /**
     * 验证登录
     */
    fun loginCheck(): LiveData<DataState<Boolean>> {
        val token = easPreferenceSource.getEasToken()
        if (!token.isLogin()) {
            return LiveDataUtils.getMutableLiveData(DataState(false))
        }
        return easService.loginCheck(token).switchMap {
            if (it.state == DataState.STATE.SUCCESS && it.data != null) {
                if (!it.data!!.first) {
                    easPreferenceSource.clearEasToken()
                } else {
                    easPreferenceSource.saveEasToken(it.data!!.second)
                }
            }
            return@switchMap MutableLiveData(DataState(it.data?.first == true))
        }
    }

    /**
     * 获取学期开始日期
     */
    fun getStartDateOfTerm(term: TermItem): LiveData<DataState<Calendar>> {
        val easToken = easPreferenceSource.getEasToken()
        if (easToken.isLogin()) {
            return easService.getStartDate(easToken, term)
        }
        return LiveDataUtils.getMutableLiveData<DataState<Calendar>>(DataState(DataState.STATE.NOT_LOGGED_IN))
    }


    /**
     * 进行获取学年学期
     */
    fun getAllTerms(): LiveData<DataState<List<TermItem>>> {
        val easToken = easPreferenceSource.getEasToken()
        if (easToken.isLogin()) {
            return easService.getAllTerms(easToken)
        }
        return LiveDataUtils.getMutableLiveData<DataState<List<TermItem>>>(DataState(DataState.STATE.NOT_LOGGED_IN))
    }

    /**
     * 获取课表结构
     */
    fun getScheduleStructure(
        term: TermItem,
        isUndergraduate: Boolean? = null
    ): LiveData<DataState<MutableList<TimePeriodInDay>>> {
        val easToken = easPreferenceSource.getEasToken()
        if (easToken.isLogin()) {
            return easService.getScheduleStructure(term, isUndergraduate, easToken)
        }
        return LiveDataUtils.getMutableLiveData<DataState<MutableList<TimePeriodInDay>>>(
            DataState(
                DataState.STATE.NOT_LOGGED_IN
            )
        )

    }

    /**
     * 获取教学楼列表
     */
    fun getTeachingBuildings(): LiveData<DataState<List<BuildingItem>>> {
        val easToken = easPreferenceSource.getEasToken()
        if (easToken.isLogin()) {
            return easService.getTeachingBuildings(easToken)
        }
        return LiveDataUtils.getMutableLiveData(DataState(DataState.STATE.NOT_LOGGED_IN))

    }

    /**
     * 查询空教室
     */
    fun queryEmptyClassroom(
        term: TermItem,
        buildingItem: BuildingItem,
        week: Int
    ): LiveData<DataState<List<ClassroomItem>>> {
        val easToken = easPreferenceSource.getEasToken()
        if (easToken.isLogin()) {
            return easService.queryEmptyClassroom(
                easToken,
                term,
                buildingItem,
                listOf(week.toString())
            )
        }
        return LiveDataUtils.getMutableLiveData(DataState(DataState.STATE.NOT_LOGGED_IN))
    }

    /**
     * 获取最终成绩
     */
    fun getPersonalScores(
        term: TermItem,
        testType: EASService.TestType
    ): LiveData<DataState<List<CourseScoreItem>>> {
        val easToken = easPreferenceSource.getEasToken()
        if (easToken.isLogin()) {
            return easService.getPersonalScores(term, easToken, testType)
        }
        return LiveDataUtils.getMutableLiveData(DataState(DataState.STATE.NOT_LOGGED_IN))
    }

    fun getPersonalScoresWithSummary(
        term: TermItem,
        testType: EASService.TestType
    ): LiveData<DataState<ScoreQueryResult>> {
        val easToken = easPreferenceSource.getEasToken()
        if (easToken.isLogin()) {
            return (easService as EASource).getPersonalScoresWithSummary(term, easToken, testType)
        }
        return LiveDataUtils.getMutableLiveData(DataState(DataState.STATE.NOT_LOGGED_IN))
    }

    /**
     * 获取考试信息
     */
    fun getExamInfo(): LiveData<DataState<List<ExamItem>>> {
        val easToken = easPreferenceSource.getEasToken()
        if (easToken.isLogin()) {
            return easService.getExamItems(easToken)
        }
        return LiveDataUtils.getMutableLiveData(DataState(DataState.STATE.NOT_LOGGED_IN))
    }

    /**
     * 动作：导入课表
     */
    private var timetableWebLiveData: LiveData<DataState<List<CourseItem>>>? = null
    fun startImportTimetableOfTerm(
        term: TermItem,
        startDate: Calendar,
        schedule: List<TimePeriodInDay>,//课表结构
        importTimetableLiveData: MediatorLiveData<DataState<Boolean>>
    ) {
        startDate.set(Calendar.HOUR_OF_DAY, 0)
        startDate.set(Calendar.MINUTE, 0)
        startDate.firstDayOfWeek = Calendar.MONDAY
        startDate.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val easToken = easPreferenceSource.getEasToken()
        if (easToken.isLogin()) {
            val finished = AtomicBoolean(false)
            val timeoutHandler = Handler(Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                if (finished.compareAndSet(false, true)) {
                    importTimetableLiveData.value =
                        DataState(DataState.STATE.FETCH_FAILED, "导入超时，请重试")
                }
            }
            timeoutHandler.postDelayed(timeoutRunnable, 90_000L)
            timetableWebLiveData?.let { importTimetableLiveData.removeSource(it) }
            timetableWebLiveData =
                easService.getTimetableOfTerm(term, easToken)
            importTimetableLiveData.addSource(timetableWebLiveData!!) {
                if (it.state == DataState.STATE.SUCCESS) {
                    val courseItems = it.data
                    if (courseItems.isNullOrEmpty()) {
                        if (finished.compareAndSet(false, true)) {
                            timeoutHandler.removeCallbacks(timeoutRunnable)
                            importTimetableLiveData.value =
                                DataState(DataState.STATE.FETCH_FAILED, "empty timetable")
                        }
                        return@addSource
                    }
                    Thread {
                        try {
                            val meta = fetchSelectedSubjectMeta(term, easToken)
                            val teacherMap = meta.teacherMap
                            val creditMap = meta.creditMap
                            val maxPeriod = courseItems.maxOfOrNull { item ->
                                (item.begin + item.last - 1).coerceAtLeast(item.begin)
                            } ?: 0
                            val safeSchedule = buildSafeSchedule(schedule, maxPeriod)
                            //更新timetable信息
                            var timetable = timetableDao.getTimetableByEASCodeSync(term.getCode())
                            if (timetable == null) {
                                timetable = Timetable()
                            } else {
                                //若存在，则先清空原有课表课程
                                val eventIds =
                                    eventItemDao.getEventIdsFromTimetablesSync(listOf(timetable.id))
                                StupidSync.putHistorySync("event", History.ACTION.REMOVE, eventIds)
                                eventItemDao.deleteCourseFromTimetable(timetable.id)
                            }
                            StupidSync.putHistorySync(
                                "timetable",
                                History.ACTION.REQUIRE,
                                listOf(timetable.id)
                            )
                            //记录最后的时间戳，作为学期结束的标志
                            var maxTs: Long = 0
                            //添加时间表
                            val events = mutableListOf<EventItem>()
                            val requireSubjects = mutableMapOf<String, String>()
                            for (item in courseItems) {
                                val startIndex = item.begin - 1
                                val endIndex = item.begin + item.last - 2
                                if (startIndex !in safeSchedule.indices || endIndex !in safeSchedule.indices) {
                                    continue
                                }
                                val spStart = safeSchedule[startIndex]
                                val spEnd = safeSchedule[endIndex]

                                //添加科目
                                var subject = subjectDao.getSubjectByName(timetable.id, item.name)
                                if (subject == null) {//不存在，新建
                                    subject = TermSubject()
                                    subject.name = item.name.toString()
                                    subject.timetableId = timetable.id
                                    subject.id = UUID.randomUUID().toString()
                                }
                            val code = CourseCodeUtils.normalize(item.code) ?: item.code?.trim().orEmpty()
                            if (code.isNotBlank() && subject.code.isNullOrBlank()) {
                                subject.code = code
                            }
                            if (subject.credit <= 0f) {
                                val mappedCredit = creditMap[code]
                                    ?: item.name?.let { name -> creditMap[name] }
                                if (mappedCredit != null && mappedCredit > 0f) {
                                    subject.credit = mappedCredit
                                }
                            }
                            if (subject.field.isNullOrBlank()) {
                                val mappedField = meta.fieldMap[code]
                                    ?: item.name?.let { name -> meta.fieldMap[name] }
                                if (!mappedField.isNullOrBlank()) {
                                    subject.field = mappedField
                                }
                            }
                            if (subject.selectCategory.isNullOrBlank()) {
                                val mappedSelect = meta.selectCategoryMap[code]
                                    ?: item.name?.let { name -> meta.selectCategoryMap[name] }
                                if (!mappedSelect.isNullOrBlank()) {
                                    subject.selectCategory = mappedSelect
                                }
                            }
                            if (subject.nature.isNullOrBlank()) {
                                val mappedNature = meta.natureMap[code]
                                    ?: item.name?.let { name -> meta.natureMap[name] }
                                if (!mappedNature.isNullOrBlank()) {
                                    subject.nature = mappedNature
                                }
                            }
                            subjectDao.saveSubjectSync(subject)
                                if (requireSubjects[subject.id] == null) {
                                    requireSubjects[subject.id] = subject.id
                                    StupidSync.putHistorySync(
                                        "subject",
                                        History.ACTION.REQUIRE,
                                        listOf(subject.id)
                                    )
                                }

                                for (week in item.weeks) {
                                    val from = getDateAtWOT(startDate, week, item.dow)
                                    val to = getDateAtWOT(startDate, week, item.dow)
                                    from.set(Calendar.HOUR_OF_DAY, spStart.from.hour)
                                    from.set(Calendar.MINUTE, spStart.from.minute)
                                    to.set(Calendar.HOUR_OF_DAY, spEnd.to.hour)
                                    to.set(Calendar.MINUTE, spEnd.to.minute)
                                    val e = EventItem()
                                    e.name = item.name.toString()
                                    e.from.time = from.timeInMillis
                                    e.fromNumber = item.begin
                                    e.subjectId = subject.id
                                    e.lastNumber = item.last
                                    e.to.time = to.timeInMillis
                                    val mappedTeacher = item.teacher?.takeIf { t -> t.isNotBlank() }
                                        ?: code.takeIf { it.isNotBlank() }?.let { teacherMap[it] }
                                        ?: item.name?.let { name -> teacherMap[name] }
                                    e.teacher = mappedTeacher
                                    e.place = item.classroom
                                    e.timetableId = timetable.id
                                    if (e.to.time > maxTs) maxTs = e.to.time
                                    events.add(e)
                                }
                            }
                            if (events.isEmpty()) {
                                if (finished.compareAndSet(false, true)) {
                                    timeoutHandler.removeCallbacks(timeoutRunnable)
                                    importTimetableLiveData.postValue(
                                        DataState(DataState.STATE.FETCH_FAILED, "empty events")
                                    )
                                }
                                return@Thread
                            }
                            eventItemDao.saveEvents(events)
                            StupidSync.putHistorySync("event", History.ACTION.REQUIRE, events.getIds())

                            //更新timetable对象
                            timetable.name = buildTimetableName(term)
                            timetable.startTime = Timestamp(startDate.timeInMillis)
                            timetable.endTime = Timestamp(maxTs)
                            timetable.code = term.getCode()
                            timetable.scheduleStructure = safeSchedule
                            timetableDao.saveTimetableSync(timetable)

                            if (finished.compareAndSet(false, true)) {
                                timeoutHandler.removeCallbacks(timeoutRunnable)
                                importTimetableLiveData.postValue(DataState(true, DataState.STATE.SUCCESS))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            if (finished.compareAndSet(false, true)) {
                                timeoutHandler.removeCallbacks(timeoutRunnable)
                                importTimetableLiveData.postValue(
                                    DataState(DataState.STATE.FETCH_FAILED, e.message)
                                )
                            }
                        }
                    }.start()
                } else {
                    if (finished.compareAndSet(false, true)) {
                        timeoutHandler.removeCallbacks(timeoutRunnable)
                        importTimetableLiveData.value =
                            DataState(DataState.STATE.FETCH_FAILED, it.message)
                    }
                }
            }
        } else {
            importTimetableLiveData.value = DataState(DataState.STATE.NOT_LOGGED_IN)
        }
    }

    private fun buildSafeSchedule(
        schedule: List<TimePeriodInDay>,
        requiredMaxPeriod: Int
    ): List<TimePeriodInDay> {
        if (requiredMaxPeriod <= 0) return schedule
        if (schedule.size >= requiredMaxPeriod) return schedule
        val defaults = Timetable().getDefaultTimeStructure()
        val size = maxOf(requiredMaxPeriod, defaults.size, schedule.size)
        return List(size) { idx ->
            schedule.getOrNull(idx) ?: defaults.getOrNull(idx) ?: defaults.last()
        }
    }

    private fun buildTimetableName(term: TermItem): String {
        val year = term.yearName.trim()
        val termName = term.termName.trim()
        if (year.isEmpty()) return termName
        if (termName.isEmpty()) return year
        return if (termName.contains(year)) termName else "$year $termName"
    }

    private data class SelectedSubjectMeta(
        val teacherMap: Map<String, String>,
        val creditMap: Map<String, Float>,
        val fieldMap: Map<String, String>,
        val selectCategoryMap: Map<String, String>,
        val natureMap: Map<String, String>
    )

    private fun fetchSelectedSubjectMeta(term: TermItem, token: EASToken): SelectedSubjectMeta {
        val teacherMap = mutableMapOf<String, String>()
        val creditMap = mutableMapOf<String, Float>()
        val fieldMap = mutableMapOf<String, String>()
        val selectCategoryMap = mutableMapOf<String, String>()
        val natureMap = mutableMapOf<String, String>()
        val latch = CountDownLatch(1)
        val live = easService.getSubjectsOfTerm(token, term)
        val observer = Observer<DataState<MutableList<TermSubject>>> { state ->
            if (state.state == DataState.STATE.SUCCESS || state.state == DataState.STATE.FETCH_FAILED) {
                state.data?.forEach { subject ->
                    val teacher = subject.teacher?.trim()
                    if (!teacher.isNullOrEmpty()) {
                        subject.code?.let { code -> teacherMap[code] = teacher }
                        if (subject.name.isNotBlank()) teacherMap[subject.name] = teacher
                    }
                    val credit = subject.credit
                    if (credit > 0f) {
                        subject.code?.let { code -> creditMap[code] = credit }
                        if (subject.name.isNotBlank()) creditMap[subject.name] = credit
                    }
                    subject.field?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
                        subject.code?.let { code -> fieldMap[code] = value }
                        if (subject.name.isNotBlank()) fieldMap[subject.name] = value
                    }
                    subject.selectCategory?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
                        subject.code?.let { code -> selectCategoryMap[code] = value }
                        if (subject.name.isNotBlank()) selectCategoryMap[subject.name] = value
                    }
                    subject.nature?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
                        subject.code?.let { code -> natureMap[code] = value }
                        if (subject.name.isNotBlank()) natureMap[subject.name] = value
                    }
                }
                latch.countDown()
            }
        }
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post { live.observeForever(observer) }
        latch.await(4, TimeUnit.SECONDS)
        mainHandler.post { live.removeObserver(observer) }
        return SelectedSubjectMeta(teacherMap, creditMap, fieldMap, selectCategoryMap, natureMap)
    }

    fun startAutoImportCurrentTimetable(
        isUndergraduate: Boolean,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        val token = easPreferenceSource.getEasToken()
        if (!token.isLogin()) {
            onResult?.invoke(false)
            return
        }
        Thread {
            val termsState = awaitLiveData(easService.getAllTerms(token), 6)
            val term = termsState.data?.firstOrNull { it.isCurrent } ?: termsState.data?.firstOrNull()
            if (term == null) {
                onResult?.invoke(false)
                return@Thread
            }
            val startState = awaitLiveData(easService.getStartDate(token, term), 6)
            val startDate = startState.data
            val scheduleState = awaitLiveData(
                easService.getScheduleStructure(term, isUndergraduate, token),
                6
            )
            val schedule = scheduleState.data ?: TimetablePreferenceSource.getInstance(appContext).getSchedule()
            if (startDate == null) {
                onResult?.invoke(false)
                return@Thread
            }
            val importLive = MediatorLiveData<DataState<Boolean>>()
            val latch = CountDownLatch(1)
            var success = false
            val observer = Observer<DataState<Boolean>> { state ->
                if (state.state == DataState.STATE.SUCCESS || state.state == DataState.STATE.FETCH_FAILED) {
                    success = state.state == DataState.STATE.SUCCESS
                    latch.countDown()
                }
            }
            val mainHandler = Handler(Looper.getMainLooper())
            mainHandler.post {
                importLive.observeForever(observer)
                startImportTimetableOfTerm(term, startDate, schedule, importLive)
            }
            latch.await(25, TimeUnit.SECONDS)
            mainHandler.post { importLive.removeObserver(observer) }
            onResult?.invoke(success)
        }.start()
    }

    private fun <T> awaitLiveData(
        live: LiveData<DataState<T>>,
        timeoutSeconds: Long
    ): DataState<T> {
        val latch = CountDownLatch(1)
        var result = DataState<T>(DataState.STATE.FETCH_FAILED)
        val observer = Observer<DataState<T>> { state ->
            result = state
            latch.countDown()
        }
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post { live.observeForever(observer) }
        latch.await(timeoutSeconds, TimeUnit.SECONDS)
        mainHandler.post { live.removeObserver(observer) }
        return result
    }

    fun getEasToken(): EASToken {
        return easPreferenceSource.getEasToken()
    }

    fun logout() {
        easPreferenceSource.clearEasToken()
    }

    fun getAvailableCourses(
        term: TermItem,
        poolCode: String = "bx-b-b",
        page: Int = 1,
        pageSize: Int = 20
    ): LiveData<DataState<List<TermSubject>>> {
        val token = easPreferenceSource.getEasToken()
        return (easService as EASource).getAvailableCourses(token, term, poolCode, page, pageSize)
    }

    fun selectCourse(
        term: TermItem,
        courseId: String,
        poolCode: String = "bx-b-b"
    ): LiveData<DataState<String>> {
        val token = easPreferenceSource.getEasToken()
        return (easService as EASource).selectCourse(token, term, courseId, poolCode)
    }


    companion object {
        @Volatile
        private var instance: EASRepository? = null
        fun getInstance(application: Application): EASRepository {
            synchronized(EASService::class.java) {
                if (instance == null) instance = EASRepository(application)
                return instance!!
            }
        }
    }
}

private fun List<EventItem>.getIds(): List<String> {
    val res = mutableListOf<String>()
    for (e in this) {
        res.add(e.id)
    }
    return res
}
