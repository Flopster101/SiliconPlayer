package com.flopster101.siliconplayer

object CorePreferenceKeys {
    const val CORE_RATE_FFMPEG = "core_rate_ffmpeg"
    const val FFMPEG_GAPLESS_REPEAT_TRACK = "ffmpeg_gapless_repeat_track"
    const val CORE_RATE_OPENMPT = "core_rate_openmpt"
    const val CORE_RATE_VGMPLAY = "core_rate_vgmplay"
    const val CORE_RATE_GME = "core_rate_gme"
    const val CORE_RATE_SIDPLAYFP = "core_rate_sidplayfp"
    const val CORE_RATE_LAZYUSF2 = "core_rate_lazyusf2"
    const val CORE_RATE_ADPLUG = "core_rate_adplug"
    const val CORE_RATE_HIVELYTRACKER = "core_rate_hivelytracker"
    const val CORE_RATE_KLYSTRACK = "core_rate_klystrack"
    const val CORE_RATE_FURNACE = "core_rate_furnace"
    const val CORE_RATE_SC68 = "core_rate_sc68"
    const val CORE_RATE_UADE = "core_rate_uade"
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
    const val SC68_ASID = "sc68_asid"
    const val SC68_DEFAULT_TIME_SECONDS = "sc68_default_time_seconds"
    const val SC68_YM_ENGINE = "sc68_ym_engine"
    const val SC68_YM_VOLMODEL = "sc68_ym_volmodel"
    const val SC68_AMIGA_FILTER = "sc68_amiga_filter"
    const val SC68_AMIGA_BLEND = "sc68_amiga_blend"
    const val SC68_AMIGA_CLOCK = "sc68_amiga_clock"
    const val UADE_FILTER_ENABLED = "uade_filter_enabled"
    const val UADE_NTSC_MODE = "uade_ntsc_mode"
    const val UADE_PANNING_MODE = "uade_panning_mode"
    const val HIVELYTRACKER_PANNING_MODE = "hivelytracker_panning_mode"
    const val HIVELYTRACKER_MIX_GAIN_PERCENT = "hivelytracker_mix_gain_percent"
    const val KLYSTRACK_PLAYER_QUALITY = "klystrack_player_quality"
    const val FURNACE_YM2612_CORE = "furnace_ym2612_core"
    const val FURNACE_SN_CORE = "furnace_sn_core"
    const val FURNACE_NES_CORE = "furnace_nes_core"
    const val FURNACE_C64_CORE = "furnace_c64_core"
    const val FURNACE_GB_QUALITY = "furnace_gb_quality"
    const val FURNACE_DSID_QUALITY = "furnace_dsid_quality"
    const val FURNACE_AY_CORE = "furnace_ay_core"
    const val ADPLUG_OPL_ENGINE = "adplug_opl_engine"
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
    const val gaplessRepeatTrack = true
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

object AdPlugDefaults {
    const val coreSampleRateHz = 0
    const val oplEngine = 2
}

object HivelyTrackerDefaults {
    const val coreSampleRateHz = 0
    const val panningMode = -1
    const val mixGainPercent = -1
}

object KlystrackDefaults {
    const val coreSampleRateHz = 0
    const val playerQuality = 2
}

object FurnaceDefaults {
    const val coreSampleRateHz = 0
    const val ym2612Core = 0
    const val snCore = 0
    const val nesCore = 0
    const val c64Core = 0
    const val gbQuality = 3
    const val dsidQuality = 3
    const val ayCore = 0
}

object Vio2sfDefaults {
    const val interpolationQuality = 4
}

object Sc68Defaults {
    const val coreSampleRateHz = 0
    const val asid = 1
    const val defaultTimeSeconds = 180
    const val ymEngine = 0
    const val ymVolModel = 0
    const val amigaFilter = true
    const val amigaBlend = 0x50
    const val amigaClock = 0
}

object UadeDefaults {
    const val coreSampleRateHz = 0
    const val filterEnabled = false
    const val ntscMode = false
    const val panningMode = 3
}
