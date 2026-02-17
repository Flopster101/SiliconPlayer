package com.flopster101.siliconplayer

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.pluginsettings.VgmPlayChipSettingsScreen

@Composable
internal fun RootRouteContent(
    onOpenAudioPlugins: () -> Unit,
    onOpenGeneralAudio: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenVisualization: () -> Unit,
    onOpenUrlCache: () -> Unit,
    onOpenMisc: () -> Unit,
    onOpenUi: () -> Unit,
    onOpenAbout: () -> Unit,
    onRequestClearAllSettings: () -> Unit
) {
    SettingsSectionLabel("General")
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
        title = "Home settings",
        description = "Configure recents shown on the Home page.",
        icon = Icons.Default.Home,
        onClick = onOpenHome
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsItemCard(
        title = "Visualization settings",
        description = "Configure player visualizers and rendering style.",
        icon = Icons.Default.GraphicEq,
        onClick = onOpenVisualization
    )

    Spacer(modifier = Modifier.height(16.dp))
    SettingsSectionLabel("Other")
    SettingsItemCard(
        title = "Cache settings",
        description = "Cached file behavior, limits, and cleanup.",
        icon = Icons.Default.Link,
        onClick = onOpenUrlCache
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
        onClick = onRequestClearAllSettings
    )
}

@Composable
internal fun PluginVgmPlayChipSettingsRouteContent(
    vgmPlayChipCoreSelections: Map<String, Int>,
    onVgmPlayChipCoreChanged: (String, Int) -> Unit
) {
    SettingsSectionLabel("Chip settings")
    VgmPlayChipSettingsScreen(
        chipCoreSpecs = VgmPlayConfig.chipCoreSpecs,
        chipCoreSelections = vgmPlayChipCoreSelections,
        onChipCoreChanged = onVgmPlayChipCoreChanged
    )
}

@Composable
internal fun PluginSampleRateRouteContent(
    title: String,
    description: String,
    selectedHz: Int,
    enabled: Boolean,
    onSelected: (Int) -> Unit
) {
    SampleRateSelectorCard(
        title = title,
        description = description,
        selectedHz = selectedHz,
        enabled = enabled,
        onSelected = onSelected
    )
}

@Composable
internal fun PluginOpenMptRouteContent(
    openMptSampleRateHz: Int,
    openMptCapabilities: Int,
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
    onOpenMptSampleRateChanged: (Int) -> Unit
) {
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
