package com.flopster101.siliconplayer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal data class CacheManagerSettingsRouteState(
    val route: SettingsRoute,
    val cachedSourceFiles: List<CachedSourceFile>
)

internal data class CacheManagerSettingsRouteActions(
    val onRefreshCachedSourceFiles: () -> Unit,
    val onDeleteCachedSourceFiles: (List<String>) -> Unit,
    val onExportCachedSourceFiles: (List<String>) -> Unit
)

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun CacheManagerSettingsRouteContent(
    state: CacheManagerSettingsRouteState,
    actions: CacheManagerSettingsRouteActions
) {
    val route = state.route
    val cachedSourceFiles = state.cachedSourceFiles
    val onRefreshCachedSourceFiles = actions.onRefreshCachedSourceFiles
    val onDeleteCachedSourceFiles = actions.onDeleteCachedSourceFiles
    val onExportCachedSourceFiles = actions.onExportCachedSourceFiles

    var selectedPaths by remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(cachedSourceFiles) {
        val existing = cachedSourceFiles.map { it.absolutePath }.toSet()
        selectedPaths = selectedPaths.filterTo(mutableSetOf()) { it in existing }
    }
    LaunchedEffect(route) {
        onRefreshCachedSourceFiles()
    }

    val inSelectionMode = selectedPaths.isNotEmpty()
    val totalBytes = cachedSourceFiles.sumOf { it.sizeBytes.coerceAtLeast(0L) }
    BackHandler(enabled = inSelectionMode) {
        selectedPaths = emptySet()
    }

    SettingsSectionLabel("Overview")
    Text(
        text = "${cachedSourceFiles.size} files • ${formatCacheByteCount(totalBytes)}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = { showDeleteConfirmDialog = true },
            enabled = inSelectionMode,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Delete (${selectedPaths.size})")
        }
        OutlinedButton(
            onClick = { onExportCachedSourceFiles(selectedPaths.toList()) },
            enabled = inSelectionMode,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Link,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Export (${selectedPaths.size})")
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = {
                selectedPaths = cachedSourceFiles.map { it.absolutePath }.toSet()
            },
            enabled = cachedSourceFiles.isNotEmpty(),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Select all")
        }
        OutlinedButton(
            onClick = { selectedPaths = emptySet() },
            enabled = inSelectionMode,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Deselect all")
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "Tap files to select. Back clears selection first.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(12.dp))

    if (cachedSourceFiles.isEmpty()) {
        Text(
            text = "No cached files.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        SettingsSectionLabel("Files")
        cachedSourceFiles.forEach { entry ->
            val isSelected = selectedPaths.contains(entry.absolutePath)
            val sourceLine = entry.sourceId ?: "Source: unknown"
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onLongClick = {
                            selectedPaths = if (isSelected) {
                                selectedPaths - entry.absolutePath
                            } else {
                                selectedPaths + entry.absolutePath
                            }
                        },
                        onClick = {
                            if (selectedPaths.isEmpty()) {
                                selectedPaths = setOf(entry.absolutePath)
                            } else {
                                selectedPaths = if (isSelected) {
                                    selectedPaths - entry.absolutePath
                                } else {
                                    selectedPaths + entry.absolutePath
                                }
                            }
                        }
                    )
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = stripCachedFileHashPrefix(entry.fileName),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "${formatCacheByteCount(entry.sizeBytes)} • $sourceLine",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }

    if (showDeleteConfirmDialog) {
        SettingsConfirmDialog(
            title = "Delete selected cached files?",
            message = "This removes ${selectedPaths.size} selected cached file(s).",
            confirmLabel = "Delete",
            onDismiss = { showDeleteConfirmDialog = false },
            onConfirm = {
                onDeleteCachedSourceFiles(selectedPaths.toList())
                selectedPaths = emptySet()
            }
        )
    }
}
