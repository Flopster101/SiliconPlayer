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

internal data class VisualizationBasicBarsRouteState(
    val visualizationBarCount: Int,
    val visualizationBarSmoothingPercent: Int,
    val visualizationBarRoundnessDp: Int,
    val visualizationBarOverlayArtwork: Boolean,
    val visualizationBarUseThemeColor: Boolean,
    val visualizationBarRenderBackend: VisualizationRenderBackend
)

internal data class VisualizationBasicBarsRouteActions(
    val onVisualizationBarCountChanged: (Int) -> Unit,
    val onVisualizationBarSmoothingPercentChanged: (Int) -> Unit,
    val onVisualizationBarRoundnessDpChanged: (Int) -> Unit,
    val onVisualizationBarOverlayArtworkChanged: (Boolean) -> Unit,
    val onVisualizationBarUseThemeColorChanged: (Boolean) -> Unit,
    val onVisualizationBarRenderBackendChanged: (VisualizationRenderBackend) -> Unit
)

internal data class VisualizationBasicVuMetersRouteState(
    val visualizationVuAnchor: VisualizationVuAnchor,
    val visualizationVuUseThemeColor: Boolean,
    val visualizationVuSmoothingPercent: Int,
    val visualizationVuRenderBackend: VisualizationRenderBackend
)

internal data class VisualizationBasicVuMetersRouteActions(
    val onVisualizationVuAnchorChanged: (VisualizationVuAnchor) -> Unit,
    val onVisualizationVuUseThemeColorChanged: (Boolean) -> Unit,
    val onVisualizationVuSmoothingPercentChanged: (Int) -> Unit,
    val onVisualizationVuRenderBackendChanged: (VisualizationRenderBackend) -> Unit
)

@Composable
internal fun VisualizationBasicBarsRouteContent(
    state: VisualizationBasicBarsRouteState,
    actions: VisualizationBasicBarsRouteActions
) {
    val visualizationBarCount = state.visualizationBarCount
    val onVisualizationBarCountChanged = actions.onVisualizationBarCountChanged
    val visualizationBarSmoothingPercent = state.visualizationBarSmoothingPercent
    val onVisualizationBarSmoothingPercentChanged = actions.onVisualizationBarSmoothingPercentChanged
    val visualizationBarRoundnessDp = state.visualizationBarRoundnessDp
    val onVisualizationBarRoundnessDpChanged = actions.onVisualizationBarRoundnessDpChanged
    val visualizationBarOverlayArtwork = state.visualizationBarOverlayArtwork
    val onVisualizationBarOverlayArtworkChanged = actions.onVisualizationBarOverlayArtworkChanged
    val visualizationBarUseThemeColor = state.visualizationBarUseThemeColor
    val onVisualizationBarUseThemeColorChanged = actions.onVisualizationBarUseThemeColorChanged
    val visualizationBarRenderBackend = state.visualizationBarRenderBackend
    val onVisualizationBarRenderBackendChanged = actions.onVisualizationBarRenderBackendChanged

    val prefsName = "silicon_player_settings"
    val barColorModeNoArtworkKey = "visualization_bar_color_mode_no_artwork"
    val barColorModeWithArtworkKey = "visualization_bar_color_mode_with_artwork"
    val barCustomColorKey = "visualization_bar_custom_color_argb"
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    }
    var barColorModeNoArtwork by remember {
        mutableStateOf(
            VisualizationOscColorMode.fromStorage(
                prefs.getString(
                    barColorModeNoArtworkKey,
                    AppDefaults.Visualization.Bars.colorModeNoArtwork.storageValue
                ),
                AppDefaults.Visualization.Bars.colorModeNoArtwork
            )
        )
    }
    var barColorModeWithArtwork by remember {
        mutableStateOf(
            VisualizationOscColorMode.fromStorage(
                prefs.getString(
                    barColorModeWithArtworkKey,
                    AppDefaults.Visualization.Bars.colorModeWithArtwork.storageValue
                ),
                AppDefaults.Visualization.Bars.colorModeWithArtwork
            )
        )
    }
    var barCustomColorArgb by remember {
        mutableIntStateOf(
            prefs.getInt(barCustomColorKey, AppDefaults.Visualization.Bars.customColorArgb)
        )
    }
    var showBarCountDialog by remember { mutableStateOf(false) }
    var showBarSmoothingDialog by remember { mutableStateOf(false) }
    var showBarRoundnessDialog by remember { mutableStateOf(false) }
    var showBarRenderBackendDialog by remember { mutableStateOf(false) }
    var showBarColorModeNoArtworkDialog by remember { mutableStateOf(false) }
    var showBarColorModeWithArtworkDialog by remember { mutableStateOf(false) }
    var showBarCustomColorDialog by remember { mutableStateOf(false) }

    SettingsSectionLabel("Bars")
    SettingsValuePickerCard(
        title = "Renderer backend",
        description = "Choose Compose or OpenGL ES (TextureView) renderer.",
        value = visualizationBarRenderBackend.label,
        onClick = { showBarRenderBackendDialog = true }
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsValuePickerCard(
        title = "Bar count",
        description = "Number of frequency bars shown in the spectrum.",
        value = "$visualizationBarCount",
        onClick = { showBarCountDialog = true }
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsValuePickerCard(
        title = "Smoothing",
        description = "How quickly bars react to level changes.",
        value = "$visualizationBarSmoothingPercent%",
        onClick = { showBarSmoothingDialog = true }
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsValuePickerCard(
        title = "Roundness",
        description = "Corner radius used for each bar.",
        value = "${visualizationBarRoundnessDp}dp",
        onClick = { showBarRoundnessDialog = true }
    )
    Spacer(modifier = Modifier.height(10.dp))
    PlayerSettingToggleCard(
        title = "Overlay on artwork",
        description = "When disabled, bars are rendered over a blank background.",
        checked = visualizationBarOverlayArtwork,
        onCheckedChange = onVisualizationBarOverlayArtworkChanged
    )
    Spacer(modifier = Modifier.height(10.dp))
    PlayerSettingToggleCard(
        title = "Use theme color",
        description = "Use app theme color for bars instead of alternate accent.",
        checked = visualizationBarUseThemeColor,
        onCheckedChange = onVisualizationBarUseThemeColorChanged
    )
    Spacer(modifier = Modifier.height(16.dp))
    SettingsSectionLabel("Colors (no artwork)")
    SettingsValuePickerCard(
        title = "Bar color",
        description = "Color source used when no artwork is available.",
        value = barColorModeNoArtwork.label,
        onClick = { showBarColorModeNoArtworkDialog = true }
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsSectionLabel("Colors (with artwork)")
    SettingsValuePickerCard(
        title = "Bar color",
        description = "Color source used when artwork is available.",
        value = barColorModeWithArtwork.label,
        onClick = { showBarColorModeWithArtworkDialog = true }
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsSectionLabel("Custom color")
    SettingsValuePickerCard(
        title = "Custom bar color",
        description = "RGB color used when color mode is set to Custom.",
        value = String.format(Locale.US, "#%06X", barCustomColorArgb and 0xFFFFFF),
        onClick = { showBarCustomColorDialog = true }
    )

    if (showBarCountDialog) {
        SteppedIntSliderDialog(
            title = "Bar count",
            unitLabel = "bars",
            range = AppDefaults.Visualization.Bars.countRange,
            step = 1,
            currentValue = visualizationBarCount,
            onDismiss = { showBarCountDialog = false },
            onConfirm = { value ->
                onVisualizationBarCountChanged(value)
                showBarCountDialog = false
            }
        )
    }
    if (showBarSmoothingDialog) {
        SteppedIntSliderDialog(
            title = "Bar smoothing",
            unitLabel = "%",
            range = AppDefaults.Visualization.Bars.smoothingRange,
            step = 1,
            currentValue = visualizationBarSmoothingPercent,
            onDismiss = { showBarSmoothingDialog = false },
            onConfirm = { value ->
                onVisualizationBarSmoothingPercentChanged(value)
                showBarSmoothingDialog = false
            }
        )
    }
    if (showBarRoundnessDialog) {
        SteppedIntSliderDialog(
            title = "Bar roundness",
            unitLabel = "dp",
            range = AppDefaults.Visualization.Bars.roundnessRange,
            step = 1,
            currentValue = visualizationBarRoundnessDp,
            onDismiss = { showBarRoundnessDialog = false },
            onConfirm = { value ->
                onVisualizationBarRoundnessDpChanged(value)
                showBarRoundnessDialog = false
            }
        )
    }
    if (showBarRenderBackendDialog) {
        SettingsSingleChoiceDialog(
            title = "Bars renderer backend",
            selectedValue = visualizationBarRenderBackend,
            options = listOf(
                ChoiceDialogOption(
                    value = VisualizationRenderBackend.Compose,
                    label = VisualizationRenderBackend.Compose.label
                ),
                ChoiceDialogOption(
                    value = VisualizationRenderBackend.OpenGlTexture,
                    label = VisualizationRenderBackend.OpenGlTexture.label
                )
            ),
            onSelected = onVisualizationBarRenderBackendChanged,
            onDismiss = { showBarRenderBackendDialog = false }
        )
    }
    if (showBarColorModeNoArtworkDialog) {
        VisualizationOscColorModeDialog(
            title = "Bar color (no artwork)",
            options = listOf(
                VisualizationOscColorMode.Monet,
                VisualizationOscColorMode.White,
                VisualizationOscColorMode.Custom
            ),
            selectedMode = barColorModeNoArtwork,
            onDismiss = { showBarColorModeNoArtworkDialog = false },
            onSelect = { mode ->
                barColorModeNoArtwork = mode
                prefs.edit().putString(barColorModeNoArtworkKey, mode.storageValue).apply()
                showBarColorModeNoArtworkDialog = false
            }
        )
    }
    if (showBarColorModeWithArtworkDialog) {
        VisualizationOscColorModeDialog(
            title = "Bar color (with artwork)",
            options = listOf(
                VisualizationOscColorMode.Artwork,
                VisualizationOscColorMode.Monet,
                VisualizationOscColorMode.White,
                VisualizationOscColorMode.Custom
            ),
            selectedMode = barColorModeWithArtwork,
            onDismiss = { showBarColorModeWithArtworkDialog = false },
            onSelect = { mode ->
                barColorModeWithArtwork = mode
                prefs.edit().putString(barColorModeWithArtworkKey, mode.storageValue).apply()
                showBarColorModeWithArtworkDialog = false
            }
        )
    }
    if (showBarCustomColorDialog) {
        VisualizationRgbColorPickerDialog(
            title = "Custom bar color",
            initialArgb = barCustomColorArgb,
            onDismiss = { showBarCustomColorDialog = false },
            onConfirm = { argb ->
                barCustomColorArgb = argb
                prefs.edit().putInt(barCustomColorKey, argb).apply()
                showBarCustomColorDialog = false
            }
        )
    }
}

@Composable
internal fun VisualizationBasicVuMetersRouteContent(
    state: VisualizationBasicVuMetersRouteState,
    actions: VisualizationBasicVuMetersRouteActions
) {
    val visualizationVuAnchor = state.visualizationVuAnchor
    val onVisualizationVuAnchorChanged = actions.onVisualizationVuAnchorChanged
    val visualizationVuUseThemeColor = state.visualizationVuUseThemeColor
    val onVisualizationVuUseThemeColorChanged = actions.onVisualizationVuUseThemeColorChanged
    val visualizationVuSmoothingPercent = state.visualizationVuSmoothingPercent
    val onVisualizationVuSmoothingPercentChanged = actions.onVisualizationVuSmoothingPercentChanged
    val visualizationVuRenderBackend = state.visualizationVuRenderBackend
    val onVisualizationVuRenderBackendChanged = actions.onVisualizationVuRenderBackendChanged

    val prefsName = "silicon_player_settings"
    val vuColorModeNoArtworkKey = "visualization_vu_color_mode_no_artwork"
    val vuColorModeWithArtworkKey = "visualization_vu_color_mode_with_artwork"
    val vuCustomColorKey = "visualization_vu_custom_color_argb"
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    }
    var vuColorModeNoArtwork by remember {
        mutableStateOf(
            VisualizationOscColorMode.fromStorage(
                prefs.getString(
                    vuColorModeNoArtworkKey,
                    AppDefaults.Visualization.Vu.colorModeNoArtwork.storageValue
                ),
                AppDefaults.Visualization.Vu.colorModeNoArtwork
            )
        )
    }
    var vuColorModeWithArtwork by remember {
        mutableStateOf(
            VisualizationOscColorMode.fromStorage(
                prefs.getString(
                    vuColorModeWithArtworkKey,
                    AppDefaults.Visualization.Vu.colorModeWithArtwork.storageValue
                ),
                AppDefaults.Visualization.Vu.colorModeWithArtwork
            )
        )
    }
    var vuCustomColorArgb by remember {
        mutableIntStateOf(
            prefs.getInt(vuCustomColorKey, AppDefaults.Visualization.Vu.customColorArgb)
        )
    }
    var showVuAnchorDialog by remember { mutableStateOf(false) }
    var showVuRenderBackendDialog by remember { mutableStateOf(false) }
    var showVuSmoothingDialog by remember { mutableStateOf(false) }
    var showVuColorModeNoArtworkDialog by remember { mutableStateOf(false) }
    var showVuColorModeWithArtworkDialog by remember { mutableStateOf(false) }
    var showVuCustomColorDialog by remember { mutableStateOf(false) }

    SettingsSectionLabel("VU meters")
    SettingsValuePickerCard(
        title = "Renderer backend",
        description = "Choose Compose or OpenGL ES (TextureView) renderer.",
        value = visualizationVuRenderBackend.label,
        onClick = { showVuRenderBackendDialog = true }
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsValuePickerCard(
        title = "Anchor position",
        description = "Where VU meter rows are aligned in the artwork area.",
        value = visualizationVuAnchor.label,
        onClick = { showVuAnchorDialog = true }
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsValuePickerCard(
        title = "Smoothing",
        description = "How quickly VU levels react to level changes.",
        value = "$visualizationVuSmoothingPercent%",
        onClick = { showVuSmoothingDialog = true }
    )
    Spacer(modifier = Modifier.height(10.dp))
    PlayerSettingToggleCard(
        title = "Use theme color",
        description = "Use app theme color for VU bars instead of alternate accent.",
        checked = visualizationVuUseThemeColor,
        onCheckedChange = onVisualizationVuUseThemeColorChanged
    )
    Spacer(modifier = Modifier.height(16.dp))
    SettingsSectionLabel("Colors (no artwork)")
    SettingsValuePickerCard(
        title = "VU color",
        description = "Color source used when no artwork is available.",
        value = vuColorModeNoArtwork.label,
        onClick = { showVuColorModeNoArtworkDialog = true }
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsSectionLabel("Colors (with artwork)")
    SettingsValuePickerCard(
        title = "VU color",
        description = "Color source used when artwork is available.",
        value = vuColorModeWithArtwork.label,
        onClick = { showVuColorModeWithArtworkDialog = true }
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsSectionLabel("Custom color")
    SettingsValuePickerCard(
        title = "Custom VU color",
        description = "RGB color used when VU color mode is Custom.",
        value = String.format(Locale.US, "#%06X", vuCustomColorArgb and 0xFFFFFF),
        onClick = { showVuCustomColorDialog = true }
    )

    if (showVuRenderBackendDialog) {
        SettingsSingleChoiceDialog(
            title = "VU renderer backend",
            selectedValue = visualizationVuRenderBackend,
            options = listOf(
                ChoiceDialogOption(
                    value = VisualizationRenderBackend.Compose,
                    label = VisualizationRenderBackend.Compose.label
                ),
                ChoiceDialogOption(
                    value = VisualizationRenderBackend.OpenGlTexture,
                    label = VisualizationRenderBackend.OpenGlTexture.label
                )
            ),
            onSelected = onVisualizationVuRenderBackendChanged,
            onDismiss = { showVuRenderBackendDialog = false }
        )
    }

    if (showVuSmoothingDialog) {
        SteppedIntSliderDialog(
            title = "VU smoothing",
            unitLabel = "%",
            range = AppDefaults.Visualization.Vu.smoothingRange,
            step = 1,
            currentValue = visualizationVuSmoothingPercent,
            onDismiss = { showVuSmoothingDialog = false },
            onConfirm = { value ->
                onVisualizationVuSmoothingPercentChanged(value)
                showVuSmoothingDialog = false
            }
        )
    }
    if (showVuAnchorDialog) {
        SettingsSingleChoiceDialog(
            title = "VU anchor position",
            selectedValue = visualizationVuAnchor,
            options = VisualizationVuAnchor.entries.map { anchor ->
                ChoiceDialogOption(value = anchor, label = anchor.label)
            },
            onSelected = onVisualizationVuAnchorChanged,
            onDismiss = { showVuAnchorDialog = false }
        )
    }
    if (showVuColorModeNoArtworkDialog) {
        VisualizationOscColorModeDialog(
            title = "VU color (no artwork)",
            options = listOf(
                VisualizationOscColorMode.Monet,
                VisualizationOscColorMode.White,
                VisualizationOscColorMode.Custom
            ),
            selectedMode = vuColorModeNoArtwork,
            onDismiss = { showVuColorModeNoArtworkDialog = false },
            onSelect = { mode ->
                vuColorModeNoArtwork = mode
                prefs.edit().putString(vuColorModeNoArtworkKey, mode.storageValue).apply()
                showVuColorModeNoArtworkDialog = false
            }
        )
    }
    if (showVuColorModeWithArtworkDialog) {
        VisualizationOscColorModeDialog(
            title = "VU color (with artwork)",
            options = listOf(
                VisualizationOscColorMode.Artwork,
                VisualizationOscColorMode.Monet,
                VisualizationOscColorMode.White,
                VisualizationOscColorMode.Custom
            ),
            selectedMode = vuColorModeWithArtwork,
            onDismiss = { showVuColorModeWithArtworkDialog = false },
            onSelect = { mode ->
                vuColorModeWithArtwork = mode
                prefs.edit().putString(vuColorModeWithArtworkKey, mode.storageValue).apply()
                showVuColorModeWithArtworkDialog = false
            }
        )
    }
    if (showVuCustomColorDialog) {
        VisualizationRgbColorPickerDialog(
            title = "Custom VU color",
            initialArgb = vuCustomColorArgb,
            onDismiss = { showVuCustomColorDialog = false },
            onConfirm = { argb ->
                vuCustomColorArgb = argb
                prefs.edit().putInt(vuCustomColorKey, argb).apply()
                showVuCustomColorDialog = false
            }
        )
    }
}
