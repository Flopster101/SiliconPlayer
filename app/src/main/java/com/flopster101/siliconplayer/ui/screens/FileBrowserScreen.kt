package com.flopster101.siliconplayer.ui.screens

import android.content.Context
import android.content.res.Configuration
import android.os.Environment
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
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.R
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun FileBrowserScreen(
    repository: FileRepository,
    initialLocationId: String? = null,
    initialDirectoryPath: String? = null,
    onFileSelected: (File, String?) -> Unit,
    onVisiblePlayableFilesChanged: (List<File>) -> Unit = {},
    onBrowserLocationChanged: (String?, String?) -> Unit = { _, _ -> },
    bottomContentPadding: Dp = 0.dp,
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
                    // Publish results progressively at a paced rate so rows appear while loading.
                    fileList.clear()
                    val publishBatchSize = directoryPublishBatchSize(loadedFiles.size)
                    val publishDelayMs = directoryPublishDelayMs(loadedFiles.size)
                    var index = 0
                    while (index < loadedFiles.size) {
                        ensureActive()
                        if (currentDirectory?.absolutePath != targetPath || selectedLocationId == null) break

                        val end = min(index + publishBatchSize, loadedFiles.size)
                        val chunk = loadedFiles.subList(index, end)
                        fileList.addAll(chunk)
                        index = end
                        // Yield a frame between batches to keep UI responsive/animated.
                        withFrameNanos { }
                        if (index < loadedFiles.size) {
                            delay(publishDelayMs)
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
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Box(modifier = Modifier.padding(end = 6.dp)) {
                            IconButton(onClick = { selectorExpanded = true }) {
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
                        items(
                            items = fileList,
                            key = { item -> item.file.absolutePath }
                        ) { item ->
                            AnimatedFileBrowserEntry(
                                itemKey = item.file.absolutePath,
                                animationEpoch = directoryAnimationEpoch,
                                animateOnFirstComposition = isLoadingDirectory
                            ) {
                                FileItemRow(
                                    item = item,
                                    isPlaying = item.file == playingFile,
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

private fun directoryPublishBatchSize(totalItems: Int): Int = when {
    totalItems <= 36 -> 1
    totalItems <= 120 -> 2
    totalItems <= 260 -> 4
    else -> 8
}

private fun directoryPublishDelayMs(totalItems: Int): Long = when {
    totalItems <= 36 -> 24L
    totalItems <= 120 -> 18L
    totalItems <= 260 -> 12L
    else -> 8L
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

    context.getExternalFilesDirs(null)
        .orEmpty()
        .forEach { externalDir ->
            if (externalDir == null) return@forEach
            val volumeRoot = resolveVolumeRoot(externalDir) ?: return@forEach
            val pathLower = volumeRoot.absolutePath.lowercase()
            val isRemovable = Environment.isExternalStorageRemovable(externalDir)
            if (!isRemovable) return@forEach

            val isUsb = pathLower.contains("usb") || pathLower.contains("otg")
            val typeLabel = if (isUsb) "USB storage" else "SD card"
            val name = volumeRoot.name.ifBlank { volumeRoot.absolutePath }
            addLocation(
                kind = if (isUsb) StorageKind.USB else StorageKind.SD,
                typeLabel = typeLabel,
                name = name,
                directory = volumeRoot
            )
        }

    return results
}

private fun resolveVolumeRoot(appSpecificDir: File): File? {
    val marker = "/Android/"
    val absolutePath = appSpecificDir.absolutePath
    val markerIndex = absolutePath.indexOf(marker)
    if (markerIndex <= 0) return null
    return File(absolutePath.substring(0, markerIndex))
}

private fun isWithinRoot(file: File, root: File): Boolean {
    val filePath = file.absolutePath
    val rootPath = root.absolutePath
    return filePath == rootPath || filePath.startsWith("$rootPath/")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemRow(item: FileItem, isPlaying: Boolean, onClick: () -> Unit) {
    val subtitle by produceState(
        initialValue = if (item.isDirectory && !item.isArchive) "Loading..." else {
            val format = item.file.extension.uppercase().ifBlank { "UNKNOWN" }
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
                val format = item.file.extension.uppercase().ifBlank { "UNKNOWN" }
                "$format • ${formatFileSizeHumanReadable(item.size)}"
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(34.dp),
            contentAlignment = Alignment.Center
        ) {
            if (item.isDirectory) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(11.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.isArchive) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_folder_zip),
                            contentDescription = "ZIP archive",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Directory",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.Default.AudioFile,
                    contentDescription = "Audio file",
                    tint = MaterialTheme.colorScheme.primary
                )
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
