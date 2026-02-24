package com.flopster101.siliconplayer.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.adaptiveDialogModifier
import com.flopster101.siliconplayer.adaptiveDialogProperties
import com.flopster101.siliconplayer.rememberDialogScrollbarAlpha

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun VisualizationModePickerDialog(
    availableModes: List<VisualizationMode>,
    selectedMode: VisualizationMode,
    onSelectMode: (VisualizationMode) -> Unit,
    onOpenSelectedVisualizationSettings: () -> Unit,
    onOpenVisualizationSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val dialogConfiguration = LocalConfiguration.current
    val optionScrollState = rememberScrollState()
    var optionViewportHeightPx by remember { mutableFloatStateOf(0f) }
    val listMaxHeight = dialogConfiguration.screenHeightDp.dp * 0.42f
    val basicModes = availableModes.filter {
        it == VisualizationMode.Off ||
            it == VisualizationMode.Bars ||
            it == VisualizationMode.Oscilloscope ||
            it == VisualizationMode.VuMeters
    }
    val advancedModes = availableModes.filterNot { basicModes.contains(it) }
    val scrollbarThumbFraction = remember(optionScrollState.maxValue, optionViewportHeightPx) {
        if (optionViewportHeightPx <= 0f) {
            1f
        } else {
            val contentHeight = optionViewportHeightPx + optionScrollState.maxValue.toFloat()
            (optionViewportHeightPx / contentHeight).coerceIn(0.08f, 1f)
        }
    }
    val scrollbarOffsetFraction = remember(optionScrollState.value, optionScrollState.maxValue) {
        if (optionScrollState.maxValue <= 0) 0f
        else optionScrollState.value.toFloat() / optionScrollState.maxValue.toFloat()
    }
    val scrollbarAlpha = rememberDialogScrollbarAlpha(
        enabled = true,
        scrollState = optionScrollState,
        label = "visualizationModeScrollbarAlpha"
    )

    AlertDialog(
        modifier = adaptiveDialogModifier(),
        properties = adaptiveDialogProperties(),
        onDismissRequest = onDismiss,
        title = { Text("Visualization mode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Available visualizations depend on the current core and song.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = listMaxHeight)
                        .onSizeChanged { optionViewportHeightPx = it.height.toFloat() }
                ) {
                    CompositionLocalProvider(
                        androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement provides false
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 10.dp)
                                .verticalScroll(optionScrollState),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Basic",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp, bottom = 1.dp)
                            )
                            basicModes.forEach { mode ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectMode(mode)
                                            onDismiss()
                                        }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = mode == selectedMode,
                                        onClick = {
                                            onSelectMode(mode)
                                            onDismiss()
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Advanced",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 1.dp)
                            )
                            if (advancedModes.isEmpty()) {
                                Text(
                                    text = "No advanced visualizations available for this core.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            } else {
                                advancedModes.forEach { mode ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onSelectMode(mode)
                                                onDismiss()
                                            }
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = mode == selectedMode,
                                            onClick = {
                                                onSelectMode(mode)
                                                onDismiss()
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = mode.label,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (optionScrollState.maxValue > 0 && optionViewportHeightPx > 0f) {
                        val thumbHeightPx = optionViewportHeightPx * scrollbarThumbFraction
                        val maxOffsetPx = (optionViewportHeightPx - thumbHeightPx).coerceAtLeast(0f)
                        val thumbOffsetPx = maxOffsetPx * scrollbarOffsetFraction
                        val dialogDensity = LocalDensity.current
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(4.dp)
                                .fillMaxHeight()
                                .graphicsLayer(alpha = scrollbarAlpha)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(y = with(dialogDensity) { thumbOffsetPx.toDp() })
                                .width(4.dp)
                                .height(with(dialogDensity) { thumbHeightPx.toDp() })
                                .graphicsLayer(alpha = scrollbarAlpha)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f), RoundedCornerShape(999.dp))
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (selectedMode != VisualizationMode.Off) {
                    TextButton(onClick = {
                        onDismiss()
                        onOpenSelectedVisualizationSettings()
                    }) {
                        Text("Selected settings")
                    }
                }
                TextButton(onClick = {
                    onDismiss()
                    onOpenVisualizationSettings()
                }) {
                    Text("Settings")
                }
            }
        }
    )
}
