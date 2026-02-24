package com.flopster101.siliconplayer

import android.content.SharedPreferences
import java.io.File

internal enum class PreviousTrackAction {
    RestartCurrent,
    PlayPreviousTrack,
    NoAction
}

internal sealed class ResumeTarget {
    data class LocalFile(val file: File) : ResumeTarget()
    data class SourceId(val sourceId: String) : ResumeTarget()
}

internal data class SubtuneState(
    val count: Int,
    val currentIndex: Int
)

internal data class NativeSubtuneCursor(
    val count: Int,
    val index: Int
)

internal data class DeferredPlaybackSeek(
    val sourceId: String,
    val positionSeconds: Double
)

internal data class SnapshotApplicationResult(
    val decoderName: String?,
    val pluginVolumeDb: Float?,
    val title: String,
    val artist: String,
    val sampleRateHz: Int,
    val channelCount: Int,
    val bitDepthLabel: String,
    val repeatModeCapabilitiesFlags: Int,
    val playbackCapabilitiesFlags: Int,
    val durationSeconds: Double
)

internal data class SubtuneSelectionResult(
    val success: Boolean,
    val snapshot: NativeTrackSnapshot? = null,
    val durationSeconds: Double = 0.0,
    val isPlaying: Boolean = false,
    val sourceId: String? = null
)

internal fun readSubtuneState(selectedFile: File?): SubtuneState {
    if (selectedFile == null) {
        return SubtuneState(count = 0, currentIndex = 0)
    }
    val count = NativeBridge.getSubtuneCount().coerceAtLeast(0)
    val currentIndex = if (count <= 0) {
        0
    } else {
        NativeBridge.getCurrentSubtuneIndex().coerceIn(0, count - 1)
    }
    return SubtuneState(count = count, currentIndex = currentIndex)
}

internal fun readNativeSubtuneCursor(): NativeSubtuneCursor {
    val count = NativeBridge.getSubtuneCount().coerceAtLeast(1)
    val index = NativeBridge.getCurrentSubtuneIndex().coerceIn(0, count - 1)
    return NativeSubtuneCursor(count = count, index = index)
}

internal fun hasNativeSubtuneCursorChanged(
    nativeCursor: NativeSubtuneCursor,
    currentSubtuneCount: Int,
    currentSubtuneIndex: Int
): Boolean {
    return nativeCursor.count != currentSubtuneCount || nativeCursor.index != currentSubtuneIndex
}

internal fun readSubtuneEntries(subtuneCount: Int): List<SubtuneEntry> {
    if (subtuneCount <= 0) return emptyList()
    return (0 until subtuneCount).map { index ->
        val title = NativeBridge.getSubtuneTitle(index).trim()
        val artist = NativeBridge.getSubtuneArtist(index).trim()
        val durationSeconds = NativeBridge.getSubtuneDurationSeconds(index)
        SubtuneEntry(
            index = index,
            title = title.ifBlank { "Subtune ${index + 1}" },
            artist = artist,
            durationSeconds = durationSeconds
        )
    }
}

@Suppress("UNUSED_PARAMETER")
internal fun resolveTrackRepeatMode(
    selectedFile: File?,
    durationSeconds: Double,
    subtuneCount: Int,
    preferredRepeatMode: RepeatMode,
    repeatModeCapabilitiesFlags: Int
): RepeatMode {
    val allowTrackRepeat = true
    val allowSubtuneRepeat = subtuneCount > 1
    return resolveActiveRepeatMode(
        preferredRepeatMode = preferredRepeatMode,
        repeatModeCapabilitiesFlags = repeatModeCapabilitiesFlags,
        includeSubtuneRepeat = allowSubtuneRepeat,
        includeTrackRepeat = allowTrackRepeat
    )
}

internal fun shouldApplyRepeatModeNow(
    selectedFile: File?,
    currentPlaybackSourceId: String?,
    seekInProgress: Boolean
): Boolean {
    val hasActiveSource = selectedFile != null || !currentPlaybackSourceId.isNullOrBlank()
    return hasActiveSource && !seekInProgress
}

@Suppress("UNUSED_PARAMETER")
internal fun resolveNextRepeatMode(
    playbackCapabilitiesFlags: Int,
    seekInProgress: Boolean,
    selectedFile: File?,
    durationSeconds: Double,
    subtuneCount: Int,
    activeRepeatMode: RepeatMode,
    repeatModeCapabilitiesFlags: Int
): RepeatMode? {
    if (!supportsLiveRepeatMode(playbackCapabilitiesFlags)) return null
    if (seekInProgress) return null
    val allowTrackRepeat = true
    val allowSubtuneRepeat = subtuneCount > 1
    return cycleRepeatModeValue(
        activeRepeatMode = activeRepeatMode,
        repeatModeCapabilitiesFlags = repeatModeCapabilitiesFlags,
        includeSubtuneRepeat = allowSubtuneRepeat,
        includeTrackRepeat = allowTrackRepeat
    )
}

internal fun snapshotAppearsValid(snapshot: NativeTrackSnapshot): Boolean {
    return snapshot.sampleRateHz > 0 ||
        snapshot.durationSeconds > 0.0 ||
        snapshot.title.isNotBlank() ||
        snapshot.artist.isNotBlank()
}

internal fun shouldPollTrackMetadata(
    metadataPollElapsedMs: Long,
    metadataTitle: String,
    metadataArtist: String
): Boolean {
    return metadataPollElapsedMs >= 540L || metadataTitle.isBlank() || metadataArtist.isBlank()
}

internal fun resolvePlaybackServiceSourceId(
    selectedFile: File?,
    currentPlaybackSourceId: String?,
    cacheRoot: File
): String? {
    return currentPlaybackSourceId
        ?: selectedFile
            ?.takeIf { it.absolutePath.startsWith(cacheRoot.absolutePath) }
            ?.let { sourceIdForCachedFileName(cacheRoot, it.name) }
}

internal fun currentTrackListIndex(
    selectedFile: File?,
    visiblePlayableFiles: List<File>
): Int {
    return currentTrackIndexForList(
        selectedFile = selectedFile,
        visiblePlayableFiles = visiblePlayableFiles
    )
}

internal fun canNavigateToPreviousTrack(
    selectedFile: File?,
    visiblePlayableFiles: List<File>,
    playlistWrapNavigation: Boolean
): Boolean {
    val currentIndex = currentTrackListIndex(selectedFile, visiblePlayableFiles)
    return if (playlistWrapNavigation) {
        currentIndex >= 0
    } else {
        currentIndex > 0
    }
}

internal fun canNavigateToNextTrack(
    selectedFile: File?,
    visiblePlayableFiles: List<File>,
    playlistWrapNavigation: Boolean
): Boolean {
    val currentIndex = currentTrackListIndex(selectedFile, visiblePlayableFiles)
    return if (playlistWrapNavigation) {
        currentIndex >= 0
    } else {
        currentIndex in 0 until visiblePlayableFiles.lastIndex
    }
}

internal fun tryPlayAdjacentTrack(
    selectedFile: File?,
    visiblePlayableFiles: List<File>,
    offset: Int,
    onPlay: (File) -> Unit
): Boolean {
    val target = adjacentTrackForOffset(
        selectedFile = selectedFile,
        visiblePlayableFiles = visiblePlayableFiles,
        offset = offset
    ) ?: return false
    onPlay(target)
    return true
}

internal fun resolvePreviousTrackAction(
    previousRestartsAfterThreshold: Boolean,
    hasTrackLoaded: Boolean,
    positionSeconds: Double,
    hasPreviousTrack: Boolean
): PreviousTrackAction {
    val shouldRestartCurrent = shouldRestartCurrentTrackOnPrevious(
        previousRestartsAfterThreshold = previousRestartsAfterThreshold,
        hasTrackLoaded = hasTrackLoaded,
        positionSeconds = positionSeconds
    )
    if (shouldRestartCurrent) return PreviousTrackAction.RestartCurrent
    if (hasPreviousTrack) return PreviousTrackAction.PlayPreviousTrack
    if (hasTrackLoaded) return PreviousTrackAction.RestartCurrent
    return PreviousTrackAction.NoAction
}

internal fun resolveResumeTarget(
    lastStoppedFile: File?,
    lastStoppedSourceId: String?
): ResumeTarget? {
    val resumableFile = lastStoppedFile?.takeIf { it.exists() && it.isFile }
    if (resumableFile != null) return ResumeTarget.LocalFile(resumableFile)
    val sourceId = lastStoppedSourceId?.takeIf { it.isNotBlank() } ?: return null
    return ResumeTarget.SourceId(sourceId)
}

internal fun buildSnapshotApplicationResult(
    snapshot: NativeTrackSnapshot,
    prefs: SharedPreferences
): SnapshotApplicationResult {
    val decoderName = snapshot.decoderName
    val pluginVolumeDb = decoderName?.let { readPluginVolumeForDecoder(prefs, it) }
    return SnapshotApplicationResult(
        decoderName = decoderName,
        pluginVolumeDb = pluginVolumeDb,
        title = snapshot.title,
        artist = snapshot.artist,
        sampleRateHz = snapshot.sampleRateHz,
        channelCount = snapshot.channelCount,
        bitDepthLabel = snapshot.bitDepthLabel,
        repeatModeCapabilitiesFlags = snapshot.repeatModeCapabilitiesFlags,
        playbackCapabilitiesFlags = snapshot.playbackCapabilitiesFlags,
        durationSeconds = snapshot.durationSeconds
    )
}

internal fun selectSubtuneAndReadState(
    index: Int,
    selectedFile: File?,
    currentPlaybackSourceId: String?
): SubtuneSelectionResult {
    if (selectedFile == null) return SubtuneSelectionResult(success = false)
    if (!NativeBridge.selectSubtune(index)) return SubtuneSelectionResult(success = false)
    return SubtuneSelectionResult(
        success = true,
        snapshot = readNativeTrackSnapshot(),
        durationSeconds = NativeBridge.getDuration(),
        isPlaying = NativeBridge.isEnginePlaying(),
        sourceId = currentPlaybackSourceId ?: selectedFile.absolutePath
    )
}
