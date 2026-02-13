package com.flopster101.siliconplayer.ui.screens

import android.content.Context
import android.content.res.Configuration
import android.os.Environment
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.data.FileItem
import com.flopster101.siliconplayer.data.FileRepository
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    repository: FileRepository,
    onFileSelected: (File) -> Unit,
    bottomContentPadding: Dp = 0.dp,
    onExitBrowser: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val storageLocations = remember(context) { detectStorageLocations(context) }
    var selectedLocationId by remember { mutableStateOf<String?>(null) }
    var currentDirectory by remember { mutableStateOf<File?>(null) }
    var fileList by remember { mutableStateOf(emptyList<FileItem>()) }
    var selectorExpanded by remember { mutableStateOf(false) }

    val selectedLocation = storageLocations.firstOrNull { it.id == selectedLocationId }
    val selectorIcon = selectedLocation?.let { iconForStorageKind(it.kind, context) } ?: Icons.Default.Home
    val selectorLabel = selectedLocation?.typeLabel ?: "Home"

    fun openBrowserHome() {
        selectedLocationId = null
        currentDirectory = null
        fileList = emptyList()
    }

    fun openLocation(location: StorageLocation) {
        selectedLocationId = location.id
        currentDirectory = location.directory
        fileList = repository.getFiles(location.directory)
    }

    fun navigateTo(directory: File) {
        currentDirectory = directory
        fileList = repository.getFiles(directory)
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

    val subtitle = if (selectedLocation == null) {
        "Storage locations"
    } else {
        currentDirectory?.name?.ifBlank { currentDirectory?.absolutePath ?: selectedLocation.name } ?: selectedLocation.name
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Silicon Player",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    if (selectedLocation != null || onExitBrowser != null) {
                        val onClick: () -> Unit = if (selectedLocation != null) {
                            { navigateUpWithinLocation() }
                        } else {
                            { onExitBrowser?.invoke(); Unit }
                        }
                        IconButton(onClick = onClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (selectedLocation != null) "Navigate up" else "Back to home"
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        TextButton(onClick = { selectorExpanded = true }) {
                            Icon(
                                imageVector = selectorIcon,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = selectorLabel,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Open location selector"
                            )
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
    ) { paddingValues ->
        if (selectedLocation == null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
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
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
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
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = bottomContentPadding)
            ) {
                items(fileList) { item ->
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

@Composable
fun FileItemRow(item: FileItem, onClick: () -> Unit) {
    val subtitle = remember(item.file.absolutePath, item.isDirectory, item.file.length(), item.file.lastModified()) {
        if (item.isDirectory) {
            buildFolderSummary(item.file)
        } else {
            formatFileSizeHumanReadable(item.file.length())
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.AudioFile,
            contentDescription = if (item.isDirectory) "Directory" else "Audio file",
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1
            )
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
