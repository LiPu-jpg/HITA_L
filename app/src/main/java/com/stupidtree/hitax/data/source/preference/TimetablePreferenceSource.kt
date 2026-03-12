package com.stupidtree.hitax.data.source.preference


import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.stupidtree.hitax.data.model.timetable.TimeInDay
import com.stupidtree.hitax.data.model.timetable.TimePeriodInDay


class TimetablePreferenceSource(private val context: Context) {
    private var sharedPreferences: SharedPreferences? = null
    private val preference: SharedPreferences
        get() {
            if (sharedPreferences == null) {
                sharedPreferences = context.getSharedPreferences(SP_NAME_TIMETABLE, Context.MODE_PRIVATE)
            }
            return sharedPreferences!!
        }

    fun getSchedule(): MutableList<TimePeriodInDay> {
        var result: MutableList<TimePeriodInDay> = mutableListOf()
        val total = preference.getInt("class_num", -1)
        if (total < 0) {
            result = undergraduate_default
            saveSchedules(result)
            return result
        }
        for (i in 0 until total) {
            val tp: TimePeriodInDay = Gson().fromJson(sharedPreferences!!.getString("class_$i", "{}"), TimePeriodInDay::class.java)
            result.add(tp)
        }
        if (isLegacyUndergraduateSchedule(result)) {
            result = undergraduate_default
            saveSchedules(result)
        }
        return result
    }

    fun saveSchedules(sch: List<TimePeriodInDay>) {
        val editor = preference.edit()
        for (i in sch.indices) {
            editor.putString("class_$i", Gson().toJson(sch[i]))
        }
        editor.putInt("class_num", sch.size).apply()
    }


    companion object {
        private const val SP_NAME_TIMETABLE = "timetable"

        @SuppressLint("StaticFieldLeak")
        private var instance: TimetablePreferenceSource? = null

        @JvmStatic
        fun getInstance(context: Context): TimetablePreferenceSource {
            if (instance == null) {
                instance = TimetablePreferenceSource(context.applicationContext)
            }
            return instance!!
        }


        var undergraduate_default = mutableListOf(
                TimePeriodInDay(TimeInDay(8, 30), TimeInDay(9, 20)),
                TimePeriodInDay(TimeInDay(9, 25), TimeInDay(10, 15)),
                TimePeriodInDay(TimeInDay(10, 30), TimeInDay(11, 20)),
                TimePeriodInDay(TimeInDay(11, 25), TimeInDay(12, 15)),
                TimePeriodInDay(TimeInDay(14, 0), TimeInDay(14, 50)),
                TimePeriodInDay(TimeInDay(14, 55), TimeInDay(15, 45)),
                TimePeriodInDay(TimeInDay(16, 0), TimeInDay(16, 50)),
                TimePeriodInDay(TimeInDay(16, 55), TimeInDay(17, 45)),
                TimePeriodInDay(TimeInDay(18, 45), TimeInDay(19, 35)),
                TimePeriodInDay(TimeInDay(19, 40), TimeInDay(20, 30)),
                TimePeriodInDay(TimeInDay(20, 45), TimeInDay(21, 35)),
                TimePeriodInDay(TimeInDay(21, 40), TimeInDay(22, 30)))

        private fun isLegacyUndergraduateSchedule(schedule: List<TimePeriodInDay>): Boolean {
            if (schedule.size < 12) return false
            val first = schedule[0]
            val second = schedule[1]
            val fifth = schedule.getOrNull(4)
            return first.from.hour == 8 && first.from.minute == 30 &&
                first.to.hour == 9 && first.to.minute == 20 &&
                second.from.hour == 9 && second.from.minute == 30 &&
                second.to.hour == 10 && second.to.minute == 15 &&
                fifth?.from?.hour == 13 && fifth.from.minute == 45
        }
    }

}
