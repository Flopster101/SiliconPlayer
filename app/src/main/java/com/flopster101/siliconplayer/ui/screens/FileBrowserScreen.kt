package com.flopster101.siliconplayer.ui.screens

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.extensionCandidatesForName
import com.flopster101.siliconplayer.inferredPrimaryExtensionForName
import com.flopster101.siliconplayer.FilePreviewKind
import com.flopster101.siliconplayer.DecoderArtworkHint
import com.flopster101.siliconplayer.BrowserLaunchState
import com.flopster101.siliconplayer.BrowserLocationModel
import com.flopster101.siliconplayer.R
import com.flopster101.siliconplayer.rememberDialogLazyListScrollbarAlpha
import com.flopster101.siliconplayer.resolveDecoderArtworkHintForFileName
import com.flopster101.siliconplayer.resolveBrowserLocationModel
import com.flopster101.siliconplayer.buildHttpDisplayUri
import com.flopster101.siliconplayer.buildSmbDisplayUri
import com.flopster101.siliconplayer.decodePercentEncodedForDisplay
import com.flopster101.siliconplayer.HomePinnedEntry
import com.flopster101.siliconplayer.RecentPathEntry
import com.flopster101.siliconplayer.previewPinnedHomeEntryInsertion
import com.flopster101.siliconplayer.PINNED_HOME_ENTRIES_LIMIT
import com.flopster101.siliconplayer.parseHttpSourceSpecFromInput
import com.flopster101.siliconplayer.parseSmbSourceSpecFromInput
import com.flopster101.siliconplayer.data.buildArchiveSourceId
import com.flopster101.siliconplayer.data.buildArchiveDirectoryPath
import com.flopster101.siliconplayer.data.parseArchiveSourceId
import com.flopster101.siliconplayer.data.parseArchiveLogicalPath
import com.flopster101.siliconplayer.LocalBrowserListLocation
import com.flopster101.siliconplayer.LocalBrowserListingAdapter
import com.flopster101.siliconplayer.data.FileItem
import com.flopster101.siliconplayer.data.FileRepository
import com.flopster101.siliconplayer.data.ensureArchiveMounted
import com.flopster101.siliconplayer.data.resolveArchiveLogicalDirectory
import com.flopster101.siliconplayer.data.resolveArchiveLocationToFile
import com.flopster101.siliconplayer.session.ExportFileItem
import com.flopster101.siliconplayer.session.ExportConflictDecision
import com.flopster101.siliconplayer.session.ExportNameConflict
import com.flopster101.siliconplayer.session.exportFilesToTree
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
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

private data class BrowserContentState(
    val pane: BrowserPane,
    val selectedLocationId: String?,
    val currentDirectoryPath: String?
)

private enum class BrowserPane {
    StorageLocations,
    LoadingDirectory,
    DirectoryEntries
}

private data class ArchiveMountInfo(
    val archivePath: String,
    val parentPath: String,
    val returnTargetPath: String? = null,
    val logicalArchivePath: String? = null,
    val smbSourceNodeId: Long? = null,
    val httpSourceNodeId: Long? = null,
    val httpRootPath: String? = null
)

private data class ArchiveToolbarContext(
    val subtitle: String,
    val isRemote: Boolean,
    val sourceLabel: String,
    val sourceTypeLabel: String,
    val sourceIcon: ImageVector
)

private const val LOCAL_BROWSER_SHOW_INTERMEDIARY_LOADING_PAGE = false

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterialApi::class,
    ExperimentalFoundationApi::class
)
@Composable
internal fun FileBrowserScreen(
    repository: FileRepository,
    decoderExtensionArtworkHints: Map<String, DecoderArtworkHint> = emptyMap(),
    initialLocationId: String? = null,
    initialDirectoryPath: String? = null,
    initialSmbSourceNodeId: Long? = null,
    initialHttpSourceNodeId: Long? = null,
    initialHttpRootPath: String? = null,
    restoreFocusedItemRequestToken: Int = 0,
    onFileSelected: (File, String?) -> Unit,
    onVisiblePlayableFilesChanged: (List<File>) -> Unit = {},
    onBrowserLocationChanged: (BrowserLaunchState) -> Unit = {},
    bottomContentPadding: Dp = 0.dp,
    showParentDirectoryEntry: Boolean = true,
    showFileIconChipBackground: Boolean = true,
    backHandlingEnabled: Boolean = true,
    onExitBrowser: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    showPrimaryTopBar: Boolean = true,
    playingFile: File? = null,
    pinnedHomeEntries: List<HomePinnedEntry> = emptyList(),
    onPinHomeEntry: (RecentPathEntry, Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val localListingAdapter = remember(repository) { LocalBrowserListingAdapter(repository) }
    var storageLocationsRefreshToken by remember { mutableIntStateOf(0) }
    val storageLocations = remember(context, storageLocationsRefreshToken) { detectStorageLocations(context) }
    var selectedLocationId by remember { mutableStateOf<String?>(null) }
    var currentDirectory by remember { mutableStateOf<File?>(null) }
    val fileList = remember { mutableStateListOf<FileItem>() }
    var selectorExpanded by remember { mutableStateOf(false) }
    var browserNavDirection by remember { mutableStateOf(BrowserPageNavDirection.Forward) }
    var isLoadingDirectory by remember { mutableStateOf(false) }
    var lastAppliedInitialNavigationKey by remember { mutableStateOf<String?>(null) }
    val directoryListState = rememberLazyListState()
    var launchAutoScrollTargetKey by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var directoryLoadJob by remember { mutableStateOf<Job?>(null) }
    var isPullRefreshing by remember { mutableStateOf(false) }
    var directoryAnimationEpoch by remember { mutableIntStateOf(0) }
    val loadingLogLines = remember { mutableStateListOf<String>() }
    val archiveMountRoots = remember { mutableStateMapOf<String, ArchiveMountInfo>() }
    val selectorButtonFocusRequester = remember { FocusRequester() }
    var browserFocusedEntryKey by remember { mutableStateOf<String?>(null) }
    val browserEntryFocusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    val browserSearchController = rememberBrowserSearchController()
    val browserSelectionController = rememberBrowserSelectionController<String>()
    var browserInfoFields by remember { mutableStateOf<List<BrowserInfoField>>(emptyList()) }
    var showBrowserInfoDialog by remember { mutableStateOf(false) }
    var textPreviewDialogState by remember { mutableStateOf<Pair<String, String>?>(null) }
    var imagePreviewDialogState by remember { mutableStateOf<Pair<String, File>?>(null) }
    var pendingExportFiles by remember { mutableStateOf<List<ExportFileItem>>(emptyList()) }
    var exportConflictDialogState by remember { mutableStateOf<BrowserExportConflictDialogState?>(null) }
    var pendingDeleteFilePaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingPinConfirmation by remember { mutableStateOf<Pair<RecentPathEntry, Boolean>?>(null) }
    var pendingPinEvictionCandidate by remember { mutableStateOf<HomePinnedEntry?>(null) }

    val selectedLocation = storageLocations.firstOrNull { it.id == selectedLocationId }
    val hasActiveDirectory = currentDirectory != null
    val hasPendingInitialNavigation =
        selectedLocationId == null &&
            !hasActiveDirectory &&
            !initialDirectoryPath.isNullOrBlank()
    val browserContentState = remember(
        selectedLocationId,
        currentDirectory?.absolutePath,
        isLoadingDirectory,
        hasPendingInitialNavigation
    ) {
        BrowserContentState(
            pane = when {
                selectedLocationId == null && !hasActiveDirectory && hasPendingInitialNavigation ->
                    BrowserPane.LoadingDirectory
                selectedLocationId == null && !hasActiveDirectory -> BrowserPane.StorageLocations
                LOCAL_BROWSER_SHOW_INTERMEDIARY_LOADING_PAGE && isLoadingDirectory -> BrowserPane.LoadingDirectory
                else -> BrowserPane.DirectoryEntries
            },
            selectedLocationId = selectedLocationId,
            currentDirectoryPath = currentDirectory?.absolutePath
        )
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
        loadingLogLines.clear()
        appendLoadingLog("Opening ${directory.absolutePath}")
        appendLoadingLog("Listing directory entries")

        val targetPath = directory.absolutePath
        val loadingStartedAt = System.currentTimeMillis()
        val normalizedTargetPathForMountLookup = runCatching { File(targetPath).canonicalPath }
            .getOrElse { File(targetPath).absolutePath }
            .replace('\\', '/')
            .trimEnd('/')
            .ifBlank { "/" }
        val mountedForTarget = archiveMountRoots.entries
            .asSequence()
            .filter { (mountRoot, _) ->
                normalizedTargetPathForMountLookup == mountRoot ||
                    normalizedTargetPathForMountLookup.startsWith("$mountRoot/")
            }
            .maxByOrNull { (mountRoot, _) -> mountRoot.length }
            ?.toPair()
        directoryLoadJob = coroutineScope.launch {
            try {
                // Ensure at least one frame is rendered with the spinner before parsing starts.
                withFrameNanos { }
                delay(16)
                val loadedFiles = withContext(Dispatchers.IO) {
                    val baseFiles = localListingAdapter
                        .list(LocalBrowserListLocation(directory))
                        .getOrThrow()
                        .entries
                    if (mountedForTarget == null) {
                        baseFiles
                    } else {
                        val mountRoot = mountedForTarget.first
                        val mountInfo = mountedForTarget.second
                        val relativeDirectory = if (normalizedTargetPathForMountLookup == mountRoot) {
                            ""
                        } else {
                            normalizedTargetPathForMountLookup.removePrefix("$mountRoot/")
                                .replace('\\', '/')
                                .trim('/')
                        }
                        val zipSizes = readZipEntrySizesForDirectory(
                            context = context,
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
                        .filter { item -> repository.isPlayableFile(item.file) }
                        .map { it.file }
                        .toList()
                )
                val stillOnSameDirectory = currentDirectory?.absolutePath == targetPath
                if (stillOnSameDirectory) {
                    val folders = loadedFiles.count { it.isDirectory }
                    val files = loadedFiles.size - folders
                    appendLoadingLog("Found ${loadedFiles.size} entries")
                    appendLoadingLog("$folders folders, $files files")
                    appendLoadingLog("Load finished")
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
                            if (currentDirectory?.absolutePath != targetPath) break

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
        browserNavDirection = BrowserPageNavDirection.Backward
        selectedLocationId = null
        currentDirectory = null
        fileList.clear()
        loadingLogLines.clear()
        onVisiblePlayableFilesChanged(emptyList())
        onBrowserLocationChanged(BrowserLaunchState())
    }

    fun openLocation(location: StorageLocation) {
        launchAutoScrollTargetKey = null
        browserNavDirection = BrowserPageNavDirection.Forward
        selectedLocationId = location.id
        currentDirectory = location.directory
        loadDirectoryAsync(location.directory)
        onBrowserLocationChanged(
            BrowserLaunchState(
                locationId = location.id,
                directoryPath = location.directory.absolutePath
            )
        )
    }

    fun normalizePathForArchiveMountLookup(path: String): String {
        val normalized = runCatching { File(path).canonicalPath }
            .getOrElse { File(path).absolutePath }
            .replace('\\', '/')
        return normalized.trimEnd('/').ifBlank { "/" }
    }

    fun findArchiveMount(path: String): Pair<String, ArchiveMountInfo>? {
        val normalizedPath = normalizePathForArchiveMountLookup(path)
        return archiveMountRoots
            .asSequence()
            .filter { (mountRoot, _) ->
                normalizedPath == mountRoot || normalizedPath.startsWith("$mountRoot/")
            }
            .maxByOrNull { (mountRoot, _) -> mountRoot.length }
            ?.toPair()
    }

    fun resolveLogicalArchivePath(directory: File?): String? {
        val dir = directory ?: return null
        val mounted = findArchiveMount(dir.absolutePath) ?: return null
        val mountRoot = mounted.first
        val archivePath = mounted.second.logicalArchivePath ?: mounted.second.archivePath
        val normalizedDirectoryPath = normalizePathForArchiveMountLookup(dir.absolutePath)
        if (normalizedDirectoryPath == mountRoot) {
            return buildArchiveDirectoryPath(archivePath)
        }
        val relative = normalizedDirectoryPath.removePrefix("$mountRoot/").replace('\\', '/').trimStart('/')
        return buildArchiveDirectoryPath(archivePath, relative)
    }

    fun resolveArchiveRelativePath(absolutePath: String, mountRoot: String): String? {
        val normalizedFilePath = normalizePathForArchiveMountLookup(absolutePath)
        if (normalizedFilePath == mountRoot || !normalizedFilePath.startsWith("$mountRoot/")) return null
        return normalizedFilePath.removePrefix("$mountRoot/").trim('/').ifBlank { null }
    }

    fun emitBrowserLocationChange(directory: File) {
        val mounted = findArchiveMount(directory.absolutePath)?.second
        val logicalArchivePath = resolveLogicalArchivePath(directory)
        val archiveOriginPath = logicalArchivePath?.let { path -> parseArchiveLogicalPath(path)?.first }
        val isArchiveSmb = archiveOriginPath != null &&
            parseSmbSourceSpecFromInput(archiveOriginPath) != null
        val isArchiveHttp = archiveOriginPath != null &&
            parseHttpSourceSpecFromInput(archiveOriginPath) != null
        onBrowserLocationChanged(
            BrowserLaunchState(
                locationId = selectedLocationId,
                directoryPath = logicalArchivePath ?: directory.absolutePath,
                smbSourceNodeId = if (isArchiveSmb) {
                    mounted?.smbSourceNodeId ?: initialSmbSourceNodeId
                } else {
                    null
                },
                httpSourceNodeId = if (isArchiveHttp) {
                    mounted?.httpSourceNodeId ?: initialHttpSourceNodeId
                } else {
                    null
                },
                httpRootPath = if (isArchiveHttp) {
                    mounted?.httpRootPath ?: initialHttpRootPath
                } else {
                    null
                }
            )
        )
    }

    fun navigateTo(directory: File) {
        launchAutoScrollTargetKey = null
        val root = selectedLocation?.directory
        val previousDepth = relativeDepth(currentDirectory, root)
        val nextDepth = relativeDepth(directory, root)
        browserNavDirection = if (nextDepth >= previousDepth) {
            BrowserPageNavDirection.Forward
        } else {
            BrowserPageNavDirection.Backward
        }
        currentDirectory = directory
        loadDirectoryAsync(directory)
        emitBrowserLocationChange(directory)
    }

    fun openArchive(item: FileItem) {
        val archiveFile = item.file
        val activeDirectory = currentDirectory
        if (!item.isArchive || activeDirectory == null) {
            return
        }
        val parentMountInfo = findArchiveMount(activeDirectory.absolutePath)?.second
        directoryLoadJob?.cancel()
        isLoadingDirectory = true
        directoryLoadJob = coroutineScope.launch {
            try {
                val mountDirectory = withContext(Dispatchers.IO) {
                    ensureArchiveMounted(context, archiveFile)
                }
                archiveFile.parentFile?.absolutePath?.let { parentPath ->
                    val mountRootKey = normalizePathForArchiveMountLookup(mountDirectory.absolutePath)
                    val existing = archiveMountRoots[mountRootKey]
                    archiveMountRoots[mountRootKey] = ArchiveMountInfo(
                        archivePath = archiveFile.absolutePath,
                        parentPath = parentPath,
                        returnTargetPath = existing?.returnTargetPath ?: parentPath,
                        logicalArchivePath = existing?.logicalArchivePath ?: archiveFile.absolutePath,
                        smbSourceNodeId = existing?.smbSourceNodeId
                            ?: parentMountInfo?.smbSourceNodeId
                            ?: initialSmbSourceNodeId,
                        httpSourceNodeId = existing?.httpSourceNodeId
                            ?: parentMountInfo?.httpSourceNodeId
                            ?: initialHttpSourceNodeId,
                        httpRootPath = existing?.httpRootPath
                            ?: parentMountInfo?.httpRootPath
                            ?: initialHttpRootPath
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
        val directory = currentDirectory ?: return openBrowserHome()

        findArchiveMount(directory.absolutePath)?.let { (mountRoot, mountInfo) ->
            val normalizedDirectoryPath = normalizePathForArchiveMountLookup(directory.absolutePath)
            if (normalizedDirectoryPath != mountRoot) {
                val archiveParent = directory.parentFile
                if (archiveParent != null && archiveParent.exists() && archiveParent.isDirectory) {
                    browserNavDirection = BrowserPageNavDirection.Backward
                    navigateTo(archiveParent)
                    return
                }
            }
            val returnTarget = mountInfo.returnTargetPath ?: mountInfo.parentPath
            val returnModel = resolveBrowserLocationModel(
                initialLocationId = null,
                initialDirectoryPath = returnTarget,
                initialSmbSourceNodeId = null,
                initialHttpSourceNodeId = null,
                initialHttpRootPath = null
            )
            when (returnModel) {
                is BrowserLocationModel.Local -> {
                    val parentDirectory = returnTarget
                        ?.let(::File)
                        ?.takeIf { it.exists() && it.isDirectory }
                    if (parentDirectory != null) {
                        browserNavDirection = BrowserPageNavDirection.Backward
                        navigateTo(parentDirectory)
                    } else {
                        openBrowserHome()
                    }
                }
                is BrowserLocationModel.Smb -> {
                    onBrowserLocationChanged(
                        BrowserLaunchState(
                            directoryPath = returnTarget,
                            smbSourceNodeId = mountInfo.smbSourceNodeId ?: initialSmbSourceNodeId
                        )
                    )
                }
                is BrowserLocationModel.Http -> {
                    onBrowserLocationChanged(
                        BrowserLaunchState(
                            directoryPath = returnTarget,
                            httpSourceNodeId = mountInfo.httpSourceNodeId ?: initialHttpSourceNodeId,
                            httpRootPath = mountInfo.httpRootPath ?: initialHttpRootPath
                        )
                    )
                }
                else -> openBrowserHome()
            }
            return
        }

        val location = selectedLocation ?: return openBrowserHome()
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
        if (browserSelectionController.isSelectionMode) {
            browserSelectionController.exitSelectionMode()
            return
        }
        if (currentDirectory != null) {
            navigateUpWithinLocation()
        } else {
            onExitBrowser?.invoke()
        }
    }

    fun showSelectionInfoDialog() {
        val selectedItems = fileList.filter { item ->
            browserSelectionController.selectedKeys.contains(item.file.absolutePath)
        }
        if (selectedItems.isEmpty()) return
        val infoEntries = selectedItems.map { item ->
            BrowserInfoEntry(
                name = item.name,
                isDirectory = item.isDirectory,
                sizeBytes = if (item.isDirectory) null else item.size
            )
        }
        val pathLabel = currentDirectory?.absolutePath
            ?: selectedLocation?.directory?.absolutePath
            ?: "/"
        val storageLabel = selectedLocation?.let { location ->
            "${location.typeLabel} (${location.name})"
        } ?: "Unknown"
        browserInfoFields = buildBrowserInfoFields(
            entries = infoEntries,
            path = pathLabel,
            storageOrHostLabel = "Storage",
            storageOrHost = storageLabel
        )
        showBrowserInfoDialog = true
    }

    fun selectedRegularFileItems(): List<FileItem> {
        return fileList.filter { item ->
            browserSelectionController.selectedKeys.contains(item.file.absolutePath) &&
                !item.isDirectory &&
                item.file.exists() &&
                item.file.isFile
        }
    }

    fun selectedAnyItems(): List<FileItem> {
        return fileList.filter { item ->
            browserSelectionController.selectedKeys.contains(item.file.absolutePath)
        }
    }

    fun openFileItem(item: FileItem) {
        if (item.isArchive) {
            openArchive(item)
            return
        }
        if (item.isDirectory) {
            navigateTo(item.file)
            return
        }
        when (browserPreviewKindForName(item.name)) {
            FilePreviewKind.Text -> {
                val textPreviewContent = readTextPreviewContent(item.file)
                if (textPreviewContent != null) {
                    textPreviewDialogState = item.name to textPreviewContent
                } else {
                    Toast.makeText(context, "Unable to preview text file", Toast.LENGTH_SHORT).show()
                }
                return
            }
            FilePreviewKind.Image -> {
                imagePreviewDialogState = item.name to item.file
                return
            }
            null -> Unit
        }
        val mounted = findArchiveMount(item.file.absolutePath)
        val sourceIdOverride = if (mounted != null) {
            val mountRoot = mounted.first
            val archivePath = mounted.second.logicalArchivePath ?: mounted.second.archivePath
            val relativePath = resolveArchiveRelativePath(item.file.absolutePath, mountRoot)
            if (relativePath.isNullOrBlank()) {
                null
            } else {
                buildArchiveSourceId(archivePath, relativePath)
            }
        } else {
            null
        }
        onFileSelected(item.file, sourceIdOverride)
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
        val targets = pendingExportFiles
        pendingExportFiles = emptyList()
        if (targets.isEmpty()) return@rememberLauncherForActivityResult
        if (treeUri == null) {
            Toast.makeText(context, "Save cancelled", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        coroutineScope.launch {
            val result = exportFilesToTree(
                context = context,
                treeUri = treeUri,
                exportItems = targets,
                onNameConflict = { conflict ->
                    requestExportConflictDecision(conflict)
                }
            )
            Toast.makeText(
                context,
                when {
                    result.cancelled -> "Save cancelled"
                    else -> {
                        "Saved ${result.exportedCount} file(s)" +
                            buildString {
                                if (result.skippedCount > 0) {
                                    append(" (${result.skippedCount} skipped)")
                                }
                                if (result.failedCount > 0) {
                                    append(" (${result.failedCount} failed)")
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

    LaunchedEffect(
        storageLocations,
        initialLocationId,
        initialDirectoryPath,
        initialSmbSourceNodeId,
        initialHttpSourceNodeId,
        initialHttpRootPath
    ) {
        val initialLocation = initialLocationId?.let { id ->
            storageLocations.firstOrNull { it.id == id }
        }
        val navigationKey = buildString {
            append(initialLocationId.orEmpty())
            append('|')
            append(initialDirectoryPath.orEmpty())
            append('|')
            append(initialSmbSourceNodeId ?: -1L)
            append('|')
            append(initialHttpSourceNodeId ?: -1L)
            append('|')
            append(initialHttpRootPath.orEmpty())
        }
        if (lastAppliedInitialNavigationKey == navigationKey) return@LaunchedEffect
        lastAppliedInitialNavigationKey = navigationKey

        val rawInitialDirectory = initialDirectoryPath?.trim().takeUnless { it.isNullOrBlank() }
        val resolvedArchive = rawInitialDirectory?.let { path ->
            withContext(Dispatchers.IO) { resolveArchiveLogicalDirectory(context, path) }
        }
        val restoredDirectory = when {
            resolvedArchive != null -> {
                val archiveLocation = resolvedArchive.archivePath
                val archiveIsSmb = parseSmbSourceSpecFromInput(archiveLocation) != null
                val archiveIsHttp = parseHttpSourceSpecFromInput(archiveLocation) != null
                archiveMountRoots[normalizePathForArchiveMountLookup(resolvedArchive.mountDirectory.absolutePath)] =
                    ArchiveMountInfo(
                        archivePath = archiveLocation,
                        parentPath = resolvedArchive.parentPath,
                        returnTargetPath = resolvedArchive.parentPath,
                        logicalArchivePath = archiveLocation,
                        smbSourceNodeId = if (archiveIsSmb) initialSmbSourceNodeId else null,
                        httpSourceNodeId = if (archiveIsHttp) initialHttpSourceNodeId else null,
                        httpRootPath = if (archiveIsHttp) initialHttpRootPath else null
                    )
                resolvedArchive.targetDirectory
            }
            rawInitialDirectory != null -> File(rawInitialDirectory).takeIf { it.exists() && it.isDirectory }
            else -> initialLocation?.directory
        } ?: initialLocation?.directory ?: run {
            openBrowserHome()
            return@LaunchedEffect
        }

        val resolvedLocationId = initialLocation?.id
            ?: storageLocations.firstOrNull { isWithinRoot(restoredDirectory, it.directory) }?.id

        browserNavDirection = BrowserPageNavDirection.Forward
        selectedLocationId = resolvedLocationId
        currentDirectory = restoredDirectory
        loadDirectoryAsync(restoredDirectory)
        val restoredMountInfo = findArchiveMount(restoredDirectory.absolutePath)?.second
        val restoredLogicalArchivePath = resolveLogicalArchivePath(restoredDirectory)
        val restoredArchiveOriginPath = restoredLogicalArchivePath?.let { path -> parseArchiveLogicalPath(path)?.first }
        val restoredIsArchiveSmb = restoredArchiveOriginPath != null &&
            parseSmbSourceSpecFromInput(restoredArchiveOriginPath) != null
        val restoredIsArchiveHttp = restoredArchiveOriginPath != null &&
            parseHttpSourceSpecFromInput(restoredArchiveOriginPath) != null
        onBrowserLocationChanged(
            BrowserLaunchState(
                locationId = resolvedLocationId,
                directoryPath = restoredLogicalArchivePath ?: restoredDirectory.absolutePath,
                smbSourceNodeId = if (restoredIsArchiveSmb) {
                    restoredMountInfo?.smbSourceNodeId ?: initialSmbSourceNodeId
                } else {
                    null
                },
                httpSourceNodeId = if (restoredIsArchiveHttp) {
                    restoredMountInfo?.httpSourceNodeId ?: initialHttpSourceNodeId
                } else {
                    null
                },
                httpRootPath = if (restoredIsArchiveHttp) {
                    restoredMountInfo?.httpRootPath ?: initialHttpRootPath
                } else {
                    null
                }
            )
        )

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

    LaunchedEffect(selectedLocationId, currentDirectory?.absolutePath) {
        browserSelectionController.exitSelectionMode()
        showBrowserInfoDialog = false
        pendingExportFiles = emptyList()
        pendingDeleteFilePaths = emptyList()
    }

    BackHandler(
        enabled = backHandlingEnabled && (currentDirectory != null || onExitBrowser != null),
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

    val logicalArchivePath = resolveLogicalArchivePath(currentDirectory)
    val archiveToolbarContext = remember(logicalArchivePath) {
        logicalArchivePath?.let(::buildArchiveToolbarContext)
    }
    val showLocalStorageSelector = archiveToolbarContext?.isRemote != true
    val subtitleIcon = archiveToolbarContext?.sourceIcon
        ?: selectedLocation?.let { iconForStorageKind(it.kind, context) }
        ?: Icons.Default.Home
    val subtitleIconPainterResId = if (archiveToolbarContext != null) R.drawable.ic_folder_zip else null
    val subtitle = archiveToolbarContext?.subtitle ?: if (selectedLocation == null && currentDirectory == null) {
        "Storage locations"
    } else {
        currentDirectory?.absolutePath
            ?: selectedLocation?.name
            ?: "Storage locations"
    }
    val filteredFileList by remember(browserSearchController.debouncedQuery) {
        derivedStateOf {
            if (browserSearchController.debouncedQuery.isBlank()) {
                fileList.toList()
            } else {
                fileList.filter { item ->
                    matchesBrowserSearchQuery(item.name, browserSearchController.debouncedQuery)
                }
            }
        }
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
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(enabled = showLocalStorageSelector) {
                                    selectorExpanded = true
                                }
                                .padding(horizontal = 2.dp)
                        ) {
                            Box {
                                BrowserToolbarSelectorLabel(
                                    expanded = selectorExpanded,
                                    onClick = {
                                        if (showLocalStorageSelector) {
                                            selectorExpanded = true
                                        }
                                    },
                                    modifier = Modifier.padding(start = 6.dp),
                                    enabled = showLocalStorageSelector,
                                    focusRequester = selectorButtonFocusRequester
                                )
                                DropdownMenu(
                                    expanded = selectorExpanded,
                                    onDismissRequest = { selectorExpanded = false }
                                ) {
                                    if (showLocalStorageSelector) {
                                        Text(
                                            text = "Storage locations",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
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
                                    } else {
                                        val contextLabel = archiveToolbarContext
                                        Text(
                                            text = "Archive source",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(contextLabel?.sourceTypeLabel ?: "Archive")
                                                    Text(
                                                        contextLabel?.sourceLabel ?: "Remote source",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = contextLabel?.sourceIcon ?: Icons.Default.Folder,
                                                    contentDescription = null
                                                )
                                            },
                                            enabled = false,
                                            onClick = {}
                                        )
                                    }
                                }
                            }
                            BrowserToolbarPathRow(
                                icon = subtitleIcon,
                                subtitle = subtitle,
                                iconPainterResId = subtitleIconPainterResId,
                                contentStartPadding = 12.dp
                            )
                        }
                        if (selectedLocationId != null) {
                            BrowserSelectionToolbarControls(
                                visible = browserSelectionController.isSelectionMode,
                                canSelectAny = filteredFileList.isNotEmpty(),
                                onSelectAll = {
                                    browserSelectionController.selectAll(
                                        filteredFileList.map { item -> item.file.absolutePath }
                                    )
                                },
                                onDeselectAll = { browserSelectionController.deselectAll() },
                                actionItems = listOf(
                                    BrowserSelectionActionItem(
                                        label = "Play",
                                        icon = Icons.Default.PlayArrow,
                                        enabled = selectedRegularFileItems().size == 1,
                                        onClick = {
                                            val selectedItem = selectedRegularFileItems().singleOrNull()
                                            if (selectedItem != null) {
                                                browserSelectionController.exitSelectionMode()
                                                openFileItem(selectedItem)
                                            }
                                        }
                                    ),
                                    BrowserSelectionActionItem(
                                        label = if (selectedRegularFileItems().size == 1) {
                                            "Save file"
                                        } else {
                                            "Save files"
                                        },
                                        icon = Icons.Default.Save,
                                        enabled = selectedRegularFileItems().isNotEmpty(),
                                        onClick = {
                                            val exportItems = selectedRegularFileItems().map { item ->
                                                ExportFileItem(
                                                    sourceFile = item.file,
                                                    displayNameOverride = item.file.name
                                                )
                                            }
                                            if (exportItems.isNotEmpty()) {
                                                pendingExportFiles = exportItems
                                                exportDirectoryLauncher.launch(null)
                                            }
                                        }
                                    ),
                                    BrowserSelectionActionItem(
                                        label = selectedAnyItems().singleOrNull()?.let { item ->
                                            if (item.isDirectory) "Pin folder to home" else "Pin file to home"
                                        } ?: "Pin to home",
                                        icon = Icons.Default.Home,
                                        enabled = selectedAnyItems().size == 1,
                                        onClick = {
                                            val selectedItem = selectedAnyItems().singleOrNull() ?: return@BrowserSelectionActionItem
                                            val recentEntry = RecentPathEntry(
                                                path = selectedItem.file.absolutePath,
                                                locationId = selectedLocationId,
                                                title = if (selectedItem.isDirectory) selectedItem.name else null
                                            )
                                            val isFolder = selectedItem.isDirectory
                                            val preview = previewPinnedHomeEntryInsertion(
                                                current = pinnedHomeEntries,
                                                candidate = HomePinnedEntry(
                                                    path = recentEntry.path,
                                                    isFolder = isFolder,
                                                    locationId = recentEntry.locationId,
                                                    title = recentEntry.title
                                                ),
                                                maxItems = PINNED_HOME_ENTRIES_LIMIT
                                            )
                                            if (preview.requiresConfirmation) {
                                                pendingPinEvictionCandidate = preview.evictionCandidate
                                                pendingPinConfirmation = recentEntry to isFolder
                                            } else {
                                                onPinHomeEntry(recentEntry, isFolder)
                                                Toast.makeText(context, if (isFolder) "Pinned folder to home" else "Pinned file to home", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ),
                                    BrowserSelectionActionItem(
                                        label = "Delete",
                                        icon = Icons.Default.Delete,
                                        enabled = selectedRegularFileItems().isNotEmpty(),
                                        onClick = {
                                            pendingDeleteFilePaths = selectedRegularFileItems()
                                                .map { it.file.absolutePath }
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
                }
                BrowserSearchToolbarRow(
                    visible = currentDirectory != null && browserSearchController.isVisible,
                    queryInput = browserSearchController.input,
                    onQueryInputChanged = browserSearchController::onInputChange,
                    onClose = browserSearchController::hide
                )
                HorizontalDivider()
            }
        }
    ) { paddingValues ->
        LaunchedEffect(selectedLocationId, currentDirectory?.absolutePath) {
            if (currentDirectory == null) {
                browserSearchController.hide()
            }
        }
        fun triggerPullRefresh() {
            if (isPullRefreshing) return
            coroutineScope.launch {
                isPullRefreshing = true
                if (currentDirectory == null) {
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
        val directoryScrollbarAlpha = rememberDialogLazyListScrollbarAlpha(
            enabled = browserContentState.pane == BrowserPane.DirectoryEntries,
            listState = directoryListState,
            flashKey = "${browserContentState.currentDirectoryPath}|${filteredFileList.size}|${browserSearchController.debouncedQuery}",
            label = "fileBrowserDirectoryScrollbarAlpha"
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
                    val loadingTransition =
                        initialState.pane == BrowserPane.LoadingDirectory ||
                            targetState.pane == BrowserPane.LoadingDirectory
                    browserContentTransform(
                        navDirection = browserNavDirection,
                        loadingTransition = loadingTransition,
                        loadingPageEnabled = LOCAL_BROWSER_SHOW_INTERMEDIARY_LOADING_PAGE
                    )
                },
                label = "browserContentTransition",
                modifier = Modifier.fillMaxSize()
            ) { state ->
                if (state.pane == BrowserPane.StorageLocations) {
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
                } else if (state.pane == BrowserPane.LoadingDirectory) {
                    BrowserLoadingCard(
                        icon = Icons.Default.Folder,
                        title = "Loading directory...",
                        subtitle = currentDirectory?.absolutePath ?: "Fetching folder entries",
                        logLines = loadingLogLines,
                        waitingLine = "[00] Waiting for filesystem response..."
                    )
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
                        if (filteredFileList.isEmpty() && browserSearchController.debouncedQuery.isNotBlank()) {
                            item(key = "search-empty") {
                                BrowserSearchNoResultsCard(query = browserSearchController.debouncedQuery)
                            }
                        } else {
                            itemsIndexed(
                                items = filteredFileList,
                                key = { _, item -> item.file.absolutePath }
                            ) { index, item ->
                                val entryKey = item.file.absolutePath
                                val isSelected = browserSelectionController.selectedKeys.contains(entryKey)
                                val hasSelectedAbove = if (index > 0) {
                                    val aboveKey = filteredFileList[index - 1].file.absolutePath
                                    browserSelectionController.selectedKeys.contains(aboveKey)
                                } else {
                                    false
                                }
                                val hasSelectedBelow = if (index < filteredFileList.lastIndex) {
                                    val belowKey = filteredFileList[index + 1].file.absolutePath
                                    browserSelectionController.selectedKeys.contains(belowKey)
                                } else {
                                    false
                                }
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
                                        isSelected = isSelected,
                                        hasSelectedAbove = hasSelectedAbove,
                                        hasSelectedBelow = hasSelectedBelow,
                                        showFileIconChipBackground = showFileIconChipBackground,
                                        decoderExtensionArtworkHints = decoderExtensionArtworkHints,
                                        rightFocusRequester = selectorButtonFocusRequester,
                                        rowFocusRequester = rowFocusRequester,
                                        onFocused = { browserFocusedEntryKey = entryKey },
                                        onLongClick = {
                                            if (browserSelectionController.isSelectionMode) {
                                                val didSelectRange =
                                                    browserSelectionController.selectedKeys.size == 1 &&
                                                        browserSelectionController.selectRangeTo(
                                                            key = entryKey,
                                                            orderedKeys = filteredFileList.map { it.file.absolutePath }
                                                        )
                                                if (!didSelectRange) {
                                                    browserSelectionController.toggleSelection(entryKey)
                                                }
                                            } else {
                                                browserSelectionController.enterSelectionWith(entryKey)
                                            }
                                        },
                                        onClick = {
                                            if (browserSelectionController.isSelectionMode) {
                                                browserSelectionController.toggleSelection(entryKey)
                                                return@FileItemRow
                                            }
                                            openFileItem(item)
                                        }
                                    )
                                }
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
            if (browserContentState.pane == BrowserPane.DirectoryEntries) {
                BrowserLazyListScrollbar(
                    listState = directoryListState,
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

    if (showBrowserInfoDialog) {
        BrowserInfoDialog(
            title = "Info",
            fields = browserInfoFields,
            onDismiss = { showBrowserInfoDialog = false }
        )
    }
    textPreviewDialogState?.let { (fileName, textContent) ->
        BrowserTextPreviewDialog(
            fileName = fileName,
            textContent = textContent,
            onDismiss = { textPreviewDialogState = null }
        )
    }
    imagePreviewDialogState?.let { (fileName, imageFile) ->
        BrowserImagePreviewDialog(
            fileName = fileName,
            imageFile = imageFile,
            onDismiss = { imagePreviewDialogState = null }
        )
    }

    exportConflictDialogState?.let { state ->
        BrowserExportConflictDialog(state = state)
    }

    if (pendingDeleteFilePaths.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingDeleteFilePaths = emptyList() },
            title = { Text("Delete files") },
            text = {
                Text(
                    text = "Delete ${pendingDeleteFilePaths.size} selected file(s)?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pathsToDelete = pendingDeleteFilePaths
                        pendingDeleteFilePaths = emptyList()
                        coroutineScope.launch {
                            val deletedCount = withContext(Dispatchers.IO) {
                                pathsToDelete.count { path ->
                                    runCatching {
                                        File(path).takeIf { it.exists() && it.isFile }?.delete() == true
                                    }.getOrDefault(false)
                                }
                            }
                            Toast.makeText(
                                context,
                                "Deleted $deletedCount file(s)" +
                                    if (deletedCount < pathsToDelete.size) {
                                        " (${pathsToDelete.size - deletedCount} failed)"
                                    } else {
                                        ""
                                    },
                                Toast.LENGTH_SHORT
                            ).show()
                            browserSelectionController.exitSelectionMode()
                            currentDirectory?.let { loadDirectoryAsync(it) }
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteFilePaths = emptyList() }) {
                    Text("Cancel")
                }
            }
        )
    }
    pendingPinConfirmation?.let { (entry, isFolder) ->
        val evictionCandidate = pendingPinEvictionCandidate
        AlertDialog(
            onDismissRequest = {
                pendingPinConfirmation = null
                pendingPinEvictionCandidate = null
            },
            title = { Text("Pin limit reached") },
            text = {
                Text(
                    text = buildString {
                        append("You can pin up to $PINNED_HOME_ENTRIES_LIMIT entries. ")
                        if (evictionCandidate != null) {
                            append("The oldest pinned ")
                            append(if (evictionCandidate.isFolder) "folder" else "file")
                            append(" will be removed to make space.")
                        } else {
                            append("The oldest pinned entry will be removed to make space.")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onPinHomeEntry(entry, isFolder)
                        pendingPinConfirmation = null
                        pendingPinEvictionCandidate = null
                        Toast.makeText(context, if (isFolder) "Pinned folder to home" else "Pinned file to home", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingPinConfirmation = null
                        pendingPinEvictionCandidate = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
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

private fun buildArchiveToolbarContext(logicalPath: String): ArchiveToolbarContext? {
    val parsed = parseArchiveLogicalPath(logicalPath) ?: return null
    val archiveLocation = parsed.first
    val inArchivePath = parsed.second
    val archiveSource = parseArchiveSourceId(archiveLocation)
    val archiveContainerLocation = archiveSource?.archivePath ?: archiveLocation
    val archiveContainerDisplay = when {
        parseSmbSourceSpecFromInput(archiveContainerLocation) != null -> {
            val spec = parseSmbSourceSpecFromInput(archiveContainerLocation) ?: return null
            decodePercentEncodedForDisplay(buildSmbDisplayUri(spec)) ?: buildSmbDisplayUri(spec)
        }
        parseHttpSourceSpecFromInput(archiveContainerLocation) != null -> {
            val spec = parseHttpSourceSpecFromInput(archiveContainerLocation) ?: return null
            decodePercentEncodedForDisplay(buildHttpDisplayUri(spec)) ?: buildHttpDisplayUri(spec)
        }
        else -> decodePercentEncodedForDisplay(archiveContainerLocation) ?: archiveContainerLocation
    }
    val archiveEntryDisplay = archiveSource
        ?.entryPath
        ?.split('/')
        ?.filter { it.isNotBlank() }
        ?.joinToString("/") { segment -> decodePercentEncodedForDisplay(segment) ?: segment }
        ?.takeIf { it.isNotBlank() }
    val inArchiveDisplay = inArchivePath
        ?.split('/')
        ?.filter { it.isNotBlank() }
        ?.joinToString("/") { segment -> decodePercentEncodedForDisplay(segment) ?: segment }
        ?.takeIf { it.isNotBlank() }
    val subtitle = buildString {
        append(archiveContainerDisplay)
        if (!archiveEntryDisplay.isNullOrBlank()) {
            append('/')
            append(archiveEntryDisplay)
        }
        if (!inArchiveDisplay.isNullOrBlank()) {
            append('/')
            append(inArchiveDisplay)
        }
    }
    return when {
        parseSmbSourceSpecFromInput(archiveContainerLocation) != null -> ArchiveToolbarContext(
            subtitle = subtitle,
            isRemote = true,
            sourceLabel = archiveContainerDisplay,
            sourceTypeLabel = "SMB archive",
            sourceIcon = NetworkIcons.SmbShare
        )
        parseHttpSourceSpecFromInput(archiveContainerLocation) != null -> ArchiveToolbarContext(
            subtitle = subtitle,
            isRemote = true,
            sourceLabel = archiveContainerDisplay,
            sourceTypeLabel = "HTTP archive",
            sourceIcon = NetworkIcons.WorldCode
        )
        else -> ArchiveToolbarContext(
            subtitle = subtitle,
            isRemote = false,
            sourceLabel = archiveContainerDisplay,
            sourceTypeLabel = "Local archive",
            sourceIcon = Icons.Default.Folder
        )
    }
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
    val rootPath = root.absolutePath.trimEnd('/')
    if (rootPath.isEmpty()) {
        // "/" should contain any absolute path.
        return filePath.startsWith("/")
    }
    return filePath == rootPath || filePath.startsWith("$rootPath/")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemRow(
    item: FileItem,
    isPlaying: Boolean,
    isSelected: Boolean = false,
    hasSelectedAbove: Boolean = false,
    hasSelectedBelow: Boolean = false,
    showFileIconChipBackground: Boolean,
    decoderExtensionArtworkHints: Map<String, DecoderArtworkHint> = emptyMap(),
    rightFocusRequester: FocusRequester? = null,
    rowFocusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val selectionShape = RoundedCornerShape(
        topStart = if (hasSelectedAbove) 0.dp else 18.dp,
        topEnd = if (hasSelectedAbove) 0.dp else 18.dp,
        bottomStart = if (hasSelectedBelow) 0.dp else 18.dp,
        bottomEnd = if (hasSelectedBelow) 0.dp else 18.dp
    )
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
            .clip(selectionShape)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                }
            )
            .focusProperties {
                if (rightFocusRequester != null) {
                    right = rightFocusRequester
                }
            }
            .then(if (rowFocusRequester != null) Modifier.focusRequester(rowFocusRequester) else Modifier)
            .onFocusChanged { state -> if (state.isFocused) onFocused?.invoke() }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
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
                    val previewKind = browserPreviewKindForName(item.name)
                    val contentDescription = when {
                        isVideoFile -> "Video file"
                        decoderArtworkHint == DecoderArtworkHint.TrackedFile -> "Tracked file"
                        decoderArtworkHint == DecoderArtworkHint.GameFile -> "Game file"
                        previewKind == FilePreviewKind.Text -> "Text file"
                        previewKind == FilePreviewKind.Image -> "Image file"
                        item.kind == FileItem.Kind.UnsupportedFile -> "File"
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
                    } else if (previewKind == FilePreviewKind.Text) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = contentDescription,
                            tint = iconTint,
                            modifier = Modifier.size(FILE_ICON_GLYPH_SIZE)
                        )
                    } else if (previewKind == FilePreviewKind.Image) {
                        Icon(
                            imageVector = Icons.Default.Photo,
                            contentDescription = contentDescription,
                            tint = iconTint,
                            modifier = Modifier.size(FILE_ICON_GLYPH_SIZE)
                        )
                    } else if (item.kind == FileItem.Kind.UnsupportedFile) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_file_unsupported),
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
    context: Context,
    archivePath: String,
    relativeDirectory: String
): Map<String, Long> {
    val normalizedDirectory = relativeDirectory.replace('\\', '/').trim('/')
    val archiveFile = resolveArchiveLocationToFile(context, archivePath) ?: return emptyMap()
    return try {
        ZipFile(archiveFile).use { zip ->
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

private fun readTextPreviewContent(
    file: File,
    maxBytes: Int = 512 * 1024
): String? {
    if (!file.exists() || !file.isFile) return null
    return runCatching {
        val bytes = file.inputStream().use { input ->
            val buffer = ByteArray(maxBytes + 1)
            var totalRead = 0
            while (totalRead < buffer.size) {
                val read = input.read(buffer, totalRead, buffer.size - totalRead)
                if (read <= 0) break
                totalRead += read
            }
            buffer.copyOf(totalRead)
        }
        val wasTruncated = bytes.size > maxBytes
        val previewBytes = if (wasTruncated) bytes.copyOf(maxBytes) else bytes
        val text = previewBytes.toString(Charsets.UTF_8)
        if (wasTruncated) {
            "$text\n\n[Preview truncated at ${maxBytes / 1024} KB]"
        } else {
            text
        }
    }.getOrNull()
}
