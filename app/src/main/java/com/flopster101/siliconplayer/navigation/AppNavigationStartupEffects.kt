package com.flopster101.siliconplayer

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun normalizeBassDepthPref(value: Int): Int {
    return if (value in 0..4) {
        value
    } else {
        (8 - value.coerceIn(4, 8)).coerceIn(0, 4)
    }
}

private fun normalizeBassRangePref(value: Int): Int {
    return if (value in 0..4) {
        value
    } else {
        (4 - ((value.coerceIn(5, 21) - 1) / 5)).coerceIn(0, 4)
    }
}

private fun normalizeSurroundDelayMsPref(value: Int): Int {
    if (value in 5..45 && value % 5 == 0) return value
    val clamped = value.coerceIn(5, 45)
    val step = ((clamped - 5) + 2) / 5
    return 5 + (step * 5)
}

@Composable
internal fun AppNavigationStartupEffects(
    prefs: SharedPreferences,
    defaultScopeTextSizeSp: Int,
    recentFoldersLimit: Int,
    recentFilesLimit: Int,
    recentFolders: List<RecentPathEntry>,
    recentPlayedFiles: List<RecentPathEntry>,
    onFfmpegCapabilitiesChanged: (Int) -> Unit,
    onOpenMptCapabilitiesChanged: (Int) -> Unit,
    onVgmPlayCapabilitiesChanged: (Int) -> Unit,
    onMasterVolumeDbChanged: (Float) -> Unit,
    onPluginVolumeDbChanged: (Float) -> Unit,
    onForceMonoChanged: (Boolean) -> Unit,
    onRecentFoldersLimitChanged: (Int) -> Unit,
    onRecentFilesLimitChanged: (Int) -> Unit,
    onRecentFoldersChanged: (List<RecentPathEntry>) -> Unit,
    onRecentPlayedFilesChanged: (List<RecentPathEntry>) -> Unit
) {
    LaunchedEffect(prefs, defaultScopeTextSizeSp) {
        if (!prefs.contains(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SIZE_SP)) {
            prefs.edit()
                .putInt(
                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SIZE_SP,
                    defaultScopeTextSizeSp
                )
                .apply()
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            onFfmpegCapabilitiesChanged(NativeBridge.getCoreCapabilities(DecoderNames.FFMPEG))
            onOpenMptCapabilitiesChanged(NativeBridge.getCoreCapabilities(DecoderNames.LIB_OPEN_MPT))
            onVgmPlayCapabilitiesChanged(NativeBridge.getCoreCapabilities(DecoderNames.VGM_PLAY))
        }
    }

    LaunchedEffect(Unit) {
        val masterVolumeDb = prefs.getFloat(AppPreferenceKeys.AUDIO_MASTER_VOLUME_DB, 0f)
        val forceMono = prefs.getBoolean(AppPreferenceKeys.AUDIO_FORCE_MONO, false)
        val outputLimiterEnabled = prefs.getBoolean(
            AppPreferenceKeys.AUDIO_OUTPUT_LIMITER_ENABLED,
            AppDefaults.AudioProcessing.outputLimiterEnabled
        )
        val dspBassEnabled = prefs.getBoolean(
            AppPreferenceKeys.AUDIO_DSP_BASS_ENABLED,
            AppDefaults.AudioProcessing.Dsp.bassEnabled
        )
        val dspBassDepth = prefs.getInt(
            AppPreferenceKeys.AUDIO_DSP_BASS_DEPTH,
            AppDefaults.AudioProcessing.Dsp.bassDepth
        ).let(::normalizeBassDepthPref)
        val dspBassRange = prefs.getInt(
            AppPreferenceKeys.AUDIO_DSP_BASS_RANGE,
            AppDefaults.AudioProcessing.Dsp.bassRange
        ).let(::normalizeBassRangePref)
        val dspSurroundEnabled = prefs.getBoolean(
            AppPreferenceKeys.AUDIO_DSP_SURROUND_ENABLED,
            AppDefaults.AudioProcessing.Dsp.surroundEnabled
        )
        val dspSurroundDepth = prefs.getInt(
            AppPreferenceKeys.AUDIO_DSP_SURROUND_DEPTH,
            AppDefaults.AudioProcessing.Dsp.surroundDepth
        ).coerceIn(1, 16)
        val dspSurroundDelayMs = prefs.getInt(
            AppPreferenceKeys.AUDIO_DSP_SURROUND_DELAY_MS,
            AppDefaults.AudioProcessing.Dsp.surroundDelayMs
        ).let(::normalizeSurroundDelayMsPref)
        val dspReverbEnabled = prefs.getBoolean(
            AppPreferenceKeys.AUDIO_DSP_REVERB_ENABLED,
            AppDefaults.AudioProcessing.Dsp.reverbEnabled
        )
        val dspReverbDepth = prefs.getInt(
            AppPreferenceKeys.AUDIO_DSP_REVERB_DEPTH,
            AppDefaults.AudioProcessing.Dsp.reverbDepth
        ).coerceIn(1, 16)
        val dspReverbPreset = prefs.getInt(
            AppPreferenceKeys.AUDIO_DSP_REVERB_PRESET,
            AppDefaults.AudioProcessing.Dsp.reverbPreset
        ).coerceIn(0, 28)
        val dspBitCrushEnabled = prefs.getBoolean(
            AppPreferenceKeys.AUDIO_DSP_BITCRUSH_ENABLED,
            AppDefaults.AudioProcessing.Dsp.bitCrushEnabled
        )
        val dspBitCrushBits = prefs.getInt(
            AppPreferenceKeys.AUDIO_DSP_BITCRUSH_BITS,
            AppDefaults.AudioProcessing.Dsp.bitCrushBits
        ).coerceIn(1, 24)

        onMasterVolumeDbChanged(masterVolumeDb)
        onPluginVolumeDbChanged(0f)
        onForceMonoChanged(forceMono)
        NativeBridge.setMasterGain(masterVolumeDb)
        NativeBridge.setPluginGain(0f)
        NativeBridge.setForceMono(forceMono)
        NativeBridge.setOutputLimiterEnabled(outputLimiterEnabled)
        NativeBridge.setDspBassEnabled(dspBassEnabled)
        NativeBridge.setDspBassDepth(dspBassDepth)
        NativeBridge.setDspBassRange(dspBassRange)
        NativeBridge.setDspSurroundEnabled(dspSurroundEnabled)
        NativeBridge.setDspSurroundDepth(dspSurroundDepth)
        NativeBridge.setDspSurroundDelayMs(dspSurroundDelayMs)
        NativeBridge.setDspReverbEnabled(dspReverbEnabled)
        NativeBridge.setDspReverbDepth(dspReverbDepth)
        NativeBridge.setDspReverbPreset(dspReverbPreset)
        NativeBridge.setDspBitCrushEnabled(dspBitCrushEnabled)
        NativeBridge.setDspBitCrushBits(dspBitCrushBits)

        withContext(Dispatchers.IO) {
            loadPluginConfigurations(prefs)
        }
    }

    LaunchedEffect(recentFoldersLimit) {
        val clamped = recentFoldersLimit.coerceIn(1, RECENTS_LIMIT_MAX)
        if (clamped != recentFoldersLimit) {
            onRecentFoldersLimitChanged(clamped)
            return@LaunchedEffect
        }
        prefs.edit().putInt(AppPreferenceKeys.RECENT_FOLDERS_LIMIT, clamped).apply()
        val trimmed = recentFolders.take(clamped)
        if (trimmed.size != recentFolders.size) {
            onRecentFoldersChanged(trimmed)
        }
        writeRecentEntries(prefs, AppPreferenceKeys.RECENT_FOLDERS, trimmed, clamped)
    }

    LaunchedEffect(recentFilesLimit) {
        val clamped = recentFilesLimit.coerceIn(1, RECENTS_LIMIT_MAX)
        if (clamped != recentFilesLimit) {
            onRecentFilesLimitChanged(clamped)
            return@LaunchedEffect
        }
        prefs.edit().putInt(AppPreferenceKeys.RECENT_PLAYED_FILES_LIMIT, clamped).apply()
        val trimmed = recentPlayedFiles.take(clamped)
        if (trimmed.size != recentPlayedFiles.size) {
            onRecentPlayedFilesChanged(trimmed)
        }
        writeRecentEntries(prefs, AppPreferenceKeys.RECENT_PLAYED_FILES, trimmed, clamped)
    }
}
