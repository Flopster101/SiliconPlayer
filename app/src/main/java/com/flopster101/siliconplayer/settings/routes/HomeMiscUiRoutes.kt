package com.flopster101.siliconplayer

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

internal data class HomeRouteState(
    val recentFoldersLimit: Int,
    val recentFilesLimit: Int
)

internal data class HomeRouteActions(
    val onRecentFoldersLimitChanged: (Int) -> Unit,
    val onRecentFilesLimitChanged: (Int) -> Unit
)

internal data class MiscRouteState(
    val rememberBrowserLocation: Boolean,
    val sortArchivesBeforeFiles: Boolean
)

internal data class MiscRouteActions(
    val onRememberBrowserLocationChanged: (Boolean) -> Unit,
    val onSortArchivesBeforeFilesChanged: (Boolean) -> Unit,
    val onClearRecentHistory: () -> Unit
)

internal data class UiRouteState(
    val themeMode: ThemeMode
)

internal data class UiRouteActions(
    val onThemeModeChanged: (ThemeMode) -> Unit
)

@Composable
internal fun HomeRouteContent(
    state: HomeRouteState,
    actions: HomeRouteActions
) {
    var showFolderLimitDialog by remember { mutableStateOf(false) }
    var showFileLimitDialog by remember { mutableStateOf(false) }

    SettingsSectionLabel("Recents")
    SettingsItemCard(
        title = "Recent folders limit",
        description = "${state.recentFoldersLimit} folders",
        icon = Icons.Default.Folder,
        onClick = { showFolderLimitDialog = true }
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsItemCard(
        title = "Recent files limit",
        description = "${state.recentFilesLimit} files",
        icon = Icons.Default.MusicNote,
        onClick = { showFileLimitDialog = true }
    )

    if (showFolderLimitDialog) {
        SettingsTextInputDialog(
            title = "Recent folders limit",
            fieldLabel = "Folders",
            initialValue = state.recentFoldersLimit.toString(),
            supportingText = "1-50 (default 3)",
            keyboardType = KeyboardType.Number,
            sanitizer = { value -> value.filter { it.isDigit() }.take(2) },
            onDismiss = { showFolderLimitDialog = false },
            onConfirm = { input ->
                val parsed = input.trim().toIntOrNull()
                if (parsed != null && parsed in 1..50) {
                    actions.onRecentFoldersLimitChanged(parsed)
                    true
                } else {
                    false
                }
            }
        )
    }

    if (showFileLimitDialog) {
        SettingsTextInputDialog(
            title = "Recent files limit",
            fieldLabel = "Files",
            initialValue = state.recentFilesLimit.toString(),
            supportingText = "1-50 (default 5)",
            keyboardType = KeyboardType.Number,
            sanitizer = { value -> value.filter { it.isDigit() }.take(2) },
            onDismiss = { showFileLimitDialog = false },
            onConfirm = { input ->
                val parsed = input.trim().toIntOrNull()
                if (parsed != null && parsed in 1..50) {
                    actions.onRecentFilesLimitChanged(parsed)
                    true
                } else {
                    false
                }
            }
        )
    }
}

@Composable
internal fun MiscRouteContent(
    state: MiscRouteState,
    actions: MiscRouteActions
) {
    SettingsSectionLabel("Browser behavior")
    PlayerSettingToggleCard(
        title = "Remember browser location",
        description = "Restore last storage and folder when reopening the library browser.",
        checked = state.rememberBrowserLocation,
        onCheckedChange = actions.onRememberBrowserLocationChanged
    )
    Spacer(modifier = Modifier.height(10.dp))
    PlayerSettingToggleCard(
        title = "Sort ZIP archives before files",
        description = "List ZIP archives after folders but before regular files.",
        checked = state.sortArchivesBeforeFiles,
        onCheckedChange = actions.onSortArchivesBeforeFilesChanged
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsItemCard(
        title = "Clear home recents",
        description = "Remove recent folders and recently played shortcuts from the Home screen.",
        icon = Icons.Default.MoreHoriz,
        onClick = actions.onClearRecentHistory
    )
}

@Composable
internal fun UiRouteContent(
    state: UiRouteState,
    actions: UiRouteActions
) {
    ThemeModeSelectorCard(
        selectedMode = state.themeMode,
        onSelectedModeChanged = actions.onThemeModeChanged
    )
}
