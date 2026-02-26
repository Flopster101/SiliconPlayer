package com.flopster101.siliconplayer

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.io.File

@Composable
internal fun AppNavigationPlaybackDialogsSection(
    prefs: SharedPreferences,
    volumeDatabase: VolumeDatabase,
    selectedFile: File?,
    lastUsedCoreName: String?,
    manualOpenDelegates: AppNavigationManualOpenDelegates,
    playbackStateDelegates: AppNavigationPlaybackStateDelegates,
    onCancelRemoteLoadJob: () -> Unit,
    showUrlOrPathDialog: Boolean,
    urlOrPathInput: String,
    urlOrPathForceCaching: Boolean,
    onUrlOrPathInputChanged: (String) -> Unit,
    onUrlOrPathForceCachingChanged: (Boolean) -> Unit,
    onShowUrlOrPathDialogChanged: (Boolean) -> Unit,
    remoteLoadUiState: RemoteLoadUiState?,
    onRemoteLoadUiStateChanged: (RemoteLoadUiState?) -> Unit,
    showSoxExperimentalDialog: Boolean,
    onShowSoxExperimentalDialogChanged: (Boolean) -> Unit,
    showSubtuneSelectorDialog: Boolean,
    subtuneEntries: List<SubtuneEntry>,
    currentSubtuneIndex: Int,
    onShowSubtuneSelectorDialogChanged: (Boolean) -> Unit,
    showAudioEffectsDialog: Boolean,
    tempMasterVolumeDb: Float,
    tempPluginVolumeDb: Float,
    tempSongVolumeDb: Float,
    tempIgnoreCoreVolumeForSong: Boolean,
    tempForceMono: Boolean,
    masterVolumeDb: Float,
    songVolumeDb: Float,
    ignoreCoreVolumeForSong: Boolean,
    forceMono: Boolean,
    onTempMasterVolumeDbChanged: (Float) -> Unit,
    onTempPluginVolumeDbChanged: (Float) -> Unit,
    onTempSongVolumeDbChanged: (Float) -> Unit,
    onTempIgnoreCoreVolumeForSongChanged: (Boolean) -> Unit,
    onTempForceMonoChanged: (Boolean) -> Unit,
    onMasterVolumeDbChanged: (Float) -> Unit,
    onPluginVolumeDbChanged: (Float) -> Unit,
    onSongVolumeDbChanged: (Float) -> Unit,
    onIgnoreCoreVolumeForSongChanged: (Boolean) -> Unit,
    onForceMonoChanged: (Boolean) -> Unit,
    onShowAudioEffectsDialogChanged: (Boolean) -> Unit
) {
    SideEffect {
        RemoteLoadUiStateHolder.current = remoteLoadUiState
    }

    var dspBassEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.AUDIO_DSP_BASS_ENABLED, AppDefaults.AudioProcessing.Dsp.bassEnabled)
        )
    }
    var dspBassDepth by remember {
        mutableIntStateOf(
            prefs.getInt(AppPreferenceKeys.AUDIO_DSP_BASS_DEPTH, AppDefaults.AudioProcessing.Dsp.bassDepth).coerceIn(4, 8)
        )
    }
    var dspBassRange by remember {
        mutableIntStateOf(
            prefs.getInt(AppPreferenceKeys.AUDIO_DSP_BASS_RANGE, AppDefaults.AudioProcessing.Dsp.bassRange).coerceIn(5, 21)
        )
    }
    var dspSurroundEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.AUDIO_DSP_SURROUND_ENABLED, AppDefaults.AudioProcessing.Dsp.surroundEnabled)
        )
    }
    var dspSurroundDepth by remember {
        mutableIntStateOf(
            prefs.getInt(AppPreferenceKeys.AUDIO_DSP_SURROUND_DEPTH, AppDefaults.AudioProcessing.Dsp.surroundDepth).coerceIn(1, 16)
        )
    }
    var dspSurroundDelayMs by remember {
        mutableIntStateOf(
            prefs.getInt(AppPreferenceKeys.AUDIO_DSP_SURROUND_DELAY_MS, AppDefaults.AudioProcessing.Dsp.surroundDelayMs).coerceIn(5, 45)
        )
    }
    var dspReverbEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.AUDIO_DSP_REVERB_ENABLED, AppDefaults.AudioProcessing.Dsp.reverbEnabled)
        )
    }
    var dspReverbDepth by remember {
        mutableIntStateOf(
            prefs.getInt(AppPreferenceKeys.AUDIO_DSP_REVERB_DEPTH, AppDefaults.AudioProcessing.Dsp.reverbDepth).coerceIn(1, 16)
        )
    }
    var dspReverbPreset by remember {
        mutableIntStateOf(
            prefs.getInt(AppPreferenceKeys.AUDIO_DSP_REVERB_PRESET, AppDefaults.AudioProcessing.Dsp.reverbPreset).coerceIn(0, 28)
        )
    }
    var dspBitCrushEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.AUDIO_DSP_BITCRUSH_ENABLED, AppDefaults.AudioProcessing.Dsp.bitCrushEnabled)
        )
    }
    var dspBitCrushBits by remember {
        mutableIntStateOf(
            prefs.getInt(AppPreferenceKeys.AUDIO_DSP_BITCRUSH_BITS, AppDefaults.AudioProcessing.Dsp.bitCrushBits).coerceIn(1, 24)
        )
    }

    var tempDspBassEnabled by remember { mutableStateOf(dspBassEnabled) }
    var tempDspBassDepth by remember { mutableIntStateOf(dspBassDepth) }
    var tempDspBassRange by remember { mutableIntStateOf(dspBassRange) }
    var tempDspSurroundEnabled by remember { mutableStateOf(dspSurroundEnabled) }
    var tempDspSurroundDepth by remember { mutableIntStateOf(dspSurroundDepth) }
    var tempDspSurroundDelayMs by remember { mutableIntStateOf(dspSurroundDelayMs) }
    var tempDspReverbEnabled by remember { mutableStateOf(dspReverbEnabled) }
    var tempDspReverbDepth by remember { mutableIntStateOf(dspReverbDepth) }
    var tempDspReverbPreset by remember { mutableIntStateOf(dspReverbPreset) }
    var tempDspBitCrushEnabled by remember { mutableStateOf(dspBitCrushEnabled) }
    var tempDspBitCrushBits by remember { mutableIntStateOf(dspBitCrushBits) }

    LaunchedEffect(showAudioEffectsDialog) {
        if (!showAudioEffectsDialog) return@LaunchedEffect
        tempDspBassEnabled = dspBassEnabled
        tempDspBassDepth = dspBassDepth
        tempDspBassRange = dspBassRange
        tempDspSurroundEnabled = dspSurroundEnabled
        tempDspSurroundDepth = dspSurroundDepth
        tempDspSurroundDelayMs = dspSurroundDelayMs
        tempDspReverbEnabled = dspReverbEnabled
        tempDspReverbDepth = dspReverbDepth
        tempDspReverbPreset = dspReverbPreset
        tempDspBitCrushEnabled = dspBitCrushEnabled
        tempDspBitCrushBits = dspBitCrushBits
    }

    PlaybackDialogsHost(
        showUrlOrPathDialog = showUrlOrPathDialog,
        urlOrPathInput = urlOrPathInput,
        urlOrPathForceCaching = urlOrPathForceCaching,
        onUrlOrPathInputChange = onUrlOrPathInputChanged,
        onUrlOrPathForceCachingChange = { checked ->
            onUrlOrPathForceCachingChanged(checked)
            prefs.edit()
                .putBoolean(AppPreferenceKeys.URL_PATH_FORCE_CACHING, checked)
                .apply()
        },
        onUrlOrPathDismiss = { onShowUrlOrPathDialogChanged(false) },
        onUrlOrPathOpen = {
            val openOptions = ManualSourceOpenOptions(forceCaching = urlOrPathForceCaching)
            onShowUrlOrPathDialogChanged(false)
            manualOpenDelegates.applyManualInputSelection(urlOrPathInput, openOptions)
        },
        remoteLoadUiState = remoteLoadUiState,
        onCancelRemoteLoad = {
            onCancelRemoteLoadJob()
            onRemoteLoadUiStateChanged(null)
        },
        showSoxExperimentalDialog = showSoxExperimentalDialog,
        onDismissSoxExperimentalDialog = { onShowSoxExperimentalDialogChanged(false) },
        showSubtuneSelectorDialog = showSubtuneSelectorDialog,
        subtuneEntries = subtuneEntries,
        currentSubtuneIndex = currentSubtuneIndex,
        onSelectSubtune = { subtuneIndex ->
            playbackStateDelegates.selectSubtune(subtuneIndex)
            onShowSubtuneSelectorDialogChanged(false)
        },
        onDismissSubtuneSelector = { onShowSubtuneSelectorDialogChanged(false) },
        showAudioEffectsDialog = showAudioEffectsDialog,
        tempMasterVolumeDb = tempMasterVolumeDb,
        tempPluginVolumeDb = tempPluginVolumeDb,
        tempSongVolumeDb = tempSongVolumeDb,
        tempIgnoreCoreVolumeForSong = tempIgnoreCoreVolumeForSong,
        tempForceMono = tempForceMono,
        tempDspBassEnabled = tempDspBassEnabled,
        tempDspBassDepth = tempDspBassDepth,
        tempDspBassRange = tempDspBassRange,
        tempDspSurroundEnabled = tempDspSurroundEnabled,
        tempDspSurroundDepth = tempDspSurroundDepth,
        tempDspSurroundDelayMs = tempDspSurroundDelayMs,
        tempDspReverbEnabled = tempDspReverbEnabled,
        tempDspReverbDepth = tempDspReverbDepth,
        tempDspReverbPreset = tempDspReverbPreset,
        tempDspBitCrushEnabled = tempDspBitCrushEnabled,
        tempDspBitCrushBits = tempDspBitCrushBits,
        hasActiveCore = lastUsedCoreName != null,
        hasActiveSong = selectedFile != null,
        currentCoreName = lastUsedCoreName,
        onMasterVolumeChange = {
            onTempMasterVolumeDbChanged(it)
            NativeBridge.setMasterGain(it)
        },
        onPluginVolumeChange = {
            onTempPluginVolumeDbChanged(it)
            NativeBridge.setPluginGain(if (tempIgnoreCoreVolumeForSong) 0f else it)
        },
        onSongVolumeChange = {
            onTempSongVolumeDbChanged(it)
            NativeBridge.setSongGain(it)
        },
        onIgnoreCoreVolumeForSongChange = {
            onTempIgnoreCoreVolumeForSongChanged(it)
            NativeBridge.setPluginGain(if (it) 0f else tempPluginVolumeDb)
        },
        onForceMonoChange = {
            onTempForceMonoChanged(it)
            NativeBridge.setForceMono(it)
        },
        onDspBassEnabledChange = {
            tempDspBassEnabled = it
            NativeBridge.setDspBassEnabled(it)
        },
        onDspBassDepthChange = {
            val normalized = it.coerceIn(4, 8)
            tempDspBassDepth = normalized
            NativeBridge.setDspBassDepth(normalized)
        },
        onDspBassRangeChange = {
            val normalized = it.coerceIn(5, 21)
            tempDspBassRange = normalized
            NativeBridge.setDspBassRange(normalized)
        },
        onDspSurroundEnabledChange = {
            tempDspSurroundEnabled = it
            NativeBridge.setDspSurroundEnabled(it)
        },
        onDspSurroundDepthChange = {
            val normalized = it.coerceIn(1, 16)
            tempDspSurroundDepth = normalized
            NativeBridge.setDspSurroundDepth(normalized)
        },
        onDspSurroundDelayMsChange = {
            val normalized = it.coerceIn(5, 45)
            tempDspSurroundDelayMs = normalized
            NativeBridge.setDspSurroundDelayMs(normalized)
        },
        onDspReverbEnabledChange = {
            tempDspReverbEnabled = it
            NativeBridge.setDspReverbEnabled(it)
        },
        onDspReverbDepthChange = {
            val normalized = it.coerceIn(1, 16)
            tempDspReverbDepth = normalized
            NativeBridge.setDspReverbDepth(normalized)
        },
        onDspReverbPresetChange = {
            val normalized = it.coerceIn(0, 28)
            tempDspReverbPreset = normalized
            NativeBridge.setDspReverbPreset(normalized)
        },
        onDspBitCrushEnabledChange = {
            tempDspBitCrushEnabled = it
            NativeBridge.setDspBitCrushEnabled(it)
        },
        onDspBitCrushBitsChange = {
            val normalized = it.coerceIn(1, 24)
            tempDspBitCrushBits = normalized
            NativeBridge.setDspBitCrushBits(normalized)
        },
        onAudioEffectsReset = {
            onTempMasterVolumeDbChanged(0f)
            onTempPluginVolumeDbChanged(0f)
            onTempSongVolumeDbChanged(0f)
            onTempIgnoreCoreVolumeForSongChanged(false)
            onTempForceMonoChanged(false)
            tempDspBassEnabled = false
            tempDspBassDepth = AppDefaults.AudioProcessing.Dsp.bassDepth
            tempDspBassRange = AppDefaults.AudioProcessing.Dsp.bassRange
            tempDspSurroundEnabled = false
            tempDspSurroundDepth = AppDefaults.AudioProcessing.Dsp.surroundDepth
            tempDspSurroundDelayMs = AppDefaults.AudioProcessing.Dsp.surroundDelayMs
            tempDspReverbEnabled = false
            tempDspReverbDepth = AppDefaults.AudioProcessing.Dsp.reverbDepth
            tempDspReverbPreset = AppDefaults.AudioProcessing.Dsp.reverbPreset
            tempDspBitCrushEnabled = false
            tempDspBitCrushBits = AppDefaults.AudioProcessing.Dsp.bitCrushBits
            NativeBridge.setMasterGain(0f)
            NativeBridge.setPluginGain(0f)
            NativeBridge.setSongGain(0f)
            NativeBridge.setForceMono(false)
            NativeBridge.setDspBassEnabled(false)
            NativeBridge.setDspBassDepth(AppDefaults.AudioProcessing.Dsp.bassDepth)
            NativeBridge.setDspBassRange(AppDefaults.AudioProcessing.Dsp.bassRange)
            NativeBridge.setDspSurroundEnabled(false)
            NativeBridge.setDspSurroundDepth(AppDefaults.AudioProcessing.Dsp.surroundDepth)
            NativeBridge.setDspSurroundDelayMs(AppDefaults.AudioProcessing.Dsp.surroundDelayMs)
            NativeBridge.setDspReverbEnabled(false)
            NativeBridge.setDspReverbDepth(AppDefaults.AudioProcessing.Dsp.reverbDepth)
            NativeBridge.setDspReverbPreset(AppDefaults.AudioProcessing.Dsp.reverbPreset)
            NativeBridge.setDspBitCrushEnabled(false)
            NativeBridge.setDspBitCrushBits(AppDefaults.AudioProcessing.Dsp.bitCrushBits)
        },
        onAudioEffectsDismiss = {
            NativeBridge.setMasterGain(masterVolumeDb)
            NativeBridge.setPluginGain(
                if (ignoreCoreVolumeForSong) 0f else readPluginVolumeForDecoder(prefs, lastUsedCoreName)
            )
            NativeBridge.setSongGain(songVolumeDb)
            NativeBridge.setForceMono(forceMono)
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
            onShowAudioEffectsDialogChanged(false)
        },
        onAudioEffectsConfirm = {
            onMasterVolumeDbChanged(tempMasterVolumeDb)
            onPluginVolumeDbChanged(tempPluginVolumeDb)
            onSongVolumeDbChanged(tempSongVolumeDb)
            onIgnoreCoreVolumeForSongChanged(tempIgnoreCoreVolumeForSong)
            onForceMonoChanged(tempForceMono)

            dspBassEnabled = tempDspBassEnabled
            dspBassDepth = tempDspBassDepth
            dspBassRange = tempDspBassRange
            dspSurroundEnabled = tempDspSurroundEnabled
            dspSurroundDepth = tempDspSurroundDepth
            dspSurroundDelayMs = tempDspSurroundDelayMs
            dspReverbEnabled = tempDspReverbEnabled
            dspReverbDepth = tempDspReverbDepth
            dspReverbPreset = tempDspReverbPreset
            dspBitCrushEnabled = tempDspBitCrushEnabled
            dspBitCrushBits = tempDspBitCrushBits

            prefs.edit().apply {
                putFloat(AppPreferenceKeys.AUDIO_MASTER_VOLUME_DB, tempMasterVolumeDb)
                putBoolean(AppPreferenceKeys.AUDIO_FORCE_MONO, tempForceMono)
                putBoolean(AppPreferenceKeys.AUDIO_DSP_BASS_ENABLED, tempDspBassEnabled)
                putInt(AppPreferenceKeys.AUDIO_DSP_BASS_DEPTH, tempDspBassDepth)
                putInt(AppPreferenceKeys.AUDIO_DSP_BASS_RANGE, tempDspBassRange)
                putBoolean(AppPreferenceKeys.AUDIO_DSP_SURROUND_ENABLED, tempDspSurroundEnabled)
                putInt(AppPreferenceKeys.AUDIO_DSP_SURROUND_DEPTH, tempDspSurroundDepth)
                putInt(AppPreferenceKeys.AUDIO_DSP_SURROUND_DELAY_MS, tempDspSurroundDelayMs)
                putBoolean(AppPreferenceKeys.AUDIO_DSP_REVERB_ENABLED, tempDspReverbEnabled)
                putInt(AppPreferenceKeys.AUDIO_DSP_REVERB_DEPTH, tempDspReverbDepth)
                putInt(AppPreferenceKeys.AUDIO_DSP_REVERB_PRESET, tempDspReverbPreset)
                putBoolean(AppPreferenceKeys.AUDIO_DSP_BITCRUSH_ENABLED, tempDspBitCrushEnabled)
                putInt(AppPreferenceKeys.AUDIO_DSP_BITCRUSH_BITS, tempDspBitCrushBits)
                apply()
            }
            writePluginVolumeForDecoder(
                prefs = prefs,
                decoderName = lastUsedCoreName,
                valueDb = tempPluginVolumeDb
            )
            selectedFile?.absolutePath?.let { path ->
                volumeDatabase.setSongVolume(path, tempSongVolumeDb)
                volumeDatabase.setSongIgnoreCoreVolume(path, tempIgnoreCoreVolumeForSong)
            }
            NativeBridge.setPluginGain(if (tempIgnoreCoreVolumeForSong) 0f else tempPluginVolumeDb)
            onShowAudioEffectsDialogChanged(false)
        }
    )
}
