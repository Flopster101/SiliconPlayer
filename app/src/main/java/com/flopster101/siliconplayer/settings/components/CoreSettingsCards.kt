package com.flopster101.siliconplayer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val CoreSettingsCardShape = RoundedCornerShape(16.dp)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun CoreChoiceSelectorCard(
    title: String,
    description: String,
    selectedValue: Int,
    options: List<IntChoice>,
    onSelected: (Int) -> Unit
) {
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label ?: options.firstOrNull()?.label.orEmpty()
    var dialogOpen by remember { mutableStateOf(false) }

    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = CoreSettingsCardShape,
        onClick = { dialogOpen = true }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = selectedLabel,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }

    if (dialogOpen) {
        SettingsSingleChoiceDialog(
            title = title,
            selectedValue = selectedValue,
            options = options.map { ChoiceDialogOption(value = it.value, label = it.label) },
            onSelected = onSelected,
            onDismiss = { dialogOpen = false }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun CoreDialogSliderCard(
    title: String,
    description: String,
    value: Int,
    valueRange: IntRange,
    step: Int,
    valueLabel: (Int) -> String,
    showNudgeButtons: Boolean = false,
    nudgeStep: Int = step,
    onValueChanged: (Int) -> Unit
) {
    val coercedValue = value.coerceIn(valueRange.first, valueRange.last)
    var dialogOpen by remember { mutableStateOf(false) }
    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = CoreSettingsCardShape,
        onClick = { dialogOpen = true }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = valueLabel(coercedValue),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }

    if (dialogOpen) {
        SteppedIntSliderDialog(
            title = title,
            unitLabel = "",
            range = valueRange,
            step = step,
            currentValue = coercedValue,
            showNudgeButtons = showNudgeButtons,
            nudgeStep = nudgeStep,
            valueLabelFormatter = valueLabel,
            confirmLabel = "Apply",
            onDismiss = { dialogOpen = false },
            onConfirm = { newValue ->
                onValueChanged(newValue)
                dialogOpen = false
            }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun CoreVolumeRampingCard(
    title: String,
    description: String,
    value: Int,
    onValueChanged: (Int) -> Unit
) {
    var dialogOpen by remember { mutableStateOf(false) }
    val autoEnabled = value < 0
    val displayedValue = if (autoEnabled) "Auto" else value.toString()

    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = CoreSettingsCardShape,
        onClick = { dialogOpen = true }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = displayedValue,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }

    if (dialogOpen) {
        SettingsSwitchSliderDialog(
            title = title,
            switchLabel = "Auto",
            switchChecked = autoEnabled,
            currentValue = value.coerceIn(0, 10),
            valueRange = 0..10,
            currentValueLabel = { checked, slider -> if (checked) "Current: Auto" else "Current: $slider" },
            onDismiss = { dialogOpen = false },
            onConfirm = { checked, slider ->
                onValueChanged(if (checked) -1 else slider)
                dialogOpen = false
            },
            confirmLabel = "Apply"
        )
    }
}
