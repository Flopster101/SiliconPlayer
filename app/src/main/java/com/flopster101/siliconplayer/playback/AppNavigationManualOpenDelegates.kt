package com.flopster101.siliconplayer

import android.content.Context
import com.flopster101.siliconplayer.data.FileRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

internal class AppNavigationManualOpenDelegates(
    private val context: Context,
    private val appScope: CoroutineScope,
    private val repository: FileRepository,
    private val storageDescriptors: List<StorageDescriptor>,
    private val openPlayerOnTrackSelectProvider: () -> Boolean,
    private val activeRepeatModeProvider: () -> RepeatMode,
    private val selectedFileAbsolutePathProvider: () -> String?,
    private val urlCacheMaxTracksProvider: () -> Int,
    private val urlCacheMaxBytesProvider: () -> Long,
    private val currentRemoteLoadJobProvider: () -> Job?,
    private val onRemoteLoadUiStateChanged: (RemoteLoadUiState?) -> Unit,
    private val onRemoteLoadJobChanged: (Job?) -> Unit,
    private val onResetPlayback: () -> Unit,
    private val onSelectedFileChanged: (File?) -> Unit,
    private val onCurrentPlaybackSourceIdChanged: (String?) -> Unit,
    private val onVisiblePlayableFilesChanged: (List<File>) -> Unit,
    private val onPlayerSurfaceVisibleChanged: (Boolean) -> Unit,
    private val onSongVolumeDbChanged: (Float) -> Unit,
    private val onSongGainChanged: (Float) -> Unit,
    private val applyNativeTrackSnapshot: (NativeTrackSnapshot) -> Unit,
    private val refreshSubtuneState: () -> Unit,
    private val onPositionChanged: (Double) -> Unit,
    private val onArtworkBitmapCleared: () -> Unit,
    private val refreshRepeatModeForTrack: () -> Unit,
    private val onAddRecentPlayedTrack: (String, String?, String?, String?) -> Unit,
    private val metadataTitleProvider: () -> String,
    private val metadataArtistProvider: () -> String,
    private val applyRepeatModeToNative: (RepeatMode) -> Unit,
    private val onStartEngine: () -> Unit,
    private val onIsPlayingChanged: (Boolean) -> Unit,
    private val scheduleRecentTrackMetadataRefresh: (String, String?) -> Unit,
    private val onPlayerExpandedChanged: (Boolean) -> Unit,
    private val syncPlaybackService: () -> Unit,
    private val onBrowserLaunchTargetChanged: (BrowserLaunchState) -> Unit,
    private val onCurrentViewChanged: (MainView) -> Unit,
    private val onAddRecentFolder: (String, String?, Long?) -> Unit,
    private val onApplyTrackSelection: (file: File, autoStart: Boolean, expandOverride: Boolean?, sourceIdOverride: String?) -> Unit
) {
    fun primeManualRemoteOpenState(resolved: ManualSourceResolution) {
        primeManualRemoteOpenStateAction(
            resolved = resolved,
            onResetPlayback = onResetPlayback,
            onSelectedFileChanged = onSelectedFileChanged,
            onCurrentPlaybackSourceIdChanged = onCurrentPlaybackSourceIdChanged,
            onVisiblePlayableFilesChanged = onVisiblePlayableFilesChanged,
            onPlayerSurfaceVisibleChanged = onPlayerSurfaceVisibleChanged,
            onSongVolumeDbChanged = onSongVolumeDbChanged,
            onSongGainChanged = onSongGainChanged,
            onRemoteLoadUiStateChanged = onRemoteLoadUiStateChanged
        )
    }

    fun applyManualRemoteOpenSuccess(
        result: ManualRemoteOpenSuccess,
        expandOverride: Boolean?
    ) {
        applyManualRemoteOpenSuccessAction(
            result = result,
            expandOverride = expandOverride,
            openPlayerOnTrackSelect = openPlayerOnTrackSelectProvider(),
            activeRepeatMode = activeRepeatModeProvider(),
            onSelectedFileChanged = onSelectedFileChanged,
            onCurrentPlaybackSourceIdChanged = onCurrentPlaybackSourceIdChanged,
            onVisiblePlayableFilesChanged = onVisiblePlayableFilesChanged,
            onPlayerSurfaceVisibleChanged = onPlayerSurfaceVisibleChanged,
            onSongVolumeDbChanged = onSongVolumeDbChanged,
            onSongGainChanged = onSongGainChanged,
            applyNativeTrackSnapshot = applyNativeTrackSnapshot,
            refreshSubtuneState = refreshSubtuneState,
            onPositionChanged = onPositionChanged,
            onArtworkBitmapCleared = onArtworkBitmapCleared,
            refreshRepeatModeForTrack = refreshRepeatModeForTrack,
            onAddRecentPlayedTrack = onAddRecentPlayedTrack,
            metadataTitleProvider = metadataTitleProvider,
            metadataArtistProvider = metadataArtistProvider,
            applyRepeatModeToNative = applyRepeatModeToNative,
            onStartEngine = onStartEngine,
            onIsPlayingChanged = onIsPlayingChanged,
            scheduleRecentTrackMetadataRefresh = scheduleRecentTrackMetadataRefresh,
            onPlayerExpandedChanged = onPlayerExpandedChanged,
            syncPlaybackService = syncPlaybackService
        )
    }

    fun failManualOpen(reason: String) {
        failManualOpenAction(context, reason)
    }

    fun launchManualRemoteSelection(
        resolved: ManualSourceResolution,
        options: ManualSourceOpenOptions,
        expandOverride: Boolean?
    ) {
        launchManualRemoteSelectionAction(
            context = context,
            appScope = appScope,
            currentJob = currentRemoteLoadJobProvider(),
            resolved = resolved,
            options = options,
            expandOverride = expandOverride,
            cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR),
            selectedFileAbsolutePathProvider = selectedFileAbsolutePathProvider,
            urlCacheMaxTracks = urlCacheMaxTracksProvider(),
            urlCacheMaxBytes = urlCacheMaxBytesProvider(),
            onRemoteLoadUiStateChanged = onRemoteLoadUiStateChanged,
            onRemoteLoadJobChanged = onRemoteLoadJobChanged,
            onPrimeManualRemoteOpenState = { source -> primeManualRemoteOpenState(source) },
            onApplyManualRemoteOpenSuccess = { success, expandTarget ->
                applyManualRemoteOpenSuccess(success, expandTarget)
            },
            onFailManualOpen = { failReason -> failManualOpen(failReason) }
        )
    }

    fun applyManualInputSelection(
        rawInput: String,
        options: ManualSourceOpenOptions = ManualSourceOpenOptions(),
        expandOverride: Boolean? = null
    ) {
        applyManualInputSelectionAction(
            context = context,
            appScope = appScope,
            repository = repository,
            rawInput = rawInput,
            options = options,
            expandOverride = expandOverride,
            storageDescriptors = storageDescriptors,
            openPlayerOnTrackSelect = openPlayerOnTrackSelectProvider(),
            onBrowserLaunchTargetChanged = onBrowserLaunchTargetChanged,
            onCurrentViewChanged = onCurrentViewChanged,
            onAddRecentFolder = onAddRecentFolder,
            onVisiblePlayableFilesChanged = onVisiblePlayableFilesChanged,
            onApplyTrackSelection = onApplyTrackSelection,
            onLaunchManualRemoteSelection = { source, openOpts, expandTarget ->
                launchManualRemoteSelection(source, openOpts, expandTarget)
            }
        )
    }
}
