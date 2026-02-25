package com.flopster101.siliconplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.flopster101.siliconplayer.ui.screens.FileBrowserScreen
import com.flopster101.siliconplayer.ui.screens.NetworkBrowserScreen
import com.flopster101.siliconplayer.ui.screens.SmbFileBrowserScreen
import java.io.File

@Composable
internal fun MainHomeRouteHost(
    mainPadding: PaddingValues,
    currentTrackPath: String?,
    currentTrackTitle: String,
    currentTrackArtist: String,
    recentFolders: List<RecentPathEntry>,
    recentPlayedFiles: List<RecentPathEntry>,
    storagePresentationForEntry: (RecentPathEntry) -> StoragePresentation,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    onOpenLibrary: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenUrlOrPath: () -> Unit,
    onOpenRecentFolder: (RecentPathEntry) -> Unit,
    onPlayRecentFile: (RecentPathEntry) -> Unit,
    onPersistRecentFileMetadata: (RecentPathEntry, String, String) -> Unit,
    onRecentFolderAction: (RecentPathEntry, FolderEntryAction) -> Unit,
    onRecentFileAction: (RecentPathEntry, SourceEntryAction) -> Unit,
    canShareRecentFile: (RecentPathEntry) -> Boolean
) {
    Box(modifier = Modifier.padding(mainPadding)) {
        HomeScreen(
            currentTrackPath = currentTrackPath,
            currentTrackTitle = currentTrackTitle,
            currentTrackArtist = currentTrackArtist,
            recentFolders = recentFolders,
            recentPlayedFiles = recentPlayedFiles,
            storagePresentationForEntry = storagePresentationForEntry,
            bottomContentPadding = bottomContentPadding,
            onOpenLibrary = onOpenLibrary,
            onOpenNetwork = onOpenNetwork,
            onOpenUrlOrPath = onOpenUrlOrPath,
            onOpenRecentFolder = onOpenRecentFolder,
            onPlayRecentFile = onPlayRecentFile,
            onPersistRecentFileMetadata = onPersistRecentFileMetadata,
            onRecentFolderAction = onRecentFolderAction,
            onRecentFileAction = onRecentFileAction,
            canShareRecentFile = canShareRecentFile
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
    onBrowseSmbSource: (String) -> Unit
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
            onBrowseSmbSource = onBrowseSmbSource
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
    restoreFocusedItemRequestToken: Int,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    showParentDirectoryEntry: Boolean,
    showFileIconChipBackground: Boolean,
    backHandlingEnabled: Boolean,
    playingFile: File?,
    onVisiblePlayableFilesChanged: (List<File>) -> Unit,
    onExitBrowser: () -> Unit,
    onBrowserLocationChanged: (String?, String?) -> Unit,
    onFileSelected: (File, String?) -> Unit,
    onOpenRemoteSource: (String) -> Unit
) {
    val initialSmbSpec = initialDirectoryPath?.let(::parseSmbSourceSpecFromInput)
    Box(modifier = Modifier.padding(mainPadding)) {
        if (initialSmbSpec != null) {
            LaunchedEffect(initialSmbSpec) {
                onVisiblePlayableFilesChanged(emptyList())
            }
            SmbFileBrowserScreen(
                sourceSpec = initialSmbSpec,
                bottomContentPadding = bottomContentPadding,
                backHandlingEnabled = backHandlingEnabled,
                onExitBrowser = onExitBrowser,
                onOpenRemoteSource = onOpenRemoteSource
            )
        } else {
            FileBrowserScreen(
                repository = repository,
                decoderExtensionArtworkHints = decoderExtensionArtworkHints,
                initialLocationId = initialLocationId,
                initialDirectoryPath = initialDirectoryPath,
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
                onFileSelected = onFileSelected
            )
        }
    }
}
