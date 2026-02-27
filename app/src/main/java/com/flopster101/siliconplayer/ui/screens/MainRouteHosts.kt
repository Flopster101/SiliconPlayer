package com.flopster101.siliconplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.flopster101.siliconplayer.ui.screens.FileBrowserScreen
import com.flopster101.siliconplayer.ui.screens.HttpFileBrowserScreen
import com.flopster101.siliconplayer.ui.screens.NetworkBrowserScreen
import com.flopster101.siliconplayer.ui.screens.SmbFileBrowserScreen
import com.flopster101.siliconplayer.RemotePlayableSourceIdsHolder
import java.io.File

@Composable
internal fun MainHomeRouteHost(
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
    canShareRecentFile: (RecentPathEntry) -> Boolean,
    canSharePinnedFile: (HomePinnedEntry) -> Boolean
) {
    Box(modifier = Modifier.padding(mainPadding)) {
        HomeScreen(
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
            canShareRecentFile = canShareRecentFile,
            canSharePinnedFile = canSharePinnedFile
        )
    }
}

@Composable
internal fun MainNetworkRouteHost(
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
    Box(modifier = Modifier.padding(mainPadding)) {
        NetworkBrowserScreen(
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
}

@Composable
internal fun MainBrowserRouteHost(
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
    val routeResolution = remember(
        initialLocationId,
        initialDirectoryPath,
        initialSmbSourceNodeId,
        initialHttpSourceNodeId,
        initialHttpRootPath
    ) {
        resolveBrowserRouteResolution(
            initialLocationId = initialLocationId,
            initialDirectoryPath = initialDirectoryPath,
            initialSmbSourceNodeId = initialSmbSourceNodeId,
            initialHttpSourceNodeId = initialHttpSourceNodeId,
            initialHttpRootPath = initialHttpRootPath
        )
    }
    val renderState = rememberBrowserRouteRenderState(routeResolution)

    Box(modifier = Modifier.padding(mainPadding)) {
        if (renderState.renderMode == BrowserRouteMode.Smb && renderState.renderSmbSpec != null) {
            val smbSpec = requireNotNull(renderState.renderSmbSpec)
            LaunchedEffect(renderState.renderSmbSessionKey) {
                onVisiblePlayableFilesChanged(emptyList())
            }
            SmbFileBrowserScreen(
                sourceSpec = smbSpec,
                bottomContentPadding = bottomContentPadding,
                backHandlingEnabled = backHandlingEnabled,
                allowHostShareNavigation = initialSmbAllowHostShareNavigation,
                onExitBrowser = onExitBrowser,
                onOpenRemoteSource = onOpenRemoteSource,
                onOpenRemoteSourceAsCached = onOpenRemoteSourceAsCached,
                onRememberSmbCredentials = onRememberSmbCredentials,
                sourceNodeId = routeResolution.requestedSmbSourceNodeId,
                onBrowserLocationChanged = onBrowserLocationChanged,
                pinnedHomeEntries = pinnedHomeEntries,
                onPinHomeEntry = onPinHomeEntry
            )
        } else if (renderState.renderMode == BrowserRouteMode.Http && renderState.renderHttpSpec != null) {
            val httpSpec = requireNotNull(renderState.renderHttpSpec)
            LaunchedEffect(renderState.renderHttpSessionKey) {
                onVisiblePlayableFilesChanged(emptyList())
            }
            HttpFileBrowserScreen(
                sourceSpec = httpSpec,
                browserRootPath = routeResolution.requestedHttpRootPath,
                bottomContentPadding = bottomContentPadding,
                backHandlingEnabled = backHandlingEnabled,
                onExitBrowser = onExitBrowser,
                onOpenRemoteSource = onOpenRemoteSource,
                onOpenRemoteSourceAsCached = onOpenRemoteSourceAsCached,
                onRememberHttpCredentials = onRememberHttpCredentials,
                sourceNodeId = routeResolution.requestedHttpSourceNodeId,
                onBrowserLocationChanged = onBrowserLocationChanged,
                pinnedHomeEntries = pinnedHomeEntries,
                onPinHomeEntry = onPinHomeEntry
            )
        } else {
            LaunchedEffect(routeResolution.requestedLocalLocationId, routeResolution.requestedLocalDirectoryPath) {
                RemotePlayableSourceIdsHolder.current = emptyList()
            }
            FileBrowserScreen(
                repository = repository,
                decoderExtensionArtworkHints = decoderExtensionArtworkHints,
                initialLocationId = routeResolution.requestedLocalLocationId,
                initialDirectoryPath = routeResolution.requestedLocalDirectoryPath,
                initialSmbSourceNodeId = routeResolution.requestedSmbSourceNodeId,
                initialHttpSourceNodeId = routeResolution.requestedHttpSourceNodeId,
                initialHttpRootPath = routeResolution.requestedHttpRootPath,
                restoreFocusedItemRequestToken = restoreFocusedItemRequestToken,
                onVisiblePlayableFilesChanged = onVisiblePlayableFilesChanged,
                bottomContentPadding = bottomContentPadding,
                showParentDirectoryEntry = showParentDirectoryEntry,
                showFileIconChipBackground = showFileIconChipBackground,
                backHandlingEnabled = backHandlingEnabled,
                onExitBrowser = onExitBrowser,
                onOpenSettings = null,
                showPrimaryTopBar = false,
                playingFile = playingFile,
                onBrowserLocationChanged = onBrowserLocationChanged,
                onFileSelected = onFileSelected,
                pinnedHomeEntries = pinnedHomeEntries,
                onPinHomeEntry = onPinHomeEntry
            )
        }
    }
}
