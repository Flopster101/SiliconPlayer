package com.flopster101.siliconplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.flopster101.siliconplayer.ui.screens.FileBrowserScreen
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
    onOpenUrlOrPath: () -> Unit,
    onOpenRecentFolder: (RecentPathEntry) -> Unit,
    onPlayRecentFile: (RecentPathEntry) -> Unit,
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
            onOpenUrlOrPath = onOpenUrlOrPath,
            onOpenRecentFolder = onOpenRecentFolder,
            onPlayRecentFile = onPlayRecentFile,
            onRecentFolderAction = onRecentFolderAction,
            onRecentFileAction = onRecentFileAction,
            canShareRecentFile = canShareRecentFile
        )
    }
}

@Composable
internal fun MainBrowserRouteHost(
    mainPadding: PaddingValues,
    repository: com.flopster101.siliconplayer.data.FileRepository,
    initialLocationId: String?,
    initialDirectoryPath: String?,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    backHandlingEnabled: Boolean,
    playingFile: File?,
    onVisiblePlayableFilesChanged: (List<File>) -> Unit,
    onExitBrowser: () -> Unit,
    onBrowserLocationChanged: (String?, String?) -> Unit,
    onFileSelected: (File) -> Unit
) {
    Box(modifier = Modifier.padding(mainPadding)) {
        FileBrowserScreen(
            repository = repository,
            initialLocationId = initialLocationId,
            initialDirectoryPath = initialDirectoryPath,
            onVisiblePlayableFilesChanged = onVisiblePlayableFilesChanged,
            bottomContentPadding = bottomContentPadding,
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
