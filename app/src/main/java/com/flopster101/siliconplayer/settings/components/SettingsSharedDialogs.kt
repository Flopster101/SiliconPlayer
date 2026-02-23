package com.flopster101.siliconplayer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

internal data class ChoiceDialogOption<T>(
    val value: T,
    val label: String,
    val enabled: Boolean = true
)

internal data class SettingsActionDialogItem(
    val label: String,
    val onSelected: () -> Unit
)

@Composable
internal fun SettingsConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    dismissLabel: String = "Cancel"
) {
    AlertDialog(
        modifier = adaptiveDialogModifier(),
        properties = adaptiveDialogProperties(),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text(confirmLabel)
            }
        }
    )
}

@Composable
internal fun SettingsActionListDialog(
    title: String,
    actions: List<SettingsActionDialogItem>,
    onDismiss: () -> Unit,
    message: String? = null,
    dismissLabel: String = "Cancel"
) {
    AlertDialog(
        modifier = adaptiveDialogModifier(),
        properties = adaptiveDialogProperties(),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (!message.isNullOrBlank()) {
                    Text(message)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                actions.forEach { action ->
                    TextButton(
                        onClick = {
                            action.onSelected()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(action.label)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        },
        confirmButton = {}
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun <T> SettingsSingleChoiceDialog(
    title: String,
    selectedValue: T,
    options: List<ChoiceDialogOption<T>>,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit,
    description: String? = null,
    showCancelButton: Boolean = true
) {
    val configuration = LocalConfiguration.current
    AlertDialog(
        modifier = adaptiveDialogModifier(),
        properties = adaptiveDialogProperties(),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            val maxDialogListHeight = configuration.screenHeightDp.dp * 0.62f
            CompositionLocalProvider(
                androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement provides false
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxDialogListHeight)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (!description.isNullOrBlank()) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .clickable(enabled = option.enabled) {
                                    onSelected(option.value)
                                    onDismiss()
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = option.value == selectedValue,
                                enabled = option.enabled,
                                onClick = {
                                    if (option.enabled) {
                                        onSelected(option.value)
                                        onDismiss()
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (option.enabled) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            if (showCancelButton) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
        confirmButton = {}
    )
}
