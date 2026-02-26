package com.flopster101.siliconplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.flopster101.siliconplayer.ui.screens.FileBrowserScreen
import com.flopster101.siliconplayer.ui.screens.HttpFileBrowserScreen
import com.flopster101.siliconplayer.ui.screens.NetworkBrowserScreen
import com.flopster101.siliconplayer.ui.screens.SmbFileBrowserScreen
import com.flopster101.siliconplayer.RemotePlayableSourceIdsHolder
import java.io.File
import java.util.Locale

private enum class MainBrowserMode {
    Local,
    Smb,
    Http
}

private fun smbBrowserSessionKey(
    spec: SmbSourceSpec?,
    sourceNodeId: Long?
): String? {
    val resolved = spec ?: return null
    val nodeScope = sourceNodeId?.toString() ?: "adhoc"
    val host = resolved.host.trim().lowercase(Locale.ROOT)
    val share = resolved.share.trim().lowercase(Locale.ROOT)
    val username = resolved.username?.trim().orEmpty()
    return "node=$nodeScope|host=$host|share=$share|user=$username"
}

private fun httpBrowserSessionKey(
    spec: HttpSourceSpec?,
    sourceNodeId: Long?,
    browserRootPath: String?
): String? {
    val resolved = spec ?: return null
    val nodeScope = sourceNodeId?.toString() ?: "adhoc"
    val rootPath = browserRootPath?.trim().takeUnless { it.isNullOrBlank() } ?: "/"
    val host = resolved.host.trim().lowercase(Locale.ROOT)
    val scheme = resolved.scheme.trim().lowercase(Locale.ROOT)
    val username = resolved.username?.trim().orEmpty()
    val port = resolved.port ?: -1
    return "node=$nodeScope|scheme=$scheme|host=$host|port=$port|user=$username|root=$rootPath"
}

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
    onBrowseSmbSource: (String, Long?) -> Unit,
    onBrowseHttpSource: (String, Long?, String?) -> Unit
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
            onBrowseHttpSource = onBrowseHttpSource
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
    onBrowserLocationChanged: (String?, String?) -> Unit,
    onFileSelected: (File, String?) -> Unit,
    onOpenRemoteSource: (String) -> Unit,
    onOpenRemoteSourceAsCached: (String) -> Unit,
    onRememberSmbCredentials: (Long?, String, String?, String?) -> Unit,
    onRememberHttpCredentials: (Long?, String, String?, String?) -> Unit
) {
    val requestedSmbSpec = initialDirectoryPath?.let(::parseSmbSourceSpecFromInput)
    val requestedHttpSpec = if (requestedSmbSpec != null) {
        null
    } else {
        initialDirectoryPath?.let(::parseHttpSourceSpecFromInput)
    }
    val requestedMode = when {
        requestedSmbSpec != null -> MainBrowserMode.Smb
        requestedHttpSpec != null -> MainBrowserMode.Http
        else -> MainBrowserMode.Local
    }
    val requestedSmbSessionKey = smbBrowserSessionKey(requestedSmbSpec, initialSmbSourceNodeId)
    val requestedHttpSessionKey = httpBrowserSessionKey(
        requestedHttpSpec,
        initialHttpSourceNodeId,
        initialHttpRootPath
    )

    var activeMode by remember { mutableStateOf(requestedMode) }
    var activeSmbSpec by remember { mutableStateOf(requestedSmbSpec) }
    var activeHttpSpec by remember { mutableStateOf(requestedHttpSpec) }
    var activeSmbSessionKey by remember { mutableStateOf(requestedSmbSessionKey) }
    var activeHttpSessionKey by remember { mutableStateOf(requestedHttpSessionKey) }

    LaunchedEffect(
        requestedMode,
        requestedSmbSpec,
        requestedHttpSpec,
        requestedSmbSessionKey,
        requestedHttpSessionKey
    ) {
        when (requestedMode) {
            MainBrowserMode.Smb -> {
                if (activeMode != MainBrowserMode.Smb || activeSmbSessionKey != requestedSmbSessionKey) {
                    activeMode = MainBrowserMode.Smb
                    activeSmbSpec = requestedSmbSpec
                    activeSmbSessionKey = requestedSmbSessionKey
                }
            }

            MainBrowserMode.Http -> {
                if (activeMode != MainBrowserMode.Http || activeHttpSessionKey != requestedHttpSessionKey) {
                    activeMode = MainBrowserMode.Http
                    activeHttpSpec = requestedHttpSpec
                    activeHttpSessionKey = requestedHttpSessionKey
                }
            }

            MainBrowserMode.Local -> {
                if (activeMode != MainBrowserMode.Local) {
                    activeMode = MainBrowserMode.Local
                }
            }
        }
    }

    val renderMode = if (requestedMode != activeMode) requestedMode else activeMode
    val renderSmbSpec = if (renderMode == MainBrowserMode.Smb) {
        if (requestedMode == MainBrowserMode.Smb) requestedSmbSpec else activeSmbSpec
    } else {
        null
    }
    val renderHttpSpec = if (renderMode == MainBrowserMode.Http) {
        if (requestedMode == MainBrowserMode.Http) requestedHttpSpec else activeHttpSpec
    } else {
        null
    }
    val renderSmbSessionKey = if (renderMode == MainBrowserMode.Smb) {
        if (requestedMode == MainBrowserMode.Smb) requestedSmbSessionKey else activeSmbSessionKey
    } else {
        null
    }
    val renderHttpSessionKey = if (renderMode == MainBrowserMode.Http) {
        if (requestedMode == MainBrowserMode.Http) requestedHttpSessionKey else activeHttpSessionKey
    } else {
        null
    }

    Box(modifier = Modifier.padding(mainPadding)) {
        if (renderMode == MainBrowserMode.Smb && renderSmbSpec != null) {
            val smbSpec = requireNotNull(renderSmbSpec)
            LaunchedEffect(renderSmbSessionKey) {
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
                sourceNodeId = initialSmbSourceNodeId,
                onBrowserLocationChanged = { smbSourceId ->
                    onBrowserLocationChanged(null, smbSourceId)
                }
            )
        } else if (renderMode == MainBrowserMode.Http && renderHttpSpec != null) {
            val httpSpec = requireNotNull(renderHttpSpec)
            LaunchedEffect(renderHttpSessionKey) {
                onVisiblePlayableFilesChanged(emptyList())
            }
            HttpFileBrowserScreen(
                sourceSpec = httpSpec,
                browserRootPath = initialHttpRootPath,
                bottomContentPadding = bottomContentPadding,
                backHandlingEnabled = backHandlingEnabled,
                onExitBrowser = onExitBrowser,
                onOpenRemoteSource = onOpenRemoteSource,
                onOpenRemoteSourceAsCached = onOpenRemoteSourceAsCached,
                onRememberHttpCredentials = onRememberHttpCredentials,
                sourceNodeId = initialHttpSourceNodeId,
                onBrowserLocationChanged = { httpSourceId ->
                    onBrowserLocationChanged(null, httpSourceId)
                }
            )
        } else {
            LaunchedEffect(initialLocationId, initialDirectoryPath) {
                RemotePlayableSourceIdsHolder.current = emptyList()
            }
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
