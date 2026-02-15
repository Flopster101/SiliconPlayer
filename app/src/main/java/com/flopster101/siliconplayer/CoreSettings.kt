package com.flopster101.siliconplayer

object CorePreferenceKeys {
    const val CORE_RATE_FFMPEG = "core_rate_ffmpeg"
    const val CORE_RATE_OPENMPT = "core_rate_openmpt"
    const val CORE_RATE_VGMPLAY = "core_rate_vgmplay"
    const val CORE_RATE_GME = "core_rate_gme"
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
    fun vgmPlayChipCoreKey(chipKey: String) = "vgmplay_chip_core_$chipKey"
}

object OpenMptDefaults {
    const val coreSampleRateHz = 0
    const val stereoSeparationPercent = 100
    const val stereoSeparationAmigaPercent = 100
    const val interpolationFilterLength = 0
    const val amigaResamplerMode = 2
    const val amigaResamplerApplyAllModules = false
    const val volumeRampingStrength = 1
    const val ft2XmVolumeRamping = false
    const val masterGainMilliBel = 0
    const val surroundEnabled = false
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
}
