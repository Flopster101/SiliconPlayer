package com.flopster101.siliconplayer.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.SmbBrowserEntry
import com.flopster101.siliconplayer.SmbSourceSpec
import com.flopster101.siliconplayer.SmbAuthenticationFailureReason
import com.flopster101.siliconplayer.AppDefaults
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.buildSmbEntrySourceSpec
import com.flopster101.siliconplayer.buildSmbDisplayUri
import com.flopster101.siliconplayer.buildSmbRequestUri
import com.flopster101.siliconplayer.buildSmbSourceId
import com.flopster101.siliconplayer.decodePercentEncodedForDisplay
import com.flopster101.siliconplayer.fileMatchesSupportedExtensions
import com.flopster101.siliconplayer.inferredPrimaryExtensionForName
import com.flopster101.siliconplayer.DecoderArtworkHint
import com.flopster101.siliconplayer.BrowserLaunchState
import com.flopster101.siliconplayer.joinSmbRelativePath
import com.flopster101.siliconplayer.SmbBrowserListLocation
import com.flopster101.siliconplayer.SmbBrowserListingAdapter
import com.flopster101.siliconplayer.ManualSmbAuthCoordinator
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.normalizeSmbPathForShare
import com.flopster101.siliconplayer.resolveSmbAuthenticationFailureReason
import com.flopster101.siliconplayer.rememberDialogLazyListScrollbarAlpha
import com.flopster101.siliconplayer.smbAuthenticationFailureMessage
import com.flopster101.siliconplayer.adaptiveDialogModifier
import com.flopster101.siliconplayer.adaptiveDialogProperties
import com.flopster101.siliconplayer.RemotePlayableSourceIdsHolder
import com.flopster101.siliconplayer.R
import com.flopster101.siliconplayer.SmbRemoteExportRequest
import com.flopster101.siliconplayer.RemoteExportCancelledException
import com.flopster101.siliconplayer.data.ensureArchiveMounted
import com.flopster101.siliconplayer.data.buildArchiveDirectoryPath
import com.flopster101.siliconplayer.prepareRemoteExportFile
import com.flopster101.siliconplayer.session.exportFilesToTree
import com.flopster101.siliconplayer.session.ExportConflictDecision
import com.flopster101.siliconplayer.session.ExportNameConflict
import java.io.File
import java.util.Locale
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private val SMB_ICON_BOX_SIZE = 38.dp
private val SMB_ICON_GLYPH_SIZE = 26.dp

private enum class SmbBrowserPane {
    Loading,
    Error,
    Empty,
    Entries
}

private data class SmbBrowserContentState(
    val pane: SmbBrowserPane,
    val pathKey: String
)

@Composable
internal fun SmbFileBrowserScreen(
    sourceSpec: SmbSourceSpec,
    bottomContentPadding: Dp,
    backHandlingEnabled: Boolean,
    allowHostShareNavigation: Boolean,
    onExitBrowser: () -> Unit,
    onOpenRemoteSource: (String) -> Unit,
    onOpenRemoteSourceAsCached: (String) -> Unit,
    onRememberSmbCredentials: (Long?, String, String?, String?) -> Unit,
    sourceNodeId: Long?,
    onBrowserLocationChanged: (BrowserLaunchState) -> Unit
) {
    val context = LocalContext.current
    val browserPrefs = remember(context) {
        context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }
    val showUnsupportedFiles = browserPrefs.getBoolean(
        AppPreferenceKeys.BROWSER_SHOW_UNSUPPORTED_FILES,
        AppDefaults.Browser.showUnsupportedFiles
    )
    val showHiddenFilesAndFolders = browserPrefs.getBoolean(
        AppPreferenceKeys.BROWSER_SHOW_HIDDEN_FILES_AND_FOLDERS,
        AppDefaults.Browser.showHiddenFilesAndFolders
    )
    val coroutineScope = rememberCoroutineScope()
    val smbListingAdapter = remember { SmbBrowserListingAdapter() }
    val allowCredentialRemember = sourceNodeId != null
    val adhocSessionCredentials: Pair<String?, String?>? = remember(
        sourceNodeId,
        sourceSpec.host,
        sourceSpec.share
    ) {
        if (sourceNodeId == null) {
            ManualSmbAuthCoordinator.credentialsFor(sourceSpec)
        } else {
            null
        }
    }
    val screenSessionKey = remember(
        sourceNodeId,
        sourceSpec.host,
        sourceSpec.share
    ) {
        "node=${sourceNodeId ?: "adhoc"}|host=${sourceSpec.host}|share=${sourceSpec.share}"
    }
    val launchShare = remember(sourceSpec.share) { sourceSpec.share.trim() }
    val canBrowseHostShares = allowHostShareNavigation || launchShare.isBlank()
    val sourceId = remember(screenSessionKey, sourceSpec) { buildSmbSourceId(sourceSpec) }

    var currentShare by remember(screenSessionKey) { mutableStateOf(launchShare) }
    var currentSubPath by remember(screenSessionKey) {
        mutableStateOf(if (launchShare.isBlank()) "" else normalizeSmbPathForShare(sourceSpec.path).orEmpty())
    }
    var entries by remember(screenSessionKey) { mutableStateOf<List<SmbBrowserEntry>>(emptyList()) }
    var isLoading by remember(screenSessionKey) { mutableStateOf(false) }
    var errorMessage by remember(screenSessionKey) { mutableStateOf<String?>(null) }
    var listJob by remember(screenSessionKey) { mutableStateOf<Job?>(null) }
    val loadingLogLines = remember(screenSessionKey) { mutableStateListOf<String>() }
    var authDialogVisible by remember(screenSessionKey) { mutableStateOf(false) }
    var authDialogErrorMessage by remember(screenSessionKey) { mutableStateOf<String?>(null) }
    var authDialogUsername by remember(screenSessionKey) {
        mutableStateOf(sourceSpec.username ?: adhocSessionCredentials?.first ?: "")
    }
    var authDialogPassword by remember(screenSessionKey) { mutableStateOf("") }
    var authDialogPasswordVisible by remember(screenSessionKey) { mutableStateOf(false) }
    var authRememberPassword by remember(screenSessionKey, sourceNodeId) { mutableStateOf(allowCredentialRemember) }
    var pendingReloadAfterAuth by remember(screenSessionKey) { mutableStateOf(false) }
    var sessionUsername by remember(screenSessionKey) {
        mutableStateOf(sourceSpec.username ?: adhocSessionCredentials?.first)
    }
    var sessionPassword by remember(screenSessionKey) {
        mutableStateOf(sourceSpec.password ?: adhocSessionCredentials?.second)
    }
    var loadRequestSequence by remember(screenSessionKey) { mutableStateOf(0) }
    var browserNavDirection by remember(screenSessionKey) { mutableStateOf(BrowserPageNavDirection.Neutral) }
    var hasLoadedInitialDirectory by remember(screenSessionKey) { mutableStateOf(false) }
    val credentialsSpec = remember(screenSessionKey, sourceSpec.host, sessionUsername, sessionPassword) {
        sourceSpec.copy(
            host = sourceSpec.host,
            share = "",
            path = null,
            username = sessionUsername,
            password = sessionPassword
        )
    }
    val supportedExtensions = remember {
        NativeBridge.getSupportedExtensions()
            .asSequence()
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
            .toSet()
    }
    val decoderExtensionArtworkHints = rememberBrowserDecoderArtworkHints()
    val browserSearchController = rememberBrowserSearchController()
    val browserSelectionController = rememberBrowserSelectionController<String>()
    var browserInfoFields by remember(screenSessionKey) { mutableStateOf<List<BrowserInfoField>>(emptyList()) }
    var showBrowserInfoDialog by remember(screenSessionKey) { mutableStateOf(false) }
    var pendingExportTargets by remember(screenSessionKey) { mutableStateOf<List<SmbSelectionFileTarget>>(emptyList()) }
    var exportConflictDialogState by remember(screenSessionKey) { mutableStateOf<BrowserExportConflictDialogState?>(null) }
    var exportDownloadProgressState by remember(screenSessionKey) { mutableStateOf<BrowserRemoteExportProgressState?>(null) }
    var exportDownloadJob by remember(screenSessionKey) { mutableStateOf<Job?>(null) }
    var archiveOpenJob by remember(screenSessionKey) { mutableStateOf<Job?>(null) }
    val directoryEntriesCache = remember(screenSessionKey) { mutableStateMapOf<String, List<SmbBrowserEntry>>() }

    fun appendLoadingLog(message: String) {
        val lineNumber = loadingLogLines.size + 1
        loadingLogLines += "[${lineNumber.toString().padStart(2, '0')}] $message"
        val maxLines = 80
        if (loadingLogLines.size > maxLines) {
            repeat(loadingLogLines.size - maxLines) {
                loadingLogLines.removeAt(0)
            }
        }
    }

    fun effectivePath(): String? {
        val sub = normalizeSmbPathForShare(currentSubPath).orEmpty()
        if (currentShare.isBlank()) return null
        return sub.ifBlank { null }
    }

    fun isSharePickerMode(): Boolean = currentShare.isBlank()

    fun currentDirectorySourceId(): String {
        return buildSmbSourceId(
            credentialsSpec.copy(
                share = currentShare,
                path = effectivePath()
            )
        )
    }

    fun directoryCacheKeyFor(share: String, subPath: String): String {
        val normalizedShare = share.trim()
        val normalizedSubPath = if (normalizedShare.isBlank()) {
            ""
        } else {
            normalizeSmbPathForShare(subPath).orEmpty()
        }
        return buildSmbSourceId(
            credentialsSpec.copy(
                share = normalizedShare,
                path = normalizedSubPath.ifBlank { null }
            )
        )
    }

    fun updatePlayableRemoteSources(
        entriesForNavigation: List<SmbBrowserEntry>,
        share: String,
        pathInsideShare: String?
    ) {
        if (share.isBlank()) {
            RemotePlayableSourceIdsHolder.current = emptyList()
            return
        }
        val playableSourceIds = entriesForNavigation
            .asSequence()
            .filter { entry ->
                !entry.isDirectory &&
                    (showHiddenFilesAndFolders || (!entry.isHidden && !entry.name.startsWith("."))) &&
                    browserArchiveCapabilityForName(entry.name) == BrowserArchiveCapability.None &&
                    fileMatchesSupportedExtensions(File(entry.name), supportedExtensions)
            }
            .map { entry ->
                val targetPath = joinSmbRelativePath(pathInsideShare.orEmpty(), entry.name)
                val targetSpec = buildSmbEntrySourceSpec(
                    credentialsSpec.copy(share = share),
                    targetPath
                )
                buildSmbRequestUri(targetSpec)
            }
            .toList()
        RemotePlayableSourceIdsHolder.current = playableSourceIds
    }

    fun abortActiveLoad() {
        listJob?.cancel()
        listJob = null
        loadRequestSequence += 1
        isLoading = false
    }

    fun loadCurrentDirectory() {
        listJob?.cancel()
        loadRequestSequence += 1
        val requestSequence = loadRequestSequence
        isLoading = true
        errorMessage = null
        RemotePlayableSourceIdsHolder.current = emptyList()
        loadingLogLines.clear()
        val share = currentShare
        val pathInsideShare = effectivePath()
        appendLoadingLog("Connecting to smb://${credentialsSpec.host}")
        if (share.isBlank()) {
            appendLoadingLog("Enumerating host shares")
        } else {
            appendLoadingLog("Opening share '$share'")
            appendLoadingLog(
                if (pathInsideShare.isNullOrBlank()) {
                    "Listing share root directory"
                } else {
                    "Listing '$pathInsideShare'"
                }
            )
        }
        listJob = coroutineScope.launch {
            val result = smbListingAdapter.list(
                location = SmbBrowserListLocation(
                    spec = credentialsSpec.copy(share = share),
                    pathInsideShare = pathInsideShare,
                    listHostShares = share.isBlank()
                )
            ).map { it.entries }
            if (requestSequence != loadRequestSequence) {
                return@launch
            }
            result.onSuccess { resolved ->
                entries = resolved
                errorMessage = null
                directoryEntriesCache[directoryCacheKeyFor(share, pathInsideShare.orEmpty())] = resolved
                onBrowserLocationChanged(
                    BrowserLaunchState(
                        directoryPath = currentDirectorySourceId(),
                        smbSourceNodeId = sourceNodeId
                    )
                )
                updatePlayableRemoteSources(
                    entriesForNavigation = resolved,
                    share = share,
                    pathInsideShare = pathInsideShare
                )
                val folders = resolved.count { it.isDirectory }
                val files = resolved.size - folders
                appendLoadingLog("Found ${resolved.size} entries")
                appendLoadingLog("$folders folders, $files files")
                appendLoadingLog("Load finished")
            }.onFailure { throwable ->
                entries = emptyList()
                RemotePlayableSourceIdsHolder.current = emptyList()
                val authFailureReason = resolveSmbAuthenticationFailureReason(throwable)?.let { reason ->
                    if (
                        (
                            reason == SmbAuthenticationFailureReason.Unknown ||
                                reason == SmbAuthenticationFailureReason.WrongPassword ||
                                reason == SmbAuthenticationFailureReason.WrongCredentials
                            ) &&
                        credentialsSpec.username.isNullOrBlank() &&
                        credentialsSpec.password.isNullOrBlank()
                    ) {
                        SmbAuthenticationFailureReason.AuthenticationRequired
                    } else {
                        reason
                    }
                }
                if (authFailureReason != null) {
                    val authMessage = smbAuthenticationFailureMessage(authFailureReason)
                    authDialogErrorMessage = authMessage
                    if (!authDialogVisible) {
                        authDialogUsername = credentialsSpec.username.orEmpty()
                        authDialogPassword = ""
                        authDialogPasswordVisible = false
                        authRememberPassword = allowCredentialRemember
                    }
                    authDialogVisible = true
                    errorMessage = authMessage
                } else {
                    errorMessage = throwable.message ?: if (share.isBlank()) {
                        "Unable to list SMB shares"
                    } else {
                        "Unable to list SMB directory"
                    }
                }
                appendLoadingLog(
                    "Load failed: ${throwable.message ?: throwable.javaClass.simpleName ?: "Unknown error"}"
                )
            }
            isLoading = false
            listJob = null
        }
    }

    fun openDirectory(
        targetShare: String,
        targetSubPath: String,
        navigationDirection: BrowserPageNavDirection = BrowserPageNavDirection.Forward,
        forceReload: Boolean = false
    ) {
        browserNavDirection = navigationDirection
        val normalizedShare = targetShare.trim()
        val normalizedSubPath = if (normalizedShare.isBlank()) {
            ""
        } else {
            normalizeSmbPathForShare(targetSubPath).orEmpty()
        }
        currentShare = normalizedShare
        currentSubPath = normalizedSubPath

        val cacheKey = directoryCacheKeyFor(normalizedShare, normalizedSubPath)
        val cachedEntries = directoryEntriesCache[cacheKey]
        if (!forceReload && cachedEntries != null) {
            abortActiveLoad()
            entries = cachedEntries
            errorMessage = null
            loadingLogLines.clear()
            val pathInsideShare = if (normalizedShare.isBlank()) null else normalizedSubPath.ifBlank { null }
            updatePlayableRemoteSources(
                entriesForNavigation = cachedEntries,
                share = normalizedShare,
                pathInsideShare = pathInsideShare
            )
            onBrowserLocationChanged(
                BrowserLaunchState(
                    directoryPath = currentDirectorySourceId(),
                    smbSourceNodeId = sourceNodeId
                )
            )
            return
        }

        loadCurrentDirectory()
    }

    fun navigateUpWithinBrowser(): Boolean {
        if (currentSubPath.isNotBlank()) {
            openDirectory(
                targetShare = currentShare,
                targetSubPath = currentSubPath.substringBeforeLast('/', missingDelimiterValue = ""),
                navigationDirection = BrowserPageNavDirection.Backward
            )
            return true
        }
        if (canBrowseHostShares && currentShare.isNotBlank()) {
            openDirectory(
                targetShare = "",
                targetSubPath = "",
                navigationDirection = BrowserPageNavDirection.Backward
            )
            return true
        }
        return false
    }

    DisposableEffect(Unit) {
        onDispose {
            listJob?.cancel()
            exportDownloadJob?.cancel()
            archiveOpenJob?.cancel()
        }
    }

    LaunchedEffect(sourceSpec, screenSessionKey) {
        val desiredShare = sourceSpec.share.trim()
        val desiredSubPath = if (desiredShare.isBlank()) "" else normalizeSmbPathForShare(sourceSpec.path).orEmpty()
        val sameNavigation = currentShare == desiredShare && normalizeSmbPathForShare(currentSubPath).orEmpty() == desiredSubPath
        val desiredUsername = sourceSpec.username ?: sessionUsername ?: adhocSessionCredentials?.first
        val desiredPassword = sourceSpec.password ?: sessionPassword ?: adhocSessionCredentials?.second
        val sameCredentials = sessionUsername == desiredUsername && sessionPassword == desiredPassword
        if (sameNavigation && sameCredentials && hasLoadedInitialDirectory) {
            return@LaunchedEffect
        }
        browserSearchController.hide()
        currentShare = desiredShare
        currentSubPath = desiredSubPath
        authDialogVisible = false
        authDialogErrorMessage = null
        authDialogUsername = desiredUsername.orEmpty()
        authDialogPassword = ""
        authDialogPasswordVisible = false
        authRememberPassword = allowCredentialRemember
        sessionUsername = desiredUsername
        sessionPassword = desiredPassword
        hasLoadedInitialDirectory = true
        openDirectory(
            targetShare = desiredShare,
            targetSubPath = desiredSubPath,
            navigationDirection = BrowserPageNavDirection.Neutral
        )
    }

    LaunchedEffect(pendingReloadAfterAuth, sessionUsername, sessionPassword) {
        if (!pendingReloadAfterAuth) return@LaunchedEffect
        pendingReloadAfterAuth = false
        openDirectory(
            targetShare = currentShare,
            targetSubPath = currentSubPath,
            navigationDirection = BrowserPageNavDirection.Neutral,
            forceReload = true
        )
    }

    BackHandler(enabled = backHandlingEnabled) {
        if (browserSelectionController.isSelectionMode) {
            browserSelectionController.exitSelectionMode()
            return@BackHandler
        }
        if (!navigateUpWithinBrowser()) {
            onExitBrowser()
        }
    }

    val canNavigateUp = currentSubPath.isNotBlank() || (canBrowseHostShares && currentShare.isNotBlank())
    val sharePickerMode = isSharePickerMode()
    val browsableEntries = remember(
        entries,
        supportedExtensions,
        showUnsupportedFiles,
        showHiddenFilesAndFolders
    ) {
        entries.filter { entry ->
            shouldShowRemoteBrowserEntry(
                name = entry.name,
                isDirectory = entry.isDirectory,
                supportedExtensions = supportedExtensions,
                showUnsupportedFiles = showUnsupportedFiles,
                showHiddenFilesAndFolders = showHiddenFilesAndFolders,
                isHiddenHint = entry.isHidden
            )
        }
    }
    val filteredEntries = remember(browsableEntries, browserSearchController.debouncedQuery) {
        if (browserSearchController.debouncedQuery.isBlank()) {
            browsableEntries
        } else {
            browsableEntries.filter { entry ->
                matchesBrowserSearchQuery(entry.name, browserSearchController.debouncedQuery)
            }
        }
    }
    val browserContentState = remember(currentShare, currentSubPath, isLoading, errorMessage, browsableEntries.isEmpty()) {
        val key = "$currentShare|$currentSubPath"
        SmbBrowserContentState(
            pane = when {
                isLoading -> SmbBrowserPane.Loading
                !errorMessage.isNullOrBlank() -> SmbBrowserPane.Error
                browsableEntries.isEmpty() -> SmbBrowserPane.Empty
                else -> SmbBrowserPane.Entries
            },
            pathKey = key
        )
    }
    val entriesListState = rememberLazyListState()
    val nonEntriesListState = rememberLazyListState()
    var selectorExpanded by remember(screenSessionKey) { mutableStateOf(false) }
    val directoryScrollbarAlpha = rememberDialogLazyListScrollbarAlpha(
        enabled = browserContentState.pane == SmbBrowserPane.Entries,
        listState = entriesListState,
        flashKey = "${browserContentState.pathKey}|${filteredEntries.size}|${browserSearchController.debouncedQuery}",
        label = "smbBrowserDirectoryScrollbarAlpha"
    )
    val subtitle = buildString {
        append("smb://")
        append(credentialsSpec.host)
        if (currentShare.isNotBlank()) {
            append('/')
            append(decodePercentEncodedForDisplay(currentShare) ?: currentShare)
            effectivePath()?.let { path ->
                if (path.isNotBlank()) {
                    append('/')
                    append(
                        path
                            .split('/')
                            .joinToString("/") { segment ->
                                decodePercentEncodedForDisplay(segment) ?: segment
                            }
                    )
                }
            }
        }
    }
    val selectionBasePath = remember(currentShare, currentSubPath) { effectivePath().orEmpty() }
    val entrySelectionKeyFor: (SmbBrowserEntry) -> String = remember(sharePickerMode, selectionBasePath) {
        { entry ->
            if (sharePickerMode) {
                "share:${entry.name}"
            } else {
                "entry:${joinSmbRelativePath(selectionBasePath, entry.name)}:${entry.isDirectory}"
            }
        }
    }

    fun selectedDownloadFileTargets(): List<SmbSelectionFileTarget> {
        val selectedEntries = browsableEntries.filter { entry ->
            browserSelectionController.selectedKeys.contains(entrySelectionKeyFor(entry))
        }
        if (sharePickerMode) return emptyList()
        return selectedEntries
            .asSequence()
            .filter { !it.isDirectory }
            .mapNotNull { entry ->
                val targetPath = joinSmbRelativePath(effectivePath().orEmpty(), entry.name)
                val targetSpec = buildSmbEntrySourceSpec(
                    credentialsSpec.copy(share = currentShare),
                    targetPath
                )
                SmbSelectionFileTarget(
                    sourceId = buildSmbSourceId(targetSpec),
                    requestUri = buildSmbRequestUri(targetSpec),
                    smbSpec = targetSpec,
                    displayName = decodePercentEncodedForDisplay(entry.name) ?: entry.name
                )
            }
            .toList()
    }

    fun selectedPlayableFileTargets(): List<SmbSelectionFileTarget> {
        return selectedDownloadFileTargets().filter { target ->
            browserArchiveCapabilityForName(target.displayName) == BrowserArchiveCapability.None
        }
    }

    fun openArchiveEntry(entry: SmbBrowserEntry) {
        val targetPath = joinSmbRelativePath(effectivePath().orEmpty(), entry.name)
        val targetSpec = buildSmbEntrySourceSpec(
            credentialsSpec.copy(share = currentShare),
            targetPath
        )
        val sourceId = buildSmbSourceId(targetSpec)
        exportDownloadJob?.cancel()
        archiveOpenJob?.cancel()
        archiveOpenJob = coroutineScope.launch {
            exportDownloadProgressState = BrowserRemoteExportProgressState(
                currentIndex = 1,
                totalCount = 1,
                currentFileName = entry.name,
                loadState = null
            )
            val prepared = prepareRemoteExportFile(
                context = context,
                request = SmbRemoteExportRequest(
                    sourceId = sourceId,
                    smbSpec = targetSpec,
                    preferredFileName = entry.name
                ),
                onStatus = { loadState ->
                    exportDownloadProgressState = BrowserRemoteExportProgressState(
                        currentIndex = 1,
                        totalCount = 1,
                        currentFileName = entry.name,
                        loadState = loadState
                    )
                }
            )
            val cachedItem = prepared.getOrNull()
            if (cachedItem == null) {
                exportDownloadProgressState = null
                val error = prepared.exceptionOrNull()
                if (error is RemoteExportCancelledException || error is CancellationException) {
                    Toast.makeText(context, "Archive open cancelled", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                Toast.makeText(context, "Failed to open archive", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val capability = browserArchiveCapabilityForName(cachedItem.sourceFile.name)
            if (capability != BrowserArchiveCapability.Browsable) {
                exportDownloadProgressState = null
                Toast.makeText(context, "Archive format not supported yet", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val mountDirectory = withContext(Dispatchers.IO) {
                ensureArchiveMounted(context, cachedItem.sourceFile)
            }
            exportDownloadProgressState = null
            onBrowserLocationChanged(
                BrowserLaunchState(
                    directoryPath = buildArchiveDirectoryPath(sourceId),
                    smbSourceNodeId = sourceNodeId
                )
            )
        }
    }

    fun showSelectionInfoDialog() {
        val selectedEntries = entries.filter { entry ->
            browserSelectionController.selectedKeys.contains(entrySelectionKeyFor(entry))
        }
        if (selectedEntries.isEmpty()) return
        val infoEntries = selectedEntries.map { entry ->
            BrowserInfoEntry(
                name = entry.name,
                isDirectory = entry.isDirectory,
                sizeBytes = if (entry.isDirectory) null else entry.sizeBytes
            )
        }
        val pathLabel = buildSmbDisplayUri(
            credentialsSpec.copy(
                share = currentShare,
                path = effectivePath()
            )
        )
        val hostLabel = buildString {
            append(credentialsSpec.host)
            if (currentShare.isNotBlank()) {
                append('/')
                append(decodePercentEncodedForDisplay(currentShare) ?: currentShare)
            }
        }
        browserInfoFields = buildBrowserInfoFields(
            entries = infoEntries,
            path = pathLabel,
            storageOrHostLabel = "Host",
            storageOrHost = hostLabel
        )
        showBrowserInfoDialog = true
    }

    suspend fun requestExportConflictDecision(
        conflict: ExportNameConflict
    ): ExportConflictDecision = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            var applyToAll = false
            fun finish(decision: ExportConflictDecision) {
                exportConflictDialogState = null
                if (continuation.isActive) {
                    continuation.resume(decision)
                }
            }
            exportConflictDialogState = BrowserExportConflictDialogState(
                fileName = conflict.fileName,
                applyToAll = applyToAll,
                onApplyToAllChange = { checked ->
                    applyToAll = checked
                    exportConflictDialogState = exportConflictDialogState?.copy(applyToAll = checked)
                },
                onResolve = { action, applyAll ->
                    finish(
                        ExportConflictDecision(
                            action = action,
                            applyToAll = applyAll
                        )
                    )
                }
            )
            continuation.invokeOnCancellation {
                exportConflictDialogState = null
            }
        }
    }

    val exportDirectoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        val targets = pendingExportTargets
        pendingExportTargets = emptyList()
        if (targets.isEmpty()) return@rememberLauncherForActivityResult
        if (treeUri == null) {
            Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        exportDownloadJob?.cancel()
        exportDownloadJob = coroutineScope.launch {
            var preparationFailed = 0
            val exportItems = mutableListOf<com.flopster101.siliconplayer.session.ExportFileItem>()
            for ((index, target) in targets.withIndex()) {
                if (!isActive) break
                exportDownloadProgressState = BrowserRemoteExportProgressState(
                    currentIndex = index + 1,
                    totalCount = targets.size,
                    currentFileName = target.displayName,
                    loadState = null
                )
                val prepared = prepareRemoteExportFile(
                    context = context,
                    request = SmbRemoteExportRequest(
                        sourceId = target.sourceId,
                        smbSpec = target.smbSpec,
                        preferredFileName = target.displayName
                    ),
                    onStatus = { loadState ->
                        exportDownloadProgressState = BrowserRemoteExportProgressState(
                            currentIndex = index + 1,
                            totalCount = targets.size,
                            currentFileName = target.displayName,
                            loadState = loadState
                        )
                    }
                )
                val preparedItem = prepared.getOrNull()
                if (preparedItem != null) {
                    exportItems += preparedItem
                } else {
                    val error = prepared.exceptionOrNull()
                    if (error is RemoteExportCancelledException || error is CancellationException) {
                        exportDownloadProgressState = null
                        Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    preparationFailed++
                }
            }
            exportDownloadProgressState = null

            val result = exportFilesToTree(
                context = context,
                treeUri = treeUri,
                exportItems = exportItems,
                onNameConflict = { conflict ->
                    requestExportConflictDecision(conflict)
                }
            )
            val totalFailed = preparationFailed + result.failedCount
            Toast.makeText(
                context,
                when {
                    result.cancelled -> "Download cancelled"
                    else -> {
                        "Downloaded ${result.exportedCount} file(s)" +
                            buildString {
                                if (result.skippedCount > 0) {
                                    append(" (${result.skippedCount} skipped)")
                                }
                                if (totalFailed > 0) {
                                    append(" ($totalFailed failed)")
                                }
                            }
                    }
                },
                Toast.LENGTH_SHORT
            ).show()
            if (!result.cancelled) {
                browserSelectionController.exitSelectionMode()
            }
        }
    }

    LaunchedEffect(currentShare, currentSubPath) {
        exportDownloadJob?.cancel()
        archiveOpenJob?.cancel()
        exportDownloadProgressState = null
        exportConflictDialogState = null
        browserSelectionController.exitSelectionMode()
        showBrowserInfoDialog = false
        pendingExportTargets = emptyList()
    }

    Scaffold(
        topBar = {
            Column {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectorExpanded = true }
                                .padding(horizontal = 2.dp)
                        ) {
                            Box {
                                BrowserToolbarSelectorLabel(
                                    expanded = selectorExpanded,
                                    onClick = { selectorExpanded = true }
                                )
                                DropdownMenu(
                                    expanded = selectorExpanded,
                                    onDismissRequest = { selectorExpanded = false }
                                ) {
                                    Text(
                                        text = "Storage locations",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("SMB server")
                                                Text(
                                                    text = credentialsSpec.host,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = NetworkIcons.SmbShare,
                                                contentDescription = null
                                            )
                                        },
                                        enabled = false,
                                        onClick = {}
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    Text(
                                        text = "Directory tree",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Coming soon") },
                                        enabled = false,
                                        onClick = {}
                                    )
                                }
                            }
                            BrowserToolbarPathRow(
                                icon = NetworkIcons.SmbShare,
                                subtitle = subtitle
                            )
                        }
                        BrowserSelectionToolbarControls(
                            visible = browserSelectionController.isSelectionMode,
                            canSelectAny = filteredEntries.isNotEmpty(),
                            onSelectAll = {
                                browserSelectionController.selectAll(filteredEntries.map(entrySelectionKeyFor))
                            },
                            onDeselectAll = { browserSelectionController.deselectAll() },
                            actionItems = listOf(
                                BrowserSelectionActionItem(
                                    label = "Play",
                                    icon = Icons.Default.PlayArrow,
                                    enabled = selectedPlayableFileTargets().size == 1,
                                    onClick = {
                                        val target = selectedPlayableFileTargets().singleOrNull()
                                        if (target != null) {
                                            browserSelectionController.exitSelectionMode()
                                            onOpenRemoteSource(target.requestUri)
                                        }
                                    }
                                ),
                                BrowserSelectionActionItem(
                                    label = "Play as cached",
                                    icon = Icons.Default.Save,
                                    enabled = selectedPlayableFileTargets().size == 1,
                                    onClick = {
                                        val target = selectedPlayableFileTargets().singleOrNull()
                                        if (target != null) {
                                            browserSelectionController.exitSelectionMode()
                                            onOpenRemoteSourceAsCached(target.requestUri)
                                        }
                                    }
                                ),
                                BrowserSelectionActionItem(
                                    label = if (selectedDownloadFileTargets().size == 1) {
                                        "Download file"
                                    } else {
                                        "Download files"
                                    },
                                    icon = Icons.Default.Link,
                                    enabled = selectedDownloadFileTargets().isNotEmpty(),
                                    onClick = {
                                        val targets = selectedDownloadFileTargets()
                                        if (targets.isNotEmpty()) {
                                            pendingExportTargets = targets
                                            exportDirectoryLauncher.launch(null)
                                        }
                                    }
                                ),
                                BrowserSelectionActionItem(
                                    label = "Info",
                                    icon = Icons.Default.Info,
                                    enabled = browserSelectionController.selectedKeys.isNotEmpty(),
                                    onClick = { showSelectionInfoDialog() }
                                )
                            ),
                            onCancel = { browserSelectionController.exitSelectionMode() }
                        )
                        BrowserToolbarSearchButton(
                            onClick = {
                                if (browserSearchController.isVisible) {
                                    browserSearchController.hide()
                                } else {
                                    browserSearchController.show()
                                }
                            }
                        )
                    }
                }
                BrowserSearchToolbarRow(
                    visible = browserSearchController.isVisible,
                    queryInput = browserSearchController.input,
                    onQueryInputChanged = browserSearchController::onInputChange,
                    onClose = browserSearchController::hide
                )
                HorizontalDivider()
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedContent(
                targetState = browserContentState,
                transitionSpec = {
                    val loadingTransition =
                        initialState.pane == SmbBrowserPane.Loading ||
                            targetState.pane == SmbBrowserPane.Loading
                    browserContentTransform(
                        navDirection = browserNavDirection,
                        loadingTransition = loadingTransition
                    )
                },
                label = "smbBrowserLoadingTransition",
                modifier = Modifier.fillMaxSize()
            ) { state ->
                val statePathParts = state.pathKey.split('|', limit = 2)
                val stateShare = statePathParts.firstOrNull().orEmpty()
                val stateSubPath = statePathParts.getOrNull(1).orEmpty()
                val stateSharePickerMode = stateShare.isBlank()
                val stateBrowsableEntries = if (state.pathKey == "$currentShare|$currentSubPath") {
                    browsableEntries
                } else {
                    directoryEntriesCache[directoryCacheKeyFor(stateShare, stateSubPath)]
                        ?.filter { entry ->
                            shouldShowRemoteBrowserEntry(
                                name = entry.name,
                                isDirectory = entry.isDirectory,
                                supportedExtensions = supportedExtensions,
                                showUnsupportedFiles = showUnsupportedFiles,
                                showHiddenFilesAndFolders = showHiddenFilesAndFolders,
                                isHiddenHint = entry.isHidden
                            )
                        }
                        .orEmpty()
                }
                val stateFilteredEntries = if (browserSearchController.debouncedQuery.isBlank()) {
                    stateBrowsableEntries
                } else {
                    stateBrowsableEntries.filter { entry ->
                        matchesBrowserSearchQuery(entry.name, browserSearchController.debouncedQuery)
                    }
                }
                val stateCanNavigateUp = stateSubPath.isNotBlank() ||
                    (canBrowseHostShares && stateShare.isNotBlank())
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = if (state.pane == SmbBrowserPane.Entries) {
                        entriesListState
                    } else {
                        nonEntriesListState
                    },
                    contentPadding = PaddingValues(bottom = bottomContentPadding)
                ) {
                if (stateCanNavigateUp && state.pane != SmbBrowserPane.Loading) {
                    item("parent") {
                        SmbParentDirectoryRow(
                            onClick = {
                                navigateUpWithinBrowser()
                            }
                        )
                    }
                }

                if (state.pane == SmbBrowserPane.Loading) {
                    item("loading") {
                        SmbLoadingCard(
                            title = if (stateSharePickerMode) {
                                "Loading SMB shares..."
                            } else {
                                "Loading SMB directory..."
                            },
                            subtitle = if (stateSharePickerMode) {
                                "Fetching available shares from host"
                            } else {
                                "Fetching folder entries"
                            },
                                logLines = loadingLogLines
                        )
                    }
                } else if (state.pane == SmbBrowserPane.Error) {
                    item("error") {
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.errorContainer)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WarningAmber,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = if (stateSharePickerMode) {
                                            "Unable to list SMB shares"
                                        } else {
                                            "Unable to open SMB directory"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Text(
                                    text = errorMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                TextButton(onClick = {
                                    openDirectory(
                                        targetShare = currentShare,
                                        targetSubPath = currentSubPath,
                                        navigationDirection = BrowserPageNavDirection.Neutral,
                                        forceReload = true
                                    )
                                }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                } else if (state.pane == SmbBrowserPane.Empty) {
                    item("empty") {
                        SmbInfoCard(
                            title = if (stateSharePickerMode) {
                                "No SMB shares found"
                            } else {
                                "This directory is empty"
                            },
                            subtitle = if (stateSharePickerMode) {
                                "No accessible disk shares on this host"
                            } else {
                                "No files or folders found"
                            }
                        )
                    }
                } else {
                    if (stateFilteredEntries.isEmpty() && browserSearchController.debouncedQuery.isNotBlank()) {
                        item("search-empty") {
                            BrowserSearchNoResultsCard(query = browserSearchController.debouncedQuery)
                        }
                    } else {
                        itemsIndexed(
                            stateFilteredEntries,
                            key = { _, entry -> "${entry.isDirectory}:${entry.name}" }
                        ) { index, entry ->
                            val entrySelectionKey = entrySelectionKeyFor(entry)
                            val hasSelectedAbove = if (index > 0) {
                                val aboveKey = entrySelectionKeyFor(stateFilteredEntries[index - 1])
                                browserSelectionController.selectedKeys.contains(aboveKey)
                            } else {
                                false
                            }
                            val hasSelectedBelow = if (index < stateFilteredEntries.lastIndex) {
                                val belowKey = entrySelectionKeyFor(stateFilteredEntries[index + 1])
                                browserSelectionController.selectedKeys.contains(belowKey)
                            } else {
                                false
                            }
                            SmbEntryRow(
                                entry = entry,
                                supportedExtensions = supportedExtensions,
                                decoderExtensionArtworkHints = decoderExtensionArtworkHints,
                                isSelected = browserSelectionController.selectedKeys.contains(entrySelectionKey),
                                hasSelectedAbove = hasSelectedAbove,
                                hasSelectedBelow = hasSelectedBelow,
                                showAsShare = stateSharePickerMode,
                                onLongClick = {
                                    if (browserSelectionController.isSelectionMode) {
                                        val didSelectRange =
                                            browserSelectionController.selectedKeys.size == 1 &&
                                                browserSelectionController.selectRangeTo(
                                                    key = entrySelectionKey,
                                                    orderedKeys = stateFilteredEntries.map { stateEntry ->
                                                        entrySelectionKeyFor(stateEntry)
                                                    }
                                                )
                                        if (!didSelectRange) {
                                            browserSelectionController.toggleSelection(entrySelectionKey)
                                        }
                                    } else {
                                        browserSelectionController.enterSelectionWith(entrySelectionKey)
                                    }
                                },
                                onClick = {
                                    if (browserSelectionController.isSelectionMode) {
                                        browserSelectionController.toggleSelection(entrySelectionKey)
                                        return@SmbEntryRow
                                    }
                                    if (stateSharePickerMode) {
                                        openDirectory(
                                            targetShare = entry.name,
                                            targetSubPath = "",
                                            navigationDirection = BrowserPageNavDirection.Forward
                                        )
                                    } else if (entry.isDirectory) {
                                        openDirectory(
                                            targetShare = stateShare,
                                            targetSubPath = joinSmbRelativePath(stateSubPath, entry.name),
                                            navigationDirection = BrowserPageNavDirection.Forward
                                        )
                                    } else {
                                        when (browserArchiveCapabilityForName(entry.name)) {
                                            BrowserArchiveCapability.Browsable -> openArchiveEntry(entry)
                                            BrowserArchiveCapability.KnownUnsupported -> {
                                                Toast.makeText(
                                                    context,
                                                    "Archive format not supported yet",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            BrowserArchiveCapability.None -> {
                                                val targetPath = joinSmbRelativePath(stateSubPath, entry.name)
                                                val targetSpec = buildSmbEntrySourceSpec(
                                                    credentialsSpec.copy(share = stateShare),
                                                    targetPath
                                                )
                                                onOpenRemoteSource(buildSmbRequestUri(targetSpec))
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
            }
            if (browserContentState.pane == SmbBrowserPane.Entries) {
                BrowserLazyListScrollbar(
                    listState = entriesListState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(
                            top = 8.dp,
                            end = 2.dp,
                            bottom = bottomContentPadding + 8.dp
                        )
                        .fillMaxHeight()
                        .width(4.dp)
                        .graphicsLayer(alpha = directoryScrollbarAlpha)
                )
            }
        }
    }

    if (authDialogVisible) {
        AlertDialog(
            modifier = adaptiveDialogModifier(),
            properties = adaptiveDialogProperties(),
            onDismissRequest = {
                authDialogVisible = false
                authDialogPasswordVisible = false
            },
            title = { Text("SMB authentication required") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    authDialogErrorMessage?.let { message ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = authDialogUsername,
                        onValueChange = { authDialogUsername = it },
                        singleLine = true,
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = remoteAuthDialogTextFieldColors()
                    )
                    OutlinedTextField(
                        value = authDialogPassword,
                        onValueChange = { authDialogPassword = it },
                        singleLine = true,
                        label = { Text("Password") },
                        visualTransformation = if (authDialogPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { authDialogPasswordVisible = !authDialogPasswordVisible }) {
                                Icon(
                                    imageVector = if (authDialogPasswordVisible) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                    contentDescription = if (authDialogPasswordVisible) {
                                        "Hide password"
                                    } else {
                                        "Show password"
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = remoteAuthDialogTextFieldColors()
                    )
                    if (allowCredentialRemember) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { authRememberPassword = !authRememberPassword },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = authRememberPassword,
                                onCheckedChange = { checked -> authRememberPassword = checked }
                            )
                            Text(
                                text = "Remember password",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                val hasCredentials = authDialogUsername.trim().isNotEmpty() || authDialogPassword.trim().isNotEmpty()
                TextButton(
                    enabled = hasCredentials,
                    onClick = {
                        val normalizedUsername = authDialogUsername.trim().ifBlank { null }
                        val normalizedPassword = authDialogPassword.trim().ifBlank { null }
                        sessionUsername = normalizedUsername
                        sessionPassword = normalizedPassword
                        if (allowCredentialRemember && authRememberPassword) {
                            onRememberSmbCredentials(
                                sourceNodeId,
                                sourceId,
                                normalizedUsername,
                                normalizedPassword
                            )
                        } else if (sourceNodeId == null) {
                            ManualSmbAuthCoordinator.rememberCredentials(
                                credentialsSpec.copy(
                                    share = currentShare,
                                    username = normalizedUsername,
                                    password = normalizedPassword
                                ),
                                normalizedUsername,
                                normalizedPassword
                            )
                        }
                        authDialogVisible = false
                        authDialogPasswordVisible = false
                        pendingReloadAfterAuth = true
                    }
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    authDialogVisible = false
                    authDialogPasswordVisible = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBrowserInfoDialog) {
        BrowserInfoDialog(
            title = "Info",
            fields = browserInfoFields,
            onDismiss = { showBrowserInfoDialog = false }
        )
    }

    exportConflictDialogState?.let { state ->
        BrowserExportConflictDialog(state = state)
    }

    exportDownloadProgressState?.let { state ->
        BrowserRemoteExportProgressDialog(
            state = state,
            onCancel = {
                exportDownloadProgressState = null
                exportDownloadJob?.cancel()
                archiveOpenJob?.cancel()
            }
        )
    }
}

private data class SmbSelectionFileTarget(
    val sourceId: String,
    val requestUri: String,
    val smbSpec: SmbSourceSpec,
    val displayName: String
)

@Composable
private fun remoteAuthDialogTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
)

@Composable
private fun SmbParentDirectoryRow(
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(SMB_ICON_BOX_SIZE),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(11.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Parent directory",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(SMB_ICON_GLYPH_SIZE)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "..",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1
            )
            Text(
                text = "Parent directory",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SmbEntryRow(
    entry: SmbBrowserEntry,
    supportedExtensions: Set<String>,
    decoderExtensionArtworkHints: Map<String, DecoderArtworkHint>,
    isSelected: Boolean,
    hasSelectedAbove: Boolean = false,
    hasSelectedBelow: Boolean = false,
    showAsShare: Boolean,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val visualKind = if (showAsShare) {
        BrowserRemoteEntryVisualKind.Directory
    } else {
        browserRemoteEntryVisualKind(
            name = entry.name,
            isDirectory = entry.isDirectory,
            supportedExtensions = supportedExtensions,
            decoderExtensionArtworkHints = decoderExtensionArtworkHints
        )
    }
    val selectionShape = RoundedCornerShape(
        topStart = if (hasSelectedAbove) 0.dp else 18.dp,
        topEnd = if (hasSelectedAbove) 0.dp else 18.dp,
        bottomStart = if (hasSelectedBelow) 0.dp else 18.dp,
        bottomEnd = if (hasSelectedBelow) 0.dp else 18.dp
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(selectionShape)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val treatAsContainer = showAsShare || entry.isDirectory
        val chipContainerColor = if (treatAsContainer) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
        val chipContentColor = if (treatAsContainer) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
        Box(
            modifier = Modifier
                .size(SMB_ICON_BOX_SIZE)
                .background(
                    color = chipContainerColor,
                    shape = RoundedCornerShape(11.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (showAsShare) {
                Icon(
                    imageVector = NetworkIcons.SmbShare,
                    contentDescription = null,
                    tint = chipContentColor,
                    modifier = Modifier.size(SMB_ICON_GLYPH_SIZE)
                )
            } else {
                when (visualKind) {
                    BrowserRemoteEntryVisualKind.Directory -> {
                        BrowserRemoteEntryIcon(
                            visualKind = BrowserRemoteEntryVisualKind.Directory,
                            tint = chipContentColor,
                            modifier = Modifier.size(SMB_ICON_GLYPH_SIZE)
                        )
                    }

                    BrowserRemoteEntryVisualKind.ArchiveFile,
                    BrowserRemoteEntryVisualKind.TrackedFile,
                    BrowserRemoteEntryVisualKind.GameFile,
                    BrowserRemoteEntryVisualKind.VideoFile,
                    BrowserRemoteEntryVisualKind.AudioFile,
                    BrowserRemoteEntryVisualKind.UnsupportedFile -> BrowserRemoteEntryIcon(
                        visualKind = visualKind,
                        tint = chipContentColor,
                        modifier = Modifier.size(SMB_ICON_GLYPH_SIZE)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (showAsShare) {
                    "Share"
                } else if (entry.isDirectory) {
                    "Folder"
                } else if (browserArchiveCapabilityForName(entry.name) != BrowserArchiveCapability.None) {
                    "Archive • ${formatSmbFileSize(entry.sizeBytes)}"
                } else {
                    "${inferSmbFormatLabel(entry.name)} • ${formatSmbFileSize(entry.sizeBytes)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SmbInfoCard(
    title: String,
    subtitle: String
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SmbLoadingCard(
    title: String,
    subtitle: String,
    logLines: List<String>
) {
    BrowserLoadingCard(
        icon = NetworkIcons.SmbShare,
        title = title,
        subtitle = subtitle,
        logLines = logLines,
        waitingLine = "[00] Waiting for SMB response..."
    )
}

private fun inferSmbFormatLabel(name: String): String {
    val ext = inferredPrimaryExtensionForName(name)
    return ext?.uppercase(Locale.ROOT) ?: "Unknown"
}

private fun formatSmbFileSize(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    if (safeBytes < 1024L) return "$safeBytes B"
    val kb = safeBytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.ROOT, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.ROOT, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.ROOT, "%.1f GB", gb)
}
