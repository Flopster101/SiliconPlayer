package com.flopster101.siliconplayer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

internal data class SettingsGroupedToggleOption<T>(
    val groupLabel: String,
    val value: T,
    val label: String
)

internal data class SettingsMultiSelectOption(
    val value: String,
    val label: String
)

@Composable
internal fun SettingsTextInputDialog(
    title: String,
    fieldLabel: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    supportingText: String? = null,
    placeholder: String? = null,
    sanitizer: (String) -> String = { it },
    confirmLabel: String = "Save",
    dismissLabel: String = "Cancel"
) {
    var input by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { value -> input = sanitizer(value) },
                singleLine = true,
                label = { Text(fieldLabel) },
                placeholder = placeholder?.let { { Text(it) } },
                supportingText = supportingText?.let { { Text(it) } },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = keyboardType
                ),
                shape = MaterialTheme.shapes.extraLarge
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (onConfirm(input)) {
                    onDismiss()
                }
            }) {
                Text(confirmLabel)
            }
        }
    )
}

@Composable
internal fun SettingsPriorityPickerDialog(
    title: String = "Set plugin priority",
    description: String = "Priorities are order indexes: 0 is first. Values stay sequential to match the plugin list.",
    currentValue: Int,
    maxValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val safeMax = maxValue.coerceAtLeast(0)
    var value by remember(currentValue, safeMax) { mutableIntStateOf(currentValue.coerceIn(0, safeMax)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(text = description, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Priority: $value", style = MaterialTheme.typography.titleMedium)
                if (safeMax > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = value.toFloat(),
                        onValueChange = { value = it.roundToInt().coerceIn(0, safeMax) },
                        valueRange = 0f..safeMax.toFloat(),
                        steps = (safeMax - 1).coerceAtLeast(0)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0 (Highest)", style = MaterialTheme.typography.labelSmall)
                    Text("$safeMax (Lowest)", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text("Apply") }
        }
    )
}

@Composable
internal fun SettingsSearchableMultiSelectDialog(
    title: String,
    options: List<SettingsMultiSelectOption>,
    selectedValues: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
    searchPlaceholder: String = "Search..."
) {
    var pendingSelected by remember(title, selectedValues) { mutableStateOf(selectedValues) }
    var searchQuery by remember(title) { mutableStateOf("") }
    val filtered = remember(options, searchQuery) {
        if (searchQuery.isBlank()) {
            options
        } else {
            options.filter { it.label.contains(searchQuery, ignoreCase = true) || it.value.contains(searchQuery, ignoreCase = true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(searchPlaceholder) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { pendingSelected = options.map { it.value }.toSet() }) {
                        Text("Select All")
                    }
                    TextButton(onClick = { pendingSelected = emptySet() }) {
                        Text("Deselect All")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(items = filtered, key = { it.value }) { option ->
                        val checked = pendingSelected.contains(option.value)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    pendingSelected = if (checked) pendingSelected - option.value else pendingSelected + option.value
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { enabled ->
                                    pendingSelected = if (enabled) pendingSelected + option.value else pendingSelected - option.value
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(option.label)
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(pendingSelected)
                onDismiss()
            }) { Text("Apply") }
        }
    )
}

@Composable
internal fun <T> SettingsGroupedToggleDialog(
    title: String,
    options: List<SettingsGroupedToggleOption<T>>,
    selectedValues: Set<T>,
    onDismiss: () -> Unit,
    onApply: (Set<T>) -> Unit,
    emptyGroupLabels: List<String> = emptyList(),
    selectAllValues: Set<T>? = null,
    topDescription: String? = null
) {
    var pendingSelected by remember(title, selectedValues) { mutableStateOf(selectedValues) }
    val groupedOptions = options.groupBy { it.groupLabel }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (selectAllValues != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { pendingSelected = selectAllValues }) {
                            Text("All")
                        }
                        OutlinedButton(onClick = { pendingSelected = emptySet() }) {
                            Text("None")
                        }
                    }
                }
                if (!topDescription.isNullOrBlank()) {
                    Text(
                        text = topDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val orderedGroupLabels = buildList {
                    addAll(groupedOptions.keys)
                    emptyGroupLabels.forEach { group ->
                        if (!contains(group)) add(group)
                    }
                }

                orderedGroupLabels.forEach { groupLabel ->
                    Text(
                        text = groupLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val groupItems = groupedOptions[groupLabel].orEmpty()
                    if (groupItems.isEmpty()) {
                        Text(
                            text = "No options yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        groupItems.forEach { option ->
                            val optionValue = option.value
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        pendingSelected = if (pendingSelected.contains(optionValue)) {
                                            pendingSelected - optionValue
                                        } else {
                                            pendingSelected + optionValue
                                        }
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = pendingSelected.contains(optionValue),
                                    onCheckedChange = { checked ->
                                        pendingSelected = if (checked) {
                                            pendingSelected + optionValue
                                        } else {
                                            pendingSelected - optionValue
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(option.label)
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(pendingSelected)
                onDismiss()
            }) { Text("Apply") }
        }
    )
}

@Composable
internal fun CacheSizeLimitDialog(
    initialBytes: Long,
    onDismiss: () -> Unit,
    onConfirmBytes: (Long) -> Unit
) {
    var unit by remember {
        mutableStateOf(
            if (initialBytes < CacheSizeUnit.GB.bytesPerUnit.toLong()) CacheSizeUnit.MB else CacheSizeUnit.GB
        )
    }
    var input by remember {
        mutableStateOf(
            String.format(Locale.US, "%.2f", initialBytes / unit.bytesPerUnit)
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cache size limit") },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    label = { Text("Max size (${unit.label})") },
                    placeholder = { Text("1.00") }
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CacheSizeUnit.values().forEach { candidate ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (candidate != unit) {
                                        val parsed = input.trim().replace(',', '.').toDoubleOrNull()
                                        val currentBytes = if (parsed != null && parsed > 0.0) {
                                            parsed * unit.bytesPerUnit
                                        } else {
                                            initialBytes.toDouble()
                                        }
                                        unit = candidate
                                        input = String.format(
                                            Locale.US,
                                            "%.2f",
                                            currentBytes / candidate.bytesPerUnit
                                        )
                                    }
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = unit == candidate,
                                onClick = {
                                    if (candidate != unit) {
                                        val parsed = input.trim().replace(',', '.').toDoubleOrNull()
                                        val currentBytes = if (parsed != null && parsed > 0.0) {
                                            parsed * unit.bytesPerUnit
                                        } else {
                                            initialBytes.toDouble()
                                        }
                                        unit = candidate
                                        input = String.format(
                                            Locale.US,
                                            "%.2f",
                                            currentBytes / candidate.bytesPerUnit
                                        )
                                    }
                                }
                            )
                            Text(
                                text = candidate.label,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = input.trim().replace(',', '.').toDoubleOrNull()
                if (parsed != null && parsed > 0.0) {
                    val bytes = (parsed * unit.bytesPerUnit).toLong().coerceAtLeast(1L)
                    onConfirmBytes(bytes)
                    onDismiss()
                }
            }) {
                Text("Save")
            }
        }
    )
}

@Composable
internal fun FilenameDisplayModeDialog(
    currentMode: FilenameDisplayMode,
    onModeSelected: (FilenameDisplayMode) -> Unit,
    onDismiss: () -> Unit
) {
    SettingsSingleChoiceDialog(
        title = "Show filename",
        selectedValue = currentMode,
        options = FilenameDisplayMode.entries.map { mode ->
            ChoiceDialogOption(value = mode, label = mode.label)
        },
        onSelected = onModeSelected,
        onDismiss = onDismiss
    )
}

@Composable
internal fun SteppedIntSliderDialog(
    title: String,
    unitLabel: String,
    range: IntRange,
    step: Int,
    currentValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    showNudgeButtons: Boolean = true,
    nudgeStep: Int = step,
    valueLabelFormatter: ((Int) -> String)? = null,
    confirmLabel: String = "Save"
) {
    val safeStep = step.coerceAtLeast(1)
    val min = range.first
    val max = range.last
    val stepCount = ((max - min) / safeStep).coerceAtLeast(1)
    var value by remember { mutableIntStateOf(currentValue.coerceIn(min, max)) }
    val safeNudgeStep = nudgeStep.coerceAtLeast(1)

    fun normalize(raw: Int): Int {
        val snapped = min + (((raw - min).toFloat() / safeStep).roundToInt() * safeStep)
        return snapped.coerceIn(min, max)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = valueLabelFormatter?.invoke(value)
                        ?: if (unitLabel == "%") "$value$unitLabel" else "$value $unitLabel",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showNudgeButtons) {
                        OutlinedButton(
                            onClick = { value = normalize(value - safeNudgeStep) },
                            enabled = value > min,
                            modifier = Modifier.width(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Decrease"
                            )
                        }
                    }
                    Slider(
                        value = value.toFloat(),
                        onValueChange = { raw ->
                            value = normalize(raw.roundToInt())
                        },
                        valueRange = min.toFloat()..max.toFloat(),
                        steps = (stepCount - 1).coerceAtLeast(0),
                        modifier = Modifier.weight(1f)
                    )
                    if (showNudgeButtons) {
                        OutlinedButton(
                            onClick = { value = normalize(value + safeNudgeStep) },
                            enabled = value < max,
                            modifier = Modifier.width(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Increase"
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("$min", style = MaterialTheme.typography.labelSmall)
                    Text("$max", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text(confirmLabel)
            }
        }
    )
}

@Composable
internal fun SettingsSwitchSliderDialog(
    title: String,
    switchLabel: String,
    switchChecked: Boolean,
    currentValue: Int,
    valueRange: IntRange,
    currentValueLabel: (Boolean, Int) -> String,
    onDismiss: () -> Unit,
    onConfirm: (Boolean, Int) -> Unit,
    confirmLabel: String = "Apply"
) {
    var checked by remember(title, switchChecked) { mutableStateOf(switchChecked) }
    var sliderValue by remember(title, currentValue, valueRange) {
        mutableIntStateOf(currentValue.coerceIn(valueRange.first, valueRange.last))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = switchLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = checked,
                        onCheckedChange = { enabled ->
                            checked = enabled
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = currentValueLabel(checked, sliderValue),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Slider(
                    value = sliderValue.toFloat(),
                    onValueChange = { raw ->
                        sliderValue = raw.roundToInt().coerceIn(valueRange.first, valueRange.last)
                    },
                    valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                    steps = (valueRange.last - valueRange.first - 1).coerceAtLeast(0),
                    enabled = !checked
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(checked, sliderValue) }) {
                Text(confirmLabel)
            }
        }
    )
}

@Composable
internal fun VisualizationOscColorModeDialog(
    title: String,
    options: List<VisualizationOscColorMode>,
    selectedMode: VisualizationOscColorMode,
    onDismiss: () -> Unit,
    onSelect: (VisualizationOscColorMode) -> Unit
) {
    SettingsSingleChoiceDialog(
        title = title,
        selectedValue = selectedMode,
        options = options.map { mode ->
            ChoiceDialogOption(value = mode, label = mode.label)
        },
        onSelected = onSelect,
        onDismiss = onDismiss
    )
}

@Composable
internal fun VisualizationRgbColorPickerDialog(
    title: String,
    initialArgb: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var red by remember { mutableIntStateOf((initialArgb shr 16) and 0xFF) }
    var green by remember { mutableIntStateOf((initialArgb shr 8) and 0xFF) }
    var blue by remember { mutableIntStateOf(initialArgb and 0xFF) }

    fun asArgbInt(r: Int, g: Int, b: Int): Int {
        return (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    }

    val previewColor = Color(asArgbInt(red, green, blue))
    val hex = String.format(Locale.US, "#%02X%02X%02X", red, green, blue)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(previewColor)
                )
                Text(text = hex, style = MaterialTheme.typography.titleMedium)

                Text("Red: $red", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = red.toFloat(),
                    onValueChange = { red = it.roundToInt().coerceIn(0, 255) },
                    valueRange = 0f..255f
                )
                Text("Green: $green", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = green.toFloat(),
                    onValueChange = { green = it.roundToInt().coerceIn(0, 255) },
                    valueRange = 0f..255f
                )
                Text("Blue: $blue", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = blue.toFloat(),
                    onValueChange = { blue = it.roundToInt().coerceIn(0, 255) },
                    valueRange = 0f..255f
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(asArgbInt(red, green, blue)) }) {
                Text("Save")
            }
        }
    )
}
