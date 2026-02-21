package com.flopster101.siliconplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.io.File

@Composable
internal fun AppNavigationHomeRouteSection(
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
    MainHomeRouteHost(
        mainPadding = mainPadding,
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

@Composable
internal fun AppNavigationBrowserRouteSection(
    mainPadding: PaddingValues,
    repository: com.flopster101.siliconplayer.data.FileRepository,
    decoderExtensionArtworkHints: Map<String, DecoderArtworkHint>,
    initialLocationId: String?,
    initialDirectoryPath: String?,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    backHandlingEnabled: Boolean,
    playingFile: File?,
    onVisiblePlayableFilesChanged: (List<File>) -> Unit,
    onExitBrowser: () -> Unit,
    onBrowserLocationChanged: (String?, String?) -> Unit,
    onFileSelected: (File, String?) -> Unit
) {
    MainBrowserRouteHost(
        mainPadding = mainPadding,
        repository = repository,
        decoderExtensionArtworkHints = decoderExtensionArtworkHints,
        initialLocationId = initialLocationId,
        initialDirectoryPath = initialDirectoryPath,
        bottomContentPadding = bottomContentPadding,
        backHandlingEnabled = backHandlingEnabled,
        playingFile = playingFile,
        onVisiblePlayableFilesChanged = onVisiblePlayableFilesChanged,
        onExitBrowser = onExitBrowser,
        onBrowserLocationChanged = onBrowserLocationChanged,
        onFileSelected = onFileSelected
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

@Composable
internal fun AppNavigationMainScaffoldSection(
    currentView: MainView,
    onOpenPlayerSurface: () -> Unit,
    onHomeRequested: () -> Unit,
    onSettingsRequested: () -> Unit,
    homeContent: @Composable (PaddingValues) -> Unit,
    browserContent: @Composable (PaddingValues) -> Unit,
    settingsContent: @Composable (PaddingValues) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        MainNavigationScaffold(
            currentView = currentView,
            onOpenPlayerSurface = onOpenPlayerSurface,
            onHomeRequested = onHomeRequested,
            onSettingsRequested = onSettingsRequested
        ) { mainPadding, targetView ->
            when (targetView) {
                MainView.Home -> homeContent(mainPadding)
                MainView.Browser -> browserContent(mainPadding)
                MainView.Settings -> settingsContent(mainPadding)
            }
        }
    }
}
