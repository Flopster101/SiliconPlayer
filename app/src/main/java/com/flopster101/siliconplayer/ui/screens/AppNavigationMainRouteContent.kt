package com.flopster101.siliconplayer

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.Dp
import com.flopster101.siliconplayer.data.parseArchiveLogicalPath
import com.flopster101.siliconplayer.data.FileRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AppNavigationHomeContentSection(
    mainPadding: PaddingValues,
    context: Context,
    prefs: SharedPreferences,
    currentTrackPath: String?,
    metadataTitle: String,
    metadataArtist: String,
    isPlaying: Boolean,
    pinnedHomeEntries: List<HomePinnedEntry>,
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
    onPinnedHomeEntriesChanged: (List<HomePinnedEntry>) -> Unit,
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
        pinnedHomeEntries = pinnedHomeEntries,
        recentFolders = recentFolders,
        recentPlayedFiles = recentPlayedFiles,
        storagePresentationForEntry = { entry ->
            storagePresentationForEntry(context, entry, storageDescriptors, networkNodes)
        },
        storagePresentationForPinnedEntry = { entry ->
            storagePresentationForEntry(context, entry.asRecentPathEntry(), storageDescriptors, networkNodes)
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
        onOpenPinnedFolder = { entry ->
            onOpenRecentFolderFromEntry(
                entry = entry.asRecentPathEntry(),
                networkNodes = networkNodes,
                onOpenBrowser = onOpenBrowser
            )
            onCurrentViewChanged(MainView.Browser)
        },
        onPlayPinnedFile = { entry ->
            playRecentFileEntryAction(
                cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR),
                entry = entry.asRecentPathEntry(),
                networkNodes = networkNodes,
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
        onOpenRecentFolder = { entry ->
            onOpenRecentFolderFromEntry(
                entry = entry,
                networkNodes = networkNodes,
                onOpenBrowser = onOpenBrowser
            )
            onCurrentViewChanged(MainView.Browser)
        },
        onPlayRecentFile = { entry ->
            playRecentFileEntryAction(
                cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR),
                entry = entry,
                networkNodes = networkNodes,
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
        onPinRecentFolder = { entry ->
            val updated = buildUpdatedPinnedHomeEntries(
                current = pinnedHomeEntries,
                candidate = HomePinnedEntry(
                    path = entry.path,
                    isFolder = true,
                    locationId = entry.locationId,
                    title = entry.title,
                    sourceNodeId = entry.sourceNodeId
                ),
                maxItems = PINNED_HOME_ENTRIES_LIMIT
            )
            onPinnedHomeEntriesChanged(updated)
        },
        onPinRecentFile = { entry ->
            val updated = buildUpdatedPinnedHomeEntries(
                current = pinnedHomeEntries,
                candidate = HomePinnedEntry(
                    path = entry.path,
                    isFolder = false,
                    locationId = entry.locationId,
                    title = entry.title,
                    artist = entry.artist,
                    decoderName = entry.decoderName,
                    sourceNodeId = entry.sourceNodeId,
                    artworkThumbnailCacheKey = entry.artworkThumbnailCacheKey
                ),
                maxItems = PINNED_HOME_ENTRIES_LIMIT
            )
            onPinnedHomeEntriesChanged(updated)
        },
        onPinnedFolderAction = { entry, action ->
            applyPinnedFolderAction(
                context = context,
                entry = entry,
                action = action,
                pinnedEntries = pinnedHomeEntries,
                onPinnedEntriesChanged = onPinnedHomeEntriesChanged,
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
                },
                networkNodes = networkNodes
            )
        },
        onPinnedFileAction = { entry, action ->
            applyPinnedSourceAction(
                context = context,
                entry = entry,
                action = action,
                pinnedEntries = pinnedHomeEntries,
                onPinnedEntriesChanged = onPinnedHomeEntriesChanged,
                resolveShareableFileForRecent = { pinned ->
                    runtimeDelegates.resolveShareableFileForRecent(pinned.asRecentPathEntry())
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
                },
                networkNodes = networkNodes
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
        onClearPinnedEntries = {
            onPinnedHomeEntriesChanged(emptyList())
        },
        onClearRecentFolders = {
            onRecentFoldersChanged(emptyList())
            writeRecentEntries(
                prefs,
                AppPreferenceKeys.RECENT_FOLDERS,
                emptyList(),
                recentFoldersLimit
            )
        },
        onClearRecentPlayed = {
            onRecentPlayedFilesChanged(emptyList())
            writeRecentEntries(
                prefs,
                AppPreferenceKeys.RECENT_PLAYED_FILES,
                emptyList(),
                recentFilesLimit
            )
        },
        canShareRecentFile = { entry ->
            runtimeDelegates.resolveShareableFileForRecent(entry) != null
        },
        canSharePinnedFile = { entry ->
            runtimeDelegates.resolveShareableFileForRecent(entry.asRecentPathEntry()) != null
        }
    )
}

@Composable
internal fun AppNavigationNetworkContentSection(
    mainPadding: PaddingValues,
    bottomContentPadding: Dp,
    isPlayerExpanded: Boolean,
    pinnedHomeEntries: List<HomePinnedEntry>,
    onPinnedHomeEntriesChanged: (List<HomePinnedEntry>) -> Unit,
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
        },
        pinnedHomeEntries = pinnedHomeEntries,
        onPinHomeEntry = { recentEntry, isFolder ->
            val updated = buildUpdatedPinnedHomeEntries(
                current = pinnedHomeEntries,
                candidate = HomePinnedEntry(
                    path = recentEntry.path,
                    isFolder = isFolder,
                    locationId = recentEntry.locationId,
                    title = recentEntry.title,
                    artist = recentEntry.artist,
                    decoderName = recentEntry.decoderName,
                    sourceNodeId = recentEntry.sourceNodeId,
                    artworkThumbnailCacheKey = recentEntry.artworkThumbnailCacheKey
                ),
                maxItems = PINNED_HOME_ENTRIES_LIMIT
            )
            onPinnedHomeEntriesChanged(updated)
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
    pinnedHomeEntries: List<HomePinnedEntry>,
    onPinnedHomeEntriesChanged: (List<HomePinnedEntry>) -> Unit,
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
        onRememberHttpCredentials = onRememberHttpCredentials,
        pinnedHomeEntries = pinnedHomeEntries,
        onPinHomeEntry = { recentEntry, isFolder ->
            val updated = buildUpdatedPinnedHomeEntries(
                current = pinnedHomeEntries,
                candidate = HomePinnedEntry(
                    path = recentEntry.path,
                    isFolder = isFolder,
                    locationId = recentEntry.locationId,
                    title = recentEntry.title,
                    artist = recentEntry.artist,
                    decoderName = recentEntry.decoderName,
                    sourceNodeId = recentEntry.sourceNodeId,
                    artworkThumbnailCacheKey = recentEntry.artworkThumbnailCacheKey
                ),
                maxItems = PINNED_HOME_ENTRIES_LIMIT
            )
            onPinnedHomeEntriesChanged(updated)
        }
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
    currentPlaybackRequestUrl: String?,
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
    val pinnedHomeEntriesState = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(readPinnedHomeEntries(prefs))
    }
    val pinnedHomeEntries = pinnedHomeEntriesState.value
    val onPinnedHomeEntriesChanged: (List<HomePinnedEntry>) -> Unit = { updated ->
        pinnedHomeEntriesState.value = updated
        writePinnedHomeEntries(prefs, updated)
    }
    val activePlaybackSource = currentPlaybackSourceId ?: selectedFile?.absolutePath
    LaunchedEffect(
        activePlaybackSource,
        currentPlaybackRequestUrl,
        metadataTitle,
        metadataArtist,
        isPlaying,
        pinnedHomeEntries
    ) {
        if (!isPlaying) return@LaunchedEffect
        val sourceId = activePlaybackSource ?: return@LaunchedEffect
        val targetPinned = pinnedHomeEntries.firstOrNull { entry ->
            !entry.isFolder && samePath(entry.path, sourceId)
        } ?: return@LaunchedEffect
        val normalizedTitle = metadataTitle.trim()
        val normalizedArtist = metadataArtist.trim()
        val decoderName = NativeBridge.getCurrentDecoderName().trim().takeIf { it.isNotEmpty() }
        var updatedPinned = mergePinnedFileMetadataAndArtwork(
            current = pinnedHomeEntries,
            path = sourceId,
            title = normalizedTitle,
            artist = normalizedArtist,
            decoderName = decoderName,
            artworkThumbnailCacheKey = null
        )
        if (targetPinned.artworkThumbnailCacheKey.isNullOrBlank()) {
            val artworkCacheKey = withContext(Dispatchers.IO) {
                ensureRecentArtworkThumbnailCached(
                    context = context.applicationContext,
                    sourceId = sourceId,
                    requestUrlHint = currentPlaybackRequestUrl
                )
            }
            updatedPinned = mergePinnedFileMetadataAndArtwork(
                current = updatedPinned,
                path = sourceId,
                title = null,
                artist = null,
                decoderName = null,
                artworkThumbnailCacheKey = artworkCacheKey
            )
        }
        if (updatedPinned != pinnedHomeEntries) {
            onPinnedHomeEntriesChanged(updatedPinned)
        }
    }
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
                pinnedHomeEntries = pinnedHomeEntries,
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
                onPinnedHomeEntriesChanged = onPinnedHomeEntriesChanged,
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
                pinnedHomeEntries = pinnedHomeEntries,
                onPinnedHomeEntriesChanged = onPinnedHomeEntriesChanged,
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
                pinnedHomeEntries = pinnedHomeEntries,
                onPinnedHomeEntriesChanged = onPinnedHomeEntriesChanged,
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

private fun onOpenRecentFolderFromEntry(
    entry: RecentPathEntry,
    networkNodes: List<NetworkNode>,
    onOpenBrowser: (BrowserOpenRequest) -> Unit
) {
    val archiveLogicalPath = parseArchiveLogicalPath(entry.path)
    if (archiveLogicalPath != null) {
        val archiveSourcePath = archiveLogicalPath.first
        val isArchiveSmb = parseSmbSourceSpecFromInput(archiveSourcePath) != null
        val isArchiveHttp = parseHttpSourceSpecFromInput(archiveSourcePath) != null
        onOpenBrowser(
            browserOpenRequest(
                locationId = null,
                directoryPath = entry.path,
                smbSourceNodeId = if (isArchiveSmb) entry.sourceNodeId else null,
                httpSourceNodeId = if (isArchiveHttp) entry.sourceNodeId else null
            )
        )
        return
    }
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
        return
    }
    val httpSpec = parseHttpSourceSpecFromInput(entry.path)
    if (httpSpec != null) {
        val httpTarget = resolveHttpRecentOpenTarget(
            targetSpec = httpSpec,
            networkNodes = networkNodes,
            preferredSourceNodeId = entry.sourceNodeId
        )
        onOpenBrowser(
            browserOpenRequest(
                directoryPath = httpTarget.requestUri,
                httpSourceNodeId = httpTarget.sourceNodeId
            )
        )
        return
    }
    onOpenBrowser(
        browserOpenRequest(
            locationId = entry.locationId,
            directoryPath = entry.path
        )
    )
}
