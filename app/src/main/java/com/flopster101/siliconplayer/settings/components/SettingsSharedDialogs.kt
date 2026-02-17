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
    val label: String
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun <T> SettingsSingleChoiceDialog(
    title: String,
    selectedValue: T,
    options: List<ChoiceDialogOption<T>>,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit,
    showCancelButton: Boolean = true
) {
    val configuration = LocalConfiguration.current
    AlertDialog(
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
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .clickable {
                                    onSelected(option.value)
                                    onDismiss()
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = option.value == selectedValue,
                                onClick = {
                                    onSelected(option.value)
                                    onDismiss()
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge
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
