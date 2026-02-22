package com.flopster101.siliconplayer.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.RemoteLoadPhase
import com.flopster101.siliconplayer.RemoteLoadUiState
import com.flopster101.siliconplayer.SubtuneEntry
import com.flopster101.siliconplayer.adaptiveDialogModifier
import com.flopster101.siliconplayer.adaptiveDialogProperties
import com.flopster101.siliconplayer.formatByteCount
import com.flopster101.siliconplayer.formatShortDuration

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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    subtuneEntries.forEach { entry ->
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
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
