package com.flopster101.siliconplayer

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.flopster101.siliconplayer.data.resolveArchiveSourceToMountedFile
import com.flopster101.siliconplayer.data.FileRepository
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal fun primeManualRemoteOpenStateAction(
    resolved: ManualSourceResolution,
    onResetPlayback: () -> Unit,
    onSelectedFileChanged: (File?) -> Unit,
    onCurrentPlaybackSourceIdChanged: (String) -> Unit,
    onVisiblePlayableFilesChanged: (List<File>) -> Unit,
    onPlayerSurfaceVisibleChanged: (Boolean) -> Unit,
    onSongVolumeDbChanged: (Float) -> Unit,
    onSongGainChanged: (Float) -> Unit,
    onRemoteLoadUiStateChanged: (RemoteLoadUiState?) -> Unit
) {
    onResetPlayback()
    onSelectedFileChanged(resolved.displayFile)
    onCurrentPlaybackSourceIdChanged(resolved.sourceId)
    onVisiblePlayableFilesChanged(emptyList())
    onPlayerSurfaceVisibleChanged(true)
    onSongVolumeDbChanged(0f)
    onSongGainChanged(0f)
    onRemoteLoadUiStateChanged(
        RemoteLoadUiState(
            sourceId = resolved.sourceId,
            phase = RemoteLoadPhase.Connecting,
            indeterminate = true
        )
    )
}

internal fun applyManualRemoteOpenSuccessAction(
    result: ManualRemoteOpenSuccess,
    expandOverride: Boolean?,
    openPlayerOnTrackSelect: Boolean,
    activeRepeatMode: RepeatMode,
    onSelectedFileChanged: (File) -> Unit,
    onCurrentPlaybackSourceIdChanged: (String) -> Unit,
    onVisiblePlayableFilesChanged: (List<File>) -> Unit,
    onPlayerSurfaceVisibleChanged: (Boolean) -> Unit,
    onSongVolumeDbChanged: (Float) -> Unit,
    onSongGainChanged: (Float) -> Unit,
    applyNativeTrackSnapshot: (NativeTrackSnapshot) -> Unit,
    refreshSubtuneState: () -> Unit,
    onPositionChanged: (Double) -> Unit,
    onArtworkBitmapCleared: () -> Unit,
    refreshRepeatModeForTrack: () -> Unit,
    onAddRecentPlayedTrack: (String, String?, String?, String?) -> Unit,
    metadataTitleProvider: () -> String,
    metadataArtistProvider: () -> String,
    applyRepeatModeToNative: (RepeatMode) -> Unit,
    onStartEngine: () -> Unit,
    onIsPlayingChanged: (Boolean) -> Unit,
    scheduleRecentTrackMetadataRefresh: (String, String?) -> Unit,
    onPlayerExpandedChanged: (Boolean) -> Unit,
    syncPlaybackService: () -> Unit
) {
    onSelectedFileChanged(result.displayFile)
    onCurrentPlaybackSourceIdChanged(result.sourceId)
    onVisiblePlayableFilesChanged(emptyList())
    onPlayerSurfaceVisibleChanged(true)
    onSongVolumeDbChanged(0f)
    onSongGainChanged(0f)
    applyNativeTrackSnapshot(result.snapshot)
    refreshSubtuneState()
    onPositionChanged(0.0)
    onArtworkBitmapCleared()
    refreshRepeatModeForTrack()
    onAddRecentPlayedTrack(result.sourceId, null, metadataTitleProvider(), metadataArtistProvider())
    applyRepeatModeToNative(activeRepeatMode)
    onStartEngine()
    onIsPlayingChanged(true)
    scheduleRecentTrackMetadataRefresh(result.sourceId, null)
    onPlayerExpandedChanged(expandOverride ?: openPlayerOnTrackSelect)
    syncPlaybackService()
}

internal fun failManualOpenAction(context: Context, reason: String) {
    Toast.makeText(context, "Unable to open source: $reason", Toast.LENGTH_LONG).show()
}

internal fun launchManualRemoteSelectionAction(
    context: Context,
    appScope: CoroutineScope,
    currentJob: Job?,
    resolved: ManualSourceResolution,
    options: ManualSourceOpenOptions,
    expandOverride: Boolean?,
    cacheRoot: File,
    selectedFileAbsolutePathProvider: () -> String?,
    urlCacheMaxTracks: Int,
    urlCacheMaxBytes: Long,
    onRemoteLoadUiStateChanged: (RemoteLoadUiState?) -> Unit,
    onRemoteLoadJobChanged: (Job?) -> Unit,
    onPrimeManualRemoteOpenState: (ManualSourceResolution) -> Unit,
    onApplyManualRemoteOpenSuccess: (ManualRemoteOpenSuccess, Boolean?) -> Unit,
    onFailManualOpen: (String) -> Unit
) {
    currentJob?.cancel()
    val launchedJob = appScope.launch {
        try {
            onPrimeManualRemoteOpenState(resolved)
            val remoteResult = when (resolved.type) {
                ManualSourceType.RemoteUrl -> {
                    executeManualRemoteOpen(
                        resolved = resolved,
                        forceCaching = options.forceCaching,
                        cacheRoot = cacheRoot,
                        selectedFileAbsolutePath = selectedFileAbsolutePathProvider(),
                        urlCacheMaxTracks = urlCacheMaxTracks,
                        urlCacheMaxBytes = urlCacheMaxBytes,
                        onStatus = { state -> onRemoteLoadUiStateChanged(state) },
                        downloadToCache = { sourceId, requestUrl, onStatus ->
                            downloadRemoteUrlToCache(
                                context = context,
                                url = sourceId,
                                requestUrl = requestUrl,
                                onStatus = onStatus
                            )
                        }
                    )
                }

                ManualSourceType.Smb -> {
                    val smbSpec = resolved.smbSpec
                    if (smbSpec == null) {
                        ManualRemoteOpenResult.Failed("Invalid SMB share configuration")
                    } else {
                        executeManualRemoteOpen(
                            resolved = resolved,
                            forceCaching = true,
                            cacheRoot = cacheRoot,
                            selectedFileAbsolutePath = selectedFileAbsolutePathProvider(),
                            urlCacheMaxTracks = urlCacheMaxTracks,
                            urlCacheMaxBytes = urlCacheMaxBytes,
                            onStatus = { state -> onRemoteLoadUiStateChanged(state) },
                            downloadToCache = { sourceId, _, onStatus ->
                                downloadSmbSourceToCache(
                                    context = context,
                                    sourceId = sourceId,
                                    spec = smbSpec,
                                    onStatus = onStatus
                                )
                            }
                        )
                    }
                }

                else -> {
                    ManualRemoteOpenResult.Failed("Unsupported manual source type")
                }
            }
            when (remoteResult) {
                is ManualRemoteOpenResult.Success -> {
                    onApplyManualRemoteOpenSuccess(remoteResult.value, expandOverride)
                }

                ManualRemoteOpenResult.DownloadCancelled -> {
                    Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                is ManualRemoteOpenResult.Failed -> {
                    Log.e(
                        URL_SOURCE_TAG,
                        "Cache download/open failed for URL: ${resolved.sourceId} reason=${remoteResult.reason}"
                    )
                    onFailManualOpen(remoteResult.reason)
                    return@launch
                }
            }
        } catch (_: CancellationException) {
            Log.d(URL_SOURCE_TAG, "Remote open cancelled for source=${resolved.sourceId}")
        } finally {
            onRemoteLoadUiStateChanged(null)
            onRemoteLoadJobChanged(null)
        }
    }
    onRemoteLoadJobChanged(launchedJob)
}

internal fun applyManualInputSelectionAction(
    context: Context,
    appScope: CoroutineScope,
    repository: FileRepository,
    rawInput: String,
    options: ManualSourceOpenOptions,
    expandOverride: Boolean?,
    storageDescriptors: List<StorageDescriptor>,
    openPlayerOnTrackSelect: Boolean,
    onBrowserLaunchTargetChanged: (String?, String?) -> Unit,
    onCurrentViewChanged: (MainView) -> Unit,
    onAddRecentFolder: (String, String?, Long?) -> Unit,
    onVisiblePlayableFilesChanged: (List<File>) -> Unit,
    onApplyTrackSelection: (File, Boolean, Boolean?, String?) -> Unit,
    onLaunchManualRemoteSelection: (ManualSourceResolution, ManualSourceOpenOptions, Boolean?) -> Unit
) {
    val archiveFile = resolveArchiveSourceToMountedFile(context, rawInput)
    if (archiveFile != null) {
        appScope.launch {
            val contextualPlayableFiles = loadContextualPlayableFilesForManualSelection(
                repository = repository,
                localFile = archiveFile
            )
            onVisiblePlayableFilesChanged(contextualPlayableFiles)
            onApplyTrackSelection(archiveFile, true, openPlayerOnTrackSelect, rawInput.trim())
        }
        return
    }

    when (val action = resolveManualInputAction(rawInput, storageDescriptors)) {
        ManualInputAction.Invalid -> {
            Toast.makeText(context, MANUAL_INPUT_INVALID_MESSAGE, Toast.LENGTH_SHORT).show()
            return
        }

        is ManualInputAction.OpenDirectory -> {
            onBrowserLaunchTargetChanged(action.locationId, action.directoryPath)
            onCurrentViewChanged(MainView.Browser)
            onAddRecentFolder(action.directoryPath, action.locationId, null)
            return
        }

        is ManualInputAction.OpenLocalFile -> {
            appScope.launch {
                val localFile = action.file
                val contextualPlayableFiles = loadContextualPlayableFilesForManualSelection(
                    repository = repository,
                    localFile = localFile
                )
                onVisiblePlayableFilesChanged(contextualPlayableFiles)
                onApplyTrackSelection(localFile, true, openPlayerOnTrackSelect, action.sourceId)
            }
            return
        }

        is ManualInputAction.OpenRemote -> {
            onLaunchManualRemoteSelection(action.resolved, options, expandOverride)
            return
        }
    }
}
