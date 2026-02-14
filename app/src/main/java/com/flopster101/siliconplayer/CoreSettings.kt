package com.flopster101.siliconplayer

object CorePreferenceKeys {
    const val CORE_RATE_FFMPEG = "core_rate_ffmpeg"
    const val CORE_RATE_OPENMPT = "core_rate_openmpt"
    const val OPENMPT_STEREO_SEPARATION_PERCENT = "openmpt_stereo_separation_percent"
    const val OPENMPT_STEREO_SEPARATION_AMIGA_PERCENT = "openmpt_stereo_separation_amiga_percent"
    const val OPENMPT_INTERPOLATION_FILTER_LENGTH = "openmpt_interpolation_filter_length"
    const val OPENMPT_AMIGA_RESAMPLER_MODE = "openmpt_amiga_resampler_mode"
    const val OPENMPT_AMIGA_RESAMPLER_APPLY_ALL_MODULES = "openmpt_amiga_resampler_apply_all_modules"
    const val OPENMPT_VOLUME_RAMPING_STRENGTH = "openmpt_volume_ramping_strength"
    const val OPENMPT_FT2_XM_VOLUME_RAMPING = "openmpt_ft2_xm_volume_ramping"
    const val OPENMPT_MASTER_GAIN_MILLIBEL = "openmpt_master_gain_millibel"
    const val OPENMPT_SURROUND_ENABLED = "openmpt_surround_enabled"
}

object OpenMptDefaults {
    const val coreSampleRateHz = 0
    const val stereoSeparationPercent = 100
    const val stereoSeparationAmigaPercent = 100
    const val interpolationFilterLength = 0
    const val amigaResamplerMode = 2
    const val amigaResamplerApplyAllModules = false
    const val volumeRampingStrength = 0
    const val ft2XmVolumeRamping = false
    const val masterGainMilliBel = 0
    const val surroundEnabled = false
}

object FfmpegDefaults {
    const val coreSampleRateHz = 0
}
