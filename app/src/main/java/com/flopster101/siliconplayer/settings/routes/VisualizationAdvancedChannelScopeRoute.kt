package com.flopster101.siliconplayer

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun VisualizationAdvancedChannelScopeRouteContent() {
                        val prefsName = "silicon_player_settings"
                        val scopeWindowKey = "visualization_channel_scope_window_ms"
                        val scopeRenderBackendKey = "visualization_channel_scope_render_backend"
                        val scopeDcRemovalEnabledKey = "visualization_channel_scope_dc_removal_enabled"
                        val scopeGainPercentKey = "visualization_channel_scope_gain_percent"
                        val scopeContrastBackdropEnabledKey =
                            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_CONTRAST_BACKDROP_ENABLED
                        val scopeTriggerKey = "visualization_channel_scope_trigger_mode"
                        val scopeFpsModeKey = "visualization_channel_scope_fps_mode"
                        val scopeLineWidthKey = "visualization_channel_scope_line_width_dp"
                        val scopeGridWidthKey = "visualization_channel_scope_grid_width_dp"
                        val scopeVerticalGridEnabledKey = "visualization_channel_scope_vertical_grid_enabled"
                        val scopeCenterLineEnabledKey = "visualization_channel_scope_center_line_enabled"
                        val scopeShowArtworkBackgroundKey = "visualization_channel_scope_show_artwork_background"
                        val scopeBackgroundModeKey = "visualization_channel_scope_background_mode"
                        val scopeCustomBackgroundColorKey = "visualization_channel_scope_custom_background_color_argb"
                        val scopeLayoutKey = "visualization_channel_scope_layout"
                        val scopeLineNoArtworkColorModeKey = "visualization_channel_scope_line_color_mode_no_artwork"
                        val scopeGridNoArtworkColorModeKey = "visualization_channel_scope_grid_color_mode_no_artwork"
                        val scopeLineArtworkColorModeKey = "visualization_channel_scope_line_color_mode_with_artwork"
                        val scopeGridArtworkColorModeKey = "visualization_channel_scope_grid_color_mode_with_artwork"
                        val scopeCustomLineColorKey = "visualization_channel_scope_custom_line_color_argb"
                        val scopeCustomGridColorKey = "visualization_channel_scope_custom_grid_color_argb"
                        val scopeTextEnabledKey = "visualization_channel_scope_text_enabled"
                        val scopeTextAnchorKey = "visualization_channel_scope_text_anchor"
                        val scopeTextPaddingKey = "visualization_channel_scope_text_padding_dp"
                        val scopeTextSizeKey = "visualization_channel_scope_text_size_sp"
                        val scopeTextHideWhenOverflowKey = "visualization_channel_scope_text_hide_when_overflow"
                        val scopeTextShadowEnabledKey = "visualization_channel_scope_text_shadow_enabled"
                        val scopeTextFontKey = "visualization_channel_scope_text_font"
                        val scopeTextColorModeKey = "visualization_channel_scope_text_color_mode"
                        val scopeCustomTextColorKey = "visualization_channel_scope_custom_text_color_argb"
                        val scopeTextNoteFormatKey = "visualization_channel_scope_text_note_format"
                        val scopeTextShowChannelKey = "visualization_channel_scope_text_show_channel"
                        val scopeTextShowNoteKey = "visualization_channel_scope_text_show_note"
                        val scopeTextShowVolumeKey = "visualization_channel_scope_text_show_volume"
                        val scopeTextShowEffectKey = "visualization_channel_scope_text_show_effect"
                        val scopeTextShowInstrumentSampleKey = "visualization_channel_scope_text_show_instrument_sample"
                        val scopeTextVuEnabledKey = "visualization_channel_scope_text_vu_enabled"
                        val scopeTextVuAnchorKey = "visualization_channel_scope_text_vu_anchor"
                        val scopeTextVuColorModeKey = "visualization_channel_scope_text_vu_color_mode"
                        val scopeTextVuCustomColorKey = "visualization_channel_scope_text_vu_custom_color_argb"
                        val context = LocalContext.current
                        val prefs = remember(context) {
                            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                        }
                        var scopeWindowMs by remember {
                            mutableIntStateOf(
                                prefs.getInt(
                                    scopeWindowKey,
                                    AppDefaults.Visualization.ChannelScope.windowMs
                                ).coerceIn(
                                    AppDefaults.Visualization.ChannelScope.windowRangeMs.first,
                                    AppDefaults.Visualization.ChannelScope.windowRangeMs.last
                                )
                            )
                        }
                        var scopeTriggerMode by remember {
                            mutableStateOf(
                                VisualizationOscTriggerMode.fromStorage(
                                    prefs.getString(
                                        scopeTriggerKey,
                                        AppDefaults.Visualization.ChannelScope.triggerMode.storageValue
                                    )
                                )
                            )
                        }
                        var scopeRenderBackend by remember {
                            mutableStateOf(
                                VisualizationRenderBackend.fromStorage(
                                    prefs.getString(
                                        scopeRenderBackendKey,
                                        AppDefaults.Visualization.ChannelScope.renderBackend.storageValue
                                    ),
                                    AppDefaults.Visualization.ChannelScope.renderBackend
                                )
                            )
                        }
                        var scopeDcRemovalEnabled by remember {
                            mutableStateOf(
                                prefs.getBoolean(
                                    scopeDcRemovalEnabledKey,
                                    AppDefaults.Visualization.ChannelScope.dcRemovalEnabled
                                )
                            )
                        }
                        var scopeGainPercent by remember {
                            mutableIntStateOf(
                                prefs.getInt(
                                    scopeGainPercentKey,
                                    AppDefaults.Visualization.ChannelScope.gainPercent
                                ).coerceIn(
                                    AppDefaults.Visualization.ChannelScope.gainRangePercent.first,
                                    AppDefaults.Visualization.ChannelScope.gainRangePercent.last
                                )
                            )
                        }
                        var scopeContrastBackdropEnabled by remember {
                            mutableStateOf(
                                prefs.getBoolean(
                                    scopeContrastBackdropEnabledKey,
                                    AppDefaults.Visualization.ChannelScope.contrastBackdropEnabled
                                )
                            )
                        }
                        var scopeFpsMode by remember {
                            mutableStateOf(
                                VisualizationOscFpsMode.fromStorage(
                                    prefs.getString(
                                        scopeFpsModeKey,
                                        AppDefaults.Visualization.ChannelScope.fpsMode.storageValue
                                    )
                                )
                            )
                        }
                        var scopeLineWidthDp by remember {
                            mutableIntStateOf(
                                prefs.getInt(
                                    scopeLineWidthKey,
                                    AppDefaults.Visualization.ChannelScope.lineWidthDp
                                ).coerceIn(
                                    AppDefaults.Visualization.ChannelScope.lineWidthRangeDp.first,
                                    AppDefaults.Visualization.ChannelScope.lineWidthRangeDp.last
                                )
                            )
                        }
                        var scopeGridWidthDp by remember {
                            mutableIntStateOf(
                                prefs.getInt(
                                    scopeGridWidthKey,
                                    AppDefaults.Visualization.ChannelScope.gridWidthDp
                                ).coerceIn(
                                    AppDefaults.Visualization.ChannelScope.gridWidthRangeDp.first,
                                    AppDefaults.Visualization.ChannelScope.gridWidthRangeDp.last
                                )
                            )
                        }
                        var scopeVerticalGridEnabled by remember {
                            mutableStateOf(
                                prefs.getBoolean(
                                    scopeVerticalGridEnabledKey,
                                    AppDefaults.Visualization.ChannelScope.verticalGridEnabled
                                )
                            )
                        }
                        var scopeCenterLineEnabled by remember {
                            mutableStateOf(
                                prefs.getBoolean(
                                    scopeCenterLineEnabledKey,
                                    AppDefaults.Visualization.ChannelScope.centerLineEnabled
                                )
                            )
                        }
                        var scopeShowArtworkBackground by remember {
                            mutableStateOf(
                                prefs.getBoolean(
                                    scopeShowArtworkBackgroundKey,
                                    AppDefaults.Visualization.ChannelScope.showArtworkBackground
                                )
                            )
                        }
                        var scopeBackgroundMode by remember {
                            mutableStateOf(
                                VisualizationChannelScopeBackgroundMode.fromStorage(
                                    prefs.getString(
                                        scopeBackgroundModeKey,
                                        AppDefaults.Visualization.ChannelScope.backgroundMode.storageValue
                                    )
                                )
                            )
                        }
                        var scopeCustomBackgroundColorArgb by remember {
                            mutableIntStateOf(
                                prefs.getInt(
                                    scopeCustomBackgroundColorKey,
                                    AppDefaults.Visualization.ChannelScope.customBackgroundColorArgb
                                )
                            )
                        }
                        var scopeLayout by remember {
                            mutableStateOf(
                                VisualizationChannelScopeLayout.fromStorage(
                                    prefs.getString(
                                        scopeLayoutKey,
                                        AppDefaults.Visualization.ChannelScope.layout.storageValue
                                    )
                                )
                            )
                        }
                        var scopeLineColorModeNoArtwork by remember {
                            mutableStateOf(
                                VisualizationOscColorMode.fromStorage(
                                    prefs.getString(
                                        scopeLineNoArtworkColorModeKey,
                                        AppDefaults.Visualization.ChannelScope.lineColorModeNoArtwork.storageValue
                                    ),
                                    AppDefaults.Visualization.ChannelScope.lineColorModeNoArtwork
                                )
                            )
                        }
                        var scopeGridColorModeNoArtwork by remember {
                            mutableStateOf(
                                VisualizationOscColorMode.fromStorage(
                                    prefs.getString(
                                        scopeGridNoArtworkColorModeKey,
                                        AppDefaults.Visualization.ChannelScope.gridColorModeNoArtwork.storageValue
                                    ),
                                    AppDefaults.Visualization.ChannelScope.gridColorModeNoArtwork
                                )
                            )
                        }
                        var scopeLineColorModeWithArtwork by remember {
                            mutableStateOf(
                                VisualizationOscColorMode.fromStorage(
                                    prefs.getString(
                                        scopeLineArtworkColorModeKey,
                                        AppDefaults.Visualization.ChannelScope.lineColorModeWithArtwork.storageValue
                                    ),
                                    AppDefaults.Visualization.ChannelScope.lineColorModeWithArtwork
                                )
                            )
                        }
                        var scopeGridColorModeWithArtwork by remember {
                            mutableStateOf(
                                VisualizationOscColorMode.fromStorage(
                                    prefs.getString(
                                        scopeGridArtworkColorModeKey,
                                        AppDefaults.Visualization.ChannelScope.gridColorModeWithArtwork.storageValue
                                    ),
                                    AppDefaults.Visualization.ChannelScope.gridColorModeWithArtwork
                                )
                            )
                        }
                        var scopeCustomLineColorArgb by remember {
                            mutableIntStateOf(
                                prefs.getInt(
                                    scopeCustomLineColorKey,
                                    AppDefaults.Visualization.ChannelScope.customLineColorArgb
                                )
                            )
                        }
                        var scopeCustomGridColorArgb by remember {
                            mutableIntStateOf(
                                prefs.getInt(
                                    scopeCustomGridColorKey,
                                    AppDefaults.Visualization.ChannelScope.customGridColorArgb
                                )
                            )
                        }
                        var scopeTextEnabled by remember {
                            mutableStateOf(
                                prefs.getBoolean(
                                    scopeTextEnabledKey,
                                    AppDefaults.Visualization.ChannelScope.textEnabled
                                )
                            )
                        }
                        var scopeTextAnchor by remember {
                            mutableStateOf(
                                VisualizationChannelScopeTextAnchor.fromStorage(
                                    prefs.getString(
                                        scopeTextAnchorKey,
                                        AppDefaults.Visualization.ChannelScope.textAnchor.storageValue
                                    )
                                )
                            )
                        }
                        var scopeTextPaddingDp by remember {
                            mutableIntStateOf(
                                prefs.getInt(
                                    scopeTextPaddingKey,
                                    AppDefaults.Visualization.ChannelScope.textPaddingDp
                                ).coerceIn(
                                    AppDefaults.Visualization.ChannelScope.textPaddingRangeDp.first,
                                    AppDefaults.Visualization.ChannelScope.textPaddingRangeDp.last
                                )
                            )
                        }
                        var scopeTextSizeSp by remember {
                            mutableIntStateOf(
                                prefs.getInt(
                                    scopeTextSizeKey,
                                    AppDefaults.Visualization.ChannelScope.textSizeSp
                                ).coerceIn(
                                    AppDefaults.Visualization.ChannelScope.textSizeRangeSp.first,
                                    AppDefaults.Visualization.ChannelScope.textSizeRangeSp.last
                                )
                            )
                        }
                        var scopeTextHideWhenOverflow by remember {
                            mutableStateOf(
                                prefs.getBoolean(
                                    scopeTextHideWhenOverflowKey,
                                    AppDefaults.Visualization.ChannelScope.textHideWhenOverflow
                                )
                            )
                        }
                        var scopeTextShadowEnabled by remember {
                            mutableStateOf(
                                prefs.getBoolean(
                                    scopeTextShadowEnabledKey,
                                    AppDefaults.Visualization.ChannelScope.textShadowEnabled
                                )
                            )
                        }
                        var scopeTextFont by remember {
                            mutableStateOf(
                                VisualizationChannelScopeTextFont.fromStorage(
                                    prefs.getString(
                                        scopeTextFontKey,
                                        AppDefaults.Visualization.ChannelScope.textFont.storageValue
                                    )
                                )
                            )
                        }
                        var scopeTextColorMode by remember {
                            mutableStateOf(
                                VisualizationChannelScopeTextColorMode.fromStorage(
                                    prefs.getString(
                                        scopeTextColorModeKey,
                                        AppDefaults.Visualization.ChannelScope.textColorMode.storageValue
                                    )
                                )
                            )
                        }
                        var scopeCustomTextColorArgb by remember {
                            mutableIntStateOf(
                                prefs.getInt(
                                    scopeCustomTextColorKey,
                                    AppDefaults.Visualization.ChannelScope.customTextColorArgb
                                )
                            )
                        }
                        var scopeTextNoteFormat by remember {
                            mutableStateOf(
                                VisualizationNoteNameFormat.fromStorage(
                                    prefs.getString(
                                        scopeTextNoteFormatKey,
                                        AppDefaults.Visualization.ChannelScope.textNoteFormat.storageValue
                                    )
                                )
                            )
                        }
                        var scopeTextShowChannel by remember {
                            mutableStateOf(
                                prefs.getBoolean(
                                    scopeTextShowChannelKey,
                                    AppDefaults.Visualization.ChannelScope.textShowChannel
                                )
                            )
                        }
                        var scopeTextShowNote by remember {
                            mutableStateOf(
                                prefs.getBoolean(
                                    scopeTextShowNoteKey,
                                    AppDefaults.Visualization.ChannelScope.textShowNote
                                )
                            )
                        }
                        var scopeTextShowVolume by remember {
                            mutableStateOf(
                                prefs.getBoolean(
                                    scopeTextShowVolumeKey,
                                    AppDefaults.Visualization.ChannelScope.textShowVolume
                                )
                            )
                        }
                        var scopeTextShowEffect by remember {
                            mutableStateOf(
                                prefs.getBoolean(
                                    scopeTextShowEffectKey,
                                    AppDefaults.Visualization.ChannelScope.textShowEffect
                                )
                            )
                        }
                        var scopeTextShowInstrumentSample by remember {
                            mutableStateOf(
                                prefs.getBoolean(
                                    scopeTextShowInstrumentSampleKey,
                                    AppDefaults.Visualization.ChannelScope.textShowInstrumentSample
                                )
                            )
                        }
                        var scopeTextVuEnabled by remember {
                            mutableStateOf(
                                prefs.getBoolean(
                                    scopeTextVuEnabledKey,
                                    AppDefaults.Visualization.ChannelScope.textVuEnabled
                                )
                            )
                        }
                        var scopeTextVuAnchor by remember {
                            mutableStateOf(
                                VisualizationVuAnchor.fromStorage(
                                    prefs.getString(
                                        scopeTextVuAnchorKey,
                                        AppDefaults.Visualization.ChannelScope.textVuAnchor.storageValue
                                    )
                                )
                            )
                        }
                        var scopeTextVuColorMode by remember {
                            mutableStateOf(
                                VisualizationChannelScopeTextColorMode.fromStorage(
                                    prefs.getString(
                                        scopeTextVuColorModeKey,
                                        AppDefaults.Visualization.ChannelScope.textVuColorMode.storageValue
                                    )
                                )
                            )
                        }
                        var scopeTextVuCustomColorArgb by remember {
                            mutableIntStateOf(
                                prefs.getInt(
                                    scopeTextVuCustomColorKey,
                                    AppDefaults.Visualization.ChannelScope.textVuCustomColorArgb
                                )
                            )
                        }

                        var showWindowDialog by remember { mutableStateOf(false) }
                        var showRendererBackendDialog by remember { mutableStateOf(false) }
                        var showGainDialog by remember { mutableStateOf(false) }
                        var showTriggerDialog by remember { mutableStateOf(false) }
                        var showFpsModeDialog by remember { mutableStateOf(false) }
                        var showLineWidthDialog by remember { mutableStateOf(false) }
                        var showGridWidthDialog by remember { mutableStateOf(false) }
                        var showBackgroundModeDialog by remember { mutableStateOf(false) }
                        var showCustomBackgroundColorDialog by remember { mutableStateOf(false) }
                        var showLayoutDialog by remember { mutableStateOf(false) }
                        var showLineNoArtworkColorModeDialog by remember { mutableStateOf(false) }
                        var showGridNoArtworkColorModeDialog by remember { mutableStateOf(false) }
                        var showLineArtworkColorModeDialog by remember { mutableStateOf(false) }
                        var showGridArtworkColorModeDialog by remember { mutableStateOf(false) }
                        var showCustomLineColorDialog by remember { mutableStateOf(false) }
                        var showCustomGridColorDialog by remember { mutableStateOf(false) }
                        var showTextAnchorDialog by remember { mutableStateOf(false) }
                        var showTextPaddingDialog by remember { mutableStateOf(false) }
                        var showTextSizeDialog by remember { mutableStateOf(false) }
                        var showTextFontDialog by remember { mutableStateOf(false) }
                        var showTextColorModeDialog by remember { mutableStateOf(false) }
                        var showCustomTextColorDialog by remember { mutableStateOf(false) }
                        var showTextVuAnchorDialog by remember { mutableStateOf(false) }
                        var showTextVuColorModeDialog by remember { mutableStateOf(false) }
                        var showTextVuCustomColorDialog by remember { mutableStateOf(false) }
                        var showTextNoteFormatDialog by remember { mutableStateOf(false) }

                        SettingsSectionLabel("Channel scope")
                        SettingsValuePickerCard(
                            title = "Visible window",
                            description = "Time span represented by each channel trace history.",
                            value = "${scopeWindowMs} ms",
                            onClick = { showWindowDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsValuePickerCard(
                            title = "Trigger",
                            description = "Sync mode used to stabilize channel traces.",
                            value = scopeTriggerMode.label,
                            onClick = { showTriggerDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsValuePickerCard(
                            title = "Renderer backend",
                            description = "Select rendering path for Channel scope.",
                            value = scopeRenderBackend.label,
                            onClick = { showRendererBackendDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "DC removal",
                            description = "Center each channel waveform around zero to reduce vertical offset drift.",
                            checked = scopeDcRemovalEnabled,
                            onCheckedChange = { enabled ->
                                scopeDcRemovalEnabled = enabled
                                prefs.edit().putBoolean(scopeDcRemovalEnabledKey, enabled).apply()
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsValuePickerCard(
                            title = "Gain",
                            description = "Output gain applied to channel waveforms.",
                            value = "${scopeGainPercent}%",
                            onClick = { showGainDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsValuePickerCard(
                            title = "Scope frame rate",
                            description = "Rendering rate for channel-scope updates.",
                            value = scopeFpsMode.label,
                            onClick = { showFpsModeDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsValuePickerCard(
                            title = "Layout strategy",
                            description = "Grid arrangement strategy for channel scopes.",
                            value = scopeLayout.label,
                            onClick = { showLayoutDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsValuePickerCard(
                            title = "Line width",
                            description = "Stroke width for channel scope lines.",
                            value = "${scopeLineWidthDp}dp",
                            onClick = { showLineWidthDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsValuePickerCard(
                            title = "Grid width",
                            description = "Stroke width for scope grid lines.",
                            value = "${scopeGridWidthDp}dp",
                            onClick = { showGridWidthDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "Show vertical grid lines",
                            description = "Display vertical time divisions in each channel scope.",
                            checked = scopeVerticalGridEnabled,
                            onCheckedChange = { enabled ->
                                scopeVerticalGridEnabled = enabled
                                prefs.edit().putBoolean(scopeVerticalGridEnabledKey, enabled).apply()
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "Show centerline",
                            description = "Display center reference line in each channel scope.",
                            checked = scopeCenterLineEnabled,
                            onCheckedChange = { enabled ->
                                scopeCenterLineEnabled = enabled
                                prefs.edit().putBoolean(scopeCenterLineEnabledKey, enabled).apply()
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "Show artwork background",
                            description = "Render album artwork/placeholder behind the channel scope.",
                            checked = scopeShowArtworkBackground,
                            onCheckedChange = { enabled ->
                                scopeShowArtworkBackground = enabled
                                prefs.edit().putBoolean(scopeShowArtworkBackgroundKey, enabled).apply()
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "Contrast backdrop",
                            description = "Add a subtle center-weighted dim backdrop for better trace readability.",
                            checked = scopeContrastBackdropEnabled,
                            onCheckedChange = { enabled ->
                                scopeContrastBackdropEnabled = enabled
                                prefs.edit().putBoolean(scopeContrastBackdropEnabledKey, enabled).apply()
                            }
                        )
                        if (!scopeShowArtworkBackground) {
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsValuePickerCard(
                                title = "Background color",
                                description = "Background source when artwork is hidden.",
                                value = scopeBackgroundMode.label,
                                onClick = { showBackgroundModeDialog = true }
                            )
                            if (scopeBackgroundMode == VisualizationChannelScopeBackgroundMode.Custom) {
                                Spacer(modifier = Modifier.height(10.dp))
                                SettingsValuePickerCard(
                                    title = "Custom background color",
                                    description = "RGB color used when background color mode is Custom.",
                                    value = String.format(Locale.US, "#%06X", scopeCustomBackgroundColorArgb and 0xFFFFFF),
                                    onClick = { showCustomBackgroundColorDialog = true }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionLabel("Colors (no artwork)")
                        SettingsValuePickerCard(
                            title = "Line color",
                            description = "Color source used when no artwork is available.",
                            value = scopeLineColorModeNoArtwork.label,
                            onClick = { showLineNoArtworkColorModeDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsValuePickerCard(
                            title = "Grid color",
                            description = "Color source used when no artwork is available.",
                            value = scopeGridColorModeNoArtwork.label,
                            onClick = { showGridNoArtworkColorModeDialog = true }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionLabel("Colors (with artwork)")
                        SettingsValuePickerCard(
                            title = "Line color",
                            description = "Color source used when artwork is available.",
                            value = scopeLineColorModeWithArtwork.label,
                            onClick = { showLineArtworkColorModeDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsValuePickerCard(
                            title = "Grid color",
                            description = "Color source used when artwork is available.",
                            value = scopeGridColorModeWithArtwork.label,
                            onClick = { showGridArtworkColorModeDialog = true }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionLabel("Custom colors")
                        SettingsValuePickerCard(
                            title = "Custom line color",
                            description = "RGB color used when line color mode is Custom.",
                            value = String.format(Locale.US, "#%06X", scopeCustomLineColorArgb and 0xFFFFFF),
                            onClick = { showCustomLineColorDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsValuePickerCard(
                            title = "Custom grid color",
                            description = "RGB color used when grid color mode is Custom.",
                            value = String.format(Locale.US, "#%06X", scopeCustomGridColorArgb and 0xFFFFFF),
                            onClick = { showCustomGridColorDialog = true }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionLabel("Text overlay")
                        PlayerSettingToggleCard(
                            title = "Show channel text",
                            description = "Display per-channel labels and live tracker values on each scope.",
                            checked = scopeTextEnabled,
                            onCheckedChange = { enabled ->
                                scopeTextEnabled = enabled
                                prefs.edit().putBoolean(scopeTextEnabledKey, enabled).apply()
                            }
                        )
                        if (scopeTextEnabled) {
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsValuePickerCard(
                                title = "Text anchor",
                                description = "Position of text inside each channel scope.",
                                value = scopeTextAnchor.label,
                                onClick = { showTextAnchorDialog = true }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsValuePickerCard(
                                title = "Text padding",
                                description = "Padding from the selected anchor.",
                                value = "${scopeTextPaddingDp}dp",
                                onClick = { showTextPaddingDialog = true }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsValuePickerCard(
                                title = "Text size",
                                description = "Font size for per-channel overlay text.",
                                value = "${scopeTextSizeSp}sp",
                                onClick = { showTextSizeDialog = true }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsValuePickerCard(
                                title = "Text font",
                                description = "Typeface used for per-channel overlay text.",
                                value = scopeTextFont.label,
                                onClick = { showTextFontDialog = true }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsValuePickerCard(
                                title = "Text color",
                                description = "Color source for channel-scope text overlay.",
                                value = scopeTextColorMode.label,
                                onClick = { showTextColorModeDialog = true }
                            )
                            if (scopeTextColorMode == VisualizationChannelScopeTextColorMode.Custom) {
                                Spacer(modifier = Modifier.height(10.dp))
                                SettingsValuePickerCard(
                                    title = "Custom text color",
                                    description = "RGB color used when text color mode is Custom.",
                                    value = String.format(Locale.US, "#%06X", scopeCustomTextColorArgb and 0xFFFFFF),
                                    onClick = { showCustomTextColorDialog = true }
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            PlayerSettingToggleCard(
                                title = "Hide when still too large",
                                description = "Hide text if it still cannot fit after auto downscaling.",
                                checked = scopeTextHideWhenOverflow,
                                onCheckedChange = { enabled ->
                                    scopeTextHideWhenOverflow = enabled
                                    prefs.edit().putBoolean(scopeTextHideWhenOverflowKey, enabled).apply()
                                }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            PlayerSettingToggleCard(
                                title = "Text shadow",
                                description = "Render a subtle drop shadow for better contrast on bright or matching backgrounds.",
                                checked = scopeTextShadowEnabled,
                                onCheckedChange = { enabled ->
                                    scopeTextShadowEnabled = enabled
                                    prefs.edit().putBoolean(scopeTextShadowEnabledKey, enabled).apply()
                                }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SettingsValuePickerCard(
                                title = "Note naming",
                                description = "Format used for note names in the overlay.",
                                value = scopeTextNoteFormat.label,
                                onClick = { showTextNoteFormatDialog = true }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            PlayerSettingToggleCard(
                                title = "Show channel label",
                                description = "Show channel index or Amiga L/R label.",
                                checked = scopeTextShowChannel,
                                onCheckedChange = { enabled ->
                                    scopeTextShowChannel = enabled
                                    prefs.edit().putBoolean(scopeTextShowChannelKey, enabled).apply()
                                }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            PlayerSettingToggleCard(
                                title = "Show note",
                                description = "Show current note name.",
                                checked = scopeTextShowNote,
                                onCheckedChange = { enabled ->
                                    scopeTextShowNote = enabled
                                    prefs.edit().putBoolean(scopeTextShowNoteKey, enabled).apply()
                                }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            PlayerSettingToggleCard(
                                title = "Show volume",
                                description = "Show current channel volume value.",
                                checked = scopeTextShowVolume,
                                onCheckedChange = { enabled ->
                                    scopeTextShowVolume = enabled
                                    prefs.edit().putBoolean(scopeTextShowVolumeKey, enabled).apply()
                                }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            PlayerSettingToggleCard(
                                title = "Show effect",
                                description = "Show active effect command and parameter.",
                                checked = scopeTextShowEffect,
                                onCheckedChange = { enabled ->
                                    scopeTextShowEffect = enabled
                                    prefs.edit().putBoolean(scopeTextShowEffectKey, enabled).apply()
                                }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            PlayerSettingToggleCard(
                                title = "Show instrument/sample",
                                description = "Show active instrument or sample index.",
                                checked = scopeTextShowInstrumentSample,
                                onCheckedChange = { enabled ->
                                    scopeTextShowInstrumentSample = enabled
                                    prefs.edit().putBoolean(scopeTextShowInstrumentSampleKey, enabled).apply()
                                }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            PlayerSettingToggleCard(
                                title = "Show VU strip",
                                description = "Show a per-channel VU strip at top or bottom edge.",
                                checked = scopeTextVuEnabled,
                                onCheckedChange = { enabled ->
                                    scopeTextVuEnabled = enabled
                                    prefs.edit().putBoolean(scopeTextVuEnabledKey, enabled).apply()
                                }
                            )
                            if (scopeTextVuEnabled) {
                                Spacer(modifier = Modifier.height(10.dp))
                                SettingsValuePickerCard(
                                    title = "VU strip anchor",
                                    description = "Place VU strip at top or bottom of each channel cell.",
                                    value = scopeTextVuAnchor.label,
                                    onClick = { showTextVuAnchorDialog = true }
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                SettingsValuePickerCard(
                                    title = "VU strip color",
                                    description = "Color source used by the VU strip.",
                                    value = scopeTextVuColorMode.label,
                                    onClick = { showTextVuColorModeDialog = true }
                                )
                                if (scopeTextVuColorMode == VisualizationChannelScopeTextColorMode.Custom) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    SettingsValuePickerCard(
                                        title = "Custom VU strip color",
                                        description = "RGB color used when VU strip color mode is Custom.",
                                        value = String.format(Locale.US, "#%06X", scopeTextVuCustomColorArgb and 0xFFFFFF),
                                        onClick = { showTextVuCustomColorDialog = true }
                                    )
                                }
                            }
                        }

                        if (showWindowDialog) {
                            SteppedIntSliderDialog(
                                title = "Visible window",
                                unitLabel = "ms",
                                range = AppDefaults.Visualization.ChannelScope.windowRangeMs,
                                step = 1,
                                currentValue = scopeWindowMs,
                                onDismiss = { showWindowDialog = false },
                                onConfirm = { value ->
                                    val clamped = value.coerceIn(5, 200)
                                    scopeWindowMs = clamped
                                    prefs.edit().putInt(scopeWindowKey, clamped).apply()
                                    showWindowDialog = false
                                }
                            )
                        }
                        if (showGainDialog) {
                            SteppedIntSliderDialog(
                                title = "Scope gain",
                                unitLabel = "%",
                                range = AppDefaults.Visualization.ChannelScope.gainRangePercent,
                                step = 5,
                                currentValue = scopeGainPercent,
                                onDismiss = { showGainDialog = false },
                                onConfirm = { value ->
                                    val clamped = value.coerceIn(25, 600)
                                    scopeGainPercent = clamped
                                    prefs.edit().putInt(scopeGainPercentKey, clamped).apply()
                                    showGainDialog = false
                                }
                            )
                        }
                        if (showRendererBackendDialog) {
                            SettingsSingleChoiceDialog(
                                title = "Renderer backend",
                                selectedValue = scopeRenderBackend,
                                options = VisualizationRenderBackend.entries.map { backend ->
                                    ChoiceDialogOption(value = backend, label = backend.label)
                                },
                                onSelected = { backend ->
                                    scopeRenderBackend = backend
                                    prefs.edit().putString(scopeRenderBackendKey, backend.storageValue).apply()
                                },
                                onDismiss = { showRendererBackendDialog = false }
                            )
                        }
                        if (showTriggerDialog) {
                            SettingsSingleChoiceDialog(
                                title = "Channel scope trigger",
                                selectedValue = scopeTriggerMode,
                                options = VisualizationOscTriggerMode.entries.map { mode ->
                                    ChoiceDialogOption(value = mode, label = mode.label)
                                },
                                onSelected = { mode ->
                                    scopeTriggerMode = mode
                                    prefs.edit().putString(scopeTriggerKey, mode.storageValue).apply()
                                },
                                onDismiss = { showTriggerDialog = false }
                            )
                        }
                        if (showBackgroundModeDialog) {
                            SettingsSingleChoiceDialog(
                                title = "Scope background color",
                                selectedValue = scopeBackgroundMode,
                                options = VisualizationChannelScopeBackgroundMode.entries.map { mode ->
                                    ChoiceDialogOption(value = mode, label = mode.label)
                                },
                                onSelected = { mode ->
                                    scopeBackgroundMode = mode
                                    prefs.edit().putString(scopeBackgroundModeKey, mode.storageValue).apply()
                                },
                                onDismiss = { showBackgroundModeDialog = false }
                            )
                        }
                        if (showCustomBackgroundColorDialog) {
                            VisualizationRgbColorPickerDialog(
                                title = "Custom scope background color",
                                initialArgb = scopeCustomBackgroundColorArgb,
                                onDismiss = { showCustomBackgroundColorDialog = false },
                                onConfirm = { argb ->
                                    scopeCustomBackgroundColorArgb = argb
                                    prefs.edit().putInt(scopeCustomBackgroundColorKey, argb).apply()
                                    showCustomBackgroundColorDialog = false
                                }
                            )
                        }
                        if (showFpsModeDialog) {
                            SettingsSingleChoiceDialog(
                                title = "Scope frame rate",
                                selectedValue = scopeFpsMode,
                                options = VisualizationOscFpsMode.entries.map { mode ->
                                    ChoiceDialogOption(value = mode, label = mode.label)
                                },
                                onSelected = { mode ->
                                    scopeFpsMode = mode
                                    prefs.edit().putString(scopeFpsModeKey, mode.storageValue).apply()
                                },
                                onDismiss = { showFpsModeDialog = false }
                            )
                        }
                        if (showLayoutDialog) {
                            SettingsSingleChoiceDialog(
                                title = "Layout strategy",
                                selectedValue = scopeLayout,
                                options = VisualizationChannelScopeLayout.entries.map { layout ->
                                    ChoiceDialogOption(value = layout, label = layout.label)
                                },
                                onSelected = { layout ->
                                    scopeLayout = layout
                                    prefs.edit().putString(scopeLayoutKey, layout.storageValue).apply()
                                },
                                onDismiss = { showLayoutDialog = false }
                            )
                        }
                        if (showLineWidthDialog) {
                            SteppedIntSliderDialog(
                                title = "Scope line width",
                                unitLabel = "dp",
                                range = AppDefaults.Visualization.ChannelScope.lineWidthRangeDp,
                                step = 1,
                                currentValue = scopeLineWidthDp,
                                onDismiss = { showLineWidthDialog = false },
                                onConfirm = { value ->
                                    val clamped = value.coerceIn(1, 12)
                                    scopeLineWidthDp = clamped
                                    prefs.edit().putInt(scopeLineWidthKey, clamped).apply()
                                    showLineWidthDialog = false
                                }
                            )
                        }
                        if (showGridWidthDialog) {
                            SteppedIntSliderDialog(
                                title = "Grid line width",
                                unitLabel = "dp",
                                range = AppDefaults.Visualization.ChannelScope.gridWidthRangeDp,
                                step = 1,
                                currentValue = scopeGridWidthDp,
                                onDismiss = { showGridWidthDialog = false },
                                onConfirm = { value ->
                                    val clamped = value.coerceIn(1, 8)
                                    scopeGridWidthDp = clamped
                                    prefs.edit().putInt(scopeGridWidthKey, clamped).apply()
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
                                selectedMode = scopeLineColorModeNoArtwork,
                                onDismiss = { showLineNoArtworkColorModeDialog = false },
                                onSelect = { mode ->
                                    scopeLineColorModeNoArtwork = mode
                                    prefs.edit().putString(scopeLineNoArtworkColorModeKey, mode.storageValue).apply()
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
                                selectedMode = scopeGridColorModeNoArtwork,
                                onDismiss = { showGridNoArtworkColorModeDialog = false },
                                onSelect = { mode ->
                                    scopeGridColorModeNoArtwork = mode
                                    prefs.edit().putString(scopeGridNoArtworkColorModeKey, mode.storageValue).apply()
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
                                selectedMode = scopeLineColorModeWithArtwork,
                                onDismiss = { showLineArtworkColorModeDialog = false },
                                onSelect = { mode ->
                                    scopeLineColorModeWithArtwork = mode
                                    prefs.edit().putString(scopeLineArtworkColorModeKey, mode.storageValue).apply()
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
                                selectedMode = scopeGridColorModeWithArtwork,
                                onDismiss = { showGridArtworkColorModeDialog = false },
                                onSelect = { mode ->
                                    scopeGridColorModeWithArtwork = mode
                                    prefs.edit().putString(scopeGridArtworkColorModeKey, mode.storageValue).apply()
                                    showGridArtworkColorModeDialog = false
                                }
                            )
                        }
                        if (showCustomLineColorDialog) {
                            VisualizationRgbColorPickerDialog(
                                title = "Custom line color",
                                initialArgb = scopeCustomLineColorArgb,
                                onDismiss = { showCustomLineColorDialog = false },
                                onConfirm = { argb ->
                                    scopeCustomLineColorArgb = argb
                                    prefs.edit().putInt(scopeCustomLineColorKey, argb).apply()
                                    showCustomLineColorDialog = false
                                }
                            )
                        }
                        if (showCustomGridColorDialog) {
                            VisualizationRgbColorPickerDialog(
                                title = "Custom grid color",
                                initialArgb = scopeCustomGridColorArgb,
                                onDismiss = { showCustomGridColorDialog = false },
                                onConfirm = { argb ->
                                    scopeCustomGridColorArgb = argb
                                    prefs.edit().putInt(scopeCustomGridColorKey, argb).apply()
                                    showCustomGridColorDialog = false
                                }
                            )
                        }
                        if (showTextAnchorDialog) {
                            SettingsSingleChoiceDialog(
                                title = "Text anchor",
                                selectedValue = scopeTextAnchor,
                                options = VisualizationChannelScopeTextAnchor.entries.map { entry ->
                                    ChoiceDialogOption(value = entry, label = entry.label)
                                },
                                onSelected = { entry ->
                                    scopeTextAnchor = entry
                                    prefs.edit().putString(scopeTextAnchorKey, entry.storageValue).apply()
                                },
                                onDismiss = { showTextAnchorDialog = false }
                            )
                        }
                        if (showTextPaddingDialog) {
                            SteppedIntSliderDialog(
                                title = "Text padding",
                                unitLabel = "dp",
                                range = AppDefaults.Visualization.ChannelScope.textPaddingRangeDp,
                                step = 1,
                                currentValue = scopeTextPaddingDp,
                                onDismiss = { showTextPaddingDialog = false },
                                onConfirm = { value ->
                                    val clamped = value.coerceIn(
                                        AppDefaults.Visualization.ChannelScope.textPaddingRangeDp.first,
                                        AppDefaults.Visualization.ChannelScope.textPaddingRangeDp.last
                                    )
                                    scopeTextPaddingDp = clamped
                                    prefs.edit().putInt(scopeTextPaddingKey, clamped).apply()
                                    showTextPaddingDialog = false
                                }
                            )
                        }
                        if (showTextSizeDialog) {
                            SteppedIntSliderDialog(
                                title = "Text size",
                                unitLabel = "sp",
                                range = AppDefaults.Visualization.ChannelScope.textSizeRangeSp,
                                step = 1,
                                currentValue = scopeTextSizeSp,
                                onDismiss = { showTextSizeDialog = false },
                                onConfirm = { value ->
                                    val clamped = value.coerceIn(
                                        AppDefaults.Visualization.ChannelScope.textSizeRangeSp.first,
                                        AppDefaults.Visualization.ChannelScope.textSizeRangeSp.last
                                    )
                                    scopeTextSizeSp = clamped
                                    prefs.edit().putInt(scopeTextSizeKey, clamped).apply()
                                    showTextSizeDialog = false
                                }
                            )
                        }
                        if (showTextColorModeDialog) {
                            SettingsSingleChoiceDialog(
                                title = "Text color",
                                selectedValue = scopeTextColorMode,
                                options = VisualizationChannelScopeTextColorMode.entries.map { entry ->
                                    ChoiceDialogOption(value = entry, label = entry.label)
                                },
                                onSelected = { entry ->
                                    scopeTextColorMode = entry
                                    prefs.edit().putString(scopeTextColorModeKey, entry.storageValue).apply()
                                },
                                onDismiss = { showTextColorModeDialog = false }
                            )
                        }
                        if (showTextFontDialog) {
                            SettingsSingleChoiceDialog(
                                title = "Text font",
                                selectedValue = scopeTextFont,
                                options = VisualizationChannelScopeTextFont.entries.map { entry ->
                                    ChoiceDialogOption(value = entry, label = entry.label)
                                },
                                onSelected = { entry ->
                                    scopeTextFont = entry
                                    prefs.edit().putString(scopeTextFontKey, entry.storageValue).apply()
                                },
                                onDismiss = { showTextFontDialog = false }
                            )
                        }
                        if (showTextVuAnchorDialog) {
                            SettingsSingleChoiceDialog(
                                title = "VU strip anchor",
                                selectedValue = scopeTextVuAnchor,
                                options = listOf(
                                    ChoiceDialogOption(value = VisualizationVuAnchor.Top, label = VisualizationVuAnchor.Top.label),
                                    ChoiceDialogOption(value = VisualizationVuAnchor.Bottom, label = VisualizationVuAnchor.Bottom.label)
                                ),
                                onSelected = { entry ->
                                    scopeTextVuAnchor = entry
                                    prefs.edit().putString(scopeTextVuAnchorKey, entry.storageValue).apply()
                                },
                                onDismiss = { showTextVuAnchorDialog = false }
                            )
                        }
                        if (showTextVuColorModeDialog) {
                            SettingsSingleChoiceDialog(
                                title = "VU strip color",
                                selectedValue = scopeTextVuColorMode,
                                options = VisualizationChannelScopeTextColorMode.entries.map { entry ->
                                    ChoiceDialogOption(value = entry, label = entry.label)
                                },
                                onSelected = { entry ->
                                    scopeTextVuColorMode = entry
                                    prefs.edit().putString(scopeTextVuColorModeKey, entry.storageValue).apply()
                                },
                                onDismiss = { showTextVuColorModeDialog = false }
                            )
                        }
                        if (showTextVuCustomColorDialog) {
                            VisualizationRgbColorPickerDialog(
                                title = "Custom VU strip color",
                                initialArgb = scopeTextVuCustomColorArgb,
                                onDismiss = { showTextVuCustomColorDialog = false },
                                onConfirm = { argb ->
                                    scopeTextVuCustomColorArgb = argb
                                    prefs.edit().putInt(scopeTextVuCustomColorKey, argb).apply()
                                    showTextVuCustomColorDialog = false
                                }
                            )
                        }
                        if (showCustomTextColorDialog) {
                            VisualizationRgbColorPickerDialog(
                                title = "Custom text color",
                                initialArgb = scopeCustomTextColorArgb,
                                onDismiss = { showCustomTextColorDialog = false },
                                onConfirm = { argb ->
                                    scopeCustomTextColorArgb = argb
                                    prefs.edit().putInt(scopeCustomTextColorKey, argb).apply()
                                    showCustomTextColorDialog = false
                                }
                            )
                        }
                        if (showTextNoteFormatDialog) {
                            SettingsSingleChoiceDialog(
                                title = "Note naming",
                                selectedValue = scopeTextNoteFormat,
                                options = VisualizationNoteNameFormat.entries.map { entry ->
                                    ChoiceDialogOption(value = entry, label = entry.label)
                                },
                                onSelected = { entry ->
                                    scopeTextNoteFormat = entry
                                    prefs.edit().putString(scopeTextNoteFormatKey, entry.storageValue).apply()
                                },
                                onDismiss = { showTextNoteFormatDialog = false }
                            )
                        }
}
