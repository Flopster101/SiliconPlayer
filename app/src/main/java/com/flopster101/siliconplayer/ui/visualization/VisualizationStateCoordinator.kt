package com.flopster101.siliconplayer.ui.visualization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.isVisualizationModeSelectable
import com.flopster101.siliconplayer.selectableVisualizationModes

internal data class VisualizationModeCoordinator(
    val availableModes: List<VisualizationMode>,
    val onCycleMode: () -> Unit,
    val onSelectMode: (VisualizationMode) -> Unit,
    val onSetEnabledModes: (Set<VisualizationMode>) -> Unit
)

@Composable
internal fun rememberVisualizationModeCoordinator(
    enabledModes: Set<VisualizationMode>,
    activeCoreName: String?,
    currentMode: VisualizationMode,
    onModeChanged: (VisualizationMode) -> Unit,
    onEnabledModesChanged: (Set<VisualizationMode>) -> Unit
): VisualizationModeCoordinator {
    val availableModes = remember(enabledModes, activeCoreName) {
        listOf(VisualizationMode.Off) + selectableVisualizationModes.filter { mode ->
            isVisualizationModeSelectable(mode, enabledModes, activeCoreName)
        }
    }

    LaunchedEffect(availableModes, currentMode) {
        if (!availableModes.contains(currentMode)) {
            onModeChanged(VisualizationMode.Off)
        }
    }

    return remember(
        availableModes,
        enabledModes,
        activeCoreName,
        currentMode,
        onModeChanged,
        onEnabledModesChanged
    ) {
        VisualizationModeCoordinator(
            availableModes = availableModes,
            onCycleMode = {
                val currentIndex = availableModes.indexOf(currentMode).takeIf { it >= 0 } ?: 0
                onModeChanged(availableModes[(currentIndex + 1) % availableModes.size])
            },
            onSelectMode = { requestedMode ->
                if (availableModes.contains(requestedMode)) {
                    onModeChanged(requestedMode)
                }
            },
            onSetEnabledModes = { requested ->
                val normalized = requested.intersect(selectableVisualizationModes.toSet())
                val previousModes = availableModes
                onEnabledModesChanged(normalized)
                if (
                    currentMode != VisualizationMode.Off &&
                    !isVisualizationModeSelectable(currentMode, normalized, activeCoreName)
                ) {
                    val currentIndex = previousModes.indexOf(currentMode)
                    val fallback = if (currentIndex > 0) {
                        previousModes
                            .subList(0, currentIndex)
                            .lastOrNull {
                                it == VisualizationMode.Off ||
                                    isVisualizationModeSelectable(it, normalized, activeCoreName)
                            }
                    } else {
                        null
                    } ?: VisualizationMode.Off
                    onModeChanged(fallback)
                }
            }
        )
    }
}
