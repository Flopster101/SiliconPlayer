package com.flopster101.siliconplayer.ui.visualization

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.isBasicVisualizationMode
import com.flopster101.siliconplayer.parseEnabledVisualizationModes
import com.flopster101.siliconplayer.serializeEnabledVisualizationModes

internal data class VisualizationSelectionState(
    val currentMode: VisualizationMode,
    val requestedMode: VisualizationMode,
    val lastBasicMode: VisualizationMode?,
    val enabledModes: Set<VisualizationMode>,
    val onCurrentModeChanged: (VisualizationMode) -> Unit,
    val onRequestedModeChanged: (VisualizationMode) -> Unit,
    val onLastBasicModeChanged: (VisualizationMode?) -> Unit,
    val onEnabledModesChanged: (Set<VisualizationMode>) -> Unit
)

@Composable
internal fun rememberVisualizationSelectionState(
    prefs: SharedPreferences
): VisualizationSelectionState {
    var currentMode by remember {
        mutableStateOf(
            VisualizationMode.fromStorage(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_MODE, VisualizationMode.Off.storageValue)
            )
        )
    }
    var requestedMode by remember {
        val storedCurrent = VisualizationMode.fromStorage(
            prefs.getString(AppPreferenceKeys.VISUALIZATION_MODE, VisualizationMode.Off.storageValue)
        )
        mutableStateOf(
            VisualizationMode.fromStorage(
                prefs.getString(
                    AppPreferenceKeys.VISUALIZATION_REQUESTED_MODE,
                    storedCurrent.storageValue
                )
            )
        )
    }
    var lastBasicMode by remember {
        val storedCurrent = VisualizationMode.fromStorage(
            prefs.getString(AppPreferenceKeys.VISUALIZATION_MODE, VisualizationMode.Off.storageValue)
        )
        mutableStateOf(
            prefs.getString(AppPreferenceKeys.VISUALIZATION_LAST_BASIC_MODE, null)
                ?.let(VisualizationMode::fromStorage)
                ?.takeIf { it.isBasicVisualizationMode() }
                ?: storedCurrent.takeIf { it.isBasicVisualizationMode() }
        )
    }
    var enabledModes by remember {
        mutableStateOf(
            parseEnabledVisualizationModes(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_ENABLED_MODES, null)
            )
        )
    }

    LaunchedEffect(currentMode) {
        prefs.edit()
            .putString(AppPreferenceKeys.VISUALIZATION_MODE, currentMode.storageValue)
            .apply()
    }

    LaunchedEffect(requestedMode) {
        prefs.edit()
            .putString(
                AppPreferenceKeys.VISUALIZATION_REQUESTED_MODE,
                requestedMode.storageValue
            )
            .apply()
    }

    LaunchedEffect(lastBasicMode) {
        prefs.edit().apply {
            if (lastBasicMode != null) {
                putString(AppPreferenceKeys.VISUALIZATION_LAST_BASIC_MODE, lastBasicMode?.storageValue)
            } else {
                remove(AppPreferenceKeys.VISUALIZATION_LAST_BASIC_MODE)
            }
        }.apply()
    }

    LaunchedEffect(enabledModes) {
        prefs.edit()
            .putString(
                AppPreferenceKeys.VISUALIZATION_ENABLED_MODES,
                serializeEnabledVisualizationModes(enabledModes)
            )
            .apply()
    }

    return remember(currentMode, requestedMode, lastBasicMode, enabledModes) {
        VisualizationSelectionState(
            currentMode = currentMode,
            requestedMode = requestedMode,
            lastBasicMode = lastBasicMode,
            enabledModes = enabledModes,
            onCurrentModeChanged = { currentMode = it },
            onRequestedModeChanged = { requestedMode = it },
            onLastBasicModeChanged = { lastBasicMode = it },
            onEnabledModesChanged = { enabledModes = it }
        )
    }
}
