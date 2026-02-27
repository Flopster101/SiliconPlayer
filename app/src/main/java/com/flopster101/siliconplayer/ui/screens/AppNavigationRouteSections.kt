package com.flopster101.siliconplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.ExperimentalComposeUiApi
import java.io.File

@Composable
internal fun AppNavigationHomeRouteSection(
    mainPadding: PaddingValues,
    currentTrackPath: String?,
    currentTrackTitle: String,
    currentTrackArtist: String,
    pinnedHomeEntries: List<HomePinnedEntry>,
    recentFolders: List<RecentPathEntry>,
    recentPlayedFiles: List<RecentPathEntry>,
    storagePresentationForEntry: (RecentPathEntry) -> StoragePresentation,
    storagePresentationForPinnedEntry: (HomePinnedEntry) -> StoragePresentation,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    onOpenLibrary: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenUrlOrPath: () -> Unit,
    onOpenPinnedFolder: (HomePinnedEntry) -> Unit,
    onPlayPinnedFile: (HomePinnedEntry) -> Unit,
    onOpenRecentFolder: (RecentPathEntry) -> Unit,
    onPlayRecentFile: (RecentPathEntry) -> Unit,
    onPinRecentFolder: (RecentPathEntry) -> Unit,
    onPinRecentFile: (RecentPathEntry) -> Unit,
    onPinnedFolderAction: (HomePinnedEntry, FolderEntryAction) -> Unit,
    onPinnedFileAction: (HomePinnedEntry, SourceEntryAction) -> Unit,
    onPersistRecentFileMetadata: (RecentPathEntry, String, String) -> Unit,
    onRecentFolderAction: (RecentPathEntry, FolderEntryAction) -> Unit,
    onRecentFileAction: (RecentPathEntry, SourceEntryAction) -> Unit,
    onClearPinnedEntries: () -> Unit,
    onClearRecentFolders: () -> Unit,
    onClearRecentPlayed: () -> Unit,
    canShareRecentFile: (RecentPathEntry) -> Boolean,
    canSharePinnedFile: (HomePinnedEntry) -> Boolean
) {
    MainHomeRouteHost(
        mainPadding = mainPadding,
        currentTrackPath = currentTrackPath,
        currentTrackTitle = currentTrackTitle,
        currentTrackArtist = currentTrackArtist,
        pinnedHomeEntries = pinnedHomeEntries,
        recentFolders = recentFolders,
        recentPlayedFiles = recentPlayedFiles,
        storagePresentationForEntry = storagePresentationForEntry,
        storagePresentationForPinnedEntry = storagePresentationForPinnedEntry,
        bottomContentPadding = bottomContentPadding,
        onOpenLibrary = onOpenLibrary,
        onOpenNetwork = onOpenNetwork,
        onOpenUrlOrPath = onOpenUrlOrPath,
        onOpenPinnedFolder = onOpenPinnedFolder,
        onPlayPinnedFile = onPlayPinnedFile,
        onOpenRecentFolder = onOpenRecentFolder,
        onPlayRecentFile = onPlayRecentFile,
        onPinRecentFolder = onPinRecentFolder,
        onPinRecentFile = onPinRecentFile,
        onPinnedFolderAction = onPinnedFolderAction,
        onPinnedFileAction = onPinnedFileAction,
        onPersistRecentFileMetadata = onPersistRecentFileMetadata,
        onRecentFolderAction = onRecentFolderAction,
        onRecentFileAction = onRecentFileAction,
        onClearPinnedEntries = onClearPinnedEntries,
        onClearRecentFolders = onClearRecentFolders,
        onClearRecentPlayed = onClearRecentPlayed,
        canShareRecentFile = canShareRecentFile,
        canSharePinnedFile = canSharePinnedFile
    )
}

@Composable
internal fun AppNavigationNetworkRouteSection(
    mainPadding: PaddingValues,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    backHandlingEnabled: Boolean,
    nodes: List<NetworkNode>,
    currentFolderId: Long?,
    onExitNetwork: () -> Unit,
    onCurrentFolderIdChanged: (Long?) -> Unit,
    onNodesChanged: (List<NetworkNode>) -> Unit,
    onResolveRemoteSourceMetadata: (String, () -> Unit) -> Unit,
    onCancelPendingMetadataBackfill: () -> Unit,
    onOpenRemoteSource: (String) -> Unit,
    onBrowseSmbSource: (String, Long?) -> Unit,
    onBrowseHttpSource: (String, Long?, String?) -> Unit,
    pinnedHomeEntries: List<HomePinnedEntry>,
    onPinHomeEntry: (RecentPathEntry, Boolean) -> Unit
) {
    MainNetworkRouteHost(
        mainPadding = mainPadding,
        bottomContentPadding = bottomContentPadding,
        backHandlingEnabled = backHandlingEnabled,
        nodes = nodes,
        currentFolderId = currentFolderId,
        onExitNetwork = onExitNetwork,
        onCurrentFolderIdChanged = onCurrentFolderIdChanged,
        onNodesChanged = onNodesChanged,
        onResolveRemoteSourceMetadata = onResolveRemoteSourceMetadata,
        onCancelPendingMetadataBackfill = onCancelPendingMetadataBackfill,
        onOpenRemoteSource = onOpenRemoteSource,
        onBrowseSmbSource = onBrowseSmbSource,
        onBrowseHttpSource = onBrowseHttpSource,
        pinnedHomeEntries = pinnedHomeEntries,
        onPinHomeEntry = onPinHomeEntry
    )
}

@Composable
internal fun AppNavigationBrowserRouteSection(
    mainPadding: PaddingValues,
    repository: com.flopster101.siliconplayer.data.FileRepository,
    decoderExtensionArtworkHints: Map<String, DecoderArtworkHint>,
    initialLocationId: String?,
    initialDirectoryPath: String?,
    initialSmbSourceNodeId: Long?,
    initialSmbAllowHostShareNavigation: Boolean,
    initialHttpSourceNodeId: Long?,
    initialHttpRootPath: String?,
    restoreFocusedItemRequestToken: Int,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    showParentDirectoryEntry: Boolean,
    showFileIconChipBackground: Boolean,
    backHandlingEnabled: Boolean,
    playingFile: File?,
    onVisiblePlayableFilesChanged: (List<File>) -> Unit,
    onExitBrowser: () -> Unit,
    onBrowserLocationChanged: (BrowserLaunchState) -> Unit,
    onFileSelected: (File, String?) -> Unit,
    onOpenRemoteSource: (String) -> Unit,
    onOpenRemoteSourceAsCached: (String) -> Unit,
    onRememberSmbCredentials: (Long?, String, String?, String?) -> Unit,
    onRememberHttpCredentials: (Long?, String, String?, String?) -> Unit,
    pinnedHomeEntries: List<HomePinnedEntry>,
    onPinHomeEntry: (RecentPathEntry, Boolean) -> Unit
) {
    MainBrowserRouteHost(
        mainPadding = mainPadding,
        repository = repository,
        decoderExtensionArtworkHints = decoderExtensionArtworkHints,
        initialLocationId = initialLocationId,
        initialDirectoryPath = initialDirectoryPath,
        initialSmbSourceNodeId = initialSmbSourceNodeId,
        initialSmbAllowHostShareNavigation = initialSmbAllowHostShareNavigation,
        initialHttpSourceNodeId = initialHttpSourceNodeId,
        initialHttpRootPath = initialHttpRootPath,
        restoreFocusedItemRequestToken = restoreFocusedItemRequestToken,
        bottomContentPadding = bottomContentPadding,
        showParentDirectoryEntry = showParentDirectoryEntry,
        showFileIconChipBackground = showFileIconChipBackground,
        backHandlingEnabled = backHandlingEnabled,
        playingFile = playingFile,
        onVisiblePlayableFilesChanged = onVisiblePlayableFilesChanged,
        onExitBrowser = onExitBrowser,
        onBrowserLocationChanged = onBrowserLocationChanged,
        onFileSelected = onFileSelected,
        onOpenRemoteSource = onOpenRemoteSource,
        onOpenRemoteSourceAsCached = onOpenRemoteSourceAsCached,
        onRememberSmbCredentials = onRememberSmbCredentials,
        onRememberHttpCredentials = onRememberHttpCredentials,
        pinnedHomeEntries = pinnedHomeEntries,
        onPinHomeEntry = onPinHomeEntry
    )
}

@Composable
internal fun AppNavigationSettingsRouteSection(
    mainPadding: PaddingValues,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.padding(mainPadding)) {
        content()
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun AppNavigationMainScaffoldSection(
    currentView: MainView,
    mainContentFocusRequester: FocusRequester,
    canFocusMiniPlayer: Boolean,
    requestMiniPlayerFocus: () -> Unit,
    onHardwareNavigationInput: () -> Unit,
    onTouchInteraction: () -> Unit,
    onOpenPlayerSurface: () -> Unit,
    onHomeRequested: () -> Unit,
    onSettingsRequested: () -> Unit,
    homeContent: @Composable (PaddingValues) -> Unit,
    networkContent: @Composable (PaddingValues) -> Unit,
    browserContent: @Composable (PaddingValues) -> Unit,
    settingsContent: @Composable (PaddingValues) -> Unit
) {
    val focusManager = LocalFocusManager.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) {
                            onTouchInteraction()
                            focusManager.clearFocus(force = true)
                        }
                    }
                }
            }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                if (
                    keyEvent.key == Key.DirectionLeft ||
                    keyEvent.key == Key.DirectionRight ||
                    keyEvent.key == Key.DirectionUp ||
                    keyEvent.key == Key.DirectionDown ||
                    keyEvent.key == Key.DirectionCenter ||
                    keyEvent.key == Key.Enter ||
                    keyEvent.key == Key.NumPadEnter
                ) {
                    onHardwareNavigationInput()
                }
                if (!canFocusMiniPlayer) {
                    return@onPreviewKeyEvent false
                }
                val moveDirection = when (keyEvent.key) {
                    Key.DirectionLeft -> FocusDirection.Left
                    Key.DirectionRight -> FocusDirection.Right
                    else -> null
                }
                if (moveDirection == null) {
                    return@onPreviewKeyEvent false
                }
                val movedWithinMainContent = focusManager.moveFocus(moveDirection)
                if (movedWithinMainContent) {
                    true
                } else {
                    mainContentFocusRequester.saveFocusedChild()
                    requestMiniPlayerFocus()
                    true
                }
            }
    ) {
        MainNavigationScaffold(
            currentView = currentView,
            onOpenPlayerSurface = onOpenPlayerSurface,
            onHomeRequested = onHomeRequested,
            onSettingsRequested = onSettingsRequested,
            mainContentModifier = Modifier
                .focusRequester(mainContentFocusRequester)
                .focusRestorer()
        ) { mainPadding, targetView ->
            when (targetView) {
                MainView.Home -> homeContent(mainPadding)
                MainView.Network -> networkContent(mainPadding)
                MainView.Browser -> browserContent(mainPadding)
                MainView.Settings -> settingsContent(mainPadding)
            }
        }
    }
}
