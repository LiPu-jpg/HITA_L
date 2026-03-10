package com.stupidtree.hitax.utils

object CourseCodeUtils {
    private val standardPattern = Regex("([A-Za-z]{4}\\d{4})")

    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val match = standardPattern.find(raw.trim()) ?: return null
        return match.groupValues.getOrNull(1)
    }
}
