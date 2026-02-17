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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val OpenMptCardShape = RoundedCornerShape(16.dp)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun OpenMptChoiceSelectorCard(
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
        shape = OpenMptCardShape,
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
internal fun OpenMptDialogSliderCard(
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
    val sliderSteps = ((valueRange.last - valueRange.first) / step).coerceAtLeast(1) - 1
    var dialogOpen by remember { mutableStateOf(false) }
    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = OpenMptCardShape,
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
        var sliderValue by remember(value) { mutableIntStateOf(coercedValue) }
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(title) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = valueLabel(sliderValue),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CompositionLocalProvider(
                        androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement provides false
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showNudgeButtons) {
                                IconButton(
                                    onClick = {
                                        val snapped = sliderValue - nudgeStep
                                        sliderValue = snapped.coerceIn(valueRange.first, valueRange.last)
                                    },
                                    enabled = sliderValue > valueRange.first,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Decrease",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Slider(
                                value = sliderValue.toFloat(),
                                onValueChange = { raw ->
                                    val stepsFromStart = ((raw - valueRange.first.toFloat()) / step.toFloat()).roundToInt()
                                    val snapped = valueRange.first + (stepsFromStart * step)
                                    sliderValue = snapped.coerceIn(valueRange.first, valueRange.last)
                                },
                                valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                                steps = sliderSteps,
                                modifier = Modifier.weight(1f)
                            )
                            if (showNudgeButtons) {
                                IconButton(
                                    onClick = {
                                        val snapped = sliderValue + nudgeStep
                                        sliderValue = snapped.coerceIn(valueRange.first, valueRange.last)
                                    },
                                    enabled = sliderValue < valueRange.last,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Increase",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogOpen = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onValueChanged(sliderValue)
                    dialogOpen = false
                }) {
                    Text("Apply")
                }
            }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun OpenMptVolumeRampingCard(
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
        shape = OpenMptCardShape,
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
        var autoState by remember(value) { mutableStateOf(value < 0) }
        var sliderValue by remember(value) { mutableIntStateOf(value.coerceIn(0, 10)) }
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(title) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Auto",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = autoState,
                            onCheckedChange = { enabled ->
                                autoState = enabled
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (autoState) "Current: Auto" else "Current: $sliderValue",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Slider(
                        value = sliderValue.toFloat(),
                        onValueChange = { raw ->
                            sliderValue = raw.roundToInt().coerceIn(0, 10)
                        },
                        valueRange = 0f..10f,
                        steps = 9,
                        enabled = !autoState
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogOpen = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onValueChanged(if (autoState) -1 else sliderValue)
                    dialogOpen = false
                }) {
                    Text("Apply")
                }
            }
        )
    }
}
