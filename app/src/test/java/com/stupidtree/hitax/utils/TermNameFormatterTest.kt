package com.stupidtree.hitax.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class TermNameFormatterTest {
    @Test
    fun `uses termName when present`() {
        assertEquals("2026春季", TermNameFormatter.shortTermName("2026春季", "2025-2026 2026春季"))
    }

    @Test
    fun `strips year range prefix from fallback`() {
        assertEquals("2026春季", TermNameFormatter.shortTermName("", "2025-2026 2026春季"))
        assertEquals("2026春季", TermNameFormatter.shortTermName(null, "2025-2026学年 2026春季"))
    }

    @Test
    fun `passes through fallback when no prefix`() {
        assertEquals("2026春季", TermNameFormatter.shortTermName(null, "2026春季"))
    }

    @Test
    fun `handles blanks`() {
        assertEquals("", TermNameFormatter.shortTermName(" ", ""))
    }
}
