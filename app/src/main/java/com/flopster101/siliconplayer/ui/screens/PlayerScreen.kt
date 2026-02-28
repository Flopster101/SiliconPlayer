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
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import com.flopster101.siliconplayer.decodePercentEncodedForDisplay
import com.flopster101.siliconplayer.formatByteCount
import com.flopster101.siliconplayer.pluginNameForCoreName
import com.flopster101.siliconplayer.RemoteLoadPhase
import com.flopster101.siliconplayer.RemoteLoadUiState
import com.flopster101.siliconplayer.RemoteLoadUiStateHolder
import com.flopster101.siliconplayer.RemotePreloadUiStateHolder
import com.flopster101.siliconplayer.rememberDialogScrollbarAlpha
import com.flopster101.siliconplayer.sanitizeRemoteCachedMetadataTitle
import com.flopster101.siliconplayer.shouldRestartCurrentTrackOnPrevious
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
private const val PREF_KEY_VIS_OSC_CONTRAST_BACKDROP_ENABLED = "visualization_osc_contrast_backdrop_enabled"
private const val PREF_KEY_VIS_BAR_RENDER_BACKEND = "visualization_bar_render_backend"
private const val PREF_KEY_VIS_BAR_FPS_MODE = "visualization_bar_fps_mode"
private const val PREF_KEY_VIS_BAR_FREQUENCY_GRID_ENABLED = "visualization_bar_frequency_grid_enabled"
private const val PREF_KEY_VIS_BAR_CONTRAST_BACKDROP_ENABLED = "visualization_bar_contrast_backdrop_enabled"
private const val PREF_KEY_VIS_BAR_COLOR_NO_ARTWORK = "visualization_bar_color_mode_no_artwork"
private const val PREF_KEY_VIS_BAR_COLOR_WITH_ARTWORK = "visualization_bar_color_mode_with_artwork"
private const val PREF_KEY_VIS_BAR_CUSTOM_COLOR = "visualization_bar_custom_color_argb"
private const val PREF_KEY_VIS_VU_RENDER_BACKEND = "visualization_vu_render_backend"
private const val PREF_KEY_VIS_VU_FPS_MODE = "visualization_vu_fps_mode"
private const val PREF_KEY_VIS_VU_CONTRAST_BACKDROP_ENABLED = "visualization_vu_contrast_backdrop_enabled"
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
    oscContrastBackdropEnabled: Boolean,
    barColorModeNoArtwork: VisualizationOscColorMode,
    barColorModeWithArtwork: VisualizationOscColorMode,
    barCustomColorArgb: Int,
    barFrequencyGridEnabled: Boolean,
    barContrastBackdropEnabled: Boolean,
    barFpsMode: VisualizationOscFpsMode,
    barRuntimeRenderBackend: VisualizationRenderBackend,
    vuColorModeNoArtwork: VisualizationOscColorMode,
    vuColorModeWithArtwork: VisualizationOscColorMode,
    vuCustomColorArgb: Int,
    vuContrastBackdropEnabled: Boolean,
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
    var oscContrastBackdropEnabled by mutableStateOf(oscContrastBackdropEnabled)
    var barColorModeNoArtwork by mutableStateOf(barColorModeNoArtwork)
    var barColorModeWithArtwork by mutableStateOf(barColorModeWithArtwork)
    var barCustomColorArgb by mutableIntStateOf(barCustomColorArgb)
    var barFrequencyGridEnabled by mutableStateOf(barFrequencyGridEnabled)
    var barContrastBackdropEnabled by mutableStateOf(barContrastBackdropEnabled)
    var barFpsMode by mutableStateOf(barFpsMode)
    var barRuntimeRenderBackend by mutableStateOf(barRuntimeRenderBackend)
    var vuColorModeNoArtwork by mutableStateOf(vuColorModeNoArtwork)
    var vuColorModeWithArtwork by mutableStateOf(vuColorModeWithArtwork)
    var vuCustomColorArgb by mutableIntStateOf(vuCustomColorArgb)
    var vuContrastBackdropEnabled by mutableStateOf(vuContrastBackdropEnabled)
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
            oscContrastBackdropEnabled = prefs.getBoolean(
                PREF_KEY_VIS_OSC_CONTRAST_BACKDROP_ENABLED,
                AppDefaults.Visualization.Oscilloscope.contrastBackdropEnabled
            ),
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
            barContrastBackdropEnabled = prefs.getBoolean(
                PREF_KEY_VIS_BAR_CONTRAST_BACKDROP_ENABLED,
                AppDefaults.Visualization.Bars.contrastBackdropEnabled
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
            vuContrastBackdropEnabled = prefs.getBoolean(
                PREF_KEY_VIS_VU_CONTRAST_BACKDROP_ENABLED,
                AppDefaults.Visualization.Vu.contrastBackdropEnabled
            ),
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
                PREF_KEY_VIS_OSC_CONTRAST_BACKDROP_ENABLED -> {
                    state.oscContrastBackdropEnabled = sharedPrefs.getBoolean(
                        PREF_KEY_VIS_OSC_CONTRAST_BACKDROP_ENABLED,
                        AppDefaults.Visualization.Oscilloscope.contrastBackdropEnabled
                    )
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
                PREF_KEY_VIS_BAR_CONTRAST_BACKDROP_ENABLED -> {
                    state.barContrastBackdropEnabled = sharedPrefs.getBoolean(
                        PREF_KEY_VIS_BAR_CONTRAST_BACKDROP_ENABLED,
                        AppDefaults.Visualization.Bars.contrastBackdropEnabled
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
                PREF_KEY_VIS_VU_CONTRAST_BACKDROP_ENABLED -> {
                    state.vuContrastBackdropEnabled = sharedPrefs.getBoolean(
                        PREF_KEY_VIS_VU_CONTRAST_BACKDROP_ENABLED,
                        AppDefaults.Visualization.Vu.contrastBackdropEnabled
                    )
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
    previousRestartsAfterThreshold: Boolean = true,
    onSeek: (Double) -> Unit,
    onPreviousTrack: () -> Unit,
    onForcePreviousTrack: () -> Unit,
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
    filenameOnlyWhenTitleMissing: Boolean = false,
    onCollapseDragProgressChanged: (Boolean) -> Unit = {}
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
    val portraitDeviceAspectRatio = if (!isLandscape && configuration.screenWidthDp > 0) {
        configuration.screenHeightDp.toFloat() / configuration.screenWidthDp.toFloat()
    } else {
        2f
    }
    val shortPortraitDevice = !isLandscape && (
        portraitDeviceAspectRatio < 1.9f ||
            configuration.screenHeightDp < 640
    )
    val collapseThresholdPx = with(density) { 132.dp.toPx() }

    LaunchedEffect(isDraggingDown) {
        onCollapseDragProgressChanged(isDraggingDown)
    }
    DisposableEffect(Unit) {
        onDispose {
            onCollapseDragProgressChanged(false)
        }
    }

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
    val panelOffsetPx = if (isDraggingDown) downwardDragPx else animatedPanelOffsetPx
    val dragFadeProgress = (panelOffsetPx / (collapseThresholdPx * 1.4f)).coerceIn(0f, 1f)
    val panelAlpha = 1f - (0.22f * dragFadeProgress)
    val topArrowFocusRequester = remember { FocusRequester() }
    val primaryContentFocusRequester = remember { FocusRequester() }

    val hasTrack = file != null
    val remoteLoadUiState = RemoteLoadUiStateHolder.current
    val sanitizedTitle = sanitizeRemoteCachedMetadataTitle(title, file)
    val displayTitle = sanitizedTitle.ifBlank {
        if (file != null) inferredDisplayTitleForName(file.name) else "No track selected"
    }
    val displayArtist = artist.ifBlank { if (hasTrack) "Unknown Artist" else "Tap a file to play" }
    val displayAlbum = if (hasTrack) {
        NativeBridge.getTrackAlbum().trim()
    } else {
        ""
    }
    val displayFilename = file?.let { toDisplayFilename(it) } ?: "No file loaded"
    val fileSizeBytes = file?.length() ?: 0L
    val formatLabel = file?.name?.let(::inferredPrimaryExtensionForName)?.uppercase() ?: "EMPTY"
    val trackBitrateOrSize by produceState<String?>(
        initialValue = null,
        file?.absolutePath,
        decoderName,
        fileSizeBytes,
        sampleRateHz
    ) {
        value = when {
            decoderName.equals(DecoderNames.FFMPEG, ignoreCase = true) -> {
                var resolved: String? = null
                repeat(8) { attempt ->
                    val bitrate = NativeBridge.getTrackBitrate()
                    val isVBR = NativeBridge.isTrackVBR()
                    if (bitrate > 0) {
                        resolved = formatBitrate(bitrate, isVBR)
                        return@repeat
                    }
                    if (attempt < 7) {
                        delay(120L)
                    }
                }
                resolved
            }
            fileSizeBytes > 0 -> formatFileSize(fileSizeBytes)
            else -> null
        }
    }
    val trackTechnicalSummary = remember(
        formatLabel,
        trackBitrateOrSize,
        sampleRateHz,
        channelCount,
        bitDepthLabel,
        decoderName
    ) {
        buildTrackTechnicalSummary(
            formatLabel = formatLabel,
            bitrateOrSize = trackBitrateOrSize,
            sampleRateHz = sampleRateHz,
            channelCount = channelCount,
            bitDepthLabel = bitDepthLabel,
            decoderName = decoderName
        )
    }
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
                                    isDraggingDown = false
                                    downwardDragPx = 0f
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
                    compactPortraitHeader = shortPortraitDevice,
                    onBack = onBack,
                    enableCollapseGesture = enableCollapseGesture,
                    focusRequester = topArrowFocusRequester,
                    downFocusRequester = if (canSeek && durationSeconds > 0.0) {
                        primaryContentFocusRequester
                    } else {
                        transportAnchorFocusRequester
                    }
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
                    val transportRowWidth = playerTransportRowWidth(maxWidth, landscapeLayoutScale)
                    val actionStripWidthFraction = lerpFloat(0.62f, 0.76f, landscapeLayoutScale)
                    val actionStripScale = (landscapeLayoutScale * 0.78f).coerceIn(0.44f, 0.76f)
                    val metadataSpacer = lerpDp(6.dp, 12.dp, landscapeLayoutScale)
                    val timelineSpacer = lerpDp(4.dp, 10.dp, landscapeLayoutScale)
                    val actionStripSpacer = lerpDp(6.dp, 12.dp, landscapeLayoutScale)
                    val landscapePaneHeight = (maxHeight - verticalPadding * 2f).coerceAtLeast(120.dp)
                    val landscapeContentWidth = (maxWidth - horizontalPadding * 2f - paneGap).coerceAtLeast(120.dp)
                    val landscapeArtPaneWidth = (landscapeContentWidth * artPaneWeight).coerceAtLeast(120.dp)
                    val landscapeArtMaxByHeight = landscapePaneHeight * lerpFloat(0.90f, 0.95f, landscapeLayoutScale)
                    val landscapeArtSize = minOf(landscapeArtPaneWidth, landscapeArtMaxByHeight).coerceAtLeast(120.dp)
                    val actionStripBottomPadding = (
                        ((landscapePaneHeight - landscapeArtSize) / 2f) +
                            lerpDp(0.dp, 2.dp, landscapeLayoutScale)
                        ).coerceAtLeast(0.dp)
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
                                oscContrastBackdropEnabled = visualizationPrefsState.oscContrastBackdropEnabled,
                                vuAnchor = visualizationVuAnchor,
                                vuUseThemeColor = visualizationVuUseThemeColor,
                                vuRenderBackend = visualizationPrefsState.vuRuntimeRenderBackend,
                                vuColorModeNoArtwork = visualizationPrefsState.vuColorModeNoArtwork,
                                vuColorModeWithArtwork = visualizationPrefsState.vuColorModeWithArtwork,
                                vuCustomColorArgb = visualizationPrefsState.vuCustomColorArgb,
                                vuContrastBackdropEnabled = visualizationPrefsState.vuContrastBackdropEnabled,
                                barContrastBackdropEnabled = visualizationPrefsState.barContrastBackdropEnabled,
                                channelScopePrefs = channelScopePrefs,
                                artworkCornerRadiusDp = artworkCornerRadiusDp,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(landscapeArtSize)
                            )
                        }

                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(rightPaneWeight)
                                .fillMaxHeight()
                        ) {
                            val rightPaneMaxWidth = maxWidth
                            val rightPaneContentWidth = minOf(rightPaneMaxWidth, transportRowWidth + 8.dp)
                            var actionStripHeightPx by remember { mutableIntStateOf(0) }
                            val actionStripHeightDp = with(density) { actionStripHeightPx.toDp() }
                            val centerLandscapeContent = landscapeLayoutScale >= 0.76f
                            val showLandscapeFilename = landscapeLayoutScale >= 0.74f
                            var centeredLandscapeContentHeightPx by remember { mutableIntStateOf(0) }
                            val centeredLandscapeContentHeightDp = with(density) {
                                centeredLandscapeContentHeightPx.toDp()
                            }
                            val centeredLandscapeReservedBottom = actionStripHeightDp + actionStripSpacer + actionStripBottomPadding
                            val centerLandscapeContentOffset = if (
                                centerLandscapeContent &&
                                    centeredLandscapeContentHeightPx > 0
                            ) {
                                val naturalBottomGap = (
                                    (maxHeight - centeredLandscapeContentHeightDp) / 2f
                                ).coerceAtLeast(0.dp)
                                val minimumBottomGap = centeredLandscapeReservedBottom +
                                    lerpDp(8.dp, 16.dp, landscapeLayoutScale)
                                (naturalBottomGap - minimumBottomGap).coerceAtMost(0.dp)
                            } else {
                                0.dp
                            }

                            Column(
                                modifier = Modifier
                                    .align(if (centerLandscapeContent) Alignment.Center else Alignment.TopCenter)
                                    .fillMaxWidth(if (centerLandscapeContent) 0.98f else 1f)
                                    .onSizeChanged {
                                        if (centerLandscapeContent) {
                                            centeredLandscapeContentHeightPx = it.height
                                        }
                                    }
                                    .offset(y = centerLandscapeContentOffset)
                                    .padding(
                                        bottom = if (centerLandscapeContent) 0.dp
                                        else actionStripHeightDp + actionStripSpacer + actionStripBottomPadding
                                    ),
                                verticalArrangement = if (centerLandscapeContent) Arrangement.Center else Arrangement.Top,
                                horizontalAlignment = if (centerLandscapeContent) Alignment.CenterHorizontally else Alignment.Start
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .width(rightPaneContentWidth)
                                ) {
                                    PortraitTrackMetadataBlock(
                                        title = displayTitle,
                                        artist = displayArtist,
                                        album = "",
                                        filename = displayFilename,
                                        filenameDisplayMode = filenameDisplayMode,
                                        decoderName = decoderName,
                                        filenameOnlyWhenTitleMissing = filenameOnlyWhenTitleMissing,
                                        currentSubtuneIndex = currentSubtuneIndex,
                                        subtuneCount = subtuneCount,
                                        onOpenSubtuneSelector = onOpenSubtuneSelector,
                                        layoutScale = landscapeLayoutScale,
                                        technicalSummary = trackTechnicalSummary,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                Spacer(modifier = Modifier.height(metadataSpacer))
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .width(rightPaneContentWidth),
                                    contentAlignment = Alignment.Center
                                ) {
                                    TimelineSection(
                                        sliderPosition = if (isSeeking) sliderPosition else positionSeconds,
                                        elapsedPositionSeconds = if (isSeeking) sliderPosition else positionSeconds,
                                        durationSeconds = durationSeconds,
                                        canSeek = canSeek,
                                        hasReliableDuration = hasReliableDuration,
                                        seekInProgress = seekInProgress,
                                        focusRequester = primaryContentFocusRequester,
                                        upFocusRequester = topArrowFocusRequester,
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
                                    positionSeconds = positionSeconds,
                                    previousRestartsAfterThreshold = previousRestartsAfterThreshold,
                                    onRestartCurrentSelection = { onSeek(0.0) },
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
                                    onForcePreviousTrack = onForcePreviousTrack,
                                    onNextTrack = onNextTrack,
                                    onPreviousSubtune = onPreviousSubtune,
                                    onNextSubtune = onNextSubtune,
                                    onOpenSubtuneSelector = onOpenSubtuneSelector,
                                    currentSubtuneIndex = currentSubtuneIndex,
                                    subtuneCount = subtuneCount,
                                    canPreviousSubtune = canPreviousSubtune,
                                    canNextSubtune = canNextSubtune,
                                    canOpenSubtuneSelector = canOpenSubtuneSelector,
                                    onStopAndClear = onStopAndClear,
                                    onCycleRepeatMode = onCycleRepeatMode,
                                    edgeAlignedWidth = rightPaneContentWidth,
                                    separateEdgeButtons = true,
                                    layoutScale = landscapeLayoutScale,
                                    transportAnchorFocusRequester = transportAnchorFocusRequester,
                                    actionStripFirstFocusRequester = actionStripFirstFocusRequester
                                )
                            }
                            FutureActionStrip(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth(actionStripWidthFraction)
                                    .onSizeChanged { actionStripHeightPx = it.height }
                                    .padding(bottom = actionStripBottomPadding),
                                canOpenCoreSettings = canOpenCoreSettings,
                                onOpenCoreSettings = onOpenCoreSettings,
                                onCycleVisualizationMode = onCycleVisualizationMode,
                                onOpenVisualizationPicker = { showVisualizationPickerDialog = true },
                                onOpenTrackInfo = { showTrackInfoDialog = true },
                                onOpenAudioEffects = onOpenAudioEffects,
                                onOpenChannelControls = { showChannelControlDialog = true },
                                compactLayout = true,
                                layoutScale = actionStripScale,
                                actionStripFirstFocusRequester = actionStripFirstFocusRequester,
                                transportAnchorFocusRequester = transportAnchorFocusRequester
                            )
                        }
                    }
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val portraitWidthScale = normalizedScale(maxWidth, compactDp = 320.dp, roomyDp = 500.dp)
                    val portraitHeightScale = normalizedScale(maxHeight, compactDp = 560.dp, roomyDp = 900.dp)
                    val shortPortraitHeightScale = normalizedScale(maxHeight, compactDp = 520.dp, roomyDp = 760.dp)
                    val portraitAspectRatio = if (maxWidth > 0.dp) {
                        (maxHeight.value / maxWidth.value).coerceAtLeast(1f)
                    } else {
                        2f
                    }
                    val compactPortraitLayout = portraitAspectRatio < 2.02f || shortPortraitHeightScale < 0.78f
                    val shortPortraitLayout = portraitAspectRatio < 1.9f || shortPortraitHeightScale < 0.62f
                    val portraitLayoutScale = (portraitHeightScale * 0.7f + portraitWidthScale * 0.3f)
                        .coerceIn(0f, 1f)
                    val portraitTimelineScale = if (shortPortraitLayout) {
                        (shortPortraitHeightScale * 0.62f + portraitWidthScale * 0.10f).coerceIn(0.38f, 0.62f)
                    } else if (compactPortraitLayout) {
                        (shortPortraitHeightScale * 0.72f).coerceIn(0.42f, 0.72f)
                    } else {
                        (
                            portraitLayoutScale * 0.55f + shortPortraitHeightScale * 0.45f
                        ).coerceIn(0f, 1f)
                    }
                    val portraitTransportScale = if (shortPortraitLayout) {
                        lerpFloat(0.86f, 0.94f, shortPortraitHeightScale)
                    } else if (compactPortraitLayout) {
                        lerpFloat(0.92f, 1f, shortPortraitHeightScale)
                    } else {
                        lerpFloat(0.84f, 1f, shortPortraitHeightScale)
                    }
                    val horizontalPadding = lerpDp(10.dp, 20.dp, portraitLayoutScale)
                    val verticalPadding = lerpDp(8.dp, 12.dp, portraitLayoutScale)
                    val artWidthFraction = if (shortPortraitLayout) {
                        (
                            lerpFloat(0.61f, 0.74f, shortPortraitHeightScale) *
                                lerpFloat(0.98f, 1.04f, portraitWidthScale)
                            ).coerceIn(0.60f, 0.76f)
                    } else {
                        (
                            lerpFloat(0.56f, 0.90f, portraitLayoutScale) *
                                lerpFloat(0.95f, 1.08f, portraitWidthScale) *
                                lerpFloat(0.98f, 1.05f, portraitHeightScale)
                            ).coerceIn(0.54f, 0.94f)
                    }
                    val artworkToInfoGap = if (shortPortraitLayout) {
                        lerpDp(6.dp, 10.dp, shortPortraitHeightScale)
                    } else if (compactPortraitLayout) {
                        lerpDp(3.dp, 8.dp, shortPortraitHeightScale)
                    } else {
                        lerpDp(4.dp, 10.dp, shortPortraitHeightScale)
                    }
                    val actionStripSpacer = if (shortPortraitLayout) {
                        lerpDp(6.dp, 10.dp, shortPortraitHeightScale)
                    } else if (compactPortraitLayout) {
                        lerpDp(3.dp, 8.dp, shortPortraitHeightScale)
                    } else {
                        lerpDp(4.dp, 10.dp, shortPortraitHeightScale)
                    }
                    val actionStripWidth = if (shortPortraitLayout) {
                        lerpFloat(0.86f, 0.92f, shortPortraitHeightScale)
                    } else {
                        lerpFloat(0.76f, 0.86f, portraitLayoutScale)
                    }
                    val actionStripBottomPadding = if (shortPortraitLayout) {
                        lerpDp(2.dp, 6.dp, shortPortraitHeightScale)
                    } else if (compactPortraitLayout) {
                        lerpDp(2.dp, 10.dp, shortPortraitHeightScale)
                    } else {
                        lerpDp(4.dp, 14.dp, shortPortraitHeightScale)
                    }
                    val portraitArtworkHeightWeight = if (shortPortraitLayout) {
                        (
                            lerpFloat(0.52f, 0.58f, shortPortraitHeightScale) +
                                lerpFloat(0.02f, 0f, portraitWidthScale)
                                - 0.03f
                            ).coerceIn(0.48f, 0.56f)
                    } else {
                        (
                            lerpFloat(0.52f, 0.62f, portraitLayoutScale) +
                                lerpFloat(0.07f, 0f, shortPortraitHeightScale) +
                                (if (compactPortraitLayout) 0.015f else 0f) -
                                0.03f
                            ).coerceIn(0.49f, 0.63f)
                    }
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
                        val portraitSectionSpacingScale = (
                            portraitLayoutScale * 0.55f +
                                normalizedScale(contentAvailableHeight, compactDp = 300.dp, roomyDp = 620.dp) * 0.45f
                            ).coerceIn(0f, 1f)
                        val balancedPortraitSpacing = !shortPortraitLayout
                        val portraitSectionGap = if (shortPortraitLayout) {
                            lerpDp(6.dp, 11.dp, portraitSectionSpacingScale)
                        } else if (compactPortraitLayout) {
                            lerpDp(8.dp, 14.dp, portraitSectionSpacingScale)
                        } else {
                            lerpDp(10.dp, 18.dp, portraitSectionSpacingScale)
                        }
                        val portraitArtworkTopPadding = if (shortPortraitLayout) {
                            lerpDp(2.dp, 6.dp, portraitSectionSpacingScale)
                        } else {
                            lerpDp(4.dp, 10.dp, portraitSectionSpacingScale)
                        }
                        val metadataSpacer = portraitSectionGap
                        val timelineSpacer = portraitSectionGap
                        val portraitArtworkTargetHeight = (
                            contentAvailableHeight * portraitArtworkHeightWeight
                        ).coerceAtLeast(minArtworkSize)
                        val portraitContentMaxWidth = lerpDp(300.dp, 440.dp, portraitWidthScale)
                        val portraitContentSideInset = if (shortPortraitLayout) 68.dp else 72.dp
                        val portraitContentWidth = minOf(
                            (this@BoxWithConstraints.maxWidth - portraitContentSideInset).coerceAtLeast(252.dp),
                            portraitContentMaxWidth
                        )
                        val portraitArtworkWidth = if (shortPortraitLayout) {
                            minOf(
                                (this@BoxWithConstraints.maxWidth - 44.dp).coerceAtLeast(minArtworkSize),
                                lerpDp(264.dp, 380.dp, portraitWidthScale)
                            )
                        } else {
                            minOf(
                                (this@BoxWithConstraints.maxWidth - 56.dp).coerceAtLeast(minArtworkSize),
                                portraitContentMaxWidth
                            )
                        }
                        val maxArtworkByWidth = minOf(
                            this@BoxWithConstraints.maxWidth * artWidthFraction,
                            portraitArtworkWidth
                        )
                        val maxArtworkByHeight = portraitArtworkTargetHeight.coerceAtLeast(minArtworkSize)
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
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = portraitArtworkTopPadding),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Top
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
                                        oscContrastBackdropEnabled = visualizationPrefsState.oscContrastBackdropEnabled,
                                        vuAnchor = visualizationVuAnchor,
                                        vuUseThemeColor = visualizationVuUseThemeColor,
                                        vuRenderBackend = visualizationPrefsState.vuRuntimeRenderBackend,
                                        vuColorModeNoArtwork = visualizationPrefsState.vuColorModeNoArtwork,
                                        vuColorModeWithArtwork = visualizationPrefsState.vuColorModeWithArtwork,
                                        vuCustomColorArgb = visualizationPrefsState.vuCustomColorArgb,
                                        vuContrastBackdropEnabled = visualizationPrefsState.vuContrastBackdropEnabled,
                                        barContrastBackdropEnabled = visualizationPrefsState.barContrastBackdropEnabled,
                                        channelScopePrefs = channelScopePrefs,
                                        artworkCornerRadiusDp = artworkCornerRadiusDp,
                                        modifier = Modifier.size(artworkSize)
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = true),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Top
                                ) {
                                    Spacer(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                    )
                                    PortraitTrackMetadataBlock(
                                        title = displayTitle,
                                        artist = displayArtist,
                                        album = displayAlbum,
                                        filename = displayFilename,
                                        filenameDisplayMode = filenameDisplayMode,
                                        decoderName = decoderName,
                                        filenameOnlyWhenTitleMissing = filenameOnlyWhenTitleMissing,
                                        currentSubtuneIndex = currentSubtuneIndex,
                                        subtuneCount = subtuneCount,
                                        onOpenSubtuneSelector = onOpenSubtuneSelector,
                                        layoutScale = portraitLayoutScale,
                                        technicalSummary = trackTechnicalSummary,
                                        modifier = Modifier.width(portraitContentWidth)
                                    )
                                    if (balancedPortraitSpacing) {
                                        Spacer(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(metadataSpacer))
                                    }
                                    Box(
                                        modifier = Modifier.width(portraitContentWidth),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        TimelineSection(
                                            sliderPosition = if (isSeeking) sliderPosition else positionSeconds,
                                            elapsedPositionSeconds = if (isSeeking) sliderPosition else positionSeconds,
                                            durationSeconds = durationSeconds,
                                            canSeek = canSeek,
                                            hasReliableDuration = hasReliableDuration,
                                            seekInProgress = seekInProgress,
                                            focusRequester = primaryContentFocusRequester,
                                            upFocusRequester = topArrowFocusRequester,
                                            layoutScale = portraitTimelineScale,
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
                                    if (balancedPortraitSpacing) {
                                        Spacer(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(timelineSpacer))
                                    }
                                    TransportControls(
                                        hasTrack = hasTrack,
                                        isPlaying = isPlaying,
                                        canResumeStoppedTrack = canResumeStoppedTrack,
                                        repeatMode = repeatMode,
                                        playbackStartInProgress = playbackStartInProgress,
                                        remoteLoadUiState = remoteLoadUiState,
                                        seekInProgress = seekInProgress,
                                        positionSeconds = positionSeconds,
                                        previousRestartsAfterThreshold = previousRestartsAfterThreshold,
                                        onRestartCurrentSelection = { onSeek(0.0) },
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
                                        onForcePreviousTrack = onForcePreviousTrack,
                                        onNextTrack = onNextTrack,
                                        onPreviousSubtune = onPreviousSubtune,
                                        onNextSubtune = onNextSubtune,
                                        onOpenSubtuneSelector = onOpenSubtuneSelector,
                                        currentSubtuneIndex = currentSubtuneIndex,
                                        subtuneCount = subtuneCount,
                                        canPreviousSubtune = canPreviousSubtune,
                                        canNextSubtune = canNextSubtune,
                                        canOpenSubtuneSelector = canOpenSubtuneSelector,
                                        onStopAndClear = onStopAndClear,
                                        onCycleRepeatMode = onCycleRepeatMode,
                                        maxClusterWidth = portraitContentWidth,
                                        compactPortraitMode = shortPortraitLayout,
                                        layoutScale = portraitTransportScale,
                                        transportAnchorFocusRequester = transportAnchorFocusRequester,
                                        actionStripFirstFocusRequester = actionStripFirstFocusRequester
                                    )
                                    Spacer(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
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
                            onOpenTrackInfo = { showTrackInfoDialog = true },
                            onOpenAudioEffects = onOpenAudioEffects,
                            onOpenChannelControls = { showChannelControlDialog = true },
                            compactLayout = true,
                            layoutScale = if (shortPortraitLayout) {
                                (shortPortraitHeightScale * 0.72f).coerceIn(0.40f, 0.62f)
                            } else if (compactPortraitLayout) {
                                (shortPortraitHeightScale * 0.8f).coerceIn(0.45f, 0.8f)
                            } else {
                                shortPortraitHeightScale
                            },
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
    compactPortraitHeader: Boolean = false,
    onBack: () -> Unit,
    enableCollapseGesture: Boolean,
    focusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null
) {
    val compactLandscapeHeader = isLandscape && !isTabletLike
    val headerHeight = when {
        compactLandscapeHeader -> 38.dp
        compactPortraitHeader -> 44.dp
        else -> 52.dp
    }
    val navButtonSize = when {
        compactLandscapeHeader -> 28.dp
        compactPortraitHeader -> 30.dp
        else -> 32.dp
    }
    val navIconSize = when {
        compactLandscapeHeader -> 22.dp
        compactPortraitHeader -> 22.dp
        else -> 24.dp
    }
    val horizontalInset = when {
        compactLandscapeHeader -> 7.dp
        compactPortraitHeader -> 7.dp
        else -> 9.dp
    }
    val topInset = when {
        compactLandscapeHeader -> 10.dp
        compactPortraitHeader -> 10.dp
        else -> 14.dp
    }
    val statusBarTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val totalHeaderHeight = statusBarTopInset + headerHeight
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeaderHeight),
        contentAlignment = Alignment.TopStart
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
                .padding(start = horizontalInset, top = statusBarTopInset + topInset)
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
    val stripped = stripRemoteCacheHashPrefix(file.name)
    return decodePercentEncodedForDisplay(stripped) ?: stripped
}

private fun normalizedScale(valueDp: Dp, compactDp: Dp, roomyDp: Dp): Float {
    if (roomyDp <= compactDp) return 1f
    return ((valueDp.value - compactDp.value) / (roomyDp.value - compactDp.value))
        .coerceIn(0f, 1f)
}

private fun playerTransportRowWidth(maxWidth: Dp, layoutScale: Float): Dp {
    val tabletWidthScale = normalizedScale(maxWidth, compactDp = 560.dp, roomyDp = 980.dp)
    val heightBias = lerpFloat(0.90f, 1f, layoutScale)
    val sideButtonMax = lerpDp(62.dp, 82.dp, tabletWidthScale)
    val playButtonMax = lerpDp(92.dp, 124.dp, tabletWidthScale)
    val sideButtonSize =
        scaledDp(maxWidth, lerpFloat(0.132f, 0.148f, layoutScale) * heightBias).coerceIn(42.dp, sideButtonMax)
    val playButtonSize = scaledDp(sideButtonSize, 1.52f).coerceIn(64.dp, playButtonMax)
    val occupiedWidth = (sideButtonSize.value * 4f + playButtonSize.value).dp
    val minGap = 6.dp
    val maxGap = lerpDp(8.dp, 14.dp, tabletWidthScale)
    val minWidth = occupiedWidth + minGap * 4f
    val maxComfortWidth = occupiedWidth + maxGap * 4f
    val preferredWidth = maxWidth * lerpFloat(0.94f, 1.0f, layoutScale)
    return minOf(maxWidth, maxComfortWidth, preferredWidth.coerceAtLeast(minWidth))
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
private fun PortraitTrackMetadataBlock(
    title: String,
    artist: String,
    album: String,
    filename: String,
    filenameDisplayMode: com.flopster101.siliconplayer.FilenameDisplayMode,
    decoderName: String?,
    filenameOnlyWhenTitleMissing: Boolean,
    currentSubtuneIndex: Int = 0,
    subtuneCount: Int = 0,
    onOpenSubtuneSelector: () -> Unit = {},
    layoutScale: Float = 1f,
    technicalSummary: String? = null,
    modifier: Modifier = Modifier
) {
    val titleTextStyle = MaterialTheme.typography.headlineSmall.copy(
        fontSize = lerpSp(18.sp, 28.sp, layoutScale),
        lineHeight = lerpSp(22.sp, 34.sp, layoutScale)
    )
    val artistTextStyle = titleTextStyle.copy(
        fontSize = lerpSp(11.5.sp, 15.5.sp, layoutScale),
        lineHeight = lerpSp(13.sp, 17.sp, layoutScale),
        fontWeight = FontWeight.Medium
    )
    val filenameTextStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = lerpSp(11.sp, 14.sp, layoutScale),
        lineHeight = lerpSp(14.sp, 18.sp, layoutScale)
    )
    val technicalSummaryTextStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = lerpSp(10.5.sp, 13.sp, layoutScale),
        lineHeight = lerpSp(13.sp, 17.sp, layoutScale)
    )
    val albumTextStyle = artistTextStyle
    val shouldShowFilename = remember(filenameDisplayMode, decoderName, title, filenameOnlyWhenTitleMissing) {
        when (filenameDisplayMode) {
            com.flopster101.siliconplayer.FilenameDisplayMode.Always -> {
                if (filenameOnlyWhenTitleMissing) title.isBlank() else true
            }
            com.flopster101.siliconplayer.FilenameDisplayMode.Never -> false
            com.flopster101.siliconplayer.FilenameDisplayMode.TrackerOnly -> {
                val decoder = decoderName?.lowercase() ?: ""
                val isTracker = decoder.contains("openmpt") || decoder.contains("libopenmpt")
                if (isTracker && filenameOnlyWhenTitleMissing) title.isBlank() else isTracker
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
    val subtuneTitleClickable = subtuneCount > 1
    val subtuneTitleFlashAlpha = remember { Animatable(0f) }
    var lastFlashedSubtuneSong by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(filename, subtuneTitleClickable) {
        if (!subtuneTitleClickable) {
            lastFlashedSubtuneSong = null
            subtuneTitleFlashAlpha.snapTo(0f)
            return@LaunchedEffect
        }
        if (filename == lastFlashedSubtuneSong) return@LaunchedEffect
        lastFlashedSubtuneSong = filename
        subtuneTitleFlashAlpha.snapTo(0f)
        repeat(2) {
            subtuneTitleFlashAlpha.animateTo(
                targetValue = 0.18f,
                animationSpec = tween(durationMillis = 280)
            )
            subtuneTitleFlashAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 340)
            )
            if (it == 0) delay(140)
        }
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top
    ) {
        if (subtuneTitleClickable) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = subtuneTitleFlashAlpha.value)
                        )
                        .clickable(onClick = onOpenSubtuneSelector)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = titleTextStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = subtuneBadge ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        maxLines = 1
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = title,
                    style = titleTextStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(modifier = Modifier.height(lerpDp(2.dp, 5.dp, layoutScale)))
        Text(
            text = artist,
            style = artistTextStyle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
        if (album.isNotBlank()) {
            Spacer(modifier = Modifier.height(lerpDp(2.dp, 4.dp, layoutScale)))
            Text(
                text = album,
                style = albumTextStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (shouldShowFilename) {
            Spacer(
                modifier = Modifier.height(
                    if (album.isNotBlank()) {
                        lerpDp(2.dp, 5.dp, layoutScale)
                    } else {
                        lerpDp(2.dp, 4.dp, layoutScale)
                    }
                )
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
            ) {
                val textMeasurer = rememberTextMeasurer()
                val density = LocalDensity.current
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = if (shouldMarqueeFilename) TextOverflow.Clip else TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (shouldMarqueeFilename) {
                                Modifier.basicMarquee(
                                    iterations = 3,
                                    initialDelayMillis = 550
                                )
                            } else {
                                Modifier
                            }
                        )
                )
            }
        }
        if (!technicalSummary.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(lerpDp(3.dp, 7.dp, layoutScale)))
            Text(
                text = technicalSummary,
                style = technicalSummaryTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
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
    onOpenSubtuneSelector: () -> Unit = {},
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
    val subtuneTitleClickable = subtuneCount > 1
    val subtuneTitleFlashAlpha = remember { Animatable(0f) }
    var lastFlashedSubtuneSong by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(filename, subtuneTitleClickable) {
        if (!subtuneTitleClickable) {
            lastFlashedSubtuneSong = null
            subtuneTitleFlashAlpha.snapTo(0f)
            return@LaunchedEffect
        }
        if (filename == lastFlashedSubtuneSong) return@LaunchedEffect
        lastFlashedSubtuneSong = filename
        subtuneTitleFlashAlpha.snapTo(0f)
        repeat(2) {
            subtuneTitleFlashAlpha.animateTo(
                targetValue = 0.18f,
                animationSpec = tween(durationMillis = 280)
            )
            subtuneTitleFlashAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 340)
            )
            if (it == 0) {
                delay(140)
            }
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
                            iterations = 3,
                            initialDelayMillis = 450
                        )
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = subtuneTitleFlashAlpha.value)
                        )
                        .then(
                            if (subtuneTitleClickable) {
                                Modifier.clickable(onClick = onOpenSubtuneSelector)
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
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
                        Spacer(modifier = Modifier.width(4.dp))
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
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
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
                                iterations = 3,
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
@OptIn(ExperimentalFoundationApi::class)
private fun TransportControls(
    hasTrack: Boolean,
    isPlaying: Boolean,
    canResumeStoppedTrack: Boolean,
    repeatMode: RepeatMode,
    playbackStartInProgress: Boolean,
    remoteLoadUiState: RemoteLoadUiState?,
    seekInProgress: Boolean,
    positionSeconds: Double,
    previousRestartsAfterThreshold: Boolean,
    onRestartCurrentSelection: () -> Unit,
    canPreviousTrack: Boolean,
    canNextTrack: Boolean,
    canCycleRepeatMode: Boolean,
    onPlayPause: () -> Unit,
    onPreviousTrack: () -> Unit,
    onForcePreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onPreviousSubtune: () -> Unit,
    onNextSubtune: () -> Unit,
    onOpenSubtuneSelector: () -> Unit,
    currentSubtuneIndex: Int,
    subtuneCount: Int,
    canPreviousSubtune: Boolean,
    canNextSubtune: Boolean,
    canOpenSubtuneSelector: Boolean,
    onStopAndClear: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    maxClusterWidth: Dp? = null,
    edgeAlignedWidth: Dp? = null,
    separateEdgeButtons: Boolean = false,
    compactPortraitMode: Boolean = false,
    layoutScale: Float = 1f,
    transportAnchorFocusRequester: FocusRequester? = null,
    actionStripFirstFocusRequester: FocusRequester? = null
) {
    val remoteLoadActive = remoteLoadUiState != null
    val remotePreloadUiState = RemotePreloadUiStateHolder.current
    val showLoadingIndicator = playbackStartInProgress || remoteLoadActive
    val controlsBusy = seekInProgress || playbackStartInProgress
    val useSubtuneTransport = subtuneCount > 1
    val hasSubtuneBefore = useSubtuneTransport && currentSubtuneIndex > 0 && canPreviousSubtune
    val hasSubtuneAfter = useSubtuneTransport && currentSubtuneIndex < (subtuneCount - 1) && canNextSubtune
    val restartCurrentBeforePrevious = useSubtuneTransport && shouldRestartCurrentTrackOnPrevious(
        previousRestartsAfterThreshold = previousRestartsAfterThreshold,
        hasTrackLoaded = hasTrack,
        positionSeconds = positionSeconds
    )
    val previousTransportTapAction = when {
        restartCurrentBeforePrevious -> onRestartCurrentSelection
        hasSubtuneBefore -> onPreviousSubtune
        else -> onPreviousTrack
    }
    val nextTransportTapAction = if (hasSubtuneAfter) onNextSubtune else onNextTrack
    val previousTransportEnabled = if (useSubtuneTransport) hasTrack else hasTrack && canPreviousTrack
    val nextTransportEnabled = if (useSubtuneTransport) hasTrack else hasTrack && canNextTrack
    val canFocusPreviousTrack = previousTransportEnabled
    val canFocusRepeatMode = canCycleRepeatMode && !controlsBusy
    val canFocusPlayPause = (hasTrack || canResumeStoppedTrack) && !controlsBusy
    val canFocusStop = true
    val canFocusNextTrack = nextTransportEnabled

    val previousTrackFocusRequester = remember { FocusRequester() }
    val repeatModeFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = transportAnchorFocusRequester ?: remember { FocusRequester() }
    val stopFocusRequester = remember { FocusRequester() }
    val nextTrackFocusRequester = remember { FocusRequester() }
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
        val transportClusterWidth = maxClusterWidth?.let { minOf(maxWidth, it) }
            ?: playerTransportRowWidth(maxWidth, layoutScale)
        val edgeAlignedTransport = separateEdgeButtons && maxClusterWidth == null && edgeAlignedWidth != null
        val transportLayoutWidth = if (edgeAlignedTransport) {
            minOf(maxWidth, edgeAlignedWidth ?: transportClusterWidth)
        } else {
            transportClusterWidth
        }
        val portraitTransportSizing = maxClusterWidth != null
        val compactPortraitTransport = portraitTransportSizing && compactPortraitMode
        val transportSizingWidth = if (portraitTransportSizing) transportClusterWidth else maxWidth
        val tabletWidthScale = normalizedScale(
            if (portraitTransportSizing) transportClusterWidth else maxWidth,
            compactDp = 560.dp,
            roomyDp = 980.dp
        )
        val effectiveButtonScale = if (portraitTransportSizing) {
            layoutScale.coerceAtLeast(0.90f)
        } else {
            layoutScale
        }
        val heightBias = if (portraitTransportSizing) 1f else lerpFloat(0.90f, 1f, layoutScale)
        val sideButtonMax = lerpDp(62.dp, 82.dp, tabletWidthScale)
        val playButtonMax = lerpDp(92.dp, 124.dp, tabletWidthScale)
        val subtuneButtonMax = lerpDp(60.dp, 80.dp, tabletWidthScale)
        val sideButtonMin = if (portraitTransportSizing) 42.dp else 42.dp
        val playButtonMin = when {
            compactPortraitTransport -> 74.dp
            portraitTransportSizing -> 80.dp
            else -> 64.dp
        }
        val sideButtonSize =
            scaledDp(transportSizingWidth, lerpFloat(0.132f, 0.148f, effectiveButtonScale) * heightBias)
                .coerceIn(sideButtonMin, sideButtonMax)
        val playButtonSize = if (portraitTransportSizing) {
            scaledDp(sideButtonSize, 1.65f).coerceIn(playButtonMin, playButtonMax)
        } else {
            scaledDp(sideButtonSize, 1.52f).coerceIn(playButtonMin, playButtonMax)
        }
        val subtuneButtonSize = scaledDp(sideButtonSize, 1.03f).coerceIn(44.dp, subtuneButtonMax)
        val occupiedWidth = (sideButtonSize.value * 4f + playButtonSize.value).dp
        val rawRowGap = ((transportClusterWidth - occupiedWidth).coerceAtLeast(0.dp) / 4f)
        val rowGap = if (maxClusterWidth != null) {
            rawRowGap.coerceAtLeast(6.dp)
        } else {
            rawRowGap.coerceIn(6.dp, lerpDp(10.dp, 16.dp, tabletWidthScale))
        }
        val sideTransportIconSize = scaledDp(sideButtonSize, 0.56f)
            .coerceIn(24.dp, lerpDp(28.dp, 34.dp, tabletWidthScale))
        val repeatIconSize = scaledDp(sideButtonSize, 0.52f).coerceIn(22.dp, lerpDp(26.dp, 32.dp, tabletWidthScale))
        val effectiveRepeatIconSize = if (compactPortraitTransport) sideTransportIconSize else repeatIconSize
        val repeatBadgeCenterOffsetX = scaledDp(sideButtonSize, 0.20f)
        val repeatBadgeCenterOffsetY = scaledDp(sideButtonSize, -0.17f)
        val repeatBadgeHorizontalPadding = scaledDp(sideButtonSize, 0.08f).coerceIn(3.dp, 6.dp)
        val repeatBadgeVerticalPadding = scaledDp(sideButtonSize, 0.03f).coerceIn(1.dp, 2.dp)
        val repeatBadgeTextSize = (sideButtonSize.value * 0.16f).coerceIn(8f, 10.5f).sp
        val loadingSpacer = if (compactPortraitTransport) {
            scaledDp(sideButtonSize, 0.10f).coerceIn(3.dp, 8.dp)
        } else {
            scaledDp(sideButtonSize, 0.14f).coerceIn(4.dp, lerpDp(8.dp, 12.dp, tabletWidthScale))
        }
        val subtuneRowTopSpacer = (
            scaledDp(sideButtonSize, 0.1f) + lerpDp(0.dp, 8.dp, layoutScale)
        ).coerceIn(3.dp, lerpDp(14.dp, 24.dp, tabletWidthScale))
        val playIndicatorSize = scaledDp(playButtonSize, 0.34f).coerceIn(24.dp, lerpDp(30.dp, 38.dp, tabletWidthScale))
        val playIconSize = scaledDp(playButtonSize, 0.42f).coerceIn(30.dp, lerpDp(40.dp, 50.dp, tabletWidthScale))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.width(transportLayoutWidth),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                FilledTonalIconButton(
                    onClick = onStopAndClear,
                    modifier = Modifier
                        .focusRequester(stopFocusRequester)
                        .size(sideButtonSize)
                        .focusProperties {
                            left = firstAvailableRequester(
                                canFocusRepeatMode to repeatModeFocusRequester,
                                canFocusNextTrack to nextTrackFocusRequester,
                                canFocusPlayPause to playPauseFocusRequester,
                                canFocusPreviousTrack to previousTrackFocusRequester,
                                canFocusStop to stopFocusRequester
                            ) ?: stopFocusRequester
                            right = firstAvailableRequester(
                                canFocusPreviousTrack to previousTrackFocusRequester,
                                canFocusPlayPause to playPauseFocusRequester,
                                canFocusNextTrack to nextTrackFocusRequester,
                                canFocusRepeatMode to repeatModeFocusRequester,
                                canFocusStop to stopFocusRequester
                            ) ?: stopFocusRequester
                            down = firstAvailableRequester(
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
                        contentDescription = "Stop",
                        modifier = Modifier.size(sideTransportIconSize)
                    )
                }
                if (edgeAlignedTransport) {
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    Spacer(modifier = Modifier.width(rowGap))
                }
                Box(
                    modifier = Modifier.size(sideButtonSize),
                    contentAlignment = Alignment.Center
                ) {
                    FilledTonalIconButton(
                        onClick = previousTransportTapAction,
                        enabled = previousTransportEnabled,
                        modifier = Modifier
                            .focusRequester(previousTrackFocusRequester)
                            .matchParentSize()
                            .focusProperties {
                                left = firstAvailableRequester(
                                    canFocusStop to stopFocusRequester,
                                    canFocusRepeatMode to repeatModeFocusRequester,
                                    canFocusNextTrack to nextTrackFocusRequester,
                                    canFocusPlayPause to playPauseFocusRequester,
                                    canFocusPreviousTrack to previousTrackFocusRequester
                                ) ?: previousTrackFocusRequester
                                right = firstAvailableRequester(
                                    canFocusPlayPause to playPauseFocusRequester,
                                    canFocusNextTrack to nextTrackFocusRequester,
                                    canFocusRepeatMode to repeatModeFocusRequester,
                                    canFocusStop to stopFocusRequester,
                                    canFocusPreviousTrack to previousTrackFocusRequester
                                ) ?: previousTrackFocusRequester
                                down = firstAvailableRequester(
                                    (actionStripFirstFocusRequester != null) to (actionStripFirstFocusRequester ?: previousTrackFocusRequester)
                                ) ?: previousTrackFocusRequester
                            }
                            .playerFocusHalo(enabled = previousTransportEnabled)
                            .focusable(enabled = previousTransportEnabled),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = if (useSubtuneTransport) {
                                Icons.Default.KeyboardDoubleArrowLeft
                            } else {
                                Icons.Default.SkipPrevious
                            },
                            contentDescription = if (useSubtuneTransport) {
                                "Previous subtune"
                            } else {
                                "Previous track"
                            },
                            modifier = Modifier.size(sideTransportIconSize)
                        )
                    }
                    if (useSubtuneTransport) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(CircleShape)
                                .combinedClickable(
                                    enabled = previousTransportEnabled,
                                    onClick = previousTransportTapAction,
                                    onLongClick = onForcePreviousTrack
                                )
                        )
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
                                canFocusPreviousTrack to previousTrackFocusRequester,
                                canFocusStop to stopFocusRequester,
                                canFocusRepeatMode to repeatModeFocusRequester,
                                canFocusNextTrack to nextTrackFocusRequester,
                                canFocusPlayPause to playPauseFocusRequester
                            ) ?: playPauseFocusRequester
                            right = firstAvailableRequester(
                                canFocusNextTrack to nextTrackFocusRequester,
                                canFocusRepeatMode to repeatModeFocusRequester,
                                canFocusStop to stopFocusRequester,
                                canFocusPreviousTrack to previousTrackFocusRequester,
                                canFocusPlayPause to playPauseFocusRequester
                            ) ?: playPauseFocusRequester
                            down = firstAvailableRequester(
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

                Box(
                    modifier = Modifier.size(sideButtonSize),
                    contentAlignment = Alignment.Center
                ) {
                    FilledTonalIconButton(
                        onClick = nextTransportTapAction,
                        enabled = nextTransportEnabled,
                        modifier = Modifier
                            .focusRequester(nextTrackFocusRequester)
                            .matchParentSize()
                            .focusProperties {
                                left = firstAvailableRequester(
                                    canFocusPlayPause to playPauseFocusRequester,
                                    canFocusPreviousTrack to previousTrackFocusRequester,
                                    canFocusStop to stopFocusRequester,
                                    canFocusRepeatMode to repeatModeFocusRequester,
                                    canFocusNextTrack to nextTrackFocusRequester
                                ) ?: nextTrackFocusRequester
                                right = firstAvailableRequester(
                                    canFocusRepeatMode to repeatModeFocusRequester,
                                    canFocusStop to stopFocusRequester,
                                    canFocusPreviousTrack to previousTrackFocusRequester,
                                    canFocusPlayPause to playPauseFocusRequester,
                                    canFocusNextTrack to nextTrackFocusRequester
                                ) ?: nextTrackFocusRequester
                                down = firstAvailableRequester(
                                    (actionStripFirstFocusRequester != null) to (actionStripFirstFocusRequester ?: nextTrackFocusRequester)
                                ) ?: nextTrackFocusRequester
                            }
                            .playerFocusHalo(enabled = nextTransportEnabled)
                            .focusable(enabled = nextTransportEnabled),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = if (useSubtuneTransport) {
                                Icons.Default.KeyboardDoubleArrowRight
                            } else {
                                Icons.Default.SkipNext
                            },
                            contentDescription = if (useSubtuneTransport) {
                                "Next subtune"
                            } else {
                                "Next track"
                            },
                            modifier = Modifier.size(sideTransportIconSize)
                        )
                    }
                    if (useSubtuneTransport) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(CircleShape)
                                .combinedClickable(
                                    enabled = nextTransportEnabled,
                                    onClick = nextTransportTapAction,
                                    onLongClick = onNextTrack
                                )
                        )
                    }
                    if (remotePreloadUiState != null) {
                        val preloadPercent = remotePreloadUiState.percent
                            ?.takeIf { it in 0..100 }
                            ?.div(100f)
                        val preloadDeterminate =
                            remotePreloadUiState.phase != RemoteLoadPhase.Connecting &&
                                remotePreloadUiState.indeterminate != true &&
                                preloadPercent != null
                        if (preloadDeterminate) {
                            CircularProgressIndicator(
                                progress = { preloadPercent ?: 0f },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .offset(y = 16.dp)
                                    .size(12.dp),
                                strokeWidth = 1.5.dp
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .offset(y = 16.dp)
                                    .size(12.dp),
                                strokeWidth = 1.5.dp
                            )
                        }
                    }
                }
                if (edgeAlignedTransport) {
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    Spacer(modifier = Modifier.width(rowGap))
                }

                FilledTonalIconButton(
                    onClick = onCycleRepeatMode,
                    enabled = canCycleRepeatMode && !controlsBusy,
                    modifier = Modifier
                        .focusRequester(repeatModeFocusRequester)
                        .size(sideButtonSize)
                        .focusProperties {
                            left = firstAvailableRequester(
                                canFocusNextTrack to nextTrackFocusRequester,
                                canFocusPlayPause to playPauseFocusRequester,
                                canFocusPreviousTrack to previousTrackFocusRequester,
                                canFocusStop to stopFocusRequester,
                                canFocusRepeatMode to repeatModeFocusRequester
                            ) ?: repeatModeFocusRequester
                            right = firstAvailableRequester(
                                canFocusStop to stopFocusRequester,
                                canFocusPreviousTrack to previousTrackFocusRequester,
                                canFocusPlayPause to playPauseFocusRequester,
                                canFocusNextTrack to nextTrackFocusRequester,
                                canFocusRepeatMode to repeatModeFocusRequester
                            ) ?: repeatModeFocusRequester
                            down = firstAvailableRequester(
                                (actionStripFirstFocusRequester != null) to (actionStripFirstFocusRequester ?: repeatModeFocusRequester)
                            ) ?: repeatModeFocusRequester
                        }
                        .playerFocusHalo(enabled = canCycleRepeatMode && !controlsBusy)
                        .focusable(enabled = canCycleRepeatMode && !controlsBusy),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (compactPortraitTransport) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else if (repeatMode != RepeatMode.None) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    val repeatModeIcon = Icons.Default.Loop
                    val modeBadgeText = when (repeatMode) {
                        RepeatMode.None -> ""
                        RepeatMode.Track -> "1"
                        RepeatMode.Subtune -> "ST"
                        RepeatMode.Playlist -> ""
                        RepeatMode.LoopPoint -> "LP"
                    }
                    val modeBadgeIcon = when (repeatMode) {
                        RepeatMode.Playlist -> Icons.AutoMirrored.Filled.List
                        else -> null
                    }
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = repeatModeIcon,
                            contentDescription = "Repeat mode: ${repeatMode.label}",
                            modifier = Modifier.size(effectiveRepeatIconSize)
                        )
                        if (!compactPortraitTransport && (modeBadgeText.isNotEmpty() || modeBadgeIcon != null)) {
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
                                if (modeBadgeIcon != null) {
                                    Icon(
                                        imageVector = modeBadgeIcon,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(
                                                horizontal = repeatBadgeHorizontalPadding,
                                                vertical = repeatBadgeVerticalPadding
                                            )
                                            .size(repeatBadgeTextSize.value.dp + 2.dp)
                                    )
                                } else {
                                    Text(
                                        text = modeBadgeText,
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
    onOpenTrackInfo: () -> Unit,
    onOpenAudioEffects: () -> Unit,
    onOpenChannelControls: () -> Unit,
    compactLayout: Boolean = false,
    layoutScale: Float = 1f,
    actionStripFirstFocusRequester: FocusRequester? = null,
    transportAnchorFocusRequester: FocusRequester? = null
) {
    val visualizationModeFocusRequester = actionStripFirstFocusRequester ?: remember { FocusRequester() }
    val coreSettingsFocusRequester = remember { FocusRequester() }
    val trackInfoFocusRequester = remember { FocusRequester() }
    val audioEffectsFocusRequester = remember { FocusRequester() }
    val channelControlsFocusRequester = remember { FocusRequester() }
    val canFocusVisualizationMode = true
    val canFocusCoreSettings = canOpenCoreSettings
    val canFocusTrackInfo = true
    val canFocusAudioEffects = true
    val canFocusChannelControls = true
    fun firstAvailableActionRequester(vararg options: Pair<Boolean, FocusRequester>): FocusRequester? {
        return options.firstOrNull { it.first }?.second
    }
    BoxWithConstraints(modifier = modifier) {
        val stripMaxWidth = maxWidth
        val tabletWidthScale = normalizedScale(stripMaxWidth, compactDp = 560.dp, roomyDp = 980.dp)
        val compactVisibleActionCount = 6
        val compactShortLayout = compactLayout && layoutScale < 0.7f
        val widthBias = lerpFloat(0.92f, 1.04f, layoutScale)
        val iconButtonMax = lerpDp(52.dp, 70.dp, tabletWidthScale)
        val iconButtonMin = if (compactShortLayout) 30.dp else 34.dp
        val iconButtonSize =
            scaledDp(stripMaxWidth, lerpFloat(0.096f, 0.118f, layoutScale) * widthBias).coerceIn(iconButtonMin, iconButtonMax)
        val stripHorizontalPadding = if (compactLayout) {
            val minPadding = if (compactShortLayout) 6.dp else 7.dp
            val maxPadding = if (compactShortLayout) lerpDp(9.dp, 13.dp, tabletWidthScale) else lerpDp(10.dp, 14.dp, tabletWidthScale)
            scaledDp(iconButtonSize, lerpFloat(0.07f, 0.10f, layoutScale))
                .coerceIn(minPadding, maxPadding)
        } else {
            scaledDp(iconButtonSize, lerpFloat(0.04f, 0.10f, layoutScale))
                .coerceIn(0.dp, lerpDp(6.dp, 12.dp, tabletWidthScale))
        }
        val stripVerticalPadding = if (compactLayout) {
            val minPadding = if (compactShortLayout) 5.dp else 6.dp
            val maxPadding = if (compactShortLayout) lerpDp(7.dp, 11.dp, tabletWidthScale) else lerpDp(10.dp, 14.dp, tabletWidthScale)
            scaledDp(iconButtonSize, lerpFloat(0.14f, 0.22f, layoutScale))
                .coerceIn(minPadding, maxPadding)
        } else {
            scaledDp(iconButtonSize, lerpFloat(0.08f, 0.16f, layoutScale))
                .coerceIn(3.dp, lerpDp(7.dp, 11.dp, tabletWidthScale))
        }
        val modeIconSize = scaledDp(iconButtonSize, 0.66f).coerceIn(22.dp, lerpDp(26.dp, 32.dp, tabletWidthScale))
        val coreSettingsIconSize = scaledDp(iconButtonSize, 0.68f).coerceIn(22.dp, lerpDp(28.dp, 34.dp, tabletWidthScale))
        val genericIconSize = scaledDp(iconButtonSize, 0.68f).coerceIn(22.dp, lerpDp(27.dp, 33.dp, tabletWidthScale))
        val compactItemSpacing = scaledDp(iconButtonSize, lerpFloat(0.14f, 0.20f, layoutScale))
            .coerceIn(
                if (compactShortLayout) 10.dp else 11.dp,
                if (compactShortLayout) lerpDp(13.dp, 19.dp, tabletWidthScale) else lerpDp(15.dp, 21.dp, tabletWidthScale)
            )
        val compactStripTargetWidth = (
            iconButtonSize.value * compactVisibleActionCount +
                compactItemSpacing.value * (compactVisibleActionCount - 1) +
                stripHorizontalPadding.value * 2f
            ).dp
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = if (compactLayout) {
                    Modifier.width(minOf(stripMaxWidth, compactStripTargetWidth))
                } else {
                    Modifier.fillMaxWidth()
                },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = stripHorizontalPadding, vertical = stripVerticalPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = if (compactLayout) {
                            Modifier.wrapContentWidth()
                        } else {
                            Modifier.fillMaxWidth()
                        },
                        horizontalArrangement = if (compactLayout) {
                            Arrangement.spacedBy(compactItemSpacing, Alignment.CenterHorizontally)
                        } else {
                            Arrangement.SpaceEvenly
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                Box(
                    modifier = Modifier
                        .size(iconButtonSize)
                        .focusRequester(visualizationModeFocusRequester)
                        .focusProperties {
                            left = firstAvailableActionRequester(
                                canFocusChannelControls to channelControlsFocusRequester,
                                canFocusAudioEffects to audioEffectsFocusRequester,
                                canFocusTrackInfo to trackInfoFocusRequester,
                                canFocusCoreSettings to coreSettingsFocusRequester,
                                canFocusVisualizationMode to visualizationModeFocusRequester
                            ) ?: visualizationModeFocusRequester
                            right = firstAvailableActionRequester(
                                canFocusCoreSettings to coreSettingsFocusRequester,
                                canFocusTrackInfo to trackInfoFocusRequester,
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
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Visualization mode",
                        modifier = Modifier.size(modeIconSize)
                    )
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
                                canFocusTrackInfo to trackInfoFocusRequester,
                                canFocusCoreSettings to coreSettingsFocusRequester
                            ) ?: coreSettingsFocusRequester
                            right = firstAvailableActionRequester(
                                canFocusTrackInfo to trackInfoFocusRequester,
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
                Box(
                    modifier = Modifier.size(iconButtonSize),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = "Subtune list placeholder",
                        modifier = Modifier.size(genericIconSize),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                }
                IconButton(
                    onClick = onOpenTrackInfo,
                    enabled = true,
                    modifier = Modifier
                        .size(iconButtonSize)
                        .focusRequester(trackInfoFocusRequester)
                        .focusProperties {
                            left = firstAvailableActionRequester(
                                canFocusCoreSettings to coreSettingsFocusRequester,
                                canFocusVisualizationMode to visualizationModeFocusRequester,
                                canFocusChannelControls to channelControlsFocusRequester,
                                canFocusAudioEffects to audioEffectsFocusRequester,
                                canFocusTrackInfo to trackInfoFocusRequester
                            ) ?: trackInfoFocusRequester
                            right = firstAvailableActionRequester(
                                canFocusAudioEffects to audioEffectsFocusRequester,
                                canFocusChannelControls to channelControlsFocusRequester,
                                canFocusVisualizationMode to visualizationModeFocusRequester,
                                canFocusCoreSettings to coreSettingsFocusRequester,
                                canFocusTrackInfo to trackInfoFocusRequester
                            ) ?: trackInfoFocusRequester
                            if (transportAnchorFocusRequester != null) {
                                up = transportAnchorFocusRequester
                            }
                        }
                        .playerFocusHalo(shape = CircleShape)
                        .focusable()
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Open track and decoder info",
                        modifier = Modifier.size(genericIconSize)
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
                                canFocusTrackInfo to trackInfoFocusRequester,
                                canFocusCoreSettings to coreSettingsFocusRequester,
                                canFocusVisualizationMode to visualizationModeFocusRequester,
                                canFocusChannelControls to channelControlsFocusRequester,
                                canFocusAudioEffects to audioEffectsFocusRequester
                            ) ?: audioEffectsFocusRequester
                            right = firstAvailableActionRequester(
                                canFocusChannelControls to channelControlsFocusRequester,
                                canFocusVisualizationMode to visualizationModeFocusRequester,
                                canFocusTrackInfo to trackInfoFocusRequester,
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
                                canFocusTrackInfo to trackInfoFocusRequester,
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
    val sliderHeight = lerpDp(30.dp, 44.dp, layoutScale)
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
            layoutScale = layoutScale,
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
    layoutScale: Float = 1f,
    onSeekInteractionChanged: (Boolean) -> Unit,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val trackHeightPx = with(density) { lerpDp(8.dp, 10.dp, layoutScale).toPx() }
    val thumbWidthPx = with(density) { lerpDp(6.dp, 7.dp, layoutScale).toPx() }
    val thumbHeightPx = with(density) { lerpDp(24.dp, 34.dp, layoutScale).toPx() }
    val thumbGrabRadiusPx = with(density) { lerpDp(18.dp, 22.dp, layoutScale).toPx() }
    val tapLaneHalfHeightPx = with(density) { lerpDp(18.dp, 24.dp, layoutScale).toPx() }
    var barWidthPx by remember { mutableFloatStateOf(0f) }
    var barHeightPx by remember { mutableFloatStateOf(0f) }
    var draggingThumb by remember { mutableStateOf(false) }
    var thumbPressed by remember { mutableStateOf(false) }
    var thumbHovered by remember { mutableStateOf(false) }
    val seekFlowPhase = if (seekInProgress) {
        val seekFlowTransition = rememberInfiniteTransition(label = "seekFlowTransition")
        seekFlowTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing)
            ),
            label = "seekFlowPhase"
        ).value
    } else {
        0f
    }

    fun xToValue(x: Float): Float {
        if (barWidthPx <= 0f || maxValue <= 0f) return 0f
        val trackStartX = thumbWidthPx / 2f
        val trackEndX = (barWidthPx - thumbWidthPx / 2f).coerceAtLeast(trackStartX)
        val trackWidth = (trackEndX - trackStartX).coerceAtLeast(0f)
        if (trackWidth <= 0f) return 0f
        val clampedX = x.coerceIn(trackStartX, trackEndX)
        val ratio = ((clampedX - trackStartX) / trackWidth).coerceIn(0f, 1f)
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
                val trackStartX = thumbWidthPx / 2f
                val trackEndX = (barWidthPx - thumbWidthPx / 2f).coerceAtLeast(trackStartX)
                val trackWidth = (trackEndX - trackStartX).coerceAtLeast(0f)
                val thumbCenterX = trackStartX + trackWidth * valueRatio
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
        val trackStartX = thumbWidthPx / 2f
        val trackEndX = (size.width - thumbWidthPx / 2f).coerceAtLeast(trackStartX)
        val trackWidth = (trackEndX - trackStartX).coerceAtLeast(0f)
        val activeWidth = trackWidth * ratio

        drawRoundRect(
            color = inactiveColor,
            topLeft = Offset(trackStartX, top),
            size = Size(trackWidth, trackHeightPx),
            cornerRadius = trackCorner
        )
        if (activeWidth > 0f) {
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(trackStartX, top),
                size = Size(activeWidth, trackHeightPx),
                cornerRadius = trackCorner
            )
        }
        if (seekInProgress) {
            val bandWidth = trackWidth * 0.18f
            val travel = trackWidth + bandWidth
            val bandLeft = (seekFlowPhase * travel) - bandWidth
            val drawLeft = (trackStartX + bandLeft).coerceAtLeast(trackStartX)
            val drawRight = (trackStartX + bandLeft + bandWidth).coerceAtMost(trackEndX)
            if (drawRight > drawLeft) {
                drawRoundRect(
                    color = activeColor.copy(alpha = 0.36f),
                    topLeft = Offset(drawLeft, top),
                    size = Size(drawRight - drawLeft, trackHeightPx),
                    cornerRadius = trackCorner
                )
            }
        }

        val thumbX = (trackStartX + activeWidth).coerceIn(trackStartX, trackEndX)
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

private fun buildTrackTechnicalSummary(
    formatLabel: String,
    bitrateOrSize: String?,
    sampleRateHz: Int,
    channelCount: Int,
    bitDepthLabel: String,
    decoderName: String?
): String {
    val sampleRateLabel = if (sampleRateHz > 0) {
        formatSampleRateForDetails(sampleRateHz)
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
    return listOfNotNull(
        formatLabel,
        bitrateOrSize,
        sampleRateLabel,
        channelsAndDepth
    ).joinToString(" • ")
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
