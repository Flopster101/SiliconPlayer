package com.flopster101.siliconplayer

object CorePreferenceKeys {
    const val CORE_RATE_FFMPEG = "core_rate_ffmpeg"
    const val CORE_RATE_OPENMPT = "core_rate_openmpt"
    const val CORE_RATE_VGMPLAY = "core_rate_vgmplay"
    const val CORE_RATE_GME = "core_rate_gme"
    const val CORE_RATE_SIDPLAYFP = "core_rate_sidplayfp"
    const val CORE_RATE_LAZYUSF2 = "core_rate_lazyusf2"
    const val VIO2SF_INTERPOLATION_QUALITY = "vio2sf_interpolation_quality"
    const val OPENMPT_STEREO_SEPARATION_PERCENT = "openmpt_stereo_separation_percent"
    const val OPENMPT_STEREO_SEPARATION_AMIGA_PERCENT = "openmpt_stereo_separation_amiga_percent"
    const val OPENMPT_INTERPOLATION_FILTER_LENGTH = "openmpt_interpolation_filter_length"
    const val OPENMPT_AMIGA_RESAMPLER_MODE = "openmpt_amiga_resampler_mode"
    const val OPENMPT_AMIGA_RESAMPLER_APPLY_ALL_MODULES = "openmpt_amiga_resampler_apply_all_modules"
    const val OPENMPT_VOLUME_RAMPING_STRENGTH = "openmpt_volume_ramping_strength"
    const val OPENMPT_FT2_XM_VOLUME_RAMPING = "openmpt_ft2_xm_volume_ramping"
    const val OPENMPT_MASTER_GAIN_MILLIBEL = "openmpt_master_gain_millibel"
    const val OPENMPT_SURROUND_ENABLED = "openmpt_surround_enabled"
    const val VGMPLAY_LOOP_COUNT = "vgmplay_loop_count"
    const val VGMPLAY_ALLOW_NON_LOOPING_LOOP = "vgmplay_allow_non_looping_loop"
    const val VGMPLAY_VSYNC_RATE = "vgmplay_vsync_rate"
    const val VGMPLAY_RESAMPLE_MODE = "vgmplay_resample_mode"
    const val VGMPLAY_CHIP_SAMPLE_MODE = "vgmplay_chip_sample_mode"
    const val VGMPLAY_CHIP_SAMPLE_RATE = "vgmplay_chip_sample_rate"
    const val GME_TEMPO_PERCENT = "gme_tempo_percent"
    const val GME_STEREO_SEPARATION_PERCENT = "gme_stereo_separation_percent"
    const val GME_ECHO_ENABLED = "gme_echo_enabled"
    const val GME_ACCURACY_ENABLED = "gme_accuracy_enabled"
    const val GME_EQ_TREBLE_DECIBEL = "gme_eq_treble_decibel"
    const val GME_EQ_BASS_HZ = "gme_eq_bass_hz"
    const val GME_SPC_USE_BUILTIN_FADE = "gme_spc_use_builtin_fade"
    const val GME_SPC_INTERPOLATION = "gme_spc_interpolation"
    const val GME_SPC_USE_NATIVE_SAMPLE_RATE = "gme_spc_use_native_sample_rate"
    const val SIDPLAYFP_BACKEND = "sidplayfp_backend"
    const val SIDPLAYFP_CLOCK_MODE = "sidplayfp_clock_mode"
    const val SIDPLAYFP_SID_MODEL_MODE = "sidplayfp_sid_model_mode"
    const val SIDPLAYFP_FILTER_6581_ENABLED = "sidplayfp_filter_6581_enabled"
    const val SIDPLAYFP_FILTER_8580_ENABLED = "sidplayfp_filter_8580_enabled"
    const val SIDPLAYFP_DIGI_BOOST_8580 = "sidplayfp_digiboost_8580"
    const val SIDPLAYFP_FILTER_CURVE_6581 = "sidplayfp_filter_curve_6581"
    const val SIDPLAYFP_FILTER_RANGE_6581 = "sidplayfp_filter_range_6581"
    const val SIDPLAYFP_FILTER_CURVE_8580 = "sidplayfp_filter_curve_8580"
    const val SIDPLAYFP_RESIDFP_FAST_SAMPLING = "sidplayfp_residfp_fast_sampling"
    const val SIDPLAYFP_RESIDFP_COMBINED_WAVEFORMS_STRENGTH = "sidplayfp_residfp_combined_waveforms_strength"
    const val LAZYUSF2_USE_HLE_AUDIO = "lazyusf2_use_hle_audio"
    fun vgmPlayChipCoreKey(chipKey: String) = "vgmplay_chip_core_$chipKey"
}

object OpenMptDefaults {
    const val coreSampleRateHz = 0
    const val stereoSeparationPercent = 100
    const val stereoSeparationAmigaPercent = 100
    const val interpolationFilterLength = 0
    const val amigaResamplerMode = 3
    const val amigaResamplerApplyAllModules = false
    const val volumeRampingStrength = 1
    const val ft2XmVolumeRamping = false
    const val masterGainMilliBel = 0
    const val surroundEnabled = true
}

object FfmpegDefaults {
    const val coreSampleRateHz = 0
}

object VgmPlayDefaults {
    const val coreSampleRateHz = 0
    const val loopCount = 1
    const val allowNonLoopingLoop = false
    const val vsyncRate = 0
    const val resampleMode = 0
    const val chipSampleMode = 0
    const val chipSampleRate = 48000
}

object GmeDefaults {
    const val coreSampleRateHz = 0
    const val tempoPercent = 100
    const val stereoSeparationPercent = 0
    const val echoEnabled = true
    const val accuracyEnabled = false
    const val eqTrebleDecibel = 0
    const val eqBassHz = 90
    const val spcUseBuiltInFade = false
    const val spcInterpolation = 0
    const val spcUseNativeSampleRate = true
    const val unknownDurationSeconds = 180
}

object SidPlayFpDefaults {
    const val coreSampleRateHz = 0
    const val backend = 0 // 0 ReSIDfp, 1 SIDLite, 2 ReSID
    const val clockMode = 0 // 0 Auto, 1 PAL, 2 NTSC
    const val sidModelMode = 0 // 0 Auto, 1 MOS6581, 2 MOS8580
    const val filter6581Enabled = true
    const val filter8580Enabled = true
    const val digiBoost8580 = false
    const val filterCurve6581Percent = 50
    const val filterRange6581Percent = 50
    const val filterCurve8580Percent = 50
    const val reSidFpFastSampling = false
    const val reSidFpCombinedWaveformsStrength = 0 // 0 Average, 1 Weak, 2 Strong
}

object LazyUsf2Defaults {
    const val coreSampleRateHz = 0
    const val useHleAudio = true
}

object Vio2sfDefaults {
    const val interpolationQuality = 4
}
