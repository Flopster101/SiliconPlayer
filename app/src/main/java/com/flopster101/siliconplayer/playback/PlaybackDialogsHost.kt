package com.flopster101.siliconplayer

import androidx.compose.runtime.Composable
import com.flopster101.siliconplayer.ui.dialogs.AudioEffectsDialog
import com.flopster101.siliconplayer.ui.dialogs.RemoteLoadProgressDialog
import com.flopster101.siliconplayer.ui.dialogs.SoxExperimentalDialog
import com.flopster101.siliconplayer.ui.dialogs.SubtuneSelectorDialog
import com.flopster101.siliconplayer.ui.dialogs.UrlOrPathDialog
@Composable
internal fun PlaybackDialogsHost(
    showUrlOrPathDialog: Boolean,
    urlOrPathInput: String,
    urlOrPathForceCaching: Boolean,
    onUrlOrPathInputChange: (String) -> Unit,
    onUrlOrPathForceCachingChange: (Boolean) -> Unit,
    onUrlOrPathDismiss: () -> Unit,
    onUrlOrPathOpen: () -> Unit,
    remoteLoadUiState: RemoteLoadUiState?,
    onCancelRemoteLoad: () -> Unit,
    showSoxExperimentalDialog: Boolean,
    onDismissSoxExperimentalDialog: () -> Unit,
    showSubtuneSelectorDialog: Boolean,
    subtuneEntries: List<SubtuneEntry>,
    currentSubtuneIndex: Int,
    onSelectSubtune: (Int) -> Unit,
    onDismissSubtuneSelector: () -> Unit,
    showAudioEffectsDialog: Boolean,
    tempMasterVolumeDb: Float,
    tempPluginVolumeDb: Float,
    tempSongVolumeDb: Float,
    tempIgnoreCoreVolumeForSong: Boolean,
    tempForceMono: Boolean,
    hasActiveCore: Boolean,
    hasActiveSong: Boolean,
    currentCoreName: String?,
    onMasterVolumeChange: (Float) -> Unit,
    onPluginVolumeChange: (Float) -> Unit,
    onSongVolumeChange: (Float) -> Unit,
    onIgnoreCoreVolumeForSongChange: (Boolean) -> Unit,
    onForceMonoChange: (Boolean) -> Unit,
    onAudioEffectsReset: () -> Unit,
    onAudioEffectsDismiss: () -> Unit,
    onAudioEffectsConfirm: () -> Unit
) {
    if (showUrlOrPathDialog) {
        UrlOrPathDialog(
            input = urlOrPathInput,
            forceCaching = urlOrPathForceCaching,
            onInputChange = onUrlOrPathInputChange,
            onForceCachingChange = onUrlOrPathForceCachingChange,
            onDismiss = onUrlOrPathDismiss,
            onOpen = onUrlOrPathOpen
        )
    }

    remoteLoadUiState?.let { loadState ->
        RemoteLoadProgressDialog(
            loadState = loadState,
            onCancel = onCancelRemoteLoad
        )
    }

    if (showSoxExperimentalDialog) {
        SoxExperimentalDialog(onDismiss = onDismissSoxExperimentalDialog)
    }

    if (showSubtuneSelectorDialog) {
        SubtuneSelectorDialog(
            subtuneEntries = subtuneEntries,
            currentSubtuneIndex = currentSubtuneIndex,
            onSelectSubtune = onSelectSubtune,
            onDismiss = onDismissSubtuneSelector
        )
    }

    if (showAudioEffectsDialog) {
        AudioEffectsDialog(
            masterVolumeDb = tempMasterVolumeDb,
            pluginVolumeDb = tempPluginVolumeDb,
            songVolumeDb = tempSongVolumeDb,
            ignoreCoreVolumeForSong = tempIgnoreCoreVolumeForSong,
            forceMono = tempForceMono,
            hasActiveCore = hasActiveCore,
            hasActiveSong = hasActiveSong,
            currentCoreName = currentCoreName,
            onMasterVolumeChange = onMasterVolumeChange,
            onPluginVolumeChange = onPluginVolumeChange,
            onSongVolumeChange = onSongVolumeChange,
            onIgnoreCoreVolumeForSongChange = onIgnoreCoreVolumeForSongChange,
            onForceMonoChange = onForceMonoChange,
            onReset = onAudioEffectsReset,
            onDismiss = onAudioEffectsDismiss,
            onConfirm = onAudioEffectsConfirm
        )
    }
}
