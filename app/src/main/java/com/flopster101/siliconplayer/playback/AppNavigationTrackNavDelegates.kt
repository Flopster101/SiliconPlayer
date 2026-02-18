package com.flopster101.siliconplayer.playback

import com.flopster101.siliconplayer.ManualSourceOpenOptions
import com.flopster101.siliconplayer.NativeBridge
import java.io.File

internal class AppNavigationTrackNavDelegates(
    private val lastStoppedFileProvider: () -> File?,
    private val lastStoppedSourceIdProvider: () -> String?,
    private val onLastStoppedCleared: () -> Unit,
    private val urlOrPathForceCachingProvider: () -> Boolean,
    private val isPlayerExpandedProvider: () -> Boolean,
    private val selectedFileProvider: () -> File?,
    private val visiblePlayableFilesProvider: () -> List<File>,
    private val previousRestartsAfterThresholdProvider: () -> Boolean,
    private val positionSecondsProvider: () -> Double,
    private val onPositionChanged: (Double) -> Unit,
    private val onSyncPlaybackService: () -> Unit,
    private val onApplyTrackSelection: (file: File, autoStart: Boolean, expandOverride: Boolean?) -> Unit,
    private val onApplyManualInputSelection: (String, ManualSourceOpenOptions, Boolean?) -> Unit
) {
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

    fun playAdjacentTrack(offset: Int): Boolean {
        return playAdjacentTrackAction(
            selectedFile = selectedFileProvider(),
            visiblePlayableFiles = visiblePlayableFilesProvider(),
            offset = offset,
            onApplyTrackSelection = onApplyTrackSelection
        )
    }

    fun handlePreviousTrackAction(): Boolean {
        return handlePreviousTrackAction(
            previousRestartsAfterThreshold = previousRestartsAfterThresholdProvider(),
            selectedFile = selectedFileProvider(),
            visiblePlayableFiles = visiblePlayableFilesProvider(),
            positionSeconds = positionSecondsProvider(),
            onRestartCurrent = {
                NativeBridge.seekTo(0.0)
                onPositionChanged(0.0)
                onSyncPlaybackService()
            },
            onPlayAdjacentTrack = { offset -> playAdjacentTrack(offset) }
        )
    }
}
