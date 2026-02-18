package com.flopster101.siliconplayer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun AboutSettingsBody() {
    val context = LocalContext.current
    val versionLabel = "v${BuildConfig.VERSION_NAME}-${BuildConfig.GIT_SHA}"
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        androidx.compose.material3.ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Silicon Player",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = versionLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        androidx.compose.material3.ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Android music player focused on mainstream and tracker/module formats.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = "UI: Jetpack Compose Material 3",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "License: GPL v3",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.size(14.dp))
                Button(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/Flopster101/SiliconPlayer")
                        )
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Open GitHub Repository")
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@Composable
internal fun ClearAudioParametersCard(
    onClearAll: () -> Unit,
    onClearPlugins: () -> Unit,
    onClearSongs: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        onClick = { showDialog = true }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Clear saved parameters",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = "Reset volume settings for all, cores, or songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showDialog) {
        SettingsActionListDialog(
            title = "Clear saved parameters",
            message = "Choose which audio parameters to reset:",
            actions = listOf(
                SettingsActionDialogItem("Clear all", onClearAll),
                SettingsActionDialogItem("Clear core volumes", onClearPlugins),
                SettingsActionDialogItem("Clear song volumes", onClearSongs)
            ),
            onDismiss = { showDialog = false }
        )
    }
}
