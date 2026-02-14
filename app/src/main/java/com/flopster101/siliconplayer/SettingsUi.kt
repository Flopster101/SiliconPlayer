package com.flopster101.siliconplayer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import java.util.Locale
import kotlin.math.roundToInt

private const val SETTINGS_PAGE_NAV_DURATION_MS = 300
private val SettingsCardShape = RoundedCornerShape(16.dp)

private fun settingsRouteOrder(route: SettingsRoute): Int = when (route) {
    SettingsRoute.Root -> 0
    SettingsRoute.AudioPlugins -> 1
    SettingsRoute.PluginFfmpeg -> 2
    SettingsRoute.PluginOpenMpt -> 2
    SettingsRoute.GeneralAudio -> 1
    SettingsRoute.Player -> 1
    SettingsRoute.Misc -> 1
    SettingsRoute.Ui -> 1
    SettingsRoute.About -> 1
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    route: SettingsRoute,
    bottomContentPadding: Dp = 0.dp,
    onBack: () -> Unit,
    onOpenAudioPlugins: () -> Unit,
    onOpenGeneralAudio: () -> Unit,
    onOpenAudioEffects: () -> Unit,
    onClearAllAudioParameters: () -> Unit,
    onClearPluginAudioParameters: () -> Unit,
    onClearSongAudioParameters: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenMisc: () -> Unit,
    onOpenUi: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenFfmpeg: () -> Unit,
    onOpenOpenMpt: () -> Unit,
    autoPlayOnTrackSelect: Boolean,
    onAutoPlayOnTrackSelectChanged: (Boolean) -> Unit,
    openPlayerOnTrackSelect: Boolean,
    onOpenPlayerOnTrackSelectChanged: (Boolean) -> Unit,
    autoPlayNextTrackOnEnd: Boolean,
    onAutoPlayNextTrackOnEndChanged: (Boolean) -> Unit,
    previousRestartsAfterThreshold: Boolean,
    onPreviousRestartsAfterThresholdChanged: (Boolean) -> Unit,
    respondHeadphoneMediaButtons: Boolean,
    onRespondHeadphoneMediaButtonsChanged: (Boolean) -> Unit,
    pauseOnHeadphoneDisconnect: Boolean,
    onPauseOnHeadphoneDisconnectChanged: (Boolean) -> Unit,
    audioFocusInterrupt: Boolean,
    onAudioFocusInterruptChanged: (Boolean) -> Unit,
    audioBackendPreference: AudioBackendPreference,
    onAudioBackendPreferenceChanged: (AudioBackendPreference) -> Unit,
    audioPerformanceMode: AudioPerformanceMode,
    onAudioPerformanceModeChanged: (AudioPerformanceMode) -> Unit,
    audioBufferPreset: AudioBufferPreset,
    onAudioBufferPresetChanged: (AudioBufferPreset) -> Unit,
    audioResamplerPreference: AudioResamplerPreference,
    onAudioResamplerPreferenceChanged: (AudioResamplerPreference) -> Unit,
    audioAllowBackendFallback: Boolean,
    onAudioAllowBackendFallbackChanged: (Boolean) -> Unit,
    openPlayerFromNotification: Boolean,
    onOpenPlayerFromNotificationChanged: (Boolean) -> Unit,
    persistRepeatMode: Boolean,
    onPersistRepeatModeChanged: (Boolean) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    rememberBrowserLocation: Boolean,
    onRememberBrowserLocationChanged: (Boolean) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChanged: (Boolean) -> Unit,
    ffmpegSampleRateHz: Int,
    ffmpegCapabilities: Int,
    onFfmpegSampleRateChanged: (Int) -> Unit,
    openMptSampleRateHz: Int,
    openMptCapabilities: Int,
    onOpenMptSampleRateChanged: (Int) -> Unit,
    openMptStereoSeparationPercent: Int,
    onOpenMptStereoSeparationPercentChanged: (Int) -> Unit,
    openMptStereoSeparationAmigaPercent: Int,
    onOpenMptStereoSeparationAmigaPercentChanged: (Int) -> Unit,
    openMptInterpolationFilterLength: Int,
    onOpenMptInterpolationFilterLengthChanged: (Int) -> Unit,
    openMptAmigaResamplerMode: Int,
    onOpenMptAmigaResamplerModeChanged: (Int) -> Unit,
    openMptAmigaResamplerApplyAllModules: Boolean,
    onOpenMptAmigaResamplerApplyAllModulesChanged: (Boolean) -> Unit,
    openMptVolumeRampingStrength: Int,
    onOpenMptVolumeRampingStrengthChanged: (Int) -> Unit,
    openMptFt2XmVolumeRamping: Boolean,
    onOpenMptFt2XmVolumeRampingChanged: (Boolean) -> Unit,
    openMptMasterGainMilliBel: Int,
    onOpenMptMasterGainMilliBelChanged: (Int) -> Unit,
    openMptSurroundEnabled: Boolean,
    onOpenMptSurroundEnabledChanged: (Boolean) -> Unit,
    onClearRecentHistory: () -> Unit,
    onClearAllSettings: () -> Unit,
    onClearAllPluginSettings: () -> Unit
) {
    var pendingResetAction by remember { mutableStateOf<SettingsResetAction?>(null) }
    val secondaryTitle = when (route) {
        SettingsRoute.Root -> null
        SettingsRoute.AudioPlugins -> "Audio plugins"
        SettingsRoute.PluginFfmpeg -> "FFmpeg plugin settings"
        SettingsRoute.PluginOpenMpt -> "OpenMPT plugin settings"
        SettingsRoute.GeneralAudio -> "General audio"
        SettingsRoute.Player -> "Player settings"
        SettingsRoute.Misc -> "Misc settings"
        SettingsRoute.Ui -> "UI settings"
        SettingsRoute.About -> "About"
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedVisibility(
                visible = secondaryTitle != null,
                enter = fadeIn(animationSpec = tween(160)) + expandVertically(
                    animationSpec = tween(180, easing = LinearOutSlowInEasing),
                    expandFrom = Alignment.Top
                ),
                exit = fadeOut(animationSpec = tween(120)) + shrinkVertically(
                    animationSpec = tween(150, easing = FastOutLinearInEasing),
                    shrinkTowards = Alignment.Top
                )
            ) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = secondaryTitle.orEmpty(),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        androidx.compose.material3.HorizontalDivider()
                    }
                }
            }

            AnimatedContent(
                targetState = route,
                transitionSpec = {
                    val forward = settingsRouteOrder(targetState) >= settingsRouteOrder(initialState)
                    val enter = slideInHorizontally(
                        initialOffsetX = { fullWidth -> if (forward) fullWidth else -fullWidth / 4 },
                        animationSpec = tween(
                            durationMillis = SETTINGS_PAGE_NAV_DURATION_MS,
                            easing = FastOutSlowInEasing
                        )
                    ) + fadeIn(
                        animationSpec = tween(
                            durationMillis = 210,
                            delayMillis = 60,
                            easing = LinearOutSlowInEasing
                        )
                    )
                    val exit = slideOutHorizontally(
                        targetOffsetX = { fullWidth -> if (forward) -fullWidth / 4 else fullWidth / 4 },
                        animationSpec = tween(
                            durationMillis = SETTINGS_PAGE_NAV_DURATION_MS,
                            easing = FastOutSlowInEasing
                        )
                    ) + fadeOut(
                        animationSpec = tween(
                            durationMillis = 120,
                            easing = FastOutLinearInEasing
                        )
                    )
                    enter togetherWith exit
                },
                label = "settingsRouteTransition",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomContentPadding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    when (it) {
                    SettingsRoute.Root -> {
                        SettingsSectionLabel("Audio")
                        SettingsItemCard(
                            title = "Audio plugins",
                            description = "Configure each playback core/plugin.",
                            icon = Icons.Default.GraphicEq,
                            onClick = onOpenAudioPlugins
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsItemCard(
                            title = "General audio settings",
                            description = "Global output and playback behavior.",
                            icon = Icons.Default.Tune,
                            onClick = onOpenGeneralAudio
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsItemCard(
                            title = "Player settings",
                            description = "Player behavior and interaction preferences.",
                            icon = Icons.Default.Slideshow,
                            onClick = onOpenPlayer
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsItemCard(
                            title = "Misc settings",
                            description = "Other app-wide preferences and utilities.",
                            icon = Icons.Default.MoreHoriz,
                            onClick = onOpenMisc
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionLabel("Interface")
                        SettingsItemCard(
                            title = "UI settings",
                            description = "Appearance and layout preferences.",
                            icon = Icons.Default.Palette,
                            onClick = onOpenUi
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionLabel("Info")
                        SettingsItemCard(
                            title = "About",
                            description = "App information, versions, and credits.",
                            icon = Icons.Default.Info,
                            onClick = onOpenAbout
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionLabel("Danger zone")
                        SettingsItemCard(
                            title = "Clear all app settings",
                            description = "Reset app settings to defaults. Plugin/core settings are kept.",
                            icon = Icons.Default.MoreHoriz,
                            onClick = { pendingResetAction = SettingsResetAction.ClearAllSettings }
                        )
                    }
                    SettingsRoute.AudioPlugins -> {
                        SettingsSectionLabel("Plugin configuration")
                        Text(
                            text = "This section will also handle enabling/disabling plugins in the future.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsItemCard(
                            title = "FFmpeg",
                            description = "Core options for mainstream audio codecs.",
                            icon = Icons.Default.GraphicEq,
                            onClick = onOpenFfmpeg
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsItemCard(
                            title = "OpenMPT",
                            description = "Core options for tracker/module playback.",
                            icon = Icons.Default.GraphicEq,
                            onClick = onOpenOpenMpt
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionLabel("Danger zone")
                        SettingsItemCard(
                            title = "Clear all plugin settings",
                            description = "Reset all plugin/core settings to defaults without changing app settings.",
                            icon = Icons.Default.MoreHoriz,
                            onClick = { pendingResetAction = SettingsResetAction.ClearPluginSettings }
                        )
                    }
                    SettingsRoute.PluginFfmpeg -> SampleRateSelectorCard(
                        title = "Render sample rate",
                        description = "Preferred internal render sample rate for this plugin. Audio is resampled to the active output stream rate.",
                        selectedHz = ffmpegSampleRateHz,
                        enabled = supportsCustomSampleRate(ffmpegCapabilities),
                        onSelected = onFfmpegSampleRateChanged
                    )
                    SettingsRoute.PluginOpenMpt -> {
                        SettingsSectionLabel("Core options")
                        OpenMptDialogSliderCard(
                            title = "Stereo separation",
                            description = "Sets mixer stereo separation.",
                            value = openMptStereoSeparationPercent,
                            valueRange = 0..200,
                            step = 5,
                            valueLabel = { "$it%" },
                            onValueChanged = onOpenMptStereoSeparationPercentChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OpenMptDialogSliderCard(
                            title = "Amiga stereo separation",
                            description = "Stereo separation used specifically for Amiga modules.",
                            value = openMptStereoSeparationAmigaPercent,
                            valueRange = 0..200,
                            step = 5,
                            valueLabel = { "$it%" },
                            onValueChanged = onOpenMptStereoSeparationAmigaPercentChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OpenMptChoiceSelectorCard(
                            title = "Interpolation filter",
                            description = "Selects interpolation quality for module playback.",
                            selectedValue = openMptInterpolationFilterLength,
                            options = listOf(
                                IntChoice(0, "Auto"),
                                IntChoice(1, "None"),
                                IntChoice(2, "Linear"),
                                IntChoice(4, "Cubic"),
                                IntChoice(8, "Sinc (8-tap)")
                            ),
                            onSelected = onOpenMptInterpolationFilterLengthChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OpenMptChoiceSelectorCard(
                            title = "Amiga resampler",
                            description = "Choose Amiga resampler mode. None uses interpolation filter.",
                            selectedValue = openMptAmigaResamplerMode,
                            options = listOf(
                                IntChoice(0, "None"),
                                IntChoice(1, "Unfiltered"),
                                IntChoice(2, "Amiga 500"),
                                IntChoice(3, "Amiga 1200")
                            ),
                            onSelected = onOpenMptAmigaResamplerModeChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "Apply Amiga resampler to all modules",
                            description = "When disabled, Amiga resampler is used only on Amiga module formats.",
                            checked = openMptAmigaResamplerApplyAllModules,
                            onCheckedChange = onOpenMptAmigaResamplerApplyAllModulesChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OpenMptVolumeRampingCard(
                            title = "Volume ramping strength",
                            description = "Controls smoothing strength for volume changes.",
                            value = openMptVolumeRampingStrength,
                            onValueChanged = onOpenMptVolumeRampingStrengthChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "FT2 5ms XM ramping",
                            description = "Apply classic FT2-style 5ms ramping for XM modules only.",
                            checked = openMptFt2XmVolumeRamping,
                            onCheckedChange = onOpenMptFt2XmVolumeRampingChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OpenMptDialogSliderCard(
                            title = "Master gain",
                            description = "Applies decoder gain before output.",
                            value = openMptMasterGainMilliBel,
                            valueRange = -1200..1200,
                            step = 100,
                            valueLabel = { formatMilliBelAsDbLabel(it) },
                            onValueChanged = onOpenMptMasterGainMilliBelChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "Enable surround sound",
                            description = "Enable surround rendering mode when supported by the playback path.",
                            checked = openMptSurroundEnabled,
                            onCheckedChange = onOpenMptSurroundEnabledChanged
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionLabel("Generic output options")
                        SampleRateSelectorCard(
                            title = "Render sample rate",
                            description = "Preferred internal render sample rate for this plugin. Audio is resampled to the active output stream rate.",
                            selectedHz = openMptSampleRateHz,
                            enabled = supportsCustomSampleRate(openMptCapabilities),
                            onSelected = onOpenMptSampleRateChanged
                        )
                    }
                    SettingsRoute.GeneralAudio -> {
                        SettingsSectionLabel("Output behavior")
                        PlayerSettingToggleCard(
                            title = "Respond to headset media buttons",
                            description = "Allow headphone/bluetooth media buttons to control playback.",
                            checked = respondHeadphoneMediaButtons,
                            onCheckedChange = onRespondHeadphoneMediaButtonsChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "Pause on output disconnect",
                            description = "Pause playback when headphones/output device disconnects.",
                            checked = pauseOnHeadphoneDisconnect,
                            onCheckedChange = onPauseOnHeadphoneDisconnectChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "Allow interruption by other apps",
                            description = "Pause playback when another app starts playing audio.",
                            checked = audioFocusInterrupt,
                            onCheckedChange = onAudioFocusInterruptChanged
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionLabel("Audio processing")
                        SettingsItemCard(
                            title = "Audio effects",
                            description = "Volume controls and audio processing.",
                            icon = Icons.Default.Tune,
                            onClick = onOpenAudioEffects
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        ClearAudioParametersCard(
                            onClearAll = onClearAllAudioParameters,
                            onClearPlugins = onClearPluginAudioParameters,
                            onClearSongs = onClearSongAudioParameters
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionLabel("Audio output pipeline")
                        AudioBackendSelectorCard(
                            selectedPreference = audioBackendPreference,
                            onSelectedPreferenceChanged = onAudioBackendPreferenceChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        AudioPerformanceModeSelectorCard(
                            selectedMode = audioPerformanceMode,
                            onSelectedModeChanged = onAudioPerformanceModeChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        AudioBufferPresetSelectorCard(
                            selectedPreset = audioBufferPreset,
                            onSelectedPresetChanged = onAudioBufferPresetChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        AudioResamplerSelectorCard(
                            selectedPreference = audioResamplerPreference,
                            onSelectedPreferenceChanged = onAudioResamplerPreferenceChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "Allow backend fallback",
                            description = "If selected backend is unavailable, fall back automatically to a working output backend.",
                            checked = audioAllowBackendFallback,
                            onCheckedChange = onAudioAllowBackendFallbackChanged
                        )
                    }
                    SettingsRoute.Player -> {
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
                    }
                    SettingsRoute.Misc -> {
                        SettingsSectionLabel("Browser behavior")
                        PlayerSettingToggleCard(
                            title = "Remember browser location",
                            description = "Restore last storage and folder when reopening the library browser.",
                            checked = rememberBrowserLocation,
                            onCheckedChange = onRememberBrowserLocationChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsItemCard(
                            title = "Clear home recents",
                            description = "Remove recent folders and recently played shortcuts from the Home screen.",
                            icon = Icons.Default.MoreHoriz,
                            onClick = onClearRecentHistory
                        )
                    }
                    SettingsRoute.Ui -> ThemeModeSelectorCard(
                        selectedMode = themeMode,
                        onSelectedModeChanged = onThemeModeChanged
                    )
                    SettingsRoute.About -> AboutSettingsBody()
                }
                }
            }
        }
    }

    pendingResetAction?.let { action ->
        val title: String
        val message: String
        val confirmText: String
        val onConfirm: () -> Unit
        when (action) {
            SettingsResetAction.ClearAllSettings -> {
                title = "Clear all app settings?"
                message = "This resets app settings to defaults and keeps plugin settings unchanged."
                confirmText = "Clear app settings"
                onConfirm = onClearAllSettings
            }
            SettingsResetAction.ClearPluginSettings -> {
                title = "Clear all plugin settings?"
                message = "This resets all plugin/core settings to defaults and keeps app settings unchanged."
                confirmText = "Clear plugin settings"
                onConfirm = onClearAllPluginSettings
            }
        }

        AlertDialog(
            onDismissRequest = { pendingResetAction = null },
            title = { Text(title) },
            text = { Text(message) },
            dismissButton = {
                TextButton(onClick = { pendingResetAction = null }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onConfirm()
                    pendingResetAction = null
                }) {
                    Text(confirmText)
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun SettingsItemCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsCardShape,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
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
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlayerSettingToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsCardShape,
        onClick = { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
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
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun SettingsPlaceholderBody(
    title: String,
    description: String
) {
    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsCardShape
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
    }
}

@Composable
private fun AboutSettingsBody() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val versionLabel = "v${BuildConfig.VERSION_NAME}-${BuildConfig.GIT_SHA}"
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        androidx.compose.material3.ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Silicon Player",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = versionLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        androidx.compose.material3.ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = SettingsCardShape
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Android music player focused on mainstream and tracker/module formats.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "UI: Jetpack Compose Material 3",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "License: GPL v3",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                androidx.compose.material3.Button(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/Flopster101/SiliconPlayer")
                        )
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Open GitHub Repository")
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null
                    )
                }
            }
        }
    }
}

private data class SampleRateChoice(val hz: Int, val label: String)

private data class ThemeModeChoice(val mode: ThemeMode, val label: String)

private data class IntChoice(val value: Int, val label: String)

private data class EnumChoice<T>(val value: T, val label: String)

private fun formatMilliBelAsDbLabel(milliBel: Int): String {
    val db = milliBel / 100.0
    return String.format(Locale.US, "%+.1f dB", db)
}

enum class CoreOptionApplyPolicy {
    Live,
    RequiresPlaybackRestart
}

private enum class SettingsResetAction {
    ClearAllSettings,
    ClearPluginSettings
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun <T> SettingsEnumSelectorCard(
    title: String,
    description: String,
    selectedValue: T,
    options: List<EnumChoice<T>>,
    onSelected: (T) -> Unit
) {
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label
        ?: options.firstOrNull()?.label.orEmpty()
    var dialogOpen by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current

    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsCardShape,
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
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
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
                                        dialogOpen = false
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = option.value == selectedValue,
                                    onClick = {
                                        onSelected(option.value)
                                        dialogOpen = false
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
                TextButton(onClick = { dialogOpen = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ThemeModeSelectorCard(
    selectedMode: ThemeMode,
    onSelectedModeChanged: (ThemeMode) -> Unit
) {
    val options = listOf(
        ThemeModeChoice(ThemeMode.Auto, "Auto"),
        ThemeModeChoice(ThemeMode.Light, "Light"),
        ThemeModeChoice(ThemeMode.Dark, "Dark")
    )
    var dialogOpen by remember { mutableStateOf(false) }

    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsCardShape,
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
                    text = "App theme",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choose Auto, Light, or Dark theme behavior.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = selectedMode.label,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text("App theme") },
            text = {
                CompositionLocalProvider(
                    androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement provides false
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        options.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .clickable {
                                        onSelectedModeChanged(option.mode)
                                        dialogOpen = false
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = option.mode == selectedMode,
                                    onClick = {
                                        onSelectedModeChanged(option.mode)
                                        dialogOpen = false
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
                TextButton(onClick = { dialogOpen = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun AudioBackendSelectorCard(
    selectedPreference: AudioBackendPreference,
    onSelectedPreferenceChanged: (AudioBackendPreference) -> Unit
) {
    SettingsEnumSelectorCard(
        title = "Audio output backend",
        description = "Preferred output backend implementation.",
        selectedValue = selectedPreference,
        options = listOf(
            EnumChoice(AudioBackendPreference.Auto, AudioBackendPreference.Auto.label),
            EnumChoice(AudioBackendPreference.AAudio, AudioBackendPreference.AAudio.label),
            EnumChoice(AudioBackendPreference.OpenSLES, AudioBackendPreference.OpenSLES.label),
            EnumChoice(AudioBackendPreference.AudioTrack, AudioBackendPreference.AudioTrack.label)
        ),
        onSelected = onSelectedPreferenceChanged
    )
}

@Composable
private fun AudioPerformanceModeSelectorCard(
    selectedMode: AudioPerformanceMode,
    onSelectedModeChanged: (AudioPerformanceMode) -> Unit
) {
    SettingsEnumSelectorCard(
        title = "Audio performance mode",
        description = "Tune output stream behavior for latency vs efficiency.",
        selectedValue = selectedMode,
        options = listOf(
            EnumChoice(AudioPerformanceMode.Auto, AudioPerformanceMode.Auto.label),
            EnumChoice(AudioPerformanceMode.LowLatency, AudioPerformanceMode.LowLatency.label),
            EnumChoice(AudioPerformanceMode.None, AudioPerformanceMode.None.label),
            EnumChoice(AudioPerformanceMode.PowerSaving, AudioPerformanceMode.PowerSaving.label)
        ),
        onSelected = onSelectedModeChanged
    )
}

@Composable
private fun AudioBufferPresetSelectorCard(
    selectedPreset: AudioBufferPreset,
    onSelectedPresetChanged: (AudioBufferPreset) -> Unit
) {
    SettingsEnumSelectorCard(
        title = "Audio buffer preset",
        description = "Choose output buffer sizing profile for stability vs latency.",
        selectedValue = selectedPreset,
        options = listOf(
            EnumChoice(AudioBufferPreset.Auto, AudioBufferPreset.Auto.label),
            EnumChoice(AudioBufferPreset.Small, AudioBufferPreset.Small.label),
            EnumChoice(AudioBufferPreset.Medium, AudioBufferPreset.Medium.label),
            EnumChoice(AudioBufferPreset.Large, AudioBufferPreset.Large.label)
        ),
        onSelected = onSelectedPresetChanged
    )
}

@Composable
private fun AudioResamplerSelectorCard(
    selectedPreference: AudioResamplerPreference,
    onSelectedPreferenceChanged: (AudioResamplerPreference) -> Unit
) {
    SettingsEnumSelectorCard(
        title = "Output resampler",
        description = "Choose the output resampler. SoX is experimental and falls back to built-in for discontinuous timeline cores.",
        selectedValue = selectedPreference,
        options = listOf(
            EnumChoice(AudioResamplerPreference.BuiltIn, AudioResamplerPreference.BuiltIn.label),
            EnumChoice(AudioResamplerPreference.Sox, AudioResamplerPreference.Sox.label)
        ),
        onSelected = onSelectedPreferenceChanged
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SampleRateSelectorCard(
    title: String,
    description: String,
    selectedHz: Int,
    enabled: Boolean = true,
    onSelected: (Int) -> Unit
) {
    fun formatSampleRateLabel(hz: Int): String {
        return if (hz == 0) "Auto" else String.format(Locale.US, "%,d Hz", hz)
    }

    val configuration = LocalConfiguration.current
    val options = listOf(
        SampleRateChoice(0, formatSampleRateLabel(0)),
        SampleRateChoice(8000, formatSampleRateLabel(8000)),
        SampleRateChoice(11025, formatSampleRateLabel(11025)),
        SampleRateChoice(12000, formatSampleRateLabel(12000)),
        SampleRateChoice(16000, formatSampleRateLabel(16000)),
        SampleRateChoice(22050, formatSampleRateLabel(22050)),
        SampleRateChoice(24000, formatSampleRateLabel(24000)),
        SampleRateChoice(32000, formatSampleRateLabel(32000)),
        SampleRateChoice(44100, formatSampleRateLabel(44100)),
        SampleRateChoice(48000, formatSampleRateLabel(48000)),
        SampleRateChoice(88200, formatSampleRateLabel(88200)),
        SampleRateChoice(96000, formatSampleRateLabel(96000))
    )
    val selectedLabel = if (enabled) {
        options.firstOrNull { it.hz == selectedHz }?.label ?: "Auto"
    } else {
        "Auto (Fixed)"
    }
    var dialogOpen by remember { mutableStateOf(false) }

    val contentAlpha = if (enabled) 1f else 0.38f

    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsCardShape,
        onClick = { if (enabled) dialogOpen = true },
        enabled = enabled
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
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = selectedLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
        }
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
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
                                        onSelected(option.hz)
                                        dialogOpen = false
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = option.hz == selectedHz,
                                    onClick = {
                                        onSelected(option.hz)
                                        dialogOpen = false
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
                TextButton(onClick = { dialogOpen = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun OpenMptChoiceSelectorCard(
    title: String,
    description: String,
    selectedValue: Int,
    options: List<IntChoice>,
    onSelected: (Int) -> Unit
) {
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label ?: options.firstOrNull()?.label.orEmpty()
    var dialogOpen by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current

    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsCardShape,
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
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
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
                                        dialogOpen = false
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = option.value == selectedValue,
                                    onClick = {
                                        onSelected(option.value)
                                        dialogOpen = false
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
                TextButton(onClick = { dialogOpen = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun OpenMptDialogSliderCard(
    title: String,
    description: String,
    value: Int,
    valueRange: IntRange,
    step: Int,
    valueLabel: (Int) -> String,
    onValueChanged: (Int) -> Unit
) {
    val coercedValue = value.coerceIn(valueRange.first, valueRange.last)
    val sliderSteps = ((valueRange.last - valueRange.first) / step).coerceAtLeast(1) - 1
    var dialogOpen by remember { mutableStateOf(false) }
    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsCardShape,
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
                    Slider(
                        value = sliderValue.toFloat(),
                        onValueChange = { raw ->
                            val stepsFromStart = ((raw - valueRange.first.toFloat()) / step.toFloat()).roundToInt()
                            val snapped = valueRange.first + (stepsFromStart * step)
                            sliderValue = snapped.coerceIn(valueRange.first, valueRange.last)
                        },
                        valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                        steps = sliderSteps
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
private fun OpenMptVolumeRampingCard(
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
        shape = SettingsCardShape,
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


@Composable
private fun ClearAudioParametersCard(
    onClearAll: () -> Unit,
    onClearPlugins: () -> Unit,
    onClearSongs: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsCardShape,
        onClick = { showDialog = true }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Clear saved parameters",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reset volume settings for all, plugins, or songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Clear saved parameters") },
            text = {
                Column {
                    Text("Choose which audio parameters to reset:")
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = {
                            onClearAll()
                            showDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear all")
                    }
                    TextButton(
                        onClick = {
                            onClearPlugins()
                            showDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear plugin volumes")
                    }
                    TextButton(
                        onClick = {
                            onClearSongs()
                            showDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear song volumes")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
