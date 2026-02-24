package com.flopster101.siliconplayer.playback

import android.content.Context
import com.flopster101.siliconplayer.ManualSourceOpenOptions
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.PreviousTrackAction
import com.flopster101.siliconplayer.ResumeTarget
import com.flopster101.siliconplayer.NativeTrackSnapshot
import com.flopster101.siliconplayer.currentTrackIndexForList
import com.flopster101.siliconplayer.readNativeTrackSnapshot
import com.flopster101.siliconplayer.runWithNativeAudioSession
import com.flopster101.siliconplayer.resolvePreviousTrackAction
import com.flopster101.siliconplayer.resolveResumeTarget
import com.flopster101.siliconplayer.data.resolveArchiveSourceToMountedFile
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun applyTrackSelectionAction(
    context: Context,
    file: File,
    autoStart: Boolean,
    wasPlayingBeforeSelection: Boolean,
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
    applyNativeTrackSnapshot: (NativeTrackSnapshot) -> Unit,
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
    NativeBridge.setFastTrackSwitchStartupHint(wasPlayingBeforeSelection)
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
    val nativeSnapshot = runWithNativeAudioSession {
        NativeBridge.loadAudio(loadFile.absolutePath)
        readNativeTrackSnapshot()
    }
    coroutineContext.ensureActive()
    applyNativeTrackSnapshot(nativeSnapshot)
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
    playlistWrapNavigation: Boolean,
    onPlaylistWrapped: (Int) -> Unit,
    onApplyTrackSelection: (file: File, autoStart: Boolean, expandOverride: Boolean?) -> Unit
): Boolean {
    val playlistSize = visiblePlayableFiles.size
    if (playlistSize <= 0) return false
    val currentIndex = currentTrackIndexForList(
        selectedFile = selectedFile,
        visiblePlayableFiles = visiblePlayableFiles
    )
    if (currentIndex < 0) return false

    val rawTargetIndex = currentIndex + offset
    val targetIndex = if (playlistWrapNavigation) {
        val wrappedTargetIndex = ((rawTargetIndex % playlistSize) + playlistSize) % playlistSize
        val wrapped = rawTargetIndex !in visiblePlayableFiles.indices && playlistSize > 1
        if (wrapped) {
            onPlaylistWrapped(offset)
        }
        wrappedTargetIndex
    } else {
        rawTargetIndex
    }

    val target = visiblePlayableFiles.getOrNull(targetIndex) ?: return false
    onApplyTrackSelection(target, true, null)
    return true
}

internal fun handlePreviousTrackAction(
    previousRestartsAfterThreshold: Boolean,
    playlistWrapNavigation: Boolean,
    selectedFile: File?,
    visiblePlayableFiles: List<File>,
    positionSeconds: Double,
    onRestartCurrent: () -> Unit,
    onPlayAdjacentTrack: (Int) -> Boolean
): Boolean {
    val hasTrackLoaded = selectedFile != null
    val currentTrackIndex = currentTrackIndexForList(
        selectedFile = selectedFile,
        visiblePlayableFiles = visiblePlayableFiles
    )
    val hasPreviousTrack = if (playlistWrapNavigation) {
        currentTrackIndex >= 0 && visiblePlayableFiles.isNotEmpty()
    } else {
        currentTrackIndex > 0 && visiblePlayableFiles.isNotEmpty()
    }
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
            val moved = onPlayAdjacentTrack(-1)
            if (moved) {
                true
            } else if (hasTrackLoaded) {
                onRestartCurrent()
                true
            } else {
                false
            }
        }

        PreviousTrackAction.NoAction -> {
            false
        }
    }
}
