package com.flopster101.siliconplayer

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.pluginsettings.GmeSettings
import com.flopster101.siliconplayer.pluginsettings.LazyUsf2Settings
import com.flopster101.siliconplayer.pluginsettings.OpenMptSettings
import com.flopster101.siliconplayer.pluginsettings.PluginSettings
import com.flopster101.siliconplayer.pluginsettings.RenderPluginSettings
import com.flopster101.siliconplayer.pluginsettings.SidPlayFpSettings
import com.flopster101.siliconplayer.pluginsettings.VgmPlaySettings
import java.util.Locale

@Composable
internal fun PluginDetailRouteContent(
    selectedPluginName: String?,
    onPluginPriorityChanged: (String, Int) -> Unit,
    onPluginExtensionsChanged: (String, Array<String>) -> Unit,
    ffmpegSampleRateHz: Int,
    onFfmpegSampleRateChanged: (Int) -> Unit,
    ffmpegCapabilities: Int,
    openMptSampleRateHz: Int,
    onOpenMptSampleRateChanged: (Int) -> Unit,
    openMptCapabilities: Int,
    vgmPlaySampleRateHz: Int,
    onVgmPlaySampleRateChanged: (Int) -> Unit,
    vgmPlayCapabilities: Int,
    gmeSampleRateHz: Int,
    onGmeSampleRateChanged: (Int) -> Unit,
    sidPlayFpSampleRateHz: Int,
    onSidPlayFpSampleRateChanged: (Int) -> Unit,
    lazyUsf2SampleRateHz: Int,
    onLazyUsf2SampleRateChanged: (Int) -> Unit,
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
    vgmPlayLoopCount: Int,
    onVgmPlayLoopCountChanged: (Int) -> Unit,
    vgmPlayAllowNonLoopingLoop: Boolean,
    onVgmPlayAllowNonLoopingLoopChanged: (Boolean) -> Unit,
    vgmPlayVsyncRate: Int,
    onVgmPlayVsyncRateChanged: (Int) -> Unit,
    vgmPlayResampleMode: Int,
    onVgmPlayResampleModeChanged: (Int) -> Unit,
    vgmPlayChipSampleMode: Int,
    onVgmPlayChipSampleModeChanged: (Int) -> Unit,
    vgmPlayChipSampleRate: Int,
    onVgmPlayChipSampleRateChanged: (Int) -> Unit,
    onOpenVgmPlayChipSettings: () -> Unit,
    gmeTempoPercent: Int,
    onGmeTempoPercentChanged: (Int) -> Unit,
    gmeStereoSeparationPercent: Int,
    onGmeStereoSeparationPercentChanged: (Int) -> Unit,
    gmeEchoEnabled: Boolean,
    onGmeEchoEnabledChanged: (Boolean) -> Unit,
    gmeAccuracyEnabled: Boolean,
    onGmeAccuracyEnabledChanged: (Boolean) -> Unit,
    gmeEqTrebleDecibel: Int,
    onGmeEqTrebleDecibelChanged: (Int) -> Unit,
    gmeEqBassHz: Int,
    onGmeEqBassHzChanged: (Int) -> Unit,
    gmeSpcUseBuiltInFade: Boolean,
    onGmeSpcUseBuiltInFadeChanged: (Boolean) -> Unit,
    gmeSpcInterpolation: Int,
    onGmeSpcInterpolationChanged: (Int) -> Unit,
    gmeSpcUseNativeSampleRate: Boolean,
    onGmeSpcUseNativeSampleRateChanged: (Boolean) -> Unit,
    sidPlayFpBackend: Int,
    onSidPlayFpBackendChanged: (Int) -> Unit,
    sidPlayFpClockMode: Int,
    onSidPlayFpClockModeChanged: (Int) -> Unit,
    sidPlayFpSidModelMode: Int,
    onSidPlayFpSidModelModeChanged: (Int) -> Unit,
    sidPlayFpFilter6581Enabled: Boolean,
    onSidPlayFpFilter6581EnabledChanged: (Boolean) -> Unit,
    sidPlayFpFilter8580Enabled: Boolean,
    onSidPlayFpFilter8580EnabledChanged: (Boolean) -> Unit,
    sidPlayFpDigiBoost8580: Boolean,
    onSidPlayFpDigiBoost8580Changed: (Boolean) -> Unit,
    sidPlayFpFilterCurve6581Percent: Int,
    onSidPlayFpFilterCurve6581PercentChanged: (Int) -> Unit,
    sidPlayFpFilterRange6581Percent: Int,
    onSidPlayFpFilterRange6581PercentChanged: (Int) -> Unit,
    sidPlayFpFilterCurve8580Percent: Int,
    onSidPlayFpFilterCurve8580PercentChanged: (Int) -> Unit,
    sidPlayFpReSidFpFastSampling: Boolean,
    onSidPlayFpReSidFpFastSamplingChanged: (Boolean) -> Unit,
    sidPlayFpReSidFpCombinedWaveformsStrength: Int,
    onSidPlayFpReSidFpCombinedWaveformsStrengthChanged: (Int) -> Unit,
    lazyUsf2UseHleAudio: Boolean,
    onLazyUsf2UseHleAudioChanged: (Boolean) -> Unit
) {
    val pluginName = selectedPluginName ?: return

    PluginDetailScreen(
        pluginName = pluginName,
        onPriorityChanged = { priority ->
            onPluginPriorityChanged(pluginName, priority)
        },
        onExtensionsChanged = { extensions ->
            onPluginExtensionsChanged(pluginName, extensions)
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
        "FFmpeg" -> ffmpegSampleRateHz
        "LibOpenMPT" -> openMptSampleRateHz
        "VGMPlay" -> vgmPlaySampleRateHz
        "Game Music Emu" -> gmeSampleRateHz
        "LibSIDPlayFP" -> sidPlayFpSampleRateHz
        "LazyUSF2" -> lazyUsf2SampleRateHz
        else -> fixedSampleRateHz
    }
    val onSampleRateSelected: ((Int) -> Unit)? = when (pluginName) {
        "FFmpeg" -> onFfmpegSampleRateChanged
        "LibOpenMPT" -> onOpenMptSampleRateChanged
        "VGMPlay" -> onVgmPlaySampleRateChanged
        "Game Music Emu" -> onGmeSampleRateChanged
        "LibSIDPlayFP" -> onSidPlayFpSampleRateChanged
        "LazyUSF2" -> onLazyUsf2SampleRateChanged
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
        "LibOpenMPT" -> OpenMptSettings(
            sampleRateHz = openMptSampleRateHz,
            capabilities = openMptCapabilities,
            stereoSeparationPercent = openMptStereoSeparationPercent,
            stereoSeparationAmigaPercent = openMptStereoSeparationAmigaPercent,
            interpolationFilterLength = openMptInterpolationFilterLength,
            amigaResamplerMode = openMptAmigaResamplerMode,
            amigaResamplerApplyAllModules = openMptAmigaResamplerApplyAllModules,
            volumeRampingStrength = openMptVolumeRampingStrength,
            ft2XmVolumeRamping = openMptFt2XmVolumeRamping,
            masterGainMilliBel = openMptMasterGainMilliBel,
            surroundEnabled = openMptSurroundEnabled,
            onSampleRateChanged = onOpenMptSampleRateChanged,
            onStereoSeparationPercentChanged = onOpenMptStereoSeparationPercentChanged,
            onStereoSeparationAmigaPercentChanged = onOpenMptStereoSeparationAmigaPercentChanged,
            onInterpolationFilterLengthChanged = onOpenMptInterpolationFilterLengthChanged,
            onAmigaResamplerModeChanged = onOpenMptAmigaResamplerModeChanged,
            onAmigaResamplerApplyAllModulesChanged = onOpenMptAmigaResamplerApplyAllModulesChanged,
            onVolumeRampingStrengthChanged = onOpenMptVolumeRampingStrengthChanged,
            onFt2XmVolumeRampingChanged = onOpenMptFt2XmVolumeRampingChanged,
            onMasterGainMilliBelChanged = onOpenMptMasterGainMilliBelChanged,
            onSurroundEnabledChanged = onOpenMptSurroundEnabledChanged,
            includeSampleRateControl = false
        )

        "VGMPlay" -> VgmPlaySettings(
            sampleRateHz = vgmPlaySampleRateHz,
            capabilities = vgmPlayCapabilities,
            loopCount = vgmPlayLoopCount,
            allowNonLoopingLoop = vgmPlayAllowNonLoopingLoop,
            vsyncRate = vgmPlayVsyncRate,
            resampleMode = vgmPlayResampleMode,
            chipSampleMode = vgmPlayChipSampleMode,
            chipSampleRate = vgmPlayChipSampleRate,
            onSampleRateChanged = onVgmPlaySampleRateChanged,
            onLoopCountChanged = onVgmPlayLoopCountChanged,
            onAllowNonLoopingLoopChanged = onVgmPlayAllowNonLoopingLoopChanged,
            onVsyncRateChanged = onVgmPlayVsyncRateChanged,
            onResampleModeChanged = onVgmPlayResampleModeChanged,
            onChipSampleModeChanged = onVgmPlayChipSampleModeChanged,
            onChipSampleRateChanged = onVgmPlayChipSampleRateChanged,
            onOpenChipSettings = onOpenVgmPlayChipSettings,
            includeSampleRateControl = false
        )

        "Game Music Emu" -> GmeSettings(
            tempoPercent = gmeTempoPercent,
            stereoSeparationPercent = gmeStereoSeparationPercent,
            echoEnabled = gmeEchoEnabled,
            accuracyEnabled = gmeAccuracyEnabled,
            eqTrebleDecibel = gmeEqTrebleDecibel,
            eqBassHz = gmeEqBassHz,
            spcUseBuiltInFade = gmeSpcUseBuiltInFade,
            spcInterpolation = gmeSpcInterpolation,
            spcUseNativeSampleRate = gmeSpcUseNativeSampleRate,
            onTempoPercentChanged = onGmeTempoPercentChanged,
            onStereoSeparationPercentChanged = onGmeStereoSeparationPercentChanged,
            onEchoEnabledChanged = onGmeEchoEnabledChanged,
            onAccuracyEnabledChanged = onGmeAccuracyEnabledChanged,
            onEqTrebleDecibelChanged = onGmeEqTrebleDecibelChanged,
            onEqBassHzChanged = onGmeEqBassHzChanged,
            onSpcUseBuiltInFadeChanged = onGmeSpcUseBuiltInFadeChanged,
            onSpcInterpolationChanged = onGmeSpcInterpolationChanged,
            onSpcUseNativeSampleRateChanged = onGmeSpcUseNativeSampleRateChanged
        )

        "LibSIDPlayFP" -> SidPlayFpSettings(
            backend = sidPlayFpBackend,
            clockMode = sidPlayFpClockMode,
            sidModelMode = sidPlayFpSidModelMode,
            filter6581Enabled = sidPlayFpFilter6581Enabled,
            filter8580Enabled = sidPlayFpFilter8580Enabled,
            digiBoost8580 = sidPlayFpDigiBoost8580,
            filterCurve6581Percent = sidPlayFpFilterCurve6581Percent,
            filterRange6581Percent = sidPlayFpFilterRange6581Percent,
            filterCurve8580Percent = sidPlayFpFilterCurve8580Percent,
            reSidFpFastSampling = sidPlayFpReSidFpFastSampling,
            reSidFpCombinedWaveformsStrength = sidPlayFpReSidFpCombinedWaveformsStrength,
            onBackendChanged = onSidPlayFpBackendChanged,
            onClockModeChanged = onSidPlayFpClockModeChanged,
            onSidModelModeChanged = onSidPlayFpSidModelModeChanged,
            onFilter6581EnabledChanged = onSidPlayFpFilter6581EnabledChanged,
            onFilter8580EnabledChanged = onSidPlayFpFilter8580EnabledChanged,
            onDigiBoost8580Changed = onSidPlayFpDigiBoost8580Changed,
            onFilterCurve6581PercentChanged = onSidPlayFpFilterCurve6581PercentChanged,
            onFilterRange6581PercentChanged = onSidPlayFpFilterRange6581PercentChanged,
            onFilterCurve8580PercentChanged = onSidPlayFpFilterCurve8580PercentChanged,
            onReSidFpFastSamplingChanged = onSidPlayFpReSidFpFastSamplingChanged,
            onReSidFpCombinedWaveformsStrengthChanged = onSidPlayFpReSidFpCombinedWaveformsStrengthChanged
        )

        "LazyUSF2" -> LazyUsf2Settings(
            useHleAudio = lazyUsf2UseHleAudio,
            onUseHleAudioChanged = onLazyUsf2UseHleAudioChanged
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
}
