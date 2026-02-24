package com.flopster101.siliconplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

internal data class PlayerRouteState(
    val unknownTrackDurationSeconds: Int,
    val endFadeDurationMs: Int,
    val endFadeCurve: EndFadeCurve,
    val endFadeApplyToAllTracks: Boolean,
    val autoPlayOnTrackSelect: Boolean,
    val openPlayerOnTrackSelect: Boolean,
    val autoPlayNextTrackOnEnd: Boolean,
    val playlistWrapNavigation: Boolean,
    val previousRestartsAfterThreshold: Boolean,
    val fadePauseResume: Boolean,
    val openPlayerFromNotification: Boolean,
    val persistRepeatMode: Boolean,
    val keepScreenOn: Boolean,
    val playerArtworkCornerRadiusDp: Int,
    val filenameDisplayMode: FilenameDisplayMode,
    val filenameOnlyWhenTitleMissing: Boolean
)

internal data class PlayerRouteActions(
    val onUnknownTrackDurationSecondsChanged: (Int) -> Unit,
    val onEndFadeDurationMsChanged: (Int) -> Unit,
    val onEndFadeCurveChanged: (EndFadeCurve) -> Unit,
    val onEndFadeApplyToAllTracksChanged: (Boolean) -> Unit,
    val onAutoPlayOnTrackSelectChanged: (Boolean) -> Unit,
    val onOpenPlayerOnTrackSelectChanged: (Boolean) -> Unit,
    val onAutoPlayNextTrackOnEndChanged: (Boolean) -> Unit,
    val onPlaylistWrapNavigationChanged: (Boolean) -> Unit,
    val onPreviousRestartsAfterThresholdChanged: (Boolean) -> Unit,
    val onFadePauseResumeChanged: (Boolean) -> Unit,
    val onOpenPlayerFromNotificationChanged: (Boolean) -> Unit,
    val onPersistRepeatModeChanged: (Boolean) -> Unit,
    val onKeepScreenOnChanged: (Boolean) -> Unit,
    val onPlayerArtworkCornerRadiusDpChanged: (Int) -> Unit,
    val onFilenameDisplayModeChanged: (FilenameDisplayMode) -> Unit,
    val onFilenameOnlyWhenTitleMissingChanged: (Boolean) -> Unit
)

internal data class VisualizationRouteState(
    val visualizationMode: VisualizationMode,
    val enabledVisualizationModes: Set<VisualizationMode>,
    val visualizationShowDebugInfo: Boolean
)

internal data class VisualizationRouteActions(
    val onVisualizationModeChanged: (VisualizationMode) -> Unit,
    val onEnabledVisualizationModesChanged: (Set<VisualizationMode>) -> Unit,
    val onVisualizationShowDebugInfoChanged: (Boolean) -> Unit,
    val onOpenVisualizationBasic: () -> Unit,
    val onOpenVisualizationAdvanced: () -> Unit
)

internal data class VisualizationBasicRouteActions(
    val onOpenVisualizationBasicBars: () -> Unit,
    val onOpenVisualizationBasicOscilloscope: () -> Unit,
    val onOpenVisualizationBasicVuMeters: () -> Unit
)

internal data class VisualizationAdvancedRouteActions(
    val onOpenVisualizationAdvancedChannelScope: () -> Unit
)

@Composable
internal fun PlayerRouteContent(
    state: PlayerRouteState,
    actions: PlayerRouteActions
) {
    val unknownTrackDurationSeconds = state.unknownTrackDurationSeconds
    val onUnknownTrackDurationSecondsChanged = actions.onUnknownTrackDurationSecondsChanged
    val endFadeDurationMs = state.endFadeDurationMs
    val onEndFadeDurationMsChanged = actions.onEndFadeDurationMsChanged
    val endFadeCurve = state.endFadeCurve
    val onEndFadeCurveChanged = actions.onEndFadeCurveChanged
    val endFadeApplyToAllTracks = state.endFadeApplyToAllTracks
    val onEndFadeApplyToAllTracksChanged = actions.onEndFadeApplyToAllTracksChanged
    val autoPlayOnTrackSelect = state.autoPlayOnTrackSelect
    val onAutoPlayOnTrackSelectChanged = actions.onAutoPlayOnTrackSelectChanged
    val openPlayerOnTrackSelect = state.openPlayerOnTrackSelect
    val onOpenPlayerOnTrackSelectChanged = actions.onOpenPlayerOnTrackSelectChanged
    val autoPlayNextTrackOnEnd = state.autoPlayNextTrackOnEnd
    val onAutoPlayNextTrackOnEndChanged = actions.onAutoPlayNextTrackOnEndChanged
    val playlistWrapNavigation = state.playlistWrapNavigation
    val onPlaylistWrapNavigationChanged = actions.onPlaylistWrapNavigationChanged
    val previousRestartsAfterThreshold = state.previousRestartsAfterThreshold
    val onPreviousRestartsAfterThresholdChanged = actions.onPreviousRestartsAfterThresholdChanged
    val fadePauseResume = state.fadePauseResume
    val onFadePauseResumeChanged = actions.onFadePauseResumeChanged
    val openPlayerFromNotification = state.openPlayerFromNotification
    val onOpenPlayerFromNotificationChanged = actions.onOpenPlayerFromNotificationChanged
    val persistRepeatMode = state.persistRepeatMode
    val onPersistRepeatModeChanged = actions.onPersistRepeatModeChanged
    val keepScreenOn = state.keepScreenOn
    val onKeepScreenOnChanged = actions.onKeepScreenOnChanged
    val playerArtworkCornerRadiusDp = state.playerArtworkCornerRadiusDp
    val onPlayerArtworkCornerRadiusDpChanged = actions.onPlayerArtworkCornerRadiusDpChanged
    val filenameDisplayMode = state.filenameDisplayMode
    val onFilenameDisplayModeChanged = actions.onFilenameDisplayModeChanged
    val filenameOnlyWhenTitleMissing = state.filenameOnlyWhenTitleMissing
    val onFilenameOnlyWhenTitleMissingChanged = actions.onFilenameOnlyWhenTitleMissingChanged

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
        SettingsTextInputDialog(
            title = "Unknown track duration",
            fieldLabel = "Seconds",
            initialValue = unknownTrackDurationSeconds.toString(),
            supportingText = "Used when a track has no real tagged duration.",
            keyboardType = KeyboardType.Number,
            sanitizer = { value -> value.filter { it.isDigit() }.take(5) },
            onDismiss = { showUnknownDurationDialog = false },
            onConfirm = { input ->
                val parsed = input.trim().toIntOrNull()
                if (parsed != null && parsed in 1..86400) {
                    onUnknownTrackDurationSecondsChanged(parsed)
                    true
                } else {
                    false
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
        val initialFadeValue = if (endFadeDurationMs % 1000 == 0) {
            (endFadeDurationMs / 1000).toString()
        } else {
            String.format(Locale.US, "%.1f", endFadeDurationMs / 1000.0)
        }
        SettingsTextInputDialog(
            title = "Fade duration",
            fieldLabel = "Seconds",
            initialValue = initialFadeValue,
            supportingText = "0.1s to 120s (default 10.0s)",
            keyboardType = KeyboardType.Decimal,
            sanitizer = { value -> value.filter { it.isDigit() || it == '.' }.take(6) },
            onDismiss = { showEndFadeDurationDialog = false },
            onConfirm = { input ->
                val parsedSeconds = input.trim().toDoubleOrNull()
                if (parsedSeconds != null && parsedSeconds in 0.1..120.0) {
                    onEndFadeDurationMsChanged((parsedSeconds * 1000.0).roundToInt())
                    true
                } else {
                    false
                }
            }
        )
    }

    if (showEndFadeCurveDialog) {
        SettingsSingleChoiceDialog(
            title = "Fade curve",
            selectedValue = endFadeCurve,
            options = EndFadeCurve.entries.map { curve ->
                ChoiceDialogOption(value = curve, label = curve.label)
            },
            onSelected = onEndFadeCurveChanged,
            onDismiss = { showEndFadeCurveDialog = false }
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
        title = "Wrap prev/next across playlist",
        description = "When enabled, Previous on first jumps to last and Next on last jumps to first.",
        checked = playlistWrapNavigation,
        onCheckedChange = onPlaylistWrapNavigationChanged
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
        title = "Fade on pause/resume",
        description = "Apply a brief volume attenuation when pausing and resuming playback.",
        checked = fadePauseResume,
        onCheckedChange = onFadePauseResumeChanged
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
    state: VisualizationRouteState,
    actions: VisualizationRouteActions
) {
    val visualizationMode = state.visualizationMode
    val onVisualizationModeChanged = actions.onVisualizationModeChanged
    val enabledVisualizationModes = state.enabledVisualizationModes
    val onEnabledVisualizationModesChanged = actions.onEnabledVisualizationModesChanged
    val visualizationShowDebugInfo = state.visualizationShowDebugInfo
    val onVisualizationShowDebugInfoChanged = actions.onVisualizationShowDebugInfoChanged
    val onOpenVisualizationBasic = actions.onOpenVisualizationBasic
    val onOpenVisualizationAdvanced = actions.onOpenVisualizationAdvanced

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
        SettingsSingleChoiceDialog(
            title = "Visualization mode",
            selectedValue = visualizationMode,
            options = availableModes.map { mode ->
                ChoiceDialogOption(value = mode, label = mode.label)
            },
            description = "Available modes depend on enabled visualizations and current core/song.",
            onSelected = onVisualizationModeChanged,
            onDismiss = { showModeDialog = false },
            showCancelButton = false
        )
    }

    if (showEnabledDialog) {
        SettingsGroupedToggleDialog(
            title = "Enabled visualizations",
            options = buildList {
                basicPages.forEach { page ->
                    add(
                        SettingsGroupedToggleOption(
                            groupLabel = VisualizationTier.Basic.label,
                            value = page.mode,
                            label = page.title
                        )
                    )
                }
                advancedPages.forEach { page ->
                    add(
                        SettingsGroupedToggleOption(
                            groupLabel = VisualizationTier.Advanced.label,
                            value = page.mode,
                            label = page.title
                        )
                    )
                }
            },
            selectedValues = enabledVisualizationModes,
            onDismiss = { showEnabledDialog = false },
            onApply = onEnabledVisualizationModesChanged,
            emptyGroupLabels = listOf(VisualizationTier.Specialized.label),
            selectAllValues = basicPages.map { it.mode }.toSet()
        )
    }
}

@Composable
internal fun VisualizationBasicRouteContent(
    actions: VisualizationBasicRouteActions
) {
    val onOpenVisualizationBasicBars = actions.onOpenVisualizationBasicBars
    val onOpenVisualizationBasicOscilloscope = actions.onOpenVisualizationBasicOscilloscope
    val onOpenVisualizationBasicVuMeters = actions.onOpenVisualizationBasicVuMeters

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
    actions: VisualizationAdvancedRouteActions
) {
    val onOpenVisualizationAdvancedChannelScope = actions.onOpenVisualizationAdvancedChannelScope

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
