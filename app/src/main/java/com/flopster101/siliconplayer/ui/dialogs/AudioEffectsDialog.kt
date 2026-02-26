package com.flopster101.siliconplayer.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.adaptiveDialogModifier
import com.flopster101.siliconplayer.adaptiveDialogProperties
import com.flopster101.siliconplayer.rememberDialogScrollbarAlpha

private val reverbPresetNames = listOf(
    "GM Plate",
    "GM Small Room",
    "GM Medium Room",
    "GM Large Room",
    "GM Medium Hall",
    "GM Large Hall",
    "Generic",
    "Padded Cell",
    "Room",
    "Bathroom",
    "Living Room",
    "Stone Room",
    "Auditorium",
    "Concert Hall",
    "Cave",
    "Arena",
    "Hangar",
    "Carpeted Hallway",
    "Hallway",
    "Stone Corridor",
    "Alley",
    "Forest",
    "City",
    "Mountains",
    "Quarry",
    "Plain",
    "Parking Lot",
    "Sewer Pipe",
    "Underwater"
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AudioEffectsDialog(
    masterVolumeDb: Float,
    pluginVolumeDb: Float,
    songVolumeDb: Float,
    ignoreCoreVolumeForSong: Boolean,
    forceMono: Boolean,
    hasActiveCore: Boolean,
    hasActiveSong: Boolean,
    currentCoreName: String?,
    onMasterVolumeChange: (Float) -> Unit,
    onPluginVolumeChange: (Float) -> Unit,
    onSongVolumeChange: (Float) -> Unit,
    onIgnoreCoreVolumeForSongChange: (Boolean) -> Unit,
    onForceMonoChange: (Boolean) -> Unit,
    dspBassEnabled: Boolean,
    dspBassDepth: Int,
    dspBassRange: Int,
    dspSurroundEnabled: Boolean,
    dspSurroundDepth: Int,
    dspSurroundDelayMs: Int,
    dspReverbEnabled: Boolean,
    dspReverbDepth: Int,
    dspReverbPreset: Int,
    dspBitCrushEnabled: Boolean,
    dspBitCrushBits: Int,
    onDspBassEnabledChange: (Boolean) -> Unit,
    onDspBassDepthChange: (Int) -> Unit,
    onDspBassRangeChange: (Int) -> Unit,
    onDspSurroundEnabledChange: (Boolean) -> Unit,
    onDspSurroundDepthChange: (Int) -> Unit,
    onDspSurroundDelayMsChange: (Int) -> Unit,
    onDspReverbEnabledChange: (Boolean) -> Unit,
    onDspReverbDepthChange: (Int) -> Unit,
    onDspReverbPresetChange: (Int) -> Unit,
    onDspBitCrushEnabledChange: (Boolean) -> Unit,
    onDspBitCrushBitsChange: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = remember { listOf("Volume", "DSP") }

    AlertDialog(
        modifier = adaptiveDialogModifier(),
        properties = adaptiveDialogProperties(),
        onDismissRequest = onDismiss,
        title = { Text("Audio effects") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
            ) {
                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (selectedTabIndex) {
                    0 -> VolumeTabContent(
                        masterVolumeDb = masterVolumeDb,
                        pluginVolumeDb = pluginVolumeDb,
                        songVolumeDb = songVolumeDb,
                        ignoreCoreVolumeForSong = ignoreCoreVolumeForSong,
                        forceMono = forceMono,
                        hasActiveCore = hasActiveCore,
                        hasActiveSong = hasActiveSong,
                        currentCoreName = currentCoreName,
                        onMasterVolumeChange = onMasterVolumeChange,
                        onPluginVolumeChange = onPluginVolumeChange,
                        onSongVolumeChange = onSongVolumeChange,
                        onIgnoreCoreVolumeForSongChange = onIgnoreCoreVolumeForSongChange,
                        onForceMonoChange = onForceMonoChange
                    )
                    else -> DspTabContent(
                        currentCoreName = currentCoreName,
                        dspBassEnabled = dspBassEnabled,
                        dspBassDepth = dspBassDepth,
                        dspBassRange = dspBassRange,
                        dspSurroundEnabled = dspSurroundEnabled,
                        dspSurroundDepth = dspSurroundDepth,
                        dspSurroundDelayMs = dspSurroundDelayMs,
                        dspReverbEnabled = dspReverbEnabled,
                        dspReverbDepth = dspReverbDepth,
                        dspReverbPreset = dspReverbPreset,
                        dspBitCrushEnabled = dspBitCrushEnabled,
                        dspBitCrushBits = dspBitCrushBits,
                        onDspBassEnabledChange = onDspBassEnabledChange,
                        onDspBassDepthChange = onDspBassDepthChange,
                        onDspBassRangeChange = onDspBassRangeChange,
                        onDspSurroundEnabledChange = onDspSurroundEnabledChange,
                        onDspSurroundDepthChange = onDspSurroundDepthChange,
                        onDspSurroundDelayMsChange = onDspSurroundDelayMsChange,
                        onDspReverbEnabledChange = onDspReverbEnabledChange,
                        onDspReverbDepthChange = onDspReverbDepthChange,
                        onDspReverbPresetChange = onDspReverbPresetChange,
                        onDspBitCrushEnabledChange = onDspBitCrushEnabledChange,
                        onDspBitCrushBitsChange = onDspBitCrushBitsChange
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("OK")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onReset) {
                    Text("Reset")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun VolumeTabContent(
    masterVolumeDb: Float,
    pluginVolumeDb: Float,
    songVolumeDb: Float,
    ignoreCoreVolumeForSong: Boolean,
    forceMono: Boolean,
    hasActiveCore: Boolean,
    hasActiveSong: Boolean,
    currentCoreName: String?,
    onMasterVolumeChange: (Float) -> Unit,
    onPluginVolumeChange: (Float) -> Unit,
    onSongVolumeChange: (Float) -> Unit,
    onIgnoreCoreVolumeForSongChange: (Boolean) -> Unit,
    onForceMonoChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        VolumeSliderRow(
            label = "Master Volume",
            valueDb = masterVolumeDb,
            onValueChange = onMasterVolumeChange
        )

        VolumeSliderRow(
            label = "Core Volume",
            valueDb = pluginVolumeDb,
            onValueChange = onPluginVolumeChange,
            enabled = hasActiveCore
        )

        VolumeSliderRow(
            label = "Song Volume",
            valueDb = songVolumeDb,
            onValueChange = onSongVolumeChange,
            enabled = hasActiveSong
        )

        CompositionLocalProvider(
            LocalMinimumInteractiveComponentEnforcement provides false
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Ignore Core volume for this song",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = ignoreCoreVolumeForSong,
                        onCheckedChange = onIgnoreCoreVolumeForSongChange,
                        enabled = hasActiveSong
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Force Mono", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = forceMono,
                        onCheckedChange = onForceMonoChange
                    )
                }
            }
        }

        Text(
            text = "Core: ${currentCoreName ?: "(none)"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DspTabContent(
    currentCoreName: String?,
    dspBassEnabled: Boolean,
    dspBassDepth: Int,
    dspBassRange: Int,
    dspSurroundEnabled: Boolean,
    dspSurroundDepth: Int,
    dspSurroundDelayMs: Int,
    dspReverbEnabled: Boolean,
    dspReverbDepth: Int,
    dspReverbPreset: Int,
    dspBitCrushEnabled: Boolean,
    dspBitCrushBits: Int,
    onDspBassEnabledChange: (Boolean) -> Unit,
    onDspBassDepthChange: (Int) -> Unit,
    onDspBassRangeChange: (Int) -> Unit,
    onDspSurroundEnabledChange: (Boolean) -> Unit,
    onDspSurroundDepthChange: (Int) -> Unit,
    onDspSurroundDelayMsChange: (Int) -> Unit,
    onDspReverbEnabledChange: (Boolean) -> Unit,
    onDspReverbDepthChange: (Int) -> Unit,
    onDspReverbPresetChange: (Int) -> Unit,
    onDspBitCrushEnabledChange: (Boolean) -> Unit,
    onDspBitCrushBitsChange: (Int) -> Unit
) {
    val scrollState = rememberScrollState()
    var viewportHeightPx by remember { mutableFloatStateOf(0f) }
    val thumbFraction = remember(scrollState.maxValue, viewportHeightPx) {
        if (viewportHeightPx <= 0f) 1f else {
            val contentHeight = viewportHeightPx + scrollState.maxValue.toFloat()
            (viewportHeightPx / contentHeight).coerceIn(0.08f, 1f)
        }
    }
    val offsetFraction = remember(scrollState.value, scrollState.maxValue) {
        if (scrollState.maxValue <= 0) 0f else scrollState.value.toFloat() / scrollState.maxValue.toFloat()
    }
    val scrollbarAlpha = rememberDialogScrollbarAlpha(
        enabled = true,
        scrollState = scrollState,
        label = "audioEffectsDspScrollbarAlpha"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { viewportHeightPx = it.height.toFloat() },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 10.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "DSP attribution: About -> Libraries -> OpenMPT DSP effects.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DspToggleRow(
                    label = "Bass Expansion",
                    checked = dspBassEnabled,
                    onCheckedChange = onDspBassEnabledChange
                )
                DspIntSliderRow(
                    label = "Bass Depth",
                    value = dspBassDepth,
                    valueRange = 0..4,
                    onValueChange = onDspBassDepthChange,
                    enabled = dspBassEnabled
                )
                DspIntSliderRow(
                    label = "Bass Range",
                    value = dspBassRange,
                    valueRange = 0..4,
                    onValueChange = onDspBassRangeChange,
                    enabled = dspBassEnabled
                )

                HorizontalDivider()

                DspToggleRow(
                    label = "Surround",
                    checked = dspSurroundEnabled,
                    onCheckedChange = onDspSurroundEnabledChange
                )
                DspIntSliderRow(
                    label = "Surround Depth",
                    value = dspSurroundDepth,
                    valueRange = 1..16,
                    onValueChange = onDspSurroundDepthChange,
                    enabled = dspSurroundEnabled
                )
                DspIntSliderRow(
                    label = "Surround Delay (ms)",
                    value = dspSurroundDelayMs,
                    valueRange = 5..45,
                    step = 5,
                    onValueChange = onDspSurroundDelayMsChange,
                    enabled = dspSurroundEnabled
                )

                HorizontalDivider()

                DspToggleRow(
                    label = "Reverb",
                    checked = dspReverbEnabled,
                    onCheckedChange = onDspReverbEnabledChange
                )
                DspIntSliderRow(
                    label = "Reverb Depth",
                    value = dspReverbDepth,
                    valueRange = 1..16,
                    onValueChange = onDspReverbDepthChange,
                    enabled = dspReverbEnabled
                )
                DspReverbPresetDropdownRow(
                    label = "Reverb Preset",
                    value = dspReverbPreset,
                    onValueChange = onDspReverbPresetChange,
                    enabled = dspReverbEnabled
                )

                HorizontalDivider()

                DspToggleRow(
                    label = "Bit Crush",
                    checked = dspBitCrushEnabled,
                    onCheckedChange = onDspBitCrushEnabledChange
                )
                DspIntSliderRow(
                    label = "Bit Crush Bits",
                    value = dspBitCrushBits,
                    valueRange = 1..24,
                    onValueChange = onDspBitCrushBitsChange,
                    enabled = dspBitCrushEnabled
                )
                Text(
                    text = "Core: ${currentCoreName ?: "(none)"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (scrollState.maxValue > 0 && viewportHeightPx > 0f) {
                val thumbHeightPx = viewportHeightPx * thumbFraction
                val maxOffsetPx = (viewportHeightPx - thumbHeightPx).coerceAtLeast(0f)
                val thumbOffsetPx = maxOffsetPx * offsetFraction
                val density = LocalDensity.current
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
                        .offset(y = with(density) { thumbOffsetPx.toDp() })
                        .width(4.dp)
                        .height(with(density) { thumbHeightPx.toDp() })
                        .graphicsLayer(alpha = scrollbarAlpha)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f), RoundedCornerShape(999.dp))
                )
            }
        }
    }
}

@Composable
private fun DspToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DspReverbPresetDropdownRow(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    enabled: Boolean = true
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    var expanded by remember { mutableStateOf(false) }
    val safeValue = value.coerceIn(0, reverbPresetNames.lastIndex)
    val selectedName = reverbPresetNames[safeValue]

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
        )

        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = { if (enabled) expanded = !expanded }
        ) {
            OutlinedTextField(
                value = "$safeValue - $selectedName",
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                textStyle = MaterialTheme.typography.bodySmall,
                shape = RoundedCornerShape(14.dp),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                reverbPresetNames.forEachIndexed { index, name ->
                    DropdownMenuItem(
                        text = { Text("$index - $name") },
                        colors = MenuDefaults.itemColors(),
                        onClick = {
                            onValueChange(index)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DspIntSliderRow(
    label: String,
    value: Int,
    valueRange: IntRange,
    step: Int = 1,
    onValueChange: (Int) -> Unit,
    enabled: Boolean = true
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = {
                val clamped = it.toInt().coerceIn(valueRange.first, valueRange.last)
                val snapped = if (step <= 1) {
                    clamped
                } else {
                    val offset = clamped - valueRange.first
                    val snappedOffset = ((offset + (step / 2)) / step) * step
                    (valueRange.first + snappedOffset).coerceIn(valueRange.first, valueRange.last)
                }
                onValueChange(snapped)
            },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = if (step <= 1) {
                (valueRange.last - valueRange.first - 1).coerceAtLeast(0)
            } else {
                (((valueRange.last - valueRange.first) / step) - 1).coerceAtLeast(0)
            },
            enabled = enabled
        )
    }
}

@Composable
private fun VolumeSliderRow(
    label: String,
    valueDb: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true
) {
    val contentAlpha = if (enabled) 1f else 0.38f

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
            Text(
                text = if (valueDb == 0f) "0.0 dB" else String.format("%.1f dB", valueDb),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onValueChange((valueDb - 1f).coerceIn(-20f, 20f)) },
                modifier = Modifier.size(36.dp),
                enabled = enabled
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease $label"
                )
            }

            Slider(
                value = valueDb,
                onValueChange = onValueChange,
                valueRange = -20f..20f,
                steps = 39,
                modifier = Modifier.weight(1f),
                enabled = enabled
            )

            IconButton(
                onClick = { onValueChange((valueDb + 1f).coerceIn(-20f, 20f)) },
                modifier = Modifier.size(36.dp),
                enabled = enabled
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase $label"
                )
            }
        }
    }
}
