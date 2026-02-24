package com.flopster101.siliconplayer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

internal sealed class DirectStreamOpenResult {
    data class Success(val snapshot: NativeTrackSnapshot) : DirectStreamOpenResult()
    data class Fallback(val reason: String) : DirectStreamOpenResult()
}

internal data class ManualRemoteOpenSuccess(
    val displayFile: File,
    val sourceId: String,
    val snapshot: NativeTrackSnapshot
)

internal sealed class ManualRemoteOpenResult {
    data class Success(val value: ManualRemoteOpenSuccess) : ManualRemoteOpenResult()
    object DownloadCancelled : ManualRemoteOpenResult()
    data class Failed(val reason: String) : ManualRemoteOpenResult()
}

internal suspend fun tryOpenDirectStreamForManualSource(
    requestUrl: String,
    timeoutMs: Long = 20_000L
): DirectStreamOpenResult {
    return try {
        val snapshot = withContext(Dispatchers.IO) {
            withTimeout(timeoutMs) {
                runWithNativeAudioSession {
                    NativeBridge.loadAudio(requestUrl)
                    readNativeTrackSnapshot()
                }
            }
        }
        if (snapshotAppearsValid(snapshot)) {
            DirectStreamOpenResult.Success(snapshot)
        } else {
            DirectStreamOpenResult.Fallback("direct streaming returned no playable metadata")
        }
    } catch (_: TimeoutCancellationException) {
        DirectStreamOpenResult.Fallback("direct streaming timed out")
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (t: Throwable) {
        DirectStreamOpenResult.Fallback(
            "direct streaming error (${t::class.java.simpleName}: ${t.message ?: "unknown"})"
        )
    }
}

internal fun buildCacheDownloadFailureReason(
    streamingFailureReason: String?,
    downloadErrorMessage: String?
): String {
    return buildString {
        streamingFailureReason?.let {
            append(it)
            append("; ")
        }
        append("cache download failed")
        downloadErrorMessage?.let { append(" ($it)") }
    }
}

internal suspend fun executeManualRemoteOpen(
    resolved: ManualSourceResolution,
    forceCaching: Boolean,
    cacheRoot: File,
    selectedFileAbsolutePath: String?,
    urlCacheMaxTracks: Int,
    urlCacheMaxBytes: Long,
    onStatus: suspend (RemoteLoadUiState) -> Unit,
    downloadToCache: suspend (
        sourceId: String,
        requestUrl: String,
        onStatus: suspend (RemoteLoadUiState) -> Unit
    ) -> RemoteDownloadResult
): ManualRemoteOpenResult {
    var streamingFailureReason: String? = null
    if (!forceCaching) {
        when (val directResult = tryOpenDirectStreamForManualSource(resolved.requestUrl)) {
            is DirectStreamOpenResult.Success -> {
                return ManualRemoteOpenResult.Success(
                    ManualRemoteOpenSuccess(
                        displayFile = resolved.displayFile ?: File("/virtual/remote/stream"),
                        sourceId = resolved.sourceId,
                        snapshot = directResult.snapshot
                    )
                )
            }

            is DirectStreamOpenResult.Fallback -> {
                streamingFailureReason = directResult.reason
            }
        }
    }

    val downloadResult = downloadToCache(
        resolved.sourceId,
        resolved.requestUrl,
        onStatus
    )
    if (downloadResult.cancelled) {
        return ManualRemoteOpenResult.DownloadCancelled
    }
    val cachedFile = downloadResult.file
    if (cachedFile == null) {
        return ManualRemoteOpenResult.Failed(
            buildCacheDownloadFailureReason(
                streamingFailureReason = streamingFailureReason,
                downloadErrorMessage = downloadResult.errorMessage
            )
        )
    }

    withContext(Dispatchers.IO) {
        val protectedPaths = buildSet {
            add(cachedFile.absolutePath)
            selectedFileAbsolutePath?.let { add(it) }
        }
        enforceRemoteCacheLimits(
            cacheRoot = cacheRoot,
            maxTracks = urlCacheMaxTracks,
            maxBytes = urlCacheMaxBytes,
            protectedPaths = protectedPaths
        )
    }

    onStatus(
        RemoteLoadUiState(
            sourceId = resolved.sourceId,
            phase = RemoteLoadPhase.Opening,
            downloadedBytes = cachedFile.length(),
            totalBytes = cachedFile.length(),
            percent = 100,
            indeterminate = false
        )
    )

    val cachedSnapshot = withContext(Dispatchers.IO) {
        withTimeout(20_000L) {
            runWithNativeAudioSession {
                NativeBridge.loadAudio(cachedFile.absolutePath)
                readNativeTrackSnapshot()
            }
        }
    }
    if (!snapshotAppearsValid(cachedSnapshot)) {
        return ManualRemoteOpenResult.Failed("cached file opened but returned no playable metadata")
    }

    return ManualRemoteOpenResult.Success(
        ManualRemoteOpenSuccess(
            displayFile = cachedFile,
            sourceId = resolved.sourceId,
            snapshot = cachedSnapshot
        )
    )
}
