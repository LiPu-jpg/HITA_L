package com.stupidtree.hitax.utils

object TermNameFormatter {
    private val yearPrefix = Regex("^\\s*\\d{4}-\\d{4}(?:\\s*学年)?\\s+")

    fun shortTermName(termName: String?, fallback: String?): String {
        val primary = termName?.trim().orEmpty()
        if (primary.isNotEmpty()) return primary
        val raw = fallback?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        return raw.replace(yearPrefix, "").trim()
    }
}
