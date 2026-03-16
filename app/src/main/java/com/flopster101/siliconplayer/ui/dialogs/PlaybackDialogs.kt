package com.flopster101.siliconplayer.ui.dialogs

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.HttpSourceSpec
import com.flopster101.siliconplayer.PlaylistTrackEntry
import com.flopster101.siliconplayer.RemoteLoadPhase
import com.flopster101.siliconplayer.RemoteLoadUiState
import com.flopster101.siliconplayer.SubtuneEntry
import com.flopster101.siliconplayer.adaptiveDialogModifier
import com.flopster101.siliconplayer.adaptiveDialogProperties
import com.flopster101.siliconplayer.ensureRecentArtworkThumbnailCached
import com.flopster101.siliconplayer.formatByteCount
import com.flopster101.siliconplayer.formatShortDuration
import com.flopster101.siliconplayer.placeholderArtworkIconForFile
import com.flopster101.siliconplayer.recentArtworkThumbnailFile
import com.flopster101.siliconplayer.rememberDialogLazyListScrollbarAlpha
import com.flopster101.siliconplayer.resolvePlaylistEntryLocalFile
import com.flopster101.siliconplayer.ui.screens.BrowserLazyListScrollbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ManualSmbAuthenticationDialog(
    host: String,
    share: String,
    failureMessage: String?,
    username: String,
    password: String,
    passwordVisible: Boolean,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val hasCredentials = username.trim().isNotEmpty() || password.trim().isNotEmpty()
    AlertDialog(
        modifier = adaptiveDialogModifier(),
        properties = adaptiveDialogProperties(),
        onDismissRequest = onDismiss,
        title = { Text("SMB authentication required") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                failureMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Text(
                    text = if (share.isBlank()) host else "$host/$share",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Username") },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Password") },
                    visualTransformation = if (passwordVisible) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        androidx.compose.ui.text.input.PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { onPasswordVisibilityChange(!passwordVisible) }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = hasCredentials) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun ManualHttpAuthenticationDialog(
    requestSpec: HttpSourceSpec,
    failureMessage: String?,
    username: String,
    password: String,
    passwordVisible: Boolean,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val hasCredentials = username.trim().isNotEmpty() || password.trim().isNotEmpty()
    AlertDialog(
        modifier = adaptiveDialogModifier(),
        properties = adaptiveDialogProperties(),
        onDismissRequest = onDismiss,
        title = { Text("HTTP authentication required") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                failureMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Text(
                    text = buildString {
                        append(requestSpec.scheme)
                        append("://")
                        append(requestSpec.host)
                        requestSpec.port?.let { append(":$it") }
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Username") },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Password") },
                    visualTransformation = if (passwordVisible) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        androidx.compose.ui.text.input.PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { onPasswordVisibilityChange(!passwordVisible) }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = hasCredentials) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun UrlOrPathDialog(
    input: String,
    forceCaching: Boolean,
    onInputChange: (String) -> Unit,
    onForceCachingChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onOpen: () -> Unit
) {
    AlertDialog(
        modifier = adaptiveDialogModifier(),
        properties = adaptiveDialogProperties(),
        onDismissRequest = onDismiss,
        title = { Text("Open URL or path") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Supported: /unix/path, file:///path/to/file, http(s)://example/file")
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Path or URL") },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onForceCachingChange(!forceCaching) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = forceCaching,
                        onCheckedChange = onForceCachingChange
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Force caching",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = input.isNotBlank(),
                onClick = onOpen
            ) {
                Text("Open")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun RemoteLoadProgressDialog(
    loadState: RemoteLoadUiState,
    onCancel: () -> Unit
) {
    AlertDialog(
        modifier = adaptiveDialogModifier(),
        properties = adaptiveDialogProperties(),
        onDismissRequest = {},
        title = { Text("Opening remote source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val phaseText = when (loadState.phase) {
                    RemoteLoadPhase.Connecting -> "Connecting..."
                    RemoteLoadPhase.Downloading -> "Downloading..."
                    RemoteLoadPhase.Opening -> "Opening..."
                }
                Text(phaseText)
                if (loadState.indeterminate) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    val progress = (loadState.percent ?: 0).coerceIn(0, 100) / 100f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (loadState.phase == RemoteLoadPhase.Downloading || loadState.phase == RemoteLoadPhase.Opening) {
                    val downloadedLabel = formatByteCount(loadState.downloadedBytes)
                    val sizeLabel = loadState.totalBytes?.let { total ->
                        "$downloadedLabel / ${formatByteCount(total)}"
                    } ?: downloadedLabel
                    Text(
                        text = sizeLabel,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    loadState.percent?.let { percent ->
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    loadState.bytesPerSecond?.takeIf { it > 0L }?.let { speed ->
                        Text(
                            text = "${formatByteCount(speed)}/s",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun SoxExperimentalDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        modifier = adaptiveDialogModifier(),
        properties = adaptiveDialogProperties(),
        onDismissRequest = onDismiss,
        title = { Text("SoX is experimental") },
        text = {
            Text(
                "SoX output resampling may cause unstable timeline behavior on some content. " +
                    "For discontinuous timeline cores (like module pattern jumps/loop points), " +
                    "the engine automatically falls back to Built-in for stability."
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
internal fun SubtuneSelectorDialog(
    subtuneEntries: List<SubtuneEntry>,
    currentSubtuneIndex: Int,
    onSelectSubtune: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        modifier = adaptiveDialogModifier(),
        properties = adaptiveDialogProperties(),
        onDismissRequest = onDismiss,
        title = { Text("Subtunes") },
        text = {
            if (subtuneEntries.isEmpty()) {
                Text("No subtunes available.")
            } else {
                val listState = rememberLazyListState()
                val scrollbarAlpha = rememberDialogLazyListScrollbarAlpha(
                    enabled = subtuneEntries.size > 1,
                    listState = listState,
                    flashKey = subtuneEntries.size to currentSubtuneIndex,
                    label = "subtuneSelectorScrollbarAlpha"
                )
                val scrollbarHeld = remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(
                            count = subtuneEntries.size,
                            key = { index -> subtuneEntries[index].index }
                        ) { index ->
                            val entry = subtuneEntries[index]
                            val isCurrent = entry.index == currentSubtuneIndex
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectSubtune(entry.index) },
                                shape = MaterialTheme.shapes.medium,
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "${entry.index + 1}. ${entry.title}",
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val details = buildString {
                                        append(formatShortDuration(entry.durationSeconds))
                                        if (entry.artist.isNotBlank()) {
                                            append(" • ")
                                            append(entry.artist)
                                        }
                                    }
                                    Text(
                                        text = details,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    BrowserLazyListScrollbar(
                        listState = listState,
                        onDragActiveChanged = { isActive -> scrollbarHeld.value = isActive },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxSize()
                            .graphicsLayer(alpha = if (scrollbarHeld.value) 1f else scrollbarAlpha)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlaylistSelectorDialog(
    title: String,
    subtitle: String?,
    shuffleActive: Boolean,
    entries: List<PlaylistTrackEntry>,
    currentEntryId: String?,
    onSelectEntry: (PlaylistTrackEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val resolvedPrimaryTitle = subtitle
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: title
    val resolvedSecondaryTitle = title.takeIf { resolvedPrimaryTitle != title }
    AlertDialog(
        modifier = adaptiveDialogModifier(),
        properties = adaptiveDialogProperties(),
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = resolvedPrimaryTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee()
                )
                resolvedSecondaryTitle?.let { secondaryTitle ->
                    Text(
                        text = secondaryTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (entries.isEmpty()) {
                    Text("No playlist entries available.")
                } else {
                    val listState = rememberLazyListState()
                    val scrollbarAlpha = rememberDialogLazyListScrollbarAlpha(
                        enabled = entries.size > 1,
                        listState = listState,
                        flashKey = entries.size to currentEntryId,
                        label = "playlistSelectorScrollbarAlpha"
                    )
                    val scrollbarHeld = remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(
                                count = entries.size,
                                key = { index -> entries[index].id }
                            ) { index ->
                                val entry = entries[index]
                                val isCurrent = entry.id == currentEntryId
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(MaterialTheme.shapes.large)
                                            .background(
                                                if (isCurrent) {
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                                                } else {
                                                    MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                                                }
                                            )
                                            .clickable { onSelectEntry(entry) }
                                            .padding(horizontal = 8.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        PlaylistSelectorArtworkChip(
                                            entry = entry,
                                            isCurrent = isCurrent
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = entry.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = if (isCurrent) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = playlistSelectorSubtitle(entry),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isCurrent) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = "${index + 1}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (isCurrent) {
                                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                    if (index < entries.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 58.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                                        )
                                    }
                                }
                            }
                        }
                        BrowserLazyListScrollbar(
                            listState = listState,
                            onDragActiveChanged = { isActive -> scrollbarHeld.value = isActive },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxSize()
                                .graphicsLayer(alpha = if (scrollbarHeld.value) 1f else scrollbarAlpha)
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = if (shuffleActive) "Shuffle active" else "Shuffle inactive",
                        tint = if (shuffleActive) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

private fun playlistSelectorSubtitle(entry: PlaylistTrackEntry): String {
    val details = buildList {
        entry.artist
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(::add)
        entry.subtuneIndex?.let { add("Subtune ${it + 1}") }
        entry.album
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !contains(it) }
            ?.let(::add)
    }
    if (details.isNotEmpty()) {
        return details.joinToString(" • ")
    }
    return resolvePlaylistEntryLocalFile(entry.source)?.name ?: entry.source
}

@Composable
private fun PlaylistSelectorArtworkChip(
    entry: PlaylistTrackEntry,
    isCurrent: Boolean
) {
    val context = LocalContext.current
    val localFile = remember(entry.source) { resolvePlaylistEntryLocalFile(entry.source) }
    val fallbackIcon = placeholderArtworkIconForFile(
        file = localFile,
        decoderName = null,
        allowCurrentDecoderFallback = false
    )
    val artworkThumbnailCacheKey by produceState<String?>(
        initialValue = entry.artworkThumbnailCacheKey,
        key1 = entry.id,
        key2 = entry.source,
        key3 = entry.artworkThumbnailCacheKey
    ) {
        if (!entry.artworkThumbnailCacheKey.isNullOrBlank()) {
            value = entry.artworkThumbnailCacheKey
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            ensureRecentArtworkThumbnailCached(
                context = context,
                sourceId = entry.source
            )
        }
    }
    val artwork by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = artworkThumbnailCacheKey
    ) {
        value = withContext(Dispatchers.IO) {
            val artworkFile = recentArtworkThumbnailFile(context, artworkThumbnailCacheKey)
                ?: return@withContext null
            BitmapFactory.decodeFile(artworkFile.absolutePath)?.asImageBitmap()
        }
    }
    Surface(
        modifier = Modifier.size(46.dp),
        shape = MaterialTheme.shapes.large,
        color = if (isCurrent) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = if (isCurrent) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(28.dp)
            )
            artwork?.let { resolvedArtwork ->
                Image(
                    bitmap = resolvedArtwork,
                    contentDescription = "Album artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
internal fun PlaylistOpenActionDialog(
    title: String,
    entryCount: Int,
    onPlayNow: () -> Unit,
    onBrowseEntries: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        modifier = adaptiveDialogModifier(),
        properties = adaptiveDialogProperties(),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                text = if (entryCount == 1) {
                    "This playlist contains 1 entry. You can play it immediately or inspect its tracks first."
                } else {
                    "This playlist contains $entryCount entries. You can play from the top or inspect its tracks first."
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onPlayNow) {
                Text("Play")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBrowseEntries) {
                    Text("List tracks")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
