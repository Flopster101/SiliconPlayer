package com.flopster101.siliconplayer

import android.webkit.MimeTypeMap
import java.util.Locale

internal enum class FilePreviewKind {
    Text,
    Image
}

internal fun detectFilePreviewKind(name: String): FilePreviewKind? {
    val extension = inferredPrimaryExtensionForName(name)
        ?.lowercase(Locale.ROOT)
        ?.trim()
        ?: return null
    if (extension in IMAGE_PREVIEW_EXTENSIONS) {
        return FilePreviewKind.Image
    }
    if (extension in TEXT_PREVIEW_EXTENSIONS) {
        return FilePreviewKind.Text
    }
    val mimeType = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(extension)
        .orEmpty()
        .lowercase(Locale.ROOT)
    return when {
        mimeType.startsWith("image/") -> FilePreviewKind.Image
        mimeType.startsWith("text/") -> FilePreviewKind.Text
        mimeType in EXTRA_TEXT_PREVIEW_MIME_TYPES -> FilePreviewKind.Text
        else -> null
    }
}

private val IMAGE_PREVIEW_EXTENSIONS = setOf(
    "png", "jpg", "jpeg", "webp", "gif", "bmp", "heif", "heic"
)

private val TEXT_PREVIEW_EXTENSIONS = setOf(
    "txt", "log", "md", "json", "xml", "yaml", "yml", "ini", "cfg", "conf",
    "csv", "tsv", "nfo", "sfv", "cue", "m3u", "m3u8", "pls", "lrc", "srt",
    "ass", "ssa"
)

private val EXTRA_TEXT_PREVIEW_MIME_TYPES = setOf(
    "application/json",
    "application/xml",
    "application/x-yaml",
    "application/javascript"
)
