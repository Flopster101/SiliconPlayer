package com.flopster101.siliconplayer.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AudioEffectsDialog(
    masterVolumeDb: Float,
    pluginVolumeDb: Float,
    songVolumeDb: Float,
    forceMono: Boolean,
    hasActiveCore: Boolean,
    hasActiveSong: Boolean,
    currentCoreName: String?,
    onMasterVolumeChange: (Float) -> Unit,
    onPluginVolumeChange: (Float) -> Unit,
    onSongVolumeChange: (Float) -> Unit,
    onForceMonoChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio effects") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    VolumeSliderRow(
                        label = "Master Volume",
                        valueDb = masterVolumeDb,
                        onValueChange = onMasterVolumeChange
                    )

                    VolumeSliderRow(
                        label = "Plugin Volume",
                        valueDb = pluginVolumeDb,
                        onValueChange = onPluginVolumeChange,
                        enabled = hasActiveCore
                    )

                    VolumeSliderRow(
                        label = "Song Volume",
                        valueDb = songVolumeDb,
                        onValueChange = onSongVolumeChange,
                        enabled = hasActiveSong
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Force Mono", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = forceMono,
                            onCheckedChange = onForceMonoChange
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Core: ${currentCoreName ?: "(none)"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("OK")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onReset) {
                    Text("Reset")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
private fun VolumeSliderRow(
    label: String,
    valueDb: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true
) {
    val contentAlpha = if (enabled) 1f else 0.38f

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
            Text(
                text = String.format("%.1f dB", valueDb),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onValueChange((valueDb - 1f).coerceIn(-20f, 20f)) },
                modifier = Modifier.size(36.dp),
                enabled = enabled
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease $label"
                )
            }

            Slider(
                value = valueDb,
                onValueChange = onValueChange,
                valueRange = -20f..20f,
                steps = 39,
                modifier = Modifier.weight(1f),
                enabled = enabled
            )

            IconButton(
                onClick = { onValueChange((valueDb + 1f).coerceIn(-20f, 20f)) },
                modifier = Modifier.size(36.dp),
                enabled = enabled
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase $label"
                )
            }
        }
    }
}
