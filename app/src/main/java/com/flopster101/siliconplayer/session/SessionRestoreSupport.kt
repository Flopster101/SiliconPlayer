package com.flopster101.siliconplayer

import android.net.Uri
import java.io.File
import java.util.Locale

internal data class SessionRestoreTarget(
    val sourceId: String,
    val displayFile: File
)

internal fun resolveSessionRestoreTarget(
    rawSessionPath: String?,
    cacheRoot: File
): SessionRestoreTarget? {
    var sourcePath = rawSessionPath?.trim()?.takeIf { it.isNotBlank() } ?: return null

    if (sourcePath.startsWith("/virtual/remote/")) {
        val legacyName = sourcePath.substringAfterLast('/').trim()
        val recovered = sourceIdForCachedFileName(cacheRoot, legacyName)
        if (!recovered.isNullOrBlank()) {
            sourcePath = recovered
        } else {
            return null
        }
    }

    if (sourcePath.startsWith(cacheRoot.absolutePath)) {
        val recovered = sourceIdForCachedFileName(cacheRoot, File(sourcePath).name)
        if (!recovered.isNullOrBlank()) {
            sourcePath = recovered
        }
    }

    val normalizedSource = normalizeSourceIdentity(sourcePath) ?: sourcePath
    val sourceUri = Uri.parse(normalizedSource)
    val sourceScheme = sourceUri.scheme?.lowercase(Locale.ROOT)
    val isRemoteSource = sourceScheme == "http" || sourceScheme == "https"

    val displayFile = if (isRemoteSource) {
        findExistingCachedFileForSource(cacheRoot, normalizedSource)
            ?: File("/virtual/remote/${remoteFilenameHintFromUri(sourceUri) ?: "remote"}")
    } else {
        val localPath = when (sourceScheme) {
            "file" -> sourceUri.path
            else -> normalizedSource
        }
        localPath?.let { File(it) } ?: File(normalizedSource)
    }

    return SessionRestoreTarget(
        sourceId = normalizedSource,
        displayFile = displayFile
    )
}
