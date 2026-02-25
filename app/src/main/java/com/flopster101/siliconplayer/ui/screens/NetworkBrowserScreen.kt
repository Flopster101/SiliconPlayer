package com.flopster101.siliconplayer.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.NetworkNode
import com.flopster101.siliconplayer.NetworkNodeType
import com.flopster101.siliconplayer.NetworkSourceKind
import com.flopster101.siliconplayer.buildRecentTrackDisplay
import com.flopster101.siliconplayer.buildSmbSourceId
import com.flopster101.siliconplayer.buildSmbSourceSpec
import com.flopster101.siliconplayer.formatNetworkFolderSummary
import com.flopster101.siliconplayer.inferredPrimaryExtensionForName
import com.flopster101.siliconplayer.nextNetworkNodeId
import com.flopster101.siliconplayer.resolveNetworkNodeDisplaySource
import com.flopster101.siliconplayer.resolveNetworkNodeOpenInput
import com.flopster101.siliconplayer.resolveNetworkNodeSmbSpec
import com.flopster101.siliconplayer.resolveNetworkNodeSourceId
import java.util.Locale
import kotlinx.coroutines.delay

private val NETWORK_ICON_BOX_SIZE = 38.dp
private val NETWORK_ICON_GLYPH_SIZE = 26.dp
private const val NETWORK_REFRESH_PLACEHOLDER_DELAY_MS = 1200L

private enum class NetworkClipboardMode {
    Copy,
    Move
}

private data class NetworkClipboardState(
    val mode: NetworkClipboardMode,
    val nodeIds: Set<Long>
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NetworkBrowserScreen(
    bottomContentPadding: Dp,
    backHandlingEnabled: Boolean,
    nodes: List<NetworkNode>,
    currentFolderId: Long?,
    onExitNetwork: () -> Unit,
    onCurrentFolderIdChanged: (Long?) -> Unit,
    onNodesChanged: (List<NetworkNode>) -> Unit,
    onResolveRemoteSourceMetadata: (String) -> Unit,
    onOpenRemoteSource: (String) -> Unit,
    onBrowseSmbSource: (String) -> Unit
) {
    var showAddMenu by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var showAddSmbSourceDialog by remember { mutableStateOf(false) }
    var showSelectionActionsMenu by remember { mutableStateOf(false) }
    var showSelectionToggleMenu by remember { mutableStateOf(false) }
    var selectionModeEnabled by remember { mutableStateOf(false) }
    var expandedEntryMenuNodeId by remember { mutableStateOf<Long?>(null) }
    var editingFolderNodeId by remember { mutableStateOf<Long?>(null) }
    var editingSourceNodeId by remember { mutableStateOf<Long?>(null) }
    var editingSmbNodeId by remember { mutableStateOf<Long?>(null) }
    var selectedNodeIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var deleteNodeIdsPendingConfirmation by remember { mutableStateOf<Set<Long>?>(null) }
    var clipboardState by remember { mutableStateOf<NetworkClipboardState?>(null) }
    var blockedOperationMessage by remember { mutableStateOf<String?>(null) }
    var refreshNodeIdsInProgress by remember { mutableStateOf<Set<Long>?>(null) }

    var newFolderName by remember { mutableStateOf("") }
    var newSourceName by remember { mutableStateOf("") }
    var newSourcePath by remember { mutableStateOf("") }
    var newSmbSourceName by remember { mutableStateOf("") }
    var newSmbHost by remember { mutableStateOf("") }
    var newSmbShare by remember { mutableStateOf("") }
    var newSmbPath by remember { mutableStateOf("") }
    var newSmbUsername by remember { mutableStateOf("") }
    var newSmbPassword by remember { mutableStateOf("") }

    val nodesById = remember(nodes, currentFolderId) { nodes.associateBy { it.id } }
    val currentEntries = remember(nodes, currentFolderId) {
        nodes
            .asSequence()
            .filter { it.parentId == currentFolderId }
            .sortedWith(
                compareBy<NetworkNode> { entry ->
                    when {
                        entry.type == NetworkNodeType.Folder -> 0
                        isSmbFolderLikeSource(entry, resolveNetworkNodeSourceId(entry).orEmpty()) -> 1
                        else -> 2
                    }
                }
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

    val isSelectionMode = selectionModeEnabled
    val deleteNodePendingIds = remember(deleteNodeIdsPendingConfirmation) {
        deleteNodeIdsPendingConfirmation.orEmpty()
    }
    val deleteNodePending = remember(deleteNodePendingIds, nodesById) {
        if (deleteNodePendingIds.size == 1) {
            nodesById[deleteNodePendingIds.first()]
        } else {
            null
        }
    }
    val refreshNodePendingIds = remember(refreshNodeIdsInProgress) {
        refreshNodeIdsInProgress.orEmpty()
    }

    fun navigateUpOneFolder() {
        onCurrentFolderIdChanged(currentFolderId?.let { nodesById[it]?.parentId })
    }

    fun beginEntryEdit(entry: NetworkNode) {
        expandedEntryMenuNodeId = null
        when {
            entry.type == NetworkNodeType.Folder -> {
                editingFolderNodeId = entry.id
                newFolderName = entry.title
                showCreateFolderDialog = true
            }

            entry.sourceKind == NetworkSourceKind.Smb -> {
                val smbSpec = resolveNetworkNodeSmbSpec(entry)
                editingSmbNodeId = entry.id
                newSmbSourceName = entry.title
                newSmbHost = smbSpec?.host.orEmpty()
                newSmbShare = smbSpec?.share.orEmpty()
                newSmbPath = smbSpec?.path.orEmpty()
                newSmbUsername = smbSpec?.username.orEmpty()
                newSmbPassword = smbSpec?.password.orEmpty()
                showAddSmbSourceDialog = true
            }

            else -> {
                editingSourceNodeId = entry.id
                newSourceName = entry.title
                newSourcePath = resolveNetworkNodeSourceId(entry).orEmpty()
                showAddSourceDialog = true
            }
        }
    }

    fun beginClipboardMode(mode: NetworkClipboardMode, nodeIds: Set<Long>) {
        if (nodeIds.isEmpty()) return
        expandedEntryMenuNodeId = null
        selectedNodeIds = emptySet()
        selectionModeEnabled = false
        showSelectionActionsMenu = false
        showSelectionToggleMenu = false
        clipboardState = NetworkClipboardState(mode = mode, nodeIds = nodeIds)
    }

    fun beginDeleteConfirmation(entry: NetworkNode) {
        expandedEntryMenuNodeId = null
        showSelectionActionsMenu = false
        deleteNodeIdsPendingConfirmation = setOf(entry.id)
    }

    fun beginDeleteConfirmation(nodeIds: Set<Long>) {
        if (nodeIds.isEmpty()) return
        expandedEntryMenuNodeId = null
        showSelectionActionsMenu = false
        deleteNodeIdsPendingConfirmation = nodeIds
    }

    fun beginRefreshPlaceholder(entry: NetworkNode) {
        expandedEntryMenuNodeId = null
        showSelectionActionsMenu = false
        refreshNodeIdsInProgress = setOf(entry.id)
    }

    fun beginRefreshPlaceholder(nodeIds: Set<Long>) {
        if (nodeIds.isEmpty()) return
        expandedEntryMenuNodeId = null
        showSelectionActionsMenu = false
        refreshNodeIdsInProgress = nodeIds
    }

    fun toggleSelection(nodeId: Long) {
        selectionModeEnabled = true
        selectedNodeIds = if (selectedNodeIds.contains(nodeId)) {
            selectedNodeIds - nodeId
        } else {
            selectedNodeIds + nodeId
        }
    }

    BackHandler(enabled = backHandlingEnabled) {
        if (isSelectionMode) {
            selectedNodeIds = emptySet()
            selectionModeEnabled = false
            showSelectionActionsMenu = false
            showSelectionToggleMenu = false
        } else if (clipboardState != null) {
            clipboardState = null
        } else if (currentFolderId != null) {
            navigateUpOneFolder()
        } else {
            onExitNetwork()
        }
    }

    fun upsertFolder(name: String) {
        val normalized = name.trim()
        if (normalized.isEmpty()) return
        val updated = if (editingFolderNodeId == null) {
            nodes + NetworkNode(
                id = nextNetworkNodeId(nodes),
                parentId = currentFolderId,
                type = NetworkNodeType.Folder,
                title = normalized
            )
        } else {
            nodes.map { node ->
                if (node.id == editingFolderNodeId) {
                    node.copy(title = normalized)
                } else {
                    node
                }
            }
        }
        onNodesChanged(updated)
    }

    fun upsertRemoteSource(name: String, source: String) {
        val normalizedSource = source.trim()
        if (normalizedSource.isEmpty()) return
        val title = name.trim().ifBlank { normalizedSource }
        val updated = if (editingSourceNodeId == null) {
            nodes + NetworkNode(
                id = nextNetworkNodeId(nodes),
                parentId = currentFolderId,
                type = NetworkNodeType.RemoteSource,
                title = title,
                source = normalizedSource,
                sourceKind = NetworkSourceKind.Generic
            )
        } else {
            nodes.map { node ->
                if (node.id == editingSourceNodeId) {
                    val sourceChanged = resolveNetworkNodeSourceId(node) != normalizedSource
                    node.copy(
                        title = title,
                        source = normalizedSource,
                        sourceKind = NetworkSourceKind.Generic,
                        smbHost = null,
                        smbShare = null,
                        smbPath = null,
                        smbUsername = null,
                        smbPassword = null,
                        metadataTitle = if (sourceChanged) null else node.metadataTitle,
                        metadataArtist = if (sourceChanged) null else node.metadataArtist
                    )
                } else {
                    node
                }
            }
        }
        onNodesChanged(updated)
        onResolveRemoteSourceMetadata(normalizedSource)
    }

    fun upsertSmbSource(
        name: String,
        host: String,
        share: String,
        path: String,
        username: String,
        password: String
    ) {
        val smbSpec = buildSmbSourceSpec(
            host = host,
            share = share,
            path = path,
            username = username,
            password = password
        ) ?: return
        val sourceId = buildSmbSourceId(smbSpec)
        val title = name.trim().ifBlank {
            if (smbSpec.share.isBlank()) {
                smbSpec.host
            } else if (smbSpec.path.isNullOrBlank()) {
                "${smbSpec.host}/${smbSpec.share}"
            } else {
                "${smbSpec.host}/${smbSpec.share}/${smbSpec.path}"
            }
        }
        val updated = if (editingSmbNodeId == null) {
            nodes + NetworkNode(
                id = nextNetworkNodeId(nodes),
                parentId = currentFolderId,
                type = NetworkNodeType.RemoteSource,
                title = title,
                source = sourceId,
                sourceKind = NetworkSourceKind.Smb,
                smbHost = smbSpec.host,
                smbShare = smbSpec.share,
                smbPath = smbSpec.path,
                smbUsername = smbSpec.username,
                smbPassword = smbSpec.password
            )
        } else {
            nodes.map { node ->
                if (node.id == editingSmbNodeId) {
                    val sourceChanged = resolveNetworkNodeSourceId(node) != sourceId
                    node.copy(
                        title = title,
                        source = sourceId,
                        sourceKind = NetworkSourceKind.Smb,
                        smbHost = smbSpec.host,
                        smbShare = smbSpec.share,
                        smbPath = smbSpec.path,
                        smbUsername = smbSpec.username,
                        smbPassword = smbSpec.password,
                        metadataTitle = if (sourceChanged) null else node.metadataTitle,
                        metadataArtist = if (sourceChanged) null else node.metadataArtist
                    )
                } else {
                    node
                }
            }
        }
        onNodesChanged(updated)
    }

    fun applyPasteFromClipboard() {
        val activeClipboard = clipboardState ?: return
        val sourceRootIds = normalizeSelectionRootIds(nodes, activeClipboard.nodeIds)
        if (sourceRootIds.isEmpty()) {
            clipboardState = null
            return
        }
        var workingNodes = nodes
        for (sourceNodeId in sourceRootIds) {
            val updated = when (activeClipboard.mode) {
                NetworkClipboardMode.Copy -> {
                    copyNodeSubtreeToParent(
                        nodes = workingNodes,
                        sourceNodeId = sourceNodeId,
                        targetParentId = currentFolderId
                    )
                }

                NetworkClipboardMode.Move -> {
                    moveNodeToParent(
                        nodes = workingNodes,
                        sourceNodeId = sourceNodeId,
                        targetParentId = currentFolderId
                    )
                }
            }
            if (updated == null) {
                blockedOperationMessage = "Cannot move/copy into this location."
                return
            }
            workingNodes = updated
        }
        if (workingNodes != nodes) {
            onNodesChanged(workingNodes)
        }
        clipboardState = null
        selectedNodeIds = emptySet()
        selectionModeEnabled = false
        showSelectionActionsMenu = false
        showSelectionToggleMenu = false
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

            if (isSelectionMode) {
                Box {
                    IconButton(onClick = { showSelectionActionsMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Selection actions"
                        )
                    }
                    DropdownMenu(
                        expanded = showSelectionActionsMenu,
                        onDismissRequest = { showSelectionActionsMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null
                                )
                            },
                            enabled = selectedNodeIds.isNotEmpty(),
                            onClick = {
                                beginClipboardMode(NetworkClipboardMode.Copy, selectedNodeIds)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Move") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                                    contentDescription = null
                                )
                            },
                            enabled = selectedNodeIds.isNotEmpty(),
                            onClick = {
                                beginClipboardMode(NetworkClipboardMode.Move, selectedNodeIds)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null
                                )
                            },
                            enabled = selectedNodeIds.isNotEmpty(),
                            onClick = { beginDeleteConfirmation(selectedNodeIds) }
                        )
                        DropdownMenuItem(
                            text = { Text("Refresh") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null
                                )
                            },
                            enabled = selectedNodeIds.isNotEmpty(),
                            onClick = { beginRefreshPlaceholder(selectedNodeIds) }
                        )
                    }
                }
                Box {
                    IconButton(onClick = { showSelectionToggleMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.SelectAll,
                            contentDescription = "Selection toggles"
                        )
                    }
                    DropdownMenu(
                        expanded = showSelectionToggleMenu,
                        onDismissRequest = { showSelectionToggleMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Select all") },
                            onClick = {
                                selectedNodeIds = currentEntries.mapTo(LinkedHashSet()) { it.id }
                                showSelectionToggleMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Deselect all") },
                            onClick = {
                                selectedNodeIds = emptySet()
                                showSelectionToggleMenu = false
                            }
                        )
                    }
                }
            }

            clipboardState?.let { activeClipboard ->
                IconButton(onClick = { applyPasteFromClipboard() }) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Paste ${activeClipboard.mode.name.lowercase(Locale.ROOT)}"
                    )
                }
            }

            if (isSelectionMode || clipboardState != null) {
                val cancelLabel = when {
                    isSelectionMode -> "Cancel selection mode"
                    clipboardState != null -> "Cancel ${clipboardState?.mode?.name?.lowercase(Locale.ROOT)} mode"
                    else -> "Cancel"
                }
                IconButton(
                    onClick = {
                        if (isSelectionMode) {
                            selectedNodeIds = emptySet()
                            selectionModeEnabled = false
                            showSelectionActionsMenu = false
                            showSelectionToggleMenu = false
                        }
                        clipboardState = null
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = cancelLabel
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
                            editingFolderNodeId = null
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
                            editingSourceNodeId = null
                            newSourceName = ""
                            newSourcePath = ""
                            showAddSourceDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("SMB source") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showAddMenu = false
                            editingSmbNodeId = null
                            newSmbSourceName = ""
                            newSmbHost = ""
                            newSmbShare = ""
                            newSmbPath = ""
                            newSmbUsername = ""
                            newSmbPassword = ""
                            showAddSmbSourceDialog = true
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
        if (isSelectionMode) {
            Text(
                text = "${selectedNodeIds.size} selected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        clipboardState?.let { activeClipboard ->
            val modeLabel = if (activeClipboard.mode == NetworkClipboardMode.Copy) {
                "Copy"
            } else {
                "Move"
            }
            Text(
                text = "$modeLabel mode active. Open destination folder and tap Paste.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
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
                val sourceId = resolveNetworkNodeSourceId(entry).orEmpty()
                val isSmbFolderLikeSource = isSmbFolderLikeSource(entry, sourceId)
                val isSelected = selectedNodeIds.contains(entry.id)
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (isSelected) {
                        CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        )
                    } else {
                        CardDefaults.elevatedCardColors()
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    when {
                                        isSelectionMode -> toggleSelection(entry.id)
                                        clipboardState != null -> {
                                            if (entry.type == NetworkNodeType.Folder) {
                                                onCurrentFolderIdChanged(entry.id)
                                            }
                                        }
                                        entry.type == NetworkNodeType.Folder -> {
                                            onCurrentFolderIdChanged(entry.id)
                                        }
                                        else -> {
                                            resolveNetworkNodeOpenInput(entry)?.let { openInput ->
                                                if (entry.sourceKind == NetworkSourceKind.Smb) {
                                                    onBrowseSmbSource(openInput)
                                                } else {
                                                    onOpenRemoteSource(openInput)
                                                }
                                            }
                                        }
                                    }
                                },
                                onLongClick = {
                                    expandedEntryMenuNodeId = null
                                    showSelectionActionsMenu = false
                                    clipboardState = null
                                    selectionModeEnabled = true
                                    selectedNodeIds = selectedNodeIds + entry.id
                                }
                            )
                            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isFolder = entry.type == NetworkNodeType.Folder
                        val chipShape = RoundedCornerShape(11.dp)
                        val chipContainerColor = if (isFolder || isSmbFolderLikeSource) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                        val chipContentColor = if (isFolder || isSmbFolderLikeSource) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                        val leadingIcon = when {
                            isFolder -> Icons.Default.Folder
                            isSmbFolderLikeSource -> NetworkIcons.FolderData
                            else -> Icons.Default.AudioFile
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
                                imageVector = leadingIcon,
                                contentDescription = null,
                                tint = chipContentColor,
                                modifier = Modifier.size(NETWORK_ICON_GLYPH_SIZE)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            val isRemoteSource = entry.type == NetworkNodeType.RemoteSource
                            val sourceLabel = resolveNetworkNodeDisplaySource(entry)
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
                                val sourceScheme = Uri.parse(sourceId).scheme?.lowercase(Locale.ROOT)
                                val sourceTypeLabel = if (sourceScheme == "smb") "SMB" else null
                                val formatLabel = if (isSmbFolderLikeSource) {
                                    "Folder"
                                } else {
                                    inferNetworkSourceFormatLabel(sourceLabel)
                                }
                                buildString {
                                    sourceTypeLabel?.let {
                                        append(it)
                                        append(" • ")
                                    }
                                    append(formatLabel)
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

                        if (!isSelectionMode) {
                            Box {
                                IconButton(
                                    onClick = { expandedEntryMenuNodeId = entry.id }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Open menu"
                                    )
                                }
                                DropdownMenu(
                                    expanded = expandedEntryMenuNodeId == entry.id,
                                    onDismissRequest = { expandedEntryMenuNodeId = null }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Edit") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = { beginEntryEdit(entry) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Copy") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            beginClipboardMode(NetworkClipboardMode.Copy, setOf(entry.id))
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Move") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            beginClipboardMode(NetworkClipboardMode.Move, setOf(entry.id))
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = { beginDeleteConfirmation(entry) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Refresh") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = { beginRefreshPlaceholder(entry) }
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.size(48.dp))
                        }
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        val isEditing = editingFolderNodeId != null
        AlertDialog(
            onDismissRequest = {
                showCreateFolderDialog = false
                editingFolderNodeId = null
            },
            title = {
                Text(if (isEditing) "Edit folder" else "Create folder")
            },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    singleLine = true,
                    label = { RequiredFieldLabel("Folder name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        upsertFolder(newFolderName)
                        showCreateFolderDialog = false
                        editingFolderNodeId = null
                        newFolderName = ""
                    },
                    enabled = newFolderName.trim().isNotEmpty()
                ) {
                    Text(if (isEditing) "Save" else "Create")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateFolderDialog = false
                        editingFolderNodeId = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddSourceDialog) {
        val isEditing = editingSourceNodeId != null
        AlertDialog(
            onDismissRequest = {
                showAddSourceDialog = false
                editingSourceNodeId = null
            },
            title = {
                Text(if (isEditing) "Edit remote source" else "Add remote source")
            },
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
                        label = { RequiredFieldLabel("URL or path") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        upsertRemoteSource(newSourceName, newSourcePath)
                        showAddSourceDialog = false
                        editingSourceNodeId = null
                        newSourceName = ""
                        newSourcePath = ""
                    },
                    enabled = newSourcePath.trim().isNotEmpty()
                ) {
                    Text(if (isEditing) "Save" else "Add")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddSourceDialog = false
                        editingSourceNodeId = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddSmbSourceDialog) {
        val isEditing = editingSmbNodeId != null
        AlertDialog(
            onDismissRequest = {
                showAddSmbSourceDialog = false
                editingSmbNodeId = null
            },
            title = {
                Text(if (isEditing) "Edit SMB source" else "Add SMB source")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newSmbSourceName,
                        onValueChange = { newSmbSourceName = it },
                        singleLine = true,
                        label = { Text("Name (optional)") }
                    )
                    OutlinedTextField(
                        value = newSmbHost,
                        onValueChange = { newSmbHost = it },
                        singleLine = true,
                        label = { RequiredFieldLabel("Host") }
                    )
                    OutlinedTextField(
                        value = newSmbShare,
                        onValueChange = { newSmbShare = it },
                        singleLine = true,
                        label = { Text("Share (optional)") }
                    )
                    OutlinedTextField(
                        value = newSmbPath,
                        onValueChange = { newSmbPath = it },
                        singleLine = true,
                        label = { Text("Path inside share (optional)") }
                    )
                    OutlinedTextField(
                        value = newSmbUsername,
                        onValueChange = { newSmbUsername = it },
                        singleLine = true,
                        label = { Text("Username (optional)") }
                    )
                    OutlinedTextField(
                        value = newSmbPassword,
                        onValueChange = { newSmbPassword = it },
                        singleLine = true,
                        label = { Text("Password (optional)") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        upsertSmbSource(
                            name = newSmbSourceName,
                            host = newSmbHost,
                            share = newSmbShare,
                            path = newSmbPath,
                            username = newSmbUsername,
                            password = newSmbPassword
                        )
                        showAddSmbSourceDialog = false
                        editingSmbNodeId = null
                        newSmbSourceName = ""
                        newSmbHost = ""
                        newSmbShare = ""
                        newSmbPath = ""
                        newSmbUsername = ""
                        newSmbPassword = ""
                    },
                    enabled = newSmbHost.trim().isNotEmpty()
                ) {
                    Text(if (isEditing) "Save" else "Add")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddSmbSourceDialog = false
                        editingSmbNodeId = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (deleteNodePendingIds.isNotEmpty()) {
        val deleteRootIds = normalizeSelectionRootIds(nodes, deleteNodePendingIds)
        AlertDialog(
            onDismissRequest = { deleteNodeIdsPendingConfirmation = null },
            title = {
                Text(
                    if (deleteRootIds.size == 1) {
                        "Delete entry"
                    } else {
                        "Delete entries"
                    }
                )
            },
            text = {
                Text(
                    text = if (deleteRootIds.size == 1 && deleteNodePending != null) {
                        "Delete \"${deleteNodePending.title}\" and all of its contents?"
                    } else {
                        "Delete ${deleteRootIds.size} selected entries and their contents?"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val idsToDelete = deleteRootIds
                            .flatMapTo(LinkedHashSet()) { rootId ->
                                collectNodeSubtreeIds(nodes, rootId)
                            }
                        val updated = nodes.filterNot { idsToDelete.contains(it.id) }
                        onNodesChanged(updated)
                        if (clipboardState?.nodeIds.orEmpty().intersect(idsToDelete).isNotEmpty()) {
                            clipboardState = null
                        }
                        selectedNodeIds = selectedNodeIds - idsToDelete
                        deleteNodeIdsPendingConfirmation = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteNodeIdsPendingConfirmation = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (refreshNodePendingIds.isNotEmpty()) {
        val refreshCount = refreshNodePendingIds.size
        LaunchedEffect(refreshNodePendingIds) {
            delay(NETWORK_REFRESH_PLACEHOLDER_DELAY_MS)
            refreshNodeIdsInProgress = null
        }
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Refresh") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text("Refreshing $refreshCount/$refreshCount files...")
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    blockedOperationMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { blockedOperationMessage = null },
            title = { Text("Cannot complete action") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { blockedOperationMessage = null }) {
                    Text("OK")
                }
            }
        )
    }
}

private fun collectNodeSubtreeIds(nodes: List<NetworkNode>, rootNodeId: Long): Set<Long> {
    val nodesById = nodes.associateBy { it.id }
    if (!nodesById.containsKey(rootNodeId)) return emptySet()
    val childrenByParent = nodes.groupBy { it.parentId }
    val visited = LinkedHashSet<Long>()
    val pending = ArrayDeque<Long>()
    pending.add(rootNodeId)
    while (pending.isNotEmpty()) {
        val nodeId = pending.removeFirst()
        if (!visited.add(nodeId)) continue
        childrenByParent[nodeId].orEmpty().forEach { child ->
            pending.add(child.id)
        }
    }
    return visited
}

private fun normalizeSelectionRootIds(
    nodes: List<NetworkNode>,
    selectedNodeIds: Set<Long>
): List<Long> {
    if (selectedNodeIds.isEmpty()) return emptyList()
    val nodesById = nodes.associateBy { it.id }
    val selectedExistingIds = selectedNodeIds.filter { nodesById.containsKey(it) }.toSet()
    if (selectedExistingIds.isEmpty()) return emptyList()

    fun hasSelectedAncestor(nodeId: Long): Boolean {
        var cursor = nodesById[nodeId]?.parentId
        while (cursor != null) {
            if (selectedExistingIds.contains(cursor)) return true
            cursor = nodesById[cursor]?.parentId
        }
        return false
    }

    return nodes.asSequence()
        .map { it.id }
        .filter { selectedExistingIds.contains(it) }
        .filterNot(::hasSelectedAncestor)
        .toList()
}

private fun copyNodeSubtreeToParent(
    nodes: List<NetworkNode>,
    sourceNodeId: Long,
    targetParentId: Long?
): List<NetworkNode>? {
    if (targetParentId != null && nodes.none { it.id == targetParentId && it.type == NetworkNodeType.Folder }) {
        return null
    }
    val nodesById = nodes.associateBy { it.id }
    val sourceNode = nodesById[sourceNodeId] ?: return null
    val childrenByParent = nodes.groupBy { it.parentId }
    val orderedSubtreeNodes = mutableListOf<NetworkNode>()
    val pending = ArrayDeque<Long>()
    pending.add(sourceNode.id)
    while (pending.isNotEmpty()) {
        val nodeId = pending.removeFirst()
        val node = nodesById[nodeId] ?: continue
        orderedSubtreeNodes += node
        childrenByParent[nodeId].orEmpty().forEach { child ->
            pending.add(child.id)
        }
    }
    if (orderedSubtreeNodes.isEmpty()) return null

    var nextId = nextNetworkNodeId(nodes)
    val remappedIds = HashMap<Long, Long>(orderedSubtreeNodes.size)
    val copiedNodes = orderedSubtreeNodes.map { node ->
        val newId = nextId++
        remappedIds[node.id] = newId
        val newParentId = if (node.id == sourceNodeId) {
            targetParentId
        } else {
            remappedIds[node.parentId] ?: targetParentId
        }
        node.copy(id = newId, parentId = newParentId)
    }
    return nodes + copiedNodes
}

private fun moveNodeToParent(
    nodes: List<NetworkNode>,
    sourceNodeId: Long,
    targetParentId: Long?
): List<NetworkNode>? {
    if (targetParentId != null && nodes.none { it.id == targetParentId && it.type == NetworkNodeType.Folder }) {
        return null
    }
    val sourceNode = nodes.firstOrNull { it.id == sourceNodeId } ?: return null
    if (sourceNode.parentId == targetParentId) {
        return nodes
    }
    val sourceSubtreeIds = collectNodeSubtreeIds(nodes, sourceNodeId)
    if (targetParentId != null && sourceSubtreeIds.contains(targetParentId)) {
        return null
    }
    return nodes.map { node ->
        if (node.id == sourceNodeId) {
            node.copy(parentId = targetParentId)
        } else {
            node
        }
    }
}

private fun isSmbFolderLikeSource(entry: NetworkNode, sourceId: String): Boolean {
    if (entry.type != NetworkNodeType.RemoteSource || entry.sourceKind != NetworkSourceKind.Smb) {
        return false
    }
    val spec = resolveNetworkNodeSmbSpec(entry) ?: return false
    if (spec.share.isBlank()) return true
    val normalizedPath = spec.path?.trim().orEmpty()
    if (normalizedPath.isBlank()) return true
    val leaf = normalizedPath.substringAfterLast('/').trim()
    if (leaf.isBlank()) return true
    return inferredPrimaryExtensionForName(leaf) == null || sourceId.endsWith("/")
}

@Composable
private fun RequiredFieldLabel(text: String) {
    Text(
        text = buildAnnotatedString {
            append(text)
            append(" ")
            withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.error)) {
                append("*")
            }
        }
    )
}

private fun inferNetworkSourceFormatLabel(source: String): String {
    if (source.isBlank()) return "Unknown"
    val parsed = Uri.parse(source)
    val leaf = if (!parsed.scheme.isNullOrBlank()) {
        parsed.lastPathSegment.orEmpty()
    } else {
        source
            .substringBefore('#')
            .substringBefore('?')
            .substringAfterLast('/')
    }
    if (leaf.isBlank()) return "Unknown"
    val ext = inferredPrimaryExtensionForName(leaf)
    return ext?.uppercase(Locale.ROOT) ?: "Unknown"
}
