package com.flopster101.siliconplayer

import androidx.compose.runtime.Composable
import com.flopster101.siliconplayer.ui.dialogs.AudioEffectsDialog
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
    tempDspBassEnabled: Boolean,
    tempDspBassDepth: Int,
    tempDspBassRange: Int,
    tempDspSurroundEnabled: Boolean,
    tempDspSurroundDepth: Int,
    tempDspSurroundDelayMs: Int,
    tempDspReverbEnabled: Boolean,
    tempDspReverbDepth: Int,
    tempDspReverbPreset: Int,
    tempDspBitCrushEnabled: Boolean,
    tempDspBitCrushBits: Int,
    hasActiveCore: Boolean,
    hasActiveSong: Boolean,
    currentCoreName: String?,
    onMasterVolumeChange: (Float) -> Unit,
    onPluginVolumeChange: (Float) -> Unit,
    onSongVolumeChange: (Float) -> Unit,
    onIgnoreCoreVolumeForSongChange: (Boolean) -> Unit,
    onForceMonoChange: (Boolean) -> Unit,
    onDspBassEnabledChange: (Boolean) -> Unit,
    onDspBassDepthChange: (Int) -> Unit,
    onDspBassRangeChange: (Int) -> Unit,
    onDspSurroundEnabledChange: (Boolean) -> Unit,
    onDspSurroundDepthChange: (Int) -> Unit,
    onDspSurroundDelayMsChange: (Int) -> Unit,
    onDspReverbEnabledChange: (Boolean) -> Unit,
    onDspReverbDepthChange: (Int) -> Unit,
    onDspReverbPresetChange: (Int) -> Unit,
    onDspBitCrushEnabledChange: (Boolean) -> Unit,
    onDspBitCrushBitsChange: (Int) -> Unit,
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
            dspBassEnabled = tempDspBassEnabled,
            dspBassDepth = tempDspBassDepth,
            dspBassRange = tempDspBassRange,
            dspSurroundEnabled = tempDspSurroundEnabled,
            dspSurroundDepth = tempDspSurroundDepth,
            dspSurroundDelayMs = tempDspSurroundDelayMs,
            dspReverbEnabled = tempDspReverbEnabled,
            dspReverbDepth = tempDspReverbDepth,
            dspReverbPreset = tempDspReverbPreset,
            dspBitCrushEnabled = tempDspBitCrushEnabled,
            dspBitCrushBits = tempDspBitCrushBits,
            hasActiveCore = hasActiveCore,
            hasActiveSong = hasActiveSong,
            currentCoreName = currentCoreName,
            onMasterVolumeChange = onMasterVolumeChange,
            onPluginVolumeChange = onPluginVolumeChange,
            onSongVolumeChange = onSongVolumeChange,
            onIgnoreCoreVolumeForSongChange = onIgnoreCoreVolumeForSongChange,
            onForceMonoChange = onForceMonoChange,
            onDspBassEnabledChange = onDspBassEnabledChange,
            onDspBassDepthChange = onDspBassDepthChange,
            onDspBassRangeChange = onDspBassRangeChange,
            onDspSurroundEnabledChange = onDspSurroundEnabledChange,
            onDspSurroundDepthChange = onDspSurroundDepthChange,
            onDspSurroundDelayMsChange = onDspSurroundDelayMsChange,
            onDspReverbEnabledChange = onDspReverbEnabledChange,
            onDspReverbDepthChange = onDspReverbDepthChange,
            onDspReverbPresetChange = onDspReverbPresetChange,
            onDspBitCrushEnabledChange = onDspBitCrushEnabledChange,
            onDspBitCrushBitsChange = onDspBitCrushBitsChange,
            onReset = onAudioEffectsReset,
            onDismiss = onAudioEffectsDismiss,
            onConfirm = onAudioEffectsConfirm
        )
    }
}
