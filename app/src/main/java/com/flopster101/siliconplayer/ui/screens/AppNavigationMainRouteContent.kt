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
    onOpenBrowser: (BrowserOpenRequest) -> Unit,
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
            onOpenBrowser(browserOpenRequest())
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
                    browserOpenRequest(
                        directoryPath = smbTarget.requestUri,
                        smbSourceNodeId = smbTarget.sourceNodeId
                    )
                )
            } else if (parseHttpSourceSpecFromInput(entry.path) != null) {
                onOpenBrowser(
                    browserOpenRequest(
                        directoryPath = entry.path,
                        httpSourceNodeId = entry.sourceNodeId
                    )
                )
            } else {
                onOpenBrowser(
                    browserOpenRequest(
                        locationId = entry.locationId,
                        directoryPath = entry.path
                    )
                )
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
                    onOpenBrowser(
                        browserOpenRequest(
                            locationId = locationId,
                            directoryPath = directoryPath,
                            smbSourceNodeId = smbSourceNodeId,
                            httpSourceNodeId = httpSourceNodeId
                        )
                    )
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
                    onOpenBrowser(
                        browserOpenRequest(
                            locationId = locationId,
                            directoryPath = directoryPath,
                            smbSourceNodeId = smbSourceNodeId,
                            httpSourceNodeId = httpSourceNodeId
                        )
                    )
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
    onOpenBrowser: (BrowserOpenRequest) -> Unit
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
            onOpenBrowser(
                browserOpenRequest(
                    directoryPath = rawInput,
                    smbSourceNodeId = sourceNodeId,
                    returnToNetworkOnExit = true
                )
            )
            onCurrentViewChanged(MainView.Browser)
        },
        onBrowseHttpSource = { rawInput, sourceNodeId, rootPath ->
            onOpenBrowser(
                browserOpenRequest(
                    directoryPath = rawInput,
                    httpSourceNodeId = sourceNodeId,
                    httpRootPath = rootPath,
                    returnToNetworkOnExit = true
                )
            )
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
    browserLaunchState: BrowserLaunchState,
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
    onBrowserLaunchStateChanged: (BrowserLaunchState) -> Unit,
    onReturnToNetworkOnBrowserExitChanged: (Boolean) -> Unit,
    returnToNetworkOnBrowserExit: Boolean,
    onLastBrowserLocationIdChanged: (String?) -> Unit,
    onLastBrowserDirectoryPathChanged: (String?) -> Unit,
    onBrowserLocationChanged: (BrowserLaunchState) -> Unit,
    onRememberSmbCredentials: (Long?, String, String?, String?) -> Unit,
    onRememberHttpCredentials: (Long?, String, String?, String?) -> Unit
) {
    val initialSmbAllowHostShareNavigation = browserLaunchState.smbSourceNodeId
        ?.let { sourceNodeId -> networkNodes.firstOrNull { it.id == sourceNodeId } }
        ?.let(::resolveNetworkNodeSmbSpec)
        ?.share
        ?.trim()
        ?.isEmpty() == true

    AppNavigationBrowserRouteSection(
        mainPadding = mainPadding,
        repository = repository,
        decoderExtensionArtworkHints = decoderExtensionArtworkHints,
        initialLocationId = browserLaunchState.locationId
            ?: if (rememberBrowserLocation) lastBrowserLocationId else null,
        initialDirectoryPath = browserLaunchState.directoryPath
            ?: if (rememberBrowserLocation) lastBrowserDirectoryPath else null,
        initialSmbSourceNodeId = browserLaunchState.smbSourceNodeId,
        initialSmbAllowHostShareNavigation = initialSmbAllowHostShareNavigation,
        initialHttpSourceNodeId = browserLaunchState.httpSourceNodeId,
        initialHttpRootPath = browserLaunchState.httpRootPath,
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
            onBrowserLaunchStateChanged(BrowserLaunchState())
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
    onOpenBrowser: (BrowserOpenRequest) -> Unit,
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
    browserLaunchState: BrowserLaunchState,
    browserFocusRestoreRequestToken: Int,
    showParentDirectoryEntry: Boolean,
    showFileIconChipBackground: Boolean,
    onVisiblePlayableFilesChanged: (List<File>) -> Unit,
    onBrowserLaunchStateChanged: (BrowserLaunchState) -> Unit,
    onReturnToNetworkOnBrowserExitChanged: (Boolean) -> Unit,
    returnToNetworkOnBrowserExit: Boolean,
    onLastBrowserLocationIdChanged: (String?) -> Unit,
    onLastBrowserDirectoryPathChanged: (String?) -> Unit,
    onBrowserLocationChanged: (BrowserLaunchState) -> Unit,
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
                browserLaunchState = browserLaunchState,
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
                onBrowserLaunchStateChanged = onBrowserLaunchStateChanged,
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
