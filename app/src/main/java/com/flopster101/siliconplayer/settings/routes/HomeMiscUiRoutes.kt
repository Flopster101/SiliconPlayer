package com.flopster101.siliconplayer

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
@Composable
internal fun HomeRouteContent(
    recentFoldersLimit: Int,
    onRecentFoldersLimitChanged: (Int) -> Unit,
    recentFilesLimit: Int,
    onRecentFilesLimitChanged: (Int) -> Unit
) {
    var showFolderLimitDialog by remember { mutableStateOf(false) }
    var showFileLimitDialog by remember { mutableStateOf(false) }

    SettingsSectionLabel("Recents")
    SettingsItemCard(
        title = "Recent folders limit",
        description = "$recentFoldersLimit folders",
        icon = Icons.Default.Folder,
        onClick = { showFolderLimitDialog = true }
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsItemCard(
        title = "Recent files limit",
        description = "$recentFilesLimit files",
        icon = Icons.Default.MusicNote,
        onClick = { showFileLimitDialog = true }
    )

    if (showFolderLimitDialog) {
        var input by remember { mutableStateOf(recentFoldersLimit.toString()) }
        AlertDialog(
            onDismissRequest = { showFolderLimitDialog = false },
            title = { Text("Recent folders limit") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { value ->
                        input = value.filter { it.isDigit() }.take(2)
                    },
                    singleLine = true,
                    label = { Text("Folders") },
                    supportingText = { Text("1-50 (default 3)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            dismissButton = {
                TextButton(onClick = { showFolderLimitDialog = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = input.trim().toIntOrNull()
                    if (parsed != null && parsed in 1..50) {
                        onRecentFoldersLimitChanged(parsed)
                        showFolderLimitDialog = false
                    }
                }) {
                    Text("Save")
                }
            }
        )
    }

    if (showFileLimitDialog) {
        var input by remember { mutableStateOf(recentFilesLimit.toString()) }
        AlertDialog(
            onDismissRequest = { showFileLimitDialog = false },
            title = { Text("Recent files limit") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { value ->
                        input = value.filter { it.isDigit() }.take(2)
                    },
                    singleLine = true,
                    label = { Text("Files") },
                    supportingText = { Text("1-50 (default 5)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            dismissButton = {
                TextButton(onClick = { showFileLimitDialog = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = input.trim().toIntOrNull()
                    if (parsed != null && parsed in 1..50) {
                        onRecentFilesLimitChanged(parsed)
                        showFileLimitDialog = false
                    }
                }) {
                    Text("Save")
                }
            }
        )
    }
}

@Composable
internal fun MiscRouteContent(
    rememberBrowserLocation: Boolean,
    onRememberBrowserLocationChanged: (Boolean) -> Unit,
    onClearRecentHistory: () -> Unit
) {
    SettingsSectionLabel("Browser behavior")
    PlayerSettingToggleCard(
        title = "Remember browser location",
        description = "Restore last storage and folder when reopening the library browser.",
        checked = rememberBrowserLocation,
        onCheckedChange = onRememberBrowserLocationChanged
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsItemCard(
        title = "Clear home recents",
        description = "Remove recent folders and recently played shortcuts from the Home screen.",
        icon = Icons.Default.MoreHoriz,
        onClick = onClearRecentHistory
    )
}

@Composable
internal fun UiRouteContent(
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit
) {
    ThemeModeSelectorCard(
        selectedMode = themeMode,
        onSelectedModeChanged = onThemeModeChanged
    )
}
