package com.stupidtree.hitax.utils

import android.content.Intent
import android.net.Uri
import android.os.Parcelable

object ShareUtils {
    fun buildShareIntentForUri(uri: Uri, mimeType: String): Intent {
        return buildShareIntentForStream(uri, mimeType)
    }

    internal fun buildShareIntentForStream(stream: Parcelable, mimeType: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, stream)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
