package com.flopster101.siliconplayer

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.data.isArchiveLogicalFolderPath
import com.flopster101.siliconplayer.data.parseArchiveLogicalPath
import com.flopster101.siliconplayer.data.parseArchiveSourceId
import com.flopster101.siliconplayer.ui.screens.NetworkIcons
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val HomeCardShape = RoundedCornerShape(16.dp)
private val HomeRecentIconChipShape = RoundedCornerShape(11.dp)
private val HomeRecentIconChipSize = 38.dp
private val HomeRecentIconGlyphSize = 26.dp
private const val HOME_RECENTS_INSERT_ANIM_DURATION_MS = 360
private const val HOME_RECENTS_PROMOTE_INITIAL_EXPAND_FRACTION = 0.9f
private const val HOME_INTRO_BASE_DELAY_MS = 0L
private const val HOME_INTRO_STAGGER_MS = 34L
private const val HOME_INTRO_ANIM_DURATION_MS = 240
private data class RecentAnimationState(
    val insertedKeys: Set<String>,
    val promotedTopKey: String?
)

internal data class RecentTrackDisplay(
    val primaryText: String,
    val includeFilenameInSubtitle: Boolean
)

internal enum class SourceEntryAction {
    DeleteFromRecents,
    ShareFile,
    CopySource,
    OpenInBrowser
}

internal enum class FolderEntryAction {
    DeleteFromRecents,
    CopyPath,
    OpenInBrowser
}

private enum class HomeBulkClearTarget {
    Pinned,
    RecentFolders,
    RecentPlayed
}

internal fun buildRecentTrackDisplay(
    title: String,
    artist: String,
    fallback: String
): RecentTrackDisplay {
    return when {
        title.isNotBlank() && artist.isNotBlank() -> {
            RecentTrackDisplay(
                primaryText = "$artist - $title",
                includeFilenameInSubtitle = true
            )
        }
        title.isNotBlank() -> {
            RecentTrackDisplay(
                primaryText = "(unknown) - $title",
                includeFilenameInSubtitle = true
            )
        }
        artist.isNotBlank() -> {
            RecentTrackDisplay(
                primaryText = fallback,
                includeFilenameInSubtitle = false
            )
        }
        else -> {
            RecentTrackDisplay(
                primaryText = fallback,
                includeFilenameInSubtitle = false
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeScreen(
    currentTrackPath: String?,
    currentTrackTitle: String,
    currentTrackArtist: String,
    pinnedHomeEntries: List<HomePinnedEntry>,
    recentFolders: List<RecentPathEntry>,
    recentPlayedFiles: List<RecentPathEntry>,
    storagePresentationForEntry: (RecentPathEntry) -> StoragePresentation,
    storagePresentationForPinnedEntry: (HomePinnedEntry) -> StoragePresentation,
    bottomContentPadding: Dp = 0.dp,
    onOpenLibrary: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenUrlOrPath: () -> Unit,
    onOpenPinnedFolder: (HomePinnedEntry) -> Unit,
    onPlayPinnedFile: (HomePinnedEntry) -> Unit,
    onOpenRecentFolder: (RecentPathEntry) -> Unit,
    onPlayRecentFile: (RecentPathEntry) -> Unit,
    onPinRecentFolder: (RecentPathEntry) -> Unit,
    onPinRecentFile: (RecentPathEntry) -> Unit,
    onPersistRecentFileMetadata: (RecentPathEntry, String, String) -> Unit,
    onPinnedFolderAction: (HomePinnedEntry, FolderEntryAction) -> Unit,
    onPinnedFileAction: (HomePinnedEntry, SourceEntryAction) -> Unit,
    onRecentFolderAction: (RecentPathEntry, FolderEntryAction) -> Unit,
    onRecentFileAction: (RecentPathEntry, SourceEntryAction) -> Unit,
    onClearPinnedEntries: () -> Unit,
    onClearRecentFolders: () -> Unit,
    onClearRecentPlayed: () -> Unit,
    canShareRecentFile: (RecentPathEntry) -> Boolean,
    canSharePinnedFile: (HomePinnedEntry) -> Boolean
) {
    val context = LocalContext.current
    var folderActionTargetEntry by remember { mutableStateOf<RecentPathEntry?>(null) }
    var fileActionTargetEntry by remember { mutableStateOf<RecentPathEntry?>(null) }
    var requestedPlayedPromoteKey by remember { mutableStateOf<String?>(null) }
    var activePlayedPromoteKey by remember { mutableStateOf<String?>(null) }
    var pinnedFolderActionTarget by remember { mutableStateOf<HomePinnedEntry?>(null) }
    var pinnedFileActionTarget by remember { mutableStateOf<HomePinnedEntry?>(null) }
    var pendingPinRecentEntry by remember { mutableStateOf<Pair<RecentPathEntry, Boolean>?>(null) }
    var pendingPinEvictionCandidate by remember { mutableStateOf<HomePinnedEntry?>(null) }
    var pinnedSectionMenuExpanded by remember { mutableStateOf(false) }
    var recentFoldersSectionMenuExpanded by remember { mutableStateOf(false) }
    var recentPlayedSectionMenuExpanded by remember { mutableStateOf(false) }
    var pendingBulkClearTarget by remember { mutableStateOf<HomeBulkClearTarget?>(null) }
    val playedEntryKey: (RecentPathEntry) -> String = { entry ->
        "${entry.locationId.orEmpty()}|${entry.path}"
    }

    val recentFolderKeys = remember(recentFolders) {
        recentFolders.map { entry ->
            "${entry.locationId.orEmpty()}|${entry.path}"
        }
    }
    val recentFolderAnimationState = rememberRecentAnimationState(recentFolderKeys)
    var runHomeIntroAnimation by rememberSaveable { mutableStateOf(true) }
    val introAnimatedItemCount = remember(recentFolders.size, recentPlayedFiles.size) {
        3 + recentFolders.size + recentPlayedFiles.size
    }

    LaunchedEffect(runHomeIntroAnimation, introAnimatedItemCount) {
        if (!runHomeIntroAnimation) return@LaunchedEffect
        val totalIntroMs = HOME_INTRO_BASE_DELAY_MS +
            (HOME_INTRO_STAGGER_MS * introAnimatedItemCount.coerceAtLeast(1)) +
            HOME_INTRO_ANIM_DURATION_MS
        delay(totalIntroMs)
        runHomeIntroAnimation = false
    }

    val recentPlayedByKey = remember(recentPlayedFiles) {
        recentPlayedFiles.associateBy(playedEntryKey)
    }
    val recentPlayedTopKey = recentPlayedFiles.firstOrNull()?.let(playedEntryKey)
    val promotedPlayedKey = remember(
        requestedPlayedPromoteKey,
        activePlayedPromoteKey,
        recentPlayedTopKey
    ) {
        activePlayedPromoteKey ?: requestedPlayedPromoteKey?.takeIf { it == recentPlayedTopKey }
    }
    val pendingPlayedPromotionEntry = remember(promotedPlayedKey, recentPlayedByKey) {
        promotedPlayedKey?.let { recentPlayedByKey[it] }
    }
    val renderedRecentPlayedFiles = remember(
        recentPlayedFiles,
        promotedPlayedKey,
        pendingPlayedPromotionEntry
    ) {
        if (promotedPlayedKey == null || pendingPlayedPromotionEntry == null) {
            recentPlayedFiles
        } else {
            buildList {
                add(pendingPlayedPromotionEntry)
                recentPlayedFiles.forEach { entry ->
                    if (playedEntryKey(entry) != promotedPlayedKey) add(entry)
                }
            }
        }
    }
    val sortedPinnedEntries = remember(pinnedHomeEntries) { sortPinnedHomeEntriesForDisplay(pinnedHomeEntries) }
    fun requestPinRecentEntry(entry: RecentPathEntry, isFolder: Boolean) {
        val preview = previewPinnedHomeEntryInsertion(
            current = pinnedHomeEntries,
            candidate = HomePinnedEntry(
                path = entry.path,
                isFolder = isFolder,
                locationId = entry.locationId,
                title = entry.title,
                artist = entry.artist,
                decoderName = entry.decoderName,
                sourceNodeId = entry.sourceNodeId,
                artworkThumbnailCacheKey = entry.artworkThumbnailCacheKey
            ),
            maxItems = PINNED_HOME_ENTRIES_LIMIT
        )
        if (preview.requiresConfirmation) {
            pendingPinRecentEntry = entry to isFolder
            pendingPinEvictionCandidate = preview.evictionCandidate
        } else {
            if (isFolder) onPinRecentFolder(entry) else onPinRecentFile(entry)
        }
    }
    val recentLiveMetadataSnapshots = remember {
        mutableStateMapOf<String, Pair<String, String>>()
    }
    val recentPersistedMetadataSnapshots = remember {
        mutableStateMapOf<String, Pair<String, String>>()
    }
    LaunchedEffect(renderedRecentPlayedFiles) {
        val validKeys = renderedRecentPlayedFiles
            .map { playedEntryKey(it) }
            .toSet()
        recentLiveMetadataSnapshots.keys
            .filterNot { it in validKeys }
            .forEach { recentLiveMetadataSnapshots.remove(it) }
    }

    LaunchedEffect(requestedPlayedPromoteKey, recentPlayedByKey) {
        val requestedKey = requestedPlayedPromoteKey ?: return@LaunchedEffect
        if (requestedKey !in recentPlayedByKey.keys) {
            requestedPlayedPromoteKey = null
            if (activePlayedPromoteKey == requestedKey) {
                activePlayedPromoteKey = null
            }
        }
    }

    LaunchedEffect(promotedPlayedKey) {
        val key = promotedPlayedKey ?: return@LaunchedEffect
        if (activePlayedPromoteKey != key) {
            activePlayedPromoteKey = key
        }
        delay(HOME_RECENTS_INSERT_ANIM_DURATION_MS.toLong())
        if (activePlayedPromoteKey == key) {
            activePlayedPromoteKey = null
        }
        if (requestedPlayedPromoteKey == key) {
            requestedPlayedPromoteKey = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .padding(bottom = bottomContentPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(96.dp)
            ) {
                AnimatedHomeIntroItem(
                    itemKey = "home_intro_files_button",
                    order = 0,
                    enabled = runHomeIntroAnimation
                ) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxSize(),
                        onClick = onOpenLibrary
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(38.dp),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Files",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Browse local folders",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(96.dp)
            ) {
                AnimatedHomeIntroItem(
                    itemKey = "home_intro_url_button",
                    order = 1,
                    enabled = runHomeIntroAnimation
                ) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxSize(),
                        onClick = onOpenUrlOrPath
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(38.dp),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = null
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "URL or path",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Open links or direct paths",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(86.dp)
        ) {
            AnimatedHomeIntroItem(
                itemKey = "home_intro_network_button",
                order = 2,
                enabled = runHomeIntroAnimation
            ) {
                ElevatedCard(
                    modifier = Modifier.fillMaxSize(),
                    onClick = onOpenNetwork
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Network",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1
                            )
                            Text(
                                text = "Open network shares and remote sources",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        if (sortedPinnedEntries.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pinned",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(onClick = { pinnedSectionMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "Pinned section actions"
                        )
                    }
                    DropdownMenu(
                        expanded = pinnedSectionMenuExpanded,
                        onDismissRequest = { pinnedSectionMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Clear all") },
                            onClick = {
                                pinnedSectionMenuExpanded = false
                                pendingBulkClearTarget = HomeBulkClearTarget.Pinned
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = HomeCardShape
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    sortedPinnedEntries.forEachIndexed { index, pinnedEntry ->
                        if (pinnedEntry.isFolder) {
                            val storagePresentation = storagePresentationForPinnedEntry(pinnedEntry)
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = { onOpenPinnedFolder(pinnedEntry) },
                                            onLongClick = {
                                                pinnedFileActionTarget = null
                                                pinnedFolderActionTarget = pinnedEntry
                                            }
                                        )
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val isSmbPinnedFolder = parseSmbSourceSpecFromInput(pinnedEntry.path) != null
                                    val isHttpPinnedFolder = parseHttpSourceSpecFromInput(pinnedEntry.path) != null
                                    RecentIconChip(
                                        icon = when {
                                            isSmbPinnedFolder -> NetworkIcons.SmbShare
                                            isHttpPinnedFolder -> NetworkIcons.WorldCode
                                            else -> Icons.Default.Folder
                                        },
                                        iconPainterResId = if (isArchiveLogicalFolderPath(pinnedEntry.path)) {
                                            R.drawable.ic_folder_zip
                                        } else {
                                            null
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = resolvedRecentFolderTitle(pinnedEntry.asRecentPathEntry()),
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = storagePresentation.icon,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = storagePresentation.label,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                DropdownMenu(
                                    expanded = pinnedFolderActionTarget == pinnedEntry,
                                    onDismissRequest = { pinnedFolderActionTarget = null }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Open location") },
                                        onClick = {
                                            onPinnedFolderAction(pinnedEntry, FolderEntryAction.OpenInBrowser)
                                            pinnedFolderActionTarget = null
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Unpin folder") },
                                        onClick = {
                                            onPinnedFolderAction(
                                                pinnedEntry,
                                                FolderEntryAction.DeleteFromRecents
                                            )
                                            pinnedFolderActionTarget = null
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Copy path") },
                                        onClick = {
                                            onPinnedFolderAction(pinnedEntry, FolderEntryAction.CopyPath)
                                            pinnedFolderActionTarget = null
                                        }
                                    )
                                }
                            }
                        } else {
                            val recentEntry = pinnedEntry.asRecentPathEntry()
                            val archiveSource = parseArchiveSourceId(recentEntry.path)
                            val trackFile = if (archiveSource != null) {
                                File(archiveSource.entryPath)
                            } else {
                                val normalizedSourcePath = normalizeSourceIdentity(recentEntry.path) ?: recentEntry.path
                                val parsedSource = Uri.parse(normalizedSourcePath)
                                if (parsedSource.scheme.equals("file", ignoreCase = true)) {
                                    File(parsedSource.path ?: normalizedSourcePath)
                                } else if (!parsedSource.scheme.isNullOrBlank()) {
                                    val decodedLeaf = sourceLeafNameForDisplay(normalizedSourcePath)
                                        ?.trim()
                                        ?.takeIf { it.isNotBlank() }
                                    File(decodedLeaf ?: normalizedSourcePath)
                                } else {
                                    File(normalizedSourcePath)
                                }
                            }
                            val storagePresentation = storagePresentationForPinnedEntry(pinnedEntry)
                            val extensionLabel = inferredPrimaryExtensionForName(trackFile.name)?.uppercase()
                                ?: "UNKNOWN"
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = { onPlayPinnedFile(pinnedEntry) },
                                            onLongClick = {
                                                pinnedFolderActionTarget = null
                                                pinnedFileActionTarget = pinnedEntry
                                            }
                                        )
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RecentTrackArtworkChip(
                                        context = context,
                                        artworkThumbnailCacheKey = pinnedEntry.artworkThumbnailCacheKey,
                                        fallbackIcon = placeholderArtworkIconForFile(
                                            file = trackFile,
                                            decoderName = pinnedEntry.decoderName,
                                            allowCurrentDecoderFallback = false
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        RecentTrackSummaryText(
                                            file = trackFile,
                                            cachedTitle = pinnedEntry.title.orEmpty(),
                                            cachedArtist = pinnedEntry.artist.orEmpty(),
                                            storagePresentation = storagePresentation,
                                            extensionLabel = extensionLabel,
                                            isArchiveSource = archiveSource != null
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = pinnedFileActionTarget == pinnedEntry,
                                    onDismissRequest = { pinnedFileActionTarget = null }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Open location") },
                                        onClick = {
                                            onPinnedFileAction(pinnedEntry, SourceEntryAction.OpenInBrowser)
                                            pinnedFileActionTarget = null
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Unpin file") },
                                        onClick = {
                                            onPinnedFileAction(
                                                pinnedEntry,
                                                SourceEntryAction.DeleteFromRecents
                                            )
                                            pinnedFileActionTarget = null
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share file") },
                                        onClick = {
                                            onPinnedFileAction(pinnedEntry, SourceEntryAction.ShareFile)
                                            pinnedFileActionTarget = null
                                        },
                                        enabled = canSharePinnedFile(pinnedEntry)
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Copy URL/path") },
                                        onClick = {
                                            onPinnedFileAction(pinnedEntry, SourceEntryAction.CopySource)
                                            pinnedFileActionTarget = null
                                        }
                                    )
                                }
                            }
                        }
                        if (index < sortedPinnedEntries.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 64.dp, end = 14.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )
                        }
                    }
                }
            }
        }
        if (recentFolders.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent folders",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(onClick = { recentFoldersSectionMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "Recent folders section actions"
                        )
                    }
                    DropdownMenu(
                        expanded = recentFoldersSectionMenuExpanded,
                        onDismissRequest = { recentFoldersSectionMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Clear all") },
                            onClick = {
                                recentFoldersSectionMenuExpanded = false
                                pendingBulkClearTarget = HomeBulkClearTarget.RecentFolders
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = HomeCardShape
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    recentFolders.forEachIndexed { index, entry ->
                        val itemKey = "${entry.locationId.orEmpty()}|${entry.path}"
                        AnimatedHomeIntroItem(
                            itemKey = "home_intro_folder_$itemKey",
                            order = 3 + index,
                            enabled = runHomeIntroAnimation
                        ) {
                            AnimatedRecentCardInsertion(
                                itemKey = itemKey,
                                animate = itemKey in recentFolderAnimationState.insertedKeys ||
                                    itemKey == recentFolderAnimationState.promotedTopKey
                            ) {
                                val storagePresentation = storagePresentationForEntry(entry)
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .combinedClickable(
                                                    onClick = { onOpenRecentFolder(entry) },
                                                    onLongClick = {
                                                        fileActionTargetEntry = null
                                                        folderActionTargetEntry = entry
                                                    }
                                                )
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val isSmbRecentFolder = parseSmbSourceSpecFromInput(entry.path) != null
                                            val isHttpRecentFolder = parseHttpSourceSpecFromInput(entry.path) != null
                                            RecentIconChip(
                                                icon = when {
                                                    isSmbRecentFolder -> NetworkIcons.SmbShare
                                                    isHttpRecentFolder -> NetworkIcons.WorldCode
                                                    else -> Icons.Default.Folder
                                                },
                                                iconPainterResId = if (isArchiveLogicalFolderPath(entry.path)) {
                                                    R.drawable.ic_folder_zip
                                                } else {
                                                    null
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = resolvedRecentFolderTitle(entry),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = storagePresentation.icon,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    val archiveName = parseArchiveLogicalPath(entry.path)
                                                        ?.takeIf { it.second != null }
                                                        ?.first
                                                        ?.let { sourceLeafNameForDisplay(it) }
                                                        ?.takeIf { it.isNotBlank() }
                                                    val storageSubtitle = if (archiveName != null) {
                                                        "${storagePresentation.label} • $archiveName"
                                                    } else {
                                                        storagePresentation.label
                                                    }
                                                    Text(
                                                        text = storageSubtitle,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                        DropdownMenu(
                                            expanded = folderActionTargetEntry == entry,
                                            onDismissRequest = { folderActionTargetEntry = null }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Open location") },
                                                onClick = {
                                                    onRecentFolderAction(entry, FolderEntryAction.OpenInBrowser)
                                                    folderActionTargetEntry = null
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete from recents") },
                                                onClick = {
                                                    onRecentFolderAction(entry, FolderEntryAction.DeleteFromRecents)
                                                    folderActionTargetEntry = null
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Pin folder to home") },
                                                onClick = {
                                                    requestPinRecentEntry(entry, true)
                                                    folderActionTargetEntry = null
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Copy path") },
                                                onClick = {
                                                    onRecentFolderAction(entry, FolderEntryAction.CopyPath)
                                                    folderActionTargetEntry = null
                                                }
                                            )
                                        }
                                    }
                                    if (index < recentFolders.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 64.dp, end = 14.dp),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (renderedRecentPlayedFiles.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recently played",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(onClick = { recentPlayedSectionMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "Recently played section actions"
                        )
                    }
                    DropdownMenu(
                        expanded = recentPlayedSectionMenuExpanded,
                        onDismissRequest = { recentPlayedSectionMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Clear all") },
                            onClick = {
                                recentPlayedSectionMenuExpanded = false
                                pendingBulkClearTarget = HomeBulkClearTarget.RecentPlayed
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = HomeCardShape
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    renderedRecentPlayedFiles.forEachIndexed { index, entry ->
                        val itemKey = playedEntryKey(entry)
                        val isPendingPromotedCard = index == 0 && itemKey == promotedPlayedKey
                        val animationIdentity = if (isPendingPromotedCard) {
                            "$itemKey#pending_promote"
                        } else {
                            itemKey
                        }
                        key(animationIdentity) {
                            AnimatedHomeIntroItem(
                                itemKey = "home_intro_played_$animationIdentity",
                                order = 3 + recentFolders.size + index,
                                enabled = runHomeIntroAnimation
                            ) {
                                AnimatedRecentCardInsertion(
                                    itemKey = animationIdentity,
                                    animate = isPendingPromotedCard,
                                    initialExpandFraction = if (isPendingPromotedCard) {
                                        HOME_RECENTS_PROMOTE_INITIAL_EXPAND_FRACTION
                                    } else {
                                        0f
                                    }
                                ) {
                                    val archiveSource = parseArchiveSourceId(entry.path)
                                    val trackFile = if (archiveSource != null) {
                                        File(archiveSource.entryPath)
                                    } else {
                                        val normalizedSourcePath =
                                            normalizeSourceIdentity(entry.path) ?: entry.path
                                        val parsedSource = Uri.parse(normalizedSourcePath)
                                        if (parsedSource.scheme.equals("file", ignoreCase = true)) {
                                            File(parsedSource.path ?: normalizedSourcePath)
                                        } else if (!parsedSource.scheme.isNullOrBlank()) {
                                            val decodedLeaf = sourceLeafNameForDisplay(normalizedSourcePath)
                                                ?.trim()
                                                ?.takeIf { it.isNotBlank() }
                                            File(decodedLeaf ?: normalizedSourcePath)
                                        } else {
                                            File(normalizedSourcePath)
                                        }
                                    }
                                    val storagePresentation = storagePresentationForEntry(entry)
                                    val extensionLabel =
                                        inferredPrimaryExtensionForName(trackFile.name)?.uppercase() ?: "UNKNOWN"
                                    val useLiveMetadata = index == 0 && samePath(currentTrackPath, entry.path)
                                    val liveTitle = currentTrackTitle.trim()
                                    val liveArtist = currentTrackArtist.trim()
                                    val liveMetadataReady = liveTitle.isNotBlank() || liveArtist.isNotBlank()
                                    LaunchedEffect(itemKey, useLiveMetadata, liveTitle, liveArtist) {
                                        if (useLiveMetadata && liveMetadataReady) {
                                            recentLiveMetadataSnapshots[itemKey] = liveTitle to liveArtist
                                        }
                                    }
                                    var allowLiveMetadataSwap by remember(itemKey, useLiveMetadata) {
                                        mutableStateOf(!useLiveMetadata)
                                    }
                                    LaunchedEffect(itemKey, useLiveMetadata) {
                                        if (!useLiveMetadata) {
                                            allowLiveMetadataSwap = true
                                            return@LaunchedEffect
                                        }
                                        allowLiveMetadataSwap = false
                                        delay(HOME_RECENTS_INSERT_ANIM_DURATION_MS.toLong())
                                        allowLiveMetadataSwap = true
                                    }
                                    LaunchedEffect(
                                        itemKey,
                                        useLiveMetadata,
                                        allowLiveMetadataSwap,
                                        liveTitle,
                                        liveArtist,
                                        entry.path,
                                        entry.locationId
                                    ) {
                                        if (!useLiveMetadata || !allowLiveMetadataSwap || !liveMetadataReady) {
                                            return@LaunchedEffect
                                        }
                                        val normalizedLiveTitle = liveTitle.trim()
                                        val normalizedLiveArtist = liveArtist.trim()
                                        val persistedSignature = normalizedLiveTitle to normalizedLiveArtist
                                        if (recentPersistedMetadataSnapshots[itemKey] == persistedSignature) {
                                            return@LaunchedEffect
                                        }
                                        recentPersistedMetadataSnapshots[itemKey] = persistedSignature
                                        onPersistRecentFileMetadata(
                                            entry,
                                            normalizedLiveTitle,
                                            normalizedLiveArtist
                                        )
                                    }
                                    val targetDisplayMetadata = if (
                                        useLiveMetadata &&
                                        allowLiveMetadataSwap &&
                                        liveMetadataReady
                                    ) {
                                        liveTitle to liveArtist
                                    } else {
                                        recentLiveMetadataSnapshots[itemKey]
                                            ?: (entry.title.orEmpty() to entry.artist.orEmpty())
                                    }
                                    val fallbackIcon = placeholderArtworkIconForFile(
                                        file = trackFile,
                                        decoderName = entry.decoderName,
                                        allowCurrentDecoderFallback = false
                                    )
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .combinedClickable(
                                                        onClick = {
                                                            activePlayedPromoteKey = null
                                                            requestedPlayedPromoteKey = itemKey
                                                            onPlayRecentFile(entry)
                                                        },
                                                        onLongClick = {
                                                            folderActionTargetEntry = null
                                                            fileActionTargetEntry = entry
                                                        }
                                                    )
                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RecentTrackArtworkChip(
                                                    context = context,
                                                    artworkThumbnailCacheKey = entry.artworkThumbnailCacheKey,
                                                    fallbackIcon = fallbackIcon
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    RecentTrackSummaryText(
                                                        file = trackFile,
                                                        cachedTitle = targetDisplayMetadata.first,
                                                        cachedArtist = targetDisplayMetadata.second,
                                                        storagePresentation = storagePresentation,
                                                        extensionLabel = extensionLabel,
                                                        isArchiveSource = archiveSource != null
                                                    )
                                                }
                                                if (useLiveMetadata) {
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = "Playing",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            DropdownMenu(
                                                expanded = fileActionTargetEntry == entry,
                                                onDismissRequest = { fileActionTargetEntry = null }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Open location") },
                                                    onClick = {
                                                        onRecentFileAction(
                                                            entry,
                                                            SourceEntryAction.OpenInBrowser
                                                        )
                                                        fileActionTargetEntry = null
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Delete from recents") },
                                                    onClick = {
                                                        onRecentFileAction(
                                                            entry,
                                                            SourceEntryAction.DeleteFromRecents
                                                        )
                                                        fileActionTargetEntry = null
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Pin file to home") },
                                                    onClick = {
                                                        requestPinRecentEntry(entry, false)
                                                        fileActionTargetEntry = null
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Share file") },
                                                    onClick = {
                                                        onRecentFileAction(entry, SourceEntryAction.ShareFile)
                                                        fileActionTargetEntry = null
                                                    },
                                                    enabled = canShareRecentFile(entry)
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Copy URL/path") },
                                                    onClick = {
                                                        onRecentFileAction(entry, SourceEntryAction.CopySource)
                                                        fileActionTargetEntry = null
                                                    }
                                                )
                                            }
                                        }
                                        if (index < renderedRecentPlayedFiles.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(start = 64.dp, end = 14.dp),
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                            )
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
    pendingPinRecentEntry?.let { (entry, isFolder) ->
        AlertDialog(
            onDismissRequest = {
                pendingPinRecentEntry = null
                pendingPinEvictionCandidate = null
            },
            title = { Text("Pin limit reached") },
            text = {
                Text(
                    text = buildString {
                        append("You can pin up to $PINNED_HOME_ENTRIES_LIMIT entries. ")
                        val eviction = pendingPinEvictionCandidate
                        if (eviction != null) {
                            append("The oldest pinned ")
                            append(if (eviction.isFolder) "folder" else "file")
                            append(" will be removed to make space.")
                        } else {
                            append("The oldest pinned entry will be removed to make space.")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        if (isFolder) onPinRecentFolder(entry) else onPinRecentFile(entry)
                        pendingPinRecentEntry = null
                        pendingPinEvictionCandidate = null
                    }
                ) { Text("Continue") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        pendingPinRecentEntry = null
                        pendingPinEvictionCandidate = null
                    }
                ) { Text("Cancel") }
            }
        )
    }
    pendingBulkClearTarget?.let { target ->
        val (title, message) = when (target) {
            HomeBulkClearTarget.Pinned -> {
                "Clear pinned entries?" to
                    "This will unpin all songs and locations from Home."
            }
            HomeBulkClearTarget.RecentFolders -> {
                "Clear recent folders?" to
                    "This will remove all entries from the Recent folders section."
            }
            HomeBulkClearTarget.RecentPlayed -> {
                "Clear recently played?" to
                    "This will remove all entries from the Recently played section."
            }
        }
        AlertDialog(
            onDismissRequest = { pendingBulkClearTarget = null },
            title = { Text(title) },
            text = {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (target) {
                            HomeBulkClearTarget.Pinned -> {
                                onClearPinnedEntries()
                            }
                            HomeBulkClearTarget.RecentFolders -> {
                                onClearRecentFolders()
                            }
                            HomeBulkClearTarget.RecentPlayed -> {
                                onClearRecentPlayed()
                                activePlayedPromoteKey = null
                                requestedPlayedPromoteKey = null
                            }
                        }
                        folderActionTargetEntry = null
                        fileActionTargetEntry = null
                        pinnedFolderActionTarget = null
                        pinnedFileActionTarget = null
                        pendingBulkClearTarget = null
                    }
                ) { Text("Clear all") }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingBulkClearTarget = null }
                ) { Text("Cancel") }
            }
        )
    }

}

private fun resolvedRecentFolderTitle(entry: RecentPathEntry): String {
    val fallback = folderTitleForDisplay(entry.path)
    val title = entry.title?.trim().takeUnless { it.isNullOrBlank() } ?: return fallback
    val smbSpec = parseSmbSourceSpecFromInput(entry.path)
    if (smbSpec != null) {
        val isHostRoot = smbSpec.share.isBlank() && smbSpec.path.isNullOrBlank()
        return if (isHostRoot) title else fallback
    }
    val httpSpec = parseHttpSourceSpecFromInput(entry.path)
    if (httpSpec != null) {
        val isRoot = normalizeHttpPath(httpSpec.path) == "/"
        return if (isRoot) title else fallback
    }
    return fallback
}

@Composable
private fun RecentIconChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconPainterResId: Int? = null
) {
    Box(
        modifier = Modifier
            .size(HomeRecentIconChipSize)
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = HomeRecentIconChipShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (iconPainterResId != null) {
            Icon(
                painter = painterResource(id = iconPainterResId),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(HomeRecentIconGlyphSize)
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(HomeRecentIconGlyphSize)
            )
        }
    }
}

@Composable
private fun RecentTrackArtworkChip(
    context: android.content.Context,
    artworkThumbnailCacheKey: String?,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val artwork = androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = artworkThumbnailCacheKey
    ) {
        value = withContext(Dispatchers.IO) {
            val artworkFile = recentArtworkThumbnailFile(context, artworkThumbnailCacheKey)
                ?: return@withContext null
            BitmapFactory.decodeFile(artworkFile.absolutePath)?.asImageBitmap()
        }
    }.value
    Box(
        modifier = Modifier
            .size(HomeRecentIconChipSize)
            .clip(HomeRecentIconChipShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (artwork != null) {
            Image(
                bitmap = artwork,
                contentDescription = "Album artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(HomeRecentIconGlyphSize)
            )
        }
    }
}

@Composable
private fun AnimatedHomeIntroItem(
    itemKey: String,
    order: Int,
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    val hiddenAlpha = if (order <= 1) 0.52f else 0f
    val hiddenOffsetPx = with(LocalDensity.current) { 12.dp.toPx() }
    var targetProgress by remember(itemKey, order, enabled) {
        mutableStateOf(if (enabled) 0f else 1f)
    }
    LaunchedEffect(itemKey, order, enabled) {
        if (!enabled) {
            targetProgress = 1f
            return@LaunchedEffect
        }
        targetProgress = 0f
        val clampedOrder = order.coerceAtLeast(0)
        if (clampedOrder > 0) {
            delay(HOME_INTRO_BASE_DELAY_MS + (HOME_INTRO_STAGGER_MS * clampedOrder))
        }
        targetProgress = 1f
    }

    val progress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(
            durationMillis = HOME_INTRO_ANIM_DURATION_MS.toInt(),
            easing = FastOutSlowInEasing
        ),
        label = "homeIntroProgress"
    )

    Box(
        modifier = Modifier.graphicsLayer {
            alpha = hiddenAlpha + ((1f - hiddenAlpha) * progress)
            translationY = (1f - progress) * hiddenOffsetPx
        }
    ) {
        content()
    }
}

@Composable
private fun rememberRecentAnimationState(currentKeys: List<String>): RecentAnimationState {
    var previousKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    var initialized by remember { mutableStateOf(false) }
    var activeInsertedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var activePromotedTopKey by remember { mutableStateOf<String?>(null) }
    var animationGeneration by remember { mutableIntStateOf(0) }
    val currentSet = remember(currentKeys) { currentKeys.toSet() }
    val previousSet = remember(previousKeys) { previousKeys.toSet() }
    val previousTopKey = previousKeys.firstOrNull()
    val currentTopKey = currentKeys.firstOrNull()

    val insertedNow = remember(currentSet, previousSet, initialized) {
        if (!initialized) emptySet() else (currentSet - previousSet)
    }
    val promotedTopKeyNow = remember(currentTopKey, previousTopKey, initialized) {
        if (!initialized) null
        else if (currentTopKey != null && currentTopKey != previousTopKey) currentTopKey
        else null
    }

    val effectiveInsertedKeys = remember(insertedNow, activeInsertedKeys) {
        if (insertedNow.isNotEmpty()) insertedNow else activeInsertedKeys
    }
    val effectivePromotedTopKey = remember(promotedTopKeyNow, activePromotedTopKey) {
        promotedTopKeyNow ?: activePromotedTopKey
    }

    LaunchedEffect(insertedNow, promotedTopKeyNow, initialized) {
        if (!initialized) {
            return@LaunchedEffect
        }
        if (insertedNow.isEmpty() && promotedTopKeyNow == null) {
            return@LaunchedEffect
        }

        val generation = animationGeneration + 1
        animationGeneration = generation
        activeInsertedKeys = insertedNow
        activePromotedTopKey = promotedTopKeyNow

        // Hold animation flags for the full enter duration so recompositions
        // (like metadata refreshes) don't cancel the pop-in early.
        delay(HOME_RECENTS_INSERT_ANIM_DURATION_MS.toLong())
        if (animationGeneration == generation) {
            activeInsertedKeys = emptySet()
            activePromotedTopKey = null
        }
    }

    SideEffect {
        if (!initialized) initialized = true
        previousKeys = currentKeys
    }

    return RecentAnimationState(
        insertedKeys = effectiveInsertedKeys,
        promotedTopKey = effectivePromotedTopKey
    )
}

@Composable
private fun AnimatedRecentCardInsertion(
    itemKey: String,
    animate: Boolean,
    expandLayoutOnEnter: Boolean = true,
    initialExpandFraction: Float = 0f,
    content: @Composable () -> Unit
) {
    if (expandLayoutOnEnter) {
        val normalizedInitialExpand = initialExpandFraction.coerceIn(0f, 1f)
        val visibleState = remember(itemKey, animate) {
            MutableTransitionState(!animate).apply { targetState = true }
        }

        AnimatedVisibility(
            visibleState = visibleState,
            enter = expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = tween(
                    durationMillis = HOME_RECENTS_INSERT_ANIM_DURATION_MS,
                    easing = FastOutSlowInEasing
                ),
                initialHeight = { fullHeight ->
                    (fullHeight * normalizedInitialExpand).toInt()
                }
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = HOME_RECENTS_INSERT_ANIM_DURATION_MS,
                    easing = FastOutSlowInEasing
                )
            ) + scaleIn(
                initialScale = 0.94f,
                animationSpec = tween(
                    durationMillis = HOME_RECENTS_INSERT_ANIM_DURATION_MS,
                    easing = FastOutSlowInEasing
                )
            )
        ) {
            content()
        }
    } else {
        var targetProgress by remember(itemKey, animate) {
            mutableStateOf(if (animate) 0f else 1f)
        }
        val enterOffsetPx = with(LocalDensity.current) { 18.dp.toPx() }
        LaunchedEffect(itemKey, animate) {
            if (!animate) {
                targetProgress = 1f
                return@LaunchedEffect
            }
            targetProgress = 0f
            withFrameNanos { }
            targetProgress = 1f
        }

        val progress by animateFloatAsState(
            targetValue = targetProgress,
            animationSpec = tween(
                durationMillis = HOME_RECENTS_INSERT_ANIM_DURATION_MS,
                easing = FastOutSlowInEasing
            ),
            label = "recentCardPopIn"
        )
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = progress
                translationX = (1f - progress) * enterOffsetPx
                val scale = 0.96f + (0.04f * progress)
                scaleX = scale
                scaleY = scale
            }
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RecentTrackSummaryText(
    file: File,
    cachedTitle: String?,
    cachedArtist: String?,
    storagePresentation: StoragePresentation,
    extensionLabel: String,
    isArchiveSource: Boolean
) {
    val fallback = inferredDisplayTitleForName(file.name)
    val display = remember(file.absolutePath, cachedTitle, cachedArtist) {
        buildRecentTrackDisplay(
            title = cachedTitle?.trim().orEmpty(),
            artist = cachedArtist?.trim().orEmpty(),
            fallback = fallback
        )
    }
    var renderedDisplay by remember(file.absolutePath) { mutableStateOf(display) }
    val metadataAlpha = remember(file.absolutePath) { Animatable(1f) }

    LaunchedEffect(display) {
        if (display == renderedDisplay) return@LaunchedEffect
        metadataAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing)
        )
        renderedDisplay = display
        metadataAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)
        )
    }

    Text(
        text = renderedDisplay.primaryText,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.graphicsLayer { alpha = metadataAlpha.value }
    )
    val isNetworkSource = storagePresentation.icon == Icons.Default.Public ||
        storagePresentation.icon == NetworkIcons.WorldCode ||
        storagePresentation.icon == NetworkIcons.SmbShare
    val subtitleText = buildAnnotatedString {
        if (isNetworkSource) {
            appendBoldSourceTypeToken(storagePresentation.label)
        } else {
            append(storagePresentation.label)
        }
        storagePresentation.qualifier?.takeIf { it.isNotBlank() }?.let {
            append(" • ")
            append(it)
        }
        append(" • ")
        append(extensionLabel)
        if (renderedDisplay.includeFilenameInSubtitle) {
            append(" • ")
            append(fallback)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isArchiveSource) {
            Icon(
                painter = painterResource(id = R.drawable.ic_folder_zip),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        } else {
            Icon(
                imageVector = storagePresentation.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .clipToBounds()
                .graphicsLayer { alpha = metadataAlpha.value }
        ) {
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendBoldSourceTypeToken(label: String) {
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
