package com.flopster101.siliconplayer

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

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

    SettingsValuePickerCard(
        title = title,
        description = description,
        value = selectedLabel,
        onClick = { dialogOpen = true }
    )

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
    showNudgeButtons: Boolean = true,
    nudgeStep: Int = step,
    onValueChanged: (Int) -> Unit
) {
    val coercedValue = value.coerceIn(valueRange.first, valueRange.last)
    var dialogOpen by remember { mutableStateOf(false) }
    SettingsValuePickerCard(
        title = title,
        description = description,
        value = valueLabel(coercedValue),
        onClick = { dialogOpen = true }
    )

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

    SettingsValuePickerCard(
        title = title,
        description = description,
        value = displayedValue,
        onClick = { dialogOpen = true }
    )

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
