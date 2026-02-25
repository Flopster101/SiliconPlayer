package com.flopster101.siliconplayer

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.focus.FocusRequester
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
                    networkNodes = networkNodes,
                    preferredSourceNodeId = entry.sourceNodeId
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
                onOpenBrowser(null, entry.path, null, entry.sourceNodeId, null, false)
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
                sourceNodeId = entry.sourceNodeId,
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
                onOpenInBrowser = { locationId, directoryPath, smbSourceNodeId, httpSourceNodeId ->
                    onOpenBrowser(locationId, directoryPath, smbSourceNodeId, httpSourceNodeId, null, false)
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
                onOpenInBrowser = { locationId, directoryPath, smbSourceNodeId, httpSourceNodeId ->
                    onOpenBrowser(locationId, directoryPath, smbSourceNodeId, httpSourceNodeId, null, false)
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
        onOpenRemoteSourceAsCached = { rawInput ->
            manualOpenDelegates.applyManualInputSelection(
                rawInput,
                options = ManualSourceOpenOptions(forceCaching = true)
            )
        },
        onRememberSmbCredentials = onRememberSmbCredentials,
        onRememberHttpCredentials = onRememberHttpCredentials
    )
}

@Composable
internal fun AppNavigationMainContentHost(
    currentView: MainView,
    mainContentFocusRequester: FocusRequester,
    canFocusMiniPlayer: Boolean,
    requestMiniPlayerFocus: () -> Unit,
    onHardwareNavigationInput: () -> Unit,
    onTouchInteraction: () -> Unit,
    onOpenPlayerSurface: () -> Unit,
    onHomeRequested: () -> Unit,
    onSettingsRequested: () -> Unit,
    context: Context,
    prefs: SharedPreferences,
    currentPlaybackSourceId: String?,
    selectedFile: File?,
    metadataTitle: String,
    metadataArtist: String,
    isPlaying: Boolean,
    recentFolders: List<RecentPathEntry>,
    recentPlayedFiles: List<RecentPathEntry>,
    recentFoldersLimit: Int,
    recentFilesLimit: Int,
    networkNodes: List<NetworkNode>,
    storageDescriptors: List<StorageDescriptor>,
    miniPlayerListInset: Dp,
    openPlayerOnTrackSelect: Boolean,
    autoPlayOnTrackSelect: Boolean,
    trackLoadDelegates: AppNavigationTrackLoadDelegates,
    manualOpenDelegates: AppNavigationManualOpenDelegates,
    runtimeDelegates: AppNavigationRuntimeDelegates,
    onRecentFoldersChanged: (List<RecentPathEntry>) -> Unit,
    onRecentPlayedFilesChanged: (List<RecentPathEntry>) -> Unit,
    onOpenBrowser: (locationId: String?, directoryPath: String?, smbSourceNodeId: Long?, httpSourceNodeId: Long?, httpRootPath: String?, returnToNetworkOnExit: Boolean) -> Unit,
    onCurrentViewChanged: (MainView) -> Unit,
    onOpenUrlOrPathDialog: () -> Unit,
    isPlayerExpanded: Boolean,
    networkCurrentFolderId: Long?,
    onNetworkCurrentFolderIdChanged: (Long?) -> Unit,
    onNetworkNodesChanged: (List<NetworkNode>) -> Unit,
    onResolveRemoteSourceMetadata: (String, () -> Unit) -> Unit,
    onCancelPendingMetadataBackfill: () -> Unit,
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
    showParentDirectoryEntry: Boolean,
    showFileIconChipBackground: Boolean,
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
    onRememberHttpCredentials: (Long?, String, String?, String?) -> Unit,
    settingsContent: @Composable (PaddingValues) -> Unit
) {
    AppNavigationMainScaffoldSection(
        currentView = currentView,
        mainContentFocusRequester = mainContentFocusRequester,
        canFocusMiniPlayer = canFocusMiniPlayer,
        requestMiniPlayerFocus = requestMiniPlayerFocus,
        onHardwareNavigationInput = onHardwareNavigationInput,
        onTouchInteraction = onTouchInteraction,
        onOpenPlayerSurface = onOpenPlayerSurface,
        onHomeRequested = onHomeRequested,
        onSettingsRequested = onSettingsRequested,
        homeContent = { mainPadding ->
            AppNavigationHomeContentSection(
                mainPadding = mainPadding,
                context = context,
                prefs = prefs,
                currentTrackPath = currentPlaybackSourceId ?: selectedFile?.absolutePath,
                metadataTitle = metadataTitle,
                metadataArtist = metadataArtist,
                isPlaying = isPlaying,
                recentFolders = recentFolders,
                recentPlayedFiles = recentPlayedFiles,
                recentFoldersLimit = recentFoldersLimit,
                recentFilesLimit = recentFilesLimit,
                networkNodes = networkNodes,
                storageDescriptors = storageDescriptors,
                bottomContentPadding = miniPlayerListInset,
                openPlayerOnTrackSelect = openPlayerOnTrackSelect,
                trackLoadDelegates = trackLoadDelegates,
                manualOpenDelegates = manualOpenDelegates,
                runtimeDelegates = runtimeDelegates,
                onRecentFoldersChanged = onRecentFoldersChanged,
                onRecentPlayedFilesChanged = onRecentPlayedFilesChanged,
                onOpenBrowser = onOpenBrowser,
                onCurrentViewChanged = onCurrentViewChanged,
                onOpenUrlOrPathDialog = onOpenUrlOrPathDialog
            )
        },
        networkContent = { mainPadding ->
            AppNavigationNetworkContentSection(
                mainPadding = mainPadding,
                bottomContentPadding = miniPlayerListInset,
                isPlayerExpanded = isPlayerExpanded,
                networkNodes = networkNodes,
                networkCurrentFolderId = networkCurrentFolderId,
                manualOpenDelegates = manualOpenDelegates,
                onCurrentViewChanged = onCurrentViewChanged,
                onNetworkCurrentFolderIdChanged = onNetworkCurrentFolderIdChanged,
                onNetworkNodesChanged = onNetworkNodesChanged,
                onResolveRemoteSourceMetadata = onResolveRemoteSourceMetadata,
                onCancelPendingMetadataBackfill = onCancelPendingMetadataBackfill,
                onOpenBrowser = onOpenBrowser
            )
        },
        browserContent = { mainPadding ->
            AppNavigationBrowserContentSection(
                mainPadding = mainPadding,
                prefs = prefs,
                repository = repository,
                decoderExtensionArtworkHints = decoderExtensionArtworkHints,
                rememberBrowserLocation = rememberBrowserLocation,
                lastBrowserLocationId = lastBrowserLocationId,
                lastBrowserDirectoryPath = lastBrowserDirectoryPath,
                browserLaunchLocationId = browserLaunchLocationId,
                browserLaunchDirectoryPath = browserLaunchDirectoryPath,
                browserLaunchSmbSourceNodeId = browserLaunchSmbSourceNodeId,
                browserLaunchHttpSourceNodeId = browserLaunchHttpSourceNodeId,
                browserLaunchHttpRootPath = browserLaunchHttpRootPath,
                browserFocusRestoreRequestToken = browserFocusRestoreRequestToken,
                bottomContentPadding = miniPlayerListInset,
                showParentDirectoryEntry = showParentDirectoryEntry,
                showFileIconChipBackground = showFileIconChipBackground,
                isPlayerExpanded = isPlayerExpanded,
                selectedFile = selectedFile,
                networkNodes = networkNodes,
                autoPlayOnTrackSelect = autoPlayOnTrackSelect,
                openPlayerOnTrackSelect = openPlayerOnTrackSelect,
                trackLoadDelegates = trackLoadDelegates,
                manualOpenDelegates = manualOpenDelegates,
                runtimeDelegates = runtimeDelegates,
                onCurrentViewChanged = onCurrentViewChanged,
                onVisiblePlayableFilesChanged = onVisiblePlayableFilesChanged,
                onBrowserLaunchLocationIdChanged = onBrowserLaunchLocationIdChanged,
                onBrowserLaunchDirectoryPathChanged = onBrowserLaunchDirectoryPathChanged,
                onBrowserLaunchSmbSourceNodeIdChanged = onBrowserLaunchSmbSourceNodeIdChanged,
                onBrowserLaunchHttpSourceNodeIdChanged = onBrowserLaunchHttpSourceNodeIdChanged,
                onBrowserLaunchHttpRootPathChanged = onBrowserLaunchHttpRootPathChanged,
                onReturnToNetworkOnBrowserExitChanged = onReturnToNetworkOnBrowserExitChanged,
                returnToNetworkOnBrowserExit = returnToNetworkOnBrowserExit,
                onLastBrowserLocationIdChanged = onLastBrowserLocationIdChanged,
                onLastBrowserDirectoryPathChanged = onLastBrowserDirectoryPathChanged,
                onBrowserLocationChanged = onBrowserLocationChanged,
                onRememberSmbCredentials = onRememberSmbCredentials,
                onRememberHttpCredentials = onRememberHttpCredentials
            )
        },
        settingsContent = settingsContent
    )
}
