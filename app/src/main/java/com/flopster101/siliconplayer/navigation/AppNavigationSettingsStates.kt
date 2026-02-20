package com.flopster101.siliconplayer

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.flopster101.siliconplayer.data.ARCHIVE_CACHE_MAX_AGE_DAYS_DEFAULT
import com.flopster101.siliconplayer.data.ARCHIVE_CACHE_MAX_BYTES_DEFAULT
import com.flopster101.siliconplayer.data.ARCHIVE_CACHE_MAX_MOUNTS_DEFAULT
import kotlinx.coroutines.Job

internal data class AppNavigationSettingsStates(
    val ffmpegCoreSampleRateHz: MutableIntState,
    val ffmpegGaplessRepeatTrack: MutableState<Boolean>,
    val openMptCoreSampleRateHz: MutableIntState,
    val vgmPlayCoreSampleRateHz: MutableIntState,
    val gmeCoreSampleRateHz: MutableIntState,
    val sidPlayFpCoreSampleRateHz: MutableIntState,
    val lazyUsf2CoreSampleRateHz: MutableIntState,
    val lazyUsf2UseHleAudio: MutableState<Boolean>,
    val vio2sfInterpolationQuality: MutableIntState,
    val sc68SamplingRateHz: MutableIntState,
    val sc68Asid: MutableIntState,
    val sc68DefaultTimeSeconds: MutableIntState,
    val sc68YmEngine: MutableIntState,
    val sc68YmVolModel: MutableIntState,
    val sc68AmigaFilter: MutableState<Boolean>,
    val sc68AmigaBlend: MutableIntState,
    val sc68AmigaClock: MutableIntState,
    val sidPlayFpBackend: MutableIntState,
    val sidPlayFpClockMode: MutableIntState,
    val sidPlayFpSidModelMode: MutableIntState,
    val sidPlayFpFilter6581Enabled: MutableState<Boolean>,
    val sidPlayFpFilter8580Enabled: MutableState<Boolean>,
    val sidPlayFpDigiBoost8580: MutableState<Boolean>,
    val sidPlayFpFilterCurve6581Percent: MutableIntState,
    val sidPlayFpFilterRange6581Percent: MutableIntState,
    val sidPlayFpFilterCurve8580Percent: MutableIntState,
    val sidPlayFpReSidFpFastSampling: MutableState<Boolean>,
    val sidPlayFpReSidFpCombinedWaveformsStrength: MutableIntState,
    val gmeTempoPercent: MutableIntState,
    val gmeStereoSeparationPercent: MutableIntState,
    val gmeEchoEnabled: MutableState<Boolean>,
    val gmeAccuracyEnabled: MutableState<Boolean>,
    val gmeEqTrebleDecibel: MutableIntState,
    val gmeEqBassHz: MutableIntState,
    val gmeSpcUseBuiltInFade: MutableState<Boolean>,
    val gmeSpcInterpolation: MutableIntState,
    val gmeSpcUseNativeSampleRate: MutableState<Boolean>,
    val vgmPlayLoopCount: MutableIntState,
    val vgmPlayAllowNonLoopingLoop: MutableState<Boolean>,
    val vgmPlayVsyncRate: MutableIntState,
    val vgmPlayResampleMode: MutableIntState,
    val vgmPlayChipSampleMode: MutableIntState,
    val vgmPlayChipSampleRate: MutableIntState,
    val vgmPlayChipCoreSelections: MutableState<Map<String, Int>>,
    val openMptStereoSeparationPercent: MutableIntState,
    val openMptStereoSeparationAmigaPercent: MutableIntState,
    val openMptInterpolationFilterLength: MutableIntState,
    val openMptAmigaResamplerMode: MutableIntState,
    val openMptAmigaResamplerApplyAllModules: MutableState<Boolean>,
    val openMptVolumeRampingStrength: MutableIntState,
    val openMptFt2XmVolumeRamping: MutableState<Boolean>,
    val openMptMasterGainMilliBel: MutableIntState,
    val openMptSurroundEnabled: MutableState<Boolean>,
    val respondHeadphoneMediaButtons: MutableState<Boolean>,
    val pauseOnHeadphoneDisconnect: MutableState<Boolean>,
    val audioBackendPreference: MutableState<AudioBackendPreference>,
    val audioPerformanceMode: MutableState<AudioPerformanceMode>,
    val audioBufferPreset: MutableState<AudioBufferPreset>,
    val audioResamplerPreference: MutableState<AudioResamplerPreference>,
    val pendingSoxExperimentalDialog: MutableState<Boolean>,
    val showSoxExperimentalDialog: MutableState<Boolean>,
    val showUrlOrPathDialog: MutableState<Boolean>,
    val urlOrPathInput: MutableState<String>,
    val remoteLoadUiState: MutableState<RemoteLoadUiState?>,
    val remoteLoadJob: MutableState<Job?>,
    val urlOrPathForceCaching: MutableState<Boolean>,
    val urlCacheClearOnLaunch: MutableState<Boolean>,
    val urlCacheMaxTracks: MutableIntState,
    val urlCacheMaxBytes: MutableLongState,
    val archiveCacheClearOnLaunch: MutableState<Boolean>,
    val archiveCacheMaxMounts: MutableIntState,
    val archiveCacheMaxBytes: MutableLongState,
    val archiveCacheMaxAgeDays: MutableIntState,
    val cachedSourceFiles: MutableState<List<CachedSourceFile>>,
    val pendingCacheExportPaths: MutableState<List<String>>,
    val audioAllowBackendFallback: MutableState<Boolean>,
    val openPlayerFromNotification: MutableState<Boolean>,
    val playbackWatchPath: MutableState<String?>,
    val currentPlaybackSourceId: MutableState<String?>
)

@Composable
internal fun rememberAppNavigationSettingsStates(
    prefs: SharedPreferences
): AppNavigationSettingsStates {
    val ffmpegCoreSampleRateHz = remember {
        mutableIntStateOf(prefs.getInt(CorePreferenceKeys.CORE_RATE_FFMPEG, FfmpegDefaults.coreSampleRateHz))
    }
    val ffmpegGaplessRepeatTrack = remember {
        mutableStateOf(
            prefs.getBoolean(
                CorePreferenceKeys.FFMPEG_GAPLESS_REPEAT_TRACK,
                FfmpegDefaults.gaplessRepeatTrack
            )
        )
    }
    val openMptCoreSampleRateHz = remember {
        mutableIntStateOf(prefs.getInt(CorePreferenceKeys.CORE_RATE_OPENMPT, OpenMptDefaults.coreSampleRateHz))
    }
    val vgmPlayCoreSampleRateHz = remember {
        mutableIntStateOf(prefs.getInt(CorePreferenceKeys.CORE_RATE_VGMPLAY, VgmPlayDefaults.coreSampleRateHz))
    }
    val gmeCoreSampleRateHz = remember {
        mutableIntStateOf(prefs.getInt(CorePreferenceKeys.CORE_RATE_GME, GmeDefaults.coreSampleRateHz))
    }
    val sidPlayFpCoreSampleRateHz = remember {
        mutableIntStateOf(prefs.getInt(CorePreferenceKeys.CORE_RATE_SIDPLAYFP, SidPlayFpDefaults.coreSampleRateHz))
    }
    val lazyUsf2CoreSampleRateHz = remember {
        mutableIntStateOf(prefs.getInt(CorePreferenceKeys.CORE_RATE_LAZYUSF2, LazyUsf2Defaults.coreSampleRateHz))
    }
    val lazyUsf2UseHleAudio = remember {
        mutableStateOf(prefs.getBoolean(CorePreferenceKeys.LAZYUSF2_USE_HLE_AUDIO, LazyUsf2Defaults.useHleAudio))
    }
    val vio2sfInterpolationQuality = remember {
        mutableIntStateOf(
            prefs.getInt(
                CorePreferenceKeys.VIO2SF_INTERPOLATION_QUALITY,
                Vio2sfDefaults.interpolationQuality
            )
        )
    }
    val sc68SamplingRateHz = remember {
        mutableIntStateOf(prefs.getInt(CorePreferenceKeys.CORE_RATE_SC68, Sc68Defaults.coreSampleRateHz))
    }
    val sc68Asid = remember {
        mutableIntStateOf(prefs.getInt(CorePreferenceKeys.SC68_ASID, Sc68Defaults.asid))
    }
    val sc68DefaultTimeSeconds = remember {
        mutableIntStateOf(prefs.getInt(CorePreferenceKeys.SC68_DEFAULT_TIME_SECONDS, Sc68Defaults.defaultTimeSeconds))
    }
    val sc68YmEngine = remember {
        mutableIntStateOf(prefs.getInt(CorePreferenceKeys.SC68_YM_ENGINE, Sc68Defaults.ymEngine))
    }
    val sc68YmVolModel = remember {
        mutableIntStateOf(prefs.getInt(CorePreferenceKeys.SC68_YM_VOLMODEL, Sc68Defaults.ymVolModel))
    }
    val sc68AmigaFilter = remember {
        mutableStateOf(prefs.getBoolean(CorePreferenceKeys.SC68_AMIGA_FILTER, Sc68Defaults.amigaFilter))
    }
    val sc68AmigaBlend = remember {
        mutableIntStateOf(prefs.getInt(CorePreferenceKeys.SC68_AMIGA_BLEND, Sc68Defaults.amigaBlend))
    }
    val sc68AmigaClock = remember {
        mutableIntStateOf(prefs.getInt(CorePreferenceKeys.SC68_AMIGA_CLOCK, Sc68Defaults.amigaClock))
    }
    val sidPlayFpBackend = remember {
        mutableIntStateOf(prefs.getInt(CorePreferenceKeys.SIDPLAYFP_BACKEND, SidPlayFpDefaults.backend))
    }
    val sidPlayFpClockMode = remember {
        mutableIntStateOf(prefs.getInt(CorePreferenceKeys.SIDPLAYFP_CLOCK_MODE, SidPlayFpDefaults.clockMode))
    }
    val sidPlayFpSidModelMode = remember {
        mutableIntStateOf(prefs.getInt(CorePreferenceKeys.SIDPLAYFP_SID_MODEL_MODE, SidPlayFpDefaults.sidModelMode))
    }
    val sidPlayFpFilter6581Enabled = remember {
        mutableStateOf(
            prefs.getBoolean(CorePreferenceKeys.SIDPLAYFP_FILTER_6581_ENABLED, SidPlayFpDefaults.filter6581Enabled)
        )
    }
    val sidPlayFpFilter8580Enabled = remember {
        mutableStateOf(
            prefs.getBoolean(CorePreferenceKeys.SIDPLAYFP_FILTER_8580_ENABLED, SidPlayFpDefaults.filter8580Enabled)
        )
    }
    val sidPlayFpDigiBoost8580 = remember {
        mutableStateOf(
            prefs.getBoolean(CorePreferenceKeys.SIDPLAYFP_DIGI_BOOST_8580, SidPlayFpDefaults.digiBoost8580)
        )
    }
    val sidPlayFpFilterCurve6581Percent = remember {
        mutableIntStateOf(
            prefs.getInt(CorePreferenceKeys.SIDPLAYFP_FILTER_CURVE_6581, SidPlayFpDefaults.filterCurve6581Percent)
        )
    }
    val sidPlayFpFilterRange6581Percent = remember {
        mutableIntStateOf(
            prefs.getInt(CorePreferenceKeys.SIDPLAYFP_FILTER_RANGE_6581, SidPlayFpDefaults.filterRange6581Percent)
        )
    }
    val sidPlayFpFilterCurve8580Percent = remember {
        mutableIntStateOf(
            prefs.getInt(CorePreferenceKeys.SIDPLAYFP_FILTER_CURVE_8580, SidPlayFpDefaults.filterCurve8580Percent)
        )
    }
    val sidPlayFpReSidFpFastSampling = remember {
        mutableStateOf(
            prefs.getBoolean(CorePreferenceKeys.SIDPLAYFP_RESIDFP_FAST_SAMPLING, SidPlayFpDefaults.reSidFpFastSampling)
        )
    }
    val sidPlayFpReSidFpCombinedWaveformsStrength = remember {
        mutableIntStateOf(
            prefs.getInt(
                CorePreferenceKeys.SIDPLAYFP_RESIDFP_COMBINED_WAVEFORMS_STRENGTH,
                SidPlayFpDefaults.reSidFpCombinedWaveformsStrength
            )
        )
    }
    val gmeTempoPercent = remember {
        mutableIntStateOf(prefs.getInt(CorePreferenceKeys.GME_TEMPO_PERCENT, GmeDefaults.tempoPercent))
    }
    val gmeStereoSeparationPercent = remember {
        mutableIntStateOf(
            prefs.getInt(CorePreferenceKeys.GME_STEREO_SEPARATION_PERCENT, GmeDefaults.stereoSeparationPercent)
        )
    }
    val gmeEchoEnabled = remember {
        mutableStateOf(prefs.getBoolean(CorePreferenceKeys.GME_ECHO_ENABLED, GmeDefaults.echoEnabled))
    }
    val gmeAccuracyEnabled = remember {
        mutableStateOf(prefs.getBoolean(CorePreferenceKeys.GME_ACCURACY_ENABLED, GmeDefaults.accuracyEnabled))
    }
    val gmeEqTrebleDecibel = remember {
        mutableIntStateOf(prefs.getInt(CorePreferenceKeys.GME_EQ_TREBLE_DECIBEL, GmeDefaults.eqTrebleDecibel))
    }
    val gmeEqBassHz = remember {
        mutableIntStateOf(prefs.getInt(CorePreferenceKeys.GME_EQ_BASS_HZ, GmeDefaults.eqBassHz))
    }
    val gmeSpcUseBuiltInFade = remember {
        mutableStateOf(prefs.getBoolean(CorePreferenceKeys.GME_SPC_USE_BUILTIN_FADE, GmeDefaults.spcUseBuiltInFade))
    }
    val gmeSpcInterpolation = remember {
        mutableIntStateOf(prefs.getInt(CorePreferenceKeys.GME_SPC_INTERPOLATION, GmeDefaults.spcInterpolation))
    }
    val gmeSpcUseNativeSampleRate = remember {
        mutableStateOf(
            prefs.getBoolean(CorePreferenceKeys.GME_SPC_USE_NATIVE_SAMPLE_RATE, GmeDefaults.spcUseNativeSampleRate)
        )
    }
    val vgmPlayLoopCount = remember {
        mutableIntStateOf(prefs.getInt(CorePreferenceKeys.VGMPLAY_LOOP_COUNT, VgmPlayDefaults.loopCount))
    }
    val vgmPlayAllowNonLoopingLoop = remember {
        mutableStateOf(
            prefs.getBoolean(CorePreferenceKeys.VGMPLAY_ALLOW_NON_LOOPING_LOOP, VgmPlayDefaults.allowNonLoopingLoop)
        )
    }
    val vgmPlayVsyncRate = remember {
        mutableIntStateOf(prefs.getInt(CorePreferenceKeys.VGMPLAY_VSYNC_RATE, VgmPlayDefaults.vsyncRate))
    }
    val vgmPlayResampleMode = remember {
        mutableIntStateOf(prefs.getInt(CorePreferenceKeys.VGMPLAY_RESAMPLE_MODE, VgmPlayDefaults.resampleMode))
    }
    val vgmPlayChipSampleMode = remember {
        mutableIntStateOf(prefs.getInt(CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_MODE, VgmPlayDefaults.chipSampleMode))
    }
    val vgmPlayChipSampleRate = remember {
        mutableIntStateOf(prefs.getInt(CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_RATE, VgmPlayDefaults.chipSampleRate))
    }
    val vgmPlayChipCoreSelections = remember {
        mutableStateOf(
            VgmPlayConfig.defaultChipCoreSelections().mapValues { (chipKey, defaultValue) ->
                prefs.getInt(CorePreferenceKeys.vgmPlayChipCoreKey(chipKey), defaultValue)
            }
        )
    }
    val openMptStereoSeparationPercent = remember {
        mutableIntStateOf(
            prefs.getInt(CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_PERCENT, OpenMptDefaults.stereoSeparationPercent)
        )
    }
    val openMptStereoSeparationAmigaPercent = remember {
        mutableIntStateOf(
            prefs.getInt(
                CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_AMIGA_PERCENT,
                OpenMptDefaults.stereoSeparationAmigaPercent
            )
        )
    }
    val openMptInterpolationFilterLength = remember {
        mutableIntStateOf(
            prefs.getInt(
                CorePreferenceKeys.OPENMPT_INTERPOLATION_FILTER_LENGTH,
                OpenMptDefaults.interpolationFilterLength
            )
        )
    }
    val openMptAmigaResamplerMode = remember {
        mutableIntStateOf(
            prefs.getInt(CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_MODE, OpenMptDefaults.amigaResamplerMode)
        )
    }
    val openMptAmigaResamplerApplyAllModules = remember {
        mutableStateOf(
            prefs.getBoolean(
                CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_APPLY_ALL_MODULES,
                OpenMptDefaults.amigaResamplerApplyAllModules
            )
        )
    }
    val openMptVolumeRampingStrength = remember {
        mutableIntStateOf(
            prefs.getInt(CorePreferenceKeys.OPENMPT_VOLUME_RAMPING_STRENGTH, OpenMptDefaults.volumeRampingStrength)
        )
    }
    val openMptFt2XmVolumeRamping = remember {
        mutableStateOf(
            prefs.getBoolean(CorePreferenceKeys.OPENMPT_FT2_XM_VOLUME_RAMPING, OpenMptDefaults.ft2XmVolumeRamping)
        )
    }
    val openMptMasterGainMilliBel = remember {
        mutableIntStateOf(
            prefs.getInt(CorePreferenceKeys.OPENMPT_MASTER_GAIN_MILLIBEL, OpenMptDefaults.masterGainMilliBel)
        )
    }
    val openMptSurroundEnabled = remember {
        mutableStateOf(prefs.getBoolean(CorePreferenceKeys.OPENMPT_SURROUND_ENABLED, OpenMptDefaults.surroundEnabled))
    }
    val respondHeadphoneMediaButtons = remember {
        mutableStateOf(prefs.getBoolean(AppPreferenceKeys.RESPOND_HEADPHONE_MEDIA_BUTTONS, true))
    }
    val pauseOnHeadphoneDisconnect = remember {
        mutableStateOf(prefs.getBoolean(AppPreferenceKeys.PAUSE_ON_HEADPHONE_DISCONNECT, true))
    }
    val audioBackendPreference = remember {
        mutableStateOf(
            AudioBackendPreference.fromStorage(
                prefs.getString(AppPreferenceKeys.AUDIO_BACKEND_PREFERENCE, AudioBackendPreference.AAudio.storageValue)
            )
        )
    }
    val audioPerformanceMode = remember {
        mutableStateOf(
            AudioPerformanceMode.fromStorage(
                prefs.getString(AppPreferenceKeys.AUDIO_PERFORMANCE_MODE, AudioPerformanceMode.None.storageValue)
            )
        )
    }
    val audioBufferPreset = remember {
        mutableStateOf(
            AudioBufferPreset.fromStorage(
                prefs.getString(AppPreferenceKeys.AUDIO_BUFFER_PRESET, AudioBufferPreset.Medium.storageValue)
            )
        )
    }
    val audioResamplerPreference = remember {
        mutableStateOf(
            AudioResamplerPreference.fromStorage(
                prefs.getString(AppPreferenceKeys.AUDIO_RESAMPLER_PREFERENCE, AudioResamplerPreference.BuiltIn.storageValue)
            )
        )
    }
    val pendingSoxExperimentalDialog = remember { mutableStateOf(false) }
    val showSoxExperimentalDialog = remember { mutableStateOf(false) }
    val showUrlOrPathDialog = remember { mutableStateOf(false) }
    val urlOrPathInput = remember { mutableStateOf("") }
    val remoteLoadUiState = remember { mutableStateOf<RemoteLoadUiState?>(null) }
    val remoteLoadJob = remember { mutableStateOf<Job?>(null) }
    val urlOrPathForceCaching = remember {
        mutableStateOf(prefs.getBoolean(AppPreferenceKeys.URL_PATH_FORCE_CACHING, false))
    }
    val urlCacheClearOnLaunch = remember {
        mutableStateOf(prefs.getBoolean(AppPreferenceKeys.URL_CACHE_CLEAR_ON_LAUNCH, false))
    }
    val urlCacheMaxTracks = remember {
        mutableIntStateOf(prefs.getInt(AppPreferenceKeys.URL_CACHE_MAX_TRACKS, SOURCE_CACHE_MAX_TRACKS_DEFAULT))
    }
    val urlCacheMaxBytes = remember {
        mutableLongStateOf(prefs.getLong(AppPreferenceKeys.URL_CACHE_MAX_BYTES, SOURCE_CACHE_MAX_BYTES_DEFAULT))
    }
    val archiveCacheClearOnLaunch = remember {
        mutableStateOf(prefs.getBoolean(AppPreferenceKeys.ARCHIVE_CACHE_CLEAR_ON_LAUNCH, false))
    }
    val archiveCacheMaxMounts = remember {
        mutableIntStateOf(prefs.getInt(AppPreferenceKeys.ARCHIVE_CACHE_MAX_MOUNTS, ARCHIVE_CACHE_MAX_MOUNTS_DEFAULT))
    }
    val archiveCacheMaxBytes = remember {
        mutableLongStateOf(prefs.getLong(AppPreferenceKeys.ARCHIVE_CACHE_MAX_BYTES, ARCHIVE_CACHE_MAX_BYTES_DEFAULT))
    }
    val archiveCacheMaxAgeDays = remember {
        mutableIntStateOf(prefs.getInt(AppPreferenceKeys.ARCHIVE_CACHE_MAX_AGE_DAYS, ARCHIVE_CACHE_MAX_AGE_DAYS_DEFAULT))
    }
    val cachedSourceFiles = remember { mutableStateOf<List<CachedSourceFile>>(emptyList()) }
    val pendingCacheExportPaths = remember { mutableStateOf<List<String>>(emptyList()) }
    val audioAllowBackendFallback = remember {
        mutableStateOf(prefs.getBoolean(AppPreferenceKeys.AUDIO_ALLOW_BACKEND_FALLBACK, true))
    }
    val openPlayerFromNotification = remember {
        mutableStateOf(prefs.getBoolean(AppPreferenceKeys.OPEN_PLAYER_FROM_NOTIFICATION, true))
    }
    val playbackWatchPath = remember { mutableStateOf<String?>(null) }
    val currentPlaybackSourceId = remember { mutableStateOf<String?>(null) }

    return AppNavigationSettingsStates(
        ffmpegCoreSampleRateHz = ffmpegCoreSampleRateHz,
        ffmpegGaplessRepeatTrack = ffmpegGaplessRepeatTrack,
        openMptCoreSampleRateHz = openMptCoreSampleRateHz,
        vgmPlayCoreSampleRateHz = vgmPlayCoreSampleRateHz,
        gmeCoreSampleRateHz = gmeCoreSampleRateHz,
        sidPlayFpCoreSampleRateHz = sidPlayFpCoreSampleRateHz,
        lazyUsf2CoreSampleRateHz = lazyUsf2CoreSampleRateHz,
        lazyUsf2UseHleAudio = lazyUsf2UseHleAudio,
        vio2sfInterpolationQuality = vio2sfInterpolationQuality,
        sc68SamplingRateHz = sc68SamplingRateHz,
        sc68Asid = sc68Asid,
        sc68DefaultTimeSeconds = sc68DefaultTimeSeconds,
        sc68YmEngine = sc68YmEngine,
        sc68YmVolModel = sc68YmVolModel,
        sc68AmigaFilter = sc68AmigaFilter,
        sc68AmigaBlend = sc68AmigaBlend,
        sc68AmigaClock = sc68AmigaClock,
        sidPlayFpBackend = sidPlayFpBackend,
        sidPlayFpClockMode = sidPlayFpClockMode,
        sidPlayFpSidModelMode = sidPlayFpSidModelMode,
        sidPlayFpFilter6581Enabled = sidPlayFpFilter6581Enabled,
        sidPlayFpFilter8580Enabled = sidPlayFpFilter8580Enabled,
        sidPlayFpDigiBoost8580 = sidPlayFpDigiBoost8580,
        sidPlayFpFilterCurve6581Percent = sidPlayFpFilterCurve6581Percent,
        sidPlayFpFilterRange6581Percent = sidPlayFpFilterRange6581Percent,
        sidPlayFpFilterCurve8580Percent = sidPlayFpFilterCurve8580Percent,
        sidPlayFpReSidFpFastSampling = sidPlayFpReSidFpFastSampling,
        sidPlayFpReSidFpCombinedWaveformsStrength = sidPlayFpReSidFpCombinedWaveformsStrength,
        gmeTempoPercent = gmeTempoPercent,
        gmeStereoSeparationPercent = gmeStereoSeparationPercent,
        gmeEchoEnabled = gmeEchoEnabled,
        gmeAccuracyEnabled = gmeAccuracyEnabled,
        gmeEqTrebleDecibel = gmeEqTrebleDecibel,
        gmeEqBassHz = gmeEqBassHz,
        gmeSpcUseBuiltInFade = gmeSpcUseBuiltInFade,
        gmeSpcInterpolation = gmeSpcInterpolation,
        gmeSpcUseNativeSampleRate = gmeSpcUseNativeSampleRate,
        vgmPlayLoopCount = vgmPlayLoopCount,
        vgmPlayAllowNonLoopingLoop = vgmPlayAllowNonLoopingLoop,
        vgmPlayVsyncRate = vgmPlayVsyncRate,
        vgmPlayResampleMode = vgmPlayResampleMode,
        vgmPlayChipSampleMode = vgmPlayChipSampleMode,
        vgmPlayChipSampleRate = vgmPlayChipSampleRate,
        vgmPlayChipCoreSelections = vgmPlayChipCoreSelections,
        openMptStereoSeparationPercent = openMptStereoSeparationPercent,
        openMptStereoSeparationAmigaPercent = openMptStereoSeparationAmigaPercent,
        openMptInterpolationFilterLength = openMptInterpolationFilterLength,
        openMptAmigaResamplerMode = openMptAmigaResamplerMode,
        openMptAmigaResamplerApplyAllModules = openMptAmigaResamplerApplyAllModules,
        openMptVolumeRampingStrength = openMptVolumeRampingStrength,
        openMptFt2XmVolumeRamping = openMptFt2XmVolumeRamping,
        openMptMasterGainMilliBel = openMptMasterGainMilliBel,
        openMptSurroundEnabled = openMptSurroundEnabled,
        respondHeadphoneMediaButtons = respondHeadphoneMediaButtons,
        pauseOnHeadphoneDisconnect = pauseOnHeadphoneDisconnect,
        audioBackendPreference = audioBackendPreference,
        audioPerformanceMode = audioPerformanceMode,
        audioBufferPreset = audioBufferPreset,
        audioResamplerPreference = audioResamplerPreference,
        pendingSoxExperimentalDialog = pendingSoxExperimentalDialog,
        showSoxExperimentalDialog = showSoxExperimentalDialog,
        showUrlOrPathDialog = showUrlOrPathDialog,
        urlOrPathInput = urlOrPathInput,
        remoteLoadUiState = remoteLoadUiState,
        remoteLoadJob = remoteLoadJob,
        urlOrPathForceCaching = urlOrPathForceCaching,
        urlCacheClearOnLaunch = urlCacheClearOnLaunch,
        urlCacheMaxTracks = urlCacheMaxTracks,
        urlCacheMaxBytes = urlCacheMaxBytes,
        archiveCacheClearOnLaunch = archiveCacheClearOnLaunch,
        archiveCacheMaxMounts = archiveCacheMaxMounts,
        archiveCacheMaxBytes = archiveCacheMaxBytes,
        archiveCacheMaxAgeDays = archiveCacheMaxAgeDays,
        cachedSourceFiles = cachedSourceFiles,
        pendingCacheExportPaths = pendingCacheExportPaths,
        audioAllowBackendFallback = audioAllowBackendFallback,
        openPlayerFromNotification = openPlayerFromNotification,
        playbackWatchPath = playbackWatchPath,
        currentPlaybackSourceId = currentPlaybackSourceId
    )
}
