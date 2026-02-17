package com.flopster101.siliconplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

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
    onConfirm: (Int) -> Unit
) {
    val safeStep = step.coerceAtLeast(1)
    val min = range.first
    val max = range.last
    val stepCount = ((max - min) / safeStep).coerceAtLeast(1)
    var value by remember { mutableIntStateOf(currentValue.coerceIn(min, max)) }

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
                    text = if (unitLabel == "%") "$value$unitLabel" else "$value $unitLabel",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { value = normalize(value - safeStep) },
                        enabled = value > min,
                        modifier = Modifier.width(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Decrease"
                        )
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
                    OutlinedButton(
                        onClick = { value = normalize(value + safeStep) },
                        enabled = value < max,
                        modifier = Modifier.width(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increase"
                        )
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
                Text("Save")
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
