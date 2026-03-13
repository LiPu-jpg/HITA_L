# Term Name Shortening Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Display only the concise term label (e.g. `2026春季`) everywhere the app shows a term name.

**Architecture:** Add a small formatter utility that normalizes term labels, then update all term-name display paths and timetable naming to call it. Keep changes display-only and avoid data migrations.

**Tech Stack:** Kotlin, Android, JUnit (app unit tests)

---

### Task 1: Add formatter utility with unit tests

**Files:**
- Create: `app/src/main/java/com/stupidtree/hitax/utils/TermNameFormatter.kt`
- Create: `app/src/test/java/com/stupidtree/hitax/utils/TermNameFormatterTest.kt`

**Step 1: Write the failing test**

```kotlin
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
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.stupidtree.hitax.utils.TermNameFormatterTest`

Expected: FAIL (class or methods not found).

**Step 3: Write minimal implementation**

```kotlin
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
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.stupidtree.hitax.utils.TermNameFormatterTest`

Expected: PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/com/stupidtree/hitax/utils/TermNameFormatter.kt \
  app/src/test/java/com/stupidtree/hitax/utils/TermNameFormatterTest.kt

git commit -m "feat: add term name formatter"
```

---

### Task 2: Use formatter in term display helpers

**Files:**
- Modify: `app/src/main/java/com/stupidtree/hitax/ui/eas/imp/ImportTimetableActivity.kt`
- Modify: `app/src/main/java/com/stupidtree/hitax/ui/eas/exam/ExamActivity.kt`
- Modify: `app/src/main/java/com/stupidtree/hitax/ui/eas/score/ScoreInquiryActivity.kt`
- Modify: `app/src/main/java/com/stupidtree/hitax/ui/eas/classroom/EmptyClassroomActivity.kt`

**Step 1: Update display helper functions**

Replace existing `getDisplayTermName(...)` logic with:

```kotlin
return TermNameFormatter.shortTermName(term.termName, term.name)
```

Import the new formatter in each file.

**Step 2: Build to verify**

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

**Step 3: Commit**

```bash
git add app/src/main/java/com/stupidtree/hitax/ui/eas/imp/ImportTimetableActivity.kt \
  app/src/main/java/com/stupidtree/hitax/ui/eas/exam/ExamActivity.kt \
  app/src/main/java/com/stupidtree/hitax/ui/eas/score/ScoreInquiryActivity.kt \
  app/src/main/java/com/stupidtree/hitax/ui/eas/classroom/EmptyClassroomActivity.kt

git commit -m "feat: shorten term labels in EAS screens"
```

---

### Task 3: Shorten timetable name generation

**Files:**
- Modify: `app/src/main/java/com/stupidtree/hitax/data/repository/EASRepository.kt`

**Step 1: Update timetable name builder**

Replace the current `buildTimetableName(...)` logic with a call to the formatter:

```kotlin
private fun buildTimetableName(term: TermItem): String {
    return TermNameFormatter.shortTermName(term.termName, term.name)
}
```

**Step 2: Build to verify**

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

**Step 3: Commit**

```bash
git add app/src/main/java/com/stupidtree/hitax/data/repository/EASRepository.kt

git commit -m "feat: shorten timetable name"
```

---

### Task 4: Final verification

**Step 1: Run unit tests**

Run: `./gradlew :app:testDebugUnitTest`

Expected: PASS.

**Step 2: Summarize changes**

List updated files and confirm term labels display as `2026春季` in all relevant screens.

