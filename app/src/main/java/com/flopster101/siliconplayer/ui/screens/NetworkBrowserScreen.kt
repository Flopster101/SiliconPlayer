package com.flopster101.siliconplayer.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.NetworkNode
import com.flopster101.siliconplayer.NetworkNodeType
import com.flopster101.siliconplayer.NetworkSourceKind
import com.flopster101.siliconplayer.SmbSourceSpec
import com.flopster101.siliconplayer.buildRecentTrackDisplay
import com.flopster101.siliconplayer.buildSmbRequestUri
import com.flopster101.siliconplayer.buildSmbSourceId
import com.flopster101.siliconplayer.buildSmbSourceSpec
import com.flopster101.siliconplayer.formatNetworkFolderSummary
import com.flopster101.siliconplayer.inferredPrimaryExtensionForName
import com.flopster101.siliconplayer.normalizeSourceIdentity
import com.flopster101.siliconplayer.nextNetworkNodeId
import com.flopster101.siliconplayer.parseSmbSourceSpecFromInput
import com.flopster101.siliconplayer.placeholderArtworkIconForFile
import com.flopster101.siliconplayer.resolveNetworkNodeDisplaySource
import com.flopster101.siliconplayer.resolveNetworkNodeDisplayTitle
import com.flopster101.siliconplayer.resolveNetworkNodeOpenInput
import com.flopster101.siliconplayer.resolveNetworkNodeSmbSpec
import com.flopster101.siliconplayer.resolveNetworkNodeSourceId
import com.flopster101.siliconplayer.resolveSmbHostDisplayName
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val NETWORK_ICON_BOX_SIZE = 38.dp
private val NETWORK_ICON_GLYPH_SIZE = 26.dp
private const val NETWORK_ENTRY_ANIM_DURATION_MS = 190
private const val NETWORK_STATUS_ANIM_DURATION_MS = 170
private const val NETWORK_SELECTION_COLOR_ANIM_DURATION_MS = 140
private const val NETWORK_REFRESH_TIMEOUT_MS = 60_000L

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
    onResolveRemoteSourceMetadata: (String, () -> Unit) -> Unit,
    onCancelPendingMetadataBackfill: () -> Unit,
    onOpenRemoteSource: (String) -> Unit,
    onBrowseSmbSource: (String, Long?) -> Unit
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
    var refreshNodeIdsPendingConfirmation by remember { mutableStateOf<Set<Long>?>(null) }
    var refreshNodeIdsInProgress by remember { mutableStateOf<Set<Long>?>(null) }
    var refreshCompletedNodeIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var refreshSuccessfulFileCount by remember { mutableStateOf(0) }
    var refreshPopupHidden by remember { mutableStateOf(false) }
    var refreshSourceNodeMap by remember { mutableStateOf<Map<String, Set<Long>>>(emptyMap()) }
    var metadataLoadingNodeIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    var newFolderName by remember { mutableStateOf("") }
    var newSourceName by remember { mutableStateOf("") }
    var newSourcePath by remember { mutableStateOf("") }
    var newSmbSourceName by remember { mutableStateOf("") }
    var newSmbHost by remember { mutableStateOf("") }
    var newSmbShare by remember { mutableStateOf("") }
    var newSmbPath by remember { mutableStateOf("") }
    var newSmbUsername by remember { mutableStateOf("") }
    var newSmbPassword by remember { mutableStateOf("") }
    var newSmbPasswordVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()
    val refreshTimeoutJobs = remember { LinkedHashMap<String, Job>() }
    val refreshSettledSources = remember { LinkedHashSet<String>() }
    val smbHostResolveJobs = remember { LinkedHashMap<String, Job>() }
    val latestNodes by rememberUpdatedState(nodes)
    val latestOnNodesChanged by rememberUpdatedState(onNodesChanged)

    DisposableEffect(onCancelPendingMetadataBackfill) {
        onDispose {
            refreshTimeoutJobs.values.forEach { it.cancel() }
            refreshTimeoutJobs.clear()
            refreshSettledSources.clear()
            smbHostResolveJobs.values.forEach { it.cancel() }
            smbHostResolveJobs.clear()
            onCancelPendingMetadataBackfill()
        }
    }

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
                    .thenBy { resolveNetworkNodeDisplayTitle(it).lowercase() }
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
    val refreshNodeConfirmationIds = remember(refreshNodeIdsPendingConfirmation) {
        refreshNodeIdsPendingConfirmation.orEmpty()
    }
    LaunchedEffect(nodes) {
        val existingNodeIds = nodes.mapTo(LinkedHashSet()) { it.id }
        metadataLoadingNodeIds = metadataLoadingNodeIds.intersect(existingNodeIds)
        refreshNodeIdsInProgress = refreshNodeIdsInProgress?.intersect(existingNodeIds)?.takeIf { it.isNotEmpty() }
        refreshCompletedNodeIds = refreshCompletedNodeIds.intersect(existingNodeIds)
        if (refreshNodeIdsInProgress == null) {
            refreshSuccessfulFileCount = 0
            refreshSourceNodeMap = emptyMap()
            refreshSettledSources.clear()
            refreshTimeoutJobs.values.forEach { it.cancel() }
            refreshTimeoutJobs.clear()
            refreshPopupHidden = false
        }
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
                newSmbPasswordVisible = false
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

    fun requestSmbHostDisplayName(
        sourceId: String,
        nodeIds: Set<Long>,
        specOverride: SmbSourceSpec? = null,
        onSettled: (() -> Unit)? = null
    ) {
        if (nodeIds.isEmpty()) {
            onSettled?.invoke()
            return
        }
        val smbSpec = specOverride
            ?: latestNodes
                .asSequence()
                .filter { nodeIds.contains(it.id) }
                .firstNotNullOfOrNull { node ->
                    if (node.type == NetworkNodeType.RemoteSource && node.sourceKind == NetworkSourceKind.Smb) {
                        resolveNetworkNodeSmbSpec(node)
                    } else {
                        null
                    }
                }
            ?: parseSmbSourceSpecFromInput(sourceId)
        if (smbSpec == null || smbSpec.host.isBlank()) {
            onSettled?.invoke()
            return
        }

        smbHostResolveJobs.remove(sourceId)?.cancel()
        val job = uiScope.launch {
            try {
                val resolvedHostName = resolveSmbHostDisplayName(smbSpec)
                    .getOrNull()
                    ?.trim()
                    .takeUnless { it.isNullOrBlank() }
                    ?: return@launch
                val updated = latestNodes.map { node ->
                    if (node.sourceKind != NetworkSourceKind.Smb || node.type != NetworkNodeType.RemoteSource) {
                        return@map node
                    }
                    val nodeSmbSpec = resolveNetworkNodeSmbSpec(node)
                    val isDirectTarget = nodeIds.contains(node.id)
                    val isSameHost = nodeSmbSpec?.host?.equals(smbSpec.host, ignoreCase = true) == true
                    if (!isDirectTarget && !isSameHost) {
                        return@map node
                    }
                    val currentTitle = node.title.trim()
                    val autoTitleCandidates = buildList {
                        val specForTitle = nodeSmbSpec ?: return@buildList
                        add(specForTitle.host)
                        if (specForTitle.share.isNotBlank()) {
                            add("${specForTitle.host}/${specForTitle.share}")
                            if (!specForTitle.path.isNullOrBlank()) {
                                add("${specForTitle.host}/${specForTitle.share}/${specForTitle.path}")
                            }
                        }
                    }
                    val isLegacyAutoTitle = autoTitleCandidates.any {
                        currentTitle.equals(it, ignoreCase = true)
                    }
                    val normalizedTitle = if (isLegacyAutoTitle) "" else node.title
                    if (node.smbDiscoveredHostName == resolvedHostName && normalizedTitle == node.title) {
                        return@map node
                    }
                    node.copy(
                        smbDiscoveredHostName = resolvedHostName,
                        title = normalizedTitle
                    )
                }
                if (updated != latestNodes) {
                    latestOnNodesChanged(updated)
                }
            } finally {
                onSettled?.invoke()
            }
        }
        smbHostResolveJobs[sourceId] = job
        job.invokeOnCompletion {
            if (smbHostResolveJobs[sourceId] == job) {
                smbHostResolveJobs.remove(sourceId)
            }
        }
    }

    fun requestRemoteSourceMetadata(entry: NetworkNode) {
        if (entry.type != NetworkNodeType.RemoteSource) return
        val sourceId = resolveNetworkNodeSourceId(entry).orEmpty()
        if (sourceId.isBlank()) return
        metadataLoadingNodeIds = metadataLoadingNodeIds + entry.id
        if (entry.sourceKind == NetworkSourceKind.Smb) {
            val isSmbFolderLike = isSmbFolderLikeSource(entry, sourceId)
            val smbRequestSourceId = resolveNetworkNodeSmbSpec(entry)?.let(::buildSmbRequestUri) ?: sourceId
            var pendingSettleCount = if (isSmbFolderLike) 1 else 2
            fun settleOne() {
                pendingSettleCount -= 1
                if (pendingSettleCount <= 0) {
                    metadataLoadingNodeIds = metadataLoadingNodeIds - entry.id
                }
            }
            requestSmbHostDisplayName(
                sourceId = sourceId,
                nodeIds = setOf(entry.id),
                specOverride = resolveNetworkNodeSmbSpec(entry),
                onSettled = ::settleOne
            )
            if (isSmbFolderLike) {
                return
            }
            onResolveRemoteSourceMetadata(smbRequestSourceId, ::settleOne)
            return
        }
        onResolveRemoteSourceMetadata(sourceId) {
            metadataLoadingNodeIds = metadataLoadingNodeIds - entry.id
        }
    }

    fun beginRefreshConfirmation(rootNodeIds: Set<Long>) {
        if (rootNodeIds.isEmpty()) return
        val refreshableFileCount = collectRefreshableRemoteSourceNodeIds(nodes, rootNodeIds).size
        if (refreshableFileCount <= 0) {
            blockedOperationMessage = "No files available to refresh."
            return
        }
        refreshNodeIdsPendingConfirmation = rootNodeIds
    }

    fun settleBatchRefreshSource(sourceId: String, success: Boolean) {
        if (!refreshSettledSources.add(sourceId)) return
        refreshTimeoutJobs.remove(sourceId)?.cancel()
        val sourceNodeIds = refreshSourceNodeMap[sourceId].orEmpty()
        if (sourceNodeIds.isEmpty()) return

        val updatedCompletedNodeIds = refreshCompletedNodeIds + sourceNodeIds
        refreshCompletedNodeIds = updatedCompletedNodeIds
        metadataLoadingNodeIds = metadataLoadingNodeIds - sourceNodeIds
        if (success) {
            refreshSuccessfulFileCount += sourceNodeIds.size
        }

        val totalTargets = refreshNodeIdsInProgress?.size ?: 0
        if (totalTargets > 0 && updatedCompletedNodeIds.size >= totalTargets) {
            val successCount = refreshSuccessfulFileCount
            refreshNodeIdsInProgress = null
            refreshCompletedNodeIds = emptySet()
            refreshSuccessfulFileCount = 0
            refreshPopupHidden = false
            refreshSourceNodeMap = emptyMap()
            refreshSettledSources.clear()
            refreshTimeoutJobs.values.forEach { it.cancel() }
            refreshTimeoutJobs.clear()
            val label = if (successCount == 1) "file" else "files"
            Toast.makeText(
                context,
                "$successCount $label refreshed successfully",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun startBatchRefresh(rootNodeIds: Set<Long>) {
        val refreshableSourceNodeIds = collectRefreshableRemoteSourceNodeIds(nodes, rootNodeIds)
        if (refreshableSourceNodeIds.isEmpty()) {
            blockedOperationMessage = "No files available to refresh."
            return
        }

        val sourceNodeIdsBySourceId = LinkedHashMap<String, MutableSet<Long>>()
        refreshableSourceNodeIds.forEach { nodeId ->
            val sourceId = nodesById[nodeId]?.let(::resolveNetworkNodeSourceId).orEmpty()
            if (sourceId.isBlank()) return@forEach
            sourceNodeIdsBySourceId.getOrPut(sourceId) { LinkedHashSet() } += nodeId
        }
        if (sourceNodeIdsBySourceId.isEmpty()) {
            blockedOperationMessage = "No files available to refresh."
            return
        }

        val targetNodeIds = LinkedHashSet<Long>()
        sourceNodeIdsBySourceId.values.forEach { targetNodeIds.addAll(it) }

        refreshTimeoutJobs.values.forEach { it.cancel() }
        refreshTimeoutJobs.clear()
        refreshSettledSources.clear()
        refreshSourceNodeMap = sourceNodeIdsBySourceId.mapValues { (_, ids) -> ids.toSet() }
        refreshNodeIdsInProgress = targetNodeIds
        refreshCompletedNodeIds = emptySet()
        refreshSuccessfulFileCount = 0
        refreshPopupHidden = false
        metadataLoadingNodeIds = metadataLoadingNodeIds + targetNodeIds

        sourceNodeIdsBySourceId.forEach { (sourceId, _) ->
            val targetNodeIdsForSource = sourceNodeIdsBySourceId[sourceId].orEmpty()
            val representativeNode = nodesById[targetNodeIdsForSource.firstOrNull()]
            val representativeSmbSpec = representativeNode?.let(::resolveNetworkNodeSmbSpec)
            val metadataRequestSourceId = representativeSmbSpec?.let(::buildSmbRequestUri) ?: sourceId
            requestSmbHostDisplayName(
                sourceId = sourceId,
                nodeIds = targetNodeIdsForSource,
                specOverride = representativeSmbSpec
            )
            onResolveRemoteSourceMetadata(metadataRequestSourceId) {
                settleBatchRefreshSource(sourceId, success = true)
            }
            refreshTimeoutJobs[sourceId] = uiScope.launch {
                delay(NETWORK_REFRESH_TIMEOUT_MS)
                settleBatchRefreshSource(sourceId, success = false)
            }
        }
    }

    fun requestRefresh(entry: NetworkNode) {
        if (refreshNodeIdsInProgress != null || refreshNodeIdsPendingConfirmation != null) return
        expandedEntryMenuNodeId = null
        showSelectionActionsMenu = false
        if (entry.type == NetworkNodeType.RemoteSource) {
            requestRemoteSourceMetadata(entry)
        } else {
            beginRefreshConfirmation(setOf(entry.id))
        }
    }

    fun requestRefresh(nodeIds: Set<Long>) {
        if (refreshNodeIdsInProgress != null || refreshNodeIdsPendingConfirmation != null) return
        if (nodeIds.isEmpty()) return
        expandedEntryMenuNodeId = null
        showSelectionActionsMenu = false
        val targetRootIds = normalizeSelectionRootIds(nodes, nodeIds)
        if (targetRootIds.size == 1) {
            val singleEntry = nodesById[targetRootIds.first()]
            if (singleEntry?.type == NetworkNodeType.RemoteSource) {
                requestRemoteSourceMetadata(singleEntry)
                return
            }
        }
        beginRefreshConfirmation(targetRootIds.toSet())
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
        if (refreshNodeIdsPendingConfirmation != null) {
            refreshNodeIdsPendingConfirmation = null
        } else if (isSelectionMode) {
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
        val upsertedNodeId: Long
        val updated = if (editingSourceNodeId == null) {
            val newNodeId = nextNetworkNodeId(nodes)
            upsertedNodeId = newNodeId
            nodes + NetworkNode(
                id = newNodeId,
                parentId = currentFolderId,
                type = NetworkNodeType.RemoteSource,
                title = title,
                source = normalizedSource,
                sourceKind = NetworkSourceKind.Generic
            )
        } else {
            upsertedNodeId = editingSourceNodeId ?: return
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
        metadataLoadingNodeIds = metadataLoadingNodeIds + upsertedNodeId
        onResolveRemoteSourceMetadata(normalizedSource) {
            metadataLoadingNodeIds = metadataLoadingNodeIds - upsertedNodeId
        }
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
        val explicitTitle = name.trim()
        val upsertedNodeId: Long
        val updated = if (editingSmbNodeId == null) {
            val newNodeId = nextNetworkNodeId(nodes)
            upsertedNodeId = newNodeId
            nodes + NetworkNode(
                id = newNodeId,
                parentId = currentFolderId,
                type = NetworkNodeType.RemoteSource,
                title = explicitTitle,
                source = sourceId,
                sourceKind = NetworkSourceKind.Smb,
                smbHost = smbSpec.host,
                smbShare = smbSpec.share,
                smbPath = smbSpec.path,
                smbUsername = smbSpec.username,
                smbPassword = smbSpec.password,
                smbDiscoveredHostName = null
            )
        } else {
            upsertedNodeId = editingSmbNodeId ?: return
            nodes.map { node ->
                if (node.id == editingSmbNodeId) {
                    val sourceChanged = resolveNetworkNodeSourceId(node) != sourceId
                    node.copy(
                        title = explicitTitle,
                        source = sourceId,
                        sourceKind = NetworkSourceKind.Smb,
                        smbHost = smbSpec.host,
                        smbShare = smbSpec.share,
                        smbPath = smbSpec.path,
                        smbUsername = smbSpec.username,
                        smbPassword = smbSpec.password,
                        smbDiscoveredHostName = if (sourceChanged) null else node.smbDiscoveredHostName,
                        metadataTitle = if (sourceChanged) null else node.metadataTitle,
                        metadataArtist = if (sourceChanged) null else node.metadataArtist
                    )
                } else {
                    node
                }
            }
        }
        onNodesChanged(updated)
        metadataLoadingNodeIds = metadataLoadingNodeIds + upsertedNodeId
        val upsertedNode = updated.firstOrNull { it.id == upsertedNodeId }
        val isSmbFolderLike = upsertedNode?.let { isSmbFolderLikeSource(it, sourceId) } ?: false
        var pendingSettleCount = if (isSmbFolderLike) 1 else 2
        fun settleOne() {
            pendingSettleCount -= 1
            if (pendingSettleCount <= 0) {
                metadataLoadingNodeIds = metadataLoadingNodeIds - upsertedNodeId
            }
        }
        requestSmbHostDisplayName(
            sourceId = sourceId,
            nodeIds = setOf(upsertedNodeId),
            specOverride = smbSpec,
            onSettled = ::settleOne
        )
        if (!isSmbFolderLike) {
            onResolveRemoteSourceMetadata(buildSmbRequestUri(smbSpec), ::settleOne)
        }
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

    val toolbarActionButtonModifier = Modifier.size(40.dp)
    val toolbarActionIconModifier = Modifier.size(20.dp)
    val statusMessage = when {
        isSelectionMode -> "${selectedNodeIds.size} selected"
        clipboardState != null -> {
            val modeLabel = if (clipboardState?.mode == NetworkClipboardMode.Copy) {
                "Copy"
            } else {
                "Move"
            }
            "$modeLabel mode active. Open destination folder and tap Paste."
        }
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .padding(bottom = bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = tween(
                        durationMillis = NETWORK_STATUS_ANIM_DURATION_MS,
                        easing = FastOutSlowInEasing
                    )
                ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 2.dp,
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentFolderId != null) {
                        IconButton(
                            onClick = { navigateUpOneFolder() },
                            modifier = toolbarActionButtonModifier
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Go to parent folder",
                                modifier = toolbarActionIconModifier
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                    ) {
                        Text(
                            text = "Network",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    if (isSelectionMode) {
                        Box {
                            IconButton(
                                onClick = { showSelectionActionsMenu = true },
                                modifier = toolbarActionButtonModifier
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Selection actions",
                                    modifier = toolbarActionIconModifier
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
                                    onClick = { requestRefresh(selectedNodeIds) }
                                )
                            }
                        }
                        Box {
                            IconButton(
                                onClick = { showSelectionToggleMenu = true },
                                modifier = toolbarActionButtonModifier
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SelectAll,
                                    contentDescription = "Selection toggles",
                                    modifier = toolbarActionIconModifier
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
                        IconButton(
                            onClick = { applyPasteFromClipboard() },
                            modifier = toolbarActionButtonModifier
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Paste ${activeClipboard.mode.name.lowercase(Locale.ROOT)}",
                                modifier = toolbarActionIconModifier
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
                            },
                            modifier = toolbarActionButtonModifier
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = cancelLabel,
                                modifier = toolbarActionIconModifier
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showAddMenu = true },
                            modifier = toolbarActionButtonModifier
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add folder or source",
                                modifier = toolbarActionIconModifier
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
                                text = { Text("SMB share") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = NetworkIcons.SmbShare,
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
                                    newSmbPasswordVisible = false
                                    showAddSmbSourceDialog = true
                                }
                            )
                        }
                    }
                }
            }
            AnimatedContent(
                targetState = statusMessage,
                transitionSpec = {
                    (
                        fadeIn(
                            animationSpec = tween(
                                durationMillis = NETWORK_STATUS_ANIM_DURATION_MS,
                                easing = LinearOutSlowInEasing
                            )
                        ) + slideInVertically(
                            initialOffsetY = { fullHeight -> fullHeight / 2 },
                            animationSpec = tween(
                                durationMillis = NETWORK_STATUS_ANIM_DURATION_MS,
                                easing = FastOutSlowInEasing
                            )
                        )
                    ) togetherWith (
                        fadeOut(
                            animationSpec = tween(
                                durationMillis = NETWORK_STATUS_ANIM_DURATION_MS / 2,
                                easing = FastOutSlowInEasing
                            )
                        ) + slideOutVertically(
                            targetOffsetY = { fullHeight -> -(fullHeight / 3) },
                            animationSpec = tween(
                                durationMillis = NETWORK_STATUS_ANIM_DURATION_MS / 2,
                                easing = FastOutSlowInEasing
                            )
                        )
                    )
                },
                label = "networkStatusTransition",
                modifier = Modifier.fillMaxWidth()
            ) { message ->
                if (message == null) {
                    Spacer(modifier = Modifier.height(2.dp))
                } else {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        AnimatedContent(
            targetState = currentEntries.isEmpty(),
            transitionSpec = {
                (
                    fadeIn(
                        animationSpec = tween(
                            durationMillis = NETWORK_STATUS_ANIM_DURATION_MS,
                            easing = LinearOutSlowInEasing
                        )
                    ) + slideInVertically(
                        initialOffsetY = { fullHeight -> fullHeight / 10 },
                        animationSpec = tween(
                            durationMillis = NETWORK_STATUS_ANIM_DURATION_MS,
                            easing = FastOutSlowInEasing
                        )
                    )
                ) togetherWith (
                    fadeOut(
                        animationSpec = tween(
                            durationMillis = NETWORK_STATUS_ANIM_DURATION_MS / 2,
                            easing = FastOutSlowInEasing
                        )
                    )
                )
            },
            label = "networkListState",
            modifier = Modifier.fillMaxWidth()
        ) { isEmpty ->
            if (isEmpty) {
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    currentEntries.forEach { entry ->
                        key(entry.id) {
                            AnimatedNetworkEntry(
                                itemKey = entry.id,
                                parentFolderId = currentFolderId
                            ) {
                val sourceId = resolveNetworkNodeSourceId(entry).orEmpty()
                val sourceScheme = Uri.parse(sourceId).scheme?.lowercase(Locale.ROOT)
                val isSmbFolderLikeSource = isSmbFolderLikeSource(entry, sourceId)
                val isSelected = selectedNodeIds.contains(entry.id)
                val cardContainerColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    animationSpec = tween(
                        durationMillis = NETWORK_SELECTION_COLOR_ANIM_DURATION_MS,
                        easing = FastOutSlowInEasing
                    ),
                    label = "networkEntrySelectionColor"
                )
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = cardContainerColor)
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
                                                    onBrowseSmbSource(openInput, entry.id)
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
                        val remoteSourceIconFile = if (!isFolder && !isSmbFolderLikeSource) {
                            resolveNetworkRemoteIconFile(sourceId)
                        } else {
                            null
                        }
                        val leadingIcon = when {
                            isFolder -> Icons.Default.Folder
                            isSmbFolderLikeSource -> NetworkIcons.SmbShare
                            else -> placeholderArtworkIconForFile(
                                file = remoteSourceIconFile,
                                decoderName = null,
                                allowCurrentDecoderFallback = false
                            )
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
                            val isMetadataLoading = isRemoteSource && metadataLoadingNodeIds.contains(entry.id)
                            val sourceLabel = resolveNetworkNodeDisplaySource(entry)
                            val fallbackTitle = resolveNetworkNodeDisplayTitle(entry)
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
                                buildAnnotatedString {
                                    append(folderSummariesById[entry.id].orEmpty())
                                }
                            } else {
                                val sourceTypeLabel = if (sourceScheme == "smb") {
                                    val smbHostLabel = entry.smbDiscoveredHostName
                                        ?.trim()
                                        .takeUnless { it.isNullOrBlank() }
                                        ?: parseSmbSourceSpecFromInput(sourceId)?.host
                                            ?.let(::normalizeSmbHostLabelForUi)
                                    if (smbHostLabel == null) "SMB" else "SMB ($smbHostLabel)"
                                } else {
                                    null
                                }
                                val formatLabel = if (isSmbFolderLikeSource) {
                                    "Folder"
                                } else {
                                    inferNetworkSourceFormatLabel(sourceLabel)
                                }
                                buildNetworkEntrySubtitle(
                                    sourceTypeLabel = sourceTypeLabel,
                                    formatLabel = formatLabel,
                                    sourceLabel = sourceLabel
                                )
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

                            if (isMetadataLoading) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.8.dp
                                    )
                                    Text(
                                        text = "Loading information...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (subtitle.isNotBlank()) {
                                if (isRemoteSource) {
                                    val shouldMarqueeSubtitle = subtitle.length > 44
                                    val subtitleIcon = when (sourceScheme) {
                                        "smb" -> NetworkIcons.SmbShare
                                        "http", "https" -> NetworkIcons.WorldCode
                                        else -> Icons.Default.Public
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = subtitleIcon,
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
                                        onClick = { requestRefresh(entry) }
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
                newSmbPasswordVisible = false
            },
            title = {
                Text(if (isEditing) "Edit SMB share" else "Add SMB share")
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
                        label = { Text("Password (optional)") },
                        visualTransformation = if (newSmbPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { newSmbPasswordVisible = !newSmbPasswordVisible }) {
                                Icon(
                                    imageVector = if (newSmbPasswordVisible) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                    contentDescription = if (newSmbPasswordVisible) {
                                        "Hide password"
                                    } else {
                                        "Show password"
                                    }
                                )
                            }
                        }
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
                        newSmbPasswordVisible = false
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
                        newSmbPasswordVisible = false
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

    if (refreshNodeConfirmationIds.isNotEmpty()) {
        val refreshableFileCount = remember(nodes, refreshNodeConfirmationIds) {
            collectRefreshableRemoteSourceNodeIds(nodes, refreshNodeConfirmationIds).size
        }
        AlertDialog(
            onDismissRequest = { refreshNodeIdsPendingConfirmation = null },
            title = { Text("Refresh files") },
            text = {
                Text(
                    text = "Refresh $refreshableFileCount ${
                        if (refreshableFileCount == 1) "file" else "files"
                    }?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pendingRootIds = refreshNodeConfirmationIds
                        refreshNodeIdsPendingConfirmation = null
                        startBatchRefresh(pendingRootIds)
                    }
                ) {
                    Text("Refresh")
                }
            },
            dismissButton = {
                TextButton(onClick = { refreshNodeIdsPendingConfirmation = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (refreshNodePendingIds.isNotEmpty() && !refreshPopupHidden) {
        val refreshTotalCount = refreshNodePendingIds.size
        val refreshCompletedCount = refreshCompletedNodeIds.size.coerceIn(0, refreshTotalCount)
        AlertDialog(
            onDismissRequest = { refreshPopupHidden = true },
            title = { Text("Refresh") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text("Refreshing $refreshCompletedCount/$refreshTotalCount files...")
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { refreshPopupHidden = true }) {
                    Text("Hide")
                }
            }
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

@Composable
private fun AnimatedNetworkEntry(
    itemKey: Long,
    parentFolderId: Long?,
    content: @Composable () -> Unit
) {
    var visible by remember(itemKey, parentFolderId) { mutableStateOf(false) }
    LaunchedEffect(itemKey, parentFolderId) {
        visible = false
        withFrameNanos { }
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = NETWORK_ENTRY_ANIM_DURATION_MS,
            easing = LinearOutSlowInEasing
        ),
        label = "networkEntryAlpha"
    )
    val offsetY by animateDpAsState(
        targetValue = if (visible) 0.dp else 8.dp,
        animationSpec = tween(
            durationMillis = NETWORK_ENTRY_ANIM_DURATION_MS,
            easing = LinearOutSlowInEasing
        ),
        label = "networkEntryOffset"
    )

    Box(
        modifier = Modifier
            .offset(y = offsetY)
            .alpha(alpha)
    ) {
        content()
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

private fun collectRefreshableRemoteSourceNodeIds(
    nodes: List<NetworkNode>,
    rootNodeIds: Set<Long>
): Set<Long> {
    if (rootNodeIds.isEmpty()) return emptySet()
    val nodesById = nodes.associateBy { it.id }
    val normalizedRootIds = normalizeSelectionRootIds(nodes, rootNodeIds)
    val effectiveRootIds = if (normalizedRootIds.isEmpty()) rootNodeIds.toList() else normalizedRootIds
    val refreshableIds = LinkedHashSet<Long>()
    effectiveRootIds.forEach { rootNodeId ->
        val subtreeIds = collectNodeSubtreeIds(nodes, rootNodeId)
        subtreeIds.forEach { nodeId ->
            val node = nodesById[nodeId] ?: return@forEach
            if (node.type == NetworkNodeType.RemoteSource) {
                refreshableIds += nodeId
            }
        }
    }
    return refreshableIds
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

private fun resolveNetworkRemoteIconFile(sourceId: String): File? {
    if (sourceId.isBlank()) return null
    val normalizedSource = normalizeSourceIdentity(sourceId) ?: sourceId
    val parsed = Uri.parse(normalizedSource)
    val leafName = when {
        parsed.scheme.equals("file", ignoreCase = true) -> {
            parsed.path
                ?.substringAfterLast('/')
                ?.trim()
                .orEmpty()
        }

        !parsed.scheme.isNullOrBlank() -> {
            parsed.lastPathSegment
                ?.trim()
                .orEmpty()
        }

        else -> {
            normalizedSource
                .substringBefore('#')
                .substringBefore('?')
                .substringAfterLast('/')
                .trim()
        }
    }
    if (leafName.isBlank()) return null
    return File(leafName)
}

private fun buildNetworkEntrySubtitle(
    sourceTypeLabel: String?,
    formatLabel: String,
    sourceLabel: String
): AnnotatedString {
    return buildAnnotatedString {
        sourceTypeLabel?.let { label ->
            appendBoldSourceTypeToken(label)
            append(" • ")
        }
        append(formatLabel)
        if (sourceLabel.isNotBlank()) {
            append(" • ")
            append(sourceLabel)
        }
    }
}

private fun AnnotatedString.Builder.appendBoldSourceTypeToken(label: String) {
    val trimmed = label.trim()
    if (trimmed.isBlank()) return
    val splitIndex = trimmed.indexOfFirst { it == ' ' || it == '(' }.let { idx ->
        if (idx < 0) trimmed.length else idx
    }
    val token = trimmed.substring(0, splitIndex)
    val suffix = trimmed.substring(splitIndex)
    withStyle(style = SpanStyle(fontWeight = FontWeight.SemiBold)) {
        append(token)
    }
    append(suffix)
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

private fun normalizeSmbHostLabelForUi(rawHost: String?): String? {
    val host = rawHost?.trim().takeUnless { it.isNullOrBlank() } ?: return null
    val withoutLocal = host.removeSuffix(".local").removeSuffix(".LOCAL").trimEnd('.')
    return withoutLocal.ifBlank { host }
}
