package com.flopster101.siliconplayer.playback

import com.flopster101.siliconplayer.ManualSourceOpenOptions
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.RemotePlayableSourceIdsHolder
import java.io.File

internal class AppNavigationTrackNavDelegates(
    private val lastStoppedFileProvider: () -> File?,
    private val lastStoppedSourceIdProvider: () -> String?,
    private val onLastStoppedCleared: () -> Unit,
    private val urlOrPathForceCachingProvider: () -> Boolean,
    private val isPlayerExpandedProvider: () -> Boolean,
    private val selectedFileProvider: () -> File?,
    private val currentPlaybackSourceIdProvider: () -> String?,
    private val visiblePlayableFilesProvider: () -> List<File>,
    private val visiblePlayableSourceIdsProvider: () -> List<String>,
    private val playlistWrapNavigationProvider: () -> Boolean,
    private val previousRestartsAfterThresholdProvider: () -> Boolean,
    private val positionSecondsProvider: () -> Double,
    private val onPositionChanged: (Double) -> Unit,
    private val onSyncPlaybackService: () -> Unit,
    private val onPlaylistWrapped: (Int) -> Unit,
    private val onApplyTrackSelection: (file: File, autoStart: Boolean, expandOverride: Boolean?) -> Unit,
    private val onApplyManualInputSelection: (String, ManualSourceOpenOptions, Boolean?) -> Unit
) {
    private fun resolvedVisiblePlayableSourceIds(): List<String> {
        val currentIds = visiblePlayableSourceIdsProvider()
        return if (currentIds.isNotEmpty()) currentIds else RemotePlayableSourceIdsHolder.lastNonEmpty
    }

    fun resumeLastStoppedTrack(autoStart: Boolean = true): Boolean {
        return resumeLastStoppedTrackAction(
            lastStoppedFile = lastStoppedFileProvider(),
            lastStoppedSourceId = lastStoppedSourceIdProvider(),
            autoStart = autoStart,
            urlOrPathForceCaching = urlOrPathForceCachingProvider(),
            isPlayerExpanded = isPlayerExpandedProvider(),
            onApplyTrackSelection = onApplyTrackSelection,
            onApplyManualInputSelection = onApplyManualInputSelection,
            onClearLastStopped = onLastStoppedCleared
        )
    }

    fun playAdjacentTrack(
        offset: Int,
        notifyWrap: Boolean = true,
        wrapOverride: Boolean? = null
    ): Boolean {
        return playAdjacentTrackAction(
            selectedFile = selectedFileProvider(),
            currentPlaybackSourceId = currentPlaybackSourceIdProvider(),
            visiblePlayableFiles = visiblePlayableFilesProvider(),
            visiblePlayableSourceIds = resolvedVisiblePlayableSourceIds(),
            urlOrPathForceCaching = urlOrPathForceCachingProvider(),
            isPlayerExpanded = isPlayerExpandedProvider(),
            offset = offset,
            playlistWrapNavigation = playlistWrapNavigationProvider(),
            wrapOverride = wrapOverride,
            onPlaylistWrapped = if (notifyWrap) onPlaylistWrapped else { _ -> },
            onApplyTrackSelection = onApplyTrackSelection,
            onApplyManualInputSelection = onApplyManualInputSelection
        )
    }

    fun handlePreviousTrackAction(): Boolean {
        return handlePreviousTrackAction(
            previousRestartsAfterThreshold = previousRestartsAfterThresholdProvider(),
            playlistWrapNavigation = playlistWrapNavigationProvider(),
            selectedFile = selectedFileProvider(),
            currentPlaybackSourceId = currentPlaybackSourceIdProvider(),
            visiblePlayableFiles = visiblePlayableFilesProvider(),
            visiblePlayableSourceIds = resolvedVisiblePlayableSourceIds(),
            positionSeconds = positionSecondsProvider(),
            onRestartCurrent = {
                NativeBridge.seekTo(0.0)
                onPositionChanged(0.0)
                onSyncPlaybackService()
            },
            onPlayAdjacentTrack = { offset -> playAdjacentTrack(offset, notifyWrap = true) }
        )
    }
}
