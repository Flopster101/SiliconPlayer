package com.flopster101.siliconplayer

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
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
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
import java.io.File
import kotlinx.coroutines.delay

private val HomeCardShape = RoundedCornerShape(16.dp)
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
    recentFolders: List<RecentPathEntry>,
    recentPlayedFiles: List<RecentPathEntry>,
    storagePresentationForEntry: (RecentPathEntry) -> StoragePresentation,
    bottomContentPadding: Dp = 0.dp,
    onOpenLibrary: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenUrlOrPath: () -> Unit,
    onOpenRecentFolder: (RecentPathEntry) -> Unit,
    onPlayRecentFile: (RecentPathEntry) -> Unit,
    onPersistRecentFileMetadata: (RecentPathEntry, String, String) -> Unit,
    onRecentFolderAction: (RecentPathEntry, FolderEntryAction) -> Unit,
    onRecentFileAction: (RecentPathEntry, SourceEntryAction) -> Unit,
    canShareRecentFile: (RecentPathEntry) -> Boolean
) {
    var folderActionTargetEntry by remember { mutableStateOf<RecentPathEntry?>(null) }
    var fileActionTargetEntry by remember { mutableStateOf<RecentPathEntry?>(null) }
    var requestedPlayedPromoteKey by remember { mutableStateOf<String?>(null) }
    var activePlayedPromoteKey by remember { mutableStateOf<String?>(null) }
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
        Text(
            text = "Select a song",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Open audio from files, URLs, or direct paths.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(18.dp))
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
        if (recentFolders.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Recent folders",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
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
                        val folderFile = File(entry.path)
                        val storagePresentation = storagePresentationForEntry(entry)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(HomeCardShape)
                                    .combinedClickable(
                                        onClick = { onOpenRecentFolder(entry) },
                                        onLongClick = {
                                            fileActionTargetEntry = null
                                            folderActionTargetEntry = entry
                                        }
                                    ),
                                shape = HomeCardShape
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isArchiveLogicalFolderPath(entry.path)) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_folder_zip),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = folderFile.name.ifBlank { entry.path },
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
                                                ?.let { File(it).name }
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
                                    text = { Text("Copy path") },
                                    onClick = {
                                        onRecentFolderAction(entry, FolderEntryAction.CopyPath)
                                        folderActionTargetEntry = null
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        if (renderedRecentPlayedFiles.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Recently played",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tip: Hold a song for more actions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
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
                                val normalizedSourcePath = normalizeSourceIdentity(entry.path) ?: entry.path
                                val parsedSource = Uri.parse(normalizedSourcePath)
                                if (parsedSource.scheme.equals("file", ignoreCase = true)) {
                                    File(parsedSource.path ?: normalizedSourcePath)
                                } else if (!parsedSource.scheme.isNullOrBlank()) {
                                    val decodedLeaf = parsedSource.lastPathSegment
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
                                onPersistRecentFileMetadata(entry, normalizedLiveTitle, normalizedLiveArtist)
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
                            Box(modifier = Modifier.fillMaxWidth()) {
                                ElevatedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(HomeCardShape)
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
                                        ),
                                    shape = HomeCardShape
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = fallbackIcon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
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
                                }
                                DropdownMenu(
                                    expanded = fileActionTargetEntry == entry,
                                    onDismissRequest = { fileActionTargetEntry = null }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Open location") },
                                        onClick = {
                                            onRecentFileAction(entry, SourceEntryAction.OpenInBrowser)
                                            fileActionTargetEntry = null
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete from recents") },
                                        onClick = {
                                            onRecentFileAction(entry, SourceEntryAction.DeleteFromRecents)
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
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
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

    val shouldMarquee = renderedDisplay.primaryText.length > 28
    if (shouldMarquee) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .graphicsLayer { alpha = metadataAlpha.value }
        ) {
            Text(
                text = renderedDisplay.primaryText,
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
            text = renderedDisplay.primaryText,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer { alpha = metadataAlpha.value }
        )
    }
    val isNetworkSource = storagePresentation.icon == Icons.Default.Public
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
    val shouldMarqueeSubtitle = subtitleText.length > 44
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
                overflow = if (shouldMarqueeSubtitle) TextOverflow.Clip else TextOverflow.Ellipsis,
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
