package com.flopster101.siliconplayer

import android.content.Context
import android.content.SharedPreferences
import com.flopster101.siliconplayer.restorePlayerStateFromSessionAndNativeAction
import com.flopster101.siliconplayer.playback.applyTrackSelectionAction
import java.io.File

internal class AppNavigationTrackLoadDelegates(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val cacheRootProvider: () -> File,
    private val lastBrowserLocationIdProvider: () -> String?,
    private val onResetPlayback: () -> Unit,
    private val onSelectedFileChanged: (File?) -> Unit,
    private val onCurrentPlaybackSourceIdChanged: (String?) -> Unit,
    private val onPlayerSurfaceVisibleChanged: (Boolean) -> Unit,
    private val loadSongVolumeForFile: (String) -> Unit,
    private val onSongVolumeDbChanged: (Float) -> Unit,
    private val onSongGainChanged: (Float) -> Unit,
    private val readNativeTrackSnapshot: () -> NativeTrackSnapshot,
    private val applyNativeTrackSnapshot: (NativeTrackSnapshot) -> Unit,
    private val refreshSubtuneState: () -> Unit,
    private val onPositionChanged: (Double) -> Unit,
    private val onArtworkBitmapCleared: () -> Unit,
    private val onIsPlayingChanged: (Boolean) -> Unit,
    private val refreshRepeatModeForTrack: () -> Unit,
    private val onAddRecentPlayedTrack: (String, String?, String?, String?) -> Unit,
    private val metadataTitleProvider: () -> String,
    private val metadataArtistProvider: () -> String,
    private val onStartEngine: () -> Unit,
    private val scheduleRecentTrackMetadataRefresh: (String, String?) -> Unit,
    private val onPlayerExpandedChanged: (Boolean) -> Unit,
    private val syncPlaybackService: () -> Unit
) {
    fun applyTrackSelection(
        file: File,
        autoStart: Boolean,
        expandOverride: Boolean? = null,
        sourceIdOverride: String? = null,
        locationIdOverride: String? = lastBrowserLocationIdProvider(),
        useSongVolumeLookup: Boolean = true
    ) {
        applyTrackSelectionAction(
            file = file,
            autoStart = autoStart,
            expandOverride = expandOverride,
            sourceIdOverride = sourceIdOverride,
            locationIdOverride = locationIdOverride,
            useSongVolumeLookup = useSongVolumeLookup,
            onResetPlayback = onResetPlayback,
            onSelectedFileChanged = onSelectedFileChanged,
            onCurrentPlaybackSourceIdChanged = onCurrentPlaybackSourceIdChanged,
            onPlayerSurfaceVisibleChanged = onPlayerSurfaceVisibleChanged,
            loadSongVolumeForFile = loadSongVolumeForFile,
            onSongVolumeDbChanged = onSongVolumeDbChanged,
            onSongGainChanged = onSongGainChanged,
            applyNativeTrackSnapshot = { applyNativeTrackSnapshot(readNativeTrackSnapshot()) },
            refreshSubtuneState = refreshSubtuneState,
            onPositionChanged = onPositionChanged,
            onArtworkBitmapCleared = onArtworkBitmapCleared,
            refreshRepeatModeForTrack = refreshRepeatModeForTrack,
            onAddRecentPlayedTrack = onAddRecentPlayedTrack,
            metadataTitleProvider = metadataTitleProvider,
            metadataArtistProvider = metadataArtistProvider,
            onStartEngine = onStartEngine,
            onIsPlayingChanged = onIsPlayingChanged,
            scheduleRecentTrackMetadataRefresh = scheduleRecentTrackMetadataRefresh,
            onPlayerExpandedChanged = onPlayerExpandedChanged,
            syncPlaybackService = syncPlaybackService
        )
    }

    fun restorePlayerStateFromSessionAndNative(openExpanded: Boolean) {
        restorePlayerStateFromSessionAndNativeAction(
            openExpanded = openExpanded,
            prefs = prefs,
            cacheRoot = cacheRootProvider(),
            onSelectedFileChanged = onSelectedFileChanged,
            onCurrentPlaybackSourceIdChanged = onCurrentPlaybackSourceIdChanged,
            onPlayerSurfaceVisibleChanged = onPlayerSurfaceVisibleChanged,
            onPlayerExpandedChanged = onPlayerExpandedChanged,
            loadSongVolumeForFile = loadSongVolumeForFile,
            applyNativeTrackSnapshot = applyNativeTrackSnapshot,
            refreshSubtuneState = refreshSubtuneState,
            onPositionChanged = onPositionChanged,
            onIsPlayingChanged = onIsPlayingChanged,
            onArtworkBitmapCleared = onArtworkBitmapCleared,
            refreshRepeatModeForTrack = refreshRepeatModeForTrack
        )
    }
}
