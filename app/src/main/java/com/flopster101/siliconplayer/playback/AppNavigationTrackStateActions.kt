package com.flopster101.siliconplayer

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import com.flopster101.siliconplayer.playback.ClearedPlaybackState
import com.flopster101.siliconplayer.data.FileRepository
import java.util.Locale
import android.net.Uri
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
    repository: FileRepository,
    cacheRoot: File,
    onSelectedFileChanged: (File) -> Unit,
    onCurrentPlaybackSourceIdChanged: (String) -> Unit,
    onVisiblePlayableFilesChanged: (List<File>) -> Unit,
    onPlayerSurfaceVisibleChanged: (Boolean) -> Unit,
    onPlayerExpandedChanged: (Boolean) -> Unit,
    loadSongVolumeForFile: (String) -> Unit,
    applyNativeTrackSnapshot: (NativeTrackSnapshot) -> Unit,
    refreshSubtuneState: () -> Unit,
    onPositionChanged: (Double) -> Unit,
    onIsPlayingChanged: (Boolean) -> Unit,
    onArtworkBitmapCleared: () -> Unit,
    refreshRepeatModeForTrack: () -> Unit,
    onDeferredPlaybackSeekChanged: (DeferredPlaybackSeek?) -> Unit
) {
    val restoreTarget = resolveSessionRestoreTarget(
        context = context,
        rawSessionPath = prefs.getString(AppPreferenceKeys.SESSION_CURRENT_PATH, null),
        cacheRoot = cacheRoot
    ) ?: return
    onSelectedFileChanged(restoreTarget.displayFile)
    onCurrentPlaybackSourceIdChanged(restoreTarget.sourceId)
    val sourceScheme = Uri.parse(restoreTarget.sourceId).scheme?.lowercase(Locale.ROOT)
    val restoredContextualPlayableFiles = if (sourceScheme == "http" || sourceScheme == "https") {
        emptyList()
    } else {
        loadContextualPlayableFilesForManualSelection(
            repository = repository,
            localFile = restoreTarget.displayFile
        )
    }
    onVisiblePlayableFilesChanged(restoredContextualPlayableFiles)
    onPlayerSurfaceVisibleChanged(false)
    onPlayerExpandedChanged(openExpanded)

    val isLoaded = withContext(Dispatchers.IO) {
        NativeBridge.getTrackSampleRate() > 0
    }
    val resumeCheckpoint = if (isLoaded) {
        null
    } else {
        readSessionResumeCheckpointForSource(prefs, restoreTarget.sourceId)
    }

    if (!isLoaded && resumeCheckpoint != null) {
        applyNativeTrackSnapshot(
            NativeTrackSnapshot(
                decoderName = null,
                title = resumeCheckpoint.title,
                artist = resumeCheckpoint.artist,
                sampleRateHz = 0,
                channelCount = 0,
                bitDepthLabel = "Unknown",
                repeatModeCapabilitiesFlags = resumeCheckpoint.repeatCapabilitiesFlags,
                playbackCapabilitiesFlags = resumeCheckpoint.playbackCapabilitiesFlags,
                durationSeconds = resumeCheckpoint.durationSeconds
            )
        )
        refreshSubtuneState()
        onPositionChanged(resumeCheckpoint.positionSeconds)
        onIsPlayingChanged(false)
        onDeferredPlaybackSeekChanged(
            DeferredPlaybackSeek(
                sourceId = restoreTarget.sourceId,
                positionSeconds = resumeCheckpoint.positionSeconds
            )
        )
        onArtworkBitmapCleared()
        refreshRepeatModeForTrack()
        onPlayerSurfaceVisibleChanged(true)

        val initialized = withContext(Dispatchers.IO) {
            if (!restoreTarget.displayFile.exists() || !restoreTarget.displayFile.isFile) {
                null
            } else {
                loadSongVolumeForFile(restoreTarget.displayFile.absolutePath)
                runWithNativeAudioSession {
                    NativeBridge.loadAudio(restoreTarget.displayFile.absolutePath)
                    readNativeTrackSnapshot()
                }
            }
        }
        if (initialized != null) {
            applyNativeTrackSnapshot(initialized)
            refreshSubtuneState()
            val initializedDuration = initialized.durationSeconds.coerceAtLeast(0.0)
            val restoredPosition = if (initializedDuration > 0.0) {
                resumeCheckpoint.positionSeconds.coerceIn(0.0, initializedDuration)
            } else {
                resumeCheckpoint.positionSeconds
            }
            onPositionChanged(restoredPosition)
            onIsPlayingChanged(false)
            onDeferredPlaybackSeekChanged(
                DeferredPlaybackSeek(
                    sourceId = restoreTarget.sourceId,
                    positionSeconds = restoredPosition
                )
            )
            onArtworkBitmapCleared()
            refreshRepeatModeForTrack()
        }
        return
    }

    onDeferredPlaybackSeekChanged(null)
    val snapshotAndState = withContext(Dispatchers.IO) {
        if (!isLoaded && restoreTarget.displayFile.exists() && restoreTarget.displayFile.isFile) {
            loadSongVolumeForFile(restoreTarget.displayFile.absolutePath)
            runWithNativeAudioSession {
                NativeBridge.loadAudio(restoreTarget.displayFile.absolutePath)
            }
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

private data class SessionResumeCheckpoint(
    val positionSeconds: Double,
    val durationSeconds: Double,
    val title: String,
    val artist: String,
    val playbackCapabilitiesFlags: Int,
    val repeatCapabilitiesFlags: Int
)

private fun readSessionResumeCheckpointForSource(
    prefs: SharedPreferences,
    sourceId: String
): SessionResumeCheckpoint? {
    val storedSourceId = prefs.getString(AppPreferenceKeys.SESSION_RESUME_SOURCE_ID, null)
    if (storedSourceId != sourceId) return null
    val playbackCapabilitiesFlags = prefs.getInt(
        AppPreferenceKeys.SESSION_RESUME_PLAYBACK_CAPABILITIES,
        PLAYBACK_CAP_SEEK
    )
    val durationSeconds = prefs.getFloat(
        AppPreferenceKeys.SESSION_RESUME_DURATION_SECONDS,
        0f
    ).toDouble()
    if (durationSeconds <= 0.0) return null
    val rawPositionSeconds = prefs.getFloat(
        AppPreferenceKeys.SESSION_RESUME_POSITION_SECONDS,
        0f
    ).toDouble()
    if (rawPositionSeconds > durationSeconds + RESUME_POSITION_DURATION_EPSILON_SECONDS) return null
    val positionSeconds = rawPositionSeconds.coerceIn(0.0, durationSeconds)
    val repeatCapabilitiesFlags = prefs.getInt(
        AppPreferenceKeys.SESSION_RESUME_REPEAT_CAPABILITIES,
        REPEAT_CAP_ALL
    )
    val title = prefs.getString(AppPreferenceKeys.SESSION_RESUME_TITLE, null)
        .orEmpty()
        .ifBlank { "Unknown Title" }
    val artist = prefs.getString(AppPreferenceKeys.SESSION_RESUME_ARTIST, null)
        .orEmpty()
        .ifBlank { "Unknown Artist" }
    return SessionResumeCheckpoint(
        positionSeconds = positionSeconds,
        durationSeconds = durationSeconds,
        title = title,
        artist = artist,
        playbackCapabilitiesFlags = playbackCapabilitiesFlags,
        repeatCapabilitiesFlags = repeatCapabilitiesFlags
    )
}

private const val RESUME_POSITION_DURATION_EPSILON_SECONDS = 0.05
