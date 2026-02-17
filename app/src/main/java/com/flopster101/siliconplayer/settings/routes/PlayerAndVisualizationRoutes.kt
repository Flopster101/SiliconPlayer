package com.flopster101.siliconplayer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

private enum class VisualizationTier(val label: String) {
    Basic("Basic visualizations"),
    Advanced("Advanced visualizations"),
    Specialized("Specialized visualizations")
}

private data class VisualizationSettingsPageItem(
    val route: SettingsRoute,
    val mode: VisualizationMode,
    val title: String,
    val description: String
)

private fun basicVisualizationSettingsPages(): List<VisualizationSettingsPageItem> = listOf(
    VisualizationSettingsPageItem(
        route = SettingsRoute.VisualizationBasicBars,
        mode = VisualizationMode.Bars,
        title = "Bars",
        description = "Spectrum bars over artwork or blank background."
    ),
    VisualizationSettingsPageItem(
        route = SettingsRoute.VisualizationBasicOscilloscope,
        mode = VisualizationMode.Oscilloscope,
        title = "Oscilloscope",
        description = "Waveform scope in mono or stereo."
    ),
    VisualizationSettingsPageItem(
        route = SettingsRoute.VisualizationBasicVuMeters,
        mode = VisualizationMode.VuMeters,
        title = "VU meters",
        description = "Channel level meters with configurable anchor."
    )
)

private fun advancedVisualizationSettingsPages(): List<VisualizationSettingsPageItem> = listOf(
    VisualizationSettingsPageItem(
        route = SettingsRoute.VisualizationAdvancedChannelScope,
        mode = VisualizationMode.ChannelScope,
        title = "Channel scope",
        description = "Per-channel scope-style visualization for supported cores."
    )
)

@Composable
internal fun PlayerRouteContent(
    unknownTrackDurationSeconds: Int,
    onUnknownTrackDurationSecondsChanged: (Int) -> Unit,
    endFadeDurationMs: Int,
    onEndFadeDurationMsChanged: (Int) -> Unit,
    endFadeCurve: EndFadeCurve,
    onEndFadeCurveChanged: (EndFadeCurve) -> Unit,
    endFadeApplyToAllTracks: Boolean,
    onEndFadeApplyToAllTracksChanged: (Boolean) -> Unit,
    autoPlayOnTrackSelect: Boolean,
    onAutoPlayOnTrackSelectChanged: (Boolean) -> Unit,
    openPlayerOnTrackSelect: Boolean,
    onOpenPlayerOnTrackSelectChanged: (Boolean) -> Unit,
    autoPlayNextTrackOnEnd: Boolean,
    onAutoPlayNextTrackOnEndChanged: (Boolean) -> Unit,
    previousRestartsAfterThreshold: Boolean,
    onPreviousRestartsAfterThresholdChanged: (Boolean) -> Unit,
    openPlayerFromNotification: Boolean,
    onOpenPlayerFromNotificationChanged: (Boolean) -> Unit,
    persistRepeatMode: Boolean,
    onPersistRepeatModeChanged: (Boolean) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChanged: (Boolean) -> Unit,
    playerArtworkCornerRadiusDp: Int,
    onPlayerArtworkCornerRadiusDpChanged: (Int) -> Unit,
    filenameDisplayMode: FilenameDisplayMode,
    onFilenameDisplayModeChanged: (FilenameDisplayMode) -> Unit,
    filenameOnlyWhenTitleMissing: Boolean,
    onFilenameOnlyWhenTitleMissingChanged: (Boolean) -> Unit
) {
    var showUnknownDurationDialog by remember { mutableStateOf(false) }
    var showEndFadeDurationDialog by remember { mutableStateOf(false) }
    var showEndFadeCurveDialog by remember { mutableStateOf(false) }
    var showArtworkCornerRadiusDialog by remember { mutableStateOf(false) }

    SettingsSectionLabel("Track duration fallback")
    SettingsItemCard(
        title = "Unknown track duration",
        description = "${unknownTrackDurationSeconds}s (default 180s)",
        icon = Icons.Default.MoreHoriz,
        onClick = { showUnknownDurationDialog = true }
    )
    if (showUnknownDurationDialog) {
        var input by remember { mutableStateOf(unknownTrackDurationSeconds.toString()) }
        AlertDialog(
            onDismissRequest = { showUnknownDurationDialog = false },
            title = { Text("Unknown track duration") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { value ->
                        input = value.filter { it.isDigit() }.take(5)
                    },
                    singleLine = true,
                    label = { Text("Seconds") },
                    supportingText = { Text("Used when a track has no real tagged duration.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = MaterialTheme.shapes.extraLarge
                )
            },
            dismissButton = {
                TextButton(onClick = { showUnknownDurationDialog = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = input.trim().toIntOrNull()
                    if (parsed != null && parsed in 1..86400) {
                        onUnknownTrackDurationSecondsChanged(parsed)
                        showUnknownDurationDialog = false
                    }
                }) {
                    Text("Save")
                }
            }
        )
    }

    Spacer(modifier = Modifier.height(16.dp))
    SettingsSectionLabel("End fadeout")
    PlayerSettingToggleCard(
        title = "Apply to tracks with known duration",
        description = "When disabled, end fadeout only applies to unknown/unreliable-duration tracks.",
        checked = endFadeApplyToAllTracks,
        onCheckedChange = onEndFadeApplyToAllTracksChanged
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsItemCard(
        title = "Fade duration",
        description = String.format(Locale.US, "%.1f seconds", endFadeDurationMs / 1000.0),
        icon = Icons.Default.MoreHoriz,
        onClick = { showEndFadeDurationDialog = true }
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsItemCard(
        title = "Fade curve",
        description = endFadeCurve.label,
        icon = Icons.Default.MoreHoriz,
        onClick = { showEndFadeCurveDialog = true }
    )

    if (showEndFadeDurationDialog) {
        var input by remember {
            mutableStateOf(
                if (endFadeDurationMs % 1000 == 0) {
                    (endFadeDurationMs / 1000).toString()
                } else {
                    String.format(Locale.US, "%.1f", endFadeDurationMs / 1000.0)
                }
            )
        }
        AlertDialog(
            onDismissRequest = { showEndFadeDurationDialog = false },
            title = { Text("Fade duration") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { value ->
                        input = value.filter { it.isDigit() || it == '.' }.take(6)
                    },
                    singleLine = true,
                    label = { Text("Seconds") },
                    supportingText = { Text("0.1s to 120s (default 10.0s)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.extraLarge
                )
            },
            dismissButton = {
                TextButton(onClick = { showEndFadeDurationDialog = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsedSeconds = input.trim().toDoubleOrNull()
                    if (parsedSeconds != null && parsedSeconds in 0.1..120.0) {
                        onEndFadeDurationMsChanged((parsedSeconds * 1000.0).roundToInt())
                        showEndFadeDurationDialog = false
                    }
                }) {
                    Text("Save")
                }
            }
        )
    }

    if (showEndFadeCurveDialog) {
        AlertDialog(
            onDismissRequest = { showEndFadeCurveDialog = false },
            title = { Text("Fade curve") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    EndFadeCurve.entries.forEach { curve ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    onEndFadeCurveChanged(curve)
                                    showEndFadeCurveDialog = false
                                }
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = curve == endFadeCurve,
                                onClick = {
                                    onEndFadeCurveChanged(curve)
                                    showEndFadeCurveDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(curve.label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEndFadeCurveDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    Spacer(modifier = Modifier.height(16.dp))
    SettingsSectionLabel("Track selection")
    PlayerSettingToggleCard(
        title = "Play on track select",
        description = "Start playback immediately when a file is tapped.",
        checked = autoPlayOnTrackSelect,
        onCheckedChange = onAutoPlayOnTrackSelectChanged
    )
    Spacer(modifier = Modifier.height(10.dp))
    PlayerSettingToggleCard(
        title = "Open player on track select",
        description = "Open full player when selecting a file. Disable to keep mini-player only.",
        checked = openPlayerOnTrackSelect,
        onCheckedChange = onOpenPlayerOnTrackSelectChanged
    )
    Spacer(modifier = Modifier.height(10.dp))
    PlayerSettingToggleCard(
        title = "Auto-play next track when current ends",
        description = "Automatically start the next visible track after natural playback end.",
        checked = autoPlayNextTrackOnEnd,
        onCheckedChange = onAutoPlayNextTrackOnEndChanged
    )
    Spacer(modifier = Modifier.height(10.dp))
    PlayerSettingToggleCard(
        title = "Previous track button behavior",
        description = "If more than 3 seconds have elapsed, the Previous track button restarts the current track instead of moving to the previous track.",
        checked = previousRestartsAfterThreshold,
        onCheckedChange = onPreviousRestartsAfterThresholdChanged
    )
    Spacer(modifier = Modifier.height(10.dp))
    PlayerSettingToggleCard(
        title = "Open player from notification",
        description = "When tapping playback notification, open the full player instead of normal app start destination.",
        checked = openPlayerFromNotification,
        onCheckedChange = onOpenPlayerFromNotificationChanged
    )
    Spacer(modifier = Modifier.height(10.dp))
    PlayerSettingToggleCard(
        title = "Persist repeat mode",
        description = "Keep selected repeat mode across app restarts.",
        checked = persistRepeatMode,
        onCheckedChange = onPersistRepeatModeChanged
    )
    Spacer(modifier = Modifier.height(10.dp))
    PlayerSettingToggleCard(
        title = "Keep screen on",
        description = "Prevent screen from turning off when the player is expanded.",
        checked = keepScreenOn,
        onCheckedChange = onKeepScreenOnChanged
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsValuePickerCard(
        title = "Artwork corner radius",
        description = "Rounded corner size for the player artwork/scope container.",
        value = "${playerArtworkCornerRadiusDp}dp",
        onClick = { showArtworkCornerRadiusDialog = true }
    )
    if (showArtworkCornerRadiusDialog) {
        SteppedIntSliderDialog(
            title = "Artwork corner radius",
            unitLabel = "dp",
            range = 0..48,
            step = 1,
            currentValue = playerArtworkCornerRadiusDp,
            onDismiss = { showArtworkCornerRadiusDialog = false },
            onConfirm = { value ->
                onPlayerArtworkCornerRadiusDpChanged(value.coerceIn(0, 48))
                showArtworkCornerRadiusDialog = false
            }
        )
    }
    Spacer(modifier = Modifier.height(10.dp))
    var showFilenameDisplayDialog by remember { mutableStateOf(false) }
    SettingsItemCard(
        title = "Show filename",
        description = when (filenameDisplayMode) {
            FilenameDisplayMode.Always -> "Always show filename"
            FilenameDisplayMode.Never -> "Never show filename"
            FilenameDisplayMode.TrackerOnly -> "Show for tracker/chiptune formats only"
        },
        icon = Icons.Default.MoreHoriz,
        onClick = { showFilenameDisplayDialog = true }
    )
    if (showFilenameDisplayDialog) {
        FilenameDisplayModeDialog(
            currentMode = filenameDisplayMode,
            onModeSelected = { mode ->
                onFilenameDisplayModeChanged(mode)
                showFilenameDisplayDialog = false
            },
            onDismiss = { showFilenameDisplayDialog = false }
        )
    }
    Spacer(modifier = Modifier.height(10.dp))
    PlayerSettingToggleCard(
        title = "Show filename only when title missing",
        description = "Only display filename when track has no title metadata.",
        checked = filenameOnlyWhenTitleMissing,
        onCheckedChange = onFilenameOnlyWhenTitleMissingChanged,
        enabled = filenameDisplayMode != FilenameDisplayMode.Never
    )
}

@Composable
internal fun VisualizationRouteContent(
    visualizationMode: VisualizationMode,
    onVisualizationModeChanged: (VisualizationMode) -> Unit,
    enabledVisualizationModes: Set<VisualizationMode>,
    onEnabledVisualizationModesChanged: (Set<VisualizationMode>) -> Unit,
    visualizationShowDebugInfo: Boolean,
    onVisualizationShowDebugInfoChanged: (Boolean) -> Unit,
    onOpenVisualizationBasic: () -> Unit,
    onOpenVisualizationAdvanced: () -> Unit
) {
    var showModeDialog by remember { mutableStateOf(false) }
    var showEnabledDialog by remember { mutableStateOf(false) }
    val basicPages = remember { basicVisualizationSettingsPages() }
    val advancedPages = remember { advancedVisualizationSettingsPages() }
    val allPages = remember { basicPages + advancedPages }

    SettingsSectionLabel("General")
    SettingsItemCard(
        title = "Current visualization",
        description = visualizationMode.label,
        icon = Icons.Default.GraphicEq,
        onClick = { showModeDialog = true }
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsItemCard(
        title = "Enabled visualizations",
        description = "${enabledVisualizationModes.size}/${allPages.size} modes enabled",
        icon = Icons.Default.Tune,
        onClick = { showEnabledDialog = true }
    )
    Spacer(modifier = Modifier.height(10.dp))
    PlayerSettingToggleCard(
        title = "Show debug info overlay",
        description = "Show renderer backend and frame timing/FPS overlay for visualizations.",
        checked = visualizationShowDebugInfo,
        onCheckedChange = onVisualizationShowDebugInfoChanged
    )
    Spacer(modifier = Modifier.height(16.dp))
    SettingsSectionLabel("Basic visualizations")
    Text(
        text = "These visualizations work on all cores.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsItemCard(
        title = "Basic visualization settings",
        description = "Configure Bars, Oscilloscope, and VU meters.",
        icon = Icons.Default.GraphicEq,
        onClick = onOpenVisualizationBasic
    )
    Spacer(modifier = Modifier.height(16.dp))
    SettingsSectionLabel("Advanced visualizations")
    Text(
        text = "These visualizations are core-specific.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsItemCard(
        title = "Advanced visualization settings",
        description = "Configure specialized visualizations per core.",
        icon = Icons.Default.Tune,
        onClick = onOpenVisualizationAdvanced
    )

    if (showModeDialog) {
        val availableModes = listOf(VisualizationMode.Off) + allPages
            .map { it.mode }
            .filter { enabledVisualizationModes.contains(it) }
        AlertDialog(
            onDismissRequest = { showModeDialog = false },
            title = { Text("Visualization mode") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Available modes depend on enabled visualizations and current core/song.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    availableModes.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onVisualizationModeChanged(mode)
                                    showModeDialog = false
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = mode == visualizationMode,
                                onClick = {
                                    onVisualizationModeChanged(mode)
                                    showModeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(mode.label)
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showModeDialog = false }) { Text("Close") }
            },
            confirmButton = {}
        )
    }

    if (showEnabledDialog) {
        var pendingEnabled by remember(showEnabledDialog) { mutableStateOf(enabledVisualizationModes) }
        AlertDialog(
            onDismissRequest = { showEnabledDialog = false },
            title = { Text("Enabled visualizations") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = {
                            pendingEnabled = basicPages.map { it.mode }.toSet()
                        }) {
                            Text("All")
                        }
                        OutlinedButton(onClick = { pendingEnabled = emptySet() }) {
                            Text("None")
                        }
                    }
                    VisualizationTier.entries.forEach { tier ->
                        Text(
                            text = tier.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val options = when (tier) {
                            VisualizationTier.Basic -> basicPages.map { it.mode to it.title }
                            VisualizationTier.Advanced -> advancedPages.map { it.mode to it.title }
                            VisualizationTier.Specialized -> emptyList()
                        }
                        if (options.isEmpty()) {
                            Text(
                                text = "No options yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            options.forEach { (mode, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            pendingEnabled = if (pendingEnabled.contains(mode)) {
                                                pendingEnabled - mode
                                            } else {
                                                pendingEnabled + mode
                                            }
                                        }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = pendingEnabled.contains(mode),
                                        onCheckedChange = { checked ->
                                            pendingEnabled = if (checked) {
                                                pendingEnabled + mode
                                            } else {
                                                pendingEnabled - mode
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(label)
                                }
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showEnabledDialog = false }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(onClick = {
                    onEnabledVisualizationModesChanged(pendingEnabled)
                    showEnabledDialog = false
                }) { Text("Apply") }
            }
        )
    }
}

@Composable
internal fun VisualizationBasicRouteContent(
    onOpenVisualizationBasicBars: () -> Unit,
    onOpenVisualizationBasicOscilloscope: () -> Unit,
    onOpenVisualizationBasicVuMeters: () -> Unit
) {
    val basicPages = remember { basicVisualizationSettingsPages() }
    SettingsSectionLabel("Basic visualizations")
    Text(
        text = "These visualizations work on all cores.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(10.dp))
    basicPages.forEachIndexed { index, page ->
        SettingsItemCard(
            title = page.title,
            description = page.description,
            icon = Icons.Default.GraphicEq,
            onClick = {
                when (page.route) {
                    SettingsRoute.VisualizationBasicBars -> onOpenVisualizationBasicBars()
                    SettingsRoute.VisualizationBasicOscilloscope -> onOpenVisualizationBasicOscilloscope()
                    SettingsRoute.VisualizationBasicVuMeters -> onOpenVisualizationBasicVuMeters()
                    else -> Unit
                }
            }
        )
        if (index < basicPages.lastIndex) {
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
internal fun VisualizationAdvancedRouteContent(
    onOpenVisualizationAdvancedChannelScope: () -> Unit
) {
    val advancedPages = remember { advancedVisualizationSettingsPages() }
    SettingsSectionLabel("Advanced visualizations")
    Text(
        text = "These visualizations are tied to specific decoder cores.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(10.dp))
    advancedPages.forEachIndexed { index, page ->
        SettingsItemCard(
            title = page.title,
            description = page.description,
            icon = Icons.Default.Tune,
            onClick = {
                when (page.route) {
                    SettingsRoute.VisualizationAdvancedChannelScope -> onOpenVisualizationAdvancedChannelScope()
                    else -> Unit
                }
            }
        )
        if (index < advancedPages.lastIndex) {
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
