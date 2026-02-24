package com.flopster101.siliconplayer.ui.screens

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.extensionCandidatesForName
import com.flopster101.siliconplayer.inferredPrimaryExtensionForName
import com.flopster101.siliconplayer.DecoderArtworkHint
import com.flopster101.siliconplayer.R
import com.flopster101.siliconplayer.resolveDecoderArtworkHintForFileName
import com.flopster101.siliconplayer.data.buildArchiveSourceId
import com.flopster101.siliconplayer.data.FileItem
import com.flopster101.siliconplayer.data.FileRepository
import com.flopster101.siliconplayer.data.ensureArchiveMounted
import com.flopster101.siliconplayer.data.resolveArchiveLogicalDirectory
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.withFrameNanos
import kotlin.math.min

private const val BROWSER_PAGE_DURATION_MS = 280
private const val MIN_LOADING_SPINNER_MS = 220L
private const val FILE_ENTRY_ANIM_DURATION_MS = 280
private const val DIRECTORY_DIRECT_PUBLISH_MAX_ITEMS = 3000
private val FILE_ICON_BOX_SIZE = 38.dp
private val FILE_ICON_GLYPH_SIZE = 26.dp
private val FALLBACK_VIDEO_EXTENSIONS = setOf(
    "3g2", "3gp", "asf", "avi", "divx", "f4v", "flv", "m2ts", "m2v", "m4v",
    "mkv", "mov", "mp4", "mpeg", "mpg", "mts", "ogm", "ogv", "rm", "rmvb",
    "ts", "vob", "webm", "wmv"
)

private enum class BrowserNavDirection {
    Forward,
    Backward
}

private data class BrowserContentState(
    val selectedLocationId: String?,
    val currentDirectoryPath: String?
)

private data class ArchiveMountInfo(
    val archivePath: String,
    val parentPath: String
)

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterialApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun FileBrowserScreen(
    repository: FileRepository,
    decoderExtensionArtworkHints: Map<String, DecoderArtworkHint> = emptyMap(),
    initialLocationId: String? = null,
    initialDirectoryPath: String? = null,
    restoreFocusedItemRequestToken: Int = 0,
    onFileSelected: (File, String?) -> Unit,
    onVisiblePlayableFilesChanged: (List<File>) -> Unit = {},
    onBrowserLocationChanged: (String?, String?) -> Unit = { _, _ -> },
    bottomContentPadding: Dp = 0.dp,
    showParentDirectoryEntry: Boolean = true,
    showFileIconChipBackground: Boolean = true,
    backHandlingEnabled: Boolean = true,
    onExitBrowser: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    showPrimaryTopBar: Boolean = true,
    playingFile: File? = null
) {
    val context = LocalContext.current
    var storageLocationsRefreshToken by remember { mutableIntStateOf(0) }
    val storageLocations = remember(context, storageLocationsRefreshToken) { detectStorageLocations(context) }
    var selectedLocationId by remember { mutableStateOf<String?>(null) }
    var currentDirectory by remember { mutableStateOf<File?>(null) }
    val fileList = remember { mutableStateListOf<FileItem>() }
    var selectorExpanded by remember { mutableStateOf(false) }
    var browserNavDirection by remember { mutableStateOf(BrowserNavDirection.Forward) }
    var isLoadingDirectory by remember { mutableStateOf(false) }
    var hasRestoredInitialNavigation by remember { mutableStateOf(false) }
    val directoryListState = rememberLazyListState()
    var launchAutoScrollTargetKey by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var directoryLoadJob by remember { mutableStateOf<Job?>(null) }
    var isPullRefreshing by remember { mutableStateOf(false) }
    var directoryAnimationEpoch by remember { mutableIntStateOf(0) }
    val archiveMountRoots = remember { mutableStateMapOf<String, ArchiveMountInfo>() }
    val selectorButtonFocusRequester = remember { FocusRequester() }
    var browserFocusedEntryKey by remember { mutableStateOf<String?>(null) }
    val browserEntryFocusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }

    val selectedLocation = storageLocations.firstOrNull { it.id == selectedLocationId }
    val selectorIcon = selectedLocation?.let { iconForStorageKind(it.kind, context) } ?: Icons.Default.Home
    val browserContentState = BrowserContentState(
        selectedLocationId = selectedLocationId,
        currentDirectoryPath = currentDirectory?.absolutePath
    )

    fun cancelDirectoryLoad() {
        directoryLoadJob?.cancel()
        directoryLoadJob = null
        isLoadingDirectory = false
    }

    fun loadDirectoryAsync(directory: File) {
        directoryLoadJob?.cancel()
        isLoadingDirectory = true
        directoryAnimationEpoch += 1
        fileList.clear()

        val targetPath = directory.absolutePath
        val loadingStartedAt = System.currentTimeMillis()
        directoryLoadJob = coroutineScope.launch {
            try {
                // Ensure at least one frame is rendered with the spinner before parsing starts.
                withFrameNanos { }
                delay(16)
                val loadedFiles = withContext(Dispatchers.IO) {
                    val baseFiles = repository.getFiles(directory)
                    val mounted = archiveMountRoots.entries
                        .asSequence()
                        .filter { (mountRoot, _) ->
                            targetPath == mountRoot || targetPath.startsWith("$mountRoot/")
                        }
                        .maxByOrNull { (mountRoot, _) -> mountRoot.length }
                    if (mounted == null) {
                        baseFiles
                    } else {
                        val mountRoot = mounted.key
                        val mountInfo = mounted.value
                        val relativeDirectory = if (targetPath == mountRoot) {
                            ""
                        } else {
                            targetPath.removePrefix("$mountRoot/").replace('\\', '/').trim('/')
                        }
                        val zipSizes = readZipEntrySizesForDirectory(
                            archivePath = mountInfo.archivePath,
                            relativeDirectory = relativeDirectory
                        )
                        if (zipSizes.isEmpty()) {
                            baseFiles
                        } else {
                            baseFiles.map { item ->
                                if (!item.isDirectory) {
                                    zipSizes[item.name]?.let { resolvedSize ->
                                        item.copy(size = resolvedSize)
                                    } ?: item
                                } else {
                                    item
                                }
                            }
                        }
                    }
                }
                onVisiblePlayableFilesChanged(
                    loadedFiles
                        .asSequence()
                        .filter { !it.isDirectory }
                        .map { it.file }
                        .toList()
                )
                val stillOnSameDirectory = currentDirectory?.absolutePath == targetPath &&
                    selectedLocationId != null
                if (stillOnSameDirectory) {
                    // Publish all items at once for normal folders. For very large folders,
                    // chunk without artificial delays to avoid blocking the main thread.
                    fileList.clear()
                    if (shouldPublishDirectoryAllAtOnce(loadedFiles.size)) {
                        fileList.addAll(loadedFiles)
                    } else {
                        val publishBatchSize = directoryPublishBatchSize(loadedFiles.size)
                        var index = 0
                        while (index < loadedFiles.size) {
                            ensureActive()
                            if (currentDirectory?.absolutePath != targetPath || selectedLocationId == null) break

                            val end = min(index + publishBatchSize, loadedFiles.size)
                            val chunk = loadedFiles.subList(index, end)
                            fileList.addAll(chunk)
                            index = end
                            if (index < loadedFiles.size) {
                                // Yield once per chunk to avoid UI stalls for huge folders.
                                withFrameNanos { }
                            }
                        }
                    }
                }
            } catch (_: CancellationException) {
                // Directory load was superseded by another navigation action.
            } finally {
                if (currentDirectory?.absolutePath == targetPath) {
                    val elapsed = System.currentTimeMillis() - loadingStartedAt
                    if (elapsed < MIN_LOADING_SPINNER_MS) {
                        delay(MIN_LOADING_SPINNER_MS - elapsed)
                    }
                    isLoadingDirectory = false
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cancelDirectoryLoad()
        }
    }

    fun relativeDepth(directory: File?, root: File?): Int {
        if (directory == null || root == null) return 0
        val rootPath = root.absolutePath.trimEnd('/')
        val dirPath = directory.absolutePath
        if (dirPath == rootPath) return 0
        return dirPath.removePrefix("$rootPath/").split("/").count { it.isNotBlank() }
    }

    fun openBrowserHome() {
        cancelDirectoryLoad()
        launchAutoScrollTargetKey = null
        browserNavDirection = BrowserNavDirection.Backward
        selectedLocationId = null
        currentDirectory = null
        fileList.clear()
        onVisiblePlayableFilesChanged(emptyList())
        onBrowserLocationChanged(null, null)
    }

    fun openLocation(location: StorageLocation) {
        launchAutoScrollTargetKey = null
        browserNavDirection = BrowserNavDirection.Forward
        selectedLocationId = location.id
        currentDirectory = location.directory
        loadDirectoryAsync(location.directory)
        onBrowserLocationChanged(location.id, location.directory.absolutePath)
    }

    fun findArchiveMount(path: String): Pair<String, ArchiveMountInfo>? {
        return archiveMountRoots
            .asSequence()
            .filter { (mountRoot, _) ->
                path == mountRoot || path.startsWith("$mountRoot/")
            }
            .maxByOrNull { (mountRoot, _) -> mountRoot.length }
            ?.toPair()
    }

    fun resolveLogicalArchivePath(directory: File?): String? {
        val dir = directory ?: return null
        val mounted = findArchiveMount(dir.absolutePath) ?: return null
        val mountRoot = mounted.first
        val archivePath = mounted.second.archivePath
        if (dir.absolutePath == mountRoot) {
            return archivePath
        }
        val relative = dir.absolutePath.removePrefix("$mountRoot/").replace('\\', '/').trimStart('/')
        return if (relative.isBlank()) archivePath else "$archivePath/$relative"
    }

    fun emitBrowserLocationChange(directory: File) {
        val logicalPath = resolveLogicalArchivePath(directory)
        onBrowserLocationChanged(selectedLocationId, logicalPath ?: directory.absolutePath)
    }

    fun navigateTo(directory: File) {
        launchAutoScrollTargetKey = null
        val root = selectedLocation?.directory
        val previousDepth = relativeDepth(currentDirectory, root)
        val nextDepth = relativeDepth(directory, root)
        browserNavDirection = if (nextDepth >= previousDepth) {
            BrowserNavDirection.Forward
        } else {
            BrowserNavDirection.Backward
        }
        currentDirectory = directory
        loadDirectoryAsync(directory)
        emitBrowserLocationChange(directory)
    }

    fun openArchive(item: FileItem) {
        val archiveFile = item.file
        if (!item.isArchive || currentDirectory == null) {
            return
        }
        directoryLoadJob?.cancel()
        isLoadingDirectory = true
        directoryLoadJob = coroutineScope.launch {
            try {
                val mountDirectory = withContext(Dispatchers.IO) {
                    ensureArchiveMounted(context, archiveFile)
                }
                archiveFile.parentFile?.absolutePath?.let { parentPath ->
                    archiveMountRoots[mountDirectory.absolutePath] = ArchiveMountInfo(
                        archivePath = archiveFile.absolutePath,
                        parentPath = parentPath
                    )
                }
                navigateTo(mountDirectory)
            } catch (_: CancellationException) {
                // Archive open was superseded by another action.
            } finally {
                isLoadingDirectory = false
            }
        }
    }

    fun navigateUpWithinLocation() {
        val location = selectedLocation ?: return openBrowserHome()
        val directory = currentDirectory ?: return openBrowserHome()
        val root = location.directory

        findArchiveMount(directory.absolutePath)?.let { (mountRoot, mountInfo) ->
            if (directory.absolutePath != mountRoot) {
                val archiveParent = directory.parentFile
                if (archiveParent != null && archiveParent.exists() && archiveParent.isDirectory) {
                    browserNavDirection = BrowserNavDirection.Backward
                    navigateTo(archiveParent)
                    return
                }
            }
            val parentDirectory = File(mountInfo.parentPath)
            if (parentDirectory.exists() && parentDirectory.isDirectory) {
                browserNavDirection = BrowserNavDirection.Backward
                navigateTo(parentDirectory)
                return
            }
        }

        if (directory.absolutePath == root.absolutePath) {
            openBrowserHome()
            return
        }

        val parent = directory.parentFile
        if (parent == null || !isWithinRoot(parent, root)) {
            navigateTo(root)
        } else {
            navigateTo(parent)
        }
    }

    fun handleBack() {
        if (selectedLocation != null) {
            navigateUpWithinLocation()
        } else {
            onExitBrowser?.invoke()
        }
    }

    LaunchedEffect(storageLocations, initialLocationId, initialDirectoryPath) {
        if (hasRestoredInitialNavigation) return@LaunchedEffect
        hasRestoredInitialNavigation = true

        val initialLocation = initialLocationId?.let { id ->
            storageLocations.firstOrNull { it.id == id }
        } ?: return@LaunchedEffect

        val restoredDirectory = initialDirectoryPath
            ?.let { path ->
                File(path).takeIf { file ->
                    file.exists() && file.isDirectory && isWithinRoot(file, initialLocation.directory)
                } ?: withContext(Dispatchers.IO) {
                    resolveArchiveLogicalDirectory(context, path)
                }?.takeIf { resolved ->
                    File(resolved.parentPath).let { parent ->
                        parent.exists() && parent.isDirectory && isWithinRoot(parent, initialLocation.directory)
                    }
                }?.also { resolved ->
                    archiveMountRoots[resolved.mountDirectory.absolutePath] = ArchiveMountInfo(
                        archivePath = resolved.archivePath,
                        parentPath = resolved.parentPath
                    )
                }?.targetDirectory
            } ?: initialLocation.directory

        browserNavDirection = BrowserNavDirection.Forward
        selectedLocationId = initialLocation.id
        currentDirectory = restoredDirectory
        loadDirectoryAsync(restoredDirectory)
        val restoredLogicalPath = resolveLogicalArchivePath(restoredDirectory) ?: restoredDirectory.absolutePath
        onBrowserLocationChanged(initialLocation.id, restoredLogicalPath)

        val playingPath = playingFile?.absolutePath
        val playingParentPath = playingFile?.parentFile?.absolutePath
        launchAutoScrollTargetKey = if (
            playingPath != null &&
            playingParentPath == restoredDirectory.absolutePath
        ) {
            "${restoredDirectory.absolutePath}|$playingPath"
        } else {
            null
        }
    }

    BackHandler(
        enabled = backHandlingEnabled && (selectedLocation != null || onExitBrowser != null),
        onBack = { handleBack() }
    )

    LaunchedEffect(launchAutoScrollTargetKey, fileList.size) {
        val targetKey = launchAutoScrollTargetKey ?: return@LaunchedEffect
        val targetPath = targetKey.substringAfter('|', missingDelimiterValue = "")
        if (targetPath.isBlank()) {
            launchAutoScrollTargetKey = null
            return@LaunchedEffect
        }
        val targetIndex = fileList.indexOfFirst { !it.isDirectory && it.file.absolutePath == targetPath }
        if (targetIndex < 0) return@LaunchedEffect

        val visible = directoryListState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }
        if (!visible) {
            // Keep a little context above the playing row when jumping.
            directoryListState.animateScrollToItem((targetIndex - 2).coerceAtLeast(0))
        }
        launchAutoScrollTargetKey = null
    }

    LaunchedEffect(
        restoreFocusedItemRequestToken,
        selectedLocationId,
        currentDirectory?.absolutePath,
        fileList.size
    ) {
        if (restoreFocusedItemRequestToken <= 0 || selectedLocationId == null) return@LaunchedEffect
        val focusedKey = browserFocusedEntryKey ?: return@LaunchedEffect
        val requester = browserEntryFocusRequesters[focusedKey] ?: return@LaunchedEffect
        val targetIndex = when {
            focusedKey.startsWith("parent:") -> if (showParentDirectoryEntry) 0 else -1
            else -> {
                val fileIndex = fileList.indexOfFirst { it.file.absolutePath == focusedKey }
                if (fileIndex < 0) -1 else fileIndex + if (showParentDirectoryEntry) 1 else 0
            }
        }
        if (targetIndex >= 0) {
            val isVisible = directoryListState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }
            if (!isVisible) {
                directoryListState.animateScrollToItem((targetIndex - 2).coerceAtLeast(0))
                withFrameNanos { }
            }
        }
        requester.requestFocus()
    }

    val subtitle = if (selectedLocation == null) {
        "Storage locations"
    } else {
        resolveLogicalArchivePath(currentDirectory) ?: (currentDirectory?.absolutePath ?: selectedLocation.name)
    }

    Scaffold(
        topBar = {
            Column {
                if (showPrimaryTopBar) {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Silicon Player",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        actions = {
                            onExitBrowser?.let { exitBrowser ->
                                IconButton(onClick = exitBrowser) {
                                    Icon(
                                        imageVector = Icons.Default.Home,
                                        contentDescription = "Go to app home"
                                    )
                                }
                            }
                            onOpenSettings?.let { openSettings ->
                                IconButton(onClick = openSettings) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Open settings"
                                    )
                                }
                            }
                        }
                    )
                }
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedLocation != null || onExitBrowser != null) {
                            IconButton(onClick = { handleBack() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = if (selectedLocation != null) "Navigate up" else "Back to home"
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(48.dp))
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                        ) {
                            Text(
                                text = "File Browser",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Crossfade(targetState = subtitle, label = "browserSubtitle") { text ->
                                val shouldMarquee = text.length > 52
                                if (shouldMarquee) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clipToBounds()
                                    ) {
                                        Text(
                                            text = text,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Clip,
                                            modifier = Modifier.basicMarquee(
                                                iterations = Int.MAX_VALUE,
                                                initialDelayMillis = 900
                                            )
                                        )
                                    }
                                } else {
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.padding(end = 6.dp)) {
                            IconButton(
                                onClick = { selectorExpanded = true },
                                modifier = Modifier.focusRequester(selectorButtonFocusRequester)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = selectorIcon,
                                        contentDescription = "Open location selector"
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = selectorExpanded,
                                onDismissRequest = { selectorExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Home")
                                            Text(
                                                "Storage locations",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Home,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        selectorExpanded = false
                                        openBrowserHome()
                                    }
                                )
                                storageLocations.forEach { location ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(location.typeLabel)
                                                Text(
                                                    location.name,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = iconForStorageKind(location.kind, context),
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            selectorExpanded = false
                                            openLocation(location)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    ) { paddingValues ->
        fun triggerPullRefresh() {
            if (isPullRefreshing) return
            coroutineScope.launch {
                isPullRefreshing = true
                if (selectedLocationId == null) {
                    storageLocationsRefreshToken += 1
                    delay(240)
                } else {
                    val refreshDir = currentDirectory ?: selectedLocation?.directory
                    if (refreshDir != null) {
                        loadDirectoryAsync(refreshDir)
                    }
                    val waitDeadline = System.currentTimeMillis() + 2200L
                    while (isLoadingDirectory && System.currentTimeMillis() < waitDeadline) {
                        delay(24)
                    }
                }
                isPullRefreshing = false
            }
        }

        val pullRefreshState = rememberPullRefreshState(
            refreshing = isPullRefreshing,
            onRefresh = { triggerPullRefresh() }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pullRefresh(pullRefreshState)
        ) {
            AnimatedContent(
                targetState = browserContentState,
                transitionSpec = {
                    if (isLoadingDirectory) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                    val forward = browserNavDirection == BrowserNavDirection.Forward
                    val enter = slideInHorizontally(
                        initialOffsetX = { width -> if (forward) width else -width / 4 },
                        animationSpec = tween(
                            durationMillis = BROWSER_PAGE_DURATION_MS,
                            easing = FastOutSlowInEasing
                        )
                    ) + fadeIn(
                        animationSpec = tween(
                            durationMillis = 190,
                            delayMillis = 50,
                            easing = LinearOutSlowInEasing
                        )
                    )
                    val exit = slideOutHorizontally(
                        targetOffsetX = { width -> if (forward) -width / 4 else width / 4 },
                        animationSpec = tween(
                            durationMillis = BROWSER_PAGE_DURATION_MS,
                            easing = FastOutSlowInEasing
                        )
                    ) + fadeOut(
                        animationSpec = tween(
                            durationMillis = 120,
                            easing = FastOutLinearInEasing
                        )
                    )
                    enter togetherWith exit
                    }
                },
                label = "browserContentTransition",
                modifier = Modifier.fillMaxSize()
            ) { state ->
                if (state.selectedLocationId == null) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = bottomContentPadding + 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                text = "Storage locations",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        items(storageLocations) { location ->
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { openLocation(location) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp, horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = iconForStorageKind(location.kind, context),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = location.typeLabel,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = location.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = directoryListState,
                        contentPadding = PaddingValues(bottom = bottomContentPadding)
                    ) {
                        if (showParentDirectoryEntry) {
                            val parentEntryKey = "parent:${state.currentDirectoryPath}"
                            item(key = parentEntryKey) {
                                AnimatedFileBrowserEntry(
                                    itemKey = parentEntryKey,
                                    animationEpoch = directoryAnimationEpoch,
                                    animateOnFirstComposition = isLoadingDirectory
                                ) {
                                    val rowFocusRequester = remember(parentEntryKey) { FocusRequester() }
                                    DisposableEffect(parentEntryKey) {
                                        browserEntryFocusRequesters[parentEntryKey] = rowFocusRequester
                                        onDispose { browserEntryFocusRequesters.remove(parentEntryKey) }
                                    }
                                    ParentDirectoryItemRow(
                                        onClick = { navigateUpWithinLocation() },
                                        rightFocusRequester = selectorButtonFocusRequester,
                                        rowFocusRequester = rowFocusRequester,
                                        onFocused = { browserFocusedEntryKey = parentEntryKey }
                                    )
                                }
                            }
                        }
                        items(
                            items = fileList,
                            key = { item -> item.file.absolutePath }
                        ) { item ->
                            val entryKey = item.file.absolutePath
                            AnimatedFileBrowserEntry(
                                itemKey = entryKey,
                                animationEpoch = directoryAnimationEpoch,
                                animateOnFirstComposition = isLoadingDirectory
                            ) {
                                val rowFocusRequester = remember(entryKey) { FocusRequester() }
                                DisposableEffect(entryKey) {
                                    browserEntryFocusRequesters[entryKey] = rowFocusRequester
                                    onDispose { browserEntryFocusRequesters.remove(entryKey) }
                                }
                                FileItemRow(
                                    item = item,
                                    isPlaying = item.file == playingFile,
                                    showFileIconChipBackground = showFileIconChipBackground,
                                    decoderExtensionArtworkHints = decoderExtensionArtworkHints,
                                    rightFocusRequester = selectorButtonFocusRequester,
                                    rowFocusRequester = rowFocusRequester,
                                    onFocused = { browserFocusedEntryKey = entryKey },
                                    onClick = {
                                        if (item.isArchive) {
                                            openArchive(item)
                                        } else if (item.isDirectory) {
                                            navigateTo(item.file)
                                        } else {
                                            val mounted = findArchiveMount(item.file.absolutePath)
                                            val sourceIdOverride = if (mounted != null) {
                                                val mountRoot = mounted.first
                                                val archivePath = mounted.second.archivePath
                                                val relativePath = item.file.absolutePath
                                                    .removePrefix(mountRoot)
                                                    .trimStart('/')
                                                    .replace('\\', '/')
                                                if (relativePath.isBlank()) {
                                                    null
                                                } else {
                                                    buildArchiveSourceId(archivePath, relativePath)
                                                }
                                            } else {
                                                null
                                            }
                                            onFileSelected(item.file, sourceIdOverride)
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
        }
    }
}

@Composable
private fun ParentDirectoryItemRow(
    onClick: () -> Unit,
    rightFocusRequester: FocusRequester? = null,
    rowFocusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusProperties {
                if (rightFocusRequester != null) {
                    right = rightFocusRequester
                }
            }
            .then(if (rowFocusRequester != null) Modifier.focusRequester(rowFocusRequester) else Modifier)
            .onFocusChanged { state -> if (state.isFocused) onFocused?.invoke() }
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(FILE_ICON_BOX_SIZE),
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
                    modifier = Modifier.size(FILE_ICON_GLYPH_SIZE)
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

@Composable
private fun AnimatedFileBrowserEntry(
    itemKey: String,
    animationEpoch: Int,
    animateOnFirstComposition: Boolean,
    content: @Composable () -> Unit
) {
    var visible by remember(itemKey, animationEpoch) {
        mutableStateOf(!animateOnFirstComposition)
    }
    LaunchedEffect(itemKey, animationEpoch, animateOnFirstComposition) {
        if (!animateOnFirstComposition) {
            visible = true
            return@LaunchedEffect
        }
        withFrameNanos { }
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = FILE_ENTRY_ANIM_DURATION_MS,
            easing = LinearOutSlowInEasing
        ),
        label = "fileEntryAlpha"
    )
    val offsetY by animateDpAsState(
        targetValue = if (visible) 0.dp else 10.dp,
        animationSpec = tween(
            durationMillis = FILE_ENTRY_ANIM_DURATION_MS,
            easing = LinearOutSlowInEasing
        ),
        label = "fileEntryOffset"
    )

    Box(
        modifier = Modifier
            .offset(y = offsetY)
            .alpha(alpha)
    ) {
        content()
    }
}

private fun shouldPublishDirectoryAllAtOnce(totalItems: Int): Boolean {
    return totalItems <= DIRECTORY_DIRECT_PUBLISH_MAX_ITEMS
}

private fun directoryPublishBatchSize(totalItems: Int): Int = when {
    totalItems <= 5000 -> 256
    totalItems <= 10000 -> 512
    else -> 1024
}

private data class StorageLocation(
    val id: String,
    val kind: StorageKind,
    val typeLabel: String,
    val name: String,
    val directory: File
)

private enum class StorageKind {
    ROOT,
    INTERNAL,
    SD,
    USB
}

private enum class MountKindHint {
    SD,
    USB
}

private fun iconForStorageKind(kind: StorageKind, context: Context): ImageVector {
    return when (kind) {
        StorageKind.ROOT -> Icons.Default.Folder
        StorageKind.INTERNAL -> {
            val isTablet = context.resources.configuration.smallestScreenWidthDp >= 600
            if (isTablet) Icons.Default.TabletAndroid else Icons.Default.PhoneAndroid
        }
        StorageKind.SD -> Icons.Default.SdCard
        StorageKind.USB -> Icons.Default.Usb
    }
}

private fun detectStorageLocations(context: Context): List<StorageLocation> {
    val results = mutableListOf<StorageLocation>()
    val seenPaths = mutableSetOf<String>()

    fun addLocation(kind: StorageKind, typeLabel: String, name: String, directory: File) {
        val normalizedPath = directory.absolutePath
        if (normalizedPath in seenPaths) return
        if (!directory.exists() || !directory.isDirectory) return
        results += StorageLocation(
            id = normalizedPath,
            kind = kind,
            typeLabel = typeLabel,
            name = name,
            directory = directory
        )
        seenPaths += normalizedPath
    }

    addLocation(
        kind = StorageKind.ROOT,
        typeLabel = "Root",
        name = "/",
        directory = File("/")
    )

    val internalStorage = Environment.getExternalStorageDirectory()
    addLocation(
        kind = StorageKind.INTERNAL,
        typeLabel = "Internal storage",
        name = internalStorage.absolutePath,
        directory = internalStorage
    )

    // On some Android 10 devices/emulators with scoped-storage quirks, the legacy
    // Environment path can be missing or unusable. Recover internal storage root
    // from app-specific external dirs as a fallback.
    context.getExternalFilesDirs(null)
        .orEmpty()
        .forEach { externalDir ->
            if (externalDir == null) return@forEach
            if (Environment.isExternalStorageRemovable(externalDir)) return@forEach
            val volumeRoot = resolveVolumeRoot(externalDir) ?: return@forEach
            addLocation(
                kind = StorageKind.INTERNAL,
                typeLabel = "Internal storage",
                name = volumeRoot.absolutePath,
                directory = volumeRoot
            )
        }

    val storageManager: StorageManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        context.getSystemService(StorageManager::class.java)
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
    }
    val removableFromVolumes = mutableSetOf<String>()
    data class RemovableVolumeCandidate(
        val root: File,
        val description: String,
        val hasUsbMarker: Boolean,
        val hasSdMarker: Boolean,
        val mountKindHint: MountKindHint?
    )
    val volumeCandidates = mutableListOf<RemovableVolumeCandidate>()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        storageManager?.storageVolumes.orEmpty().forEach { volume ->
            if (!volume.isRemovable) return@forEach
            val volumeRoot = resolveStorageVolumeRoot(volume) ?: return@forEach
            val description = volume.getDescription(context).orEmpty().trim()
            val detectionText = "${description.lowercase()} ${volumeRoot.absolutePath.lowercase()}"
            volumeCandidates += RemovableVolumeCandidate(
                root = volumeRoot,
                description = description,
                hasUsbMarker = detectionText.contains("usb") || detectionText.contains("otg"),
                hasSdMarker = detectionText.contains("sd card") ||
                    detectionText.contains("sdcard") ||
                    detectionText.contains(" microsd") ||
                    detectionText.contains("sd "),
                mountKindHint = detectMountKindHint(volumeRoot)
            )
        }
    }

    volumeCandidates.forEach { candidate ->
        val isUsb = when {
            candidate.hasUsbMarker && !candidate.hasSdMarker -> true
            candidate.hasSdMarker && !candidate.hasUsbMarker -> false
            candidate.mountKindHint == MountKindHint.USB -> true
            candidate.mountKindHint == MountKindHint.SD -> false
            else -> false
        }
        val label = candidate.description.ifBlank { candidate.root.name.ifBlank { "Volume" } }
        val typeLabel = if (isUsb) "$label (USB)" else "$label (SD)"
        addLocation(
            kind = if (isUsb) StorageKind.USB else StorageKind.SD,
            typeLabel = typeLabel,
            name = candidate.root.absolutePath,
            directory = candidate.root
        )
        removableFromVolumes += candidate.root.absolutePath
    }

    // Fallback scan for removable media on devices that may not expose a
    // StorageVolume path on some OEMs.
    context.getExternalFilesDirs(null)
        .orEmpty()
        .forEach { externalDir ->
            if (externalDir == null) return@forEach
            val volumeRoot = resolveVolumeRoot(externalDir) ?: return@forEach
            if (volumeRoot.absolutePath in removableFromVolumes) return@forEach
            val pathLower = volumeRoot.absolutePath.lowercase()
            val isRemovable = Environment.isExternalStorageRemovable(externalDir)
            if (!isRemovable) return@forEach
            val mountKindHint = detectMountKindHint(volumeRoot)

            val isUsb = when {
                pathLower.contains("usb") || pathLower.contains("otg") -> true
                pathLower.contains("sd") -> false
                mountKindHint == MountKindHint.USB -> true
                mountKindHint == MountKindHint.SD -> false
                else -> false
            }
            val label = volumeRoot.name.ifBlank { "Volume" }
            val typeLabel = if (isUsb) "$label (USB)" else "$label (SD)"
            addLocation(
                kind = if (isUsb) StorageKind.USB else StorageKind.SD,
                typeLabel = typeLabel,
                name = volumeRoot.absolutePath,
                directory = volumeRoot
            )
        }

    return results
}

private fun resolveStorageVolumeRoot(volume: StorageVolume): File? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        volume.directory?.let { directory ->
            if (directory.exists() && directory.isDirectory) return directory
        }
    }
    runCatching {
        val method = StorageVolume::class.java.getMethod("getPathFile")
        (method.invoke(volume) as? File)?.let { file ->
            if (file.exists() && file.isDirectory) return file
        }
    }
    runCatching {
        val method = StorageVolume::class.java.getMethod("getPath")
        val path = method.invoke(volume) as? String
        path?.let { File(it) }?.let { file ->
            if (file.exists() && file.isDirectory) return file
        }
    }
    return null
}

private fun resolveVolumeRoot(appSpecificDir: File): File? {
    val marker = "/Android/"
    val absolutePath = appSpecificDir.absolutePath
    val markerIndex = absolutePath.indexOf(marker)
    if (markerIndex <= 0) return null
    return File(absolutePath.substring(0, markerIndex))
}

private fun detectMountKindHint(root: File): MountKindHint? {
    val mountPoint = root.absolutePath
    val mounts = sequenceOf("/proc/self/mounts", "/proc/mounts")
    mounts.forEach { mountsPath ->
        val hint = runCatching {
            File(mountsPath).useLines { lines ->
                lines
                    .mapNotNull { line ->
                        val parts = line.split(' ')
                        if (parts.size < 2) return@mapNotNull null
                        val source = parts[0]
                        val target = parts[1]
                        if (target != mountPoint) return@mapNotNull null
                        classifyMountSource(source)
                    }
                    .firstOrNull()
            }
        }.getOrNull()
        if (hint != null) return hint
    }
    return null
}

private fun classifyMountSource(source: String): MountKindHint? {
    val lower = source.lowercase(Locale.US)
    if (lower.contains("public:")) {
        val major = lower
            .substringAfter("public:", "")
            .substringBefore(',')
            .toIntOrNull()
        return when (major) {
            179 -> MountKindHint.SD
            8 -> MountKindHint.USB
            else -> null
        }
    }
    return when {
        "mmcblk" in lower -> MountKindHint.SD
        Regex("""(^|/)(sd[a-z]\d*|usb\d+|uas\d+)($|/)""").containsMatchIn(lower) -> MountKindHint.USB
        else -> null
    }
}

private fun isWithinRoot(file: File, root: File): Boolean {
    val filePath = file.absolutePath
    val rootPath = root.absolutePath
    return filePath == rootPath || filePath.startsWith("$rootPath/")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemRow(
    item: FileItem,
    isPlaying: Boolean,
    showFileIconChipBackground: Boolean,
    decoderExtensionArtworkHints: Map<String, DecoderArtworkHint> = emptyMap(),
    rightFocusRequester: FocusRequester? = null,
    rowFocusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val isVideoFile = !item.isDirectory && isLikelyVideoFile(item.file)
    val decoderArtworkHint = if (item.isDirectory || isVideoFile) {
        null
    } else {
        resolveDecoderArtworkHintForFileName(item.file.name, decoderExtensionArtworkHints)
    }
    val subtitle by produceState(
        initialValue = if (item.isDirectory && !item.isArchive) "Loading..." else {
            val format = inferredPrimaryExtensionForName(item.file.name)?.uppercase(Locale.ROOT) ?: "UNKNOWN"
            "$format • ${formatFileSizeHumanReadable(item.size)}"
        },
        key1 = item.file.absolutePath,
        key2 = item.kind,
        key3 = item.size
    ) {
        value = withContext(Dispatchers.IO) {
            if (item.isArchive) {
                "ZIP archive • ${formatFileSizeHumanReadable(item.size)}"
            } else if (item.isDirectory) {
                buildFolderSummary(item.file)
            } else {
                val format = inferredPrimaryExtensionForName(item.file.name)?.uppercase(Locale.ROOT) ?: "UNKNOWN"
                "$format • ${formatFileSizeHumanReadable(item.size)}"
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusProperties {
                if (rightFocusRequester != null) {
                    right = rightFocusRequester
                }
            }
            .then(if (rowFocusRequester != null) Modifier.focusRequester(rowFocusRequester) else Modifier)
            .onFocusChanged { state -> if (state.isFocused) onFocused?.invoke() }
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val showIconChipBackground = item.isDirectory || showFileIconChipBackground
        val chipShape = RoundedCornerShape(11.dp)
        val chipContainerColor = if (item.isDirectory) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
        val chipContentColor = if (item.isDirectory) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
        val iconTint = if (showIconChipBackground) {
            chipContentColor
        } else {
            MaterialTheme.colorScheme.primary
        }
        Box(
            modifier = Modifier.size(FILE_ICON_BOX_SIZE),
            contentAlignment = Alignment.Center
        ) {
            val iconContent: @Composable () -> Unit = {
                if (item.isDirectory) {
                    if (item.isArchive) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_folder_zip),
                            contentDescription = "ZIP archive",
                            tint = iconTint,
                            modifier = Modifier.size(FILE_ICON_GLYPH_SIZE)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Directory",
                            tint = iconTint,
                            modifier = Modifier.size(FILE_ICON_GLYPH_SIZE)
                        )
                    }
                } else {
                    val contentDescription = when {
                        isVideoFile -> "Video file"
                        decoderArtworkHint == DecoderArtworkHint.TrackedFile -> "Tracked file"
                        decoderArtworkHint == DecoderArtworkHint.GameFile -> "Game file"
                        else -> "Audio file"
                    }
                    if (isVideoFile) {
                        Icon(
                            imageVector = Icons.Default.VideoFile,
                            contentDescription = contentDescription,
                            tint = iconTint,
                            modifier = Modifier.size(FILE_ICON_GLYPH_SIZE)
                        )
                    } else if (decoderArtworkHint == DecoderArtworkHint.TrackedFile) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_file_tracked),
                            contentDescription = contentDescription,
                            tint = iconTint,
                            modifier = Modifier.size(FILE_ICON_GLYPH_SIZE)
                        )
                    } else if (decoderArtworkHint == DecoderArtworkHint.GameFile) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_file_game),
                            contentDescription = contentDescription,
                            tint = iconTint,
                            modifier = Modifier.size(FILE_ICON_GLYPH_SIZE)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AudioFile,
                            contentDescription = contentDescription,
                            tint = iconTint,
                            modifier = Modifier.size(FILE_ICON_GLYPH_SIZE)
                        )
                    }
                }
            }
            if (showIconChipBackground) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = chipContainerColor,
                            shape = chipShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    iconContent()
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    iconContent()
                }
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            val shouldMarquee = item.name.length > 28
            if (shouldMarquee) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            initialDelayMillis = 900
                        )
                    )
                }
            } else {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        if (isPlaying) {
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Playing",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun isLikelyVideoFile(file: File): Boolean {
    val candidates = extensionCandidatesForName(file.name)
    if (candidates.isEmpty()) return false
    return candidates.any { extension ->
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        mimeType?.startsWith("video/") == true || extension in FALLBACK_VIDEO_EXTENSIONS
    }
}

private fun formatFileSizeHumanReadable(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble().coerceAtLeast(0.0)
    var unitIndex = 0

    while (size >= 1024.0 && unitIndex < units.lastIndex) {
        size /= 1024.0
        unitIndex++
    }

    return if (unitIndex == 0) {
        String.format(Locale.US, "%.0f %s", size, units[unitIndex])
    } else {
        String.format(Locale.US, "%.1f %s", size, units[unitIndex])
    }
}

private fun readZipEntrySizesForDirectory(
    archivePath: String,
    relativeDirectory: String
): Map<String, Long> {
    val normalizedDirectory = relativeDirectory.replace('\\', '/').trim('/')
    return try {
        ZipFile(File(archivePath)).use { zip ->
            val sizes = LinkedHashMap<String, Long>()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory || entry.size < 0L) continue
                val normalizedName = entry.name.replace('\\', '/').trimStart('/')
                if (normalizedName.isBlank()) continue
                val parent = normalizedName.substringBeforeLast('/', "")
                if (parent != normalizedDirectory) continue
                val leaf = normalizedName.substringAfterLast('/')
                if (leaf.isBlank()) continue
                sizes[leaf] = entry.size
            }
            sizes
        }
    } catch (_: Exception) {
        emptyMap()
    }
}

private fun buildFolderSummary(folder: File): String {
    val children = folder.listFiles().orEmpty()
    val folderCount = children.count { it.isDirectory }
    val fileCount = children.count { it.isFile }

    val parts = mutableListOf<String>()
    if (folderCount > 0) {
        parts += pluralize(folderCount, "folder")
    }
    // Only show file count if there are files OR if there are no folders
    if (fileCount > 0 || folderCount == 0) {
        parts += pluralize(fileCount, "file")
    }
    return parts.joinToString(" • ")
}

private fun pluralize(count: Int, singular: String): String {
    val word = if (count == 1) singular else "${singular}s"
    return "$count $word"
}
