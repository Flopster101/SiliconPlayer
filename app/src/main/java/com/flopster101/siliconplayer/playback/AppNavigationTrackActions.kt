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
import com.flopster101.siliconplayer.samePath
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
    currentPlaybackSourceId: String?,
    visiblePlayableFiles: List<File>,
    visiblePlayableSourceIds: List<String>,
    urlOrPathForceCaching: Boolean,
    isPlayerExpanded: Boolean,
    offset: Int,
    playlistWrapNavigation: Boolean,
    wrapOverride: Boolean? = null,
    onPlaylistWrapped: (Int) -> Unit,
    onApplyTrackSelection: (file: File, autoStart: Boolean, expandOverride: Boolean?) -> Unit,
    onApplyManualInputSelection: (rawInput: String, options: ManualSourceOpenOptions, expandOverride: Boolean?) -> Unit
): Boolean {
    val hasLocalPlaylist = visiblePlayableFiles.isNotEmpty()
    val playlistSize = if (hasLocalPlaylist) {
        visiblePlayableFiles.size
    } else {
        visiblePlayableSourceIds.size
    }
    if (playlistSize <= 0) return false
    val currentIndex = if (hasLocalPlaylist) {
        currentTrackIndexForList(
            selectedFile = selectedFile,
            visiblePlayableFiles = visiblePlayableFiles
        )
    } else {
        val activeSourceId = currentPlaybackSourceId ?: selectedFile?.absolutePath
        if (activeSourceId.isNullOrBlank()) {
            -1
        } else {
            visiblePlayableSourceIds.indexOfFirst { sourceId ->
                samePath(sourceId, activeSourceId)
            }
        }
    }
    if (currentIndex < 0) return false

    val rawTargetIndex = currentIndex + offset
    val shouldWrap = wrapOverride ?: playlistWrapNavigation
    val targetIndex = if (shouldWrap) {
        val wrappedTargetIndex = ((rawTargetIndex % playlistSize) + playlistSize) % playlistSize
        val wrapped = rawTargetIndex !in (0 until playlistSize) && playlistSize > 1
        if (wrapped) {
            onPlaylistWrapped(offset)
        }
        wrappedTargetIndex
    } else {
        rawTargetIndex
    }

    if (hasLocalPlaylist) {
        val target = visiblePlayableFiles.getOrNull(targetIndex) ?: return false
        onApplyTrackSelection(target, true, null)
    } else {
        val targetSourceId = visiblePlayableSourceIds.getOrNull(targetIndex) ?: return false
        onApplyManualInputSelection(
            targetSourceId,
            ManualSourceOpenOptions(forceCaching = urlOrPathForceCaching),
            isPlayerExpanded
        )
    }
    return true
}

internal fun handlePreviousTrackAction(
    previousRestartsAfterThreshold: Boolean,
    playlistWrapNavigation: Boolean,
    selectedFile: File?,
    currentPlaybackSourceId: String?,
    visiblePlayableFiles: List<File>,
    visiblePlayableSourceIds: List<String>,
    positionSeconds: Double,
    onRestartCurrent: () -> Unit,
    onPlayAdjacentTrack: (Int) -> Boolean
): Boolean {
    val hasLocalPlaylist = visiblePlayableFiles.isNotEmpty()
    val activePlaylistSize = if (hasLocalPlaylist) {
        visiblePlayableFiles.size
    } else {
        visiblePlayableSourceIds.size
    }
    val hasTrackLoaded = selectedFile != null
    val currentTrackIndex = if (hasLocalPlaylist) {
        currentTrackIndexForList(
            selectedFile = selectedFile,
            visiblePlayableFiles = visiblePlayableFiles
        )
    } else {
        val activeSourceId = currentPlaybackSourceId ?: selectedFile?.absolutePath
        if (activeSourceId.isNullOrBlank()) {
            -1
        } else {
            visiblePlayableSourceIds.indexOfFirst { sourceId ->
                samePath(sourceId, activeSourceId)
            }
        }
    }
    val hasPreviousTrack = if (playlistWrapNavigation) {
        currentTrackIndex >= 0 && activePlaylistSize > 0
    } else {
        currentTrackIndex > 0 && activePlaylistSize > 0
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
