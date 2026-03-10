package com.stupidtree.hitax.data.source.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.stupidtree.hitax.data.model.eas.ExamItem

class ExamMemoStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<ExamItem>>() {}.type

    fun load(): MutableList<ExamItem> {
        val raw = prefs.getString(KEY_MEMOS, "").orEmpty()
        if (raw.isBlank()) return mutableListOf()
        return try {
            val parsed: List<ExamItem>? = gson.fromJson(raw, listType)
            parsed?.toMutableList() ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun save(list: List<ExamItem>) {
        prefs.edit().putString(KEY_MEMOS, gson.toJson(list)).apply()
    }

    fun add(item: ExamItem): List<ExamItem> {
        val list = load()
        list.add(item)
        save(list)
        return list
    }

    fun delete(memoId: String): List<ExamItem> {
        val list = load().filter { it.memoId != memoId }
        save(list)
        return list
    }

    companion object {
        private const val PREF_NAME = "exam_memo"
        private const val KEY_MEMOS = "exam_memo_list"
    }
}
