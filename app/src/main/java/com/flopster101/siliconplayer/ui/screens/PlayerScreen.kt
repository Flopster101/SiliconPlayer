package com.flopster101.siliconplayer.ui.screens

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import com.flopster101.siliconplayer.NativeBridge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.focusable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import android.view.MotionEvent
import com.flopster101.siliconplayer.AppDefaults
import com.flopster101.siliconplayer.DecoderNames
import com.flopster101.siliconplayer.inferredDisplayTitleForName
import com.flopster101.siliconplayer.inferredPrimaryExtensionForName
import com.flopster101.siliconplayer.R
import com.flopster101.siliconplayer.RepeatMode
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.VisualizationChannelScopeLayout
import com.flopster101.siliconplayer.VisualizationOscColorMode
import com.flopster101.siliconplayer.VisualizationOscFpsMode
import com.flopster101.siliconplayer.VisualizationRenderBackend
import com.flopster101.siliconplayer.VisualizationVuAnchor
import com.flopster101.siliconplayer.adaptiveDialogModifier
import com.flopster101.siliconplayer.adaptiveDialogProperties
import com.flopster101.siliconplayer.formatByteCount
import com.flopster101.siliconplayer.pluginNameForCoreName
import com.flopster101.siliconplayer.RemoteLoadPhase
import com.flopster101.siliconplayer.RemoteLoadUiState
import com.flopster101.siliconplayer.RemoteLoadUiStateHolder
import com.flopster101.siliconplayer.rememberDialogScrollbarAlpha
import com.flopster101.siliconplayer.sanitizeRemoteCachedMetadataTitle
import com.flopster101.siliconplayer.stripRemoteCacheHashPrefix
import com.flopster101.siliconplayer.ui.dialogs.VisualizationModePickerDialog
import com.flopster101.siliconplayer.ui.visualization.basic.BasicVisualizationOverlay
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import androidx.compose.foundation.text.selection.SelectionContainer

internal val LocalPlayerFocusIndicatorsEnabled = compositionLocalOf { true }

private const val PREF_KEY_VIS_OSC_WINDOW_MS = "visualization_osc_window_ms"
private const val PREF_KEY_VIS_OSC_TRIGGER_MODE = "visualization_osc_trigger_mode"
private const val PREF_KEY_VIS_OSC_LINE_WIDTH_DP = "visualization_osc_line_width_dp"
private const val PREF_KEY_VIS_OSC_GRID_WIDTH_DP = "visualization_osc_grid_width_dp"
private const val PREF_KEY_VIS_OSC_FPS_MODE = "visualization_osc_fps_mode"
private const val PREF_KEY_VIS_OSC_RENDER_BACKEND = "visualization_osc_render_backend"
private const val PREF_KEY_VIS_OSC_VERTICAL_GRID_ENABLED = "visualization_osc_vertical_grid_enabled"
private const val PREF_KEY_VIS_OSC_CENTER_LINE_ENABLED = "visualization_osc_center_line_enabled"
private const val PREF_KEY_VIS_OSC_LINE_COLOR_NO_ARTWORK = "visualization_osc_line_color_mode_no_artwork"
private const val PREF_KEY_VIS_OSC_GRID_COLOR_NO_ARTWORK = "visualization_osc_grid_color_mode_no_artwork"
private const val PREF_KEY_VIS_OSC_LINE_COLOR_WITH_ARTWORK = "visualization_osc_line_color_mode_with_artwork"
private const val PREF_KEY_VIS_OSC_GRID_COLOR_WITH_ARTWORK = "visualization_osc_grid_color_mode_with_artwork"
private const val PREF_KEY_VIS_OSC_CUSTOM_LINE_COLOR = "visualization_osc_custom_line_color_argb"
private const val PREF_KEY_VIS_OSC_CUSTOM_GRID_COLOR = "visualization_osc_custom_grid_color_argb"
private const val PREF_KEY_VIS_BAR_RENDER_BACKEND = "visualization_bar_render_backend"
private const val PREF_KEY_VIS_BAR_FPS_MODE = "visualization_bar_fps_mode"
private const val PREF_KEY_VIS_BAR_FREQUENCY_GRID_ENABLED = "visualization_bar_frequency_grid_enabled"
private const val PREF_KEY_VIS_BAR_COLOR_NO_ARTWORK = "visualization_bar_color_mode_no_artwork"
private const val PREF_KEY_VIS_BAR_COLOR_WITH_ARTWORK = "visualization_bar_color_mode_with_artwork"
private const val PREF_KEY_VIS_BAR_CUSTOM_COLOR = "visualization_bar_custom_color_argb"
private const val PREF_KEY_VIS_VU_RENDER_BACKEND = "visualization_vu_render_backend"
private const val PREF_KEY_VIS_VU_FPS_MODE = "visualization_vu_fps_mode"
private const val PREF_KEY_VIS_VU_COLOR_NO_ARTWORK = "visualization_vu_color_mode_no_artwork"
private const val PREF_KEY_VIS_VU_COLOR_WITH_ARTWORK = "visualization_vu_color_mode_with_artwork"
private const val PREF_KEY_VIS_VU_CUSTOM_COLOR = "visualization_vu_custom_color_argb"

private class PlayerVisualizationPreferenceState(
    oscWindowMs: Int,
    oscTriggerModeNative: Int,
    oscFpsMode: VisualizationOscFpsMode,
    oscRenderBackend: VisualizationRenderBackend,
    oscLineWidthDp: Int,
    oscGridWidthDp: Int,
    oscVerticalGridEnabled: Boolean,
    oscCenterLineEnabled: Boolean,
    oscLineColorModeNoArtwork: VisualizationOscColorMode,
    oscGridColorModeNoArtwork: VisualizationOscColorMode,
    oscLineColorModeWithArtwork: VisualizationOscColorMode,
    oscGridColorModeWithArtwork: VisualizationOscColorMode,
    oscCustomLineColorArgb: Int,
    oscCustomGridColorArgb: Int,
    barColorModeNoArtwork: VisualizationOscColorMode,
    barColorModeWithArtwork: VisualizationOscColorMode,
    barCustomColorArgb: Int,
    barFrequencyGridEnabled: Boolean,
    barFpsMode: VisualizationOscFpsMode,
    barRuntimeRenderBackend: VisualizationRenderBackend,
    vuColorModeNoArtwork: VisualizationOscColorMode,
    vuColorModeWithArtwork: VisualizationOscColorMode,
    vuCustomColorArgb: Int,
    vuFpsMode: VisualizationOscFpsMode,
    vuRuntimeRenderBackend: VisualizationRenderBackend
) {
    var oscWindowMs by mutableIntStateOf(oscWindowMs)
    var oscTriggerModeNative by mutableIntStateOf(oscTriggerModeNative)
    var oscFpsMode by mutableStateOf(oscFpsMode)
    var oscRenderBackend by mutableStateOf(oscRenderBackend)
    var oscLineWidthDp by mutableIntStateOf(oscLineWidthDp)
    var oscGridWidthDp by mutableIntStateOf(oscGridWidthDp)
    var oscVerticalGridEnabled by mutableStateOf(oscVerticalGridEnabled)
    var oscCenterLineEnabled by mutableStateOf(oscCenterLineEnabled)
    var oscLineColorModeNoArtwork by mutableStateOf(oscLineColorModeNoArtwork)
    var oscGridColorModeNoArtwork by mutableStateOf(oscGridColorModeNoArtwork)
    var oscLineColorModeWithArtwork by mutableStateOf(oscLineColorModeWithArtwork)
    var oscGridColorModeWithArtwork by mutableStateOf(oscGridColorModeWithArtwork)
    var oscCustomLineColorArgb by mutableIntStateOf(oscCustomLineColorArgb)
    var oscCustomGridColorArgb by mutableIntStateOf(oscCustomGridColorArgb)
    var barColorModeNoArtwork by mutableStateOf(barColorModeNoArtwork)
    var barColorModeWithArtwork by mutableStateOf(barColorModeWithArtwork)
    var barCustomColorArgb by mutableIntStateOf(barCustomColorArgb)
    var barFrequencyGridEnabled by mutableStateOf(barFrequencyGridEnabled)
    var barFpsMode by mutableStateOf(barFpsMode)
    var barRuntimeRenderBackend by mutableStateOf(barRuntimeRenderBackend)
    var vuColorModeNoArtwork by mutableStateOf(vuColorModeNoArtwork)
    var vuColorModeWithArtwork by mutableStateOf(vuColorModeWithArtwork)
    var vuCustomColorArgb by mutableIntStateOf(vuCustomColorArgb)
    var vuFpsMode by mutableStateOf(vuFpsMode)
    var vuRuntimeRenderBackend by mutableStateOf(vuRuntimeRenderBackend)
}

private fun parseOscTriggerModeNative(value: String?): Int {
    return when (value) {
        "rising" -> 1
        "falling" -> 2
        else -> 0
    }
}

@Composable
private fun rememberPlayerVisualizationPreferenceState(
    prefs: SharedPreferences,
    defaultBarRenderBackend: VisualizationRenderBackend,
    defaultVuRenderBackend: VisualizationRenderBackend
): PlayerVisualizationPreferenceState {
    val state = remember(prefs, defaultBarRenderBackend, defaultVuRenderBackend) {
        PlayerVisualizationPreferenceState(
            oscWindowMs = prefs.getInt(PREF_KEY_VIS_OSC_WINDOW_MS, 40).coerceIn(5, 200),
            oscTriggerModeNative = parseOscTriggerModeNative(
                prefs.getString(PREF_KEY_VIS_OSC_TRIGGER_MODE, "rising")
            ),
            oscFpsMode = VisualizationOscFpsMode.fromStorage(
                prefs.getString(PREF_KEY_VIS_OSC_FPS_MODE, VisualizationOscFpsMode.Default.storageValue)
            ),
            oscRenderBackend = VisualizationRenderBackend.fromStorage(
                prefs.getString(
                    PREF_KEY_VIS_OSC_RENDER_BACKEND,
                    AppDefaults.Visualization.Oscilloscope.renderBackend.storageValue
                ),
                AppDefaults.Visualization.Oscilloscope.renderBackend
            ),
            oscLineWidthDp = prefs.getInt(PREF_KEY_VIS_OSC_LINE_WIDTH_DP, 3).coerceIn(1, 12),
            oscGridWidthDp = prefs.getInt(PREF_KEY_VIS_OSC_GRID_WIDTH_DP, 2).coerceIn(1, 8),
            oscVerticalGridEnabled = prefs.getBoolean(PREF_KEY_VIS_OSC_VERTICAL_GRID_ENABLED, false),
            oscCenterLineEnabled = prefs.getBoolean(PREF_KEY_VIS_OSC_CENTER_LINE_ENABLED, false),
            oscLineColorModeNoArtwork = VisualizationOscColorMode.fromStorage(
                prefs.getString(
                    PREF_KEY_VIS_OSC_LINE_COLOR_NO_ARTWORK,
                    VisualizationOscColorMode.Monet.storageValue
                ),
                VisualizationOscColorMode.Monet
            ),
            oscGridColorModeNoArtwork = VisualizationOscColorMode.fromStorage(
                prefs.getString(
                    PREF_KEY_VIS_OSC_GRID_COLOR_NO_ARTWORK,
                    VisualizationOscColorMode.Monet.storageValue
                ),
                VisualizationOscColorMode.Monet
            ),
            oscLineColorModeWithArtwork = VisualizationOscColorMode.fromStorage(
                prefs.getString(
                    PREF_KEY_VIS_OSC_LINE_COLOR_WITH_ARTWORK,
                    VisualizationOscColorMode.Artwork.storageValue
                ),
                VisualizationOscColorMode.Artwork
            ),
            oscGridColorModeWithArtwork = VisualizationOscColorMode.fromStorage(
                prefs.getString(
                    PREF_KEY_VIS_OSC_GRID_COLOR_WITH_ARTWORK,
                    VisualizationOscColorMode.Artwork.storageValue
                ),
                VisualizationOscColorMode.Artwork
            ),
            oscCustomLineColorArgb = prefs.getInt(PREF_KEY_VIS_OSC_CUSTOM_LINE_COLOR, 0xFF6BD8FF.toInt()),
            oscCustomGridColorArgb = prefs.getInt(PREF_KEY_VIS_OSC_CUSTOM_GRID_COLOR, 0x66FFFFFF),
            barColorModeNoArtwork = VisualizationOscColorMode.fromStorage(
                prefs.getString(
                    PREF_KEY_VIS_BAR_COLOR_NO_ARTWORK,
                    VisualizationOscColorMode.Monet.storageValue
                ),
                VisualizationOscColorMode.Monet
            ),
            barColorModeWithArtwork = VisualizationOscColorMode.fromStorage(
                prefs.getString(
                    PREF_KEY_VIS_BAR_COLOR_WITH_ARTWORK,
                    VisualizationOscColorMode.Artwork.storageValue
                ),
                VisualizationOscColorMode.Artwork
            ),
            barCustomColorArgb = prefs.getInt(PREF_KEY_VIS_BAR_CUSTOM_COLOR, 0xFF6BD8FF.toInt()),
            barFrequencyGridEnabled = prefs.getBoolean(
                PREF_KEY_VIS_BAR_FREQUENCY_GRID_ENABLED,
                AppDefaults.Visualization.Bars.frequencyGridEnabled
            ),
            barFpsMode = VisualizationOscFpsMode.fromStorage(
                prefs.getString(
                    PREF_KEY_VIS_BAR_FPS_MODE,
                    AppDefaults.Visualization.Bars.fpsMode.storageValue
                )
            ),
            barRuntimeRenderBackend = VisualizationRenderBackend.fromStorage(
                prefs.getString(PREF_KEY_VIS_BAR_RENDER_BACKEND, defaultBarRenderBackend.storageValue),
                defaultBarRenderBackend
            ),
            vuColorModeNoArtwork = VisualizationOscColorMode.fromStorage(
                prefs.getString(
                    PREF_KEY_VIS_VU_COLOR_NO_ARTWORK,
                    VisualizationOscColorMode.Monet.storageValue
                ),
                VisualizationOscColorMode.Monet
            ),
            vuColorModeWithArtwork = VisualizationOscColorMode.fromStorage(
                prefs.getString(
                    PREF_KEY_VIS_VU_COLOR_WITH_ARTWORK,
                    VisualizationOscColorMode.Artwork.storageValue
                ),
                VisualizationOscColorMode.Artwork
            ),
            vuCustomColorArgb = prefs.getInt(PREF_KEY_VIS_VU_CUSTOM_COLOR, 0xFF6BD8FF.toInt()),
            vuFpsMode = VisualizationOscFpsMode.fromStorage(
                prefs.getString(
                    PREF_KEY_VIS_VU_FPS_MODE,
                    AppDefaults.Visualization.Vu.fpsMode.storageValue
                )
            ),
            vuRuntimeRenderBackend = VisualizationRenderBackend.fromStorage(
                prefs.getString(PREF_KEY_VIS_VU_RENDER_BACKEND, defaultVuRenderBackend.storageValue),
                defaultVuRenderBackend
            )
        )
    }
    DisposableEffect(prefs, defaultBarRenderBackend, defaultVuRenderBackend) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
            when (key) {
                PREF_KEY_VIS_OSC_WINDOW_MS -> {
                    state.oscWindowMs = sharedPrefs.getInt(PREF_KEY_VIS_OSC_WINDOW_MS, 40).coerceIn(5, 200)
                }
                PREF_KEY_VIS_OSC_TRIGGER_MODE -> {
                    state.oscTriggerModeNative = parseOscTriggerModeNative(
                        sharedPrefs.getString(PREF_KEY_VIS_OSC_TRIGGER_MODE, "rising")
                    )
                }
                PREF_KEY_VIS_OSC_FPS_MODE -> {
                    state.oscFpsMode = VisualizationOscFpsMode.fromStorage(
                        sharedPrefs.getString(
                            PREF_KEY_VIS_OSC_FPS_MODE,
                            VisualizationOscFpsMode.Default.storageValue
                        )
                    )
                }
                PREF_KEY_VIS_OSC_RENDER_BACKEND -> {
                    state.oscRenderBackend = VisualizationRenderBackend.fromStorage(
                        sharedPrefs.getString(
                            PREF_KEY_VIS_OSC_RENDER_BACKEND,
                            AppDefaults.Visualization.Oscilloscope.renderBackend.storageValue
                        ),
                        AppDefaults.Visualization.Oscilloscope.renderBackend
                    )
                }
                PREF_KEY_VIS_OSC_LINE_WIDTH_DP -> {
                    state.oscLineWidthDp = sharedPrefs.getInt(PREF_KEY_VIS_OSC_LINE_WIDTH_DP, 3).coerceIn(1, 12)
                }
                PREF_KEY_VIS_OSC_GRID_WIDTH_DP -> {
                    state.oscGridWidthDp = sharedPrefs.getInt(PREF_KEY_VIS_OSC_GRID_WIDTH_DP, 2).coerceIn(1, 8)
                }
                PREF_KEY_VIS_OSC_VERTICAL_GRID_ENABLED -> {
                    state.oscVerticalGridEnabled =
                        sharedPrefs.getBoolean(PREF_KEY_VIS_OSC_VERTICAL_GRID_ENABLED, false)
                }
                PREF_KEY_VIS_OSC_CENTER_LINE_ENABLED -> {
                    state.oscCenterLineEnabled =
                        sharedPrefs.getBoolean(PREF_KEY_VIS_OSC_CENTER_LINE_ENABLED, false)
                }
                PREF_KEY_VIS_OSC_LINE_COLOR_NO_ARTWORK -> {
                    state.oscLineColorModeNoArtwork = VisualizationOscColorMode.fromStorage(
                        sharedPrefs.getString(
                            PREF_KEY_VIS_OSC_LINE_COLOR_NO_ARTWORK,
                            VisualizationOscColorMode.Monet.storageValue
                        ),
                        VisualizationOscColorMode.Monet
                    )
                }
                PREF_KEY_VIS_OSC_GRID_COLOR_NO_ARTWORK -> {
                    state.oscGridColorModeNoArtwork = VisualizationOscColorMode.fromStorage(
                        sharedPrefs.getString(
                            PREF_KEY_VIS_OSC_GRID_COLOR_NO_ARTWORK,
                            VisualizationOscColorMode.Monet.storageValue
                        ),
                        VisualizationOscColorMode.Monet
                    )
                }
                PREF_KEY_VIS_OSC_LINE_COLOR_WITH_ARTWORK -> {
                    state.oscLineColorModeWithArtwork = VisualizationOscColorMode.fromStorage(
                        sharedPrefs.getString(
                            PREF_KEY_VIS_OSC_LINE_COLOR_WITH_ARTWORK,
                            VisualizationOscColorMode.Artwork.storageValue
                        ),
                        VisualizationOscColorMode.Artwork
                    )
                }
                PREF_KEY_VIS_OSC_GRID_COLOR_WITH_ARTWORK -> {
                    state.oscGridColorModeWithArtwork = VisualizationOscColorMode.fromStorage(
                        sharedPrefs.getString(
                            PREF_KEY_VIS_OSC_GRID_COLOR_WITH_ARTWORK,
                            VisualizationOscColorMode.Artwork.storageValue
                        ),
                        VisualizationOscColorMode.Artwork
                    )
                }
                PREF_KEY_VIS_OSC_CUSTOM_LINE_COLOR -> {
                    state.oscCustomLineColorArgb =
                        sharedPrefs.getInt(PREF_KEY_VIS_OSC_CUSTOM_LINE_COLOR, 0xFF6BD8FF.toInt())
                }
                PREF_KEY_VIS_OSC_CUSTOM_GRID_COLOR -> {
                    state.oscCustomGridColorArgb =
                        sharedPrefs.getInt(PREF_KEY_VIS_OSC_CUSTOM_GRID_COLOR, 0x66FFFFFF)
                }
                PREF_KEY_VIS_BAR_COLOR_NO_ARTWORK -> {
                    state.barColorModeNoArtwork = VisualizationOscColorMode.fromStorage(
                        sharedPrefs.getString(
                            PREF_KEY_VIS_BAR_COLOR_NO_ARTWORK,
                            VisualizationOscColorMode.Monet.storageValue
                        ),
                        VisualizationOscColorMode.Monet
                    )
                }
                PREF_KEY_VIS_BAR_COLOR_WITH_ARTWORK -> {
                    state.barColorModeWithArtwork = VisualizationOscColorMode.fromStorage(
                        sharedPrefs.getString(
                            PREF_KEY_VIS_BAR_COLOR_WITH_ARTWORK,
                            VisualizationOscColorMode.Artwork.storageValue
                        ),
                        VisualizationOscColorMode.Artwork
                    )
                }
                PREF_KEY_VIS_BAR_CUSTOM_COLOR -> {
                    state.barCustomColorArgb =
                        sharedPrefs.getInt(PREF_KEY_VIS_BAR_CUSTOM_COLOR, 0xFF6BD8FF.toInt())
                }
                PREF_KEY_VIS_BAR_FREQUENCY_GRID_ENABLED -> {
                    state.barFrequencyGridEnabled = sharedPrefs.getBoolean(
                        PREF_KEY_VIS_BAR_FREQUENCY_GRID_ENABLED,
                        AppDefaults.Visualization.Bars.frequencyGridEnabled
                    )
                }
                PREF_KEY_VIS_BAR_FPS_MODE -> {
                    state.barFpsMode = VisualizationOscFpsMode.fromStorage(
                        sharedPrefs.getString(
                            PREF_KEY_VIS_BAR_FPS_MODE,
                            AppDefaults.Visualization.Bars.fpsMode.storageValue
                        )
                    )
                }
                PREF_KEY_VIS_BAR_RENDER_BACKEND -> {
                    state.barRuntimeRenderBackend = VisualizationRenderBackend.fromStorage(
                        sharedPrefs.getString(
                            PREF_KEY_VIS_BAR_RENDER_BACKEND,
                            defaultBarRenderBackend.storageValue
                        ),
                        defaultBarRenderBackend
                    )
                }
                PREF_KEY_VIS_VU_COLOR_NO_ARTWORK -> {
                    state.vuColorModeNoArtwork = VisualizationOscColorMode.fromStorage(
                        sharedPrefs.getString(
                            PREF_KEY_VIS_VU_COLOR_NO_ARTWORK,
                            VisualizationOscColorMode.Monet.storageValue
                        ),
                        VisualizationOscColorMode.Monet
                    )
                }
                PREF_KEY_VIS_VU_COLOR_WITH_ARTWORK -> {
                    state.vuColorModeWithArtwork = VisualizationOscColorMode.fromStorage(
                        sharedPrefs.getString(
                            PREF_KEY_VIS_VU_COLOR_WITH_ARTWORK,
                            VisualizationOscColorMode.Artwork.storageValue
                        ),
                        VisualizationOscColorMode.Artwork
                    )
                }
                PREF_KEY_VIS_VU_CUSTOM_COLOR -> {
                    state.vuCustomColorArgb =
                        sharedPrefs.getInt(PREF_KEY_VIS_VU_CUSTOM_COLOR, 0xFF6BD8FF.toInt())
                }
                PREF_KEY_VIS_VU_FPS_MODE -> {
                    state.vuFpsMode = VisualizationOscFpsMode.fromStorage(
                        sharedPrefs.getString(
                            PREF_KEY_VIS_VU_FPS_MODE,
                            AppDefaults.Visualization.Vu.fpsMode.storageValue
                        )
                    )
                }
                PREF_KEY_VIS_VU_RENDER_BACKEND -> {
                    state.vuRuntimeRenderBackend = VisualizationRenderBackend.fromStorage(
                        sharedPrefs.getString(
                            PREF_KEY_VIS_VU_RENDER_BACKEND,
                            defaultVuRenderBackend.storageValue
                        ),
                        defaultVuRenderBackend
                    )
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    return state
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerScreen(
    file: File?,
    onBack: () -> Unit,
    onCollapseBySwipe: () -> Unit = onBack,
    enableCollapseGesture: Boolean = true,
    isPlaying: Boolean,
    canResumeStoppedTrack: Boolean = false,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStopAndClear: () -> Unit,
    canPreviousTrack: Boolean,
    canNextTrack: Boolean,
    durationSeconds: Double,
    positionSeconds: Double,
    title: String,
    artist: String,
    sampleRateHz: Int,
    channelCount: Int,
    bitDepthLabel: String,
    decoderName: String?,
    playbackSourceLabel: String? = null,
    pathOrUrl: String? = null,
    artwork: ImageBitmap?,
    noArtworkIcon: ImageVector = Icons.Default.MusicNote,
    repeatMode: RepeatMode,
    canCycleRepeatMode: Boolean,
    canSeek: Boolean,
    hasReliableDuration: Boolean,
    playbackStartInProgress: Boolean = false,
    seekInProgress: Boolean = false,
    onSeek: (Double) -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onPreviousSubtune: () -> Unit,
    onNextSubtune: () -> Unit,
    onOpenSubtuneSelector: () -> Unit,
    canPreviousSubtune: Boolean,
    canNextSubtune: Boolean,
    canOpenSubtuneSelector: Boolean,
    currentSubtuneIndex: Int = 0,
    subtuneCount: Int = 0,
    onCycleRepeatMode: () -> Unit,
    canOpenCoreSettings: Boolean,
    onOpenCoreSettings: () -> Unit,
    visualizationMode: VisualizationMode,
    availableVisualizationModes: List<VisualizationMode>,
    onCycleVisualizationMode: () -> Unit,
    onSelectVisualizationMode: (VisualizationMode) -> Unit,
    onOpenVisualizationSettings: () -> Unit,
    onOpenSelectedVisualizationSettings: () -> Unit,
    visualizationBarCount: Int,
    visualizationBarSmoothingPercent: Int,
    visualizationBarRoundnessDp: Int,
    visualizationBarOverlayArtwork: Boolean,
    visualizationBarUseThemeColor: Boolean,
    visualizationBarRenderBackend: VisualizationRenderBackend,
    visualizationOscStereo: Boolean,
    visualizationVuAnchor: VisualizationVuAnchor,
    visualizationVuUseThemeColor: Boolean,
    visualizationVuSmoothingPercent: Int,
    visualizationVuRenderBackend: VisualizationRenderBackend,
    visualizationShowDebugInfo: Boolean = false,
    artworkCornerRadiusDp: Int = 3,
    onOpenAudioEffects: () -> Unit,
    filenameDisplayMode: com.flopster101.siliconplayer.FilenameDisplayMode = com.flopster101.siliconplayer.FilenameDisplayMode.Always,
    filenameOnlyWhenTitleMissing: Boolean = false
) {
    var sliderPosition by remember(file?.absolutePath, durationSeconds) {
        mutableDoubleStateOf(positionSeconds.coerceIn(0.0, durationSeconds.coerceAtLeast(0.0)))
    }
    var isSeeking by remember { mutableStateOf(false) }
    var isTimelineTouchActive by remember { mutableStateOf(false) }
    var downwardDragPx by remember { mutableFloatStateOf(0f) }
    var isDraggingDown by remember { mutableStateOf(false) }
    var showTrackInfoDialog by remember { mutableStateOf(false) }
    var showVisualizationPickerDialog by remember { mutableStateOf(false) }
    var showChannelControlDialog by remember { mutableStateOf(false) }
    var showVisualizationModeBadge by remember { mutableStateOf(false) }
    var visualizationModeBadgeText by remember { mutableStateOf(visualizationMode.label) }
    var lastVisualizationModeForBadge by remember { mutableStateOf<VisualizationMode?>(null) }
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("silicon_player_settings", Context.MODE_PRIVATE)
    }
    val visualizationPrefsState = rememberPlayerVisualizationPreferenceState(
        prefs = prefs,
        defaultBarRenderBackend = visualizationBarRenderBackend,
        defaultVuRenderBackend = visualizationVuRenderBackend
    )
    val channelScopePrefs = rememberChannelScopePrefs(prefs)
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val isTabletLike = configuration.smallestScreenWidthDp >= 600
    val collapseThresholdPx = with(density) { 132.dp.toPx() }

    LaunchedEffect(positionSeconds, isSeeking) {
        if (!isSeeking) {
            sliderPosition = positionSeconds.coerceIn(0.0, durationSeconds.coerceAtLeast(0.0))
        }
    }
    val animatedPanelOffsetPx by animateFloatAsState(
        targetValue = if (isDraggingDown) downwardDragPx else 0f,
        animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing),
        label = "playerCollapseDragOffset"
    )
    val animatedSliderPosition by animateFloatAsState(
        targetValue = sliderPosition.toFloat(),
        animationSpec = tween(durationMillis = 90, easing = LinearOutSlowInEasing),
        label = "playerTimelinePosition"
    )
    val panelOffsetPx = if (isDraggingDown) downwardDragPx else animatedPanelOffsetPx
    val dragFadeProgress = (panelOffsetPx / (collapseThresholdPx * 1.4f)).coerceIn(0f, 1f)
    val panelAlpha = 1f - (0.22f * dragFadeProgress)
    val topArrowFocusRequester = remember { FocusRequester() }
    val infoChipsFocusRequester = remember { FocusRequester() }

    val hasTrack = file != null
    val remoteLoadUiState = RemoteLoadUiStateHolder.current
    val sanitizedTitle = sanitizeRemoteCachedMetadataTitle(title, file)
    val displayTitle = sanitizedTitle.ifBlank {
        if (file != null) inferredDisplayTitleForName(file.name) else "No track selected"
    }
    val displayArtist = artist.ifBlank { if (hasTrack) "Unknown Artist" else "Tap a file to play" }
    val displayFilename = file?.let { toDisplayFilename(it) } ?: "No file loaded"
    LaunchedEffect(visualizationMode) {
        val previous = lastVisualizationModeForBadge
        lastVisualizationModeForBadge = visualizationMode
        if (previous == null || previous == visualizationMode) return@LaunchedEffect
        visualizationModeBadgeText = visualizationMode.label
        showVisualizationModeBadge = true
        delay(1200)
        showVisualizationModeBadge = false
    }
    val transportAnchorFocusRequester = remember { FocusRequester() }
    val actionStripFirstFocusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                // Only handle key down events to avoid double-triggering
                if (keyEvent.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) {
                    return@onPreviewKeyEvent false
                }
                handlePlayerGlobalKeyDown(
                    keyEvent = keyEvent,
                    hasTrack = hasTrack,
                    canResumeStoppedTrack = canResumeStoppedTrack,
                    isPlaying = isPlaying,
                    canPreviousSubtune = canPreviousSubtune,
                    canNextSubtune = canNextSubtune,
                    canPreviousTrack = canPreviousTrack,
                    canNextTrack = canNextTrack,
                    canSeek = canSeek,
                    durationSeconds = durationSeconds,
                    canCycleRepeatMode = canCycleRepeatMode,
                    onPlay = onPlay,
                    onPause = onPause,
                    onPreviousSubtune = onPreviousSubtune,
                    onNextSubtune = onNextSubtune,
                    onPreviousTrack = onPreviousTrack,
                    onNextTrack = onNextTrack,
                    onSeek = onSeek,
                    onCycleRepeatMode = onCycleRepeatMode,
                    onStopAndClear = onStopAndClear
                )
            }
            .offset { IntOffset(0, panelOffsetPx.roundToInt()) }
            .graphicsLayer(alpha = panelAlpha)
            .then(
                if (enableCollapseGesture) {
                    Modifier.pointerInput(collapseThresholdPx, isTimelineTouchActive) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                if (isTimelineTouchActive) return@detectVerticalDragGestures
                                val next = (downwardDragPx + dragAmount).coerceAtLeast(0f)
                                if (next > 0f || downwardDragPx > 0f) {
                                    isDraggingDown = true
                                    downwardDragPx = next
                                    change.consume()
                                }
                            },
                            onDragEnd = {
                                if (isTimelineTouchActive) return@detectVerticalDragGestures
                                val shouldCollapse = downwardDragPx >= collapseThresholdPx
                                if (shouldCollapse) {
                                    onCollapseBySwipe()
                                } else {
                                    isDraggingDown = false
                                    downwardDragPx = 0f
                                }
                            },
                            onDragCancel = {
                                if (isTimelineTouchActive) return@detectVerticalDragGestures
                                isDraggingDown = false
                                downwardDragPx = 0f
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Scaffold(
            topBar = {
                PlayerTopBar(
                    isLandscape = isLandscape,
                    isTabletLike = isTabletLike,
                    onBack = onBack,
                    enableCollapseGesture = enableCollapseGesture,
                    focusRequester = topArrowFocusRequester,
                    downFocusRequester = infoChipsFocusRequester
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                            )
                        )
                    )
            ) {
            if (isLandscape) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val landscapeWidthScale = normalizedScale(maxWidth, compactDp = 640.dp, roomyDp = 1280.dp)
                    val landscapeHeightScale = normalizedScale(maxHeight, compactDp = 320.dp, roomyDp = 720.dp)
                    val landscapeLayoutScale = (landscapeHeightScale * 0.65f + landscapeWidthScale * 0.35f)
                        .coerceIn(0f, 1f)
                    val horizontalPadding = lerpDp(10.dp, 16.dp, landscapeLayoutScale)
                    val verticalPadding = lerpDp(6.dp, 12.dp, landscapeLayoutScale)
                    val paneGap = lerpDp(12.dp, 20.dp, landscapeLayoutScale)
                    val artPaneWeight = lerpFloat(0.36f, 0.48f, landscapeLayoutScale)
                    val rightPaneWeight = 1f - artPaneWeight
                    val timelineWidthFraction = lerpFloat(0.88f, 0.96f, landscapeLayoutScale)
                    val actionStripWidthFraction = lerpFloat(0.86f, 0.94f, landscapeLayoutScale)
                    val chipSpacer = lerpDp(4.dp, 10.dp, landscapeLayoutScale)
                    val metadataSpacer = lerpDp(6.dp, 12.dp, landscapeLayoutScale)
                    val timelineSpacer = lerpDp(4.dp, 10.dp, landscapeLayoutScale)
                    val actionStripSpacer = lerpDp(6.dp, 12.dp, landscapeLayoutScale)
                    val actionStripBottomPadding = lerpDp(2.dp, 4.dp, landscapeLayoutScale)
                    val timelineFocusRequester = remember { FocusRequester() }

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                        horizontalArrangement = Arrangement.spacedBy(paneGap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(artPaneWeight)
                                .fillMaxHeight()
                        ) {
                            val artMaxByHeight = this.maxHeight * lerpFloat(0.90f, 0.95f, landscapeLayoutScale)
                            val artSize = minOf(this.maxWidth, artMaxByHeight).coerceAtLeast(120.dp)
                            AlbumArtPlaceholder(
                                file = file,
                                isPlaying = isPlaying && !seekInProgress,
                                decoderName = decoderName,
                                sampleRateHz = sampleRateHz,
                                artwork = artwork,
                                placeholderIcon = noArtworkIcon,
                                visualizationModeBadgeText = visualizationModeBadgeText,
                                showVisualizationModeBadge = showVisualizationModeBadge,
                                visualizationMode = visualizationMode,
                                visualizationShowDebugInfo = visualizationShowDebugInfo,
                                visualizationOscWindowMs = visualizationPrefsState.oscWindowMs,
                                visualizationOscTriggerModeNative = visualizationPrefsState.oscTriggerModeNative,
                                visualizationOscFpsMode = visualizationPrefsState.oscFpsMode,
                                visualizationBarFpsMode = visualizationPrefsState.barFpsMode,
                                visualizationVuFpsMode = visualizationPrefsState.vuFpsMode,
                                visualizationOscRenderBackend = visualizationPrefsState.oscRenderBackend,
                                visualizationBarSmoothingPercent = visualizationBarSmoothingPercent,
                                visualizationVuSmoothingPercent = visualizationVuSmoothingPercent,
                                barCount = visualizationBarCount,
                                barRoundnessDp = visualizationBarRoundnessDp,
                                barOverlayArtwork = visualizationBarOverlayArtwork,
                                barUseThemeColor = visualizationBarUseThemeColor,
                                barFrequencyGridEnabled = visualizationPrefsState.barFrequencyGridEnabled,
                                barRenderBackend = visualizationPrefsState.barRuntimeRenderBackend,
                                barColorModeNoArtwork = visualizationPrefsState.barColorModeNoArtwork,
                                barColorModeWithArtwork = visualizationPrefsState.barColorModeWithArtwork,
                                barCustomColorArgb = visualizationPrefsState.barCustomColorArgb,
                                oscStereo = visualizationOscStereo,
                                oscLineWidthDp = visualizationPrefsState.oscLineWidthDp,
                                oscGridWidthDp = visualizationPrefsState.oscGridWidthDp,
                                oscVerticalGridEnabled = visualizationPrefsState.oscVerticalGridEnabled,
                                oscCenterLineEnabled = visualizationPrefsState.oscCenterLineEnabled,
                                oscLineColorModeNoArtwork = visualizationPrefsState.oscLineColorModeNoArtwork,
                                oscGridColorModeNoArtwork = visualizationPrefsState.oscGridColorModeNoArtwork,
                                oscLineColorModeWithArtwork = visualizationPrefsState.oscLineColorModeWithArtwork,
                                oscGridColorModeWithArtwork = visualizationPrefsState.oscGridColorModeWithArtwork,
                                oscCustomLineColorArgb = visualizationPrefsState.oscCustomLineColorArgb,
                                oscCustomGridColorArgb = visualizationPrefsState.oscCustomGridColorArgb,
                                vuAnchor = visualizationVuAnchor,
                                vuUseThemeColor = visualizationVuUseThemeColor,
                                vuRenderBackend = visualizationPrefsState.vuRuntimeRenderBackend,
                                vuColorModeNoArtwork = visualizationPrefsState.vuColorModeNoArtwork,
                                vuColorModeWithArtwork = visualizationPrefsState.vuColorModeWithArtwork,
                                vuCustomColorArgb = visualizationPrefsState.vuCustomColorArgb,
                                channelScopePrefs = channelScopePrefs,
                                artworkCornerRadiusDp = artworkCornerRadiusDp,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(artSize)
                            )
                        }

                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(rightPaneWeight)
                                .fillMaxHeight()
                        ) {
                            var actionStripHeightPx by remember { mutableIntStateOf(0) }
                            val actionStripHeightDp = with(density) { actionStripHeightPx.toDp() }
                            val centerLandscapeContent = landscapeLayoutScale >= 0.84f
                            val showLandscapeFilename = landscapeLayoutScale >= 0.74f

                            Column(
                                modifier = Modifier
                                    .align(if (centerLandscapeContent) Alignment.Center else Alignment.TopCenter)
                                    .fillMaxWidth(if (centerLandscapeContent) 0.98f else 1f)
                                    .padding(
                                        bottom = if (centerLandscapeContent) 0.dp
                                        else actionStripHeightDp + actionStripSpacer + actionStripBottomPadding
                                    ),
                                verticalArrangement = if (centerLandscapeContent) Arrangement.Center else Arrangement.Top,
                                horizontalAlignment = if (centerLandscapeContent) Alignment.CenterHorizontally else Alignment.Start
                            ) {
                                TrackInfoChips(
                                    file = file,
                                    decoderName = decoderName,
                                    fileSizeBytes = file?.length() ?: 0L,
                                    sampleRateHz = sampleRateHz,
                                    channelCount = channelCount,
                                    bitDepthLabel = bitDepthLabel,
                                    focusRequester = infoChipsFocusRequester,
                                    upFocusRequester = topArrowFocusRequester,
                                    downFocusRequester = if (canSeek && durationSeconds > 0.0) {
                                        timelineFocusRequester
                                    } else {
                                        transportAnchorFocusRequester
                                    },
                                    layoutScale = landscapeLayoutScale,
                                    onClick = { showTrackInfoDialog = true }
                                )
                                Spacer(modifier = Modifier.height(chipSpacer))
                                TrackMetadataBlock(
                                    title = displayTitle,
                                    artist = displayArtist,
                                    filename = displayFilename,
                                    filenameDisplayMode = filenameDisplayMode,
                                    decoderName = decoderName,
                                    filenameOnlyWhenTitleMissing = filenameOnlyWhenTitleMissing,
                                    showFilename = showLandscapeFilename,
                                    centerSupportingMetadata = isLandscape,
                                    currentSubtuneIndex = currentSubtuneIndex,
                                    subtuneCount = subtuneCount,
                                    layoutScale = landscapeLayoutScale
                                )
                                Spacer(modifier = Modifier.height(metadataSpacer))
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .fillMaxWidth(timelineWidthFraction),
                                    contentAlignment = Alignment.Center
                                ) {
                                    TimelineSection(
                                        sliderPosition = if (isSeeking) sliderPosition else animatedSliderPosition.toDouble(),
                                        elapsedPositionSeconds = if (isSeeking) sliderPosition else positionSeconds,
                                        durationSeconds = durationSeconds,
                                        canSeek = canSeek,
                                        hasReliableDuration = hasReliableDuration,
                                        seekInProgress = seekInProgress,
                                        focusRequester = timelineFocusRequester,
                                        upFocusRequester = infoChipsFocusRequester,
                                        layoutScale = landscapeLayoutScale,
                                        onSeekInteractionChanged = { isTimelineTouchActive = it },
                                        onSliderValueChange = { value ->
                                            isSeeking = true
                                            val sliderMax = durationSeconds.coerceAtLeast(0.0)
                                            sliderPosition = value.toDouble().coerceIn(0.0, sliderMax)
                                        },
                                        onSliderValueChangeFinished = {
                                            isSeeking = false
                                            if (canSeek && durationSeconds > 0.0) {
                                                onSeek(sliderPosition)
                                            }
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.height(timelineSpacer))
                                TransportControls(
                                    hasTrack = hasTrack,
                                    isPlaying = isPlaying,
                                    canResumeStoppedTrack = canResumeStoppedTrack,
                                    repeatMode = repeatMode,
                                    playbackStartInProgress = playbackStartInProgress,
                                    remoteLoadUiState = remoteLoadUiState,
                                    seekInProgress = seekInProgress,
                                    canPreviousTrack = canPreviousTrack,
                                    canNextTrack = canNextTrack,
                                    canCycleRepeatMode = canCycleRepeatMode,
                                    onPlayPause = {
                                        if (isPlaying) {
                                            onPause()
                                        } else {
                                            onPlay()
                                        }
                                    },
                                    onPreviousTrack = onPreviousTrack,
                                    onNextTrack = onNextTrack,
                                    onPreviousSubtune = onPreviousSubtune,
                                    onNextSubtune = onNextSubtune,
                                    onOpenSubtuneSelector = onOpenSubtuneSelector,
                                    canPreviousSubtune = canPreviousSubtune,
                                    canNextSubtune = canNextSubtune,
                                    canOpenSubtuneSelector = canOpenSubtuneSelector,
                                    onStopAndClear = onStopAndClear,
                                    onCycleRepeatMode = onCycleRepeatMode,
                                    layoutScale = landscapeLayoutScale,
                                    transportAnchorFocusRequester = transportAnchorFocusRequester,
                                    actionStripFirstFocusRequester = actionStripFirstFocusRequester
                                )

                                if (centerLandscapeContent) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    FutureActionStrip(
                                        modifier = Modifier.fillMaxWidth(actionStripWidthFraction),
                                        canOpenCoreSettings = canOpenCoreSettings,
                                        onOpenCoreSettings = onOpenCoreSettings,
                                        onCycleVisualizationMode = onCycleVisualizationMode,
                                        onOpenVisualizationPicker = { showVisualizationPickerDialog = true },
                                        onOpenAudioEffects = onOpenAudioEffects,
                                        onOpenChannelControls = { showChannelControlDialog = true },
                                        layoutScale = landscapeLayoutScale,
                                        actionStripFirstFocusRequester = actionStripFirstFocusRequester,
                                        transportAnchorFocusRequester = transportAnchorFocusRequester
                                    )
                                }
                            }

                            if (!centerLandscapeContent) {
                                FutureActionStrip(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth(actionStripWidthFraction)
                                        .onSizeChanged { actionStripHeightPx = it.height }
                                        .navigationBarsPadding()
                                        .padding(bottom = actionStripBottomPadding),
                                    canOpenCoreSettings = canOpenCoreSettings,
                                    onOpenCoreSettings = onOpenCoreSettings,
                                    onCycleVisualizationMode = onCycleVisualizationMode,
                                    onOpenVisualizationPicker = { showVisualizationPickerDialog = true },
                                    onOpenAudioEffects = onOpenAudioEffects,
                                    onOpenChannelControls = { showChannelControlDialog = true },
                                    layoutScale = landscapeLayoutScale,
                                    actionStripFirstFocusRequester = actionStripFirstFocusRequester,
                                    transportAnchorFocusRequester = transportAnchorFocusRequester
                                )
                            }
                        }
                    }
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val portraitWidthScale = normalizedScale(maxWidth, compactDp = 320.dp, roomyDp = 500.dp)
                    val portraitHeightScale = normalizedScale(maxHeight, compactDp = 560.dp, roomyDp = 900.dp)
                    val portraitLayoutScale = (portraitHeightScale * 0.7f + portraitWidthScale * 0.3f)
                        .coerceIn(0f, 1f)
                    val chipWidthScale = normalizedScale(maxWidth, compactDp = 340.dp, roomyDp = 760.dp)
                    val chipTabletWidthScale = normalizedScale(maxWidth, compactDp = 440.dp, roomyDp = 900.dp)
                    val chipScaleForEstimate = (portraitLayoutScale * 0.70f + chipWidthScale * 0.30f).coerceIn(0f, 1f)
                    val visualChipScaleForEstimate = (
                        chipScaleForEstimate +
                            lerpFloat(0f, 0.08f, portraitLayoutScale) +
                            lerpFloat(0f, 0.16f, chipTabletWidthScale)
                        ).coerceIn(0f, 1f)
                    val estimatedChipHeight = (
                        lerpDp(22.dp, 36.dp, visualChipScaleForEstimate) +
                            lerpDp(0.dp, 14.dp, chipTabletWidthScale)
                        ).coerceAtLeast(26.dp)
                    val horizontalPadding = lerpDp(10.dp, 20.dp, portraitLayoutScale)
                    val verticalPadding = lerpDp(8.dp, 12.dp, portraitLayoutScale)
                    val artWidthFraction = (
                        lerpFloat(0.52f, 0.86f, portraitLayoutScale) *
                            lerpFloat(0.95f, 1.08f, portraitWidthScale) *
                            lerpFloat(0.98f, 1.05f, portraitHeightScale)
                        ).coerceIn(0.50f, 0.90f)
                    val artworkToInfoGap = lerpDp(8.dp, 12.dp, portraitLayoutScale)
                    val metadataSpacer = lerpDp(6.dp, 12.dp, portraitLayoutScale)
                    val timelineSpacer = lerpDp(4.dp, 10.dp, portraitLayoutScale)
                    val actionStripSpacer = lerpDp(4.dp, 14.dp, portraitLayoutScale)
                    val actionStripWidth = lerpFloat(0.83f, 0.93f, portraitLayoutScale)
                    val actionStripBottomPadding = lerpDp(2.dp, 4.dp, portraitLayoutScale)
                    val topPaneWeight = lerpFloat(0.48f, 0.58f, portraitLayoutScale)
                    val bottomPaneWeight = (1f - topPaneWeight).coerceAtLeast(0.34f)
                    val metadataSplitGap = lerpDp(2.dp, 8.dp, portraitLayoutScale)
                    val timelineFocusRequester = remember { FocusRequester() }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                    ) {
                        var actionStripHeightPx by remember { mutableIntStateOf(0) }
                        val actionStripHeightDp = with(density) { actionStripHeightPx.toDp() }
                        val minArtworkSize = lerpDp(128.dp, 240.dp, portraitLayoutScale)
                        val contentAvailableHeight = (
                            this@BoxWithConstraints.maxHeight -
                                actionStripHeightDp -
                                actionStripSpacer -
                                actionStripBottomPadding
                            ).coerceAtLeast(minArtworkSize)
                        val topPaneTargetHeight = (contentAvailableHeight * topPaneWeight).coerceAtLeast(minArtworkSize)
                        val chipBlockHeightEstimate = estimatedChipHeight + lerpDp(6.dp, 14.dp, portraitLayoutScale)
                        val maxArtworkByWidth = this@BoxWithConstraints.maxWidth * artWidthFraction
                        val maxArtworkByHeight = (
                            topPaneTargetHeight -
                                chipBlockHeightEstimate -
                                artworkToInfoGap
                            ).coerceAtLeast(minArtworkSize)
                        val artworkSize = minOf(maxArtworkByWidth, maxArtworkByHeight).coerceAtLeast(minArtworkSize)

                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxHeight()
                                .fillMaxWidth()
                                .padding(
                                    bottom = actionStripHeightDp + actionStripSpacer + actionStripBottomPadding
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(topPaneWeight, fill = true),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    AlbumArtPlaceholder(
                                        file = file,
                                        isPlaying = isPlaying && !seekInProgress,
                                        decoderName = decoderName,
                                        sampleRateHz = sampleRateHz,
                                        artwork = artwork,
                                        placeholderIcon = noArtworkIcon,
                                        visualizationModeBadgeText = visualizationModeBadgeText,
                                        showVisualizationModeBadge = showVisualizationModeBadge,
                                        visualizationMode = visualizationMode,
                                        visualizationShowDebugInfo = visualizationShowDebugInfo,
                                        visualizationOscWindowMs = visualizationPrefsState.oscWindowMs,
                                        visualizationOscTriggerModeNative = visualizationPrefsState.oscTriggerModeNative,
                                        visualizationOscFpsMode = visualizationPrefsState.oscFpsMode,
                                        visualizationBarFpsMode = visualizationPrefsState.barFpsMode,
                                        visualizationVuFpsMode = visualizationPrefsState.vuFpsMode,
                                        visualizationOscRenderBackend = visualizationPrefsState.oscRenderBackend,
                                        visualizationBarSmoothingPercent = visualizationBarSmoothingPercent,
                                        visualizationVuSmoothingPercent = visualizationVuSmoothingPercent,
                                        barCount = visualizationBarCount,
                                        barRoundnessDp = visualizationBarRoundnessDp,
                                        barOverlayArtwork = visualizationBarOverlayArtwork,
                                        barUseThemeColor = visualizationBarUseThemeColor,
                                        barFrequencyGridEnabled = visualizationPrefsState.barFrequencyGridEnabled,
                                        barRenderBackend = visualizationPrefsState.barRuntimeRenderBackend,
                                        barColorModeNoArtwork = visualizationPrefsState.barColorModeNoArtwork,
                                        barColorModeWithArtwork = visualizationPrefsState.barColorModeWithArtwork,
                                        barCustomColorArgb = visualizationPrefsState.barCustomColorArgb,
                                        oscStereo = visualizationOscStereo,
                                        oscLineWidthDp = visualizationPrefsState.oscLineWidthDp,
                                        oscGridWidthDp = visualizationPrefsState.oscGridWidthDp,
                                        oscVerticalGridEnabled = visualizationPrefsState.oscVerticalGridEnabled,
                                        oscCenterLineEnabled = visualizationPrefsState.oscCenterLineEnabled,
                                        oscLineColorModeNoArtwork = visualizationPrefsState.oscLineColorModeNoArtwork,
                                        oscGridColorModeNoArtwork = visualizationPrefsState.oscGridColorModeNoArtwork,
                                        oscLineColorModeWithArtwork = visualizationPrefsState.oscLineColorModeWithArtwork,
                                        oscGridColorModeWithArtwork = visualizationPrefsState.oscGridColorModeWithArtwork,
                                        oscCustomLineColorArgb = visualizationPrefsState.oscCustomLineColorArgb,
                                        oscCustomGridColorArgb = visualizationPrefsState.oscCustomGridColorArgb,
                                        vuAnchor = visualizationVuAnchor,
                                        vuUseThemeColor = visualizationVuUseThemeColor,
                                        vuRenderBackend = visualizationPrefsState.vuRuntimeRenderBackend,
                                        vuColorModeNoArtwork = visualizationPrefsState.vuColorModeNoArtwork,
                                        vuColorModeWithArtwork = visualizationPrefsState.vuColorModeWithArtwork,
                                        vuCustomColorArgb = visualizationPrefsState.vuCustomColorArgb,
                                        channelScopePrefs = channelScopePrefs,
                                        artworkCornerRadiusDp = artworkCornerRadiusDp,
                                        modifier = Modifier.size(artworkSize)
                                    )
                                    Spacer(modifier = Modifier.height(artworkToInfoGap))
                                    TrackInfoChips(
                                        file = file,
                                        decoderName = decoderName,
                                        fileSizeBytes = file?.length() ?: 0L,
                                        sampleRateHz = sampleRateHz,
                                        channelCount = channelCount,
                                        bitDepthLabel = bitDepthLabel,
                                        focusRequester = infoChipsFocusRequester,
                                        upFocusRequester = topArrowFocusRequester,
                                        downFocusRequester = if (canSeek && durationSeconds > 0.0) {
                                            timelineFocusRequester
                                        } else {
                                            transportAnchorFocusRequester
                                        },
                                        layoutScale = portraitLayoutScale,
                                        onClick = { showTrackInfoDialog = true },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(metadataSplitGap))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(bottomPaneWeight, fill = true),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Top
                                ) {
                                    TrackMetadataBlock(
                                        title = displayTitle,
                                        artist = displayArtist,
                                        filename = displayFilename,
                                        filenameDisplayMode = filenameDisplayMode,
                                        decoderName = decoderName,
                                        filenameOnlyWhenTitleMissing = filenameOnlyWhenTitleMissing,
                                        currentSubtuneIndex = currentSubtuneIndex,
                                        subtuneCount = subtuneCount,
                                        layoutScale = portraitLayoutScale
                                    )
                                    Spacer(modifier = Modifier.height(metadataSpacer))
                                    TimelineSection(
                                        sliderPosition = if (isSeeking) sliderPosition else animatedSliderPosition.toDouble(),
                                        elapsedPositionSeconds = if (isSeeking) sliderPosition else positionSeconds,
                                        durationSeconds = durationSeconds,
                                        canSeek = canSeek,
                                        hasReliableDuration = hasReliableDuration,
                                        seekInProgress = seekInProgress,
                                        focusRequester = timelineFocusRequester,
                                        upFocusRequester = infoChipsFocusRequester,
                                        layoutScale = portraitLayoutScale,
                                        onSeekInteractionChanged = { isTimelineTouchActive = it },
                                        onSliderValueChange = { value ->
                                            isSeeking = true
                                            val sliderMax = durationSeconds.coerceAtLeast(0.0)
                                            sliderPosition = value.toDouble().coerceIn(0.0, sliderMax)
                                        },
                                        onSliderValueChangeFinished = {
                                            isSeeking = false
                                            if (canSeek && durationSeconds > 0.0) {
                                                onSeek(sliderPosition)
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(timelineSpacer))
                                    TransportControls(
                                        hasTrack = hasTrack,
                                        isPlaying = isPlaying,
                                        canResumeStoppedTrack = canResumeStoppedTrack,
                                        repeatMode = repeatMode,
                                        playbackStartInProgress = playbackStartInProgress,
                                        remoteLoadUiState = remoteLoadUiState,
                                        seekInProgress = seekInProgress,
                                        canPreviousTrack = canPreviousTrack,
                                        canNextTrack = canNextTrack,
                                        canCycleRepeatMode = canCycleRepeatMode,
                                        onPlayPause = {
                                            if (isPlaying) {
                                                onPause()
                                            } else {
                                                onPlay()
                                            }
                                        },
                                        onPreviousTrack = onPreviousTrack,
                                        onNextTrack = onNextTrack,
                                        onPreviousSubtune = onPreviousSubtune,
                                        onNextSubtune = onNextSubtune,
                                        onOpenSubtuneSelector = onOpenSubtuneSelector,
                                        canPreviousSubtune = canPreviousSubtune,
                                        canNextSubtune = canNextSubtune,
                                        canOpenSubtuneSelector = canOpenSubtuneSelector,
                                        onStopAndClear = onStopAndClear,
                                        onCycleRepeatMode = onCycleRepeatMode,
                                        layoutScale = portraitLayoutScale,
                                        transportAnchorFocusRequester = transportAnchorFocusRequester,
                                        actionStripFirstFocusRequester = actionStripFirstFocusRequester
                                    )
                                }
                            }
                        }

                        FutureActionStrip(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth(actionStripWidth)
                                .onSizeChanged { actionStripHeightPx = it.height }
                                .navigationBarsPadding()
                                .padding(bottom = actionStripBottomPadding),
                            canOpenCoreSettings = canOpenCoreSettings,
                            onOpenCoreSettings = onOpenCoreSettings,
                            onCycleVisualizationMode = onCycleVisualizationMode,
                            onOpenVisualizationPicker = { showVisualizationPickerDialog = true },
                            onOpenAudioEffects = onOpenAudioEffects,
                            onOpenChannelControls = { showChannelControlDialog = true },
                            layoutScale = portraitLayoutScale,
                            actionStripFirstFocusRequester = actionStripFirstFocusRequester,
                            transportAnchorFocusRequester = transportAnchorFocusRequester
                        )
                    }
                }
            }
        }
    }
    if (showTrackInfoDialog) {
        TrackInfoDetailsDialog(
            file = file,
            title = displayTitle,
            artist = displayArtist,
            decoderName = decoderName,
            isDialogVisible = showTrackInfoDialog,
            playbackSourceLabel = playbackSourceLabel,
            pathOrUrl = pathOrUrl,
            sampleRateHz = sampleRateHz,
            channelCount = channelCount,
            bitDepthLabel = bitDepthLabel,
            durationSeconds = durationSeconds,
            hasReliableDuration = hasReliableDuration,
            onDismiss = { showTrackInfoDialog = false }
        )
    }
    if (showVisualizationPickerDialog) {
        VisualizationModePickerDialog(
            availableModes = availableVisualizationModes,
            selectedMode = visualizationMode,
            onSelectMode = onSelectVisualizationMode,
            onOpenSelectedVisualizationSettings = onOpenSelectedVisualizationSettings,
            onOpenVisualizationSettings = onOpenVisualizationSettings,
            onDismiss = { showVisualizationPickerDialog = false }
        )
    }
    if (showChannelControlDialog) {
        ChannelControlDialog(
            onDismiss = { showChannelControlDialog = false }
        )
    }
}
}

private fun handlePlayerGlobalKeyDown(
    keyEvent: androidx.compose.ui.input.key.KeyEvent,
    hasTrack: Boolean,
    canResumeStoppedTrack: Boolean,
    isPlaying: Boolean,
    canPreviousSubtune: Boolean,
    canNextSubtune: Boolean,
    canPreviousTrack: Boolean,
    canNextTrack: Boolean,
    canSeek: Boolean,
    durationSeconds: Double,
    canCycleRepeatMode: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onPreviousSubtune: () -> Unit,
    onNextSubtune: () -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onSeek: (Double) -> Unit,
    onCycleRepeatMode: () -> Unit,
    onStopAndClear: () -> Unit
): Boolean {
    return when (keyEvent.key) {
        Key.Spacebar -> {
            if (hasTrack || canResumeStoppedTrack) {
                if (isPlaying) onPause() else onPlay()
                true
            } else false
        }
        Key.DirectionLeft -> {
            if (keyEvent.isCtrlPressed && canPreviousSubtune) {
                onPreviousSubtune()
                true
            } else false
        }
        Key.DirectionRight -> {
            if (keyEvent.isCtrlPressed && canNextSubtune) {
                onNextSubtune()
                true
            } else false
        }
        Key.PageUp -> {
            if (hasTrack && canPreviousTrack) {
                onPreviousTrack()
                true
            } else false
        }
        Key.PageDown -> {
            if (hasTrack && canNextTrack) {
                onNextTrack()
                true
            } else false
        }
        Key.MoveHome -> {
            if (canSeek && durationSeconds > 0.0) {
                onSeek(0.0)
                true
            } else false
        }
        Key.R -> {
            if (canCycleRepeatMode) {
                onCycleRepeatMode()
                true
            } else false
        }
        Key.Backspace -> {
            onStopAndClear()
            true
        }
        else -> false
    }
}

@Composable
private fun PlayerTopBar(
    isLandscape: Boolean,
    isTabletLike: Boolean,
    onBack: () -> Unit,
    enableCollapseGesture: Boolean,
    focusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null
) {
    val compactLandscapeHeader = isLandscape && !isTabletLike
    val headerHeight = if (compactLandscapeHeader) 30.dp else 40.dp
    val navButtonSize = if (compactLandscapeHeader) 28.dp else 32.dp
    val navIconSize = if (compactLandscapeHeader) 22.dp else 24.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(headerHeight),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (focusRequester != null) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    }
                )
                .padding(start = 4.dp)
                .size(navButtonSize)
                .focusProperties {
                    if (downFocusRequester != null) {
                        down = downFocusRequester
                    }
                }
                .clip(CircleShape)
                .playerFocusHighlight(
                    enabled = enableCollapseGesture,
                    shape = CircleShape,
                    activeAlpha = 0.14f
                )
                .clickable(
                    enabled = enableCollapseGesture,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack
                )
                .focusable(enabled = enableCollapseGesture),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Minimize player",
                modifier = Modifier.size(navIconSize)
            )
        }
    }
}

private fun toDisplayFilename(file: File): String {
    return stripRemoteCacheHashPrefix(file.name)
}

private fun normalizedScale(valueDp: Dp, compactDp: Dp, roomyDp: Dp): Float {
    if (roomyDp <= compactDp) return 1f
    return ((valueDp.value - compactDp.value) / (roomyDp.value - compactDp.value))
        .coerceIn(0f, 1f)
}

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float {
    val t = fraction.coerceIn(0f, 1f)
    return start + (end - start) * t
}

private fun lerpDp(start: Dp, end: Dp, fraction: Float): Dp {
    val t = fraction.coerceIn(0f, 1f)
    return (start.value + (end.value - start.value) * t).dp
}

private fun lerpSp(start: TextUnit, end: TextUnit, fraction: Float): TextUnit {
    val t = fraction.coerceIn(0f, 1f)
    return (start.value + (end.value - start.value) * t).sp
}

private fun scaledDp(value: Dp, factor: Float): Dp {
    return (value.value * factor).dp
}

private fun Modifier.playerFocusHalo(
    enabled: Boolean = true,
    shape: Shape = CircleShape
): Modifier = composed {
    val focusIndicatorsEnabled = LocalPlayerFocusIndicatorsEnabled.current
    var isFocused by remember { mutableStateOf(false) }
    val haloAlpha by animateFloatAsState(
        targetValue = if (enabled && focusIndicatorsEnabled && isFocused) 0.7f else 0f,
        animationSpec = tween(durationMillis = 140),
        label = "playerFocusHaloAlpha"
    )
    this
        .onFocusChanged { isFocused = it.isFocused }
        .border(
            width = 2.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = haloAlpha),
            shape = shape
        )
}

private fun Modifier.playerFocusHighlight(
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    activeAlpha: Float = 0.22f
): Modifier = composed {
    val focusIndicatorsEnabled = LocalPlayerFocusIndicatorsEnabled.current
    var isFocused by remember { mutableStateOf(false) }
    val highlightAlpha by animateFloatAsState(
        targetValue = if (enabled && focusIndicatorsEnabled && isFocused) activeAlpha else 0f,
        animationSpec = tween(durationMillis = 140),
        label = "playerFocusHighlightAlpha"
    )
    this
        .onFocusChanged { isFocused = it.isFocused }
        .background(
            color = MaterialTheme.colorScheme.primary.copy(alpha = highlightAlpha),
            shape = shape
        )
}

@Composable
private fun TrackInfoChips(
    file: File?,
    decoderName: String?,
    fileSizeBytes: Long,
    sampleRateHz: Int,
    channelCount: Int,
    bitDepthLabel: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    layoutScale: Float = 1f,
    modifier: Modifier = Modifier
) {
    val formatLabel = file?.name?.let(::inferredPrimaryExtensionForName)?.uppercase() ?: "EMPTY"

    // Bitrate or file size based on decoder
    val bitrateOrSize = remember(file, decoderName, fileSizeBytes) {
        when {
            decoderName.equals(DecoderNames.FFMPEG, ignoreCase = true) -> {
                val bitrate = NativeBridge.getTrackBitrate()
                val isVBR = NativeBridge.isTrackVBR()
                if (bitrate > 0) {
                    formatBitrate(bitrate, isVBR)
                } else null
            }
            fileSizeBytes > 0 -> formatFileSize(fileSizeBytes)
            else -> null
        }
    }

    val sampleRateLabel = if (sampleRateHz > 0) {
        if (sampleRateHz % 1000 == 0) {
            "${sampleRateHz / 1000} kHz"
        } else {
            String.format(java.util.Locale.US, "%.1f kHz", sampleRateHz / 1000.0)
        }
    } else {
        "-- kHz"
    }
    val showBitDepth = decoderName.equals(DecoderNames.FFMPEG, ignoreCase = true)
    val depthDisplay = bitDepthLabel.ifBlank { "Unknown" }
    val channelsAndDepth = when {
        channelCount > 0 && showBitDepth -> "${channelCount} ch / $depthDisplay"
        channelCount > 0 -> "${channelCount} ch"
        showBitDepth -> depthDisplay
        else -> "-- ch"
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val widthScale = normalizedScale(maxWidth, compactDp = 340.dp, roomyDp = 760.dp)
        val tabletWidthScale = normalizedScale(maxWidth, compactDp = 440.dp, roomyDp = 900.dp)
        val chipScale = (layoutScale * 0.70f + widthScale * 0.30f).coerceIn(0f, 1f)
        val visualChipScale = (
            chipScale +
                lerpFloat(0f, 0.08f, layoutScale) +
                lerpFloat(0f, 0.16f, tabletWidthScale)
            ).coerceIn(0f, 1f)
        Row(
            modifier = Modifier
                .then(
                    if (focusRequester != null) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    }
                )
                .wrapContentWidth()
                .clip(RoundedCornerShape(999.dp))
                .pointerInput(onClick) {
                    detectTapGestures(onTap = { onClick() })
                }
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) {
                        return@onPreviewKeyEvent false
                    }
                    when (keyEvent.key) {
                        Key.Enter,
                        Key.NumPadEnter,
                        Key.DirectionCenter -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                }
                .focusProperties {
                    if (upFocusRequester != null) {
                        up = upFocusRequester
                    }
                    if (downFocusRequester != null) {
                        down = downFocusRequester
                    }
                }
                .playerFocusHighlight(shape = RoundedCornerShape(999.dp))
                .focusable(),
            horizontalArrangement = Arrangement.spacedBy(lerpDp(3.dp, 10.dp, visualChipScale))
        ) {
            TrackInfoChip(
                icon = Icons.Default.AudioFile,
                text = formatLabel,
                chipScale = visualChipScale,
                tabletWidthScale = tabletWidthScale
            )
            if (bitrateOrSize != null) {
                TrackInfoChip(
                    icon = if (decoderName.equals(DecoderNames.FFMPEG, ignoreCase = true))
                        PlayerChipIcons.WaveSawTool else PlayerChipIcons.HardDrive,
                    text = bitrateOrSize,
                    chipScale = visualChipScale,
                    tabletWidthScale = tabletWidthScale
                )
            }
            TrackInfoChip(
                icon = Icons.Default.Equalizer,
                text = sampleRateLabel,
                chipScale = visualChipScale,
                tabletWidthScale = tabletWidthScale
            )
            TrackInfoChip(
                icon = Icons.Default.Info,
                text = channelsAndDepth,
                chipScale = visualChipScale,
                tabletWidthScale = tabletWidthScale
            )
        }
    }
}

@Composable
private fun TrackInfoDetailsDialog(
    file: File?,
    title: String,
    artist: String,
    decoderName: String?,
    isDialogVisible: Boolean,
    playbackSourceLabel: String?,
    pathOrUrl: String?,
    sampleRateHz: Int,
    channelCount: Int,
    bitDepthLabel: String,
    durationSeconds: Double,
    hasReliableDuration: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val liveMetadata = rememberTrackInfoLiveMetadata(
        filePath = file?.absolutePath,
        decoderName = decoderName,
        isDialogVisible = isDialogVisible
    )
    val detailsScrollState = rememberScrollState()
    var detailsViewportHeightPx by remember { mutableIntStateOf(0) }
    val detailsScrollbarAlpha = rememberDialogScrollbarAlpha(
        enabled = true,
        scrollState = detailsScrollState,
        label = "trackInfoDetailsScrollbarAlpha"
    )
    val fileSizeBytes = file?.length() ?: 0L
    val filename = file?.name ?: "No file loaded"
    val extension = file?.name?.let(::inferredPrimaryExtensionForName)?.uppercase() ?: "UNKNOWN"
    val decoderLabel = decoderName?.ifBlank { "Unknown" } ?: "Unknown"
    val bitrateLabel = if (liveMetadata.bitrate > 0L) {
        "${formatBitrate(liveMetadata.bitrate, liveMetadata.isVbr)} (${if (liveMetadata.isVbr) "VBR" else "CBR"})"
    } else {
        "Unavailable"
    }
    val audioBackendLabel = liveMetadata.audioBackendLabel.ifBlank { "(inactive)" }
    val lengthLabel = if (durationSeconds > 0.0) {
        if (hasReliableDuration) formatTime(durationSeconds) else "${formatTime(durationSeconds)}?"
    } else {
        "Unavailable"
    }
    val channelsLabel = if (channelCount > 0) "$channelCount channels" else "Unknown"
    val depthLabel = bitDepthLabel.ifBlank { "Unknown" }
    val sampleRateChain =
        "${formatSampleRateForDetails(sampleRateHz)} -> " +
            "${formatSampleRateForDetails(liveMetadata.renderRateHz)} -> " +
            formatSampleRateForDetails(liveMetadata.outputRateHz)
    val pathOrUrlLabel = pathOrUrl?.ifBlank { "Unavailable" } ?: "Unavailable"
    val copyAllText = buildString {
        fun row(label: String, value: String) {
            append(label).append(": ").append(value).append('\n')
        }

        row("Filename", filename)
        row("Title", title)
        row("Artist", artist)
        if (liveMetadata.composer.isNotBlank()) row("Composer", liveMetadata.composer)
        if (liveMetadata.genre.isNotBlank()) row("Genre", liveMetadata.genre)
        if (liveMetadata.album.isNotBlank()) row("Album", liveMetadata.album)
        if (liveMetadata.year.isNotBlank()) row("Year", liveMetadata.year)
        if (liveMetadata.date.isNotBlank()) row("Date", liveMetadata.date)
        if (liveMetadata.copyrightText.isNotBlank()) row("Copyright", liveMetadata.copyrightText)
        if (liveMetadata.comment.isNotBlank()) row("Comment", liveMetadata.comment)
        row("Format", extension)
        row("Decoder", decoderLabel)
        playbackSourceLabel?.takeIf { it.isNotBlank() }?.let { row("Playback source", it) }
        row("File size", if (fileSizeBytes > 0L) formatFileSize(fileSizeBytes) else "Unavailable")
        row("Sample rate chain", sampleRateChain)
        row("Bitrate", bitrateLabel)
        row("Length", lengthLabel)
        row("Audio channels", channelsLabel)
        row("Bit depth", depthLabel)
        row("Audio backend", audioBackendLabel)
        row("Path / URL", pathOrUrlLabel)
        appendCoreTrackInfoCopyRows(
            builder = this,
            decoderName = decoderName,
            sampleRateHz = sampleRateHz,
            metadata = liveMetadata
        )
    }

    AlertDialog(
        modifier = adaptiveDialogModifier(),
        properties = adaptiveDialogProperties(),
        onDismissRequest = onDismiss,
        title = { Text("Track and decoder info") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { detailsViewportHeightPx = it.height }
                        .padding(end = 10.dp)
                        .verticalScroll(detailsScrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TrackInfoDetailsRow("Filename", filename)
                            TrackInfoDetailsRow("Title", title)
                            TrackInfoDetailsRow("Artist", artist)
                            if (liveMetadata.composer.isNotBlank()) {
                                TrackInfoDetailsRow("Composer", liveMetadata.composer)
                            }
                            if (liveMetadata.genre.isNotBlank()) {
                                TrackInfoDetailsRow("Genre", liveMetadata.genre)
                            }
                            if (liveMetadata.album.isNotBlank()) {
                                TrackInfoDetailsRow("Album", liveMetadata.album)
                            }
                            if (liveMetadata.year.isNotBlank()) {
                                TrackInfoDetailsRow("Year", liveMetadata.year)
                            }
                            if (liveMetadata.date.isNotBlank()) {
                                TrackInfoDetailsRow("Date", liveMetadata.date)
                            }
                            if (liveMetadata.copyrightText.isNotBlank()) {
                                TrackInfoDetailsRow("Copyright", liveMetadata.copyrightText)
                            }
                            if (liveMetadata.comment.isNotBlank()) {
                                TrackInfoDetailsRow("Comment", liveMetadata.comment)
                            }
                            TrackInfoDetailsRow("Format", extension)
                            TrackInfoDetailsRow("Decoder", decoderLabel)
                            playbackSourceLabel?.takeIf { it.isNotBlank() }?.let {
                                TrackInfoDetailsRow("Playback source", it)
                            }
                            TrackInfoDetailsRow(
                                "File size",
                                if (fileSizeBytes > 0L) formatFileSize(fileSizeBytes) else "Unavailable"
                            )
                            TrackInfoDetailsRow("Sample rate chain", sampleRateChain)
                            TrackInfoDetailsRow("Bitrate", bitrateLabel)
                            TrackInfoDetailsRow("Length", lengthLabel)
                            TrackInfoDetailsRow("Audio channels", channelsLabel)
                            TrackInfoDetailsRow("Bit depth", depthLabel)
                            TrackInfoDetailsRow("Audio backend", audioBackendLabel)
                            TrackInfoDetailsRow("Path / URL", pathOrUrlLabel)
                            TrackInfoCoreSections(
                                decoderName = decoderName,
                                sampleRateHz = sampleRateHz,
                                metadata = liveMetadata
                            )
                        }
                    }
                }
                if (detailsScrollState.maxValue > 0 && detailsViewportHeightPx > 0) {
                    TrackInfoDetailsScrollbar(
                        scrollState = detailsScrollState,
                        viewportHeightPx = detailsViewportHeightPx,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(6.dp)
                            .graphicsLayer(alpha = detailsScrollbarAlpha)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(copyAllText.trim()))
                    Toast.makeText(context, "Copied track and decoder info", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("Copy all")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun TrackInfoDetailsScrollbar(
    scrollState: androidx.compose.foundation.ScrollState,
    viewportHeightPx: Int,
    modifier: Modifier = Modifier
) {
    val maxScroll = scrollState.maxValue
    if (maxScroll <= 0 || viewportHeightPx <= 0) return

    val viewport = viewportHeightPx.toFloat()
    val content = viewport + maxScroll.toFloat()
    val thumbHeightPx = (viewport * (viewport / content)).coerceAtLeast(24f)
    val travelRangePx = (viewport - thumbHeightPx).coerceAtLeast(0f)
    val thumbOffsetPx =
        if (maxScroll > 0) (scrollState.value.toFloat() / maxScroll.toFloat()) * travelRangePx else 0f

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f))
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                .fillMaxWidth()
                .height(with(LocalDensity.current) { thumbHeightPx.toDp() })
                .clip(RoundedCornerShape(99.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.68f))
        )
    }
}

@Composable
private fun TrackInfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    chipScale: Float,
    tabletWidthScale: Float
) {
    val iconSize = lerpDp(11.dp, 16.5.dp, chipScale) + lerpDp(0.dp, 4.dp, tabletWidthScale)
    val iconSlot = lerpDp(12.dp, 16.5.dp, chipScale) + lerpDp(0.dp, 4.dp, tabletWidthScale)
    val sideInset = lerpDp(3.dp, 8.dp, chipScale) + lerpDp(0.dp, 3.dp, tabletWidthScale)
    val minHeight = lerpDp(22.dp, 36.dp, chipScale) + lerpDp(0.dp, 14.dp, tabletWidthScale)
    val iconTextGap = lerpDp(1.dp, 5.dp, chipScale) + lerpDp(0.dp, 2.5f.dp, tabletWidthScale)
    val textStartPadding = iconSlot + iconTextGap
    val textEndPadding = lerpDp(1.dp, 3.dp, chipScale) + lerpDp(0.dp, 2.dp, tabletWidthScale)
    val baseTextStyle = when {
        tabletWidthScale > 0.28f && chipScale > 0.58f && text.length <= 10 -> MaterialTheme.typography.labelLarge
        chipScale < 0.35f -> MaterialTheme.typography.labelSmall
        chipScale < 0.55f && text.length >= 10 -> MaterialTheme.typography.labelSmall
        else -> MaterialTheme.typography.labelMedium
    }
    val compactTextScale = lerpFloat(0.90f, 1f, (chipScale * 0.80f + tabletWidthScale * 0.20f).coerceIn(0f, 1f))
    val textStyle = baseTextStyle.copy(
        fontSize = if (baseTextStyle.fontSize != TextUnit.Unspecified) {
            (baseTextStyle.fontSize.value * compactTextScale).sp
        } else {
            baseTextStyle.fontSize
        },
        lineHeight = if (baseTextStyle.lineHeight != TextUnit.Unspecified) {
            (baseTextStyle.lineHeight.value * compactTextScale).sp
        } else {
            baseTextStyle.lineHeight
        }
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
        shape = RoundedCornerShape(percent = 50)
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = minHeight)
                .padding(horizontal = sideInset),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(iconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = text,
                style = textStyle,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                softWrap = false,
                modifier = Modifier.padding(start = textStartPadding, end = textEndPadding)
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TrackMetadataBlock(
    title: String,
    artist: String,
    filename: String,
    filenameDisplayMode: com.flopster101.siliconplayer.FilenameDisplayMode,
    decoderName: String?,
    filenameOnlyWhenTitleMissing: Boolean,
    showFilename: Boolean = true,
    centerSupportingMetadata: Boolean = false,
    currentSubtuneIndex: Int = 0,
    subtuneCount: Int = 0,
    layoutScale: Float = 1f
) {
    val titleTextStyle = MaterialTheme.typography.headlineSmall.copy(
        fontSize = lerpSp(20.sp, 30.sp, layoutScale),
        lineHeight = lerpSp(26.sp, 36.sp, layoutScale)
    )
    val artistTextStyle = MaterialTheme.typography.titleMedium.copy(
        fontSize = lerpSp(13.sp, 18.sp, layoutScale),
        lineHeight = lerpSp(17.sp, 24.sp, layoutScale)
    )
    val titleArtistSpacer = lerpDp(3.dp, 8.dp, layoutScale)
    val artistFilenameSpacer = lerpDp(1.dp, 6.dp, layoutScale)
    val shouldShowFilename = remember(filenameDisplayMode, decoderName, title, filenameOnlyWhenTitleMissing) {
        when (filenameDisplayMode) {
            com.flopster101.siliconplayer.FilenameDisplayMode.Always -> {
                // If "only when title missing" is enabled, check if title is blank
                if (filenameOnlyWhenTitleMissing) {
                    title.isBlank()
                } else {
                    true
                }
            }
            com.flopster101.siliconplayer.FilenameDisplayMode.Never -> false
            com.flopster101.siliconplayer.FilenameDisplayMode.TrackerOnly -> {
                val decoder = decoderName?.lowercase() ?: ""
                val isTracker = decoder.contains("openmpt") || decoder.contains("libopenmpt")
                // If tracker format, apply the "only when title missing" logic
                if (isTracker && filenameOnlyWhenTitleMissing) {
                    title.isBlank()
                } else {
                    isTracker
                }
            }
        }
    }

    val subtuneBadge = remember(currentSubtuneIndex, subtuneCount) {
        if (subtuneCount > 1) {
            val shownIndex = (currentSubtuneIndex + 1).coerceIn(1, subtuneCount)
            "[$shownIndex/$subtuneCount]"
        } else {
            null
        }
    }

    AnimatedContent(
        targetState = title to subtuneBadge,
        transitionSpec = {
            fadeIn(animationSpec = tween(durationMillis = 180, delayMillis = 40)) togetherWith
                fadeOut(animationSpec = tween(durationMillis = 120))
        },
        label = "trackTitleSwap"
    ) { (animatedTitle, animatedSubtuneBadge) ->
        val shouldMarquee = animatedTitle.length > 30
        if (shouldMarquee && animatedSubtuneBadge == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
            ) {
                Text(
                    text = animatedTitle,
                    style = titleTextStyle,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            initialDelayMillis = 450
                        )
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = animatedTitle,
                    style = titleTextStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                if (animatedSubtuneBadge != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = animatedSubtuneBadge,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        maxLines = 1
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(titleArtistSpacer))
    AnimatedContent(
        targetState = artist,
        transitionSpec = {
            fadeIn(animationSpec = tween(durationMillis = 180, delayMillis = 30)) togetherWith
                fadeOut(animationSpec = tween(durationMillis = 110))
        },
        label = "trackArtistSwap"
    ) { animatedArtist ->
        Text(
            text = animatedArtist,
            style = artistTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (centerSupportingMetadata) TextAlign.Center else TextAlign.Start,
            modifier = if (centerSupportingMetadata) Modifier.fillMaxWidth() else Modifier
        )
    }
    if (showFilename && shouldShowFilename) {
        Spacer(modifier = Modifier.height(artistFilenameSpacer))
        val filenameTextStyle = MaterialTheme.typography.bodySmall
        val filenameColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f)
        val textMeasurer = rememberTextMeasurer()
        val density = LocalDensity.current
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
        ) {
            val maxWidthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
            val shouldMarqueeFilename = remember(filename, filenameTextStyle, maxWidthPx) {
                textMeasurer.measure(
                    text = AnnotatedString(filename),
                    style = filenameTextStyle,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    constraints = Constraints(maxWidth = maxWidthPx)
                ).hasVisualOverflow
            }
            Text(
                text = filename,
                style = filenameTextStyle,
                color = filenameColor,
                maxLines = 1,
                softWrap = false,
                overflow = if (shouldMarqueeFilename) TextOverflow.Clip else TextOverflow.Ellipsis,
                textAlign = if (shouldMarqueeFilename) TextAlign.Start else TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (shouldMarqueeFilename) {
                            Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                initialDelayMillis = 550
                            )
                        } else {
                            Modifier
                        }
                    )
            )
        }
    }
}

@Composable
private fun TransportControls(
    hasTrack: Boolean,
    isPlaying: Boolean,
    canResumeStoppedTrack: Boolean,
    repeatMode: RepeatMode,
    playbackStartInProgress: Boolean,
    remoteLoadUiState: RemoteLoadUiState?,
    seekInProgress: Boolean,
    canPreviousTrack: Boolean,
    canNextTrack: Boolean,
    canCycleRepeatMode: Boolean,
    onPlayPause: () -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onPreviousSubtune: () -> Unit,
    onNextSubtune: () -> Unit,
    onOpenSubtuneSelector: () -> Unit,
    canPreviousSubtune: Boolean,
    canNextSubtune: Boolean,
    canOpenSubtuneSelector: Boolean,
    onStopAndClear: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    layoutScale: Float = 1f,
    transportAnchorFocusRequester: FocusRequester? = null,
    actionStripFirstFocusRequester: FocusRequester? = null
) {
    val remoteLoadActive = remoteLoadUiState != null
    val showLoadingIndicator = playbackStartInProgress || remoteLoadActive
    val controlsBusy = seekInProgress || playbackStartInProgress
    val canFocusPreviousTrack = hasTrack && canPreviousTrack
    val canFocusRepeatMode = canCycleRepeatMode && !controlsBusy
    val canFocusPlayPause = (hasTrack || canResumeStoppedTrack) && !controlsBusy
    val canFocusStop = true
    val canFocusNextTrack = hasTrack && canNextTrack
    val canFocusPreviousSubtune = canPreviousSubtune
    val canFocusSubtuneSelector = canOpenSubtuneSelector
    val canFocusNextSubtune = canNextSubtune

    val previousTrackFocusRequester = remember { FocusRequester() }
    val repeatModeFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = transportAnchorFocusRequester ?: remember { FocusRequester() }
    val stopFocusRequester = remember { FocusRequester() }
    val nextTrackFocusRequester = remember { FocusRequester() }
    val previousSubtuneFocusRequester = remember { FocusRequester() }
    val subtuneSelectorFocusRequester = remember { FocusRequester() }
    val nextSubtuneFocusRequester = remember { FocusRequester() }
    var initialTransportFocusAssigned by remember { mutableStateOf(false) }
    fun firstAvailableRequester(vararg options: Pair<Boolean, FocusRequester>): FocusRequester? {
        return options.firstOrNull { it.first }?.second
    }
    LaunchedEffect(
        canFocusPlayPause,
        canFocusStop,
        canFocusPreviousTrack,
        canFocusRepeatMode,
        canFocusNextTrack
    ) {
        if (initialTransportFocusAssigned) return@LaunchedEffect
        delay(90)
        val requester = firstAvailableRequester(
            canFocusPlayPause to playPauseFocusRequester,
            canFocusStop to stopFocusRequester,
            canFocusPreviousTrack to previousTrackFocusRequester,
            canFocusRepeatMode to repeatModeFocusRequester,
            canFocusNextTrack to nextTrackFocusRequester
        )
        requester?.requestFocus()
        if (requester != null) {
            initialTransportFocusAssigned = true
        }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val tabletWidthScale = normalizedScale(maxWidth, compactDp = 560.dp, roomyDp = 980.dp)
        val heightBias = lerpFloat(0.90f, 1f, layoutScale)
        val sideButtonMax = lerpDp(62.dp, 82.dp, tabletWidthScale)
        val playButtonMax = lerpDp(92.dp, 124.dp, tabletWidthScale)
        val subtuneButtonMax = lerpDp(60.dp, 80.dp, tabletWidthScale)
        val sideButtonSize =
            scaledDp(maxWidth, lerpFloat(0.132f, 0.148f, layoutScale) * heightBias).coerceIn(42.dp, sideButtonMax)
        val playButtonSize = scaledDp(sideButtonSize, 1.52f).coerceIn(64.dp, playButtonMax)
        val subtuneButtonSize = scaledDp(sideButtonSize, 1.03f).coerceIn(44.dp, subtuneButtonMax)
        val occupiedWidth = (sideButtonSize.value * 4f + playButtonSize.value).dp
        val rowGap = ((maxWidth - occupiedWidth).coerceAtLeast(0.dp) / 4f).coerceIn(6.dp, lerpDp(14.dp, 22.dp, tabletWidthScale))
        val subtuneGap = (rowGap * 0.75f).coerceIn(6.dp, lerpDp(11.dp, 16.dp, tabletWidthScale))
        val repeatIconSize = scaledDp(sideButtonSize, 0.42f).coerceIn(18.dp, lerpDp(24.dp, 30.dp, tabletWidthScale))
        val repeatBadgeCenterOffsetX = scaledDp(sideButtonSize, 0.20f)
        val repeatBadgeCenterOffsetY = scaledDp(sideButtonSize, -0.17f)
        val repeatBadgeHorizontalPadding = scaledDp(sideButtonSize, 0.08f).coerceIn(3.dp, 6.dp)
        val repeatBadgeVerticalPadding = scaledDp(sideButtonSize, 0.03f).coerceIn(1.dp, 2.dp)
        val repeatBadgeTextSize = (sideButtonSize.value * 0.16f).coerceIn(8f, 10.5f).sp
        val loadingSpacer = scaledDp(sideButtonSize, 0.14f).coerceIn(4.dp, lerpDp(8.dp, 12.dp, tabletWidthScale))
        val subtuneRowTopSpacer = (
            scaledDp(sideButtonSize, 0.1f) + lerpDp(0.dp, 8.dp, layoutScale)
        ).coerceIn(3.dp, lerpDp(14.dp, 24.dp, tabletWidthScale))
        val playIndicatorSize = scaledDp(playButtonSize, 0.34f).coerceIn(24.dp, lerpDp(30.dp, 38.dp, tabletWidthScale))
        val playIconSize = scaledDp(playButtonSize, 0.38f).coerceIn(28.dp, lerpDp(36.dp, 46.dp, tabletWidthScale))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = onPreviousTrack,
                        enabled = hasTrack && canPreviousTrack,
                        modifier = Modifier
                            .focusRequester(previousTrackFocusRequester)
                            .size(sideButtonSize)
                            .focusProperties {
                                left = firstAvailableRequester(
                                    canFocusNextTrack to nextTrackFocusRequester,
                                    canFocusStop to stopFocusRequester,
                                    canFocusPlayPause to playPauseFocusRequester,
                                    canFocusRepeatMode to repeatModeFocusRequester,
                                    canFocusPreviousTrack to previousTrackFocusRequester
                                ) ?: previousTrackFocusRequester
                                right = firstAvailableRequester(
                                    canFocusRepeatMode to repeatModeFocusRequester,
                                    canFocusPlayPause to playPauseFocusRequester,
                                    canFocusStop to stopFocusRequester,
                                    canFocusNextTrack to nextTrackFocusRequester,
                                    canFocusPreviousTrack to previousTrackFocusRequester
                                ) ?: previousTrackFocusRequester
                                down = firstAvailableRequester(
                                    canFocusPreviousSubtune to previousSubtuneFocusRequester,
                                    canFocusSubtuneSelector to subtuneSelectorFocusRequester,
                                    canFocusNextSubtune to nextSubtuneFocusRequester,
                                    (actionStripFirstFocusRequester != null) to (actionStripFirstFocusRequester ?: previousTrackFocusRequester)
                                ) ?: previousTrackFocusRequester
                            }
                            .playerFocusHalo(enabled = hasTrack && canPreviousTrack)
                            .focusable(enabled = hasTrack && canPreviousTrack),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous track"
                        )
                    }

                    Spacer(modifier = Modifier.width(rowGap))

                    FilledTonalIconButton(
                        onClick = onCycleRepeatMode,
                        enabled = canCycleRepeatMode && !controlsBusy,
                        modifier = Modifier
                            .focusRequester(repeatModeFocusRequester)
                            .size(sideButtonSize)
                            .focusProperties {
                                left = firstAvailableRequester(
                                    canFocusPreviousTrack to previousTrackFocusRequester,
                                    canFocusNextTrack to nextTrackFocusRequester,
                                    canFocusStop to stopFocusRequester,
                                    canFocusPlayPause to playPauseFocusRequester,
                                    canFocusRepeatMode to repeatModeFocusRequester
                                ) ?: repeatModeFocusRequester
                                right = firstAvailableRequester(
                                    canFocusPlayPause to playPauseFocusRequester,
                                    canFocusStop to stopFocusRequester,
                                    canFocusNextTrack to nextTrackFocusRequester,
                                    canFocusPreviousTrack to previousTrackFocusRequester,
                                    canFocusRepeatMode to repeatModeFocusRequester
                                ) ?: repeatModeFocusRequester
                                down = firstAvailableRequester(
                                    canFocusSubtuneSelector to subtuneSelectorFocusRequester,
                                    canFocusPreviousSubtune to previousSubtuneFocusRequester,
                                    canFocusNextSubtune to nextSubtuneFocusRequester,
                                    (actionStripFirstFocusRequester != null) to (actionStripFirstFocusRequester ?: repeatModeFocusRequester)
                                ) ?: repeatModeFocusRequester
                            }
                            .playerFocusHalo(enabled = canCycleRepeatMode && !controlsBusy)
                            .focusable(enabled = canCycleRepeatMode && !controlsBusy),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (repeatMode != RepeatMode.None) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        val modeBadge = when (repeatMode) {
                            RepeatMode.None -> ""
                            RepeatMode.Track -> "1"
                            RepeatMode.Subtune -> "ST"
                            RepeatMode.LoopPoint -> "LP"
                        }
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Loop,
                                contentDescription = "Repeat mode: ${repeatMode.label}",
                                modifier = Modifier.size(repeatIconSize)
                            )
                            if (modeBadge.isNotEmpty()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    shape = RoundedCornerShape(percent = 50),
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .offset(
                                            x = repeatBadgeCenterOffsetX,
                                            y = repeatBadgeCenterOffsetY
                                        )
                                ) {
                                    Text(
                                        text = modeBadge,
                                        fontSize = repeatBadgeTextSize,
                                        lineHeight = repeatBadgeTextSize,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(
                                            horizontal = repeatBadgeHorizontalPadding,
                                            vertical = repeatBadgeVerticalPadding
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.width(rowGap))

                FilledIconButton(
                    onClick = onPlayPause,
                    enabled = (hasTrack || canResumeStoppedTrack) && !controlsBusy,
                    modifier = Modifier
                        .size(playButtonSize)
                        .focusRequester(playPauseFocusRequester)
                        .focusProperties {
                            left = firstAvailableRequester(
                                canFocusRepeatMode to repeatModeFocusRequester,
                                canFocusPreviousTrack to previousTrackFocusRequester,
                                canFocusNextTrack to nextTrackFocusRequester,
                                canFocusStop to stopFocusRequester,
                                canFocusPlayPause to playPauseFocusRequester
                            ) ?: playPauseFocusRequester
                            right = firstAvailableRequester(
                                canFocusStop to stopFocusRequester,
                                canFocusNextTrack to nextTrackFocusRequester,
                                canFocusPreviousTrack to previousTrackFocusRequester,
                                canFocusRepeatMode to repeatModeFocusRequester,
                                canFocusPlayPause to playPauseFocusRequester
                            ) ?: playPauseFocusRequester
                            down = firstAvailableRequester(
                                canFocusSubtuneSelector to subtuneSelectorFocusRequester,
                                canFocusPreviousSubtune to previousSubtuneFocusRequester,
                                canFocusNextSubtune to nextSubtuneFocusRequester,
                                (actionStripFirstFocusRequester != null) to (actionStripFirstFocusRequester ?: playPauseFocusRequester)
                            ) ?: playPauseFocusRequester
                        }
                        .playerFocusHalo(enabled = (hasTrack || canResumeStoppedTrack) && !controlsBusy)
                        .focusable(enabled = (hasTrack || canResumeStoppedTrack) && !controlsBusy),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    if (showLoadingIndicator) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(playIndicatorSize),
                            strokeWidth = 3.dp
                        )
                    } else {
                        AnimatedContent(
                            targetState = isPlaying,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "playerPlayPauseIcon"
                        ) { playing ->
                            Icon(
                                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playing) "Pause" else "Play",
                                modifier = Modifier.size(playIconSize)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(rowGap))

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = onStopAndClear,
                        modifier = Modifier
                            .focusRequester(stopFocusRequester)
                            .size(sideButtonSize)
                            .focusProperties {
                                left = firstAvailableRequester(
                                    canFocusPlayPause to playPauseFocusRequester,
                                    canFocusRepeatMode to repeatModeFocusRequester,
                                    canFocusPreviousTrack to previousTrackFocusRequester,
                                    canFocusNextTrack to nextTrackFocusRequester,
                                    canFocusStop to stopFocusRequester
                                ) ?: stopFocusRequester
                                right = firstAvailableRequester(
                                    canFocusNextTrack to nextTrackFocusRequester,
                                    canFocusPreviousTrack to previousTrackFocusRequester,
                                    canFocusRepeatMode to repeatModeFocusRequester,
                                    canFocusPlayPause to playPauseFocusRequester,
                                    canFocusStop to stopFocusRequester
                                ) ?: stopFocusRequester
                                down = firstAvailableRequester(
                                    canFocusSubtuneSelector to subtuneSelectorFocusRequester,
                                    canFocusPreviousSubtune to previousSubtuneFocusRequester,
                                    canFocusNextSubtune to nextSubtuneFocusRequester,
                                    (actionStripFirstFocusRequester != null) to (actionStripFirstFocusRequester ?: stopFocusRequester)
                                ) ?: stopFocusRequester
                            }
                            .playerFocusHalo()
                            .focusable(),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop"
                        )
                    }

                    Spacer(modifier = Modifier.width(rowGap))

                    FilledTonalIconButton(
                        onClick = onNextTrack,
                        enabled = hasTrack && canNextTrack,
                        modifier = Modifier
                            .focusRequester(nextTrackFocusRequester)
                            .size(sideButtonSize)
                            .focusProperties {
                                left = firstAvailableRequester(
                                    canFocusStop to stopFocusRequester,
                                    canFocusPlayPause to playPauseFocusRequester,
                                    canFocusRepeatMode to repeatModeFocusRequester,
                                    canFocusPreviousTrack to previousTrackFocusRequester,
                                    canFocusNextTrack to nextTrackFocusRequester
                                ) ?: nextTrackFocusRequester
                                right = firstAvailableRequester(
                                    canFocusPreviousTrack to previousTrackFocusRequester,
                                    canFocusRepeatMode to repeatModeFocusRequester,
                                    canFocusPlayPause to playPauseFocusRequester,
                                    canFocusStop to stopFocusRequester,
                                    canFocusNextTrack to nextTrackFocusRequester
                                ) ?: nextTrackFocusRequester
                                down = firstAvailableRequester(
                                    canFocusNextSubtune to nextSubtuneFocusRequester,
                                    canFocusSubtuneSelector to subtuneSelectorFocusRequester,
                                    canFocusPreviousSubtune to previousSubtuneFocusRequester,
                                    (actionStripFirstFocusRequester != null) to (actionStripFirstFocusRequester ?: nextTrackFocusRequester)
                                ) ?: nextTrackFocusRequester
                            }
                            .playerFocusHalo(enabled = hasTrack && canNextTrack)
                            .focusable(enabled = hasTrack && canNextTrack),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next track"
                        )
                    }
                }
            }

            if (showLoadingIndicator) {
                Spacer(modifier = Modifier.height(loadingSpacer))
                Text(
                    text = remoteLoadProgressLabel(remoteLoadUiState),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(subtuneRowTopSpacer))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onPreviousSubtune,
                    enabled = canPreviousSubtune,
                    modifier = Modifier
                        .focusRequester(previousSubtuneFocusRequester)
                        .size(subtuneButtonSize)
                        .focusProperties {
                            left = firstAvailableRequester(
                                canFocusNextSubtune to nextSubtuneFocusRequester,
                                canFocusSubtuneSelector to subtuneSelectorFocusRequester,
                                canFocusPreviousSubtune to previousSubtuneFocusRequester
                            ) ?: previousSubtuneFocusRequester
                            right = firstAvailableRequester(
                                canFocusSubtuneSelector to subtuneSelectorFocusRequester,
                                canFocusNextSubtune to nextSubtuneFocusRequester,
                                canFocusPreviousSubtune to previousSubtuneFocusRequester
                            ) ?: previousSubtuneFocusRequester
                            up = firstAvailableRequester(
                                canFocusPreviousTrack to previousTrackFocusRequester,
                                canFocusRepeatMode to repeatModeFocusRequester,
                                canFocusPlayPause to playPauseFocusRequester,
                                canFocusStop to stopFocusRequester,
                                canFocusNextTrack to nextTrackFocusRequester
                            ) ?: previousSubtuneFocusRequester
                            if (actionStripFirstFocusRequester != null) {
                                down = actionStripFirstFocusRequester
                            }
                        }
                        .playerFocusHalo(enabled = canPreviousSubtune)
                        .focusable(enabled = canPreviousSubtune),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardDoubleArrowLeft,
                        contentDescription = "Previous subtune"
                    )
                }
                Spacer(modifier = Modifier.width(subtuneGap))
                FilledTonalIconButton(
                    onClick = onOpenSubtuneSelector,
                    enabled = canOpenSubtuneSelector,
                    modifier = Modifier
                        .focusRequester(subtuneSelectorFocusRequester)
                        .size(subtuneButtonSize)
                        .focusProperties {
                            left = firstAvailableRequester(
                                canFocusPreviousSubtune to previousSubtuneFocusRequester,
                                canFocusNextSubtune to nextSubtuneFocusRequester,
                                canFocusSubtuneSelector to subtuneSelectorFocusRequester
                            ) ?: subtuneSelectorFocusRequester
                            right = firstAvailableRequester(
                                canFocusNextSubtune to nextSubtuneFocusRequester,
                                canFocusPreviousSubtune to previousSubtuneFocusRequester,
                                canFocusSubtuneSelector to subtuneSelectorFocusRequester
                            ) ?: subtuneSelectorFocusRequester
                            up = firstAvailableRequester(
                                canFocusPlayPause to playPauseFocusRequester,
                                canFocusRepeatMode to repeatModeFocusRequester,
                                canFocusStop to stopFocusRequester,
                                canFocusPreviousTrack to previousTrackFocusRequester,
                                canFocusNextTrack to nextTrackFocusRequester
                            ) ?: subtuneSelectorFocusRequester
                            if (actionStripFirstFocusRequester != null) {
                                down = actionStripFirstFocusRequester
                            }
                        }
                        .playerFocusHalo(enabled = canOpenSubtuneSelector)
                        .focusable(enabled = canOpenSubtuneSelector),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = "Subtune selector"
                    )
                }
                Spacer(modifier = Modifier.width(subtuneGap))
                FilledTonalIconButton(
                    onClick = onNextSubtune,
                    enabled = canNextSubtune,
                    modifier = Modifier
                        .focusRequester(nextSubtuneFocusRequester)
                        .size(subtuneButtonSize)
                        .focusProperties {
                            left = firstAvailableRequester(
                                canFocusSubtuneSelector to subtuneSelectorFocusRequester,
                                canFocusPreviousSubtune to previousSubtuneFocusRequester,
                                canFocusNextSubtune to nextSubtuneFocusRequester
                            ) ?: nextSubtuneFocusRequester
                            right = firstAvailableRequester(
                                canFocusPreviousSubtune to previousSubtuneFocusRequester,
                                canFocusSubtuneSelector to subtuneSelectorFocusRequester,
                                canFocusNextSubtune to nextSubtuneFocusRequester
                            ) ?: nextSubtuneFocusRequester
                            up = firstAvailableRequester(
                                canFocusNextTrack to nextTrackFocusRequester,
                                canFocusStop to stopFocusRequester,
                                canFocusPlayPause to playPauseFocusRequester,
                                canFocusRepeatMode to repeatModeFocusRequester,
                                canFocusPreviousTrack to previousTrackFocusRequester
                            ) ?: nextSubtuneFocusRequester
                            if (actionStripFirstFocusRequester != null) {
                                down = actionStripFirstFocusRequester
                            }
                        }
                        .playerFocusHalo(enabled = canNextSubtune)
                        .focusable(enabled = canNextSubtune),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardDoubleArrowRight,
                        contentDescription = "Next subtune"
                    )
                }
            }
        }
    }
}

private fun remoteLoadProgressLabel(remoteLoadUiState: RemoteLoadUiState?): String {
    if (remoteLoadUiState == null) return "Loading track..."
    val phaseLabel = when (remoteLoadUiState.phase) {
        RemoteLoadPhase.Connecting -> "Connecting..."
        RemoteLoadPhase.Downloading -> "Downloading..."
        RemoteLoadPhase.Opening -> "Opening..."
    }
    if (remoteLoadUiState.phase == RemoteLoadPhase.Connecting) return phaseLabel
    val downloadedLabel = formatByteCount(remoteLoadUiState.downloadedBytes)
    val sizeLabel = remoteLoadUiState.totalBytes
        ?.takeIf { it > 0L }
        ?.let { total -> "$downloadedLabel / ${formatByteCount(total)}" }
        ?: downloadedLabel
    val percentLabel = remoteLoadUiState.percent
        ?.takeIf { it in 0..100 }
        ?.let { percent -> " • $percent%" }
        .orEmpty()
    return "$phaseLabel $sizeLabel$percentLabel"
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FutureActionStrip(
    modifier: Modifier = Modifier,
    canOpenCoreSettings: Boolean,
    onOpenCoreSettings: () -> Unit,
    onCycleVisualizationMode: () -> Unit,
    onOpenVisualizationPicker: () -> Unit,
    onOpenAudioEffects: () -> Unit,
    onOpenChannelControls: () -> Unit,
    layoutScale: Float = 1f,
    actionStripFirstFocusRequester: FocusRequester? = null,
    transportAnchorFocusRequester: FocusRequester? = null
) {
    val visualizationModeFocusRequester = actionStripFirstFocusRequester ?: remember { FocusRequester() }
    val coreSettingsFocusRequester = remember { FocusRequester() }
    val audioEffectsFocusRequester = remember { FocusRequester() }
    val channelControlsFocusRequester = remember { FocusRequester() }
    val canFocusVisualizationMode = true
    val canFocusCoreSettings = canOpenCoreSettings
    val canFocusAudioEffects = true
    val canFocusChannelControls = true
    fun firstAvailableActionRequester(vararg options: Pair<Boolean, FocusRequester>): FocusRequester? {
        return options.firstOrNull { it.first }?.second
    }
    BoxWithConstraints(modifier = modifier) {
        val tabletWidthScale = normalizedScale(maxWidth, compactDp = 560.dp, roomyDp = 980.dp)
        val widthBias = lerpFloat(0.92f, 1.04f, layoutScale)
        val iconButtonMax = lerpDp(52.dp, 70.dp, tabletWidthScale)
        val iconButtonSize =
            scaledDp(maxWidth, lerpFloat(0.100f, 0.126f, layoutScale) * widthBias).coerceIn(34.dp, iconButtonMax)
        val stripHorizontalPadding = scaledDp(iconButtonSize, lerpFloat(0.16f, 0.24f, layoutScale))
            .coerceIn(2.dp, lerpDp(12.dp, 18.dp, tabletWidthScale))
        val stripVerticalPadding = scaledDp(iconButtonSize, lerpFloat(0.03f, 0.14f, layoutScale))
            .coerceIn(0.dp, lerpDp(8.dp, 14.dp, tabletWidthScale))
        val modeIconSize = scaledDp(iconButtonSize, 0.50f).coerceIn(18.dp, lerpDp(24.dp, 30.dp, tabletWidthScale))
        val coreSettingsIconSize = scaledDp(iconButtonSize, 0.56f).coerceIn(20.dp, lerpDp(26.dp, 32.dp, tabletWidthScale))
        val genericIconSize = scaledDp(iconButtonSize, 0.54f).coerceIn(19.dp, lerpDp(25.dp, 30.dp, tabletWidthScale))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = stripHorizontalPadding, vertical = stripVerticalPadding),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .size(iconButtonSize)
                        .focusRequester(visualizationModeFocusRequester)
                        .focusProperties {
                            left = firstAvailableActionRequester(
                                canFocusChannelControls to channelControlsFocusRequester,
                                canFocusAudioEffects to audioEffectsFocusRequester,
                                canFocusCoreSettings to coreSettingsFocusRequester,
                                canFocusVisualizationMode to visualizationModeFocusRequester
                            ) ?: visualizationModeFocusRequester
                            right = firstAvailableActionRequester(
                                canFocusCoreSettings to coreSettingsFocusRequester,
                                canFocusAudioEffects to audioEffectsFocusRequester,
                                canFocusChannelControls to channelControlsFocusRequester,
                                canFocusVisualizationMode to visualizationModeFocusRequester
                            ) ?: visualizationModeFocusRequester
                            if (transportAnchorFocusRequester != null) {
                                up = transportAnchorFocusRequester
                            }
                        }
                        .clip(CircleShape)
                        .playerFocusHalo(shape = CircleShape)
                        .focusable()
                        .combinedClickable(
                            onClick = onCycleVisualizationMode,
                            onLongClick = onOpenVisualizationPicker
                        )
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Visualization mode",
                            modifier = Modifier.size(modeIconSize),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onOpenCoreSettings,
                    enabled = canOpenCoreSettings,
                    modifier = Modifier
                        .size(iconButtonSize)
                        .focusRequester(coreSettingsFocusRequester)
                        .focusProperties {
                            left = firstAvailableActionRequester(
                                canFocusVisualizationMode to visualizationModeFocusRequester,
                                canFocusChannelControls to channelControlsFocusRequester,
                                canFocusAudioEffects to audioEffectsFocusRequester,
                                canFocusCoreSettings to coreSettingsFocusRequester
                            ) ?: coreSettingsFocusRequester
                            right = firstAvailableActionRequester(
                                canFocusAudioEffects to audioEffectsFocusRequester,
                                canFocusChannelControls to channelControlsFocusRequester,
                                canFocusVisualizationMode to visualizationModeFocusRequester,
                                canFocusCoreSettings to coreSettingsFocusRequester
                            ) ?: coreSettingsFocusRequester
                            if (transportAnchorFocusRequester != null) {
                                up = transportAnchorFocusRequester
                            }
                        }
                        .playerFocusHalo(enabled = canOpenCoreSettings, shape = CircleShape)
                        .focusable(enabled = canOpenCoreSettings)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_settings_applications),
                        contentDescription = "Open current core settings",
                        modifier = Modifier.size(coreSettingsIconSize)
                    )
                }
                IconButton(
                    onClick = onOpenAudioEffects,
                    enabled = true,
                    modifier = Modifier
                        .size(iconButtonSize)
                        .focusRequester(audioEffectsFocusRequester)
                        .focusProperties {
                            left = firstAvailableActionRequester(
                                canFocusCoreSettings to coreSettingsFocusRequester,
                                canFocusVisualizationMode to visualizationModeFocusRequester,
                                canFocusChannelControls to channelControlsFocusRequester,
                                canFocusAudioEffects to audioEffectsFocusRequester
                            ) ?: audioEffectsFocusRequester
                            right = firstAvailableActionRequester(
                                canFocusChannelControls to channelControlsFocusRequester,
                                canFocusVisualizationMode to visualizationModeFocusRequester,
                                canFocusCoreSettings to coreSettingsFocusRequester,
                                canFocusAudioEffects to audioEffectsFocusRequester
                            ) ?: audioEffectsFocusRequester
                            if (transportAnchorFocusRequester != null) {
                                up = transportAnchorFocusRequester
                            }
                        }
                        .playerFocusHalo(shape = CircleShape)
                        .focusable()
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Audio effects",
                        modifier = Modifier.size(genericIconSize)
                    )
                }
                IconButton(
                    onClick = onOpenChannelControls,
                    enabled = true,
                    modifier = Modifier
                        .size(iconButtonSize)
                        .focusRequester(channelControlsFocusRequester)
                        .focusProperties {
                            left = firstAvailableActionRequester(
                                canFocusAudioEffects to audioEffectsFocusRequester,
                                canFocusCoreSettings to coreSettingsFocusRequester,
                                canFocusVisualizationMode to visualizationModeFocusRequester,
                                canFocusChannelControls to channelControlsFocusRequester
                            ) ?: channelControlsFocusRequester
                            right = firstAvailableActionRequester(
                                canFocusVisualizationMode to visualizationModeFocusRequester,
                                canFocusCoreSettings to coreSettingsFocusRequester,
                                canFocusAudioEffects to audioEffectsFocusRequester,
                                canFocusChannelControls to channelControlsFocusRequester
                            ) ?: channelControlsFocusRequester
                            if (transportAnchorFocusRequester != null) {
                                up = transportAnchorFocusRequester
                            }
                        }
                        .playerFocusHalo(shape = CircleShape)
                        .focusable()
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_airwave),
                        contentDescription = "Channel controls",
                        modifier = Modifier.size(genericIconSize)
                    )
                }
            }
        }
    }
}

private data class ChannelControlItem(
    val name: String,
    val channelIndex: Int,
    val muted: Boolean,
    val available: Boolean = true
)

private fun sortChannelControlsForDisplay(
    items: List<ChannelControlItem>
): List<ChannelControlItem> {
    val paulaRegex = Regex("^Paula ([LR])(\\d+)$")
    if (items.isEmpty() || items.any { !paulaRegex.matches(it.name) }) {
        return items
    }
    return items.sortedWith(
        compareBy<ChannelControlItem> { item ->
            val match = paulaRegex.matchEntire(item.name)
            match?.groupValues?.get(2)?.toIntOrNull() ?: Int.MAX_VALUE
        }.thenBy { item ->
            val match = paulaRegex.matchEntire(item.name)
            val side = match?.groupValues?.get(1)
            if (side == "L") 0 else 1
        }
    )
}

@Composable
private fun ChannelControlDialog(
    onDismiss: () -> Unit
) {
    var masterChannels by remember {
        mutableStateOf(
            listOf(
                ChannelControlItem(name = "Left", channelIndex = 0, muted = false, available = true),
                ChannelControlItem(name = "Right", channelIndex = 1, muted = false, available = true)
            )
        )
    }
    var decoderChannels by remember { mutableStateOf(emptyList<ChannelControlItem>()) }

    fun loadMasterState() {
        masterChannels = masterChannels.map { channel ->
            channel.copy(muted = NativeBridge.getMasterChannelMute(channel.channelIndex))
        }
    }

    fun loadDecoderState() {
        val names = NativeBridge.getDecoderToggleChannelNames().toList()
        val availability = NativeBridge.getDecoderToggleChannelAvailability()
        val rawItems = names.mapIndexed { index, name ->
            ChannelControlItem(
                name = name,
                channelIndex = index,
                muted = NativeBridge.getDecoderToggleChannelMuted(index),
                available = availability.getOrElse(index) { true }
            )
        }
        decoderChannels = sortChannelControlsForDisplay(rawItems)
    }

    fun clearMasterSoloFlags() {
        masterChannels.forEach { channel ->
            NativeBridge.setMasterChannelSolo(channel.channelIndex, false)
        }
    }

    LaunchedEffect(Unit) {
        loadMasterState()
        while (true) {
            coroutineContext.ensureActive()
            loadDecoderState()
            delay(500)
        }
    }

    AlertDialog(
        modifier = adaptiveDialogModifier(),
        properties = adaptiveDialogProperties(),
        onDismissRequest = onDismiss,
        title = { Text("Channel controls") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Master channels",
                    style = MaterialTheme.typography.titleSmall
                )
                ChannelControlGrid(
                    items = masterChannels,
                    onToggleMute = { item ->
                        clearMasterSoloFlags()
                        NativeBridge.setMasterChannelMute(
                            item.channelIndex,
                            !item.muted
                        )
                        masterChannels = masterChannels.map { existing ->
                            if (existing.channelIndex == item.channelIndex) {
                                existing.copy(muted = !existing.muted)
                            } else {
                                existing
                            }
                        }
                    },
                    onSoloHold = { item ->
                        clearMasterSoloFlags()
                        val activeCount = masterChannels.count { !it.muted }
                        val isOnlyActive = !item.muted && activeCount == 1
                        if (isOnlyActive) {
                            masterChannels.forEach { channel ->
                                NativeBridge.setMasterChannelMute(channel.channelIndex, false)
                            }
                            masterChannels = masterChannels.map { it.copy(muted = false) }
                        } else {
                            masterChannels.forEach { channel ->
                                NativeBridge.setMasterChannelMute(
                                    channel.channelIndex,
                                    channel.channelIndex != item.channelIndex
                                )
                            }
                            masterChannels = masterChannels.map { channel ->
                                channel.copy(muted = channel.channelIndex != item.channelIndex)
                            }
                        }
                    }
                )
                if (decoderChannels.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        text = "Core channels",
                        style = MaterialTheme.typography.titleSmall
                    )
                    ChannelControlGrid(
                        items = decoderChannels,
                        showScrollbar = true,
                        onToggleMute = { item ->
                            if (!item.available) {
                                return@ChannelControlGrid
                            }
                            NativeBridge.setDecoderToggleChannelMuted(
                                item.channelIndex,
                                !item.muted
                            )
                            decoderChannels = decoderChannels.map { existing ->
                                if (existing.channelIndex == item.channelIndex) {
                                    existing.copy(muted = !existing.muted)
                                } else {
                                    existing
                                }
                            }
                        },
                        onSoloHold = { item ->
                            if (!item.available) {
                                return@ChannelControlGrid
                            }
                            val availableChannels = decoderChannels.filter { it.available }
                            val activeCount = availableChannels.count { !it.muted }
                            val isOnlyActive = !item.muted && activeCount == 1
                            if (isOnlyActive) {
                                availableChannels.forEach { channel ->
                                    NativeBridge.setDecoderToggleChannelMuted(
                                        channel.channelIndex,
                                        false
                                    )
                                }
                                decoderChannels = decoderChannels.map { channel ->
                                    if (channel.available) {
                                        channel.copy(muted = false)
                                    } else {
                                        channel
                                    }
                                }
                            } else {
                                availableChannels.forEach { channel ->
                                    NativeBridge.setDecoderToggleChannelMuted(
                                        channel.channelIndex,
                                        channel.channelIndex != item.channelIndex
                                    )
                                }
                                decoderChannels = decoderChannels.map { channel ->
                                    if (channel.available) {
                                        channel.copy(muted = channel.channelIndex != item.channelIndex)
                                    } else {
                                        channel
                                    }
                                }
                            }
                        }
                    )
                }
                HorizontalDivider()
                Text(
                    text = "Tap: mute/unmute. Long press: solo this channel (mutes others).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Unavailable channels are greyed out and update while this dialog is open.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Core-specific channel groups will be added per decoder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = {
                        clearMasterSoloFlags()
                        masterChannels.forEach { channel ->
                            NativeBridge.setMasterChannelMute(channel.channelIndex, false)
                        }
                        masterChannels = masterChannels.map { it.copy(muted = false) }
                        NativeBridge.clearDecoderToggleChannelMutes()
                        decoderChannels = decoderChannels.map { it.copy(muted = false) }
                    },
                    modifier = Modifier.align(Alignment.Start),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Unmute all")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ChannelControlGrid(
    items: List<ChannelControlItem>,
    showScrollbar: Boolean = false,
    onToggleMute: (ChannelControlItem) -> Unit,
    onSoloHold: (ChannelControlItem) -> Unit
) {
    val isPaulaSet = items.isNotEmpty() && items.all { it.name.startsWith("Paula ") }
    val columns = when {
        isPaulaSet && items.size == 4 -> 2
        items.size <= 2 -> items.size.coerceAtLeast(1)
        else -> 3
    }
    val rows = items.chunked(columns)
    val gridScrollState = rememberScrollState()
    var gridViewportHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val gridViewportHeightDp = with(density) { gridViewportHeightPx.toDp() }
    val scrollbarAlpha = rememberDialogScrollbarAlpha(
        enabled = showScrollbar,
        scrollState = gridScrollState,
        label = "channelGridScrollbarAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 176.dp)
            .onSizeChanged { gridViewportHeightPx = it.height }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(gridScrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { item ->
                        val backgroundColor = when {
                            !item.available -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            item.muted -> MaterialTheme.colorScheme.surfaceVariant
                            else -> MaterialTheme.colorScheme.primary
                        }
                        val contentColor = when {
                            !item.available -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            item.muted -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onPrimary
                        }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.large)
                                .combinedClickable(
                                    enabled = item.available,
                                    onClick = { onToggleMute(item) },
                                    onLongClick = { onSoloHold(item) }
                                ),
                            shape = MaterialTheme.shapes.large,
                            color = backgroundColor
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CompositionLocalProvider(
                                    LocalTextStyle provides MaterialTheme.typography.labelLarge.copy(
                                        color = contentColor
                                    )
                                ) {
                                    AutoSizeChipLabel(item.name)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showScrollbar && gridViewportHeightPx > 0 && gridScrollState.maxValue > 0) {
            TrackInfoDetailsScrollbar(
                scrollState = gridScrollState,
                viewportHeightPx = gridViewportHeightPx,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(vertical = 2.dp)
                    .width(4.dp)
                    .height(gridViewportHeightDp)
                    .graphicsLayer(alpha = scrollbarAlpha)
            )
        }
    }
}

@Composable
private fun AutoSizeChipLabel(
    text: String
) {
    val maxSize = 14.sp
    val minSize = 9.sp
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val baseTextStyle = LocalTextStyle.current
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val safetyPaddingPx = with(density) { 2.dp.roundToPx() }
        val availableWidthPx = (maxWidthPx - safetyPaddingPx).coerceAtLeast(1)
        val resolvedFontSize = remember(text, availableWidthPx, baseTextStyle) {
            var low = minSize.value
            var high = maxSize.value
            var best = minSize.value
            repeat(7) {
                val mid = (low + high) * 0.5f
                val layoutResult = textMeasurer.measure(
                    text = AnnotatedString(text),
                    style = baseTextStyle.copy(fontSize = mid.sp),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    constraints = Constraints(maxWidth = availableWidthPx)
                )
                if (layoutResult.hasVisualOverflow) {
                    high = mid - 0.1f
                } else {
                    best = mid
                    low = mid + 0.1f
                }
            }
            best.sp
        }
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            fontSize = resolvedFontSize,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TimelineSection(
    sliderPosition: Double,
    elapsedPositionSeconds: Double,
    durationSeconds: Double,
    canSeek: Boolean,
    hasReliableDuration: Boolean,
    seekInProgress: Boolean,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    layoutScale: Float = 1f,
    onSeekInteractionChanged: (Boolean) -> Unit,
    onSliderValueChange: (Float) -> Unit,
    onSliderValueChangeFinished: () -> Unit
) {
    val sliderMax = durationSeconds.coerceAtLeast(0.0).toFloat()
    val normalizedValue = sliderPosition.toFloat().coerceIn(0f, sliderMax)
    val seekEnabled = canSeek && durationSeconds > 0.0
    val durationText = if (durationSeconds > 0.0) {
        if (hasReliableDuration) formatTime(durationSeconds) else "${formatTime(durationSeconds)}?"
    } else {
        "-:--"
    }
    val sliderHeight = lerpDp(28.dp, 36.dp, layoutScale)
    val timeTextStyle = if (layoutScale < 0.35f) {
        MaterialTheme.typography.labelSmall
    } else {
        MaterialTheme.typography.labelMedium
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        LineageStyleSeekBar(
            value = normalizedValue,
            maxValue = sliderMax,
            enabled = seekEnabled,
            seekInProgress = seekInProgress,
            onSeekInteractionChanged = onSeekInteractionChanged,
            onValueChange = onSliderValueChange,
            onValueChangeFinished = onSliderValueChangeFinished,
            modifier = Modifier
                .then(
                    if (focusRequester != null) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    }
                )
                .focusProperties {
                    if (upFocusRequester != null) {
                        up = upFocusRequester
                    }
                }
                .fillMaxWidth()
                .height(sliderHeight)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (seekInProgress) {
                Text(
                    text = "Seeking...",
                    style = timeTextStyle,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = formatTime(elapsedPositionSeconds),
                    style = timeTextStyle
                )
            }
            Text(
                text = durationText,
                style = timeTextStyle
            )
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun LineageStyleSeekBar(
    value: Float,
    maxValue: Float,
    enabled: Boolean,
    seekInProgress: Boolean,
    onSeekInteractionChanged: (Boolean) -> Unit,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val trackHeightPx = with(density) { 8.dp.toPx() }
    val thumbWidthPx = with(density) { 6.dp.toPx() }
    val thumbHeightPx = with(density) { 26.dp.toPx() }
    val thumbGrabRadiusPx = with(density) { 20.dp.toPx() }
    val tapLaneHalfHeightPx = with(density) { 22.dp.toPx() }
    var barWidthPx by remember { mutableFloatStateOf(0f) }
    var barHeightPx by remember { mutableFloatStateOf(0f) }
    var draggingThumb by remember { mutableStateOf(false) }
    var thumbPressed by remember { mutableStateOf(false) }
    var thumbHovered by remember { mutableStateOf(false) }
    val seekFlowTransition = rememberInfiniteTransition(label = "seekFlowTransition")
    val seekFlowPhase by seekFlowTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing)
        ),
        label = "seekFlowPhase"
    )

    fun xToValue(x: Float): Float {
        if (barWidthPx <= 0f || maxValue <= 0f) return 0f
        val ratio = (x / barWidthPx).coerceIn(0f, 1f)
        return ratio * maxValue
    }

    Canvas(
        modifier = modifier
            .playerFocusHalo(enabled = true, shape = RoundedCornerShape(10.dp))
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (
                    !enabled ||
                    maxValue <= 0f ||
                    keyEvent.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN
                ) {
                    return@onPreviewKeyEvent false
                }
                when (keyEvent.key) {
                    Key.DirectionLeft -> {
                        onValueChange((value - 5f).coerceIn(0f, maxValue))
                        onValueChangeFinished()
                        true
                    }
                    Key.DirectionRight -> {
                        onValueChange((value + 5f).coerceIn(0f, maxValue))
                        onValueChangeFinished()
                        true
                    }
                    else -> false
                }
            }
            .pointerInteropFilter { event ->
                if (!enabled || barWidthPx <= 0f || maxValue <= 0f) return@pointerInteropFilter false
                val centerY = barHeightPx / 2f
                val valueRatio = if (maxValue > 0f) (value / maxValue).coerceIn(0f, 1f) else 0f
                val thumbCenterX = valueRatio * barWidthPx
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        thumbHovered = false
                        val nearTrackLane = kotlin.math.abs(event.y - centerY) <= tapLaneHalfHeightPx
                        if (!nearTrackLane) return@pointerInteropFilter false
                        val nearThumb = kotlin.math.abs(event.x - thumbCenterX) <= thumbGrabRadiusPx
                        return@pointerInteropFilter if (nearThumb) {
                            draggingThumb = true
                            thumbPressed = true
                            onSeekInteractionChanged(true)
                            onValueChange(xToValue(event.x))
                            true
                        } else {
                            onValueChange(xToValue(event.x))
                            onValueChangeFinished()
                            true
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!draggingThumb) return@pointerInteropFilter false
                        onValueChange(xToValue(event.x))
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (!draggingThumb) return@pointerInteropFilter false
                        draggingThumb = false
                        thumbPressed = false
                        onSeekInteractionChanged(false)
                        onValueChangeFinished()
                        true
                    }
                    MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_HOVER_ENTER -> {
                        val nearThumb = kotlin.math.abs(event.x - thumbCenterX) <= thumbGrabRadiusPx
                        thumbHovered = nearThumb
                        false
                    }
                    MotionEvent.ACTION_HOVER_EXIT -> {
                        thumbHovered = false
                        false
                    }
                    else -> false
                }
            }
            .onSizeChanged { canvasSize ->
                barWidthPx = canvasSize.width.toFloat()
                barHeightPx = canvasSize.height.toFloat()
            }
    ) {
        val centerY = size.height / 2f
        val top = centerY - trackHeightPx / 2f
        val trackCorner = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)
        val activeColor = colorScheme.primary
        val inactiveColor = colorScheme.surfaceVariant
        val ratio = if (maxValue > 0f) (value / maxValue).coerceIn(0f, 1f) else 0f
        val activeWidth = size.width * ratio

        drawRoundRect(
            color = inactiveColor,
            topLeft = Offset(0f, top),
            size = Size(size.width, trackHeightPx),
            cornerRadius = trackCorner
        )
        if (activeWidth > 0f) {
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(0f, top),
                size = Size(activeWidth, trackHeightPx),
                cornerRadius = trackCorner
            )
        }
        if (seekInProgress) {
            val bandWidth = size.width * 0.18f
            val travel = size.width + bandWidth
            val bandLeft = (seekFlowPhase * travel) - bandWidth
            val drawLeft = bandLeft.coerceAtLeast(0f)
            val drawRight = (bandLeft + bandWidth).coerceAtMost(size.width)
            if (drawRight > drawLeft) {
                drawRoundRect(
                    color = activeColor.copy(alpha = 0.36f),
                    topLeft = Offset(drawLeft, top),
                    size = Size(drawRight - drawLeft, trackHeightPx),
                    cornerRadius = trackCorner
                )
            }
        }

        val thumbX = activeWidth.coerceIn(0f, size.width)
        if (thumbHovered || thumbPressed || draggingThumb) {
            drawCircle(
                color = activeColor.copy(alpha = 0.22f),
                radius = with(density) { 14.dp.toPx() },
                center = Offset(thumbX, centerY)
            )
        }
        val thumbLeft = (thumbX - thumbWidthPx / 2f).coerceIn(0f, size.width - thumbWidthPx)
        val thumbTop = centerY - thumbHeightPx / 2f
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(thumbLeft, thumbTop),
            size = Size(thumbWidthPx, thumbHeightPx),
            cornerRadius = CornerRadius(thumbWidthPx / 2f, thumbWidthPx / 2f)
        )
    }
}

internal fun formatTime(seconds: Double): String {
    val safeSeconds = seconds.coerceAtLeast(0.0).roundToInt()
    val minutes = safeSeconds / 60
    val remainingSeconds = safeSeconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}

private fun formatBitrate(bitrateInBitsPerSecond: Long, isVBR: Boolean): String {
    val kbps = bitrateInBitsPerSecond / 1000.0
    val prefix = if (isVBR) "~" else ""

    return when {
        kbps >= 1000 -> String.format(java.util.Locale.US, "%s%.1f Mbps", prefix, kbps / 1000.0)
        else -> String.format(java.util.Locale.US, "%s%.0f kbps", prefix, kbps)
    }
}

private fun formatSampleRateForDetails(rateHz: Int): String {
    if (rateHz <= 0) return "Unknown"
    return if (rateHz % 1000 == 0) {
        "${rateHz / 1000} kHz"
    } else {
        String.format(java.util.Locale.US, "%.1f kHz", rateHz / 1000.0)
    }
}

internal fun formatFileSize(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble().coerceAtLeast(0.0)
    var unitIndex = 0

    while (size >= 1024.0 && unitIndex < units.lastIndex) {
        size /= 1024.0
        unitIndex++
    }

    return if (unitIndex == 0) {
        String.format(java.util.Locale.US, "%.0f %s", size, units[unitIndex])
    } else {
        String.format(java.util.Locale.US, "%.1f %s", size, units[unitIndex])
    }
}
