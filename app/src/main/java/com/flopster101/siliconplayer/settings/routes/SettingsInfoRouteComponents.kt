package com.flopster101.siliconplayer

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Link
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
    val coreEntries = remember { AboutCatalog.cores }
    val libraryEntries = remember { AboutCatalog.libraries }
    var selectedAboutEntry by remember { mutableStateOf<AboutEntity?>(null) }
    Column(
        modifier = Modifier.fillMaxWidth()
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
        Spacer(modifier = Modifier.size(8.dp))

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
        Spacer(modifier = Modifier.size(8.dp))

        Text(
            text = "Audio cores",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.size(4.dp))
        coreEntries.forEachIndexed { index, entry ->
            AboutEntityListItemCard(
                entity = entry,
                icon = Icons.Default.GraphicEq,
                onClick = { selectedAboutEntry = entry }
            )
            if (index != coreEntries.lastIndex) {
                SettingsRowSpacer()
            }
        }
        Spacer(modifier = Modifier.size(8.dp))

        Text(
            text = "Libraries",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.size(4.dp))
        libraryEntries.forEachIndexed { index, entry ->
            AboutEntityListItemCard(
                entity = entry,
                icon = Icons.Default.Link,
                onClick = { selectedAboutEntry = entry }
            )
            if (index != libraryEntries.lastIndex) {
                SettingsRowSpacer()
            }
        }
    }

    selectedAboutEntry?.let { entity ->
        AboutEntityDialog(
            entity = entity,
            onDismiss = { selectedAboutEntry = null }
        )
    }
}

@Composable
private fun AboutEntityListItemCard(
    entity: AboutEntity,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    SettingsItemCard(
        title = entity.name,
        description = "${entity.description}\nAuthor: ${entity.author}\nLicense: ${entity.license}",
        icon = icon,
        onClick = onClick
    )
}

@Composable
internal fun ClearAudioParametersCard(
    onClearAll: () -> Unit,
    onClearPlugins: () -> Unit,
    onClearSongs: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    SettingsItemCard(
        title = "Clear saved parameters",
        description = "Reset volume settings for all, cores, or songs",
        icon = Icons.Default.Delete,
        onClick = { showDialog = true }
    )

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
