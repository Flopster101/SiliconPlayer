package com.flopster101.siliconplayer.ui.screens

import android.content.Context
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import android.view.MotionEvent
import com.flopster101.siliconplayer.AppDefaults
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
import com.flopster101.siliconplayer.pluginNameForCoreName
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
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
    val oscLineWidthKey = "visualization_osc_line_width_dp"
    val oscGridWidthKey = "visualization_osc_grid_width_dp"
    val oscFpsModeKey = "visualization_osc_fps_mode"
    val oscRenderBackendKey = "visualization_osc_render_backend"
    val oscVerticalGridEnabledKey = "visualization_osc_vertical_grid_enabled"
    val oscCenterLineEnabledKey = "visualization_osc_center_line_enabled"
    val oscLineNoArtworkColorModeKey = "visualization_osc_line_color_mode_no_artwork"
    val oscGridNoArtworkColorModeKey = "visualization_osc_grid_color_mode_no_artwork"
    val oscLineArtworkColorModeKey = "visualization_osc_line_color_mode_with_artwork"
    val oscGridArtworkColorModeKey = "visualization_osc_grid_color_mode_with_artwork"
    val oscCustomLineColorKey = "visualization_osc_custom_line_color_argb"
    val oscCustomGridColorKey = "visualization_osc_custom_grid_color_argb"
    val barRenderBackendKey = "visualization_bar_render_backend"
    val barColorModeNoArtworkKey = "visualization_bar_color_mode_no_artwork"
    val barColorModeWithArtworkKey = "visualization_bar_color_mode_with_artwork"
    val barCustomColorKey = "visualization_bar_custom_color_argb"
    val vuRenderBackendKey = "visualization_vu_render_backend"
    val vuColorModeNoArtworkKey = "visualization_vu_color_mode_no_artwork"
    val vuColorModeWithArtworkKey = "visualization_vu_color_mode_with_artwork"
    val vuCustomColorKey = "visualization_vu_custom_color_argb"
    var visualizationOscWindowMs by remember {
        mutableIntStateOf(prefs.getInt("visualization_osc_window_ms", 40).coerceIn(5, 200))
    }
    var visualizationOscTriggerModeNative by remember {
        mutableIntStateOf(
            when (prefs.getString("visualization_osc_trigger_mode", "rising")) {
                "rising" -> 1
                "falling" -> 2
                else -> 0
            }
        )
    }
    var visualizationOscFpsMode by remember {
        mutableStateOf(
            VisualizationOscFpsMode.fromStorage(
                prefs.getString(oscFpsModeKey, VisualizationOscFpsMode.Default.storageValue)
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
        mutableIntStateOf(prefs.getInt(oscLineWidthKey, 3).coerceIn(1, 12))
    }
    var visualizationOscGridWidthDp by remember {
        mutableIntStateOf(prefs.getInt(oscGridWidthKey, 2).coerceIn(1, 8))
    }
    var visualizationOscVerticalGridEnabled by remember {
        mutableStateOf(prefs.getBoolean(oscVerticalGridEnabledKey, false))
    }
    var visualizationOscCenterLineEnabled by remember {
        mutableStateOf(prefs.getBoolean(oscCenterLineEnabledKey, false))
    }
    var visualizationOscLineColorModeNoArtwork by remember {
        mutableStateOf(
            VisualizationOscColorMode.fromStorage(
                prefs.getString(oscLineNoArtworkColorModeKey, VisualizationOscColorMode.Monet.storageValue),
                VisualizationOscColorMode.Monet
            )
        )
    }
    var visualizationOscGridColorModeNoArtwork by remember {
        mutableStateOf(
            VisualizationOscColorMode.fromStorage(
                prefs.getString(oscGridNoArtworkColorModeKey, VisualizationOscColorMode.Monet.storageValue),
                VisualizationOscColorMode.Monet
            )
        )
    }
    var visualizationOscLineColorModeWithArtwork by remember {
        mutableStateOf(
            VisualizationOscColorMode.fromStorage(
                prefs.getString(oscLineArtworkColorModeKey, VisualizationOscColorMode.Artwork.storageValue),
                VisualizationOscColorMode.Artwork
            )
        )
    }
    var visualizationOscGridColorModeWithArtwork by remember {
        mutableStateOf(
            VisualizationOscColorMode.fromStorage(
                prefs.getString(oscGridArtworkColorModeKey, VisualizationOscColorMode.Artwork.storageValue),
                VisualizationOscColorMode.Artwork
            )
        )
    }
    var visualizationOscCustomLineColorArgb by remember {
        mutableIntStateOf(prefs.getInt(oscCustomLineColorKey, 0xFF6BD8FF.toInt()))
    }
    var visualizationOscCustomGridColorArgb by remember {
        mutableIntStateOf(prefs.getInt(oscCustomGridColorKey, 0x66FFFFFF))
    }
    var visualizationBarColorModeNoArtwork by remember {
        mutableStateOf(
            VisualizationOscColorMode.fromStorage(
                prefs.getString(barColorModeNoArtworkKey, VisualizationOscColorMode.Monet.storageValue),
                VisualizationOscColorMode.Monet
            )
        )
    }
    var visualizationBarColorModeWithArtwork by remember {
        mutableStateOf(
            VisualizationOscColorMode.fromStorage(
                prefs.getString(barColorModeWithArtworkKey, VisualizationOscColorMode.Artwork.storageValue),
                VisualizationOscColorMode.Artwork
            )
        )
    }
    var visualizationBarCustomColorArgb by remember {
        mutableIntStateOf(prefs.getInt(barCustomColorKey, 0xFF6BD8FF.toInt()))
    }
    var visualizationBarRuntimeRenderBackend by remember {
        mutableStateOf(
            VisualizationRenderBackend.fromStorage(
                prefs.getString(barRenderBackendKey, visualizationBarRenderBackend.storageValue),
                visualizationBarRenderBackend
            )
        )
    }
    var visualizationVuColorModeNoArtwork by remember {
        mutableStateOf(
            VisualizationOscColorMode.fromStorage(
                prefs.getString(vuColorModeNoArtworkKey, VisualizationOscColorMode.Monet.storageValue),
                VisualizationOscColorMode.Monet
            )
        )
    }
    var visualizationVuColorModeWithArtwork by remember {
        mutableStateOf(
            VisualizationOscColorMode.fromStorage(
                prefs.getString(vuColorModeWithArtworkKey, VisualizationOscColorMode.Artwork.storageValue),
                VisualizationOscColorMode.Artwork
            )
        )
    }
    var visualizationVuCustomColorArgb by remember {
        mutableIntStateOf(prefs.getInt(vuCustomColorKey, 0xFF6BD8FF.toInt()))
    }
    var visualizationVuRuntimeRenderBackend by remember {
        mutableStateOf(
            VisualizationRenderBackend.fromStorage(
                prefs.getString(vuRenderBackendKey, visualizationVuRenderBackend.storageValue),
                visualizationVuRenderBackend
            )
        )
    }
    val channelScopePrefs = rememberChannelScopePrefs(prefs)
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
            when (key) {
                "visualization_osc_window_ms" -> {
                    visualizationOscWindowMs =
                        sharedPrefs.getInt("visualization_osc_window_ms", 40).coerceIn(5, 200)
                }

                "visualization_osc_trigger_mode" -> {
                    visualizationOscTriggerModeNative = when (
                        sharedPrefs.getString("visualization_osc_trigger_mode", "rising")
                    ) {
                        "rising" -> 1
                        "falling" -> 2
                        else -> 0
                    }
                }
                oscFpsModeKey -> {
                    visualizationOscFpsMode = VisualizationOscFpsMode.fromStorage(
                        sharedPrefs.getString(oscFpsModeKey, VisualizationOscFpsMode.Default.storageValue)
                    )
                }
                oscRenderBackendKey -> {
                    visualizationOscRenderBackend = VisualizationRenderBackend.fromStorage(
                        sharedPrefs.getString(
                            oscRenderBackendKey,
                            AppDefaults.Visualization.Oscilloscope.renderBackend.storageValue
                        ),
                        AppDefaults.Visualization.Oscilloscope.renderBackend
                    )
                }

                oscLineWidthKey -> {
                    visualizationOscLineWidthDp =
                        sharedPrefs.getInt(oscLineWidthKey, 3).coerceIn(1, 12)
                }

                oscGridWidthKey -> {
                    visualizationOscGridWidthDp =
                        sharedPrefs.getInt(oscGridWidthKey, 2).coerceIn(1, 8)
                }

                oscVerticalGridEnabledKey -> {
                    visualizationOscVerticalGridEnabled =
                        sharedPrefs.getBoolean(oscVerticalGridEnabledKey, false)
                }
                oscCenterLineEnabledKey -> {
                    visualizationOscCenterLineEnabled =
                        sharedPrefs.getBoolean(oscCenterLineEnabledKey, false)
                }

                oscLineNoArtworkColorModeKey -> {
                    visualizationOscLineColorModeNoArtwork = VisualizationOscColorMode.fromStorage(
                        sharedPrefs.getString(
                            oscLineNoArtworkColorModeKey,
                            VisualizationOscColorMode.Monet.storageValue
                        ),
                        VisualizationOscColorMode.Monet
                    )
                }

                oscGridNoArtworkColorModeKey -> {
                    visualizationOscGridColorModeNoArtwork = VisualizationOscColorMode.fromStorage(
                        sharedPrefs.getString(
                            oscGridNoArtworkColorModeKey,
                            VisualizationOscColorMode.Monet.storageValue
                        ),
                        VisualizationOscColorMode.Monet
                    )
                }

                oscLineArtworkColorModeKey -> {
                    visualizationOscLineColorModeWithArtwork = VisualizationOscColorMode.fromStorage(
                        sharedPrefs.getString(
                            oscLineArtworkColorModeKey,
                            VisualizationOscColorMode.Artwork.storageValue
                        ),
                        VisualizationOscColorMode.Artwork
                    )
                }

                oscGridArtworkColorModeKey -> {
                    visualizationOscGridColorModeWithArtwork = VisualizationOscColorMode.fromStorage(
                        sharedPrefs.getString(
                            oscGridArtworkColorModeKey,
                            VisualizationOscColorMode.Artwork.storageValue
                        ),
                        VisualizationOscColorMode.Artwork
                    )
                }

                oscCustomLineColorKey -> {
                    visualizationOscCustomLineColorArgb =
                        sharedPrefs.getInt(oscCustomLineColorKey, 0xFF6BD8FF.toInt())
                }

                oscCustomGridColorKey -> {
                    visualizationOscCustomGridColorArgb =
                        sharedPrefs.getInt(oscCustomGridColorKey, 0x66FFFFFF)
                }

                barColorModeNoArtworkKey -> {
                    visualizationBarColorModeNoArtwork = VisualizationOscColorMode.fromStorage(
                        sharedPrefs.getString(
                            barColorModeNoArtworkKey,
                            VisualizationOscColorMode.Monet.storageValue
                        ),
                        VisualizationOscColorMode.Monet
                    )
                }

                barColorModeWithArtworkKey -> {
                    visualizationBarColorModeWithArtwork = VisualizationOscColorMode.fromStorage(
                        sharedPrefs.getString(
                            barColorModeWithArtworkKey,
                            VisualizationOscColorMode.Artwork.storageValue
                        ),
                        VisualizationOscColorMode.Artwork
                    )
                }

                barCustomColorKey -> {
                    visualizationBarCustomColorArgb =
                        sharedPrefs.getInt(barCustomColorKey, 0xFF6BD8FF.toInt())
                }
                barRenderBackendKey -> {
                    visualizationBarRuntimeRenderBackend = VisualizationRenderBackend.fromStorage(
                        sharedPrefs.getString(barRenderBackendKey, visualizationBarRenderBackend.storageValue),
                        visualizationBarRenderBackend
                    )
                }

                vuColorModeNoArtworkKey -> {
                    visualizationVuColorModeNoArtwork = VisualizationOscColorMode.fromStorage(
                        sharedPrefs.getString(
                            vuColorModeNoArtworkKey,
                            VisualizationOscColorMode.Monet.storageValue
                        ),
                        VisualizationOscColorMode.Monet
                    )
                }

                vuColorModeWithArtworkKey -> {
                    visualizationVuColorModeWithArtwork = VisualizationOscColorMode.fromStorage(
                        sharedPrefs.getString(
                            vuColorModeWithArtworkKey,
                            VisualizationOscColorMode.Artwork.storageValue
                        ),
                        VisualizationOscColorMode.Artwork
                    )
                }

                vuCustomColorKey -> {
                    visualizationVuCustomColorArgb =
                        sharedPrefs.getInt(vuCustomColorKey, 0xFF6BD8FF.toInt())
                }
                vuRenderBackendKey -> {
                    visualizationVuRuntimeRenderBackend = VisualizationRenderBackend.fromStorage(
                        sharedPrefs.getString(vuRenderBackendKey, visualizationVuRenderBackend.storageValue),
                        visualizationVuRenderBackend
                    )
                }

            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
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

    val hasTrack = file != null
    val displayTitle = title.ifBlank {
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

    // Focus management for keyboard input
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                // Only handle key down events to avoid double-triggering
                if (keyEvent.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) {
                    return@onPreviewKeyEvent false
                }

                when (keyEvent.key) {
                    // Spacebar: Play/Pause
                    Key.Spacebar -> {
                        if (hasTrack || canResumeStoppedTrack) {
                            if (isPlaying) onPause() else onPlay()
                            true
                        } else false
                    }
                    // Left Arrow: Seek backward 5 seconds (without Ctrl)
                    Key.DirectionLeft -> {
                        if (keyEvent.isCtrlPressed && canPreviousSubtune) {
                            onPreviousSubtune()
                            true
                        } else if (!keyEvent.isCtrlPressed && canSeek && durationSeconds > 0.0) {
                            val newPosition = (positionSeconds - 5.0).coerceAtLeast(0.0)
                            onSeek(newPosition)
                            true
                        } else false
                    }
                    // Right Arrow: Seek forward 5 seconds (without Ctrl)
                    Key.DirectionRight -> {
                        if (keyEvent.isCtrlPressed && canNextSubtune) {
                            onNextSubtune()
                            true
                        } else if (!keyEvent.isCtrlPressed && canSeek && durationSeconds > 0.0) {
                            val newPosition = (positionSeconds + 5.0).coerceAtMost(durationSeconds)
                            onSeek(newPosition)
                            true
                        } else false
                    }
                    // Page Up: Previous track
                    Key.PageUp -> {
                        if (hasTrack && canPreviousTrack) {
                            onPreviousTrack()
                            true
                        } else false
                    }
                    // Page Down: Next track
                    Key.PageDown -> {
                        if (hasTrack && canNextTrack) {
                            onNextTrack()
                            true
                        } else false
                    }
                    // Home: Restart song (seek to 0)
                    Key.MoveHome -> {
                        if (canSeek && durationSeconds > 0.0) {
                            onSeek(0.0)
                            true
                        } else false
                    }
                    // R: Cycle repeat mode
                    Key.R -> {
                        if (canCycleRepeatMode) {
                            onCycleRepeatMode()
                            true
                        } else false
                    }
                    // Backspace: Stop
                    Key.Backspace -> {
                        onStopAndClear()
                        true
                    }
                    else -> false
                }
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
                val compactLandscapeHeader = isLandscape && !isTabletLike
                val headerHeight = if (compactLandscapeHeader) 30.dp else 40.dp
                val navButtonSize = if (compactLandscapeHeader) 28.dp else 32.dp
                val navIconSize = if (compactLandscapeHeader) 22.dp else 24.dp
                // Keep a minimal header so the player can use more vertical space on phones.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(headerHeight),
                    contentAlignment = Alignment.CenterStart
                ) {
                    IconButton(
                        onClick = onBack,
                        enabled = enableCollapseGesture,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(navButtonSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Minimize player",
                            modifier = Modifier.size(navIconSize)
                        )
                    }
                }
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
                    val compactHeightLevel = when {
                        maxHeight < 360.dp -> 3
                        maxHeight < 460.dp -> 2
                        maxHeight < 520.dp -> 1
                        else -> 0
                    }
                    val horizontalPadding = when (compactHeightLevel) {
                        3 -> 10.dp
                        2 -> 12.dp
                        else -> 16.dp
                    }
                    val verticalPadding = when (compactHeightLevel) {
                        3 -> 6.dp
                        2 -> 8.dp
                        else -> 12.dp
                    }
                    val paneGap = when (compactHeightLevel) {
                        3 -> 12.dp
                        2 -> 14.dp
                        else -> 20.dp
                    }
                    val artPaneWeight = when (compactHeightLevel) {
                        3 -> 0.36f
                        2 -> 0.39f
                        1 -> 0.43f
                        else -> 0.48f
                    }
                    val rightPaneWeight = 1f - artPaneWeight
                    val timelineWidthFraction = when (compactHeightLevel) {
                        3 -> 0.88f
                        2 -> 0.90f
                        1 -> 0.93f
                        else -> 0.96f
                    }
                    val actionStripWidthFraction = when (compactHeightLevel) {
                        3 -> 0.86f
                        2 -> 0.88f
                        1 -> 0.91f
                        else -> 0.94f
                    }
                    val chipSpacer = when {
                        compactHeightLevel >= 3 -> 4.dp
                        compactHeightLevel >= 2 -> 6.dp
                        compactHeightLevel >= 1 -> 8.dp
                        else -> 10.dp
                    }
                    val metadataSpacer = when {
                        compactHeightLevel >= 3 -> 6.dp
                        compactHeightLevel >= 2 -> 8.dp
                        compactHeightLevel >= 1 -> 10.dp
                        else -> 12.dp
                    }
                    val timelineSpacer = when {
                        compactHeightLevel >= 3 -> 4.dp
                        compactHeightLevel >= 2 -> 6.dp
                        compactHeightLevel >= 1 -> 8.dp
                        else -> 10.dp
                    }
                    val actionStripSpacer = when {
                        compactHeightLevel >= 3 -> 6.dp
                        compactHeightLevel >= 2 -> 8.dp
                        compactHeightLevel >= 1 -> 10.dp
                        else -> 12.dp
                    }
                    val actionStripBottomPadding = if (compactHeightLevel >= 2) 2.dp else 4.dp

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
                            val artMaxByHeight = this.maxHeight * if (compactHeightLevel >= 2) 0.90f else 0.95f
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
                                visualizationOscWindowMs = visualizationOscWindowMs,
                                visualizationOscTriggerModeNative = visualizationOscTriggerModeNative,
                                visualizationOscFpsMode = visualizationOscFpsMode,
                                visualizationOscRenderBackend = visualizationOscRenderBackend,
                                visualizationBarSmoothingPercent = visualizationBarSmoothingPercent,
                                visualizationVuSmoothingPercent = visualizationVuSmoothingPercent,
                                barCount = visualizationBarCount,
                                barRoundnessDp = visualizationBarRoundnessDp,
                                barOverlayArtwork = visualizationBarOverlayArtwork,
                                barUseThemeColor = visualizationBarUseThemeColor,
                                barRenderBackend = visualizationBarRuntimeRenderBackend,
                                barColorModeNoArtwork = visualizationBarColorModeNoArtwork,
                                barColorModeWithArtwork = visualizationBarColorModeWithArtwork,
                                barCustomColorArgb = visualizationBarCustomColorArgb,
                                oscStereo = visualizationOscStereo,
                                oscLineWidthDp = visualizationOscLineWidthDp,
                                oscGridWidthDp = visualizationOscGridWidthDp,
                                oscVerticalGridEnabled = visualizationOscVerticalGridEnabled,
                                oscCenterLineEnabled = visualizationOscCenterLineEnabled,
                                oscLineColorModeNoArtwork = visualizationOscLineColorModeNoArtwork,
                                oscGridColorModeNoArtwork = visualizationOscGridColorModeNoArtwork,
                                oscLineColorModeWithArtwork = visualizationOscLineColorModeWithArtwork,
                                oscGridColorModeWithArtwork = visualizationOscGridColorModeWithArtwork,
                                oscCustomLineColorArgb = visualizationOscCustomLineColorArgb,
                                oscCustomGridColorArgb = visualizationOscCustomGridColorArgb,
                                vuAnchor = visualizationVuAnchor,
                                vuUseThemeColor = visualizationVuUseThemeColor,
                                vuRenderBackend = visualizationVuRuntimeRenderBackend,
                                vuColorModeNoArtwork = visualizationVuColorModeNoArtwork,
                                vuColorModeWithArtwork = visualizationVuColorModeWithArtwork,
                                vuCustomColorArgb = visualizationVuCustomColorArgb,
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
                            val centerLandscapeContent = compactHeightLevel == 0

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
                                    compactHeightLevel = compactHeightLevel,
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
                                    showFilename = compactHeightLevel == 0,
                                    centerSupportingMetadata = isLandscape,
                                    currentSubtuneIndex = currentSubtuneIndex,
                                    subtuneCount = subtuneCount,
                                    compactHeightLevel = compactHeightLevel
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
                                    compactHeightLevel = compactHeightLevel
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
                                        compactHeightLevel = compactHeightLevel
                                    )
                                }
                            }

                            if (!centerLandscapeContent) {
                                FutureActionStrip(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth(actionStripWidthFraction)
                                        .navigationBarsPadding()
                                        .padding(bottom = actionStripBottomPadding)
                                        .onSizeChanged { actionStripHeightPx = it.height },
                                    canOpenCoreSettings = canOpenCoreSettings,
                                    onOpenCoreSettings = onOpenCoreSettings,
                                    onCycleVisualizationMode = onCycleVisualizationMode,
                                    onOpenVisualizationPicker = { showVisualizationPickerDialog = true },
                                    onOpenAudioEffects = onOpenAudioEffects,
                                    onOpenChannelControls = { showChannelControlDialog = true },
                                    compactHeightLevel = compactHeightLevel
                                )
                            }
                        }
                    }
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val compactHeightLevel = when {
                        maxHeight < 620.dp -> 3
                        maxHeight < 690.dp -> 2
                        maxHeight < 760.dp -> 1
                        else -> 0
                    }
                    val horizontalPadding = when (compactHeightLevel) {
                        3 -> 10.dp
                        2 -> 12.dp
                        1 -> 16.dp
                        else -> 20.dp
                    }
                    val verticalPadding = when (compactHeightLevel) {
                        3 -> 8.dp
                        2 -> 10.dp
                        else -> 12.dp
                    }
                    val artWidthFraction = when (compactHeightLevel) {
                        3 -> 0.64f
                        2 -> 0.72f
                        1 -> 0.80f
                        else -> 0.86f
                    }
                    val artworkToInfoGap = when (compactHeightLevel) {
                        3 -> 8.dp
                        2 -> 10.dp
                        1 -> 11.dp
                        else -> 12.dp
                    }
                    val chipSpacer = if (compactHeightLevel >= 2) 6.dp else 10.dp
                    val metadataSpacer = if (compactHeightLevel >= 2) 8.dp else 12.dp
                    val timelineSpacer = if (compactHeightLevel >= 2) 8.dp else 10.dp
                    val actionStripSpacer = when (compactHeightLevel) {
                        3 -> 8.dp
                        2 -> 8.dp
                        1 -> 10.dp
                        else -> 14.dp
                    }
                    val actionStripWidth = if (compactHeightLevel >= 2) 0.98f else 0.94f
                    val actionStripBottomPadding = if (compactHeightLevel >= 2) 2.dp else 4.dp

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                    ) {
                        var lowerContentHeightPx by remember { mutableIntStateOf(0) }
                        var actionStripHeightPx by remember { mutableIntStateOf(0) }
                        val lowerContentHeightDp = with(density) { lowerContentHeightPx.toDp() }
                        val actionStripHeightDp = with(density) { actionStripHeightPx.toDp() }
                        val minArtworkSize = when (compactHeightLevel) {
                            3 -> 128.dp
                            2 -> 176.dp
                            1 -> 210.dp
                            else -> 240.dp
                        }
                        val maxArtworkByWidth = this@BoxWithConstraints.maxWidth * artWidthFraction
                        val maxArtworkByHeight = (
                            this@BoxWithConstraints.maxHeight -
                                lowerContentHeightDp -
                                actionStripHeightDp -
                                actionStripSpacer -
                                actionStripBottomPadding -
                                artworkToInfoGap
                            ).coerceAtLeast(minArtworkSize)
                        val artworkSize = minOf(maxArtworkByWidth, maxArtworkByHeight).coerceAtLeast(minArtworkSize)

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
                            visualizationOscWindowMs = visualizationOscWindowMs,
                            visualizationOscTriggerModeNative = visualizationOscTriggerModeNative,
                            visualizationOscFpsMode = visualizationOscFpsMode,
                            visualizationOscRenderBackend = visualizationOscRenderBackend,
                            visualizationBarSmoothingPercent = visualizationBarSmoothingPercent,
                            visualizationVuSmoothingPercent = visualizationVuSmoothingPercent,
                            barCount = visualizationBarCount,
                            barRoundnessDp = visualizationBarRoundnessDp,
                            barOverlayArtwork = visualizationBarOverlayArtwork,
                            barUseThemeColor = visualizationBarUseThemeColor,
                            barRenderBackend = visualizationBarRuntimeRenderBackend,
                            barColorModeNoArtwork = visualizationBarColorModeNoArtwork,
                            barColorModeWithArtwork = visualizationBarColorModeWithArtwork,
                            barCustomColorArgb = visualizationBarCustomColorArgb,
                            oscStereo = visualizationOscStereo,
                            oscLineWidthDp = visualizationOscLineWidthDp,
                            oscGridWidthDp = visualizationOscGridWidthDp,
                            oscVerticalGridEnabled = visualizationOscVerticalGridEnabled,
                            oscCenterLineEnabled = visualizationOscCenterLineEnabled,
                            oscLineColorModeNoArtwork = visualizationOscLineColorModeNoArtwork,
                            oscGridColorModeNoArtwork = visualizationOscGridColorModeNoArtwork,
                            oscLineColorModeWithArtwork = visualizationOscLineColorModeWithArtwork,
                            oscGridColorModeWithArtwork = visualizationOscGridColorModeWithArtwork,
                            oscCustomLineColorArgb = visualizationOscCustomLineColorArgb,
                            oscCustomGridColorArgb = visualizationOscCustomGridColorArgb,
                            vuAnchor = visualizationVuAnchor,
                            vuUseThemeColor = visualizationVuUseThemeColor,
                            vuRenderBackend = visualizationVuRuntimeRenderBackend,
                            vuColorModeNoArtwork = visualizationVuColorModeNoArtwork,
                            vuColorModeWithArtwork = visualizationVuColorModeWithArtwork,
                            vuCustomColorArgb = visualizationVuCustomColorArgb,
                            channelScopePrefs = channelScopePrefs,
                            artworkCornerRadiusDp = artworkCornerRadiusDp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .size(artworkSize)
                        )

                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .padding(
                                    top = artworkSize + artworkToInfoGap,
                                    bottom = actionStripHeightDp + actionStripSpacer + actionStripBottomPadding
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Top
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onSizeChanged { lowerContentHeightPx = it.height },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Top
                            ) {
                                TrackInfoChips(
                                    file = file,
                                    decoderName = decoderName,
                                    fileSizeBytes = file?.length() ?: 0L,
                                    sampleRateHz = sampleRateHz,
                                    channelCount = channelCount,
                                    bitDepthLabel = bitDepthLabel,
                                    compactHeightLevel = compactHeightLevel,
                                    onClick = { showTrackInfoDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(chipSpacer))
                                TrackMetadataBlock(
                                    title = displayTitle,
                                    artist = displayArtist,
                                    filename = displayFilename,
                                    filenameDisplayMode = filenameDisplayMode,
                                    decoderName = decoderName,
                                    filenameOnlyWhenTitleMissing = filenameOnlyWhenTitleMissing,
                                    currentSubtuneIndex = currentSubtuneIndex,
                                    subtuneCount = subtuneCount,
                                    compactHeightLevel = compactHeightLevel
                                )
                                Spacer(modifier = Modifier.height(metadataSpacer))
                                TimelineSection(
                                    sliderPosition = if (isSeeking) sliderPosition else animatedSliderPosition.toDouble(),
                                    elapsedPositionSeconds = if (isSeeking) sliderPosition else positionSeconds,
                                    durationSeconds = durationSeconds,
                                    canSeek = canSeek,
                                    hasReliableDuration = hasReliableDuration,
                                    seekInProgress = seekInProgress,
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
                                    compactHeightLevel = compactHeightLevel
                                )
                            }
                        }

                        FutureActionStrip(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth(actionStripWidth)
                                .navigationBarsPadding()
                                .padding(bottom = actionStripBottomPadding)
                                .onSizeChanged { actionStripHeightPx = it.height },
                            canOpenCoreSettings = canOpenCoreSettings,
                            onOpenCoreSettings = onOpenCoreSettings,
                            onCycleVisualizationMode = onCycleVisualizationMode,
                            onOpenVisualizationPicker = { showVisualizationPickerDialog = true },
                            onOpenAudioEffects = onOpenAudioEffects,
                            onOpenChannelControls = { showChannelControlDialog = true },
                            compactHeightLevel = compactHeightLevel
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

private fun toDisplayFilename(file: File): String {
    val name = file.name
    val path = file.absolutePath
    if (path.contains("/cache/remote_sources/")) {
        val normalized = name.replaceFirst(Regex("^[0-9a-fA-F]{40}_"), "")
        if (normalized.isNotBlank()) return normalized
    }
    return name
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
    compactHeightLevel: Int = 0,
    modifier: Modifier = Modifier
) {
    val formatLabel = file?.name?.let(::inferredPrimaryExtensionForName)?.uppercase() ?: "EMPTY"

    // Bitrate or file size based on decoder
    val bitrateOrSize = remember(file, decoderName, fileSizeBytes) {
        when {
            decoderName.equals("FFmpeg", ignoreCase = true) -> {
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
            "${sampleRateHz / 1000}kHz"
        } else {
            String.format("%.1fkHz", sampleRateHz / 1000.0)
        }
    } else {
        "--kHz"
    }
    val showBitDepth = decoderName.equals("FFmpeg", ignoreCase = true)
    val depthDisplay = bitDepthLabel.ifBlank { "Unknown" }
    val channelsAndDepth = when {
        channelCount > 0 && showBitDepth -> "${channelCount}ch/$depthDisplay"
        channelCount > 0 -> "${channelCount}ch"
        showBitDepth -> depthDisplay
        else -> "--ch"
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val widthCompactLevel = when {
            maxWidth <= 440.dp -> 3
            maxWidth <= 560.dp -> 2
            maxWidth <= 680.dp -> 1
            else -> 0
        }
        val heightCompactLevel = when (compactHeightLevel.coerceAtMost(3)) {
            3 -> 2
            2 -> 1
            else -> compactHeightLevel.coerceAtLeast(0)
        }
        val compactLevel = maxOf(widthCompactLevel, heightCompactLevel)
        Row(
            modifier = Modifier
                .wrapContentWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(
                when (compactLevel) {
                    3 -> 3.dp
                    2 -> 4.dp
                    1 -> 5.dp
                    else -> 7.dp
                }
            )
        ) {
            TrackInfoChip(
                icon = Icons.Default.AudioFile,
                text = formatLabel,
                compactLevel = compactLevel
            )
            if (bitrateOrSize != null) {
                TrackInfoChip(
                    icon = if (decoderName.equals("FFmpeg", ignoreCase = true))
                        PlayerChipIcons.ReadinessScore else PlayerChipIcons.HardDrive,
                    text = bitrateOrSize,
                    compactLevel = compactLevel
                )
            }
            TrackInfoChip(
                icon = Icons.Default.Equalizer,
                text = sampleRateLabel,
                compactLevel = compactLevel
            )
            TrackInfoChip(
                icon = Icons.Default.Info,
                text = channelsAndDepth,
                compactLevel = compactLevel
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
    val fileSizeBytes = file?.length() ?: 0L
    val filename = file?.name ?: "No file loaded"
    val extension = file?.name?.let(::inferredPrimaryExtensionForName)?.uppercase() ?: "UNKNOWN"
    val decoderLabel = decoderName?.ifBlank { "Unknown" } ?: "Unknown"
    val bitrateLabel = if (liveMetadata.bitrate > 0L) {
        "${formatBitrate(liveMetadata.bitrate, liveMetadata.isVbr)} (${if (liveMetadata.isVbr) "VBR" else "CBR"})"
    } else {
        "Unavailable"
    }
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
        row("Path / URL", pathOrUrlLabel)
        appendCoreTrackInfoCopyRows(
            builder = this,
            decoderName = decoderName,
            sampleRateHz = sampleRateHz,
            metadata = liveMetadata
        )
    }

    AlertDialog(
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
    compactLevel: Int
) {
    val iconSize = when (compactLevel) {
        3 -> 12.dp
        2 -> 13.dp
        1 -> 14.dp
        else -> 15.dp
    }
    val iconSlot = when (compactLevel) {
        3 -> 13.dp
        2 -> 14.dp
        else -> 15.dp
    }
    val sideInset = when (compactLevel) {
        3 -> 4.dp
        2 -> 5.dp
        1 -> 6.dp
        else -> 7.dp
    }
    val minHeight = when (compactLevel) {
        3 -> 26.dp
        2 -> 30.dp
        1 -> 32.dp
        else -> 34.dp
    }
    val iconTextGap = when (compactLevel) {
        3 -> 2.dp
        2 -> 3.dp
        else -> 4.dp
    }
    val textStartPadding = iconSlot + iconTextGap
    val textEndPadding = when (compactLevel) {
        3 -> 1.dp
        2 -> 1.dp
        else -> 2.dp
    }
    val textStyle = when {
        compactLevel >= 3 -> MaterialTheme.typography.labelSmall
        compactLevel == 2 -> MaterialTheme.typography.labelSmall
        compactLevel == 1 && text.length >= 10 -> MaterialTheme.typography.labelSmall
        else -> MaterialTheme.typography.labelMedium
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
        shape = MaterialTheme.shapes.large
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
    compactHeightLevel: Int = 0
) {
    val titleTextStyle = if (compactHeightLevel >= 2) {
        if (compactHeightLevel >= 3) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge
    } else {
        MaterialTheme.typography.headlineSmall
    }
    val artistTextStyle = if (compactHeightLevel >= 2) {
        if (compactHeightLevel >= 3) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
    } else {
        MaterialTheme.typography.titleMedium
    }
    val titleArtistSpacer = when {
        compactHeightLevel >= 3 -> 4.dp
        compactHeightLevel >= 2 -> 6.dp
        else -> 8.dp
    }
    val artistFilenameSpacer = when {
        compactHeightLevel >= 3 -> 2.dp
        compactHeightLevel >= 2 -> 4.dp
        else -> 6.dp
    }
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
                    modifier = Modifier.basicMarquee(
                        iterations = 3,
                        initialDelayMillis = 900
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
        val shouldMarqueeFilename = filename.length > 42
        if (shouldMarqueeFilename) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
            ) {
                Text(
                    text = filename,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.basicMarquee(
                        iterations = 3,
                        initialDelayMillis = 1100
                    )
                )
            }
        } else {
            Text(
                text = filename,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (centerSupportingMetadata) TextAlign.Center else TextAlign.Start,
                modifier = if (centerSupportingMetadata) Modifier.fillMaxWidth() else Modifier
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
    compactHeightLevel: Int = 0
) {
    val controlsBusy = seekInProgress || playbackStartInProgress
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val ultraCompact = compactHeightLevel >= 3
        val tight = maxWidth < 350.dp || ultraCompact
        val compact = !tight && maxWidth < 390.dp
        val sideButtonSize = when {
            ultraCompact -> 40.dp
            tight -> 48.dp
            compact -> 52.dp
            else -> 62.dp
        }
        val playButtonSize = when {
            ultraCompact -> 62.dp
            tight -> 76.dp
            compact -> 78.dp
            else -> 90.dp
        }
        val subtuneButtonSize = when {
            ultraCompact -> 40.dp
            tight -> 48.dp
            compact -> 48.dp
            else -> 56.dp
        }
        val rowGap = when {
            ultraCompact -> 6.dp
            tight -> 6.dp
            compact -> 6.dp
            else -> 10.dp
        }
        val subtuneGap = when {
            ultraCompact -> 6.dp
            tight -> 8.dp
            else -> 10.dp
        }
        val loadingSpacer = when {
            compactHeightLevel >= 3 -> 4.dp
            compactHeightLevel >= 2 -> 6.dp
            else -> 8.dp
        }
        val subtuneRowTopSpacer = when {
            compactHeightLevel >= 3 -> 4.dp
            compactHeightLevel >= 2 -> 8.dp
            else -> 10.dp
        }

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
                        modifier = Modifier.size(sideButtonSize),
                        shape = MaterialTheme.shapes.extraLarge,
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
                        modifier = Modifier.size(sideButtonSize),
                        shape = MaterialTheme.shapes.extraLarge,
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
                        BadgedBox(
                            badge = {
                                if (modeBadge.isNotEmpty()) {
                                    Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                        Text(
                                            text = modeBadge,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Loop,
                                contentDescription = "Repeat mode: ${repeatMode.label}",
                                modifier = Modifier.size(
                                    when {
                                        tight -> 20.dp
                                        compact -> 22.dp
                                        else -> 24.dp
                                    }
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(rowGap))

                FilledIconButton(
                    onClick = onPlayPause,
                    enabled = (hasTrack || canResumeStoppedTrack) && !controlsBusy,
                    modifier = Modifier.size(playButtonSize),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    if (playbackStartInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(
                                when {
                                    tight -> 24.dp
                                    compact -> 26.dp
                                    else -> 28.dp
                                }
                            ),
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
                                modifier = Modifier.size(
                                    when {
                                        tight -> 28.dp
                                        compact -> 30.dp
                                        else -> 34.dp
                                    }
                                )
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
                        modifier = Modifier.size(sideButtonSize),
                        shape = MaterialTheme.shapes.extraLarge,
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
                        modifier = Modifier.size(sideButtonSize),
                        shape = MaterialTheme.shapes.extraLarge,
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

            if (playbackStartInProgress) {
                Spacer(modifier = Modifier.height(loadingSpacer))
                Text(
                    text = "Loading track...",
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
                    modifier = Modifier.size(subtuneButtonSize),
                    shape = MaterialTheme.shapes.extraLarge,
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
                    modifier = Modifier.size(subtuneButtonSize),
                    shape = MaterialTheme.shapes.extraLarge,
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
                    modifier = Modifier.size(subtuneButtonSize),
                    shape = MaterialTheme.shapes.extraLarge,
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
    compactHeightLevel: Int = 0
) {
    val stripHorizontalPadding = when (compactHeightLevel) {
        3 -> 5.dp
        2 -> 8.dp
        else -> 10.dp
    }
    val stripVerticalPadding = when (compactHeightLevel) {
        3 -> 2.dp
        2 -> 4.dp
        else -> 6.dp
    }
    val iconButtonSize = when (compactHeightLevel) {
        3 -> 40.dp
        2 -> 44.dp
        else -> 48.dp
    }
    val modeIconSize = if (compactHeightLevel >= 2) 20.dp else 22.dp
    val coreSettingsIconSize = when (compactHeightLevel) {
        3 -> 22.dp
        2 -> 24.dp
        else -> 26.dp
    }
    Surface(
        modifier = modifier,
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
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .size(iconButtonSize)
                    .clip(MaterialTheme.shapes.extraLarge)
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
                modifier = Modifier.size(iconButtonSize)
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
                modifier = Modifier.size(iconButtonSize)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Audio effects"
                )
            }
            IconButton(
                onClick = onOpenChannelControls,
                enabled = true,
                modifier = Modifier.size(iconButtonSize)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_airwave),
                    contentDescription = "Channel controls"
                )
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
    var scrollbarVisible by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val gridViewportHeightDp = with(density) { gridViewportHeightPx.toDp() }

    LaunchedEffect(gridScrollState.isScrollInProgress, gridScrollState.maxValue) {
        if (!showScrollbar || gridScrollState.maxValue <= 0) {
            scrollbarVisible = false
            return@LaunchedEffect
        }
        if (gridScrollState.isScrollInProgress) {
            scrollbarVisible = true
        } else {
            delay(800)
            scrollbarVisible = false
        }
    }

    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (showScrollbar && scrollbarVisible && gridScrollState.maxValue > 0) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
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
                .fillMaxWidth()
                .height(36.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (seekInProgress) {
                Text(
                    text = "Seeking...",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = formatTime(elapsedPositionSeconds),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Text(
                text = durationText,
                style = MaterialTheme.typography.labelMedium
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
