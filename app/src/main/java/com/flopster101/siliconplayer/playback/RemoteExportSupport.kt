package com.flopster101.siliconplayer

import android.content.Context
import com.flopster101.siliconplayer.session.ExportFileItem
import java.io.File
import kotlinx.coroutines.CancellationException

internal sealed interface RemoteExportRequest {
    val sourceId: String
    val preferredFileName: String
}

internal data class HttpRemoteExportRequest(
    override val sourceId: String,
    val requestUrl: String,
    override val preferredFileName: String
) : RemoteExportRequest

internal data class SmbRemoteExportRequest(
    override val sourceId: String,
    val smbSpec: SmbSourceSpec,
    override val preferredFileName: String
) : RemoteExportRequest

internal class RemoteExportCancelledException(
    message: String = "Cancelled"
) : CancellationException(message)

internal suspend fun prepareRemoteExportFile(
    context: Context,
    request: RemoteExportRequest,
    onStatus: suspend (RemoteLoadUiState) -> Unit = {}
): Result<ExportFileItem> {
    val cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR)
    val existing = findExistingCachedFileForSource(cacheRoot, request.sourceId)
    if (existing != null) {
        existing.setLastModified(System.currentTimeMillis())
        return Result.success(
            ExportFileItem(
                sourceFile = existing,
                displayNameOverride = sanitizeRemoteLeafName(request.preferredFileName)
                    ?: stripRemoteCacheHashPrefix(existing.name)
            )
        )
    }

    val downloaded = when (request) {
        is HttpRemoteExportRequest -> {
            downloadRemoteUrlToCache(
                context = context,
                url = request.sourceId,
                requestUrl = request.requestUrl,
                onStatus = onStatus
            )
        }

        is SmbRemoteExportRequest -> {
            downloadSmbSourceToCache(
                context = context,
                sourceId = request.sourceId,
                spec = request.smbSpec,
                onStatus = onStatus
            )
        }
    }

    val downloadedFile = downloaded.file
        ?: return Result.failure(
            if (downloaded.cancelled) {
                RemoteExportCancelledException(downloaded.errorMessage ?: "Cancelled")
            } else {
                IllegalStateException(downloaded.errorMessage ?: "Failed to cache remote file")
            }
        )
    downloadedFile.setLastModified(System.currentTimeMillis())
    return Result.success(
        ExportFileItem(
            sourceFile = downloadedFile,
            displayNameOverride = sanitizeRemoteLeafName(request.preferredFileName)
                ?: stripRemoteCacheHashPrefix(downloadedFile.name)
        )
    )
}
