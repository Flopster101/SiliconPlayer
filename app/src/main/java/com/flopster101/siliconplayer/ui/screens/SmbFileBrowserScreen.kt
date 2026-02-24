package com.flopster101.siliconplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.SmbBrowserEntry
import com.flopster101.siliconplayer.SmbSourceSpec
import com.flopster101.siliconplayer.buildSmbEntrySourceSpec
import com.flopster101.siliconplayer.buildSmbRequestUri
import com.flopster101.siliconplayer.inferredPrimaryExtensionForName
import com.flopster101.siliconplayer.joinSmbRelativePath
import com.flopster101.siliconplayer.listSmbDirectoryEntries
import com.flopster101.siliconplayer.listSmbHostShareEntries
import com.flopster101.siliconplayer.normalizeSmbPathForShare
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val SMB_ICON_BOX_SIZE = 38.dp
private val SMB_ICON_GLYPH_SIZE = 26.dp

@Composable
internal fun SmbFileBrowserScreen(
    sourceSpec: SmbSourceSpec,
    bottomContentPadding: Dp,
    backHandlingEnabled: Boolean,
    onExitBrowser: () -> Unit,
    onOpenRemoteSource: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val launchShare = remember(sourceSpec.share) { sourceSpec.share.trim() }
    val canBrowseHostShares = launchShare.isBlank()
    val credentialsSpec = remember(sourceSpec) { sourceSpec.copy(share = "", path = null) }
    val rootPath = remember(sourceSpec.path, launchShare) {
        if (launchShare.isBlank()) "" else normalizeSmbPathForShare(sourceSpec.path).orEmpty()
    }

    var currentShare by remember(sourceSpec) { mutableStateOf(launchShare) }
    var currentSubPath by remember(sourceSpec) { mutableStateOf("") }
    var entries by remember(sourceSpec) { mutableStateOf<List<SmbBrowserEntry>>(emptyList()) }
    var isLoading by remember(sourceSpec) { mutableStateOf(false) }
    var errorMessage by remember(sourceSpec) { mutableStateOf<String?>(null) }
    var listJob by remember(sourceSpec) { mutableStateOf<Job?>(null) }

    fun effectivePath(): String? {
        val sub = normalizeSmbPathForShare(currentSubPath).orEmpty()
        return when {
            rootPath.isBlank() && sub.isBlank() -> null
            sub.isBlank() -> rootPath
            rootPath.isBlank() -> sub
            else -> "$rootPath/$sub"
        }
    }

    fun isSharePickerMode(): Boolean = currentShare.isBlank()

    fun loadCurrentDirectory() {
        listJob?.cancel()
        isLoading = true
        errorMessage = null
        val share = currentShare
        val pathInsideShare = effectivePath()
        listJob = coroutineScope.launch {
            val result = if (share.isBlank()) {
                listSmbHostShareEntries(credentialsSpec)
            } else {
                listSmbDirectoryEntries(
                    credentialsSpec.copy(share = share),
                    pathInsideShare
                )
            }
            result.onSuccess { resolved ->
                entries = resolved
                errorMessage = null
            }.onFailure { throwable ->
                entries = emptyList()
                errorMessage = throwable.message ?: if (share.isBlank()) {
                    "Unable to list SMB shares"
                } else {
                    "Unable to list SMB directory"
                }
            }
            isLoading = false
            listJob = null
        }
    }

    fun navigateUpWithinBrowser(): Boolean {
        if (currentSubPath.isNotBlank()) {
            currentSubPath = currentSubPath.substringBeforeLast('/', missingDelimiterValue = "")
            loadCurrentDirectory()
            return true
        }
        if (canBrowseHostShares && currentShare.isNotBlank()) {
            currentShare = ""
            currentSubPath = ""
            loadCurrentDirectory()
            return true
        }
        return false
    }

    DisposableEffect(Unit) {
        onDispose {
            listJob?.cancel()
        }
    }

    LaunchedEffect(sourceSpec) {
        currentShare = launchShare
        currentSubPath = ""
        loadCurrentDirectory()
    }

    BackHandler(enabled = backHandlingEnabled) {
        if (!navigateUpWithinBrowser()) {
            onExitBrowser()
        }
    }

    val canNavigateUp = currentSubPath.isNotBlank() || (canBrowseHostShares && currentShare.isNotBlank())
    val sharePickerMode = isSharePickerMode()
    val subtitle = buildString {
        append("smb://")
        append(credentialsSpec.host)
        if (currentShare.isNotBlank()) {
            append('/')
            append(currentShare)
            effectivePath()?.let { path ->
                if (path.isNotBlank()) {
                    append('/')
                    append(path)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (!navigateUpWithinBrowser()) {
                                onExitBrowser()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (canNavigateUp) {
                                    "Navigate up"
                                } else {
                                    "Back to home"
                                }
                            )
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
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = bottomContentPadding)
        ) {
            if (canNavigateUp) {
                item("parent") {
                    SmbParentDirectoryRow(
                        onClick = {
                            navigateUpWithinBrowser()
                        }
                    )
                }
            }

            when {
                isLoading && entries.isEmpty() -> {
                    item("loading") {
                        SmbInfoCard(
                            title = if (sharePickerMode) {
                                "Loading SMB shares..."
                            } else {
                                "Loading SMB directory..."
                            },
                            subtitle = if (sharePickerMode) {
                                "Fetching shares from host"
                            } else {
                                "Fetching folder entries"
                            }
                        )
                    }
                }

                !errorMessage.isNullOrBlank() -> {
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
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (sharePickerMode) {
                                        "Unable to list SMB shares"
                                    } else {
                                        "Unable to open SMB directory"
                                    },
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = errorMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = { loadCurrentDirectory() }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }

                entries.isEmpty() -> {
                    item("empty") {
                        SmbInfoCard(
                            title = if (sharePickerMode) {
                                "No SMB shares found"
                            } else {
                                "This directory is empty"
                            },
                            subtitle = if (sharePickerMode) {
                                "No accessible disk shares on this host"
                            } else {
                                "No files or folders found"
                            }
                        )
                    }
                }

                else -> {
                    items(entries, key = { entry -> "${entry.isDirectory}:${entry.name}" }) { entry ->
                        SmbEntryRow(
                            entry = entry,
                            showAsShare = sharePickerMode,
                            onClick = {
                                if (sharePickerMode) {
                                    currentShare = entry.name
                                    currentSubPath = ""
                                    loadCurrentDirectory()
                                } else if (entry.isDirectory) {
                                    currentSubPath = joinSmbRelativePath(currentSubPath, entry.name)
                                    loadCurrentDirectory()
                                } else {
                                    val targetPath = joinSmbRelativePath(effectivePath().orEmpty(), entry.name)
                                    val targetSpec = buildSmbEntrySourceSpec(
                                        credentialsSpec.copy(share = currentShare),
                                        targetPath
                                    )
                                    onOpenRemoteSource(buildSmbRequestUri(targetSpec))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

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

@Composable
private fun SmbEntryRow(
    entry: SmbBrowserEntry,
    showAsShare: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                .size(SMB_ICON_BOX_SIZE)
                .background(
                    color = chipContainerColor,
                    shape = RoundedCornerShape(11.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.AudioFile,
                contentDescription = null,
                tint = chipContentColor,
                modifier = Modifier.size(SMB_ICON_GLYPH_SIZE)
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
                text = if (showAsShare) {
                    "Share"
                } else if (entry.isDirectory) {
                    "Folder"
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
