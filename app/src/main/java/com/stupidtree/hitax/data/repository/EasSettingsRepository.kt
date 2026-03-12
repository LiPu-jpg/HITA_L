package com.stupidtree.hitax.data.repository

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.stupidtree.component.data.SharedPreferenceBooleanLiveData
import com.stupidtree.component.data.booleanLiveData

class EasSettingsRepository(application: Application) {
    private val preference: SharedPreferences =
        application.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)

    val autoReimportLiveData: SharedPreferenceBooleanLiveData =
        preference.booleanLiveData(KEY_AUTO_REIMPORT, true)

    init {
        // Default to enabled for existing users if the key was never set.
        if (!preference.contains(KEY_AUTO_REIMPORT)) {
            preference.edit().putBoolean(KEY_AUTO_REIMPORT, true).apply()
        }
    }

    fun setAutoReimport(enabled: Boolean) {
        preference.edit().putBoolean(KEY_AUTO_REIMPORT, enabled).apply()
    }

    fun isAutoReimportEnabled(): Boolean {
        return preference.getBoolean(KEY_AUTO_REIMPORT, true)
    }

    fun getLastAutoReimportTs(): Long {
        return preference.getLong(KEY_AUTO_REIMPORT_LAST_TS, 0L)
    }

    fun setLastAutoReimportTs(ts: Long) {
        preference.edit().putLong(KEY_AUTO_REIMPORT_LAST_TS, ts).apply()
    }

    companion object {
        private var instance: EasSettingsRepository? = null
        private const val SP_NAME = "eas_settings"
        const val KEY_AUTO_REIMPORT = "auto_reimport"
        const val KEY_AUTO_REIMPORT_LAST_TS = "auto_reimport_last_ts"

        fun getInstance(application: Application): EasSettingsRepository {
            if (instance == null) instance = EasSettingsRepository(application)
            return instance!!
        }
    }
}
