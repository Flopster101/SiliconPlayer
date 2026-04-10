package com.flopster101.siliconplayer.ui.visualization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.isAdvancedVisualizationMode
import com.flopster101.siliconplayer.isBasicVisualizationMode
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
    requestedMode: VisualizationMode,
    lastBasicMode: VisualizationMode?,
    onModeChanged: (VisualizationMode) -> Unit,
    onRequestedModeChanged: (VisualizationMode) -> Unit,
    onLastBasicModeChanged: (VisualizationMode?) -> Unit,
    onEnabledModesChanged: (Set<VisualizationMode>) -> Unit
): VisualizationModeCoordinator {
    val availableModes = remember(enabledModes, activeCoreName) {
        listOf(VisualizationMode.Off) + selectableVisualizationModes.filter { mode ->
            isVisualizationModeSelectable(mode, enabledModes, activeCoreName)
        }
    }

    val resolvedMode = remember(enabledModes, activeCoreName, requestedMode, lastBasicMode) {
        resolveVisualizationMode(
            requestedMode = requestedMode,
            lastBasicMode = lastBasicMode,
            enabledModes = enabledModes,
            activeCoreName = activeCoreName
        )
    }

    LaunchedEffect(resolvedMode, currentMode) {
        if (currentMode != resolvedMode) {
            onModeChanged(resolvedMode)
        }
    }

    return remember(
        availableModes,
        enabledModes,
        activeCoreName,
        currentMode,
        requestedMode,
        lastBasicMode,
        onModeChanged,
        onRequestedModeChanged,
        onLastBasicModeChanged,
        onEnabledModesChanged
    ) {
        VisualizationModeCoordinator(
            availableModes = availableModes,
            onCycleMode = {
                val currentIndex = availableModes.indexOf(currentMode).takeIf { it >= 0 } ?: 0
                val requested = availableModes[(currentIndex + 1) % availableModes.size]
                onRequestedModeChanged(requested)
                if (requested.isBasicVisualizationMode()) {
                    onLastBasicModeChanged(requested)
                }
            },
            onSelectMode = { requestedMode ->
                if (availableModes.contains(requestedMode)) {
                    onRequestedModeChanged(requestedMode)
                    if (requestedMode.isBasicVisualizationMode()) {
                        onLastBasicModeChanged(requestedMode)
                    }
                }
            },
            onSetEnabledModes = { requested ->
                val normalized = requested.intersect(selectableVisualizationModes.toSet())
                onEnabledModesChanged(normalized)
                val requestedAfterNormalization = when {
                    requestedMode == VisualizationMode.Off -> VisualizationMode.Off
                    normalized.contains(requestedMode) -> requestedMode
                    currentMode != VisualizationMode.Off &&
                        currentMode != requestedMode &&
                        isVisualizationModeSelectable(currentMode, normalized, activeCoreName) -> currentMode
                    else -> resolveVisualizationMode(
                        requestedMode = requestedMode,
                        lastBasicMode = lastBasicMode,
                        enabledModes = normalized,
                        activeCoreName = activeCoreName
                    )
                }
                if (requestedAfterNormalization != requestedMode) {
                    onRequestedModeChanged(requestedAfterNormalization)
                    if (requestedAfterNormalization.isBasicVisualizationMode()) {
                        onLastBasicModeChanged(requestedAfterNormalization)
                    }
                }
            }
        )
    }
}

private fun resolveVisualizationMode(
    requestedMode: VisualizationMode,
    lastBasicMode: VisualizationMode?,
    enabledModes: Set<VisualizationMode>,
    activeCoreName: String?
): VisualizationMode {
    if (requestedMode == VisualizationMode.Off) {
        return VisualizationMode.Off
    }
    if (isVisualizationModeSelectable(requestedMode, enabledModes, activeCoreName)) {
        return requestedMode
    }
    if (requestedMode.isAdvancedVisualizationMode()) {
        val basicFallback = lastBasicMode?.takeIf {
            it.isBasicVisualizationMode() &&
                isVisualizationModeSelectable(it, enabledModes, activeCoreName)
        }
        return basicFallback ?: VisualizationMode.Off
    }
    return lastBasicMode?.takeIf {
        it.isBasicVisualizationMode() &&
            isVisualizationModeSelectable(it, enabledModes, activeCoreName)
    } ?: VisualizationMode.Off
}
