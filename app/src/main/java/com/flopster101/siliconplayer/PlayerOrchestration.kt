package com.flopster101.siliconplayer

import android.content.Context
import android.widget.Toast
import java.io.File

internal data class NativeTrackSnapshot(
    val decoderName: String?,
    val title: String,
    val artist: String,
    val sampleRateHz: Int,
    val channelCount: Int,
    val bitDepthLabel: String,
    val repeatModeCapabilitiesFlags: Int,
    val playbackCapabilitiesFlags: Int,
    val durationSeconds: Double
)

internal fun readNativeTrackSnapshot(): NativeTrackSnapshot {
    val decoder = NativeBridge.getCurrentDecoderName().takeIf { it.isNotBlank() }
    return NativeTrackSnapshot(
        decoderName = decoder,
        title = NativeBridge.getTrackTitle(),
        artist = NativeBridge.getTrackArtist(),
        sampleRateHz = NativeBridge.getTrackSampleRate(),
        channelCount = NativeBridge.getTrackChannelCount(),
        bitDepthLabel = NativeBridge.getTrackBitDepthLabel(),
        repeatModeCapabilitiesFlags = NativeBridge.getRepeatModeCapabilities(),
        playbackCapabilitiesFlags = NativeBridge.getPlaybackCapabilities(),
        durationSeconds = NativeBridge.getDuration()
    )
}

internal fun syncPlaybackServiceForState(
    context: Context,
    selectedFile: File?,
    sourceId: String?,
    metadataTitle: String,
    metadataArtist: String,
    durationSeconds: Double,
    positionSeconds: Double,
    isPlaying: Boolean
) {
    PlaybackService.syncFromUi(
        context = context,
        path = sourceId ?: selectedFile?.absolutePath,
        title = metadataTitle.ifBlank { selectedFile?.nameWithoutExtension.orEmpty() },
        artist = metadataArtist.ifBlank { "Unknown Artist" },
        durationSeconds = durationSeconds,
        positionSeconds = positionSeconds,
        isPlaying = isPlaying
    )
}

internal fun resolveActiveRepeatMode(
    preferredRepeatMode: RepeatMode,
    repeatModeCapabilitiesFlags: Int,
    includeSubtuneRepeat: Boolean = false,
    includeTrackRepeat: Boolean = true
): RepeatMode {
    return resolveRepeatModeForFlags(
        preferredRepeatMode,
        repeatModeCapabilitiesFlags,
        includeSubtuneRepeat,
        includeTrackRepeat
    )
}

internal fun cycleRepeatModeValue(
    activeRepeatMode: RepeatMode,
    repeatModeCapabilitiesFlags: Int,
    includeSubtuneRepeat: Boolean = false,
    includeTrackRepeat: Boolean = true
): RepeatMode? {
    val modes = availableRepeatModesForFlags(
        flags = repeatModeCapabilitiesFlags,
        includeSubtuneRepeat = includeSubtuneRepeat,
        includeTrackRepeat = includeTrackRepeat
    )
    if (modes.isEmpty()) return null
    val currentIndex = modes.indexOf(activeRepeatMode).let { if (it < 0) 0 else it }
    return modes[(currentIndex + 1) % modes.size]
}

internal fun maybeShowCoreOptionRestartToast(
    context: Context,
    coreName: String,
    selectedFile: File?,
    isPlaying: Boolean,
    policy: CoreOptionApplyPolicy,
    optionLabel: String?
) {
    if (policy != CoreOptionApplyPolicy.RequiresPlaybackRestart) return
    if (!isPlaying || selectedFile == null) return
    val currentDecoderName = NativeBridge.getCurrentDecoderName()
    if (!currentDecoderName.equals(coreName, ignoreCase = true)) return
    val name = optionLabel?.ifBlank { null } ?: "This option"
    Toast.makeText(
        context,
        "$name will apply after restarting playback",
        Toast.LENGTH_SHORT
    ).show()
}

internal fun currentTrackIndexForList(
    selectedFile: File?,
    visiblePlayableFiles: List<File>
): Int {
    val currentPath = selectedFile?.absolutePath ?: return -1
    return visiblePlayableFiles.indexOfFirst { it.absolutePath == currentPath }
}

internal fun adjacentTrackForOffset(
    selectedFile: File?,
    visiblePlayableFiles: List<File>,
    offset: Int
): File? {
    val index = currentTrackIndexForList(selectedFile, visiblePlayableFiles)
    if (index < 0) return null
    val targetIndex = index + offset
    if (targetIndex !in visiblePlayableFiles.indices) return null
    return visiblePlayableFiles[targetIndex]
}

internal fun shouldRestartCurrentTrackOnPrevious(
    previousRestartsAfterThreshold: Boolean,
    hasTrackLoaded: Boolean,
    positionSeconds: Double
): Boolean {
    return previousRestartsAfterThreshold &&
        hasTrackLoaded &&
        positionSeconds > PREVIOUS_RESTART_THRESHOLD_SECONDS
}

internal fun pluginNameForCoreName(coreName: String?): String? {
    return when (coreName?.trim()?.lowercase()) {
        "ffmpeg" -> "FFmpeg"
        "libopenmpt", "openmpt" -> "LibOpenMPT"
        "vgmplay" -> "VGMPlay"
        "game music emu", "libgme", "gme" -> "Game Music Emu"
        "libsidplayfp", "sidplayfp", "sid" -> "LibSIDPlayFP"
        "lazyusf2", "lazyusf", "usf" -> "LazyUSF2"
        "vio2sf", "2sf", "mini2sf" -> "Vio2SF"
        "sc68", "sndh" -> "SC68"
        else -> null
    }
}
