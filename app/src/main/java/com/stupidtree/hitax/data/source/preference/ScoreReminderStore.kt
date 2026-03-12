package com.stupidtree.hitax.data.source.preference

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ScoreReminderStore private constructor(context: Context) {
    private val preference: SharedPreferences =
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = preference.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        preference.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getKnownScores(): Set<String> {
        val raw = preference.getString(KEY_KNOWN_SCORES, null) ?: return emptySet()
        return runCatching {
            val type = object : TypeToken<Set<String>>() {}.type
            Gson().fromJson<Set<String>>(raw, type) ?: emptySet()
        }.getOrDefault(emptySet())
    }

    fun setKnownScores(values: Set<String>) {
        preference.edit().putString(KEY_KNOWN_SCORES, Gson().toJson(values)).apply()
    }

    fun clearKnownScores() {
        preference.edit().remove(KEY_KNOWN_SCORES).apply()
    }

    companion object {
        private const val SP_NAME = "score_reminder"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_KNOWN_SCORES = "known_scores"

        @SuppressLint("StaticFieldLeak")
        private var instance: ScoreReminderStore? = null

        fun getInstance(context: Context): ScoreReminderStore {
            if (instance == null) {
                instance = ScoreReminderStore(context.applicationContext)
            }
            return instance!!
        }
    }
}
