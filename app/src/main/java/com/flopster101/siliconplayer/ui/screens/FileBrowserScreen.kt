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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.data.FileItem
import com.flopster101.siliconplayer.data.FileRepository
import java.io.File
import java.util.Locale
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
private const val DIRECTORY_CHUNK_SIZE = 48
private const val MIN_LOADING_SPINNER_MS = 220L

private enum class BrowserNavDirection {
    Forward,
    Backward
}

private data class BrowserContentState(
    val selectedLocationId: String?,
    val currentDirectoryPath: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    repository: FileRepository,
    initialLocationId: String? = null,
    initialDirectoryPath: String? = null,
    onFileSelected: (File) -> Unit,
    onVisiblePlayableFilesChanged: (List<File>) -> Unit = {},
    onBrowserLocationChanged: (String?, String?) -> Unit = { _, _ -> },
    bottomContentPadding: Dp = 0.dp,
    backHandlingEnabled: Boolean = true,
    onExitBrowser: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    showPrimaryTopBar: Boolean = true
) {
    val context = LocalContext.current
    val storageLocations = remember(context) { detectStorageLocations(context) }
    var selectedLocationId by remember { mutableStateOf<String?>(null) }
    var currentDirectory by remember { mutableStateOf<File?>(null) }
    val fileList = remember { mutableStateListOf<FileItem>() }
    var selectorExpanded by remember { mutableStateOf(false) }
    var browserNavDirection by remember { mutableStateOf(BrowserNavDirection.Forward) }
    var isLoadingDirectory by remember { mutableStateOf(false) }
    var hasRestoredInitialNavigation by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var directoryLoadJob by remember { mutableStateOf<Job?>(null) }

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
        fileList.clear()

        val targetPath = directory.absolutePath
        val loadingStartedAt = System.currentTimeMillis()
        directoryLoadJob = coroutineScope.launch {
            try {
                // Ensure at least one frame is rendered with the spinner before parsing starts.
                withFrameNanos { }
                delay(16)
                val loadedFiles = withContext(Dispatchers.IO) {
                    repository.getFiles(directory)
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
                    // Publish results progressively so large folders don't freeze frames.
                    fileList.clear()
                    var index = 0
                    while (index < loadedFiles.size) {
                        ensureActive()
                        if (currentDirectory?.absolutePath != targetPath || selectedLocationId == null) break

                        val end = min(index + DIRECTORY_CHUNK_SIZE, loadedFiles.size)
                        val chunk = loadedFiles.subList(index, end)
                        fileList.addAll(chunk)
                        index = end
                        // Yield a frame between chunks to keep UI responsive/animated.
                        withFrameNanos { }
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
        browserNavDirection = BrowserNavDirection.Backward
        selectedLocationId = null
        currentDirectory = null
        fileList.clear()
        onVisiblePlayableFilesChanged(emptyList())
        onBrowserLocationChanged(null, null)
    }

    fun openLocation(location: StorageLocation) {
        browserNavDirection = BrowserNavDirection.Forward
        selectedLocationId = location.id
        currentDirectory = location.directory
        loadDirectoryAsync(location.directory)
        onBrowserLocationChanged(location.id, location.directory.absolutePath)
    }

    fun navigateTo(directory: File) {
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
        onBrowserLocationChanged(selectedLocationId, directory.absolutePath)
    }

    fun navigateUpWithinLocation() {
        val location = selectedLocation ?: return openBrowserHome()
        val directory = currentDirectory ?: return openBrowserHome()
        val root = location.directory

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
            ?.let { path -> File(path) }
            ?.takeIf { file ->
                file.exists() && file.isDirectory && isWithinRoot(file, initialLocation.directory)
            }
            ?: initialLocation.directory

        browserNavDirection = BrowserNavDirection.Forward
        selectedLocationId = initialLocation.id
        currentDirectory = restoredDirectory
        loadDirectoryAsync(restoredDirectory)
        onBrowserLocationChanged(initialLocation.id, restoredDirectory.absolutePath)
    }

    BackHandler(
        enabled = backHandlingEnabled && (selectedLocation != null || onExitBrowser != null),
        onBack = { handleBack() }
    )

    val subtitle = if (selectedLocation == null) {
        "Storage locations"
    } else {
        currentDirectory?.absolutePath ?: selectedLocation.name
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(text = "Silicon Player")
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
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
                            Icon(
                                imageVector = iconForStorageKind(location.kind, context),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
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
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomContentPadding)
                ) {
                    items(
                        items = fileList,
                        key = { it.file.absolutePath }
                    ) { item ->
                        FileItemRow(item = item, onClick = {
                            if (item.isDirectory) {
                                navigateTo(item.file)
                            } else {
                                onFileSelected(item.file)
                            }
                        })
                    }
                }
            }
        }
    }
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
fun FileItemRow(item: FileItem, onClick: () -> Unit) {
    val subtitle by produceState(
        initialValue = if (item.isDirectory) "Loading..." else formatFileSizeHumanReadable(item.file.length()),
        key1 = item.file.absolutePath,
        key2 = item.isDirectory,
        key3 = item.file.lastModified()
    ) {
        value = withContext(Dispatchers.IO) {
            if (item.isDirectory) {
                buildFolderSummary(item.file)
            } else {
                formatFileSizeHumanReadable(item.file.length())
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
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Directory",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
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

private fun buildFolderSummary(folder: File): String {
    val children = folder.listFiles().orEmpty()
    val folderCount = children.count { it.isDirectory }
    val fileCount = children.count { it.isFile }

    val parts = mutableListOf<String>()
    if (folderCount > 0) {
        parts += pluralize(folderCount, "folder")
    }
    parts += pluralize(fileCount, "file")
    return parts.joinToString(" • ")
}

private fun pluralize(count: Int, singular: String): String {
    val word = if (count == 1) singular else "${singular}s"
    return "$count $word"
}
