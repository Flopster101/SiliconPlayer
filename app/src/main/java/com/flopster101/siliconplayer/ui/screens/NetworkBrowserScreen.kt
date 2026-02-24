package com.flopster101.siliconplayer.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.buildRecentTrackDisplay
import com.flopster101.siliconplayer.formatNetworkFolderSummary
import com.flopster101.siliconplayer.NetworkNode
import com.flopster101.siliconplayer.NetworkNodeType
import com.flopster101.siliconplayer.nextNetworkNodeId
import com.flopster101.siliconplayer.inferredPrimaryExtensionForName
import java.util.Locale

private val NETWORK_ICON_BOX_SIZE = 38.dp
private val NETWORK_ICON_GLYPH_SIZE = 26.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NetworkBrowserScreen(
    bottomContentPadding: Dp,
    backHandlingEnabled: Boolean,
    nodes: List<NetworkNode>,
    onExitNetwork: () -> Unit,
    onNodesChanged: (List<NetworkNode>) -> Unit,
    onResolveRemoteSourceMetadata: (String) -> Unit,
    onOpenRemoteSource: (String) -> Unit
) {
    var currentFolderId by remember { mutableStateOf<Long?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var newSourceName by remember { mutableStateOf("") }
    var newSourcePath by remember { mutableStateOf("") }

    val nodesById = remember(nodes, currentFolderId) { nodes.associateBy { it.id } }
    val currentEntries = remember(nodes, currentFolderId) {
        nodes
            .asSequence()
            .filter { it.parentId == currentFolderId }
            .sortedWith(
                compareBy<NetworkNode> { it.type != NetworkNodeType.Folder }
                    .thenBy { it.title.lowercase() }
            )
            .toList()
    }
    val folderSummariesById = remember(nodes, currentEntries) {
        currentEntries
            .asSequence()
            .filter { it.type == NetworkNodeType.Folder }
            .associate { entry ->
                val folderCount = nodes.count {
                    it.parentId == entry.id && it.type == NetworkNodeType.Folder
                }
                val sourceCount = nodes.count {
                    it.parentId == entry.id && it.type == NetworkNodeType.RemoteSource
                }
                entry.id to formatNetworkFolderSummary(folderCount, sourceCount)
            }
    }

    val breadcrumbLabels = remember(nodes, currentFolderId) {
        val labels = mutableListOf<String>()
        var cursor = currentFolderId
        while (cursor != null) {
            val folder = nodesById[cursor] ?: break
            labels += folder.title
            cursor = folder.parentId
        }
        labels.asReversed()
    }

    fun navigateUpOneFolder() {
        currentFolderId = currentFolderId?.let { nodesById[it]?.parentId }
    }

    BackHandler(enabled = backHandlingEnabled) {
        if (currentFolderId != null) {
            navigateUpOneFolder()
        } else {
            onExitNetwork()
        }
    }

    fun addFolder(name: String) {
        val normalized = name.trim()
        if (normalized.isEmpty()) return
        val updated = nodes + NetworkNode(
            id = nextNetworkNodeId(nodes),
            parentId = currentFolderId,
            type = NetworkNodeType.Folder,
            title = normalized
        )
        onNodesChanged(updated)
    }

    fun addRemoteSource(name: String, source: String) {
        val normalizedSource = source.trim()
        if (normalizedSource.isEmpty()) return
        val title = name.trim().ifBlank { normalizedSource }
        val updated = nodes + NetworkNode(
            id = nextNetworkNodeId(nodes),
            parentId = currentFolderId,
            type = NetworkNodeType.RemoteSource,
            title = title,
            source = normalizedSource
        )
        onNodesChanged(updated)
        onResolveRemoteSourceMetadata(normalizedSource)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .padding(bottom = bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentFolderId != null) {
                IconButton(onClick = { navigateUpOneFolder() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go to parent folder"
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Network",
                    style = MaterialTheme.typography.headlineSmall
                )
                if (breadcrumbLabels.isNotEmpty()) {
                    Text(
                        text = breadcrumbLabels.joinToString(" / "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box {
                IconButton(onClick = { showAddMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add folder or source"
                    )
                }
                DropdownMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { showAddMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Folder") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showAddMenu = false
                            newFolderName = ""
                            showCreateFolderDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remote source") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showAddMenu = false
                            newSourceName = ""
                            newSourcePath = ""
                            showAddSourceDialog = true
                        }
                    )
                }
            }
        }
        Text(
            text = "Browse network shares and remote sources.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        if (currentEntries.isEmpty()) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (currentFolderId == null) {
                            "No network shares yet"
                        } else {
                            "This folder is empty"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Use + to add folders and remote sources.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            currentEntries.forEach { entry ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (entry.type == NetworkNodeType.Folder) {
                            currentFolderId = entry.id
                        } else {
                            entry.source?.let(onOpenRemoteSource)
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isFolder = entry.type == NetworkNodeType.Folder
                        val chipShape = RoundedCornerShape(11.dp)
                        val chipContainerColor = if (isFolder) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                        val chipContentColor = if (isFolder) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                        Box(
                            modifier = Modifier
                                .size(NETWORK_ICON_BOX_SIZE)
                                .background(
                                    color = chipContainerColor,
                                    shape = chipShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isFolder) {
                                    Icons.Default.Folder
                                } else {
                                    Icons.Default.AudioFile
                                },
                                contentDescription = null,
                                tint = chipContentColor,
                                modifier = Modifier.size(NETWORK_ICON_GLYPH_SIZE)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val isRemoteSource = entry.type == NetworkNodeType.RemoteSource
                            val sourceLabel = entry.source.orEmpty()
                            val fallbackTitle = entry.title.ifBlank { sourceLabel }
                            val remoteDisplay = buildRecentTrackDisplay(
                                title = entry.metadataTitle.orEmpty(),
                                artist = entry.metadataArtist.orEmpty(),
                                fallback = fallbackTitle
                            )
                            val displayTitle = if (isRemoteSource) {
                                remoteDisplay.primaryText
                            } else {
                                entry.title
                            }.orEmpty()
                            val subtitle = if (!isRemoteSource) {
                                folderSummariesById[entry.id].orEmpty()
                            } else {
                                buildString {
                                    append(inferNetworkSourceFormatLabel(sourceLabel))
                                    if (sourceLabel.isNotBlank()) {
                                        append(" • ")
                                        append(sourceLabel)
                                    }
                                }
                            }
                            val shouldMarqueePrimary = isRemoteSource && displayTitle.length > 28
                            if (shouldMarqueePrimary) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clipToBounds()
                                ) {
                                    Text(
                                        text = displayTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip,
                                        modifier = Modifier.basicMarquee(
                                            iterations = Int.MAX_VALUE,
                                            initialDelayMillis = 900,
                                            spacing = MarqueeSpacing(24.dp)
                                        )
                                    )
                                }
                            } else {
                                Text(
                                    text = displayTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (subtitle.isNotBlank()) {
                                if (isRemoteSource) {
                                    val shouldMarqueeSubtitle = subtitle.length > 44
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Public,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clipToBounds()
                                        ) {
                                            Text(
                                                text = subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = if (shouldMarqueeSubtitle) {
                                                    TextOverflow.Clip
                                                } else {
                                                    TextOverflow.Ellipsis
                                                },
                                                modifier = if (shouldMarqueeSubtitle) {
                                                    Modifier.basicMarquee(
                                                        iterations = Int.MAX_VALUE,
                                                        initialDelayMillis = 900,
                                                        spacing = MarqueeSpacing(24.dp)
                                                    )
                                                } else {
                                                    Modifier
                                                }
                                            )
                                        }
                                    }
                                } else {
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
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    singleLine = true,
                    label = { Text("Folder name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        addFolder(newFolderName)
                        showCreateFolderDialog = false
                    },
                    enabled = newFolderName.trim().isNotEmpty()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddSourceDialog) {
        AlertDialog(
            onDismissRequest = { showAddSourceDialog = false },
            title = { Text("Add remote source") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newSourceName,
                        onValueChange = { newSourceName = it },
                        singleLine = true,
                        label = { Text("Name (optional)") }
                    )
                    OutlinedTextField(
                        value = newSourcePath,
                        onValueChange = { newSourcePath = it },
                        singleLine = true,
                        label = { Text("URL or path") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        addRemoteSource(newSourceName, newSourcePath)
                        showAddSourceDialog = false
                    },
                    enabled = newSourcePath.trim().isNotEmpty()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSourceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun inferNetworkSourceFormatLabel(source: String): String {
    if (source.isBlank()) return "Unknown"
    val leaf = source
        .substringBefore('#')
        .substringBefore('?')
        .substringAfterLast('/')
    val ext = inferredPrimaryExtensionForName(leaf)
    return ext?.uppercase(Locale.ROOT) ?: "Unknown"
}
