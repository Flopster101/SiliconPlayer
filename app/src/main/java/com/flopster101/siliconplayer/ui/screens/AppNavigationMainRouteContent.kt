package com.flopster101.siliconplayer

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import com.flopster101.siliconplayer.data.FileRepository
import java.io.File

@Composable
internal fun AppNavigationHomeContentSection(
    mainPadding: PaddingValues,
    context: Context,
    prefs: SharedPreferences,
    currentTrackPath: String?,
    metadataTitle: String,
    metadataArtist: String,
    isPlaying: Boolean,
    recentFolders: List<RecentPathEntry>,
    recentPlayedFiles: List<RecentPathEntry>,
    recentFoldersLimit: Int,
    recentFilesLimit: Int,
    networkNodes: List<NetworkNode>,
    storageDescriptors: List<StorageDescriptor>,
    bottomContentPadding: Dp,
    openPlayerOnTrackSelect: Boolean,
    trackLoadDelegates: AppNavigationTrackLoadDelegates,
    manualOpenDelegates: AppNavigationManualOpenDelegates,
    runtimeDelegates: AppNavigationRuntimeDelegates,
    onRecentFoldersChanged: (List<RecentPathEntry>) -> Unit,
    onRecentPlayedFilesChanged: (List<RecentPathEntry>) -> Unit,
    onOpenBrowser: (locationId: String?, directoryPath: String?, smbSourceNodeId: Long?, httpSourceNodeId: Long?, httpRootPath: String?, returnToNetworkOnExit: Boolean) -> Unit,
    onCurrentViewChanged: (MainView) -> Unit,
    onOpenUrlOrPathDialog: () -> Unit
) {
    AppNavigationHomeRouteSection(
        mainPadding = mainPadding,
        currentTrackPath = currentTrackPath,
        currentTrackTitle = metadataTitle,
        currentTrackArtist = metadataArtist,
        recentFolders = recentFolders,
        recentPlayedFiles = recentPlayedFiles,
        storagePresentationForEntry = { entry ->
            storagePresentationForEntry(context, entry, storageDescriptors)
        },
        bottomContentPadding = bottomContentPadding,
        onOpenLibrary = {
            onOpenBrowser(null, null, null, null, null, false)
            onCurrentViewChanged(MainView.Browser)
        },
        onOpenNetwork = {
            onCurrentViewChanged(MainView.Network)
        },
        onOpenUrlOrPath = onOpenUrlOrPathDialog,
        onOpenRecentFolder = { entry ->
            val smbSpec = parseSmbSourceSpecFromInput(entry.path)
            if (smbSpec != null) {
                val smbTarget = resolveSmbRecentOpenTarget(
                    targetSpec = smbSpec,
                    networkNodes = networkNodes
                )
                onOpenBrowser(
                    null,
                    smbTarget.requestUri,
                    smbTarget.sourceNodeId,
                    null,
                    null,
                    false
                )
            } else if (parseHttpSourceSpecFromInput(entry.path) != null) {
                onOpenBrowser(null, entry.path, null, null, null, false)
            } else {
                onOpenBrowser(entry.locationId, entry.path, null, null, null, false)
            }
            onCurrentViewChanged(MainView.Browser)
        },
        onPlayRecentFile = { entry ->
            playRecentFileEntryAction(
                cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR),
                entry = entry,
                openPlayerOnTrackSelect = openPlayerOnTrackSelect,
                onApplyTrackSelection = { file, autoStart, expandOverride, sourceIdOverride, locationIdOverride, useSongVolumeLookup ->
                    trackLoadDelegates.applyTrackSelection(
                        file,
                        autoStart,
                        expandOverride,
                        sourceIdOverride,
                        locationIdOverride,
                        useSongVolumeLookup = useSongVolumeLookup
                    )
                },
                onApplyManualInputSelection = { rawInput ->
                    manualOpenDelegates.applyManualInputSelection(rawInput)
                }
            )
        },
        onPersistRecentFileMetadata = { entry, title, artist ->
            if (!isPlaying) return@AppNavigationHomeRouteSection
            val decoderName = NativeBridge.getCurrentDecoderName().trim().takeIf { it.isNotEmpty() }
            val updatedRecentPlayed = buildUpdatedRecentPlayedTracks(
                current = recentPlayedFiles,
                newPath = entry.path,
                locationId = entry.locationId,
                title = title,
                artist = artist,
                decoderName = decoderName,
                clearBlankMetadataOnUpdate = true,
                limit = recentFilesLimit
            )
            onRecentPlayedFilesChanged(updatedRecentPlayed)
            writeRecentEntries(
                prefs,
                AppPreferenceKeys.RECENT_PLAYED_FILES,
                updatedRecentPlayed,
                recentFilesLimit
            )
        },
        onRecentFolderAction = { entry, action ->
            applyRecentFolderAction(
                context = context,
                prefs = prefs,
                entry = entry,
                action = action,
                recentFolders = recentFolders,
                recentFoldersLimit = recentFoldersLimit,
                networkNodes = networkNodes,
                onRecentFoldersChanged = onRecentFoldersChanged,
                onOpenInBrowser = { locationId, directoryPath, smbSourceNodeId ->
                    onOpenBrowser(locationId, directoryPath, smbSourceNodeId, null, null, false)
                    onCurrentViewChanged(MainView.Browser)
                }
            )
        },
        onRecentFileAction = { entry, action ->
            applyRecentSourceAction(
                context = context,
                prefs = prefs,
                entry = entry,
                action = action,
                recentPlayedFiles = recentPlayedFiles,
                recentFilesLimit = recentFilesLimit,
                networkNodes = networkNodes,
                onRecentPlayedFilesChanged = onRecentPlayedFilesChanged,
                resolveShareableFileForRecent = { recent ->
                    runtimeDelegates.resolveShareableFileForRecent(recent)
                },
                onOpenInBrowser = { locationId, directoryPath, smbSourceNodeId ->
                    onOpenBrowser(locationId, directoryPath, smbSourceNodeId, null, null, false)
                    onCurrentViewChanged(MainView.Browser)
                }
            )
        },
        canShareRecentFile = { entry ->
            runtimeDelegates.resolveShareableFileForRecent(entry) != null
        }
    )
}

@Composable
internal fun AppNavigationNetworkContentSection(
    mainPadding: PaddingValues,
    bottomContentPadding: Dp,
    isPlayerExpanded: Boolean,
    networkNodes: List<NetworkNode>,
    networkCurrentFolderId: Long?,
    manualOpenDelegates: AppNavigationManualOpenDelegates,
    onCurrentViewChanged: (MainView) -> Unit,
    onNetworkCurrentFolderIdChanged: (Long?) -> Unit,
    onNetworkNodesChanged: (List<NetworkNode>) -> Unit,
    onResolveRemoteSourceMetadata: (String, () -> Unit) -> Unit,
    onCancelPendingMetadataBackfill: () -> Unit,
    onOpenBrowser: (locationId: String?, directoryPath: String?, smbSourceNodeId: Long?, httpSourceNodeId: Long?, httpRootPath: String?, returnToNetworkOnExit: Boolean) -> Unit
) {
    AppNavigationNetworkRouteSection(
        mainPadding = mainPadding,
        bottomContentPadding = bottomContentPadding,
        backHandlingEnabled = !isPlayerExpanded,
        nodes = networkNodes,
        currentFolderId = networkCurrentFolderId,
        onExitNetwork = { onCurrentViewChanged(MainView.Home) },
        onCurrentFolderIdChanged = onNetworkCurrentFolderIdChanged,
        onNodesChanged = onNetworkNodesChanged,
        onResolveRemoteSourceMetadata = onResolveRemoteSourceMetadata,
        onCancelPendingMetadataBackfill = onCancelPendingMetadataBackfill,
        onOpenRemoteSource = { rawInput ->
            manualOpenDelegates.applyManualInputSelection(rawInput)
        },
        onBrowseSmbSource = { rawInput, sourceNodeId ->
            onOpenBrowser(null, rawInput, sourceNodeId, null, null, true)
            onCurrentViewChanged(MainView.Browser)
        },
        onBrowseHttpSource = { rawInput, sourceNodeId, rootPath ->
            onOpenBrowser(null, rawInput, null, sourceNodeId, rootPath, true)
            onCurrentViewChanged(MainView.Browser)
        }
    )
}

@Composable
internal fun AppNavigationBrowserContentSection(
    mainPadding: PaddingValues,
    prefs: SharedPreferences,
    repository: FileRepository,
    decoderExtensionArtworkHints: Map<String, DecoderArtworkHint>,
    rememberBrowserLocation: Boolean,
    lastBrowserLocationId: String?,
    lastBrowserDirectoryPath: String?,
    browserLaunchLocationId: String?,
    browserLaunchDirectoryPath: String?,
    browserLaunchSmbSourceNodeId: Long?,
    browserLaunchHttpSourceNodeId: Long?,
    browserLaunchHttpRootPath: String?,
    browserFocusRestoreRequestToken: Int,
    bottomContentPadding: Dp,
    showParentDirectoryEntry: Boolean,
    showFileIconChipBackground: Boolean,
    isPlayerExpanded: Boolean,
    selectedFile: File?,
    networkNodes: List<NetworkNode>,
    autoPlayOnTrackSelect: Boolean,
    openPlayerOnTrackSelect: Boolean,
    trackLoadDelegates: AppNavigationTrackLoadDelegates,
    manualOpenDelegates: AppNavigationManualOpenDelegates,
    runtimeDelegates: AppNavigationRuntimeDelegates,
    onCurrentViewChanged: (MainView) -> Unit,
    onVisiblePlayableFilesChanged: (List<File>) -> Unit,
    onBrowserLaunchLocationIdChanged: (String?) -> Unit,
    onBrowserLaunchDirectoryPathChanged: (String?) -> Unit,
    onBrowserLaunchSmbSourceNodeIdChanged: (Long?) -> Unit,
    onBrowserLaunchHttpSourceNodeIdChanged: (Long?) -> Unit,
    onBrowserLaunchHttpRootPathChanged: (String?) -> Unit,
    onReturnToNetworkOnBrowserExitChanged: (Boolean) -> Unit,
    returnToNetworkOnBrowserExit: Boolean,
    onLastBrowserLocationIdChanged: (String?) -> Unit,
    onLastBrowserDirectoryPathChanged: (String?) -> Unit,
    onBrowserLocationChanged: (String?, String?) -> Unit,
    onRememberSmbCredentials: (Long?, String, String?, String?) -> Unit,
    onRememberHttpCredentials: (Long?, String, String?, String?) -> Unit
) {
    AppNavigationBrowserRouteSection(
        mainPadding = mainPadding,
        repository = repository,
        decoderExtensionArtworkHints = decoderExtensionArtworkHints,
        initialLocationId = browserLaunchLocationId
            ?: if (rememberBrowserLocation) lastBrowserLocationId else null,
        initialDirectoryPath = browserLaunchDirectoryPath
            ?: if (rememberBrowserLocation) lastBrowserDirectoryPath else null,
        initialSmbSourceNodeId = browserLaunchSmbSourceNodeId,
        initialHttpSourceNodeId = browserLaunchHttpSourceNodeId,
        initialHttpRootPath = browserLaunchHttpRootPath,
        restoreFocusedItemRequestToken = browserFocusRestoreRequestToken,
        bottomContentPadding = bottomContentPadding,
        showParentDirectoryEntry = showParentDirectoryEntry,
        showFileIconChipBackground = showFileIconChipBackground,
        backHandlingEnabled = !isPlayerExpanded,
        playingFile = selectedFile,
        onVisiblePlayableFilesChanged = onVisiblePlayableFilesChanged,
        onExitBrowser = {
            onCurrentViewChanged(if (returnToNetworkOnBrowserExit) MainView.Network else MainView.Home)
            onReturnToNetworkOnBrowserExitChanged(false)
            onBrowserLaunchSmbSourceNodeIdChanged(null)
            onBrowserLaunchHttpSourceNodeIdChanged(null)
            onBrowserLaunchHttpRootPathChanged(null)
        },
        onBrowserLocationChanged = onBrowserLocationChanged,
        onFileSelected = { file, sourceIdOverride ->
            trackLoadDelegates.applyTrackSelection(
                file = file,
                autoStart = autoPlayOnTrackSelect,
                expandOverride = openPlayerOnTrackSelect,
                sourceIdOverride = sourceIdOverride
            )
        },
        onOpenRemoteSource = { rawInput ->
            manualOpenDelegates.applyManualInputSelection(rawInput)
        },
        onRememberSmbCredentials = onRememberSmbCredentials,
        onRememberHttpCredentials = onRememberHttpCredentials
    )
}
