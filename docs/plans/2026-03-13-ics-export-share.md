# ICS Export Share Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make exported .ics files shareable to calendar apps by using `content://` URIs, `text/calendar` MIME, and read permissions.

**Architecture:** Keep ical4j export as-is; adjust the share intent to use `FileProvider` and a small `ShareUtils` helper to avoid duplication.

**Tech Stack:** Android (Kotlin), ical4j, FileProvider.

---

### Task 1: Add Share Intent Helper and Update Export Share

**Files:**
- Create: `app/src/main/java/com/stupidtree/hitax/utils/ShareUtils.kt`
- Modify: `app/src/main/java/com/stupidtree/hitax/ui/timetable/detail/TimetableDetailActivity.kt`
- Modify: `app/src/main/java/com/stupidtree/hitax/ui/timetable/manager/TimetableManagerActivity.kt`
- Test: `app/src/test/java/com/stupidtree/hitax/ShareUtilsTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.stupidtree.hitax

import android.content.Intent
import android.net.Uri
import com.stupidtree.hitax.utils.ShareUtils
import org.junit.Assert.*
import org.junit.Test

class ShareUtilsTest {
    @Test
    fun buildShareIntentForUri_setsMimeStreamAndFlags() {
        val uri = Uri.parse("content://com.example/file.ics")
        val intent = ShareUtils.buildShareIntentForUri(uri, "text/calendar")

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/calendar", intent.type)
        assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.stupidtree.hitax.ShareUtilsTest`
Expected: FAIL (ShareUtils not found)

**Step 3: Write minimal implementation**

```kotlin
package com.stupidtree.hitax.utils

import android.content.Intent
import android.net.Uri

object ShareUtils {
    fun buildShareIntentForUri(uri: Uri, mimeType: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
```

Update both activities to use FileProvider + ShareUtils:

```kotlin
val file = File(it.data)
val uri = FileProviderUtils.getUriForFile(getThis(), file)
val shareIntent = ShareUtils.buildShareIntentForUri(uri, "text/calendar")
startActivity(Intent.createChooser(shareIntent, "分享"))
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.stupidtree.hitax.ShareUtilsTest`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/stupidtree/hitax/utils/ShareUtils.kt \
  app/src/test/java/com/stupidtree/hitax/ShareUtilsTest.kt \
  app/src/main/java/com/stupidtree/hitax/ui/timetable/detail/TimetableDetailActivity.kt \
  app/src/main/java/com/stupidtree/hitax/ui/timetable/manager/TimetableManagerActivity.kt

git commit -m "fix: share ICS with calendar MIME and FileProvider"
```
