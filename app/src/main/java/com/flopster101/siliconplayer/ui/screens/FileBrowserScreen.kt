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
        Text(text = item.name, style = MaterialTheme.typography.bodyLarge)
    }
}
