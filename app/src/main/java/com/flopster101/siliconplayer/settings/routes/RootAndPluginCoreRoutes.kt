package com.flopster101.siliconplayer

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.pluginsettings.VgmPlayChipSettingsScreen

internal data class RootRouteActions(
    val onOpenAudioPlugins: () -> Unit,
    val onOpenGeneralAudio: () -> Unit,
    val onOpenPlayer: () -> Unit,
    val onOpenHome: () -> Unit,
    val onOpenFileBrowser: () -> Unit,
    val onOpenNetwork: () -> Unit,
    val onOpenVisualization: () -> Unit,
    val onOpenUrlCache: () -> Unit,
    val onOpenMisc: () -> Unit,
    val onOpenUi: () -> Unit,
    val onOpenAbout: () -> Unit,
    val onRequestClearAllSettings: () -> Unit
)

internal data class PluginVgmPlayChipSettingsRouteState(
    val vgmPlayChipCoreSelections: Map<String, Int>
)

internal data class PluginVgmPlayChipSettingsRouteActions(
    val onVgmPlayChipCoreChanged: (String, Int) -> Unit
)

internal data class PluginSampleRateRouteState(
    val title: String,
    val description: String,
    val selectedHz: Int,
    val enabled: Boolean
)

internal data class PluginSampleRateRouteActions(
    val onSelected: (Int) -> Unit
)

internal data class PluginOpenMptRouteState(
    val openMptSampleRateHz: Int,
    val openMptCapabilities: Int,
    val openMptStereoSeparationPercent: Int,
    val openMptStereoSeparationAmigaPercent: Int,
    val openMptInterpolationFilterLength: Int,
    val openMptAmigaResamplerMode: Int,
    val openMptAmigaResamplerApplyAllModules: Boolean,
    val openMptVolumeRampingStrength: Int,
    val openMptFt2XmVolumeRamping: Boolean,
    val openMptMasterGainMilliBel: Int,
    val openMptSurroundEnabled: Boolean
)

internal data class PluginOpenMptRouteActions(
    val onOpenMptStereoSeparationPercentChanged: (Int) -> Unit,
    val onOpenMptStereoSeparationAmigaPercentChanged: (Int) -> Unit,
    val onOpenMptInterpolationFilterLengthChanged: (Int) -> Unit,
    val onOpenMptAmigaResamplerModeChanged: (Int) -> Unit,
    val onOpenMptAmigaResamplerApplyAllModulesChanged: (Boolean) -> Unit,
    val onOpenMptVolumeRampingStrengthChanged: (Int) -> Unit,
    val onOpenMptFt2XmVolumeRampingChanged: (Boolean) -> Unit,
    val onOpenMptMasterGainMilliBelChanged: (Int) -> Unit,
    val onOpenMptSurroundEnabledChanged: (Boolean) -> Unit,
    val onOpenMptSampleRateChanged: (Int) -> Unit
)

@Composable
internal fun RootRouteContent(
    actions: RootRouteActions
) {
    val onOpenAudioPlugins = actions.onOpenAudioPlugins
    val onOpenGeneralAudio = actions.onOpenGeneralAudio
    val onOpenPlayer = actions.onOpenPlayer
    val onOpenHome = actions.onOpenHome
    val onOpenFileBrowser = actions.onOpenFileBrowser
    val onOpenNetwork = actions.onOpenNetwork
    val onOpenVisualization = actions.onOpenVisualization
    val onOpenUrlCache = actions.onOpenUrlCache
    val onOpenMisc = actions.onOpenMisc
    val onOpenUi = actions.onOpenUi
    val onOpenAbout = actions.onOpenAbout
    val onRequestClearAllSettings = actions.onRequestClearAllSettings

    SettingsSectionLabel("General")
    SettingsItemCard(
        title = "Audio cores",
        description = "Configure each playback core.",
        icon = Icons.Default.GraphicEq,
        onClick = onOpenAudioPlugins
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "General audio settings",
        description = "Global output and playback behavior.",
        icon = Icons.Default.Tune,
        onClick = onOpenGeneralAudio
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "Player settings",
        description = "Player behavior and interaction preferences.",
        icon = Icons.Default.Slideshow,
        onClick = onOpenPlayer
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "Home settings",
        description = "Configure recents shown on the Home page.",
        icon = Icons.Default.Home,
        onClick = onOpenHome
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "File browser settings",
        description = "Sorting and behavior preferences for the library browser.",
        icon = Icons.Default.Folder,
        onClick = onOpenFileBrowser
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "Network settings",
        description = "Manage network source behavior and saved entries.",
        icon = Icons.Default.Public,
        onClick = onOpenNetwork
    )
    SettingsRowSpacer()
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
    SettingsRowSpacer()
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
        description = "Reset app settings to defaults. Core settings are kept.",
        icon = Icons.Default.MoreHoriz,
        onClick = onRequestClearAllSettings
    )
}

@Composable
internal fun PluginVgmPlayChipSettingsRouteContent(
    state: PluginVgmPlayChipSettingsRouteState,
    actions: PluginVgmPlayChipSettingsRouteActions
) {
    val vgmPlayChipCoreSelections = state.vgmPlayChipCoreSelections
    val onVgmPlayChipCoreChanged = actions.onVgmPlayChipCoreChanged

    SettingsSectionLabel("Chip settings")
    VgmPlayChipSettingsScreen(
        chipCoreSpecs = VgmPlayConfig.chipCoreSpecs,
        chipCoreSelections = vgmPlayChipCoreSelections,
        onChipCoreChanged = onVgmPlayChipCoreChanged
    )
}

@Composable
internal fun PluginSampleRateRouteContent(
    state: PluginSampleRateRouteState,
    actions: PluginSampleRateRouteActions
) {
    val title = state.title
    val description = state.description
    val selectedHz = state.selectedHz
    val enabled = state.enabled
    val onSelected = actions.onSelected

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
    state: PluginOpenMptRouteState,
    actions: PluginOpenMptRouteActions
) {
    val openMptSampleRateHz = state.openMptSampleRateHz
    val openMptCapabilities = state.openMptCapabilities
    val openMptStereoSeparationPercent = state.openMptStereoSeparationPercent
    val onOpenMptStereoSeparationPercentChanged = actions.onOpenMptStereoSeparationPercentChanged
    val openMptStereoSeparationAmigaPercent = state.openMptStereoSeparationAmigaPercent
    val onOpenMptStereoSeparationAmigaPercentChanged = actions.onOpenMptStereoSeparationAmigaPercentChanged
    val openMptInterpolationFilterLength = state.openMptInterpolationFilterLength
    val onOpenMptInterpolationFilterLengthChanged = actions.onOpenMptInterpolationFilterLengthChanged
    val openMptAmigaResamplerMode = state.openMptAmigaResamplerMode
    val onOpenMptAmigaResamplerModeChanged = actions.onOpenMptAmigaResamplerModeChanged
    val openMptAmigaResamplerApplyAllModules = state.openMptAmigaResamplerApplyAllModules
    val onOpenMptAmigaResamplerApplyAllModulesChanged = actions.onOpenMptAmigaResamplerApplyAllModulesChanged
    val openMptVolumeRampingStrength = state.openMptVolumeRampingStrength
    val onOpenMptVolumeRampingStrengthChanged = actions.onOpenMptVolumeRampingStrengthChanged
    val openMptFt2XmVolumeRamping = state.openMptFt2XmVolumeRamping
    val onOpenMptFt2XmVolumeRampingChanged = actions.onOpenMptFt2XmVolumeRampingChanged
    val openMptMasterGainMilliBel = state.openMptMasterGainMilliBel
    val onOpenMptMasterGainMilliBelChanged = actions.onOpenMptMasterGainMilliBelChanged
    val openMptSurroundEnabled = state.openMptSurroundEnabled
    val onOpenMptSurroundEnabledChanged = actions.onOpenMptSurroundEnabledChanged
    val onOpenMptSampleRateChanged = actions.onOpenMptSampleRateChanged

    SettingsSectionLabel("Core options")
    CoreDialogSliderCard(
        title = "Stereo separation",
        description = "Sets mixer stereo separation.",
        value = openMptStereoSeparationPercent,
        valueRange = 0..200,
        step = 5,
        valueLabel = { "$it%" },
        onValueChanged = onOpenMptStereoSeparationPercentChanged
    )
    SettingsRowSpacer()
    CoreDialogSliderCard(
        title = "Amiga stereo separation",
        description = "Stereo separation used specifically for Amiga modules.",
        value = openMptStereoSeparationAmigaPercent,
        valueRange = 0..200,
        step = 5,
        valueLabel = { "$it%" },
        onValueChanged = onOpenMptStereoSeparationAmigaPercentChanged
    )
    SettingsRowSpacer()
    CoreChoiceSelectorCard(
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
    SettingsRowSpacer()
    CoreChoiceSelectorCard(
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
    SettingsRowSpacer()
    PlayerSettingToggleCard(
        title = "Apply Amiga resampler to all modules",
        description = "When disabled, Amiga resampler is used only on Amiga module formats.",
        checked = openMptAmigaResamplerApplyAllModules,
        onCheckedChange = onOpenMptAmigaResamplerApplyAllModulesChanged
    )
    SettingsRowSpacer()
    CoreVolumeRampingCard(
        title = "Volume ramping strength",
        description = "Controls smoothing strength for volume changes.",
        value = openMptVolumeRampingStrength,
        onValueChanged = onOpenMptVolumeRampingStrengthChanged
    )
    SettingsRowSpacer()
    PlayerSettingToggleCard(
        title = "FT2 5ms XM ramping",
        description = "Apply classic FT2-style 5ms ramping for XM modules only.",
        checked = openMptFt2XmVolumeRamping,
        onCheckedChange = onOpenMptFt2XmVolumeRampingChanged
    )
    SettingsRowSpacer()
    CoreDialogSliderCard(
        title = "Master gain",
        description = "Applies decoder gain before output.",
        value = openMptMasterGainMilliBel,
        valueRange = -1200..1200,
        step = 100,
        valueLabel = { formatMilliBelAsDbLabel(it) },
        onValueChanged = onOpenMptMasterGainMilliBelChanged
    )
    SettingsRowSpacer()
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
        description = "Preferred internal render sample rate for this core. Audio is resampled to the active output stream rate.",
        selectedHz = openMptSampleRateHz,
        enabled = supportsCustomSampleRate(openMptCapabilities),
        onSelected = onOpenMptSampleRateChanged
    )
}
