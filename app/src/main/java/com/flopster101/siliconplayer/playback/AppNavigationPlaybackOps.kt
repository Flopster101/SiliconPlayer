package com.flopster101.siliconplayer.playback

import android.content.Context
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.PLAYBACK_CAP_LIVE_REPEAT_MODE
import com.flopster101.siliconplayer.PLAYBACK_CAP_RELIABLE_DURATION
import com.flopster101.siliconplayer.PLAYBACK_CAP_SEEK
import com.flopster101.siliconplayer.REMOTE_SOURCE_CACHE_DIR
import com.flopster101.siliconplayer.REPEAT_CAP_ALL
import com.flopster101.siliconplayer.RepeatMode
import com.flopster101.siliconplayer.resolveNextRepeatMode
import com.flopster101.siliconplayer.resolvePlaybackServiceSourceId
import com.flopster101.siliconplayer.resolveTrackRepeatMode
import com.flopster101.siliconplayer.shouldApplyRepeatModeNow
import com.flopster101.siliconplayer.showRepeatModeToast
import com.flopster101.siliconplayer.syncPlaybackServiceForState
import com.flopster101.siliconplayer.readSubtuneState
import java.io.File

internal data class SubtuneUiState(
    val count: Int,
    val currentIndex: Int,
    val shouldShowSelector: Boolean
)

internal fun syncPlaybackServiceFromUiState(
    context: Context,
    selectedFile: File?,
    currentPlaybackSourceId: String?,
    metadataTitle: String,
    metadataArtist: String,
    durationSeconds: Double,
    positionSeconds: Double,
    isPlaying: Boolean
) {
    val cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR)
    val resolvedSourceId = resolvePlaybackServiceSourceId(
        selectedFile = selectedFile,
        currentPlaybackSourceId = currentPlaybackSourceId,
        cacheRoot = cacheRoot
    )
    syncPlaybackServiceForState(
        context = context,
        selectedFile = selectedFile,
        sourceId = resolvedSourceId,
        metadataTitle = metadataTitle,
        metadataArtist = metadataArtist,
        durationSeconds = durationSeconds,
        positionSeconds = positionSeconds,
        isPlaying = isPlaying
    )
}

internal fun resolveSubtuneUiState(selectedFile: File?): SubtuneUiState {
    val state = readSubtuneState(selectedFile)
    return if (state.count <= 0) {
        SubtuneUiState(count = 0, currentIndex = 0, shouldShowSelector = false)
    } else {
        SubtuneUiState(count = state.count, currentIndex = state.currentIndex, shouldShowSelector = true)
    }
}

internal fun resolveAndMaybeApplyRepeatMode(
    selectedFile: File?,
    durationSeconds: Double,
    subtuneCount: Int,
    preferredRepeatMode: RepeatMode,
    repeatModeCapabilitiesFlags: Int,
    currentPlaybackSourceId: String?,
    seekInProgress: Boolean,
    applyRepeatMode: (RepeatMode) -> Unit
): RepeatMode {
    val activeRepeatMode = resolveTrackRepeatMode(
        selectedFile = selectedFile,
        durationSeconds = durationSeconds,
        subtuneCount = subtuneCount,
        preferredRepeatMode = preferredRepeatMode,
        repeatModeCapabilitiesFlags = repeatModeCapabilitiesFlags
    )
    if (shouldApplyRepeatModeNow(selectedFile, currentPlaybackSourceId, seekInProgress)) {
        applyRepeatMode(activeRepeatMode)
    }
    return activeRepeatMode
}

internal fun cycleRepeatModeWithCapabilities(
    context: Context,
    playbackCapabilitiesFlags: Int,
    seekInProgress: Boolean,
    selectedFile: File?,
    durationSeconds: Double,
    subtuneCount: Int,
    activeRepeatMode: RepeatMode,
    repeatModeCapabilitiesFlags: Int,
    applyRepeatMode: (RepeatMode) -> Unit
): RepeatMode? {
    val next = resolveNextRepeatMode(
        playbackCapabilitiesFlags = playbackCapabilitiesFlags,
        seekInProgress = seekInProgress,
        selectedFile = selectedFile,
        durationSeconds = durationSeconds,
        subtuneCount = subtuneCount,
        activeRepeatMode = activeRepeatMode,
        repeatModeCapabilitiesFlags = repeatModeCapabilitiesFlags
    ) ?: return null
    applyRepeatMode(next)
    showRepeatModeToast(context, next)
    return next
}

internal data class ClearedPlaybackState(
    val duration: Double = 0.0,
    val position: Double = 0.0,
    val isPlaying: Boolean = false,
    val seekInProgress: Boolean = false,
    val seekUiBusy: Boolean = false,
    val seekStartedAtMs: Long = 0L,
    val seekRequestedAtMs: Long = 0L,
    val metadataTitle: String = "",
    val metadataArtist: String = "",
    val metadataSampleRate: Int = 0,
    val metadataChannelCount: Int = 0,
    val metadataBitDepthLabel: String = "Unknown",
    val subtuneCount: Int = 0,
    val currentSubtuneIndex: Int = 0,
    val repeatModeCapabilitiesFlags: Int = REPEAT_CAP_ALL,
    val playbackCapabilitiesFlags: Int = PLAYBACK_CAP_SEEK or
        PLAYBACK_CAP_RELIABLE_DURATION or
        PLAYBACK_CAP_LIVE_REPEAT_MODE
)

