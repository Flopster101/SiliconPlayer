package com.flopster101.siliconplayer.ui.visualization

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.flopster101.siliconplayer.DecoderNames
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.pluginNameForCoreName

internal data class VisualizationUiState(
    val mode: VisualizationMode,
    val enabledModes: Set<VisualizationMode>,
    val availableModes: List<VisualizationMode>,
    val onCycleMode: () -> Unit,
    val onSelectMode: (VisualizationMode) -> Unit,
    val onSetEnabledModes: (Set<VisualizationMode>) -> Unit
)

@Composable
internal fun rememberVisualizationUiState(
    prefs: SharedPreferences,
    activeCoreName: String?,
    isPlayerSurfaceVisible: Boolean
): VisualizationUiState {
    val selectionState = rememberVisualizationSelectionState(prefs)
    val currentMode = selectionState.currentMode
    val enabledModes = selectionState.enabledModes
    val currentCorePluginName = pluginNameForCoreName(activeCoreName)

    LaunchedEffect(activeCoreName, currentCorePluginName, currentMode, isPlayerSurfaceVisible) {
        if (currentCorePluginName != DecoderNames.LIB_SID_PLAY_FP &&
            currentCorePluginName != DecoderNames.C_RSID &&
            currentCorePluginName != DecoderNames.GAME_MUSIC_EMU &&
            currentCorePluginName != DecoderNames.SC68) {
            return@LaunchedEffect
        }
        val coreName = activeCoreName?.trim().takeIf { !it.isNullOrEmpty() } ?: return@LaunchedEffect
        val channelScopeActive =
            isPlayerSurfaceVisible && currentMode == VisualizationMode.ChannelScope
        NativeBridge.setCoreOption(
            coreName,
            "visualization.channel_scope_active",
            if (channelScopeActive) "true" else "false"
        )
    }

    val coordinator = rememberVisualizationModeCoordinator(
        enabledModes = enabledModes,
        activeCoreName = activeCoreName,
        currentMode = currentMode,
        requestedMode = selectionState.requestedMode,
        lastBasicMode = selectionState.lastBasicMode,
        onModeChanged = selectionState.onCurrentModeChanged,
        onRequestedModeChanged = selectionState.onRequestedModeChanged,
        onLastBasicModeChanged = selectionState.onLastBasicModeChanged,
        onEnabledModesChanged = selectionState.onEnabledModesChanged
    )

    return remember(currentMode, enabledModes, coordinator) {
        VisualizationUiState(
            mode = currentMode,
            enabledModes = enabledModes,
            availableModes = coordinator.availableModes,
            onCycleMode = coordinator.onCycleMode,
            onSelectMode = coordinator.onSelectMode,
            onSetEnabledModes = coordinator.onSetEnabledModes
        )
    }
}
