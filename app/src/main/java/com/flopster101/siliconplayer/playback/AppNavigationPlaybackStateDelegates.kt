package com.flopster101.siliconplayer

import android.content.Context
import android.content.SharedPreferences
import java.io.File

internal class AppNavigationPlaybackStateDelegates(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val selectedFileProvider: () -> File?,
    private val onSelectedFileChanged: (File?) -> Unit,
    private val currentPlaybackSourceIdProvider: () -> String?,
    private val onCurrentPlaybackSourceIdChanged: (String?) -> Unit,
    private val isPlayingProvider: () -> Boolean,
    private val lastBrowserLocationIdProvider: () -> String?,
    private val isLocalPlayableFile: (File?) -> Boolean,
    private val metadataTitleProvider: () -> String,
    private val metadataArtistProvider: () -> String,
    private val refreshRepeatModeForTrack: () -> Unit,
    private val refreshSubtuneState: () -> Unit,
    private val addRecentPlayedTrack: (String, String?, String?, String?) -> Unit,
    private val syncPlaybackService: () -> Unit,
    private val readNativeTrackSnapshot: () -> NativeTrackSnapshot,
    private val ignoreCoreVolumeForCurrentSongProvider: () -> Boolean,
    private val onLastUsedCoreNameChanged: (String?) -> Unit,
    private val onPluginVolumeDbChanged: (Float) -> Unit,
    private val onPluginGainChanged: (Float) -> Unit,
    private val onDurationChanged: (Double) -> Unit,
    private val onPositionChanged: (Double) -> Unit,
    private val onIsPlayingChanged: (Boolean) -> Unit,
    private val onSeekInProgressChanged: (Boolean) -> Unit,
    private val onSeekUiBusyChanged: (Boolean) -> Unit,
    private val onSeekStartedAtMsChanged: (Long) -> Unit,
    private val onSeekRequestedAtMsChanged: (Long) -> Unit,
    private val onMetadataTitleChanged: (String) -> Unit,
    private val onMetadataArtistChanged: (String) -> Unit,
    private val onMetadataSampleRateChanged: (Int) -> Unit,
    private val onMetadataChannelCountChanged: (Int) -> Unit,
    private val onMetadataBitDepthLabelChanged: (String) -> Unit,
    private val onSubtuneCountChanged: (Int) -> Unit,
    private val onCurrentSubtuneIndexChanged: (Int) -> Unit,
    private val onSubtuneEntriesCleared: () -> Unit,
    private val onShowSubtuneSelectorDialogChanged: (Boolean) -> Unit,
    private val onRepeatModeCapabilitiesFlagsChanged: (Int) -> Unit,
    private val onPlaybackCapabilitiesFlagsChanged: (Int) -> Unit,
    private val onArtworkBitmapCleared: () -> Unit,
    private val onIgnoreCoreVolumeForSongChanged: (Boolean) -> Unit,
    private val onLastStoppedChanged: (File?, String?) -> Unit,
    private val onStopEngine: () -> Unit
) {
    fun applyCoreOptionWithPolicy(
        coreName: String,
        optionName: String,
        optionValue: String,
        policy: CoreOptionApplyPolicy,
        optionLabel: String? = null
    ) {
        applyCoreOptionWithPolicyAction(
            context,
            coreName = coreName,
            optionName = optionName,
            optionValue = optionValue,
            policy = policy,
            optionLabel = optionLabel,
            selectedFile = selectedFileProvider(),
            isPlaying = isPlayingProvider()
        )
    }

    fun applyNativeTrackSnapshot(snapshot: NativeTrackSnapshot) {
        applyNativeTrackSnapshotAction(
            snapshot = snapshot,
            prefs = prefs,
            ignoreCoreVolumeForCurrentSong = ignoreCoreVolumeForCurrentSongProvider(),
            onLastUsedCoreNameChanged = onLastUsedCoreNameChanged,
            onPluginVolumeDbChanged = onPluginVolumeDbChanged,
            onPluginGainChanged = onPluginGainChanged,
            onMetadataTitleChanged = onMetadataTitleChanged,
            onMetadataArtistChanged = onMetadataArtistChanged,
            onMetadataSampleRateChanged = onMetadataSampleRateChanged,
            onMetadataChannelCountChanged = onMetadataChannelCountChanged,
            onMetadataBitDepthLabelChanged = onMetadataBitDepthLabelChanged,
            onRepeatModeCapabilitiesFlagsChanged = onRepeatModeCapabilitiesFlagsChanged,
            onPlaybackCapabilitiesFlagsChanged = onPlaybackCapabilitiesFlagsChanged,
            onDurationChanged = onDurationChanged
        )
    }

    fun selectSubtune(index: Int): Boolean {
        return selectSubtuneAction(
            context = context,
            index = index,
            selectedFile = selectedFileProvider(),
            currentPlaybackSourceId = currentPlaybackSourceIdProvider(),
            lastBrowserLocationId = lastBrowserLocationIdProvider(),
            isLocalPlayableFile = isLocalPlayableFile,
            metadataTitleProvider = metadataTitleProvider,
            metadataArtistProvider = metadataArtistProvider,
            applyNativeTrackSnapshot = { applyNativeTrackSnapshot(readNativeTrackSnapshot()) },
            refreshRepeatModeForTrack = refreshRepeatModeForTrack,
            refreshSubtuneState = refreshSubtuneState,
            onDurationChanged = onDurationChanged,
            onPositionChanged = onPositionChanged,
            onIsPlayingChanged = onIsPlayingChanged,
            onAddRecentPlayedTrack = addRecentPlayedTrack,
            syncPlaybackService = syncPlaybackService
        )
    }

    fun clearPlaybackMetadataState() {
        clearPlaybackMetadataStateAction(
            onSelectedFileChanged = onSelectedFileChanged,
            onCurrentPlaybackSourceIdChanged = onCurrentPlaybackSourceIdChanged,
            onDurationChanged = onDurationChanged,
            onPositionChanged = onPositionChanged,
            onIsPlayingChanged = onIsPlayingChanged,
            onSeekInProgressChanged = onSeekInProgressChanged,
            onSeekUiBusyChanged = onSeekUiBusyChanged,
            onSeekStartedAtMsChanged = onSeekStartedAtMsChanged,
            onSeekRequestedAtMsChanged = onSeekRequestedAtMsChanged,
            onMetadataTitleChanged = onMetadataTitleChanged,
            onMetadataArtistChanged = onMetadataArtistChanged,
            onMetadataSampleRateChanged = onMetadataSampleRateChanged,
            onMetadataChannelCountChanged = onMetadataChannelCountChanged,
            onMetadataBitDepthLabelChanged = onMetadataBitDepthLabelChanged,
            onSubtuneCountChanged = onSubtuneCountChanged,
            onCurrentSubtuneIndexChanged = onCurrentSubtuneIndexChanged,
            onSubtuneEntriesCleared = onSubtuneEntriesCleared,
            onShowSubtuneSelectorDialogChanged = onShowSubtuneSelectorDialogChanged,
            onRepeatModeCapabilitiesFlagsChanged = onRepeatModeCapabilitiesFlagsChanged,
            onPlaybackCapabilitiesFlagsChanged = onPlaybackCapabilitiesFlagsChanged,
            onArtworkBitmapCleared = onArtworkBitmapCleared,
            onIgnoreCoreVolumeForSongChanged = onIgnoreCoreVolumeForSongChanged
        )
    }

    fun resetAndOptionallyKeepLastTrack(keepLastTrack: Boolean) {
        resetAndOptionallyKeepLastTrackAction(
            keepLastTrack = keepLastTrack,
            selectedFile = selectedFileProvider(),
            currentPlaybackSourceId = currentPlaybackSourceIdProvider(),
            onLastStoppedChanged = onLastStoppedChanged,
            onStopEngine = onStopEngine,
            clearPlaybackMetadataState = ::clearPlaybackMetadataState
        )
    }
}
