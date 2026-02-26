package com.flopster101.siliconplayer

import android.content.Context
import java.io.File

internal fun resolveNextRemoteSourceIdForPreload(
    selectedFile: File?,
    currentPlaybackSourceId: String?,
    visiblePlayableFiles: List<File>,
    visiblePlayableSourceIds: List<String>,
    playlistWrapNavigation: Boolean
): String? {
    if (visiblePlayableFiles.isNotEmpty()) return null
    if (visiblePlayableSourceIds.isEmpty()) return null
    val activeSourceId = currentPlaybackSourceId ?: selectedFile?.absolutePath ?: return null
    val currentIndex = visiblePlayableSourceIds.indexOfFirst { sourceId ->
        samePath(sourceId, activeSourceId)
    }
    if (currentIndex < 0) return null
    val rawTargetIndex = currentIndex + 1
    val targetIndex = if (playlistWrapNavigation) {
        ((rawTargetIndex % visiblePlayableSourceIds.size) + visiblePlayableSourceIds.size) %
            visiblePlayableSourceIds.size
    } else {
        if (rawTargetIndex !in visiblePlayableSourceIds.indices) return null
        rawTargetIndex
    }
    if (targetIndex == currentIndex) return null
    return visiblePlayableSourceIds.getOrNull(targetIndex)
}

internal suspend fun preloadRemoteSourceToCache(
    context: Context,
    sourceId: String,
    allowHttpCaching: Boolean,
    onStatus: suspend (RemoteLoadUiState) -> Unit = {}
): Boolean {
    val resolved = resolveManualSourceInput(sourceId) ?: return false
    return when (resolved.type) {
        ManualSourceType.Smb -> {
            val smbSpec = resolved.smbSpec ?: return false
            val result = downloadSmbSourceToCache(
                context = context,
                sourceId = resolved.sourceId,
                spec = smbSpec,
                onStatus = onStatus
            )
            result.file != null && !result.cancelled
        }

        ManualSourceType.RemoteUrl -> {
            if (!allowHttpCaching) return false
            val result = downloadRemoteUrlToCache(
                context = context,
                url = resolved.sourceId,
                requestUrl = resolved.requestUrl,
                onStatus = onStatus
            )
            result.file != null && !result.cancelled
        }

        else -> false
    }
}
