package com.flopster101.siliconplayer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    bottomContentPadding: Dp = 0.dp
) {
    var currentDirectory by remember { mutableStateOf(repository.getRootDirectory()) }
    var fileList by remember { mutableStateOf(repository.getFiles(currentDirectory)) }

    fun navigateTo(directory: File) {
        currentDirectory = directory
        fileList = repository.getFiles(directory)
    }

    fun navigateUp() {
        currentDirectory.parentFile?.let { navigateTo(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentDirectory.name) },
                navigationIcon = {
                    if (currentDirectory != repository.getRootDirectory()) {
                        IconButton(onClick = { navigateUp() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Navigate up"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
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
            .padding(16.dp),
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
