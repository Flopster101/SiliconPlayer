package com.flopster101.siliconplayer

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.pluginsettings.AdPlugSettings
import com.flopster101.siliconplayer.pluginsettings.FfmpegSettings
import com.flopster101.siliconplayer.pluginsettings.GmeSettings
import com.flopster101.siliconplayer.pluginsettings.LazyUsf2Settings
import com.flopster101.siliconplayer.pluginsettings.OpenMptSettings
import com.flopster101.siliconplayer.pluginsettings.PluginSettings
import com.flopster101.siliconplayer.pluginsettings.RenderPluginSettings
import com.flopster101.siliconplayer.pluginsettings.Sc68Settings
import com.flopster101.siliconplayer.pluginsettings.SidPlayFpSettings
import com.flopster101.siliconplayer.pluginsettings.UadeSettings
import com.flopster101.siliconplayer.pluginsettings.Vio2sfSettings
import com.flopster101.siliconplayer.pluginsettings.VgmPlaySettings
import java.util.Locale

internal data class PluginDetailRouteState(
    val selectedPluginName: String?,
    val ffmpegSampleRateHz: Int,
    val ffmpegGaplessRepeatTrack: Boolean,
    val openMptSampleRateHz: Int,
    val openMptCapabilities: Int,
    val vgmPlaySampleRateHz: Int,
    val vgmPlayCapabilities: Int,
    val gmeSampleRateHz: Int,
    val sidPlayFpSampleRateHz: Int,
    val lazyUsf2SampleRateHz: Int,
    val adPlugSampleRateHz: Int,
    val uadeSampleRateHz: Int,
    val adPlugOplEngine: Int,
    val openMptStereoSeparationPercent: Int,
    val openMptStereoSeparationAmigaPercent: Int,
    val openMptInterpolationFilterLength: Int,
    val openMptAmigaResamplerMode: Int,
    val openMptAmigaResamplerApplyAllModules: Boolean,
    val openMptVolumeRampingStrength: Int,
    val openMptFt2XmVolumeRamping: Boolean,
    val openMptMasterGainMilliBel: Int,
    val openMptSurroundEnabled: Boolean,
    val vgmPlayLoopCount: Int,
    val vgmPlayAllowNonLoopingLoop: Boolean,
    val vgmPlayVsyncRate: Int,
    val vgmPlayResampleMode: Int,
    val vgmPlayChipSampleMode: Int,
    val vgmPlayChipSampleRate: Int,
    val gmeTempoPercent: Int,
    val gmeStereoSeparationPercent: Int,
    val gmeEchoEnabled: Boolean,
    val gmeAccuracyEnabled: Boolean,
    val gmeEqTrebleDecibel: Int,
    val gmeEqBassHz: Int,
    val gmeSpcUseBuiltInFade: Boolean,
    val gmeSpcInterpolation: Int,
    val gmeSpcUseNativeSampleRate: Boolean,
    val sidPlayFpBackend: Int,
    val sidPlayFpClockMode: Int,
    val sidPlayFpSidModelMode: Int,
    val sidPlayFpFilter6581Enabled: Boolean,
    val sidPlayFpFilter8580Enabled: Boolean,
    val sidPlayFpDigiBoost8580: Boolean,
    val sidPlayFpFilterCurve6581Percent: Int,
    val sidPlayFpFilterRange6581Percent: Int,
    val sidPlayFpFilterCurve8580Percent: Int,
    val sidPlayFpReSidFpFastSampling: Boolean,
    val sidPlayFpReSidFpCombinedWaveformsStrength: Int,
    val lazyUsf2UseHleAudio: Boolean,
    val vio2sfInterpolationQuality: Int,
    val sc68SamplingRateHz: Int,
    val sc68Asid: Int,
    val sc68DefaultTimeSeconds: Int,
    val sc68YmEngine: Int,
    val sc68YmVolModel: Int,
    val sc68AmigaFilter: Boolean,
    val sc68AmigaBlend: Int,
    val sc68AmigaClock: Int,
    val uadeFilterEnabled: Boolean,
    val uadeNtscMode: Boolean,
    val uadePanningMode: Int
)

internal data class PluginDetailRouteActions(
    val onPluginPriorityChanged: (String, Int) -> Unit,
    val onPluginExtensionsChanged: (String, Array<String>) -> Unit,
    val onFfmpegSampleRateChanged: (Int) -> Unit,
    val onFfmpegGaplessRepeatTrackChanged: (Boolean) -> Unit,
    val onOpenMptSampleRateChanged: (Int) -> Unit,
    val onVgmPlaySampleRateChanged: (Int) -> Unit,
    val onGmeSampleRateChanged: (Int) -> Unit,
    val onSidPlayFpSampleRateChanged: (Int) -> Unit,
    val onLazyUsf2SampleRateChanged: (Int) -> Unit,
    val onAdPlugSampleRateChanged: (Int) -> Unit,
    val onUadeSampleRateChanged: (Int) -> Unit,
    val onAdPlugOplEngineChanged: (Int) -> Unit,
    val onOpenMptStereoSeparationPercentChanged: (Int) -> Unit,
    val onOpenMptStereoSeparationAmigaPercentChanged: (Int) -> Unit,
    val onOpenMptInterpolationFilterLengthChanged: (Int) -> Unit,
    val onOpenMptAmigaResamplerModeChanged: (Int) -> Unit,
    val onOpenMptAmigaResamplerApplyAllModulesChanged: (Boolean) -> Unit,
    val onOpenMptVolumeRampingStrengthChanged: (Int) -> Unit,
    val onOpenMptFt2XmVolumeRampingChanged: (Boolean) -> Unit,
    val onOpenMptMasterGainMilliBelChanged: (Int) -> Unit,
    val onOpenMptSurroundEnabledChanged: (Boolean) -> Unit,
    val onVgmPlayLoopCountChanged: (Int) -> Unit,
    val onVgmPlayAllowNonLoopingLoopChanged: (Boolean) -> Unit,
    val onVgmPlayVsyncRateChanged: (Int) -> Unit,
    val onVgmPlayResampleModeChanged: (Int) -> Unit,
    val onVgmPlayChipSampleModeChanged: (Int) -> Unit,
    val onVgmPlayChipSampleRateChanged: (Int) -> Unit,
    val onOpenVgmPlayChipSettings: () -> Unit,
    val onGmeTempoPercentChanged: (Int) -> Unit,
    val onGmeStereoSeparationPercentChanged: (Int) -> Unit,
    val onGmeEchoEnabledChanged: (Boolean) -> Unit,
    val onGmeAccuracyEnabledChanged: (Boolean) -> Unit,
    val onGmeEqTrebleDecibelChanged: (Int) -> Unit,
    val onGmeEqBassHzChanged: (Int) -> Unit,
    val onGmeSpcUseBuiltInFadeChanged: (Boolean) -> Unit,
    val onGmeSpcInterpolationChanged: (Int) -> Unit,
    val onGmeSpcUseNativeSampleRateChanged: (Boolean) -> Unit,
    val onSidPlayFpBackendChanged: (Int) -> Unit,
    val onSidPlayFpClockModeChanged: (Int) -> Unit,
    val onSidPlayFpSidModelModeChanged: (Int) -> Unit,
    val onSidPlayFpFilter6581EnabledChanged: (Boolean) -> Unit,
    val onSidPlayFpFilter8580EnabledChanged: (Boolean) -> Unit,
    val onSidPlayFpDigiBoost8580Changed: (Boolean) -> Unit,
    val onSidPlayFpFilterCurve6581PercentChanged: (Int) -> Unit,
    val onSidPlayFpFilterRange6581PercentChanged: (Int) -> Unit,
    val onSidPlayFpFilterCurve8580PercentChanged: (Int) -> Unit,
    val onSidPlayFpReSidFpFastSamplingChanged: (Boolean) -> Unit,
    val onSidPlayFpReSidFpCombinedWaveformsStrengthChanged: (Int) -> Unit,
    val onLazyUsf2UseHleAudioChanged: (Boolean) -> Unit,
    val onVio2sfInterpolationQualityChanged: (Int) -> Unit,
    val onSc68SamplingRateHzChanged: (Int) -> Unit,
    val onSc68AsidChanged: (Int) -> Unit,
    val onSc68DefaultTimeSecondsChanged: (Int) -> Unit,
    val onSc68YmEngineChanged: (Int) -> Unit,
    val onSc68YmVolModelChanged: (Int) -> Unit,
    val onSc68AmigaFilterChanged: (Boolean) -> Unit,
    val onSc68AmigaBlendChanged: (Int) -> Unit,
    val onSc68AmigaClockChanged: (Int) -> Unit,
    val onUadeFilterEnabledChanged: (Boolean) -> Unit,
    val onUadeNtscModeChanged: (Boolean) -> Unit,
    val onUadePanningModeChanged: (Int) -> Unit
)

@Composable
internal fun PluginDetailRouteContent(
    state: PluginDetailRouteState,
    actions: PluginDetailRouteActions
) {
    val pluginName = state.selectedPluginName ?: return
    val coreAboutEntry = remember(pluginName) { AboutCatalog.resolveCoreForPlugin(pluginName) }
    var selectedAboutEntry by remember(pluginName) { mutableStateOf<AboutEntity?>(null) }

    PluginDetailScreen(
        pluginName = pluginName,
        onPriorityChanged = { priority ->
            actions.onPluginPriorityChanged(pluginName, priority)
        },
        onExtensionsChanged = { extensions ->
            actions.onPluginExtensionsChanged(pluginName, extensions)
        }
    )

    val selectedCoreCapabilities = remember(pluginName) {
        NativeBridge.getCoreCapabilities(pluginName)
    }
    val fixedSampleRateHz = remember(pluginName) {
        NativeBridge.getCoreFixedSampleRateHz(pluginName)
    }
    val supportsConfigurableRate = supportsCustomSampleRate(selectedCoreCapabilities)
    val supportsLiveRateChange = supportsLiveSampleRateChange(selectedCoreCapabilities)
    val hasFixedRate = hasFixedSampleRate(selectedCoreCapabilities) && fixedSampleRateHz > 0
    val selectedRateHz = when (pluginName) {
        "FFmpeg" -> state.ffmpegSampleRateHz
        "LibOpenMPT" -> state.openMptSampleRateHz
        "VGMPlay" -> state.vgmPlaySampleRateHz
        "Game Music Emu" -> state.gmeSampleRateHz
        "LibSIDPlayFP" -> state.sidPlayFpSampleRateHz
        "LazyUSF2" -> state.lazyUsf2SampleRateHz
        "AdPlug" -> state.adPlugSampleRateHz
        "UADE" -> state.uadeSampleRateHz
        "SC68" -> state.sc68SamplingRateHz
        else -> fixedSampleRateHz
    }
    val onSampleRateSelected: ((Int) -> Unit)? = when (pluginName) {
        "FFmpeg" -> actions.onFfmpegSampleRateChanged
        "LibOpenMPT" -> actions.onOpenMptSampleRateChanged
        "VGMPlay" -> actions.onVgmPlaySampleRateChanged
        "Game Music Emu" -> actions.onGmeSampleRateChanged
        "LibSIDPlayFP" -> actions.onSidPlayFpSampleRateChanged
        "LazyUSF2" -> actions.onLazyUsf2SampleRateChanged
        "AdPlug" -> actions.onAdPlugSampleRateChanged
        "UADE" -> actions.onUadeSampleRateChanged
        "SC68" -> actions.onSc68SamplingRateHzChanged
        else -> null
    }
    val fixedRateLabel = if (fixedSampleRateHz > 0) {
        if (fixedSampleRateHz % 1000 == 0) {
            "${fixedSampleRateHz / 1000} kHz"
        } else {
            String.format(Locale.US, "%.1f kHz", fixedSampleRateHz / 1000.0)
        }
    } else {
        "unknown"
    }
    val sampleRateDescription = when {
        hasFixedRate -> "Internal render rate for this core: $fixedRateLabel."
        else -> "Preferred internal render sample rate for this core. Audio is resampled to the active output stream rate."
    }
    val sampleRateStatus = when {
        hasFixedRate -> "Fixed rate"
        supportsConfigurableRate && supportsLiveRateChange -> "Applies immediately"
        supportsConfigurableRate -> "Playback restart required"
        else -> "Not configurable"
    }

    val pluginSettings: PluginSettings? = when (pluginName) {
        "FFmpeg" -> FfmpegSettings(
            sampleRateHz = state.ffmpegSampleRateHz,
            capabilities = selectedCoreCapabilities,
            gaplessRepeatTrack = state.ffmpegGaplessRepeatTrack,
            onSampleRateChanged = actions.onFfmpegSampleRateChanged,
            onGaplessRepeatTrackChanged = actions.onFfmpegGaplessRepeatTrackChanged
        )

        "LibOpenMPT" -> OpenMptSettings(
            sampleRateHz = state.openMptSampleRateHz,
            capabilities = state.openMptCapabilities,
            stereoSeparationPercent = state.openMptStereoSeparationPercent,
            stereoSeparationAmigaPercent = state.openMptStereoSeparationAmigaPercent,
            interpolationFilterLength = state.openMptInterpolationFilterLength,
            amigaResamplerMode = state.openMptAmigaResamplerMode,
            amigaResamplerApplyAllModules = state.openMptAmigaResamplerApplyAllModules,
            volumeRampingStrength = state.openMptVolumeRampingStrength,
            ft2XmVolumeRamping = state.openMptFt2XmVolumeRamping,
            masterGainMilliBel = state.openMptMasterGainMilliBel,
            surroundEnabled = state.openMptSurroundEnabled,
            onSampleRateChanged = actions.onOpenMptSampleRateChanged,
            onStereoSeparationPercentChanged = actions.onOpenMptStereoSeparationPercentChanged,
            onStereoSeparationAmigaPercentChanged = actions.onOpenMptStereoSeparationAmigaPercentChanged,
            onInterpolationFilterLengthChanged = actions.onOpenMptInterpolationFilterLengthChanged,
            onAmigaResamplerModeChanged = actions.onOpenMptAmigaResamplerModeChanged,
            onAmigaResamplerApplyAllModulesChanged = actions.onOpenMptAmigaResamplerApplyAllModulesChanged,
            onVolumeRampingStrengthChanged = actions.onOpenMptVolumeRampingStrengthChanged,
            onFt2XmVolumeRampingChanged = actions.onOpenMptFt2XmVolumeRampingChanged,
            onMasterGainMilliBelChanged = actions.onOpenMptMasterGainMilliBelChanged,
            onSurroundEnabledChanged = actions.onOpenMptSurroundEnabledChanged,
            includeSampleRateControl = false
        )

        "VGMPlay" -> VgmPlaySettings(
            sampleRateHz = state.vgmPlaySampleRateHz,
            capabilities = state.vgmPlayCapabilities,
            loopCount = state.vgmPlayLoopCount,
            allowNonLoopingLoop = state.vgmPlayAllowNonLoopingLoop,
            vsyncRate = state.vgmPlayVsyncRate,
            resampleMode = state.vgmPlayResampleMode,
            chipSampleMode = state.vgmPlayChipSampleMode,
            chipSampleRate = state.vgmPlayChipSampleRate,
            onSampleRateChanged = actions.onVgmPlaySampleRateChanged,
            onLoopCountChanged = actions.onVgmPlayLoopCountChanged,
            onAllowNonLoopingLoopChanged = actions.onVgmPlayAllowNonLoopingLoopChanged,
            onVsyncRateChanged = actions.onVgmPlayVsyncRateChanged,
            onResampleModeChanged = actions.onVgmPlayResampleModeChanged,
            onChipSampleModeChanged = actions.onVgmPlayChipSampleModeChanged,
            onChipSampleRateChanged = actions.onVgmPlayChipSampleRateChanged,
            onOpenChipSettings = actions.onOpenVgmPlayChipSettings,
            includeSampleRateControl = false
        )

        "Game Music Emu" -> GmeSettings(
            tempoPercent = state.gmeTempoPercent,
            stereoSeparationPercent = state.gmeStereoSeparationPercent,
            echoEnabled = state.gmeEchoEnabled,
            accuracyEnabled = state.gmeAccuracyEnabled,
            eqTrebleDecibel = state.gmeEqTrebleDecibel,
            eqBassHz = state.gmeEqBassHz,
            spcUseBuiltInFade = state.gmeSpcUseBuiltInFade,
            spcInterpolation = state.gmeSpcInterpolation,
            spcUseNativeSampleRate = state.gmeSpcUseNativeSampleRate,
            onTempoPercentChanged = actions.onGmeTempoPercentChanged,
            onStereoSeparationPercentChanged = actions.onGmeStereoSeparationPercentChanged,
            onEchoEnabledChanged = actions.onGmeEchoEnabledChanged,
            onAccuracyEnabledChanged = actions.onGmeAccuracyEnabledChanged,
            onEqTrebleDecibelChanged = actions.onGmeEqTrebleDecibelChanged,
            onEqBassHzChanged = actions.onGmeEqBassHzChanged,
            onSpcUseBuiltInFadeChanged = actions.onGmeSpcUseBuiltInFadeChanged,
            onSpcInterpolationChanged = actions.onGmeSpcInterpolationChanged,
            onSpcUseNativeSampleRateChanged = actions.onGmeSpcUseNativeSampleRateChanged
        )

        "LibSIDPlayFP" -> SidPlayFpSettings(
            backend = state.sidPlayFpBackend,
            clockMode = state.sidPlayFpClockMode,
            sidModelMode = state.sidPlayFpSidModelMode,
            filter6581Enabled = state.sidPlayFpFilter6581Enabled,
            filter8580Enabled = state.sidPlayFpFilter8580Enabled,
            digiBoost8580 = state.sidPlayFpDigiBoost8580,
            filterCurve6581Percent = state.sidPlayFpFilterCurve6581Percent,
            filterRange6581Percent = state.sidPlayFpFilterRange6581Percent,
            filterCurve8580Percent = state.sidPlayFpFilterCurve8580Percent,
            reSidFpFastSampling = state.sidPlayFpReSidFpFastSampling,
            reSidFpCombinedWaveformsStrength = state.sidPlayFpReSidFpCombinedWaveformsStrength,
            onBackendChanged = actions.onSidPlayFpBackendChanged,
            onClockModeChanged = actions.onSidPlayFpClockModeChanged,
            onSidModelModeChanged = actions.onSidPlayFpSidModelModeChanged,
            onFilter6581EnabledChanged = actions.onSidPlayFpFilter6581EnabledChanged,
            onFilter8580EnabledChanged = actions.onSidPlayFpFilter8580EnabledChanged,
            onDigiBoost8580Changed = actions.onSidPlayFpDigiBoost8580Changed,
            onFilterCurve6581PercentChanged = actions.onSidPlayFpFilterCurve6581PercentChanged,
            onFilterRange6581PercentChanged = actions.onSidPlayFpFilterRange6581PercentChanged,
            onFilterCurve8580PercentChanged = actions.onSidPlayFpFilterCurve8580PercentChanged,
            onReSidFpFastSamplingChanged = actions.onSidPlayFpReSidFpFastSamplingChanged,
            onReSidFpCombinedWaveformsStrengthChanged = actions.onSidPlayFpReSidFpCombinedWaveformsStrengthChanged
        )

        "LazyUSF2" -> LazyUsf2Settings(
            useHleAudio = state.lazyUsf2UseHleAudio,
            onUseHleAudioChanged = actions.onLazyUsf2UseHleAudioChanged
        )

        "AdPlug" -> AdPlugSettings(
            oplEngine = state.adPlugOplEngine,
            onOplEngineChanged = actions.onAdPlugOplEngineChanged
        )

        "Vio2SF" -> Vio2sfSettings(
            interpolationQuality = state.vio2sfInterpolationQuality,
            onInterpolationQualityChanged = actions.onVio2sfInterpolationQualityChanged
        )

        "SC68" -> Sc68Settings(
            asid = state.sc68Asid,
            ymEngine = state.sc68YmEngine,
            ymVolModel = state.sc68YmVolModel,
            amigaFilter = state.sc68AmigaFilter,
            amigaBlend = state.sc68AmigaBlend,
            amigaClock = state.sc68AmigaClock,
            onAsidChanged = actions.onSc68AsidChanged,
            onYmEngineChanged = actions.onSc68YmEngineChanged,
            onYmVolModelChanged = actions.onSc68YmVolModelChanged,
            onAmigaFilterChanged = actions.onSc68AmigaFilterChanged,
            onAmigaBlendChanged = actions.onSc68AmigaBlendChanged,
            onAmigaClockChanged = actions.onSc68AmigaClockChanged
        )

        "UADE" -> UadeSettings(
            filterEnabled = state.uadeFilterEnabled,
            ntscMode = state.uadeNtscMode,
            panningMode = state.uadePanningMode,
            onFilterEnabledChanged = actions.onUadeFilterEnabledChanged,
            onNtscModeChanged = actions.onUadeNtscModeChanged,
            onPanningModeChanged = actions.onUadePanningModeChanged
        )

        else -> null
    }

    if (pluginSettings != null) {
        RenderPluginSettings(
            pluginSettings = pluginSettings,
            settingsSectionLabel = { label -> SettingsSectionLabel(label) }
        )
    }

    Spacer(modifier = Modifier.height(16.dp))
    SettingsSectionLabel("Generic output options")
    SampleRateSelectorCard(
        title = "Render sample rate",
        description = sampleRateDescription,
        selectedHz = if (hasFixedRate) fixedSampleRateHz else selectedRateHz,
        statusText = sampleRateStatus,
        enabled = supportsConfigurableRate && onSampleRateSelected != null,
        onSelected = { hz -> onSampleRateSelected?.invoke(hz) }
    )

    if (coreAboutEntry != null) {
        Spacer(modifier = Modifier.height(16.dp))
        SettingsSectionLabel("Info")
        SettingsItemCard(
            title = "About this core",
            description = "Credits, license info, upstream links, and integration notes.",
            icon = Icons.Default.Info,
            onClick = { selectedAboutEntry = coreAboutEntry }
        )
    }

    selectedAboutEntry?.let { entity ->
        AboutEntityDialog(
            entity = entity,
            onDismiss = { selectedAboutEntry = null }
        )
    }
}
