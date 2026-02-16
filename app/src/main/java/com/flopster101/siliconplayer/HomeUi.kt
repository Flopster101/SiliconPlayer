package com.flopster101.siliconplayer

import android.media.MediaMetadataRetriever
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val HomeCardShape = RoundedCornerShape(16.dp)

internal data class RecentTrackDisplay(
    val primaryText: String,
    val includeFilenameInSubtitle: Boolean
)

internal enum class SourceEntryAction {
    DeleteFromRecents,
    ShareFile,
    CopySource
}

internal enum class FolderEntryAction {
    DeleteFromRecents,
    CopyPath
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
    onOpenUrlOrPath: () -> Unit,
    onOpenRecentFolder: (RecentPathEntry) -> Unit,
    onPlayRecentFile: (RecentPathEntry) -> Unit,
    onRecentFolderAction: (RecentPathEntry, FolderEntryAction) -> Unit,
    onRecentFileAction: (RecentPathEntry, SourceEntryAction) -> Unit,
    canShareRecentFile: (RecentPathEntry) -> Boolean
) {
    var folderActionTargetEntry by remember { mutableStateOf<RecentPathEntry?>(null) }
    var fileActionTargetEntry by remember { mutableStateOf<RecentPathEntry?>(null) }
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
            ElevatedCard(
                modifier = Modifier
                    .weight(1f)
                    .height(96.dp),
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
            ElevatedCard(
                modifier = Modifier
                    .weight(1f)
                    .height(96.dp),
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
        if (recentFolders.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Recent folders",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            recentFolders.forEach { entry ->
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
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
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
                    }
                    DropdownMenu(
                        expanded = folderActionTargetEntry == entry,
                        onDismissRequest = { folderActionTargetEntry = null }
                    ) {
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
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        if (recentPlayedFiles.isNotEmpty()) {
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
            recentPlayedFiles.forEach { entry ->
                val trackFile = File(entry.path)
                val storagePresentation = storagePresentationForEntry(entry)
                val extensionLabel = trackFile.extension.uppercase().ifBlank { "UNKNOWN" }
                val useLiveMetadata = samePath(currentTrackPath, entry.path)
                Box(modifier = Modifier.fillMaxWidth()) {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(HomeCardShape)
                            .combinedClickable(
                                onClick = { onPlayRecentFile(entry) },
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
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                RecentTrackSummaryText(
                                    file = trackFile,
                                    cachedTitle = if (useLiveMetadata) currentTrackTitle else entry.title,
                                    cachedArtist = if (useLiveMetadata) currentTrackArtist else entry.artist,
                                    storagePresentation = storagePresentation,
                                    extensionLabel = extensionLabel
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
                Spacer(modifier = Modifier.height(8.dp))
            }
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
    extensionLabel: String
) {
    val fallback = file.nameWithoutExtension.ifBlank { file.name }
    val display by produceState(
        initialValue = RecentTrackDisplay(
            primaryText = fallback,
            includeFilenameInSubtitle = false
        ),
        key1 = file.absolutePath,
        key2 = cachedTitle,
        key3 = cachedArtist
    ) {
        value = withContext(Dispatchers.IO) {
            val cachedTitleValue = cachedTitle?.trim().orEmpty()
            val cachedArtistValue = cachedArtist?.trim().orEmpty()
            try {
                val result = if (cachedTitleValue.isNotBlank() || cachedArtistValue.isNotBlank()) {
                    buildRecentTrackDisplay(
                        title = cachedTitleValue,
                        artist = cachedArtistValue,
                        fallback = fallback
                    )
                } else {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(file.absolutePath)
                        val title = retriever
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                            ?.trim()
                            .orEmpty()
                        val artist = retriever
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                            ?.trim()
                            .orEmpty()
                        buildRecentTrackDisplay(
                            title = title,
                            artist = artist,
                            fallback = fallback
                        )
                    } finally {
                        retriever.release()
                    }
                }
                result
            } catch (_: Exception) {
                RecentTrackDisplay(
                    primaryText = fallback,
                    includeFilenameInSubtitle = false
                )
            }
        }
    }
    val shouldMarquee = display.primaryText.length > 28
    if (shouldMarquee) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
        ) {
            Text(
                text = display.primaryText,
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
            text = display.primaryText,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    val subtitleText = buildString {
        append(storagePresentation.label)
        storagePresentation.qualifier?.takeIf { it.isNotBlank() }?.let {
            append(" • ")
            append(it)
        }
        append(" • ")
        append(extensionLabel)
        if (display.includeFilenameInSubtitle) {
            append(" • ")
            append(fallback)
        }
    }
    val shouldMarqueeSubtitle = subtitleText.length > 44
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = storagePresentation.icon,
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
