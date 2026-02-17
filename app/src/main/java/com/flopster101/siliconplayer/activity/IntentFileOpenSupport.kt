package com.flopster101.siliconplayer

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import java.io.File

internal fun resolveFileFromViewIntent(contentResolver: ContentResolver, intent: Intent?): File? {
    if (intent?.action != Intent.ACTION_VIEW) return null
    val uri = intent.data ?: return null
    return try {
        val file = when (uri.scheme) {
            "file" -> File(uri.path ?: return null)
            "content" -> {
                val path = queryRealPathFromUri(contentResolver, uri)
                if (path != null) File(path) else null
            }
            else -> null
        }
        if (file?.exists() == true) file else null
    } catch (_: Exception) {
        null
    }
}

private fun queryRealPathFromUri(contentResolver: ContentResolver, uri: Uri): String? {
    return try {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex("_data")
                if (columnIndex >= 0) {
                    cursor.getString(columnIndex)
                } else {
                    uri.path
                }
            } else {
                uri.path
            }
        }
    } catch (_: Exception) {
        uri.path
    }
}
