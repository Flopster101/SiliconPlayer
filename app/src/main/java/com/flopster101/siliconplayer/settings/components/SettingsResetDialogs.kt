package com.flopster101.siliconplayer

import androidx.compose.runtime.Composable

@Composable
internal fun SettingsResetDialogHost(
    pendingResetAction: SettingsResetAction?,
    onDismiss: () -> Unit,
    onConfirmClearAllSettings: () -> Unit,
    onConfirmClearAllPluginSettings: () -> Unit
) {
    pendingResetAction?.let { action ->
        val title: String
        val message: String
        val confirmText: String
        val onConfirm: () -> Unit
        when (action) {
            SettingsResetAction.ClearAllSettings -> {
                title = "Clear all app settings?"
                message = "This resets app settings to defaults and keeps core settings unchanged."
                confirmText = "Clear app settings"
                onConfirm = onConfirmClearAllSettings
            }

            SettingsResetAction.ClearPluginSettings -> {
                title = "Clear all core settings?"
                message = "This resets all core settings to defaults and keeps app settings unchanged."
                confirmText = "Clear core settings"
                onConfirm = onConfirmClearAllPluginSettings
            }
        }

        SettingsConfirmDialog(
            title = title,
            message = message,
            confirmLabel = confirmText,
            onDismiss = onDismiss,
            onConfirm = onConfirm
        )
    }
}

@Composable
internal fun PluginResetDialogHost(
    pendingPluginResetName: String?,
    onDismiss: () -> Unit,
    onConfirmResetPluginSettings: (String) -> Unit
) {
    pendingPluginResetName?.let { pluginName ->
        SettingsConfirmDialog(
            title = "Reset $pluginName core settings?",
            message = "This resets only $pluginName core settings to defaults.",
            confirmLabel = "Reset",
            onDismiss = onDismiss,
            onConfirm = { onConfirmResetPluginSettings(pluginName) }
        )
    }
}
