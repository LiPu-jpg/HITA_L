package com.stupidtree.hitax.utils

import java.util.Locale

object CourseNameUtils {
    private val trailingBracket = Regex("[（(【\\[][^）)】\\]]*[A-Za-z]+[^）)】\\]]*[）)】\\]]\\s*$")
    private val trailingLetter = Regex("[\\s·_.-]*[A-Za-z]+\\d*\\s*$")

    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var name = raw.trim()
        name = name.replace(trailingBracket, "")
        name = name.replace(trailingLetter, "")
        name = name.trim()
        return if (name.isBlank()) raw.trim() else name
    }

    fun normalizeKey(raw: String?): String {
        val normalized = normalize(raw) ?: return ""
        return normalized
            .replace("\\s+".toRegex(), "")
            .lowercase(Locale.ROOT)
    }
}
