package com.flopster101.siliconplayer

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import com.flopster101.siliconplayer.playback.ClearedPlaybackState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun applyNativeTrackSnapshotAction(
    snapshot: NativeTrackSnapshot,
    prefs: SharedPreferences,
    ignoreCoreVolumeForCurrentSong: Boolean,
    onLastUsedCoreNameChanged: (String) -> Unit,
    onPluginVolumeDbChanged: (Float) -> Unit,
    onPluginGainChanged: (Float) -> Unit,
    onMetadataTitleChanged: (String) -> Unit,
    onMetadataArtistChanged: (String) -> Unit,
    onMetadataSampleRateChanged: (Int) -> Unit,
    onMetadataChannelCountChanged: (Int) -> Unit,
    onMetadataBitDepthLabelChanged: (String) -> Unit,
    onRepeatModeCapabilitiesFlagsChanged: (Int) -> Unit,
    onPlaybackCapabilitiesFlagsChanged: (Int) -> Unit,
    onDurationChanged: (Double) -> Unit
) {
    val applied = buildSnapshotApplicationResult(snapshot, prefs)
    applied.decoderName?.let { decoderName ->
        onLastUsedCoreNameChanged(decoderName)
        applied.pluginVolumeDb?.let { decoderPluginVolumeDb ->
            onPluginVolumeDbChanged(decoderPluginVolumeDb)
            onPluginGainChanged(if (ignoreCoreVolumeForCurrentSong) 0f else decoderPluginVolumeDb)
        }
    }
    onMetadataTitleChanged(applied.title)
    onMetadataArtistChanged(applied.artist)
    onMetadataSampleRateChanged(applied.sampleRateHz)
    onMetadataChannelCountChanged(applied.channelCount)
    onMetadataBitDepthLabelChanged(applied.bitDepthLabel)
    onRepeatModeCapabilitiesFlagsChanged(applied.repeatModeCapabilitiesFlags)
    onPlaybackCapabilitiesFlagsChanged(applied.playbackCapabilitiesFlags)
    onDurationChanged(applied.durationSeconds)
}

internal fun selectSubtuneAction(
    context: Context,
    index: Int,
    selectedFile: File?,
    currentPlaybackSourceId: String?,
    lastBrowserLocationId: String?,
    isLocalPlayableFile: (File?) -> Boolean,
    metadataTitleProvider: () -> String,
    metadataArtistProvider: () -> String,
    applyNativeTrackSnapshot: (NativeTrackSnapshot) -> Unit,
    refreshRepeatModeForTrack: () -> Unit,
    refreshSubtuneState: () -> Unit,
    onDurationChanged: (Double) -> Unit,
    onPositionChanged: (Double) -> Unit,
    onIsPlayingChanged: (Boolean) -> Unit,
    onAddRecentPlayedTrack: (String, String?, String?, String?) -> Unit,
    syncPlaybackService: () -> Unit
): Boolean {
    val result = selectSubtuneAndReadState(
        index = index,
        selectedFile = selectedFile,
        currentPlaybackSourceId = currentPlaybackSourceId
    )
    if (!result.success) {
        Toast.makeText(context, "Unable to switch subtune", Toast.LENGTH_SHORT).show()
        return false
    }
    val snapshot = result.snapshot ?: return false
    applyNativeTrackSnapshot(snapshot)
    onPositionChanged(0.0)
    onDurationChanged(result.durationSeconds)
    onIsPlayingChanged(result.isPlaying)
    refreshRepeatModeForTrack()
    refreshSubtuneState()
    val sourceId = result.sourceId
    if (sourceId != null) {
        onAddRecentPlayedTrack(
            sourceId,
            if (isLocalPlayableFile(selectedFile)) lastBrowserLocationId else null,
            metadataTitleProvider(),
            metadataArtistProvider()
        )
    }
    syncPlaybackService()
    return true
}

internal fun clearPlaybackMetadataStateAction(
    onSelectedFileChanged: (File?) -> Unit,
    onCurrentPlaybackSourceIdChanged: (String?) -> Unit,
    onDurationChanged: (Double) -> Unit,
    onPositionChanged: (Double) -> Unit,
    onIsPlayingChanged: (Boolean) -> Unit,
    onSeekInProgressChanged: (Boolean) -> Unit,
    onSeekUiBusyChanged: (Boolean) -> Unit,
    onSeekStartedAtMsChanged: (Long) -> Unit,
    onSeekRequestedAtMsChanged: (Long) -> Unit,
    onMetadataTitleChanged: (String) -> Unit,
    onMetadataArtistChanged: (String) -> Unit,
    onMetadataSampleRateChanged: (Int) -> Unit,
    onMetadataChannelCountChanged: (Int) -> Unit,
    onMetadataBitDepthLabelChanged: (String) -> Unit,
    onSubtuneCountChanged: (Int) -> Unit,
    onCurrentSubtuneIndexChanged: (Int) -> Unit,
    onSubtuneEntriesCleared: () -> Unit,
    onShowSubtuneSelectorDialogChanged: (Boolean) -> Unit,
    onRepeatModeCapabilitiesFlagsChanged: (Int) -> Unit,
    onPlaybackCapabilitiesFlagsChanged: (Int) -> Unit,
    onArtworkBitmapCleared: () -> Unit,
    onIgnoreCoreVolumeForSongChanged: (Boolean) -> Unit
) {
    val cleared = ClearedPlaybackState()
    onSelectedFileChanged(null)
    onCurrentPlaybackSourceIdChanged(null)
    onDurationChanged(cleared.duration)
    onPositionChanged(cleared.position)
    onIsPlayingChanged(cleared.isPlaying)
    onSeekInProgressChanged(cleared.seekInProgress)
    onSeekUiBusyChanged(cleared.seekUiBusy)
    onSeekStartedAtMsChanged(cleared.seekStartedAtMs)
    onSeekRequestedAtMsChanged(cleared.seekRequestedAtMs)
    onMetadataTitleChanged(cleared.metadataTitle)
    onMetadataArtistChanged(cleared.metadataArtist)
    onMetadataSampleRateChanged(cleared.metadataSampleRate)
    onMetadataChannelCountChanged(cleared.metadataChannelCount)
    onMetadataBitDepthLabelChanged(cleared.metadataBitDepthLabel)
    onSubtuneCountChanged(cleared.subtuneCount)
    onCurrentSubtuneIndexChanged(cleared.currentSubtuneIndex)
    onSubtuneEntriesCleared()
    onShowSubtuneSelectorDialogChanged(false)
    onRepeatModeCapabilitiesFlagsChanged(cleared.repeatModeCapabilitiesFlags)
    onPlaybackCapabilitiesFlagsChanged(cleared.playbackCapabilitiesFlags)
    onArtworkBitmapCleared()
    onIgnoreCoreVolumeForSongChanged(false)
}

internal fun resetAndOptionallyKeepLastTrackAction(
    keepLastTrack: Boolean,
    selectedFile: File?,
    currentPlaybackSourceId: String?,
    onLastStoppedChanged: (File?, String?) -> Unit,
    onStopEngine: () -> Unit,
    clearPlaybackMetadataState: () -> Unit
) {
    if (keepLastTrack && (selectedFile != null || currentPlaybackSourceId != null)) {
        onLastStoppedChanged(selectedFile, currentPlaybackSourceId ?: selectedFile?.absolutePath)
    }
    onStopEngine()
    clearPlaybackMetadataState()
}

internal suspend fun restorePlayerStateFromSessionAndNativeAction(
    context: Context,
    openExpanded: Boolean,
    prefs: SharedPreferences,
    cacheRoot: File,
    onSelectedFileChanged: (File) -> Unit,
    onCurrentPlaybackSourceIdChanged: (String) -> Unit,
    onPlayerSurfaceVisibleChanged: (Boolean) -> Unit,
    onPlayerExpandedChanged: (Boolean) -> Unit,
    loadSongVolumeForFile: (String) -> Unit,
    applyNativeTrackSnapshot: (NativeTrackSnapshot) -> Unit,
    refreshSubtuneState: () -> Unit,
    onPositionChanged: (Double) -> Unit,
    onIsPlayingChanged: (Boolean) -> Unit,
    onArtworkBitmapCleared: () -> Unit,
    refreshRepeatModeForTrack: () -> Unit
) {
    val restoreTarget = resolveSessionRestoreTarget(
        context = context,
        rawSessionPath = prefs.getString(AppPreferenceKeys.SESSION_CURRENT_PATH, null),
        cacheRoot = cacheRoot
    ) ?: return
    onSelectedFileChanged(restoreTarget.displayFile)
    onCurrentPlaybackSourceIdChanged(restoreTarget.sourceId)
    onPlayerSurfaceVisibleChanged(false)
    onPlayerExpandedChanged(openExpanded)

    val snapshotAndState = withContext(Dispatchers.IO) {
        val isLoaded = NativeBridge.getTrackSampleRate() > 0
        if (!isLoaded && restoreTarget.displayFile.exists() && restoreTarget.displayFile.isFile) {
            loadSongVolumeForFile(restoreTarget.displayFile.absolutePath)
            NativeBridge.loadAudio(restoreTarget.displayFile.absolutePath)
        }
        Triple(
            readNativeTrackSnapshot(),
            NativeBridge.getPosition(),
            NativeBridge.isEnginePlaying()
        )
    }

    applyNativeTrackSnapshot(snapshotAndState.first)
    refreshSubtuneState()
    onPositionChanged(snapshotAndState.second)
    onIsPlayingChanged(snapshotAndState.third)
    onArtworkBitmapCleared()
    refreshRepeatModeForTrack()
    onPlayerSurfaceVisibleChanged(true)
}
