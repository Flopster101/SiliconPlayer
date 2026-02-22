package com.flopster101.siliconplayer.playback

import android.content.Context
import com.flopster101.siliconplayer.ManualSourceOpenOptions
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.PreviousTrackAction
import com.flopster101.siliconplayer.ResumeTarget
import com.flopster101.siliconplayer.adjacentTrackForOffset
import com.flopster101.siliconplayer.resolvePreviousTrackAction
import com.flopster101.siliconplayer.resolveResumeTarget
import com.flopster101.siliconplayer.data.resolveArchiveSourceToMountedFile
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val trackSelectionLoadMutex = Mutex()

internal suspend fun applyTrackSelectionAction(
    context: Context,
    file: File,
    autoStart: Boolean,
    expandOverride: Boolean?,
    sourceIdOverride: String?,
    locationIdOverride: String?,
    initialSeekSeconds: Double?,
    useSongVolumeLookup: Boolean,
    onResetPlayback: () -> Unit,
    onSelectedFileChanged: (File) -> Unit,
    onCurrentPlaybackSourceIdChanged: (String) -> Unit,
    onPlayerSurfaceVisibleChanged: (Boolean) -> Unit,
    loadSongVolumeForFile: (String) -> Unit,
    onSongVolumeDbChanged: (Float) -> Unit,
    onSongGainChanged: (Float) -> Unit,
    applyNativeTrackSnapshot: () -> Unit,
    refreshSubtuneState: () -> Unit,
    onPositionChanged: (Double) -> Unit,
    onArtworkBitmapCleared: () -> Unit,
    refreshRepeatModeForTrack: () -> Unit,
    onAddRecentPlayedTrack: (path: String, locationId: String?, title: String?, artist: String?) -> Unit,
    metadataTitleProvider: () -> String,
    metadataArtistProvider: () -> String,
    onStartEngine: () -> Unit,
    onIsPlayingChanged: (Boolean) -> Unit,
    scheduleRecentTrackMetadataRefresh: (String, String?) -> Unit,
    onPlayerExpandedChanged: (Boolean) -> Unit,
    syncPlaybackService: () -> Unit
) {
    onResetPlayback()
    val sourceId = sourceIdOverride ?: file.absolutePath
    val loadFile = resolveArchiveSourceToMountedFile(context, sourceId) ?: file
    onSelectedFileChanged(loadFile)
    onCurrentPlaybackSourceIdChanged(sourceId)
    onPlayerSurfaceVisibleChanged(true)
    if (useSongVolumeLookup) {
        loadSongVolumeForFile(loadFile.absolutePath)
    } else {
        onSongVolumeDbChanged(0f)
        onSongGainChanged(0f)
    }
    withContext(Dispatchers.IO) {
        trackSelectionLoadMutex.withLock {
            NativeBridge.loadAudio(loadFile.absolutePath)
        }
    }
    coroutineContext.ensureActive()
    applyNativeTrackSnapshot()
    refreshSubtuneState()
    val loadedDurationSeconds = NativeBridge.getDuration().coerceAtLeast(0.0)
    val shouldApplyInitialSeek = initialSeekSeconds != null &&
        loadedDurationSeconds > 0.0
    val resolvedPositionSeconds = if (shouldApplyInitialSeek) {
        initialSeekSeconds
            ?.coerceIn(0.0, loadedDurationSeconds)
            ?.also { seekTargetSeconds ->
                if (seekTargetSeconds > 0.0) {
                    NativeBridge.seekTo(seekTargetSeconds)
                }
            } ?: 0.0
    } else {
        0.0
    }
    onPositionChanged(resolvedPositionSeconds)
    onArtworkBitmapCleared()
    refreshRepeatModeForTrack()
    if (autoStart) {
        onAddRecentPlayedTrack(sourceId, locationIdOverride, metadataTitleProvider(), metadataArtistProvider())
        onStartEngine()
        onIsPlayingChanged(true)
        scheduleRecentTrackMetadataRefresh(sourceId, locationIdOverride)
    }
    expandOverride?.let { onPlayerExpandedChanged(it) }
    syncPlaybackService()
}

internal fun resumeLastStoppedTrackAction(
    lastStoppedFile: File?,
    lastStoppedSourceId: String?,
    autoStart: Boolean,
    urlOrPathForceCaching: Boolean,
    isPlayerExpanded: Boolean,
    onApplyTrackSelection: (file: File, autoStart: Boolean, expandOverride: Boolean?) -> Unit,
    onApplyManualInputSelection: (String, ManualSourceOpenOptions, Boolean?) -> Unit,
    onClearLastStopped: () -> Unit
): Boolean {
    return when (val target = resolveResumeTarget(lastStoppedFile, lastStoppedSourceId)) {
        is ResumeTarget.LocalFile -> {
            onApplyTrackSelection(target.file, autoStart, null)
            true
        }

        is ResumeTarget.SourceId -> {
            onApplyManualInputSelection(
                target.sourceId,
                ManualSourceOpenOptions(forceCaching = urlOrPathForceCaching),
                isPlayerExpanded
            )
            true
        }

        null -> {
            onClearLastStopped()
            false
        }
    }
}

internal fun playAdjacentTrackAction(
    selectedFile: File?,
    visiblePlayableFiles: List<File>,
    offset: Int,
    onApplyTrackSelection: (file: File, autoStart: Boolean, expandOverride: Boolean?) -> Unit
): Boolean {
    val target = adjacentTrackForOffset(
        selectedFile = selectedFile,
        visiblePlayableFiles = visiblePlayableFiles,
        offset = offset
    ) ?: return false
    onApplyTrackSelection(target, true, null)
    return true
}

internal fun handlePreviousTrackAction(
    previousRestartsAfterThreshold: Boolean,
    selectedFile: File?,
    visiblePlayableFiles: List<File>,
    positionSeconds: Double,
    onRestartCurrent: () -> Unit,
    onPlayAdjacentTrack: (Int) -> Boolean
): Boolean {
    val hasTrackLoaded = selectedFile != null
    val hasPreviousTrack = adjacentTrackForOffset(
        selectedFile = selectedFile,
        visiblePlayableFiles = visiblePlayableFiles,
        offset = -1
    ) != null
    return when (
        resolvePreviousTrackAction(
            previousRestartsAfterThreshold = previousRestartsAfterThreshold,
            hasTrackLoaded = hasTrackLoaded,
            positionSeconds = positionSeconds,
            hasPreviousTrack = hasPreviousTrack
        )
    ) {
        PreviousTrackAction.RestartCurrent -> {
            onRestartCurrent()
            true
        }

        PreviousTrackAction.PlayPreviousTrack -> {
            onPlayAdjacentTrack(-1)
        }

        PreviousTrackAction.NoAction -> {
            false
        }
    }
}
