package com.flopster101.siliconplayer

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
                message = "This resets app settings to defaults and keeps plugin settings unchanged."
                confirmText = "Clear app settings"
                onConfirm = onConfirmClearAllSettings
            }

            SettingsResetAction.ClearPluginSettings -> {
                title = "Clear all plugin settings?"
                message = "This resets all plugin/core settings to defaults and keeps app settings unchanged."
                confirmText = "Clear plugin settings"
                onConfirm = onConfirmClearAllPluginSettings
            }
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Text(message) },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onConfirm()
                    onDismiss()
                }) {
                    Text(confirmText)
                }
            }
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
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Reset $pluginName settings?") },
            text = { Text("This resets only $pluginName plugin/core settings to defaults.") },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onConfirmResetPluginSettings(pluginName)
                    onDismiss()
                }) {
                    Text("Reset")
                }
            }
        )
    }
}
