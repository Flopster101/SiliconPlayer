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

private enum class DspSettingsNamespace {
    Global,
    CurrentCore
}

private data class DspSettings(
    val bassEnabled: Boolean,
    val bassDepth: Int,
    val bassRange: Int,
    val surroundEnabled: Boolean,
    val surroundDepth: Int,
    val surroundDelayMs: Int,
    val reverbEnabled: Boolean,
    val reverbDepth: Int,
    val reverbPreset: Int,
    val bitCrushEnabled: Boolean,
    val bitCrushBits: Int
)

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

private fun defaultDspSettings(): DspSettings {
    return DspSettings(
        bassEnabled = AppDefaults.AudioProcessing.Dsp.bassEnabled,
        bassDepth = AppDefaults.AudioProcessing.Dsp.bassDepth,
        bassRange = AppDefaults.AudioProcessing.Dsp.bassRange,
        surroundEnabled = AppDefaults.AudioProcessing.Dsp.surroundEnabled,
        surroundDepth = AppDefaults.AudioProcessing.Dsp.surroundDepth,
        surroundDelayMs = AppDefaults.AudioProcessing.Dsp.surroundDelayMs,
        reverbEnabled = AppDefaults.AudioProcessing.Dsp.reverbEnabled,
        reverbDepth = AppDefaults.AudioProcessing.Dsp.reverbDepth,
        reverbPreset = AppDefaults.AudioProcessing.Dsp.reverbPreset,
        bitCrushEnabled = AppDefaults.AudioProcessing.Dsp.bitCrushEnabled,
        bitCrushBits = AppDefaults.AudioProcessing.Dsp.bitCrushBits
    )
}

private fun readGlobalDspSettings(prefs: SharedPreferences): DspSettings {
    val defaults = defaultDspSettings()
    return DspSettings(
        bassEnabled = prefs.getBoolean(AppPreferenceKeys.AUDIO_DSP_BASS_ENABLED, defaults.bassEnabled),
        bassDepth = normalizeBassDepthPref(prefs.getInt(AppPreferenceKeys.AUDIO_DSP_BASS_DEPTH, defaults.bassDepth)),
        bassRange = normalizeBassRangePref(prefs.getInt(AppPreferenceKeys.AUDIO_DSP_BASS_RANGE, defaults.bassRange)),
        surroundEnabled = prefs.getBoolean(AppPreferenceKeys.AUDIO_DSP_SURROUND_ENABLED, defaults.surroundEnabled),
        surroundDepth = prefs.getInt(AppPreferenceKeys.AUDIO_DSP_SURROUND_DEPTH, defaults.surroundDepth).coerceIn(1, 16),
        surroundDelayMs = normalizeSurroundDelayMsPref(
            prefs.getInt(AppPreferenceKeys.AUDIO_DSP_SURROUND_DELAY_MS, defaults.surroundDelayMs)
        ),
        reverbEnabled = prefs.getBoolean(AppPreferenceKeys.AUDIO_DSP_REVERB_ENABLED, defaults.reverbEnabled),
        reverbDepth = prefs.getInt(AppPreferenceKeys.AUDIO_DSP_REVERB_DEPTH, defaults.reverbDepth).coerceIn(1, 16),
        reverbPreset = prefs.getInt(AppPreferenceKeys.AUDIO_DSP_REVERB_PRESET, defaults.reverbPreset).coerceIn(0, 28),
        bitCrushEnabled = prefs.getBoolean(AppPreferenceKeys.AUDIO_DSP_BITCRUSH_ENABLED, defaults.bitCrushEnabled),
        bitCrushBits = prefs.getInt(AppPreferenceKeys.AUDIO_DSP_BITCRUSH_BITS, defaults.bitCrushBits).coerceIn(1, 24)
    )
}

private fun writeGlobalDspSettings(editor: SharedPreferences.Editor, settings: DspSettings) {
    editor.putBoolean(AppPreferenceKeys.AUDIO_DSP_BASS_ENABLED, settings.bassEnabled)
    editor.putInt(AppPreferenceKeys.AUDIO_DSP_BASS_DEPTH, settings.bassDepth)
    editor.putInt(AppPreferenceKeys.AUDIO_DSP_BASS_RANGE, settings.bassRange)
    editor.putBoolean(AppPreferenceKeys.AUDIO_DSP_SURROUND_ENABLED, settings.surroundEnabled)
    editor.putInt(AppPreferenceKeys.AUDIO_DSP_SURROUND_DEPTH, settings.surroundDepth)
    editor.putInt(AppPreferenceKeys.AUDIO_DSP_SURROUND_DELAY_MS, settings.surroundDelayMs)
    editor.putBoolean(AppPreferenceKeys.AUDIO_DSP_REVERB_ENABLED, settings.reverbEnabled)
    editor.putInt(AppPreferenceKeys.AUDIO_DSP_REVERB_DEPTH, settings.reverbDepth)
    editor.putInt(AppPreferenceKeys.AUDIO_DSP_REVERB_PRESET, settings.reverbPreset)
    editor.putBoolean(AppPreferenceKeys.AUDIO_DSP_BITCRUSH_ENABLED, settings.bitCrushEnabled)
    editor.putInt(AppPreferenceKeys.AUDIO_DSP_BITCRUSH_BITS, settings.bitCrushBits)
}

private fun readCoreDspSettings(prefs: SharedPreferences, coreName: String?): DspSettings {
    val defaults = defaultDspSettings()
    val name = coreName ?: return defaults
    return DspSettings(
        bassEnabled = prefs.getBoolean(AppPreferenceKeys.audioDspCoreBassEnabledKey(name), defaults.bassEnabled),
        bassDepth = normalizeBassDepthPref(prefs.getInt(AppPreferenceKeys.audioDspCoreBassDepthKey(name), defaults.bassDepth)),
        bassRange = normalizeBassRangePref(prefs.getInt(AppPreferenceKeys.audioDspCoreBassRangeKey(name), defaults.bassRange)),
        surroundEnabled = prefs.getBoolean(AppPreferenceKeys.audioDspCoreSurroundEnabledKey(name), defaults.surroundEnabled),
        surroundDepth = prefs.getInt(AppPreferenceKeys.audioDspCoreSurroundDepthKey(name), defaults.surroundDepth).coerceIn(1, 16),
        surroundDelayMs = normalizeSurroundDelayMsPref(
            prefs.getInt(AppPreferenceKeys.audioDspCoreSurroundDelayMsKey(name), defaults.surroundDelayMs)
        ),
        reverbEnabled = prefs.getBoolean(AppPreferenceKeys.audioDspCoreReverbEnabledKey(name), defaults.reverbEnabled),
        reverbDepth = prefs.getInt(AppPreferenceKeys.audioDspCoreReverbDepthKey(name), defaults.reverbDepth).coerceIn(1, 16),
        reverbPreset = prefs.getInt(AppPreferenceKeys.audioDspCoreReverbPresetKey(name), defaults.reverbPreset).coerceIn(0, 28),
        bitCrushEnabled = prefs.getBoolean(AppPreferenceKeys.audioDspCoreBitCrushEnabledKey(name), defaults.bitCrushEnabled),
        bitCrushBits = prefs.getInt(AppPreferenceKeys.audioDspCoreBitCrushBitsKey(name), defaults.bitCrushBits).coerceIn(1, 24)
    )
}

private fun writeCoreDspSettings(editor: SharedPreferences.Editor, coreName: String, settings: DspSettings) {
    editor.putBoolean(AppPreferenceKeys.audioDspCoreBassEnabledKey(coreName), settings.bassEnabled)
    editor.putInt(AppPreferenceKeys.audioDspCoreBassDepthKey(coreName), settings.bassDepth)
    editor.putInt(AppPreferenceKeys.audioDspCoreBassRangeKey(coreName), settings.bassRange)
    editor.putBoolean(AppPreferenceKeys.audioDspCoreSurroundEnabledKey(coreName), settings.surroundEnabled)
    editor.putInt(AppPreferenceKeys.audioDspCoreSurroundDepthKey(coreName), settings.surroundDepth)
    editor.putInt(AppPreferenceKeys.audioDspCoreSurroundDelayMsKey(coreName), settings.surroundDelayMs)
    editor.putBoolean(AppPreferenceKeys.audioDspCoreReverbEnabledKey(coreName), settings.reverbEnabled)
    editor.putInt(AppPreferenceKeys.audioDspCoreReverbDepthKey(coreName), settings.reverbDepth)
    editor.putInt(AppPreferenceKeys.audioDspCoreReverbPresetKey(coreName), settings.reverbPreset)
    editor.putBoolean(AppPreferenceKeys.audioDspCoreBitCrushEnabledKey(coreName), settings.bitCrushEnabled)
    editor.putInt(AppPreferenceKeys.audioDspCoreBitCrushBitsKey(coreName), settings.bitCrushBits)
}

private fun hasCoreDspOverrides(prefs: SharedPreferences, coreName: String?): Boolean {
    val name = coreName ?: return false
    return prefs.contains(AppPreferenceKeys.audioDspCoreBassEnabledKey(name)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreBassDepthKey(name)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreBassRangeKey(name)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreSurroundEnabledKey(name)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreSurroundDepthKey(name)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreSurroundDelayMsKey(name)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreReverbEnabledKey(name)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreReverbDepthKey(name)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreReverbPresetKey(name)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreBitCrushEnabledKey(name)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreBitCrushBitsKey(name))
}

private fun readCoreIgnoreGlobalDsp(prefs: SharedPreferences, coreName: String?): Boolean {
    val name = coreName ?: return false
    return prefs.getBoolean(AppPreferenceKeys.audioDspCoreIgnoreGlobalKey(name), false)
}

private fun applyDspSettingsToNative(settings: DspSettings) {
    NativeBridge.setDspBassEnabled(settings.bassEnabled)
    NativeBridge.setDspBassDepth(settings.bassDepth)
    NativeBridge.setDspBassRange(settings.bassRange)
    NativeBridge.setDspSurroundEnabled(settings.surroundEnabled)
    NativeBridge.setDspSurroundDepth(settings.surroundDepth)
    NativeBridge.setDspSurroundDelayMs(settings.surroundDelayMs)
    NativeBridge.setDspReverbEnabled(settings.reverbEnabled)
    NativeBridge.setDspReverbDepth(settings.reverbDepth)
    NativeBridge.setDspReverbPreset(settings.reverbPreset)
    NativeBridge.setDspBitCrushEnabled(settings.bitCrushEnabled)
    NativeBridge.setDspBitCrushBits(settings.bitCrushBits)
}

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

    var globalDspSettings by remember { mutableStateOf(readGlobalDspSettings(prefs)) }
    var coreDspSettings by remember(lastUsedCoreName) { mutableStateOf(readCoreDspSettings(prefs, lastUsedCoreName)) }
    var coreDspHasOverrides by remember(lastUsedCoreName) { mutableStateOf(hasCoreDspOverrides(prefs, lastUsedCoreName)) }
    var coreIgnoreGlobalDsp by remember(lastUsedCoreName) { mutableStateOf(readCoreIgnoreGlobalDsp(prefs, lastUsedCoreName)) }
    var dspNamespaceSelection by remember {
        mutableStateOf(
            when (prefs.getString(AppPreferenceKeys.AUDIO_DSP_EDITOR_NAMESPACE, "global")) {
                "core" -> DspSettingsNamespace.CurrentCore
                else -> DspSettingsNamespace.Global
            }
        )
    }
    if (lastUsedCoreName == null && dspNamespaceSelection == DspSettingsNamespace.CurrentCore) {
        dspNamespaceSelection = DspSettingsNamespace.Global
    }

    var tempGlobalDspSettings by remember { mutableStateOf(globalDspSettings) }
    var tempCoreDspSettings by remember { mutableStateOf(coreDspSettings) }
    var tempCoreIgnoreGlobalDsp by remember { mutableStateOf(coreIgnoreGlobalDsp) }
    var tempDspNamespaceSelection by remember { mutableStateOf(dspNamespaceSelection) }

    fun resolveEffectiveDspSettings(
        global: DspSettings,
        core: DspSettings,
        coreHasOverrides: Boolean,
        ignoreGlobalForCore: Boolean
    ): DspSettings {
        return if (lastUsedCoreName == null) {
            global
        } else if (ignoreGlobalForCore || coreHasOverrides) {
            core
        } else {
            global
        }
    }

    fun applyCurrentTempDspSettingsToNative() {
        val coreHasTempOverrides = coreDspHasOverrides || tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore
        val effective = resolveEffectiveDspSettings(
            global = tempGlobalDspSettings,
            core = tempCoreDspSettings,
            coreHasOverrides = coreHasTempOverrides,
            ignoreGlobalForCore = tempCoreIgnoreGlobalDsp
        )
        applyDspSettingsToNative(effective)
    }

    LaunchedEffect(showAudioEffectsDialog) {
        if (!showAudioEffectsDialog) return@LaunchedEffect
        globalDspSettings = readGlobalDspSettings(prefs)
        coreDspSettings = readCoreDspSettings(prefs, lastUsedCoreName)
        coreDspHasOverrides = hasCoreDspOverrides(prefs, lastUsedCoreName)
        coreIgnoreGlobalDsp = readCoreIgnoreGlobalDsp(prefs, lastUsedCoreName)
        tempGlobalDspSettings = globalDspSettings
        tempCoreDspSettings = coreDspSettings
        tempCoreIgnoreGlobalDsp = coreIgnoreGlobalDsp
        tempDspNamespaceSelection = dspNamespaceSelection
        if (lastUsedCoreName == null && tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore) {
            tempDspNamespaceSelection = DspSettingsNamespace.Global
        }
    }

    LaunchedEffect(
        lastUsedCoreName,
        globalDspSettings,
        coreDspSettings,
        coreDspHasOverrides,
        coreIgnoreGlobalDsp,
        showAudioEffectsDialog
    ) {
        if (showAudioEffectsDialog) return@LaunchedEffect
        applyDspSettingsToNative(
            resolveEffectiveDspSettings(
                global = globalDspSettings,
                core = coreDspSettings,
                coreHasOverrides = coreDspHasOverrides,
                ignoreGlobalForCore = coreIgnoreGlobalDsp
            )
        )
    }

    val tempEditedDspSettings =
        if (tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore) tempCoreDspSettings else tempGlobalDspSettings

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
        tempDspBassEnabled = tempEditedDspSettings.bassEnabled,
        tempDspBassDepth = tempEditedDspSettings.bassDepth,
        tempDspBassRange = tempEditedDspSettings.bassRange,
        tempDspSurroundEnabled = tempEditedDspSettings.surroundEnabled,
        tempDspSurroundDepth = tempEditedDspSettings.surroundDepth,
        tempDspSurroundDelayMs = tempEditedDspSettings.surroundDelayMs,
        tempDspReverbEnabled = tempEditedDspSettings.reverbEnabled,
        tempDspReverbDepth = tempEditedDspSettings.reverbDepth,
        tempDspReverbPreset = tempEditedDspSettings.reverbPreset,
        tempDspBitCrushEnabled = tempEditedDspSettings.bitCrushEnabled,
        tempDspBitCrushBits = tempEditedDspSettings.bitCrushBits,
        tempDspNamespaceSelection = if (tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore) "core" else "global",
        tempDspIgnoreGlobalForCore = tempCoreIgnoreGlobalDsp,
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
        onDspNamespaceSelectionChange = { value ->
            tempDspNamespaceSelection = if (value == "core" && lastUsedCoreName != null) {
                DspSettingsNamespace.CurrentCore
            } else {
                DspSettingsNamespace.Global
            }
            applyCurrentTempDspSettingsToNative()
        },
        onDspIgnoreGlobalForCoreChange = {
            tempCoreIgnoreGlobalDsp = it
            applyCurrentTempDspSettingsToNative()
        },
        onDspBassEnabledChange = {
            if (tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore) {
                tempCoreDspSettings = tempCoreDspSettings.copy(bassEnabled = it)
            } else {
                tempGlobalDspSettings = tempGlobalDspSettings.copy(bassEnabled = it)
            }
            applyCurrentTempDspSettingsToNative()
        },
        onDspBassDepthChange = {
            val normalized = it.coerceIn(0, 4)
            if (tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore) {
                tempCoreDspSettings = tempCoreDspSettings.copy(bassDepth = normalized)
            } else {
                tempGlobalDspSettings = tempGlobalDspSettings.copy(bassDepth = normalized)
            }
            applyCurrentTempDspSettingsToNative()
        },
        onDspBassRangeChange = {
            val normalized = it.coerceIn(0, 4)
            if (tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore) {
                tempCoreDspSettings = tempCoreDspSettings.copy(bassRange = normalized)
            } else {
                tempGlobalDspSettings = tempGlobalDspSettings.copy(bassRange = normalized)
            }
            applyCurrentTempDspSettingsToNative()
        },
        onDspSurroundEnabledChange = {
            if (tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore) {
                tempCoreDspSettings = tempCoreDspSettings.copy(surroundEnabled = it)
            } else {
                tempGlobalDspSettings = tempGlobalDspSettings.copy(surroundEnabled = it)
            }
            applyCurrentTempDspSettingsToNative()
        },
        onDspSurroundDepthChange = {
            val normalized = it.coerceIn(1, 16)
            if (tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore) {
                tempCoreDspSettings = tempCoreDspSettings.copy(surroundDepth = normalized)
            } else {
                tempGlobalDspSettings = tempGlobalDspSettings.copy(surroundDepth = normalized)
            }
            applyCurrentTempDspSettingsToNative()
        },
        onDspSurroundDelayMsChange = {
            val normalized = normalizeSurroundDelayMsPref(it)
            if (tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore) {
                tempCoreDspSettings = tempCoreDspSettings.copy(surroundDelayMs = normalized)
            } else {
                tempGlobalDspSettings = tempGlobalDspSettings.copy(surroundDelayMs = normalized)
            }
            applyCurrentTempDspSettingsToNative()
        },
        onDspReverbEnabledChange = {
            if (tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore) {
                tempCoreDspSettings = tempCoreDspSettings.copy(reverbEnabled = it)
            } else {
                tempGlobalDspSettings = tempGlobalDspSettings.copy(reverbEnabled = it)
            }
            applyCurrentTempDspSettingsToNative()
        },
        onDspReverbDepthChange = {
            val normalized = it.coerceIn(1, 16)
            if (tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore) {
                tempCoreDspSettings = tempCoreDspSettings.copy(reverbDepth = normalized)
            } else {
                tempGlobalDspSettings = tempGlobalDspSettings.copy(reverbDepth = normalized)
            }
            applyCurrentTempDspSettingsToNative()
        },
        onDspReverbPresetChange = {
            val normalized = it.coerceIn(0, 28)
            if (tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore) {
                tempCoreDspSettings = tempCoreDspSettings.copy(reverbPreset = normalized)
            } else {
                tempGlobalDspSettings = tempGlobalDspSettings.copy(reverbPreset = normalized)
            }
            applyCurrentTempDspSettingsToNative()
        },
        onDspBitCrushEnabledChange = {
            if (tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore) {
                tempCoreDspSettings = tempCoreDspSettings.copy(bitCrushEnabled = it)
            } else {
                tempGlobalDspSettings = tempGlobalDspSettings.copy(bitCrushEnabled = it)
            }
            applyCurrentTempDspSettingsToNative()
        },
        onDspBitCrushBitsChange = {
            val normalized = it.coerceIn(1, 24)
            if (tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore) {
                tempCoreDspSettings = tempCoreDspSettings.copy(bitCrushBits = normalized)
            } else {
                tempGlobalDspSettings = tempGlobalDspSettings.copy(bitCrushBits = normalized)
            }
            applyCurrentTempDspSettingsToNative()
        },
        onAudioEffectsResetVolumeTab = {
            onTempMasterVolumeDbChanged(0f)
            onTempPluginVolumeDbChanged(0f)
            onTempSongVolumeDbChanged(0f)
            onTempIgnoreCoreVolumeForSongChanged(false)
            onTempForceMonoChanged(false)
            NativeBridge.setMasterGain(0f)
            NativeBridge.setPluginGain(0f)
            NativeBridge.setSongGain(0f)
            NativeBridge.setForceMono(false)
        },
        onAudioEffectsResetDspScope = { scope ->
            val defaults = defaultDspSettings()
            if (scope == "core" && lastUsedCoreName != null) {
                tempCoreDspSettings = defaults
                tempCoreIgnoreGlobalDsp = false
            } else {
                tempGlobalDspSettings = defaults
            }
            applyCurrentTempDspSettingsToNative()
        },
        onAudioEffectsDismiss = {
            NativeBridge.setMasterGain(masterVolumeDb)
            NativeBridge.setPluginGain(
                if (ignoreCoreVolumeForSong) 0f else readPluginVolumeForDecoder(prefs, lastUsedCoreName)
            )
            NativeBridge.setSongGain(songVolumeDb)
            NativeBridge.setForceMono(forceMono)
            applyDspSettingsToNative(
                resolveEffectiveDspSettings(
                    global = globalDspSettings,
                    core = coreDspSettings,
                    coreHasOverrides = coreDspHasOverrides,
                    ignoreGlobalForCore = coreIgnoreGlobalDsp
                )
            )
            onShowAudioEffectsDialogChanged(false)
        },
        onAudioEffectsConfirm = {
            onMasterVolumeDbChanged(tempMasterVolumeDb)
            onPluginVolumeDbChanged(tempPluginVolumeDb)
            onSongVolumeDbChanged(tempSongVolumeDb)
            onIgnoreCoreVolumeForSongChanged(tempIgnoreCoreVolumeForSong)
            onForceMonoChanged(tempForceMono)

            dspNamespaceSelection = tempDspNamespaceSelection
            globalDspSettings = tempGlobalDspSettings
            coreDspSettings = tempCoreDspSettings
            coreIgnoreGlobalDsp = tempCoreIgnoreGlobalDsp

            prefs.edit().apply {
                putFloat(AppPreferenceKeys.AUDIO_MASTER_VOLUME_DB, tempMasterVolumeDb)
                putBoolean(AppPreferenceKeys.AUDIO_FORCE_MONO, tempForceMono)
                putString(
                    AppPreferenceKeys.AUDIO_DSP_EDITOR_NAMESPACE,
                    if (dspNamespaceSelection == DspSettingsNamespace.CurrentCore) "core" else "global"
                )
                writeGlobalDspSettings(this, tempGlobalDspSettings)
                if (lastUsedCoreName != null) {
                    putBoolean(AppPreferenceKeys.audioDspCoreIgnoreGlobalKey(lastUsedCoreName), tempCoreIgnoreGlobalDsp)
                    if (dspNamespaceSelection == DspSettingsNamespace.CurrentCore) {
                        writeCoreDspSettings(this, lastUsedCoreName, tempCoreDspSettings)
                        coreDspHasOverrides = true
                    } else {
                        coreDspHasOverrides = hasCoreDspOverrides(prefs, lastUsedCoreName)
                    }
                }
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
            applyDspSettingsToNative(
                resolveEffectiveDspSettings(
                    global = globalDspSettings,
                    core = coreDspSettings,
                    coreHasOverrides = coreDspHasOverrides,
                    ignoreGlobalForCore = coreIgnoreGlobalDsp
                )
            )
            onShowAudioEffectsDialogChanged(false)
        }
    )
}
