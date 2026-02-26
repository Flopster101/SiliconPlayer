package com.flopster101.siliconplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun rememberRemoteNextTrackPreloadCanceller(
    context: android.content.Context,
    isPlaying: Boolean,
    selectedFile: File?,
    currentPlaybackSourceId: String?,
    preloadNextCachedRemoteTrack: Boolean,
    playlistWrapNavigation: Boolean,
    urlOrPathForceCaching: Boolean,
    visiblePlayableFiles: List<File>,
    visiblePlayableSourceIds: List<String>
): () -> Unit {
    val appScope = rememberCoroutineScope()
    var remotePreloadJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(
        isPlaying,
        selectedFile?.absolutePath,
        currentPlaybackSourceId,
        preloadNextCachedRemoteTrack,
        playlistWrapNavigation,
        urlOrPathForceCaching,
        visiblePlayableSourceIds
    ) {
        remotePreloadJob?.cancel()
        remotePreloadJob = null
        RemotePreloadUiStateHolder.current = null
        if (!isPlaying || !preloadNextCachedRemoteTrack) return@LaunchedEffect
        val nextSourceId = resolveNextRemoteSourceIdForPreload(
            selectedFile = selectedFile,
            currentPlaybackSourceId = currentPlaybackSourceId,
            visiblePlayableFiles = visiblePlayableFiles,
            visiblePlayableSourceIds = visiblePlayableSourceIds,
            playlistWrapNavigation = playlistWrapNavigation
        ) ?: return@LaunchedEffect
        remotePreloadJob = appScope.launch {
            try {
                RemotePreloadUiStateHolder.current = RemoteLoadUiState(
                    sourceId = nextSourceId,
                    phase = RemoteLoadPhase.Connecting,
                    indeterminate = true
                )
                preloadRemoteSourceToCache(
                    context = context,
                    sourceId = nextSourceId,
                    allowHttpCaching = urlOrPathForceCaching
                ) { state ->
                    RemotePreloadUiStateHolder.current = state
                }
            } finally {
                RemotePreloadUiStateHolder.current = null
                remotePreloadJob = null
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            remotePreloadJob?.cancel()
            remotePreloadJob = null
            RemotePreloadUiStateHolder.current = null
        }
    }

    return {
        remotePreloadJob?.cancel()
        remotePreloadJob = null
        RemotePreloadUiStateHolder.current = null
    }
}
