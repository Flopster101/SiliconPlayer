package com.flopster101.siliconplayer

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun VisualizationBasicOscilloscopeRouteContent(
    visualizationOscStereo: Boolean,
    onVisualizationOscStereoChanged: (Boolean) -> Unit
) {
    val prefsName = "silicon_player_settings"
    val oscWindowKey = "visualization_osc_window_ms"
    val oscTriggerKey = "visualization_osc_trigger_mode"
    val oscFpsModeKey = "visualization_osc_fps_mode"
    val oscRenderBackendKey = "visualization_osc_render_backend"
    val oscLineWidthKey = "visualization_osc_line_width_dp"
    val oscGridWidthKey = "visualization_osc_grid_width_dp"
    val oscVerticalGridEnabledKey = "visualization_osc_vertical_grid_enabled"
    val oscCenterLineEnabledKey = "visualization_osc_center_line_enabled"
    val oscLineNoArtworkColorModeKey = "visualization_osc_line_color_mode_no_artwork"
    val oscGridNoArtworkColorModeKey = "visualization_osc_grid_color_mode_no_artwork"
    val oscLineArtworkColorModeKey = "visualization_osc_line_color_mode_with_artwork"
    val oscGridArtworkColorModeKey = "visualization_osc_grid_color_mode_with_artwork"
    val oscCustomLineColorKey = "visualization_osc_custom_line_color_argb"
    val oscCustomGridColorKey = "visualization_osc_custom_grid_color_argb"
    val oscContrastBackdropEnabledKey = AppPreferenceKeys.VISUALIZATION_OSC_CONTRAST_BACKDROP_ENABLED
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    }
    var visualizationOscWindowMs by remember {
        mutableIntStateOf(
            prefs.getInt(oscWindowKey, AppDefaults.Visualization.Oscilloscope.windowMs).coerceIn(
                AppDefaults.Visualization.Oscilloscope.windowRangeMs.first,
                AppDefaults.Visualization.Oscilloscope.windowRangeMs.last
            )
        )
    }
    var visualizationOscTriggerMode by remember {
        mutableStateOf(
            VisualizationOscTriggerMode.fromStorage(
                prefs.getString(
                    oscTriggerKey,
                    AppDefaults.Visualization.Oscilloscope.triggerMode.storageValue
                )
            )
        )
    }
    var visualizationOscFpsMode by remember {
        mutableStateOf(
            VisualizationOscFpsMode.fromStorage(
                prefs.getString(
                    oscFpsModeKey,
                    AppDefaults.Visualization.Oscilloscope.fpsMode.storageValue
                )
            )
        )
    }
    var visualizationOscRenderBackend by remember {
        mutableStateOf(
            VisualizationRenderBackend.fromStorage(
                prefs.getString(
                    oscRenderBackendKey,
                    AppDefaults.Visualization.Oscilloscope.renderBackend.storageValue
                ),
                AppDefaults.Visualization.Oscilloscope.renderBackend
            )
        )
    }
    var visualizationOscLineWidthDp by remember {
        mutableIntStateOf(
            prefs.getInt(
                oscLineWidthKey,
                AppDefaults.Visualization.Oscilloscope.lineWidthDp
            ).coerceIn(
                AppDefaults.Visualization.Oscilloscope.lineWidthRangeDp.first,
                AppDefaults.Visualization.Oscilloscope.lineWidthRangeDp.last
            )
        )
    }
    var visualizationOscGridWidthDp by remember {
        mutableIntStateOf(
            prefs.getInt(
                oscGridWidthKey,
                AppDefaults.Visualization.Oscilloscope.gridWidthDp
            ).coerceIn(
                AppDefaults.Visualization.Oscilloscope.gridWidthRangeDp.first,
                AppDefaults.Visualization.Oscilloscope.gridWidthRangeDp.last
            )
        )
    }
    var visualizationOscVerticalGridEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                oscVerticalGridEnabledKey,
                AppDefaults.Visualization.Oscilloscope.verticalGridEnabled
            )
        )
    }
    var visualizationOscCenterLineEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                oscCenterLineEnabledKey,
                AppDefaults.Visualization.Oscilloscope.centerLineEnabled
            )
        )
    }
    var oscLineColorModeNoArtwork by remember {
        mutableStateOf(
            VisualizationOscColorMode.fromStorage(
                prefs.getString(
                    oscLineNoArtworkColorModeKey,
                    AppDefaults.Visualization.Oscilloscope.lineColorModeNoArtwork.storageValue
                ),
                AppDefaults.Visualization.Oscilloscope.lineColorModeNoArtwork
            )
        )
    }
    var oscGridColorModeNoArtwork by remember {
        mutableStateOf(
            VisualizationOscColorMode.fromStorage(
                prefs.getString(
                    oscGridNoArtworkColorModeKey,
                    AppDefaults.Visualization.Oscilloscope.gridColorModeNoArtwork.storageValue
                ),
                AppDefaults.Visualization.Oscilloscope.gridColorModeNoArtwork
            )
        )
    }
    var oscLineColorModeWithArtwork by remember {
        mutableStateOf(
            VisualizationOscColorMode.fromStorage(
                prefs.getString(
                    oscLineArtworkColorModeKey,
                    AppDefaults.Visualization.Oscilloscope.lineColorModeWithArtwork.storageValue
                ),
                AppDefaults.Visualization.Oscilloscope.lineColorModeWithArtwork
            )
        )
    }
    var oscGridColorModeWithArtwork by remember {
        mutableStateOf(
            VisualizationOscColorMode.fromStorage(
                prefs.getString(
                    oscGridArtworkColorModeKey,
                    AppDefaults.Visualization.Oscilloscope.gridColorModeWithArtwork.storageValue
                ),
                AppDefaults.Visualization.Oscilloscope.gridColorModeWithArtwork
            )
        )
    }
    var oscCustomLineColorArgb by remember {
        mutableIntStateOf(
            prefs.getInt(
                oscCustomLineColorKey,
                AppDefaults.Visualization.Oscilloscope.customLineColorArgb
            )
        )
    }
    var oscCustomGridColorArgb by remember {
        mutableIntStateOf(
            prefs.getInt(
                oscCustomGridColorKey,
                AppDefaults.Visualization.Oscilloscope.customGridColorArgb
            )
        )
    }
    var oscContrastBackdropEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                oscContrastBackdropEnabledKey,
                AppDefaults.Visualization.Oscilloscope.contrastBackdropEnabled
            )
        )
    }
    var showWindowDialog by remember { mutableStateOf(false) }
    var showTriggerDialog by remember { mutableStateOf(false) }
    var showFpsModeDialog by remember { mutableStateOf(false) }
    var showRenderBackendDialog by remember { mutableStateOf(false) }
    var showLineWidthDialog by remember { mutableStateOf(false) }
    var showGridWidthDialog by remember { mutableStateOf(false) }
    var showLineNoArtworkColorModeDialog by remember { mutableStateOf(false) }
    var showGridNoArtworkColorModeDialog by remember { mutableStateOf(false) }
    var showLineArtworkColorModeDialog by remember { mutableStateOf(false) }
    var showGridArtworkColorModeDialog by remember { mutableStateOf(false) }
    var showCustomLineColorDialog by remember { mutableStateOf(false) }
    var showCustomGridColorDialog by remember { mutableStateOf(false) }

    SettingsSectionLabel("Oscilloscope")
    PlayerSettingToggleCard(
        title = "Stereo mode",
        description = "Render stereo waveform when channel layout supports it.",
        checked = visualizationOscStereo,
        onCheckedChange = onVisualizationOscStereoChanged
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsValuePickerCard(
        title = "Visible window",
        description = "Time span shown across the oscilloscope view.",
        value = "${visualizationOscWindowMs} ms",
        onClick = { showWindowDialog = true }
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsValuePickerCard(
        title = "Trigger",
        description = "Sync mode used to stabilize waveform start point.",
        value = visualizationOscTriggerMode.label,
        onClick = { showTriggerDialog = true }
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsValuePickerCard(
        title = "Scope frame rate",
        description = "Rendering rate for oscilloscope updates.",
        value = visualizationOscFpsMode.label,
        onClick = { showFpsModeDialog = true }
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsValuePickerCard(
        title = "Renderer backend",
        description = "Rendering backend used for oscilloscope drawing.",
        value = visualizationOscRenderBackend.label,
        onClick = { showRenderBackendDialog = true }
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsValuePickerCard(
        title = "Line width",
        description = "Stroke width for waveform lines.",
        value = "${visualizationOscLineWidthDp}dp",
        onClick = { showLineWidthDialog = true }
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsValuePickerCard(
        title = "Grid width",
        description = "Stroke width for grid lines.",
        value = "${visualizationOscGridWidthDp}dp",
        onClick = { showGridWidthDialog = true }
    )
    Spacer(modifier = Modifier.height(10.dp))
    PlayerSettingToggleCard(
        title = "Show vertical grid lines",
        description = "Display vertical time divisions in the oscilloscope.",
        checked = visualizationOscVerticalGridEnabled,
        onCheckedChange = { enabled ->
            visualizationOscVerticalGridEnabled = enabled
            prefs.edit().putBoolean(oscVerticalGridEnabledKey, enabled).apply()
        }
    )
    Spacer(modifier = Modifier.height(10.dp))
    PlayerSettingToggleCard(
        title = "Show centerline",
        description = "Display the waveform center reference line.",
        checked = visualizationOscCenterLineEnabled,
        onCheckedChange = { enabled ->
            visualizationOscCenterLineEnabled = enabled
            prefs.edit().putBoolean(oscCenterLineEnabledKey, enabled).apply()
        }
    )
    Spacer(modifier = Modifier.height(10.dp))
    PlayerSettingToggleCard(
        title = "Contrast backdrop",
        description = "Add a dark backdrop behind waveforms for better readability over artwork.",
        checked = oscContrastBackdropEnabled,
        onCheckedChange = { enabled ->
            oscContrastBackdropEnabled = enabled
            prefs.edit().putBoolean(oscContrastBackdropEnabledKey, enabled).apply()
        }
    )
    Spacer(modifier = Modifier.height(16.dp))
    SettingsSectionLabel("Colors (no artwork)")
    SettingsValuePickerCard(
        title = "Line color",
        description = "Color source used when no artwork is available.",
        value = oscLineColorModeNoArtwork.label,
        onClick = { showLineNoArtworkColorModeDialog = true }
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsValuePickerCard(
        title = "Grid color",
        description = "Color source used when no artwork is available.",
        value = oscGridColorModeNoArtwork.label,
        onClick = { showGridNoArtworkColorModeDialog = true }
    )
    Spacer(modifier = Modifier.height(16.dp))
    SettingsSectionLabel("Colors (with artwork)")
    SettingsValuePickerCard(
        title = "Line color",
        description = "Color source used when artwork is available.",
        value = oscLineColorModeWithArtwork.label,
        onClick = { showLineArtworkColorModeDialog = true }
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsValuePickerCard(
        title = "Grid color",
        description = "Color source used when artwork is available.",
        value = oscGridColorModeWithArtwork.label,
        onClick = { showGridArtworkColorModeDialog = true }
    )
    Spacer(modifier = Modifier.height(16.dp))
    SettingsSectionLabel("Custom colors")
    SettingsValuePickerCard(
        title = "Custom line color",
        description = "RGB color used when line color mode is Custom.",
        value = String.format(Locale.US, "#%06X", oscCustomLineColorArgb and 0xFFFFFF),
        onClick = { showCustomLineColorDialog = true }
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsValuePickerCard(
        title = "Custom grid color",
        description = "RGB color used when grid color mode is Custom.",
        value = String.format(Locale.US, "#%06X", oscCustomGridColorArgb and 0xFFFFFF),
        onClick = { showCustomGridColorDialog = true }
    )

    if (showWindowDialog) {
        SteppedIntSliderDialog(
            title = "Visible window",
            unitLabel = "ms",
            range = AppDefaults.Visualization.Oscilloscope.windowRangeMs,
            step = 1,
            currentValue = visualizationOscWindowMs,
            onDismiss = { showWindowDialog = false },
            onConfirm = { value ->
                val clamped = value.coerceIn(5, 200)
                visualizationOscWindowMs = clamped
                prefs.edit()
                    .putInt(oscWindowKey, clamped)
                    .apply()
                showWindowDialog = false
            }
        )
    }
    if (showTriggerDialog) {
        SettingsSingleChoiceDialog(
            title = "Oscilloscope trigger",
            selectedValue = visualizationOscTriggerMode,
            options = VisualizationOscTriggerMode.entries.map { mode ->
                ChoiceDialogOption(value = mode, label = mode.label)
            },
            onSelected = { mode ->
                visualizationOscTriggerMode = mode
                prefs.edit()
                    .putString(oscTriggerKey, mode.storageValue)
                    .apply()
            },
            onDismiss = { showTriggerDialog = false }
        )
    }
    if (showFpsModeDialog) {
        SettingsSingleChoiceDialog(
            title = "Scope frame rate",
            selectedValue = visualizationOscFpsMode,
            options = VisualizationOscFpsMode.entries.map { mode ->
                ChoiceDialogOption(value = mode, label = mode.label)
            },
            onSelected = { mode ->
                visualizationOscFpsMode = mode
                prefs.edit()
                    .putString(oscFpsModeKey, mode.storageValue)
                    .apply()
            },
            onDismiss = { showFpsModeDialog = false }
        )
    }
    if (showRenderBackendDialog) {
        SettingsSingleChoiceDialog(
            title = "Oscilloscope renderer backend",
            selectedValue = visualizationOscRenderBackend,
            options = listOf(
                VisualizationRenderBackend.Compose,
                VisualizationRenderBackend.OpenGlTexture
            ).map { backend ->
                ChoiceDialogOption(value = backend, label = backend.label)
            },
            onSelected = { backend ->
                visualizationOscRenderBackend = backend
                prefs.edit()
                    .putString(oscRenderBackendKey, backend.storageValue)
                    .apply()
            },
            onDismiss = { showRenderBackendDialog = false }
        )
    }
    if (showLineWidthDialog) {
        SteppedIntSliderDialog(
            title = "Scope line width",
            unitLabel = "dp",
            range = AppDefaults.Visualization.Oscilloscope.lineWidthRangeDp,
            step = 1,
            currentValue = visualizationOscLineWidthDp,
            onDismiss = { showLineWidthDialog = false },
            onConfirm = { value ->
                val clamped = value.coerceIn(1, 12)
                visualizationOscLineWidthDp = clamped
                prefs.edit().putInt(oscLineWidthKey, clamped).apply()
                showLineWidthDialog = false
            }
        )
    }
    if (showGridWidthDialog) {
        SteppedIntSliderDialog(
            title = "Grid line width",
            unitLabel = "dp",
            range = AppDefaults.Visualization.Oscilloscope.gridWidthRangeDp,
            step = 1,
            currentValue = visualizationOscGridWidthDp,
            onDismiss = { showGridWidthDialog = false },
            onConfirm = { value ->
                val clamped = value.coerceIn(1, 8)
                visualizationOscGridWidthDp = clamped
                prefs.edit().putInt(oscGridWidthKey, clamped).apply()
                showGridWidthDialog = false
            }
        )
    }
    if (showLineNoArtworkColorModeDialog) {
        VisualizationOscColorModeDialog(
            title = "Line color (no artwork)",
            options = listOf(
                VisualizationOscColorMode.Monet,
                VisualizationOscColorMode.White,
                VisualizationOscColorMode.Custom
            ),
            selectedMode = oscLineColorModeNoArtwork,
            onDismiss = { showLineNoArtworkColorModeDialog = false },
            onSelect = { mode ->
                oscLineColorModeNoArtwork = mode
                prefs.edit()
                    .putString(oscLineNoArtworkColorModeKey, mode.storageValue)
                    .apply()
                showLineNoArtworkColorModeDialog = false
            }
        )
    }
    if (showGridNoArtworkColorModeDialog) {
        VisualizationOscColorModeDialog(
            title = "Grid color (no artwork)",
            options = listOf(
                VisualizationOscColorMode.Monet,
                VisualizationOscColorMode.White,
                VisualizationOscColorMode.Custom
            ),
            selectedMode = oscGridColorModeNoArtwork,
            onDismiss = { showGridNoArtworkColorModeDialog = false },
            onSelect = { mode ->
                oscGridColorModeNoArtwork = mode
                prefs.edit()
                    .putString(oscGridNoArtworkColorModeKey, mode.storageValue)
                    .apply()
                showGridNoArtworkColorModeDialog = false
            }
        )
    }
    if (showLineArtworkColorModeDialog) {
        VisualizationOscColorModeDialog(
            title = "Line color (with artwork)",
            options = listOf(
                VisualizationOscColorMode.Artwork,
                VisualizationOscColorMode.Monet,
                VisualizationOscColorMode.White,
                VisualizationOscColorMode.Custom
            ),
            selectedMode = oscLineColorModeWithArtwork,
            onDismiss = { showLineArtworkColorModeDialog = false },
            onSelect = { mode ->
                oscLineColorModeWithArtwork = mode
                prefs.edit()
                    .putString(oscLineArtworkColorModeKey, mode.storageValue)
                    .apply()
                showLineArtworkColorModeDialog = false
            }
        )
    }
    if (showGridArtworkColorModeDialog) {
        VisualizationOscColorModeDialog(
            title = "Grid color (with artwork)",
            options = listOf(
                VisualizationOscColorMode.Artwork,
                VisualizationOscColorMode.Monet,
                VisualizationOscColorMode.White,
                VisualizationOscColorMode.Custom
            ),
            selectedMode = oscGridColorModeWithArtwork,
            onDismiss = { showGridArtworkColorModeDialog = false },
            onSelect = { mode ->
                oscGridColorModeWithArtwork = mode
                prefs.edit()
                    .putString(oscGridArtworkColorModeKey, mode.storageValue)
                    .apply()
                showGridArtworkColorModeDialog = false
            }
        )
    }
    if (showCustomLineColorDialog) {
        VisualizationRgbColorPickerDialog(
            title = "Custom line color",
            initialArgb = oscCustomLineColorArgb,
            onDismiss = { showCustomLineColorDialog = false },
            onConfirm = { argb ->
                oscCustomLineColorArgb = argb
                prefs.edit().putInt(oscCustomLineColorKey, argb).apply()
                showCustomLineColorDialog = false
            }
        )
    }
    if (showCustomGridColorDialog) {
        VisualizationRgbColorPickerDialog(
            title = "Custom grid color",
            initialArgb = oscCustomGridColorArgb,
            onDismiss = { showCustomGridColorDialog = false },
            onConfirm = { argb ->
                oscCustomGridColorArgb = argb
                prefs.edit().putInt(oscCustomGridColorKey, argb).apply()
                showCustomGridColorDialog = false
            }
        )
    }
}
