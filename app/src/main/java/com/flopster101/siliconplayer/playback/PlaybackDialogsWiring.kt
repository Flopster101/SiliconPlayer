package com.flopster101.siliconplayer

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
        onAudioEffectsReset = {
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
        onAudioEffectsDismiss = {
            NativeBridge.setMasterGain(masterVolumeDb)
            NativeBridge.setPluginGain(
                if (ignoreCoreVolumeForSong) 0f else readPluginVolumeForDecoder(prefs, lastUsedCoreName)
            )
            NativeBridge.setSongGain(songVolumeDb)
            NativeBridge.setForceMono(forceMono)
            onShowAudioEffectsDialogChanged(false)
        },
        onAudioEffectsConfirm = {
            onMasterVolumeDbChanged(tempMasterVolumeDb)
            onPluginVolumeDbChanged(tempPluginVolumeDb)
            onSongVolumeDbChanged(tempSongVolumeDb)
            onIgnoreCoreVolumeForSongChanged(tempIgnoreCoreVolumeForSong)
            onForceMonoChanged(tempForceMono)
            prefs.edit().apply {
                putFloat(AppPreferenceKeys.AUDIO_MASTER_VOLUME_DB, tempMasterVolumeDb)
                putBoolean(AppPreferenceKeys.AUDIO_FORCE_MONO, tempForceMono)
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
