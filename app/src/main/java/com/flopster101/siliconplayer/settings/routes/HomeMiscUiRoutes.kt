package com.flopster101.siliconplayer

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Public
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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

internal data class FileBrowserRouteState(
    val rememberBrowserLocation: Boolean,
    val showParentDirectoryEntry: Boolean,
    val showFileIconChipBackground: Boolean,
    val sortArchivesBeforeFiles: Boolean,
    val browserNameSortMode: BrowserNameSortMode
)

internal data class FileBrowserRouteActions(
    val onRememberBrowserLocationChanged: (Boolean) -> Unit,
    val onShowParentDirectoryEntryChanged: (Boolean) -> Unit,
    val onShowFileIconChipBackgroundChanged: (Boolean) -> Unit,
    val onSortArchivesBeforeFilesChanged: (Boolean) -> Unit,
    val onBrowserNameSortModeChanged: (BrowserNameSortMode) -> Unit
)

internal data class MiscRouteActions(
    val onClearRecentHistory: () -> Unit
)

internal data class NetworkRouteActions(
    val onClearSavedNetworkSources: () -> Unit
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
    SettingsRowSpacer()
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
    actions: MiscRouteActions
) {
    SettingsSectionLabel("Utilities")
    SettingsItemCard(
        title = "Clear home recents",
        description = "Remove recent folders and recently played shortcuts from the Home screen.",
        icon = Icons.Default.MoreHoriz,
        onClick = actions.onClearRecentHistory
    )
}

@Composable
internal fun NetworkRouteContent(
    actions: NetworkRouteActions
) {
    var showClearSavedSourcesConfirm by remember { mutableStateOf(false) }

    SettingsSectionLabel("Saved network sources")
    SettingsItemCard(
        title = "Clear saved sources",
        description = "Remove all saved network folders and remote sources.",
        icon = Icons.Default.DeleteForever,
        onClick = { showClearSavedSourcesConfirm = true }
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "Future examples and providers",
        description = "Built-in examples and provider toggles will be added here later.",
        icon = Icons.Default.Public,
        onClick = {}
    )

    if (showClearSavedSourcesConfirm) {
        SettingsConfirmDialog(
            title = "Clear saved network sources?",
            message = "This removes all user-saved network folders and remote sources.",
            confirmLabel = "Clear",
            onDismiss = { showClearSavedSourcesConfirm = false },
            onConfirm = actions.onClearSavedNetworkSources
        )
    }
}

@Composable
internal fun FileBrowserRouteContent(
    state: FileBrowserRouteState,
    actions: FileBrowserRouteActions
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }
    var showUnsupportedFiles by remember {
        mutableStateOf(
            prefs.getBoolean(
                AppPreferenceKeys.BROWSER_SHOW_UNSUPPORTED_FILES,
                AppDefaults.Browser.showUnsupportedFiles
            )
        )
    }
    var showPreviewFiles by remember {
        mutableStateOf(
            prefs.getBoolean(
                AppPreferenceKeys.BROWSER_SHOW_PREVIEW_FILES,
                AppDefaults.Browser.showPreviewFiles
            )
        )
    }
    var showHiddenFilesAndFolders by remember {
        mutableStateOf(
            prefs.getBoolean(
                AppPreferenceKeys.BROWSER_SHOW_HIDDEN_FILES_AND_FOLDERS,
                AppDefaults.Browser.showHiddenFilesAndFolders
            )
        )
    }

    SettingsSectionLabel("Browser behavior")
    PlayerSettingToggleCard(
        title = "Remember browser location",
        description = "Restore last storage and folder when reopening the library browser.",
        checked = state.rememberBrowserLocation,
        onCheckedChange = actions.onRememberBrowserLocationChanged
    )
    SettingsRowSpacer()
    PlayerSettingToggleCard(
        title = "Show parent directory entry (..)",
        description = "Show a '..' row at the top of file lists to go one level up.",
        checked = state.showParentDirectoryEntry,
        onCheckedChange = actions.onShowParentDirectoryEntryChanged
    )
    SettingsRowSpacer()
    PlayerSettingToggleCard(
        title = "Show file icon chip background",
        description = "Draw the same rounded chip background behind file icons as folders.",
        checked = state.showFileIconChipBackground,
        onCheckedChange = actions.onShowFileIconChipBackgroundChanged
    )
    SettingsRowSpacer()
    PlayerSettingToggleCard(
        title = "Show unsupported files",
        description = "Display files that no enabled decoder core can open.",
        checked = showUnsupportedFiles,
        onCheckedChange = {
            showUnsupportedFiles = it
            prefs.edit().putBoolean(AppPreferenceKeys.BROWSER_SHOW_UNSUPPORTED_FILES, it).apply()
        }
    )
    SettingsRowSpacer()
    PlayerSettingToggleCard(
        title = "Show text and image files",
        description = "Display previewable text/image files in browser lists.",
        checked = showPreviewFiles,
        onCheckedChange = {
            showPreviewFiles = it
            prefs.edit().putBoolean(AppPreferenceKeys.BROWSER_SHOW_PREVIEW_FILES, it).apply()
        }
    )
    SettingsRowSpacer()
    PlayerSettingToggleCard(
        title = "Show hidden files and folders",
        description = "Display hidden entries (such as dot-prefixed names).",
        checked = showHiddenFilesAndFolders,
        onCheckedChange = {
            showHiddenFilesAndFolders = it
            prefs.edit().putBoolean(AppPreferenceKeys.BROWSER_SHOW_HIDDEN_FILES_AND_FOLDERS, it).apply()
        }
    )
    SettingsRowSpacer()
    BrowserNameSortModeSelectorCard(
        selectedMode = state.browserNameSortMode,
        onSelectedModeChanged = actions.onBrowserNameSortModeChanged
    )
    SettingsRowSpacer()
    PlayerSettingToggleCard(
        title = "Sort ZIP archives before files",
        description = "List ZIP archives after folders but before regular files.",
        checked = state.sortArchivesBeforeFiles,
        onCheckedChange = actions.onSortArchivesBeforeFilesChanged
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
