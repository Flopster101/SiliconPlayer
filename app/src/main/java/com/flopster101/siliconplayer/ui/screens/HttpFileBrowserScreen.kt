package com.flopster101.siliconplayer.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.HttpAuthenticationFailureReason
import com.flopster101.siliconplayer.HttpBrowserEntry
import com.flopster101.siliconplayer.HttpRemoteExportRequest
import com.flopster101.siliconplayer.HttpSourceSpec
import com.flopster101.siliconplayer.RemoteExportCancelledException
import com.flopster101.siliconplayer.buildHttpDisplayUri
import com.flopster101.siliconplayer.buildHttpRequestUri
import com.flopster101.siliconplayer.buildHttpSourceId
import com.flopster101.siliconplayer.decodePercentEncodedForDisplay
import com.flopster101.siliconplayer.fileMatchesSupportedExtensions
import com.flopster101.siliconplayer.httpAuthenticationFailureMessage
import com.flopster101.siliconplayer.HttpBrowserListLocation
import com.flopster101.siliconplayer.HttpBrowserListingAdapter
import com.flopster101.siliconplayer.inferredPrimaryExtensionForName
import com.flopster101.siliconplayer.DecoderArtworkHint
import com.flopster101.siliconplayer.BrowserLaunchState
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.normalizeHttpDirectoryPath
import com.flopster101.siliconplayer.normalizeHttpPath
import com.flopster101.siliconplayer.parseHttpSourceSpecFromInput
import com.flopster101.siliconplayer.prepareRemoteExportFile
import com.flopster101.siliconplayer.resolveHttpAuthenticationFailureReason
import com.flopster101.siliconplayer.rememberDialogLazyListScrollbarAlpha
import com.flopster101.siliconplayer.stripUrlFragment
import com.flopster101.siliconplayer.adaptiveDialogModifier
import com.flopster101.siliconplayer.adaptiveDialogProperties
import com.flopster101.siliconplayer.RemotePlayableSourceIdsHolder
import com.flopster101.siliconplayer.R
import com.flopster101.siliconplayer.data.ensureArchiveMounted
import com.flopster101.siliconplayer.data.buildArchiveDirectoryPath
import com.flopster101.siliconplayer.session.exportFilesToTree
import com.flopster101.siliconplayer.session.ExportConflictDecision
import com.flopster101.siliconplayer.session.ExportNameConflict
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private val HTTP_ICON_BOX_SIZE = 38.dp
private val HTTP_ICON_GLYPH_SIZE = 26.dp

private data class HttpLoadingCancelState(
    val previousSpec: HttpSourceSpec?,
    val previousEntries: List<HttpBrowserEntry>,
    val previousErrorMessage: String?
)

private enum class HttpBrowserPane {
    Loading,
    Error,
    Empty,
    Entries
}

private data class HttpBrowserContentState(
    val pane: HttpBrowserPane,
    val path: String
)

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun HttpFileBrowserScreen(
    sourceSpec: HttpSourceSpec,
    browserRootPath: String?,
    bottomContentPadding: Dp,
    backHandlingEnabled: Boolean,
    onExitBrowser: () -> Unit,
    onOpenRemoteSource: (String) -> Unit,
    onOpenRemoteSourceAsCached: (String) -> Unit,
    onRememberHttpCredentials: (Long?, String, String?, String?) -> Unit,
    sourceNodeId: Long?,
    onBrowserLocationChanged: (BrowserLaunchState) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val httpListingAdapter = remember { HttpBrowserListingAdapter() }
    val allowCredentialRemember = sourceNodeId != null
    val screenSessionKey = remember(
        sourceNodeId,
        sourceSpec.scheme,
        sourceSpec.host,
        sourceSpec.port,
        sourceSpec.username,
        sourceSpec.password,
        browserRootPath
    ) {
        "node=${sourceNodeId ?: "adhoc"}|scheme=${sourceSpec.scheme}|host=${sourceSpec.host}|port=${sourceSpec.port ?: -1}|user=${sourceSpec.username.orEmpty()}|root=${browserRootPath.orEmpty()}"
    }
    var currentSpec by remember(screenSessionKey) { mutableStateOf(sourceSpec) }
    var entries by remember(screenSessionKey) { mutableStateOf<List<HttpBrowserEntry>>(emptyList()) }
    var isLoading by remember(screenSessionKey) { mutableStateOf(false) }
    var errorMessage by remember(screenSessionKey) { mutableStateOf<String?>(null) }
    var listJob by remember(screenSessionKey) { mutableStateOf<Job?>(null) }
    val loadingLogLines = remember(screenSessionKey) { mutableStateListOf<String>() }
    var authDialogVisible by remember(screenSessionKey) { mutableStateOf(false) }
    var authDialogErrorMessage by remember(screenSessionKey) { mutableStateOf<String?>(null) }
    var authDialogUsername by remember(screenSessionKey) { mutableStateOf(sourceSpec.username.orEmpty()) }
    var authDialogPassword by remember(screenSessionKey) { mutableStateOf("") }
    var authDialogPasswordVisible by remember(screenSessionKey) { mutableStateOf(false) }
    var authRememberPassword by remember(screenSessionKey, sourceNodeId) { mutableStateOf(allowCredentialRemember) }
    var sessionUsername by remember(screenSessionKey) { mutableStateOf(sourceSpec.username) }
    var sessionPassword by remember(screenSessionKey) { mutableStateOf(sourceSpec.password) }
    var loadRequestSequence by remember(screenSessionKey) { mutableStateOf(0) }
    var isPullRefreshing by remember(screenSessionKey) { mutableStateOf(false) }
    var loadingPartialEntries by remember(screenSessionKey) { mutableStateOf<List<HttpBrowserEntry>>(emptyList()) }
    var loadingLoadedCount by remember(screenSessionKey) { mutableStateOf(0) }
    var loadCancelState by remember(screenSessionKey) { mutableStateOf<HttpLoadingCancelState?>(null) }
    var browserNavDirection by remember(screenSessionKey) { mutableStateOf(BrowserPageNavDirection.Neutral) }
    var hasLoadedInitialDirectory by remember(screenSessionKey) { mutableStateOf(false) }
    val directoryEntriesCache = remember(screenSessionKey) { mutableStateMapOf<String, List<HttpBrowserEntry>>() }
    val effectiveRootPath = remember(screenSessionKey, browserRootPath) {
        browserRootPath
            ?.trim()
            .takeUnless { it.isNullOrBlank() }
            ?.let(::normalizeHttpDirectoryPath)
            ?: "/"
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
    var pendingExportTargets by remember(screenSessionKey) { mutableStateOf<List<HttpSelectionFileTarget>>(emptyList()) }
    var exportConflictDialogState by remember(screenSessionKey) { mutableStateOf<BrowserExportConflictDialogState?>(null) }
    var exportDownloadProgressState by remember(screenSessionKey) { mutableStateOf<BrowserRemoteExportProgressState?>(null) }
    var exportDownloadJob by remember(screenSessionKey) { mutableStateOf<Job?>(null) }
    var archiveOpenJob by remember(screenSessionKey) { mutableStateOf<Job?>(null) }

    fun updatePlayableRemoteSources(entriesForNavigation: List<HttpBrowserEntry>) {
        RemotePlayableSourceIdsHolder.current = entriesForNavigation
            .asSequence()
            .filter { entry ->
                !entry.isDirectory &&
                    browserArchiveCapabilityForName(entry.name) == BrowserArchiveCapability.None &&
                    fileMatchesSupportedExtensions(File(entry.name), supportedExtensions)
            }
            .map { entry -> entry.requestUrl }
            .toList()
    }

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

    fun browserSpec(): HttpSourceSpec {
        return currentSpec.copy(
            username = sessionUsername,
            password = sessionPassword
        )
    }

    fun currentDirectorySourceId(): String {
        return buildHttpSourceId(browserSpec())
    }

    fun directoryCacheKeyFor(spec: HttpSourceSpec): String {
        return buildHttpRequestUri(
            spec.copy(
                path = normalizeHttpDirectoryPath(spec.path)
            )
        )
    }

    fun abortActiveLoad() {
        loadRequestSequence += 1
        listJob?.cancel()
        listJob = null
        isLoading = false
    }

    fun showLoadedEntriesNow() {
        val partialSnapshot = loadingPartialEntries
        browserNavDirection = BrowserPageNavDirection.Neutral
        abortActiveLoad()
        entries = partialSnapshot
        updatePlayableRemoteSources(partialSnapshot)
        errorMessage = null
        loadingPartialEntries = emptyList()
        loadingLoadedCount = 0
        loadCancelState = null
        onBrowserLocationChanged(
            BrowserLaunchState(
                directoryPath = currentDirectorySourceId(),
                httpSourceNodeId = sourceNodeId,
                httpRootPath = browserRootPath
            )
        )
    }

    fun loadCurrentDirectory(cancelState: HttpLoadingCancelState?) {
        listJob?.cancel()
        loadRequestSequence += 1
        val requestSequence = loadRequestSequence
        isLoading = true
        errorMessage = null
        entries = emptyList()
        RemotePlayableSourceIdsHolder.current = emptyList()
        loadingPartialEntries = emptyList()
        loadingLoadedCount = 0
        loadCancelState = cancelState
        loadingLogLines.clear()
        val requestSpec = browserSpec()
        appendLoadingLog("Connecting to ${buildHttpDisplayUri(requestSpec)}")
        appendLoadingLog("Listing directory page")
        listJob = coroutineScope.launch {
            var lastLoggedCount = 0
            val result = httpListingAdapter.list(
                location = HttpBrowserListLocation(requestSpec),
                onProgress = { loadedCount, partialEntries ->
                    if (requestSequence != loadRequestSequence) return@list
                    loadingLoadedCount = loadedCount
                    loadingPartialEntries = partialEntries
                    if (loadedCount > 0 && (loadedCount - lastLoggedCount) >= 50) {
                        appendLoadingLog("Loaded $loadedCount entries...")
                        lastLoggedCount = loadedCount
                    }
                }
            )
            if (requestSequence != loadRequestSequence) {
                return@launch
            }
            result.onSuccess { listing ->
                currentSpec = listing.metadata.copy(
                    username = sessionUsername,
                    password = sessionPassword
                )
                entries = listing.entries
                updatePlayableRemoteSources(listing.entries)
                directoryEntriesCache[directoryCacheKeyFor(currentSpec)] = listing.entries
                errorMessage = null
                onBrowserLocationChanged(
                    BrowserLaunchState(
                        directoryPath = currentDirectorySourceId(),
                        httpSourceNodeId = sourceNodeId,
                        httpRootPath = browserRootPath
                    )
                )
                val folders = listing.entries.count { it.isDirectory }
                val files = listing.entries.size - folders
                appendLoadingLog("Found ${listing.entries.size} entries")
                appendLoadingLog("$folders folders, $files files")
                appendLoadingLog("Load finished")
            }.onFailure { throwable ->
                if (throwable is CancellationException) {
                    return@onFailure
                }
                entries = emptyList()
                RemotePlayableSourceIdsHolder.current = emptyList()
                val authFailureReason = resolveHttpAuthenticationFailureReason(throwable)?.let { reason ->
                    if (
                        reason == HttpAuthenticationFailureReason.Unknown &&
                        requestSpec.username.isNullOrBlank() &&
                        requestSpec.password.isNullOrBlank()
                    ) {
                        HttpAuthenticationFailureReason.AuthenticationRequired
                    } else {
                        reason
                    }
                }
                if (authFailureReason != null) {
                    val authMessage = httpAuthenticationFailureMessage(authFailureReason)
                    authDialogErrorMessage = authMessage
                    if (!authDialogVisible) {
                        authDialogUsername = requestSpec.username.orEmpty()
                        authDialogPassword = ""
                        authDialogPasswordVisible = false
                        authRememberPassword = allowCredentialRemember
                    }
                    authDialogVisible = true
                    errorMessage = authMessage
                } else {
                    errorMessage = throwable.message ?: "Unable to list HTTP directory"
                }
                appendLoadingLog(
                    "Load failed: ${throwable.message ?: throwable.javaClass.simpleName ?: "Unknown error"}"
                )
            }
            if (requestSequence == loadRequestSequence) {
                isLoading = false
                listJob = null
                loadCancelState = null
                loadingPartialEntries = emptyList()
                loadingLoadedCount = 0
            }
        }
    }

    fun openDirectory(
        targetSpec: HttpSourceSpec,
        cancelState: HttpLoadingCancelState?,
        forceReload: Boolean = false,
        navigationDirection: BrowserPageNavDirection = BrowserPageNavDirection.Forward
    ) {
        browserNavDirection = navigationDirection
        val normalizedTargetSpec = targetSpec.copy(
            path = normalizeHttpDirectoryPath(targetSpec.path),
            username = sessionUsername,
            password = sessionPassword
        )
        currentSpec = normalizedTargetSpec
        val cacheKey = directoryCacheKeyFor(normalizedTargetSpec)
        val cachedEntries = directoryEntriesCache[cacheKey]
        if (!forceReload && cachedEntries != null) {
            abortActiveLoad()
            entries = cachedEntries
            updatePlayableRemoteSources(cachedEntries)
            errorMessage = null
            loadingPartialEntries = emptyList()
            loadingLoadedCount = 0
            loadCancelState = null
            onBrowserLocationChanged(
                BrowserLaunchState(
                    directoryPath = currentDirectorySourceId(),
                    httpSourceNodeId = sourceNodeId,
                    httpRootPath = browserRootPath
                )
            )
            return
        }
        loadCurrentDirectory(cancelState)
    }

    fun navigateUpWithinBrowser(): Boolean {
        val normalizedPath = normalizeHttpDirectoryPath(currentSpec.path)
        if (!canNavigateUpWithinHttpBrowser(normalizedPath, effectiveRootPath)) {
            return false
        }
        val parent = normalizedPath
            .trimEnd('/')
            .substringBeforeLast('/', missingDelimiterValue = "")
            .trim()
        val parentPath = if (parent.isBlank()) "/" else normalizeHttpDirectoryPath(parent)
        val clampedParentPath = if (parentPath.length < effectiveRootPath.length) {
            effectiveRootPath
        } else {
            parentPath
        }
        val cancelSnapshot = HttpLoadingCancelState(
            previousSpec = currentSpec,
            previousEntries = entries,
            previousErrorMessage = errorMessage
        )
        openDirectory(
            targetSpec = currentSpec.copy(path = clampedParentPath, query = null),
            cancelState = cancelSnapshot,
            navigationDirection = BrowserPageNavDirection.Backward
        )
        return true
    }

    fun cancelCurrentLoadAndGoBack() {
        abortActiveLoad()
        loadingPartialEntries = emptyList()
        loadingLoadedCount = 0
        loadCancelState = null
        if (!navigateUpWithinBrowser()) {
            onExitBrowser()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            listJob?.cancel()
            exportDownloadJob?.cancel()
            archiveOpenJob?.cancel()
        }
    }

    LaunchedEffect(sourceSpec, screenSessionKey) {
        val desiredSpec = sourceSpec.copy(path = normalizeHttpDirectoryPath(sourceSpec.path))
        val currentNormalizedPath = normalizeHttpDirectoryPath(currentSpec.path)
        val sameNavigation =
            currentSpec.scheme.equals(desiredSpec.scheme, ignoreCase = true) &&
                currentSpec.host.equals(desiredSpec.host, ignoreCase = true) &&
                currentSpec.port == desiredSpec.port &&
                currentNormalizedPath == desiredSpec.path &&
                currentSpec.query == desiredSpec.query
        val sameCredentials = sessionUsername == sourceSpec.username && sessionPassword == sourceSpec.password
        if (sameNavigation && sameCredentials && hasLoadedInitialDirectory) {
            return@LaunchedEffect
        }
        browserSearchController.hide()
        currentSpec = desiredSpec
        entries = emptyList()
        loadingPartialEntries = emptyList()
        loadingLoadedCount = 0
        loadCancelState = null
        authDialogVisible = false
        authDialogErrorMessage = null
        authDialogUsername = sourceSpec.username.orEmpty()
        authDialogPassword = ""
        authDialogPasswordVisible = false
        authRememberPassword = allowCredentialRemember
        sessionUsername = sourceSpec.username
        sessionPassword = sourceSpec.password
        hasLoadedInitialDirectory = true
        openDirectory(
            targetSpec = desiredSpec,
            cancelState = HttpLoadingCancelState(
                previousSpec = null,
                previousEntries = emptyList(),
                previousErrorMessage = null
            ),
            navigationDirection = BrowserPageNavDirection.Neutral
        )
    }

    BackHandler(enabled = backHandlingEnabled) {
        if (browserSelectionController.isSelectionMode) {
            browserSelectionController.exitSelectionMode()
            return@BackHandler
        }
        if (isLoading) {
            cancelCurrentLoadAndGoBack()
            return@BackHandler
        }
        if (!navigateUpWithinBrowser()) {
            onExitBrowser()
        }
    }

    val canNavigateUp = canNavigateUpWithinHttpBrowser(
        currentPath = currentSpec.path,
        rootPath = effectiveRootPath
    )
    val browsableEntries = remember(entries, supportedExtensions) {
        entries.filter { entry ->
            shouldShowRemoteBrowserEntry(
                name = entry.name,
                isDirectory = entry.isDirectory,
                supportedExtensions = supportedExtensions
            )
        }
    }
    val browserContentState = remember(currentSpec.path, isLoading, errorMessage, browsableEntries.isEmpty()) {
        HttpBrowserContentState(
            pane = when {
                isLoading -> HttpBrowserPane.Loading
                !errorMessage.isNullOrBlank() -> HttpBrowserPane.Error
                browsableEntries.isEmpty() -> HttpBrowserPane.Empty
                else -> HttpBrowserPane.Entries
            },
            path = normalizeHttpDirectoryPath(currentSpec.path)
        )
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
    val entrySelectionKeyFor: (HttpBrowserEntry) -> String = remember {
        { entry -> entry.sourceId }
    }
    val rawSubtitle = buildHttpDisplayUri(browserSpec())
    val subtitle = decodePercentEncodedForDisplay(rawSubtitle) ?: rawSubtitle
    val protocolLabel = browserSpec().scheme.uppercase(Locale.ROOT)
    var selectorExpanded by remember(screenSessionKey) { mutableStateOf(false) }
    val entriesListState = rememberLazyListState()
    val nonEntriesListState = rememberLazyListState()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isPullRefreshing,
        onRefresh = {
            if (isPullRefreshing || isLoading) return@rememberPullRefreshState
            coroutineScope.launch {
                isPullRefreshing = true
                openDirectory(
                    targetSpec = currentSpec,
                    cancelState = HttpLoadingCancelState(
                        previousSpec = currentSpec,
                        previousEntries = entries,
                        previousErrorMessage = errorMessage
                    ),
                    forceReload = true,
                    navigationDirection = BrowserPageNavDirection.Neutral
                )
                val deadline = System.currentTimeMillis() + 20_000L
                while (isLoading && System.currentTimeMillis() < deadline) {
                    delay(24)
                }
                isPullRefreshing = false
            }
        }
    )
    val directoryScrollbarAlpha = rememberDialogLazyListScrollbarAlpha(
        enabled = browserContentState.pane == HttpBrowserPane.Entries,
        listState = entriesListState,
        flashKey = "${browserContentState.path}|${filteredEntries.size}|${browserSearchController.debouncedQuery}",
        label = "httpBrowserDirectoryScrollbarAlpha"
    )

    fun selectedDownloadFileTargets(): List<HttpSelectionFileTarget> {
        val selectedEntries = browsableEntries.filter { entry ->
            browserSelectionController.selectedKeys.contains(entrySelectionKeyFor(entry))
        }
        return selectedEntries
            .asSequence()
            .filter { !it.isDirectory }
            .mapNotNull { entry ->
                val parsedSpec = parseHttpSourceSpecFromInput(entry.requestUrl) ?: return@mapNotNull null
                val authSpec = parsedSpec.copy(
                    username = sessionUsername,
                    password = sessionPassword
                )
                HttpSelectionFileTarget(
                    sourceId = buildHttpSourceId(authSpec),
                    requestUrl = stripUrlFragment(buildHttpRequestUri(authSpec)),
                    openInput = appendHttpDisplayNameFragment(
                        sourceUrl = entry.requestUrl,
                        displayName = entry.name
                    ),
                    displayName = decodePercentEncodedForDisplay(entry.name) ?: entry.name
                )
            }
            .toList()
    }

    fun selectedPlayableFileTargets(): List<HttpSelectionFileTarget> {
        return selectedDownloadFileTargets().filter { target ->
            browserArchiveCapabilityForName(target.displayName) == BrowserArchiveCapability.None
        }
    }

    fun openArchiveEntry(entry: HttpBrowserEntry) {
        val parsedSpec = parseHttpSourceSpecFromInput(entry.requestUrl) ?: return
        val authSpec = parsedSpec.copy(
            username = sessionUsername,
            password = sessionPassword
        )
        val sourceId = buildHttpSourceId(authSpec)
        val requestUrl = stripUrlFragment(buildHttpRequestUri(authSpec))
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
                request = HttpRemoteExportRequest(
                    sourceId = sourceId,
                    requestUrl = requestUrl,
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
                    httpSourceNodeId = sourceNodeId,
                    httpRootPath = browserRootPath
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
                sizeBytes = null
            )
        }
        val spec = browserSpec()
        val hostLabel = buildString {
            append(spec.host)
            spec.port?.let { port ->
                if (port > 0) append(":$port")
            }
        }
        browserInfoFields = buildBrowserInfoFields(
            entries = infoEntries,
            path = buildHttpDisplayUri(spec),
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
                    request = HttpRemoteExportRequest(
                        sourceId = target.sourceId,
                        requestUrl = target.requestUrl,
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

    LaunchedEffect(currentSpec.path) {
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
                                                Text(
                                                    text = if (browserSpec().scheme.equals("https", ignoreCase = true)) {
                                                        "HTTPS server"
                                                    } else {
                                                        "HTTP server"
                                                    }
                                                )
                                                Text(
                                                    text = browserSpec().host,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = NetworkIcons.WorldCode,
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
                                icon = NetworkIcons.WorldCode,
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
                                            onOpenRemoteSource(target.openInput)
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
                                            onOpenRemoteSourceAsCached(target.openInput)
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
                .pullRefresh(pullRefreshState)
        ) {
            AnimatedContent(
                targetState = browserContentState,
                transitionSpec = {
                    val loadingTransition =
                        initialState.pane == HttpBrowserPane.Loading ||
                            targetState.pane == HttpBrowserPane.Loading
                    browserContentTransform(
                        navDirection = browserNavDirection,
                        loadingTransition = loadingTransition
                    )
                },
                label = "httpBrowserLoadingTransition",
                modifier = Modifier.fillMaxSize()
            ) { state ->
                val stateBrowsableEntries = if (state.path == normalizeHttpDirectoryPath(currentSpec.path)) {
                    browsableEntries
                } else {
                    val cachedSpec = currentSpec.copy(
                        path = state.path,
                        query = null,
                        username = sessionUsername,
                        password = sessionPassword
                    )
                    directoryEntriesCache[directoryCacheKeyFor(cachedSpec)]
                        ?.filter { entry ->
                            shouldShowRemoteBrowserEntry(
                                name = entry.name,
                                isDirectory = entry.isDirectory,
                                supportedExtensions = supportedExtensions
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
                val stateCanNavigateUp = canNavigateUpWithinHttpBrowser(
                    currentPath = state.path,
                    rootPath = effectiveRootPath
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = if (state.pane == HttpBrowserPane.Entries) {
                        entriesListState
                    } else {
                        nonEntriesListState
                    },
                    contentPadding = PaddingValues(bottom = bottomContentPadding)
                ) {
                    if (stateCanNavigateUp && state.pane != HttpBrowserPane.Loading) {
                        item("parent") {
                            HttpParentDirectoryRow(
                                onClick = {
                                    if (!isLoading) {
                                        navigateUpWithinBrowser()
                                    }
                                }
                            )
                        }
                    }

                    if (state.pane == HttpBrowserPane.Loading) {
                        item("loading") {
                            HttpLoadingCard(
                                title = "Loading $protocolLabel directory...",
                                subtitle = if (loadingLoadedCount > 0) {
                                    "$loadingLoadedCount entries loaded so far"
                                } else {
                                    "Fetching folder entries"
                                },
                                logLines = loadingLogLines,
                                protocolLabel = protocolLabel,
                                onShowNow = { showLoadedEntriesNow() },
                                onCancel = { cancelCurrentLoadAndGoBack() },
                                showNowEnabled = loadingLoadedCount > 0
                            )
                        }
                    } else if (state.pane == HttpBrowserPane.Error) {
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
                                            text = "Unable to open HTTP directory",
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
                                    TextButton(
                                        onClick = {
                                            openDirectory(
                                                targetSpec = currentSpec,
                                                cancelState = HttpLoadingCancelState(
                                                    previousSpec = currentSpec,
                                                    previousEntries = entries,
                                                    previousErrorMessage = errorMessage
                                                ),
                                                forceReload = true,
                                                navigationDirection = BrowserPageNavDirection.Neutral
                                            )
                                        }
                                    ) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                    } else if (state.pane == HttpBrowserPane.Empty) {
                        item("empty") {
                            HttpInfoCard(
                                title = "This directory is empty",
                                subtitle = "No files or folders found"
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
                                key = { _, entry -> entry.sourceId }
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
                                HttpEntryRow(
                                    entry = entry,
                                    decoderExtensionArtworkHints = decoderExtensionArtworkHints,
                                    isSelected = browserSelectionController.selectedKeys.contains(entrySelectionKey),
                                    hasSelectedAbove = hasSelectedAbove,
                                    hasSelectedBelow = hasSelectedBelow,
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
                                            return@HttpEntryRow
                                        }
                                        if (entry.isDirectory) {
                                            val nextSpec = parseHttpSourceSpecFromInput(entry.requestUrl)
                                                ?: return@HttpEntryRow
                                            val cancelSnapshot = HttpLoadingCancelState(
                                                previousSpec = currentSpec,
                                                previousEntries = entries,
                                                previousErrorMessage = errorMessage
                                            )
                                            openDirectory(
                                                targetSpec = nextSpec.copy(
                                                    username = sessionUsername,
                                                    password = sessionPassword
                                                ),
                                                cancelState = cancelSnapshot,
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
                                                    onOpenRemoteSource(
                                                        appendHttpDisplayNameFragment(
                                                            sourceUrl = entry.requestUrl,
                                                            displayName = entry.name
                                                        )
                                                    )
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
            PullRefreshIndicator(
                refreshing = isPullRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.primary
            )
            if (browserContentState.pane == HttpBrowserPane.Entries) {
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
        androidx.compose.material3.AlertDialog(
            modifier = adaptiveDialogModifier(),
            properties = adaptiveDialogProperties(),
            onDismissRequest = {
                authDialogVisible = false
                authDialogPasswordVisible = false
            },
            title = { Text("HTTP authentication required") },
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
                            onRememberHttpCredentials(
                                sourceNodeId,
                                currentDirectorySourceId(),
                                normalizedUsername,
                                normalizedPassword
                            )
                        }
                        authDialogVisible = false
                        authDialogPasswordVisible = false
                        loadCurrentDirectory(
                            cancelState = HttpLoadingCancelState(
                                previousSpec = currentSpec,
                                previousEntries = entries,
                                previousErrorMessage = errorMessage
                            )
                        )
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

private data class HttpSelectionFileTarget(
    val sourceId: String,
    val requestUrl: String,
    val openInput: String,
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
private fun HttpParentDirectoryRow(
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
            modifier = Modifier.size(HTTP_ICON_BOX_SIZE),
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
                    modifier = Modifier.size(HTTP_ICON_GLYPH_SIZE)
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
private fun HttpEntryRow(
    entry: HttpBrowserEntry,
    decoderExtensionArtworkHints: Map<String, DecoderArtworkHint>,
    isSelected: Boolean,
    hasSelectedAbove: Boolean = false,
    hasSelectedBelow: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val visualKind = browserRemoteEntryVisualKind(
        name = entry.name,
        isDirectory = entry.isDirectory,
        decoderExtensionArtworkHints = decoderExtensionArtworkHints
    )
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
        val chipContainerColor = if (entry.isDirectory) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
        val chipContentColor = if (entry.isDirectory) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
        Box(
            modifier = Modifier
                .size(HTTP_ICON_BOX_SIZE)
                .background(
                    color = chipContainerColor,
                    shape = RoundedCornerShape(11.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            BrowserRemoteEntryIcon(
                visualKind = visualKind,
                tint = chipContentColor,
                modifier = Modifier.size(HTTP_ICON_GLYPH_SIZE)
            )
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
                text = if (entry.isDirectory) {
                    "Folder"
                } else if (browserArchiveCapabilityForName(entry.name) != BrowserArchiveCapability.None) {
                    "Archive"
                } else {
                    inferHttpFormatLabel(entry.name)
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
private fun HttpInfoCard(
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
private fun HttpLoadingCard(
    title: String,
    subtitle: String,
    logLines: List<String>,
    protocolLabel: String,
    showContainerCard: Boolean = true,
    onShowNow: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    showNowEnabled: Boolean = false
) {
    if (!showContainerCard) {
        return
    }
    BrowserLoadingCard(
        icon = NetworkIcons.WorldCode,
        title = title,
        subtitle = subtitle,
        logLines = logLines,
        waitingLine = "[00] Waiting for $protocolLabel response...",
        primaryActionLabel = if (onShowNow != null) "Show now" else null,
        primaryActionEnabled = showNowEnabled,
        onPrimaryAction = onShowNow,
        secondaryActionLabel = if (onCancel != null) "Cancel" else null,
        onSecondaryAction = onCancel
    )
}

private fun inferHttpFormatLabel(name: String): String {
    val ext = inferredPrimaryExtensionForName(name)
    return ext?.uppercase(Locale.ROOT) ?: "Unknown"
}

private fun canNavigateUpWithinHttpBrowser(
    currentPath: String?,
    rootPath: String?
): Boolean {
    val normalizedCurrent = normalizeHttpDirectoryPath(currentPath)
    val normalizedRoot = rootPath
        ?.trim()
        .takeUnless { it.isNullOrBlank() }
        ?.let(::normalizeHttpDirectoryPath)
        ?: "/"
    if (normalizedCurrent == normalizedRoot) return false
    if (!normalizedCurrent.startsWith(normalizedRoot)) return false
    return true
}

private fun appendHttpDisplayNameFragment(
    sourceUrl: String,
    displayName: String
): String {
    val trimmedLabel = displayName.trim()
    if (trimmedLabel.isBlank()) return sourceUrl
    val sanitizedLabel = trimmedLabel
        .replace('/', '_')
        .replace('\\', '_')
    return try {
        Uri.parse(sourceUrl)
            .buildUpon()
            .fragment(Uri.encode(sanitizedLabel))
            .build()
            .toString()
    } catch (_: Throwable) {
        sourceUrl
    }
}
