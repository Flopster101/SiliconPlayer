package com.flopster101.siliconplayer

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import java.util.Locale

@Composable
internal fun AppNavigationCoreEffects(
    prefs: SharedPreferences,
    ffmpegCoreSampleRateHz: Int,
    ffmpegGaplessRepeatTrack: Boolean,
    openMptCoreSampleRateHz: Int,
    vgmPlayCoreSampleRateHz: Int,
    gmeCoreSampleRateHz: Int,
    sidPlayFpCoreSampleRateHz: Int,
    lazyUsf2CoreSampleRateHz: Int,
    lazyUsf2UseHleAudio: Boolean,
    vio2sfInterpolationQuality: Int,
    sc68SamplingRateHz: Int,
    sc68Asid: Int,
    sc68DefaultTimeSeconds: Int,
    sc68YmEngine: Int,
    sc68YmVolModel: Int,
    sc68AmigaFilter: Boolean,
    sc68AmigaBlend: Int,
    sc68AmigaClock: Int,
    sidPlayFpBackend: Int,
    sidPlayFpClockMode: Int,
    sidPlayFpSidModelMode: Int,
    sidPlayFpFilter6581Enabled: Boolean,
    sidPlayFpFilter8580Enabled: Boolean,
    sidPlayFpDigiBoost8580: Boolean,
    sidPlayFpFilterCurve6581Percent: Int,
    sidPlayFpFilterRange6581Percent: Int,
    sidPlayFpFilterCurve8580Percent: Int,
    sidPlayFpReSidFpFastSampling: Boolean,
    sidPlayFpReSidFpCombinedWaveformsStrength: Int,
    gmeTempoPercent: Int,
    gmeStereoSeparationPercent: Int,
    gmeEchoEnabled: Boolean,
    gmeAccuracyEnabled: Boolean,
    gmeEqTrebleDecibel: Int,
    gmeEqBassHz: Int,
    gmeSpcUseBuiltInFade: Boolean,
    gmeSpcInterpolation: Int,
    gmeSpcUseNativeSampleRate: Boolean,
    unknownTrackDurationSeconds: Int,
    vgmPlayLoopCount: Int,
    vgmPlayAllowNonLoopingLoop: Boolean,
    vgmPlayVsyncRate: Int,
    vgmPlayResampleMode: Int,
    vgmPlayChipSampleMode: Int,
    vgmPlayChipSampleRate: Int,
    vgmPlayChipCoreSelections: Map<String, Int>,
    openMptStereoSeparationPercent: Int,
    openMptStereoSeparationAmigaPercent: Int,
    openMptInterpolationFilterLength: Int,
    openMptAmigaResamplerMode: Int,
    openMptAmigaResamplerApplyAllModules: Boolean,
    openMptVolumeRampingStrength: Int,
    openMptFt2XmVolumeRamping: Boolean,
    openMptMasterGainMilliBel: Int,
    openMptSurroundEnabled: Boolean,
    applyCoreOptionWithPolicyFn: (
        coreName: String,
        optionName: String,
        optionValue: String,
        policy: CoreOptionApplyPolicy,
        optionLabel: String?
    ) -> Unit
) {
    fun applyCoreOptionWithPolicy(
        coreName: String,
        optionName: String,
        optionValue: String,
        policy: CoreOptionApplyPolicy,
        optionLabel: String?
    ) {
        applyCoreOptionWithPolicyFn(coreName, optionName, optionValue, policy, optionLabel)
    }

    LaunchedEffect(ffmpegCoreSampleRateHz) {
        prefs.edit().putInt(CorePreferenceKeys.CORE_RATE_FFMPEG, ffmpegCoreSampleRateHz).apply()
        NativeBridge.setCoreOutputSampleRate("FFmpeg", ffmpegCoreSampleRateHz)
    }

    LaunchedEffect(ffmpegGaplessRepeatTrack) {
        prefs.edit().putBoolean(CorePreferenceKeys.FFMPEG_GAPLESS_REPEAT_TRACK, ffmpegGaplessRepeatTrack).apply()
        applyCoreOptionWithPolicy(
            coreName = "FFmpeg",
            optionName = FfmpegOptionKeys.GAPLESS_REPEAT_TRACK,
            optionValue = ffmpegGaplessRepeatTrack.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Gapless repeat track"
        )
    }

    LaunchedEffect(openMptCoreSampleRateHz) {
        prefs.edit().putInt(CorePreferenceKeys.CORE_RATE_OPENMPT, openMptCoreSampleRateHz).apply()
        NativeBridge.setCoreOutputSampleRate("LibOpenMPT", openMptCoreSampleRateHz)
    }

    LaunchedEffect(vgmPlayCoreSampleRateHz) {
        prefs.edit().putInt(CorePreferenceKeys.CORE_RATE_VGMPLAY, vgmPlayCoreSampleRateHz).apply()
        NativeBridge.setCoreOutputSampleRate("VGMPlay", vgmPlayCoreSampleRateHz)
    }

    LaunchedEffect(gmeCoreSampleRateHz) {
        prefs.edit().putInt(CorePreferenceKeys.CORE_RATE_GME, gmeCoreSampleRateHz).apply()
        NativeBridge.setCoreOutputSampleRate("Game Music Emu", gmeCoreSampleRateHz)
    }

    LaunchedEffect(sidPlayFpCoreSampleRateHz) {
        val normalized = if (sidPlayFpCoreSampleRateHz <= 0) 0 else sidPlayFpCoreSampleRateHz
        prefs.edit().putInt(CorePreferenceKeys.CORE_RATE_SIDPLAYFP, normalized).apply()
        NativeBridge.setCoreOutputSampleRate("LibSIDPlayFP", normalized)
    }

    LaunchedEffect(lazyUsf2CoreSampleRateHz) {
        val normalized = if (lazyUsf2CoreSampleRateHz <= 0) 0 else lazyUsf2CoreSampleRateHz
        prefs.edit().putInt(CorePreferenceKeys.CORE_RATE_LAZYUSF2, normalized).apply()
        NativeBridge.setCoreOutputSampleRate("LazyUSF2", normalized)
    }

    LaunchedEffect(lazyUsf2UseHleAudio) {
        prefs.edit().putBoolean(CorePreferenceKeys.LAZYUSF2_USE_HLE_AUDIO, lazyUsf2UseHleAudio).apply()
        applyCoreOptionWithPolicy(
            coreName = "LazyUSF2",
            optionName = LazyUsf2OptionKeys.USE_HLE_AUDIO,
            optionValue = lazyUsf2UseHleAudio.toString(),
            policy = CoreOptionApplyPolicy.RequiresPlaybackRestart,
            optionLabel = "Use HLE audio"
        )
    }

    LaunchedEffect(vio2sfInterpolationQuality) {
        val normalized = vio2sfInterpolationQuality.coerceIn(0, 4)
        prefs.edit().putInt(CorePreferenceKeys.VIO2SF_INTERPOLATION_QUALITY, normalized).apply()
        applyCoreOptionWithPolicy(
            coreName = "Vio2SF",
            optionName = Vio2sfOptionKeys.INTERPOLATION_QUALITY,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Interpolation quality"
        )
    }

    LaunchedEffect(sc68SamplingRateHz) {
        val normalized = if (sc68SamplingRateHz <= 0) 0 else sc68SamplingRateHz.coerceIn(8000, 192000)
        prefs.edit().putInt(CorePreferenceKeys.CORE_RATE_SC68, normalized).apply()
        NativeBridge.setCoreOutputSampleRate("SC68", normalized)
    }

    LaunchedEffect(sc68Asid) {
        val normalized = sc68Asid.coerceIn(0, 2)
        prefs.edit().putInt(CorePreferenceKeys.SC68_ASID, normalized).apply()
        applyCoreOptionWithPolicy(
            coreName = "SC68",
            optionName = Sc68OptionKeys.ASID,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.RequiresPlaybackRestart,
            optionLabel = "aSID filter"
        )
    }

    LaunchedEffect(sc68DefaultTimeSeconds) {
        val normalized = sc68DefaultTimeSeconds.coerceIn(0, 24 * 60 * 60 - 1)
        prefs.edit().putInt(CorePreferenceKeys.SC68_DEFAULT_TIME_SECONDS, normalized).apply()
        applyCoreOptionWithPolicy(
            coreName = "SC68",
            optionName = Sc68OptionKeys.DEFAULT_TIME_SECONDS,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.RequiresPlaybackRestart,
            optionLabel = "Default track time"
        )
    }

    LaunchedEffect(sc68YmEngine) {
        val normalized = sc68YmEngine.coerceIn(0, 1)
        prefs.edit().putInt(CorePreferenceKeys.SC68_YM_ENGINE, normalized).apply()
        applyCoreOptionWithPolicy(
            coreName = "SC68",
            optionName = Sc68OptionKeys.YM_ENGINE,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.RequiresPlaybackRestart,
            optionLabel = "YM engine"
        )
    }

    LaunchedEffect(sc68YmVolModel) {
        val normalized = sc68YmVolModel.coerceIn(0, 1)
        prefs.edit().putInt(CorePreferenceKeys.SC68_YM_VOLMODEL, normalized).apply()
        applyCoreOptionWithPolicy(
            coreName = "SC68",
            optionName = Sc68OptionKeys.YM_VOLMODEL,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.RequiresPlaybackRestart,
            optionLabel = "YM volume model"
        )
    }

    LaunchedEffect(sc68AmigaFilter) {
        prefs.edit().putBoolean(CorePreferenceKeys.SC68_AMIGA_FILTER, sc68AmigaFilter).apply()
        applyCoreOptionWithPolicy(
            coreName = "SC68",
            optionName = Sc68OptionKeys.AMIGA_FILTER,
            optionValue = sc68AmigaFilter.toString(),
            policy = CoreOptionApplyPolicy.RequiresPlaybackRestart,
            optionLabel = "Amiga filter"
        )
    }

    LaunchedEffect(sc68AmigaBlend) {
        val normalized = sc68AmigaBlend.coerceIn(0, 255)
        prefs.edit().putInt(CorePreferenceKeys.SC68_AMIGA_BLEND, normalized).apply()
        applyCoreOptionWithPolicy(
            coreName = "SC68",
            optionName = Sc68OptionKeys.AMIGA_BLEND,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.RequiresPlaybackRestart,
            optionLabel = "Amiga blend"
        )
    }

    LaunchedEffect(sc68AmigaClock) {
        val normalized = sc68AmigaClock.coerceIn(0, 1)
        prefs.edit().putInt(CorePreferenceKeys.SC68_AMIGA_CLOCK, normalized).apply()
        applyCoreOptionWithPolicy(
            coreName = "SC68",
            optionName = Sc68OptionKeys.AMIGA_CLOCK,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.RequiresPlaybackRestart,
            optionLabel = "Amiga clock"
        )
    }

    LaunchedEffect(sidPlayFpBackend) {
        val normalized = sidPlayFpBackend.coerceIn(0, 2)
        prefs.edit().putInt(CorePreferenceKeys.SIDPLAYFP_BACKEND, normalized).apply()
        applyCoreOptionWithPolicy(
            coreName = "LibSIDPlayFP",
            optionName = SidPlayFpOptionKeys.BACKEND,
            optionValue = when (normalized) {
                1 -> "sidlite"
                2 -> "resid"
                else -> "residfp"
            },
            policy = CoreOptionApplyPolicy.RequiresPlaybackRestart,
            optionLabel = "Engine"
        )
    }

    LaunchedEffect(sidPlayFpClockMode) {
        val normalized = sidPlayFpClockMode.coerceIn(0, 2)
        prefs.edit().putInt(CorePreferenceKeys.SIDPLAYFP_CLOCK_MODE, normalized).apply()
        applyCoreOptionWithPolicy(
            coreName = "LibSIDPlayFP",
            optionName = SidPlayFpOptionKeys.CLOCK_MODE,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.RequiresPlaybackRestart,
            optionLabel = "Timing standard"
        )
    }

    LaunchedEffect(sidPlayFpSidModelMode) {
        val normalized = sidPlayFpSidModelMode.coerceIn(0, 2)
        prefs.edit().putInt(CorePreferenceKeys.SIDPLAYFP_SID_MODEL_MODE, normalized).apply()
        applyCoreOptionWithPolicy(
            coreName = "LibSIDPlayFP",
            optionName = SidPlayFpOptionKeys.SID_MODEL_MODE,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.RequiresPlaybackRestart,
            optionLabel = "SID model"
        )
    }

    LaunchedEffect(sidPlayFpFilter6581Enabled) {
        prefs.edit().putBoolean(CorePreferenceKeys.SIDPLAYFP_FILTER_6581_ENABLED, sidPlayFpFilter6581Enabled).apply()
        applyCoreOptionWithPolicy(
            coreName = "LibSIDPlayFP",
            optionName = SidPlayFpOptionKeys.FILTER_6581_ENABLED,
            optionValue = sidPlayFpFilter6581Enabled.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Filter for MOS6581"
        )
    }

    LaunchedEffect(sidPlayFpFilter8580Enabled) {
        prefs.edit().putBoolean(CorePreferenceKeys.SIDPLAYFP_FILTER_8580_ENABLED, sidPlayFpFilter8580Enabled).apply()
        applyCoreOptionWithPolicy(
            coreName = "LibSIDPlayFP",
            optionName = SidPlayFpOptionKeys.FILTER_8580_ENABLED,
            optionValue = sidPlayFpFilter8580Enabled.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Filter for MOS8580"
        )
    }

    LaunchedEffect(sidPlayFpDigiBoost8580) {
        prefs.edit().putBoolean(CorePreferenceKeys.SIDPLAYFP_DIGI_BOOST_8580, sidPlayFpDigiBoost8580).apply()
        applyCoreOptionWithPolicy(
            coreName = "LibSIDPlayFP",
            optionName = SidPlayFpOptionKeys.DIGI_BOOST_8580,
            optionValue = sidPlayFpDigiBoost8580.toString(),
            policy = CoreOptionApplyPolicy.RequiresPlaybackRestart,
            optionLabel = "Digi boost (8580)"
        )
    }

    LaunchedEffect(sidPlayFpFilterCurve6581Percent) {
        val normalized = sidPlayFpFilterCurve6581Percent.coerceIn(0, 100)
        prefs.edit().putInt(CorePreferenceKeys.SIDPLAYFP_FILTER_CURVE_6581, normalized).apply()
        applyCoreOptionWithPolicy(
            coreName = "LibSIDPlayFP",
            optionName = SidPlayFpOptionKeys.FILTER_CURVE_6581,
            optionValue = String.format(Locale.US, "%.2f", normalized / 100.0),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Filter curve 6581"
        )
    }

    LaunchedEffect(sidPlayFpFilterRange6581Percent) {
        val normalized = sidPlayFpFilterRange6581Percent.coerceIn(0, 100)
        prefs.edit().putInt(CorePreferenceKeys.SIDPLAYFP_FILTER_RANGE_6581, normalized).apply()
        applyCoreOptionWithPolicy(
            coreName = "LibSIDPlayFP",
            optionName = SidPlayFpOptionKeys.FILTER_RANGE_6581,
            optionValue = String.format(Locale.US, "%.2f", normalized / 100.0),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Filter range 6581"
        )
    }

    LaunchedEffect(sidPlayFpFilterCurve8580Percent) {
        val normalized = sidPlayFpFilterCurve8580Percent.coerceIn(0, 100)
        prefs.edit().putInt(CorePreferenceKeys.SIDPLAYFP_FILTER_CURVE_8580, normalized).apply()
        applyCoreOptionWithPolicy(
            coreName = "LibSIDPlayFP",
            optionName = SidPlayFpOptionKeys.FILTER_CURVE_8580,
            optionValue = String.format(Locale.US, "%.2f", normalized / 100.0),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Filter curve 8580"
        )
    }

    LaunchedEffect(sidPlayFpReSidFpFastSampling) {
        prefs.edit().putBoolean(CorePreferenceKeys.SIDPLAYFP_RESIDFP_FAST_SAMPLING, sidPlayFpReSidFpFastSampling).apply()
        applyCoreOptionWithPolicy(
            coreName = "LibSIDPlayFP",
            optionName = SidPlayFpOptionKeys.RESIDFP_FAST_SAMPLING,
            optionValue = sidPlayFpReSidFpFastSampling.toString(),
            policy = CoreOptionApplyPolicy.RequiresPlaybackRestart,
            optionLabel = "Fast sampling"
        )
    }

    LaunchedEffect(sidPlayFpReSidFpCombinedWaveformsStrength) {
        val normalized = sidPlayFpReSidFpCombinedWaveformsStrength.coerceIn(0, 2)
        prefs.edit().putInt(CorePreferenceKeys.SIDPLAYFP_RESIDFP_COMBINED_WAVEFORMS_STRENGTH, normalized).apply()
        applyCoreOptionWithPolicy(
            coreName = "LibSIDPlayFP",
            optionName = SidPlayFpOptionKeys.RESIDFP_COMBINED_WAVEFORMS_STRENGTH,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Combined waveforms"
        )
    }

    LaunchedEffect(gmeTempoPercent) {
        val normalized = gmeTempoPercent.coerceIn(50, 200)
        prefs.edit().putInt(CorePreferenceKeys.GME_TEMPO_PERCENT, normalized).apply()
        applyCoreOptionWithPolicy(
            coreName = "Game Music Emu",
            optionName = GmeOptionKeys.TEMPO,
            optionValue = String.format(Locale.US, "%.2f", normalized / 100.0),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Tempo"
        )
    }

    LaunchedEffect(gmeStereoSeparationPercent) {
        val normalized = gmeStereoSeparationPercent.coerceIn(0, 100)
        prefs.edit().putInt(CorePreferenceKeys.GME_STEREO_SEPARATION_PERCENT, normalized).apply()
        applyCoreOptionWithPolicy(
            coreName = "Game Music Emu",
            optionName = GmeOptionKeys.STEREO_SEPARATION,
            optionValue = String.format(Locale.US, "%.2f", normalized / 100.0),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Stereo separation"
        )
    }

    LaunchedEffect(gmeEchoEnabled) {
        prefs.edit().putBoolean(CorePreferenceKeys.GME_ECHO_ENABLED, gmeEchoEnabled).apply()
        applyCoreOptionWithPolicy(
            coreName = "Game Music Emu",
            optionName = GmeOptionKeys.ECHO_ENABLED,
            optionValue = gmeEchoEnabled.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "SPC echo"
        )
    }

    LaunchedEffect(gmeAccuracyEnabled) {
        prefs.edit().putBoolean(CorePreferenceKeys.GME_ACCURACY_ENABLED, gmeAccuracyEnabled).apply()
        applyCoreOptionWithPolicy(
            coreName = "Game Music Emu",
            optionName = GmeOptionKeys.ACCURACY_ENABLED,
            optionValue = gmeAccuracyEnabled.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "High accuracy emulation"
        )
    }

    LaunchedEffect(gmeEqTrebleDecibel) {
        val normalized = gmeEqTrebleDecibel.coerceIn(-50, 5)
        prefs.edit().putInt(CorePreferenceKeys.GME_EQ_TREBLE_DECIBEL, normalized).apply()
        applyCoreOptionWithPolicy(
            coreName = "Game Music Emu",
            optionName = GmeOptionKeys.EQ_TREBLE_DB,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "EQ treble"
        )
    }

    LaunchedEffect(gmeEqBassHz) {
        val normalized = gmeEqBassHz.coerceIn(1, 1000)
        prefs.edit().putInt(CorePreferenceKeys.GME_EQ_BASS_HZ, normalized).apply()
        applyCoreOptionWithPolicy(
            coreName = "Game Music Emu",
            optionName = GmeOptionKeys.EQ_BASS_HZ,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "EQ bass"
        )
    }

    LaunchedEffect(gmeSpcUseBuiltInFade) {
        prefs.edit().putBoolean(CorePreferenceKeys.GME_SPC_USE_BUILTIN_FADE, gmeSpcUseBuiltInFade).apply()
        applyCoreOptionWithPolicy(
            coreName = "Game Music Emu",
            optionName = GmeOptionKeys.SPC_USE_BUILTIN_FADE,
            optionValue = gmeSpcUseBuiltInFade.toString(),
            policy = CoreOptionApplyPolicy.RequiresPlaybackRestart,
            optionLabel = "SPC built-in fade"
        )
    }

    LaunchedEffect(gmeSpcInterpolation) {
        val normalized = gmeSpcInterpolation.coerceIn(-2, 2)
        prefs.edit().putInt(CorePreferenceKeys.GME_SPC_INTERPOLATION, normalized).apply()
        applyCoreOptionWithPolicy(
            coreName = "Game Music Emu",
            optionName = GmeOptionKeys.SPC_INTERPOLATION,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.RequiresPlaybackRestart,
            optionLabel = "SPC interpolation"
        )
    }

    LaunchedEffect(gmeSpcUseNativeSampleRate) {
        prefs.edit().putBoolean(CorePreferenceKeys.GME_SPC_USE_NATIVE_SAMPLE_RATE, gmeSpcUseNativeSampleRate).apply()
        applyCoreOptionWithPolicy(
            coreName = "Game Music Emu",
            optionName = GmeOptionKeys.SPC_USE_NATIVE_SAMPLE_RATE,
            optionValue = gmeSpcUseNativeSampleRate.toString(),
            policy = CoreOptionApplyPolicy.RequiresPlaybackRestart,
            optionLabel = "Use native SPC sample rate"
        )
    }

    LaunchedEffect(unknownTrackDurationSeconds) {
        val normalized = unknownTrackDurationSeconds.coerceIn(1, 86400)
        prefs.edit().putInt(AppPreferenceKeys.UNKNOWN_TRACK_DURATION_SECONDS, normalized).apply()
        applyCoreOptionWithPolicy(
            coreName = "Game Music Emu",
            optionName = GmeOptionKeys.UNKNOWN_DURATION_SECONDS,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Unknown track duration"
        )
        applyCoreOptionWithPolicy(
            coreName = "LibSIDPlayFP",
            optionName = SidPlayFpOptionKeys.UNKNOWN_DURATION_SECONDS,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Unknown track duration"
        )
    }

    LaunchedEffect(vgmPlayLoopCount) {
        val normalized = vgmPlayLoopCount.coerceIn(1, 99)
        prefs.edit().putInt(CorePreferenceKeys.VGMPLAY_LOOP_COUNT, normalized).apply()
        applyCoreOptionWithPolicy(
            coreName = "VGMPlay",
            optionName = VgmPlayOptionKeys.LOOP_COUNT,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Loop count"
        )
    }

    LaunchedEffect(vgmPlayAllowNonLoopingLoop) {
        prefs.edit().putBoolean(CorePreferenceKeys.VGMPLAY_ALLOW_NON_LOOPING_LOOP, vgmPlayAllowNonLoopingLoop).apply()
        applyCoreOptionWithPolicy(
            coreName = "VGMPlay",
            optionName = VgmPlayOptionKeys.ALLOW_NON_LOOPING_LOOP,
            optionValue = vgmPlayAllowNonLoopingLoop.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Allow non-looping loop"
        )
    }

    LaunchedEffect(vgmPlayVsyncRate) {
        val normalized = if (vgmPlayVsyncRate == 50 || vgmPlayVsyncRate == 60) vgmPlayVsyncRate else 0
        prefs.edit().putInt(CorePreferenceKeys.VGMPLAY_VSYNC_RATE, normalized).apply()
        applyCoreOptionWithPolicy(
            coreName = "VGMPlay",
            optionName = VgmPlayOptionKeys.VSYNC_RATE_HZ,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "VSync mode"
        )
    }

    LaunchedEffect(vgmPlayResampleMode) {
        val normalized = vgmPlayResampleMode.coerceIn(0, 2)
        prefs.edit().putInt(CorePreferenceKeys.VGMPLAY_RESAMPLE_MODE, normalized).apply()
        applyCoreOptionWithPolicy(
            coreName = "VGMPlay",
            optionName = VgmPlayOptionKeys.RESAMPLE_MODE,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.RequiresPlaybackRestart,
            optionLabel = "Resampling mode"
        )
    }

    LaunchedEffect(vgmPlayChipSampleMode) {
        val normalized = vgmPlayChipSampleMode.coerceIn(0, 2)
        prefs.edit().putInt(CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_MODE, normalized).apply()
        applyCoreOptionWithPolicy(
            coreName = "VGMPlay",
            optionName = VgmPlayOptionKeys.CHIP_SAMPLE_MODE,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.RequiresPlaybackRestart,
            optionLabel = "Chip sample mode"
        )
    }

    LaunchedEffect(vgmPlayChipSampleRate) {
        val normalized = vgmPlayChipSampleRate.coerceIn(8000, 192000)
        prefs.edit().putInt(CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_RATE, normalized).apply()
        applyCoreOptionWithPolicy(
            coreName = "VGMPlay",
            optionName = VgmPlayOptionKeys.CHIP_SAMPLE_RATE_HZ,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.RequiresPlaybackRestart,
            optionLabel = "Chip sample rate"
        )
    }

    LaunchedEffect(vgmPlayChipCoreSelections) {
        val editor = prefs.edit()
        vgmPlayChipCoreSelections.forEach { (chipKey, selectedValue) ->
            editor.putInt(CorePreferenceKeys.vgmPlayChipCoreKey(chipKey), selectedValue)
        }
        editor.apply()
        vgmPlayChipCoreSelections.forEach { (chipKey, selectedValue) ->
            applyCoreOptionWithPolicy(
                coreName = "VGMPlay",
                optionName = "${VgmPlayOptionKeys.CHIP_CORE_PREFIX}$chipKey",
                optionValue = selectedValue.toString(),
                policy = CoreOptionApplyPolicy.RequiresPlaybackRestart,
                optionLabel = "$chipKey emulator core"
            )
        }
    }

    LaunchedEffect(openMptStereoSeparationPercent) {
        prefs.edit().putInt(CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_PERCENT, openMptStereoSeparationPercent).apply()
        applyCoreOptionWithPolicy(
            "LibOpenMPT",
            "openmpt.stereo_separation_percent",
            openMptStereoSeparationPercent.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Stereo separation"
        )
    }

    LaunchedEffect(openMptStereoSeparationAmigaPercent) {
        prefs.edit().putInt(CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_AMIGA_PERCENT, openMptStereoSeparationAmigaPercent).apply()
        applyCoreOptionWithPolicy(
            "LibOpenMPT",
            "openmpt.stereo_separation_amiga_percent",
            openMptStereoSeparationAmigaPercent.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Amiga stereo separation"
        )
    }

    LaunchedEffect(openMptInterpolationFilterLength) {
        prefs.edit().putInt(CorePreferenceKeys.OPENMPT_INTERPOLATION_FILTER_LENGTH, openMptInterpolationFilterLength).apply()
        applyCoreOptionWithPolicy(
            "LibOpenMPT",
            "openmpt.interpolation_filter_length",
            openMptInterpolationFilterLength.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Interpolation filter"
        )
    }

    LaunchedEffect(openMptAmigaResamplerMode) {
        prefs.edit().putInt(CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_MODE, openMptAmigaResamplerMode).apply()
        applyCoreOptionWithPolicy(
            "LibOpenMPT",
            "openmpt.amiga_resampler_mode",
            openMptAmigaResamplerMode.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Amiga resampler"
        )
    }

    LaunchedEffect(openMptAmigaResamplerApplyAllModules) {
        prefs.edit().putBoolean(CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_APPLY_ALL_MODULES, openMptAmigaResamplerApplyAllModules).apply()
        applyCoreOptionWithPolicy(
            "LibOpenMPT",
            "openmpt.amiga_resampler_apply_all_modules",
            openMptAmigaResamplerApplyAllModules.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Apply Amiga resampler to all modules"
        )
    }

    LaunchedEffect(openMptVolumeRampingStrength) {
        prefs.edit().putInt(CorePreferenceKeys.OPENMPT_VOLUME_RAMPING_STRENGTH, openMptVolumeRampingStrength).apply()
        applyCoreOptionWithPolicy(
            "LibOpenMPT",
            "openmpt.volume_ramping_strength",
            openMptVolumeRampingStrength.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Volume ramping strength"
        )
    }

    LaunchedEffect(openMptFt2XmVolumeRamping) {
        prefs.edit().putBoolean(CorePreferenceKeys.OPENMPT_FT2_XM_VOLUME_RAMPING, openMptFt2XmVolumeRamping).apply()
        applyCoreOptionWithPolicy(
            "LibOpenMPT",
            "openmpt.ft2_xm_volume_ramping",
            openMptFt2XmVolumeRamping.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "FT2 5ms XM ramping"
        )
    }

    LaunchedEffect(openMptMasterGainMilliBel) {
        prefs.edit().putInt(CorePreferenceKeys.OPENMPT_MASTER_GAIN_MILLIBEL, openMptMasterGainMilliBel).apply()
        applyCoreOptionWithPolicy(
            "LibOpenMPT",
            "openmpt.master_gain_millibel",
            openMptMasterGainMilliBel.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Master gain"
        )
    }

    LaunchedEffect(openMptSurroundEnabled) {
        prefs.edit().putBoolean(CorePreferenceKeys.OPENMPT_SURROUND_ENABLED, openMptSurroundEnabled).apply()
        applyCoreOptionWithPolicy(
            "LibOpenMPT",
            "openmpt.surround_enabled",
            openMptSurroundEnabled.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Enable surround sound"
        )
    }
}
