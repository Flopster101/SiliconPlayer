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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Extension
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
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import android.view.MotionEvent
import com.flopster101.siliconplayer.RepeatMode
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.VisualizationChannelScopeLayout
import com.flopster101.siliconplayer.VisualizationOscColorMode
import com.flopster101.siliconplayer.VisualizationOscFpsMode
import com.flopster101.siliconplayer.VisualizationVuAnchor
import com.flopster101.siliconplayer.pluginNameForCoreName
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
    onSeek: (Double) -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onPreviousSubtune: () -> Unit,
    onNextSubtune: () -> Unit,
    onOpenSubtuneSelector: () -> Unit,
    canPreviousSubtune: Boolean,
    canNextSubtune: Boolean,
    canOpenSubtuneSelector: Boolean,
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
    visualizationOscStereo: Boolean,
    visualizationVuAnchor: VisualizationVuAnchor,
    visualizationVuUseThemeColor: Boolean,
    visualizationVuSmoothingPercent: Int,
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
    val oscVerticalGridEnabledKey = "visualization_osc_vertical_grid_enabled"
    val oscCenterLineEnabledKey = "visualization_osc_center_line_enabled"
    val oscLineNoArtworkColorModeKey = "visualization_osc_line_color_mode_no_artwork"
    val oscGridNoArtworkColorModeKey = "visualization_osc_grid_color_mode_no_artwork"
    val oscLineArtworkColorModeKey = "visualization_osc_line_color_mode_with_artwork"
    val oscGridArtworkColorModeKey = "visualization_osc_grid_color_mode_with_artwork"
    val oscCustomLineColorKey = "visualization_osc_custom_line_color_argb"
    val oscCustomGridColorKey = "visualization_osc_custom_grid_color_argb"
    val barColorModeNoArtworkKey = "visualization_bar_color_mode_no_artwork"
    val barColorModeWithArtworkKey = "visualization_bar_color_mode_with_artwork"
    val barCustomColorKey = "visualization_bar_custom_color_argb"
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
        if (file != null) file.nameWithoutExtension.ifBlank { file.name } else "No track selected"
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
                CenterAlignedTopAppBar(
                    title = { Text("Now Playing") },
                    navigationIcon = {
                        IconButton(onClick = onBack, enabled = enableCollapseGesture) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Minimize player"
                            )
                        }
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
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AlbumArtPlaceholder(
                        file = file,
                        isPlaying = isPlaying,
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
                        visualizationBarSmoothingPercent = visualizationBarSmoothingPercent,
                        visualizationVuSmoothingPercent = visualizationVuSmoothingPercent,
                        barCount = visualizationBarCount,
                        barRoundnessDp = visualizationBarRoundnessDp,
                        barOverlayArtwork = visualizationBarOverlayArtwork,
                        barUseThemeColor = visualizationBarUseThemeColor,
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
                        vuColorModeNoArtwork = visualizationVuColorModeNoArtwork,
                        vuColorModeWithArtwork = visualizationVuColorModeWithArtwork,
                        vuCustomColorArgb = visualizationVuCustomColorArgb,
                        channelScopePrefs = channelScopePrefs,
                        artworkCornerRadiusDp = artworkCornerRadiusDp,
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight(0.84f)
                    )

                    Column(
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight(0.86f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start
                    ) {
                        TrackInfoChips(
                        file = file,
                        decoderName = decoderName,
                        fileSizeBytes = file?.length() ?: 0L,
                        sampleRateHz = sampleRateHz,
                        channelCount = channelCount,
                        bitDepthLabel = bitDepthLabel,
                        onClick = { showTrackInfoDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        TrackMetadataBlock(
                            title = displayTitle,
                            artist = displayArtist,
                            filename = displayFilename,
                            filenameDisplayMode = filenameDisplayMode,
                            decoderName = decoderName,
                            filenameOnlyWhenTitleMissing = filenameOnlyWhenTitleMissing
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        TimelineSection(
                            sliderPosition = if (isSeeking) sliderPosition else animatedSliderPosition.toDouble(),
                            elapsedPositionSeconds = if (isSeeking) sliderPosition else positionSeconds,
                            durationSeconds = durationSeconds,
                            canSeek = canSeek,
                            hasReliableDuration = hasReliableDuration,
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
                        Spacer(modifier = Modifier.height(10.dp))
                        TransportControls(
                            hasTrack = hasTrack,
                            isPlaying = isPlaying,
                            canResumeStoppedTrack = canResumeStoppedTrack,
                            repeatMode = repeatMode,
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
                            onCycleRepeatMode = onCycleRepeatMode
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FutureActionStrip(
                            canOpenCoreSettings = canOpenCoreSettings,
                            onOpenCoreSettings = onOpenCoreSettings,
                            onCycleVisualizationMode = onCycleVisualizationMode,
                            onOpenVisualizationPicker = { showVisualizationPickerDialog = true },
                            onOpenAudioEffects = onOpenAudioEffects
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    AlbumArtPlaceholder(
                        file = file,
                        isPlaying = isPlaying,
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
                        visualizationBarSmoothingPercent = visualizationBarSmoothingPercent,
                        visualizationVuSmoothingPercent = visualizationVuSmoothingPercent,
                        barCount = visualizationBarCount,
                        barRoundnessDp = visualizationBarRoundnessDp,
                        barOverlayArtwork = visualizationBarOverlayArtwork,
                        barUseThemeColor = visualizationBarUseThemeColor,
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
                        vuColorModeNoArtwork = visualizationVuColorModeNoArtwork,
                        vuColorModeWithArtwork = visualizationVuColorModeWithArtwork,
                        vuCustomColorArgb = visualizationVuCustomColorArgb,
                        channelScopePrefs = channelScopePrefs,
                        artworkCornerRadiusDp = artworkCornerRadiusDp,
                        modifier = Modifier
                            .fillMaxWidth(0.86f)
                            .aspectRatio(1f)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    TrackInfoChips(
                        file = file,
                        decoderName = decoderName,
                        fileSizeBytes = file?.length() ?: 0L,
                        sampleRateHz = sampleRateHz,
                        channelCount = channelCount,
                        bitDepthLabel = bitDepthLabel,
                        onClick = { showTrackInfoDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TrackMetadataBlock(
                        title = displayTitle,
                        artist = displayArtist,
                        filename = displayFilename,
                        filenameDisplayMode = filenameDisplayMode,
                        decoderName = decoderName,
                        filenameOnlyWhenTitleMissing = filenameOnlyWhenTitleMissing
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TimelineSection(
                        sliderPosition = if (isSeeking) sliderPosition else animatedSliderPosition.toDouble(),
                        elapsedPositionSeconds = if (isSeeking) sliderPosition else positionSeconds,
                        durationSeconds = durationSeconds,
                        canSeek = canSeek,
                        hasReliableDuration = hasReliableDuration,
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
                    Spacer(modifier = Modifier.height(10.dp))
                    TransportControls(
                        hasTrack = hasTrack,
                        isPlaying = isPlaying,
                        canResumeStoppedTrack = canResumeStoppedTrack,
                        repeatMode = repeatMode,
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
                        onCycleRepeatMode = onCycleRepeatMode
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FutureActionStrip(
                        modifier = Modifier.fillMaxWidth(0.94f),
                        canOpenCoreSettings = canOpenCoreSettings,
                        onOpenCoreSettings = onOpenCoreSettings,
                        onCycleVisualizationMode = onCycleVisualizationMode,
                        onOpenVisualizationPicker = { showVisualizationPickerDialog = true },
                        onOpenAudioEffects = onOpenAudioEffects
                    )
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
        val dialogConfiguration = LocalConfiguration.current
        val optionScrollState = rememberScrollState()
        var optionViewportHeightPx by remember { mutableFloatStateOf(0f) }
        val listMaxHeight = dialogConfiguration.screenHeightDp.dp * 0.42f
        val basicModes = availableVisualizationModes.filter {
            it == VisualizationMode.Off ||
                it == VisualizationMode.Bars ||
                it == VisualizationMode.Oscilloscope ||
                it == VisualizationMode.VuMeters
        }
        val advancedModes = availableVisualizationModes.filterNot { basicModes.contains(it) }
        val scrollbarThumbFraction = remember(optionScrollState.maxValue, optionViewportHeightPx) {
            if (optionViewportHeightPx <= 0f) {
                1f
            } else {
                val contentHeight = optionViewportHeightPx + optionScrollState.maxValue.toFloat()
                (optionViewportHeightPx / contentHeight).coerceIn(0.08f, 1f)
            }
        }
        val scrollbarOffsetFraction = remember(optionScrollState.value, optionScrollState.maxValue) {
            if (optionScrollState.maxValue <= 0) 0f
            else optionScrollState.value.toFloat() / optionScrollState.maxValue.toFloat()
        }
        AlertDialog(
            onDismissRequest = { showVisualizationPickerDialog = false },
            title = { Text("Visualization mode") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Available visualizations depend on the current core and song.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = listMaxHeight)
                            .onSizeChanged { optionViewportHeightPx = it.height.toFloat() }
                    ) {
                        CompositionLocalProvider(
                            androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement provides false
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 10.dp)
                                    .verticalScroll(optionScrollState),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "Basic",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 1.dp)
                                )
                                basicModes.forEach { mode ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onSelectVisualizationMode(mode)
                                                showVisualizationPickerDialog = false
                                            }
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = mode == visualizationMode,
                                            onClick = {
                                                onSelectVisualizationMode(mode)
                                                showVisualizationPickerDialog = false
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = mode.label,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Advanced",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 1.dp)
                                )
                                if (advancedModes.isEmpty()) {
                                    Text(
                                        text = "No advanced visualizations available yet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                } else {
                                    advancedModes.forEach { mode ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onSelectVisualizationMode(mode)
                                                    showVisualizationPickerDialog = false
                                                }
                                                .padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = mode == visualizationMode,
                                                onClick = {
                                                    onSelectVisualizationMode(mode)
                                                    showVisualizationPickerDialog = false
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = mode.label,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (optionScrollState.maxValue > 0 && optionViewportHeightPx > 0f) {
                            val thumbHeightPx = optionViewportHeightPx * scrollbarThumbFraction
                            val maxOffsetPx = (optionViewportHeightPx - thumbHeightPx).coerceAtLeast(0f)
                            val thumbOffsetPx = maxOffsetPx * scrollbarOffsetFraction
                            val dialogDensity = LocalDensity.current
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(4.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(y = with(dialogDensity) { thumbOffsetPx.toDp() })
                                    .width(4.dp)
                                    .height(with(dialogDensity) { thumbHeightPx.toDp() })
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f))
                            )
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showVisualizationPickerDialog = false }) {
                    Text("Close")
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (visualizationMode != VisualizationMode.Off) {
                        TextButton(onClick = {
                            showVisualizationPickerDialog = false
                            onOpenSelectedVisualizationSettings()
                        }) {
                            Text("Selected settings")
                        }
                    }
                    TextButton(onClick = {
                        showVisualizationPickerDialog = false
                        onOpenVisualizationSettings()
                    }) {
                        Text("Settings")
                    }
                }
            }
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
    modifier: Modifier = Modifier
) {
    val formatLabel = file?.extension?.uppercase()?.ifBlank { "UNKNOWN" } ?: "EMPTY"

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
    val depthDisplay = bitDepthLabel.ifBlank { "Unknown" }
    val channelsAndDepth = if (channelCount > 0) {
        "${channelCount}ch/$depthDisplay"
    } else {
        depthDisplay
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val compactLevel = when {
        screenWidthDp <= 400 -> 2
        screenWidthDp <= 460 -> 1
        else -> 0
    }
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .wrapContentWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(
                when (compactLevel) {
                    2 -> 3.dp
                    1 -> 4.dp
                    else -> 6.dp
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
                        Icons.Default.Speed else Icons.Default.Info,
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
    var liveBitrate by remember { mutableLongStateOf(0L) }
    var liveIsVbr by remember { mutableStateOf(false) }
    var liveRenderRateHz by remember { mutableIntStateOf(0) }
    var liveOutputRateHz by remember { mutableIntStateOf(0) }
    var liveComposer by remember { mutableStateOf("") }
    var liveGenre by remember { mutableStateOf("") }
    var openMptTypeLong by remember { mutableStateOf("") }
    var openMptTracker by remember { mutableStateOf("") }
    var openMptSongMessage by remember { mutableStateOf("") }
    var openMptOrderCount by remember { mutableIntStateOf(0) }
    var openMptPatternCount by remember { mutableIntStateOf(0) }
    var openMptInstrumentCount by remember { mutableIntStateOf(0) }
    var openMptSampleCount by remember { mutableIntStateOf(0) }
    var openMptInstrumentNames by remember { mutableStateOf("") }
    var openMptSampleNames by remember { mutableStateOf("") }
    var vgmGameName by remember { mutableStateOf("") }
    var vgmSystemName by remember { mutableStateOf("") }
    var vgmReleaseDate by remember { mutableStateOf("") }
    var vgmEncodedBy by remember { mutableStateOf("") }
    var vgmNotes by remember { mutableStateOf("") }
    var vgmFileVersion by remember { mutableStateOf("") }
    var vgmDeviceCount by remember { mutableIntStateOf(0) }
    var vgmUsedChipList by remember { mutableStateOf("") }
    var vgmHasLoopPoint by remember { mutableStateOf(false) }
    var ffmpegCodecName by remember { mutableStateOf("") }
    var ffmpegContainerName by remember { mutableStateOf("") }
    var ffmpegSampleFormatName by remember { mutableStateOf("") }
    var ffmpegChannelLayoutName by remember { mutableStateOf("") }
    var ffmpegEncoderName by remember { mutableStateOf("") }
    var gmeSystemName by remember { mutableStateOf("") }
    var gmeGameName by remember { mutableStateOf("") }
    var gmeCopyright by remember { mutableStateOf("") }
    var gmeComment by remember { mutableStateOf("") }
    var gmeDumper by remember { mutableStateOf("") }
    var gmeTrackCount by remember { mutableIntStateOf(0) }
    var gmeVoiceCount by remember { mutableIntStateOf(0) }
    var gmeHasLoopPoint by remember { mutableStateOf(false) }
    var gmeLoopStartMs by remember { mutableIntStateOf(-1) }
    var gmeLoopLengthMs by remember { mutableIntStateOf(-1) }
    val detailsScrollState = rememberScrollState()
    var detailsViewportHeightPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(file?.absolutePath, decoderName) {
        while (true) {
            liveBitrate = NativeBridge.getTrackBitrate()
            liveIsVbr = NativeBridge.isTrackVBR()
            liveRenderRateHz = NativeBridge.getDecoderRenderSampleRateHz()
            liveOutputRateHz = NativeBridge.getOutputStreamSampleRateHz()
            liveComposer = NativeBridge.getTrackComposer()
            liveGenre = NativeBridge.getTrackGenre()
            if (decoderName.equals("LibOpenMPT", ignoreCase = true)) {
                openMptTypeLong = NativeBridge.getOpenMptModuleTypeLong()
                openMptTracker = NativeBridge.getOpenMptTracker()
                openMptSongMessage = NativeBridge.getOpenMptSongMessage()
                openMptOrderCount = NativeBridge.getOpenMptOrderCount()
                openMptPatternCount = NativeBridge.getOpenMptPatternCount()
                openMptInstrumentCount = NativeBridge.getOpenMptInstrumentCount()
                openMptSampleCount = NativeBridge.getOpenMptSampleCount()
                openMptInstrumentNames = NativeBridge.getOpenMptInstrumentNames()
                openMptSampleNames = NativeBridge.getOpenMptSampleNames()
            } else {
                openMptTypeLong = ""
                openMptTracker = ""
                openMptSongMessage = ""
                openMptOrderCount = 0
                openMptPatternCount = 0
                openMptInstrumentCount = 0
                openMptSampleCount = 0
                openMptInstrumentNames = ""
                openMptSampleNames = ""
            }
            if (decoderName.equals("VGMPlay", ignoreCase = true)) {
                vgmGameName = NativeBridge.getVgmGameName()
                vgmSystemName = NativeBridge.getVgmSystemName()
                vgmReleaseDate = NativeBridge.getVgmReleaseDate()
                vgmEncodedBy = NativeBridge.getVgmEncodedBy()
                vgmNotes = NativeBridge.getVgmNotes()
                vgmFileVersion = NativeBridge.getVgmFileVersion()
                vgmDeviceCount = NativeBridge.getVgmDeviceCount()
                vgmUsedChipList = NativeBridge.getVgmUsedChipList()
                vgmHasLoopPoint = NativeBridge.getVgmHasLoopPoint()
            } else {
                vgmGameName = ""
                vgmSystemName = ""
                vgmReleaseDate = ""
                vgmEncodedBy = ""
                vgmNotes = ""
                vgmFileVersion = ""
                vgmDeviceCount = 0
                vgmUsedChipList = ""
                vgmHasLoopPoint = false
            }
            if (decoderName.equals("FFmpeg", ignoreCase = true)) {
                ffmpegCodecName = NativeBridge.getFfmpegCodecName()
                ffmpegContainerName = NativeBridge.getFfmpegContainerName()
                ffmpegSampleFormatName = NativeBridge.getFfmpegSampleFormatName()
                ffmpegChannelLayoutName = NativeBridge.getFfmpegChannelLayoutName()
                ffmpegEncoderName = NativeBridge.getFfmpegEncoderName()
            } else {
                ffmpegCodecName = ""
                ffmpegContainerName = ""
                ffmpegSampleFormatName = ""
                ffmpegChannelLayoutName = ""
                ffmpegEncoderName = ""
            }
            if (decoderName.equals("Game Music Emu", ignoreCase = true)) {
                gmeSystemName = NativeBridge.getGmeSystemName()
                gmeGameName = NativeBridge.getGmeGameName()
                gmeCopyright = NativeBridge.getGmeCopyright()
                gmeComment = NativeBridge.getGmeComment()
                gmeDumper = NativeBridge.getGmeDumper()
                gmeTrackCount = NativeBridge.getGmeTrackCount()
                gmeVoiceCount = NativeBridge.getGmeVoiceCount()
                gmeHasLoopPoint = NativeBridge.getGmeHasLoopPoint()
                gmeLoopStartMs = NativeBridge.getGmeLoopStartMs()
                gmeLoopLengthMs = NativeBridge.getGmeLoopLengthMs()
            } else {
                gmeSystemName = ""
                gmeGameName = ""
                gmeCopyright = ""
                gmeComment = ""
                gmeDumper = ""
                gmeTrackCount = 0
                gmeVoiceCount = 0
                gmeHasLoopPoint = false
                gmeLoopStartMs = -1
                gmeLoopLengthMs = -1
            }
            delay(500)
        }
    }

    val fileSizeBytes = file?.length() ?: 0L
    val filename = file?.name ?: "No file loaded"
    val extension = file?.extension?.uppercase()?.ifBlank { "UNKNOWN" } ?: "UNKNOWN"
    val decoderLabel = decoderName?.ifBlank { "Unknown" } ?: "Unknown"
    val bitrateLabel = if (liveBitrate > 0L) {
        "${formatBitrate(liveBitrate, liveIsVbr)} (${if (liveIsVbr) "VBR" else "CBR"})"
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
            "${formatSampleRateForDetails(liveRenderRateHz)} -> " +
            formatSampleRateForDetails(liveOutputRateHz)
    val pathOrUrlLabel = pathOrUrl?.ifBlank { "Unavailable" } ?: "Unavailable"
    val copyAllText = buildString {
        fun row(label: String, value: String) {
            append(label).append(": ").append(value).append('\n')
        }

        row("Filename", filename)
        row("Title", title)
        row("Artist", artist)
        if (liveComposer.isNotBlank()) row("Composer", liveComposer)
        if (liveGenre.isNotBlank()) row("Genre", liveGenre)
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

        if (decoderName.equals("LibOpenMPT", ignoreCase = true)) {
            append('\n').append("[OpenMPT]").append('\n')
            if (openMptTypeLong.isNotBlank()) row("Module type", openMptTypeLong)
            if (openMptTracker.isNotBlank()) row("Tracker", openMptTracker)
            row("Orders", openMptOrderCount.toString())
            row("Patterns", openMptPatternCount.toString())
            row("Instruments", openMptInstrumentCount.toString())
            row("Samples", openMptSampleCount.toString())
            if (openMptSongMessage.isNotBlank()) row("Message", openMptSongMessage)
            if (openMptInstrumentNames.isNotBlank()) row("Instrument names", openMptInstrumentNames)
            if (openMptSampleNames.isNotBlank()) row("Sample names", openMptSampleNames)
        }
        if (decoderName.equals("VGMPlay", ignoreCase = true)) {
            append('\n').append("[VGMPlay]").append('\n')
            if (vgmGameName.isNotBlank()) row("Game", vgmGameName)
            if (vgmSystemName.isNotBlank()) row("System", vgmSystemName)
            if (vgmReleaseDate.isNotBlank()) row("Release date", vgmReleaseDate)
            if (vgmEncodedBy.isNotBlank()) row("Encoded by", vgmEncodedBy)
            if (vgmFileVersion.isNotBlank()) row("VGM version", vgmFileVersion)
            if (vgmDeviceCount > 0) row("Used chips", vgmDeviceCount.toString())
            if (vgmUsedChipList.isNotBlank()) row("Chip list", vgmUsedChipList)
            row("Has loop point", if (vgmHasLoopPoint) "Yes" else "No")
            if (vgmNotes.isNotBlank()) row("Notes", vgmNotes)
        }
        if (decoderName.equals("FFmpeg", ignoreCase = true)) {
            append('\n').append("[FFmpeg]").append('\n')
            if (ffmpegCodecName.isNotBlank()) row("Codec", ffmpegCodecName)
            if (ffmpegContainerName.isNotBlank()) row("Container", ffmpegContainerName)
            if (ffmpegSampleFormatName.isNotBlank()) row("Sample format", ffmpegSampleFormatName)
            if (ffmpegChannelLayoutName.isNotBlank()) row("Channel layout", ffmpegChannelLayoutName)
            if (ffmpegEncoderName.isNotBlank()) row("Encoder", ffmpegEncoderName)
        }
        if (decoderName.equals("Game Music Emu", ignoreCase = true)) {
            append('\n').append("[Game Music Emu]").append('\n')
            if (gmeSystemName.isNotBlank()) row("System", gmeSystemName)
            if (gmeGameName.isNotBlank()) row("Game", gmeGameName)
            if (gmeTrackCount > 0) row("Track count", gmeTrackCount.toString())
            if (gmeVoiceCount > 0) row("Voice count", gmeVoiceCount.toString())
            row("Has loop point", if (gmeHasLoopPoint) "Yes" else "No")
            if (gmeLoopStartMs >= 0) row("Loop start", formatTime(gmeLoopStartMs / 1000.0))
            if (gmeLoopLengthMs > 0) row("Loop length", formatTime(gmeLoopLengthMs / 1000.0))
            if (gmeCopyright.isNotBlank()) row("Copyright", gmeCopyright)
            if (gmeDumper.isNotBlank()) row("Dumper", gmeDumper)
            if (gmeComment.isNotBlank()) row("Comment", gmeComment)
        }
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
                            if (liveComposer.isNotBlank()) {
                                TrackInfoDetailsRow("Composer", liveComposer)
                            }
                            if (liveGenre.isNotBlank()) {
                                TrackInfoDetailsRow("Genre", liveGenre)
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
                            if (decoderName.equals("LibOpenMPT", ignoreCase = true)) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "OpenMPT",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (openMptTypeLong.isNotBlank()) {
                                    TrackInfoDetailsRow("Module type", openMptTypeLong)
                                }
                                if (openMptTracker.isNotBlank()) {
                                    TrackInfoDetailsRow("Tracker", openMptTracker)
                                }
                                TrackInfoDetailsRow("Orders", openMptOrderCount.toString())
                                TrackInfoDetailsRow("Patterns", openMptPatternCount.toString())
                                TrackInfoDetailsRow("Instruments", openMptInstrumentCount.toString())
                                TrackInfoDetailsRow("Samples", openMptSampleCount.toString())
                                if (openMptSongMessage.isNotBlank()) {
                                    TrackInfoDetailsRow("Message", openMptSongMessage)
                                }
                                if (openMptInstrumentNames.isNotBlank()) {
                                    TrackInfoDetailsRow("Instrument names", openMptInstrumentNames)
                                }
                                if (openMptSampleNames.isNotBlank()) {
                                    TrackInfoDetailsRow("Sample names", openMptSampleNames)
                                }
                            }
                            if (decoderName.equals("VGMPlay", ignoreCase = true)) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "VGMPlay",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (vgmGameName.isNotBlank()) {
                                    TrackInfoDetailsRow("Game", vgmGameName)
                                }
                                if (vgmSystemName.isNotBlank()) {
                                    TrackInfoDetailsRow("System", vgmSystemName)
                                }
                                if (vgmReleaseDate.isNotBlank()) {
                                    TrackInfoDetailsRow("Release date", vgmReleaseDate)
                                }
                                if (vgmEncodedBy.isNotBlank()) {
                                    TrackInfoDetailsRow("Encoded by", vgmEncodedBy)
                                }
                                if (vgmFileVersion.isNotBlank()) {
                                    TrackInfoDetailsRow("VGM version", vgmFileVersion)
                                }
                                if (vgmDeviceCount > 0) {
                                    TrackInfoDetailsRow("Used chips", vgmDeviceCount.toString())
                                }
                                if (vgmUsedChipList.isNotBlank()) {
                                    TrackInfoDetailsRow("Chip list", vgmUsedChipList)
                                }
                                TrackInfoDetailsRow("Has loop point", if (vgmHasLoopPoint) "Yes" else "No")
                                if (vgmNotes.isNotBlank()) {
                                    TrackInfoDetailsRow("Notes", vgmNotes)
                                }
                            }
                            if (decoderName.equals("FFmpeg", ignoreCase = true)) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "FFmpeg",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (ffmpegCodecName.isNotBlank()) {
                                    TrackInfoDetailsRow("Codec", ffmpegCodecName)
                                }
                                if (ffmpegContainerName.isNotBlank()) {
                                    TrackInfoDetailsRow("Container", ffmpegContainerName)
                                }
                                if (ffmpegSampleFormatName.isNotBlank()) {
                                    TrackInfoDetailsRow("Sample format", ffmpegSampleFormatName)
                                }
                                if (ffmpegChannelLayoutName.isNotBlank()) {
                                    TrackInfoDetailsRow("Channel layout", ffmpegChannelLayoutName)
                                }
                                if (ffmpegEncoderName.isNotBlank()) {
                                    TrackInfoDetailsRow("Encoder", ffmpegEncoderName)
                                }
                            }
                            if (decoderName.equals("Game Music Emu", ignoreCase = true)) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Game Music Emu",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (gmeSystemName.isNotBlank()) {
                                    TrackInfoDetailsRow("System", gmeSystemName)
                                }
                                if (gmeGameName.isNotBlank()) {
                                    TrackInfoDetailsRow("Game", gmeGameName)
                                }
                                if (gmeTrackCount > 0) {
                                    TrackInfoDetailsRow("Track count", gmeTrackCount.toString())
                                }
                                if (gmeVoiceCount > 0) {
                                    TrackInfoDetailsRow("Voice count", gmeVoiceCount.toString())
                                }
                                TrackInfoDetailsRow("Has loop point", if (gmeHasLoopPoint) "Yes" else "No")
                                if (gmeLoopStartMs >= 0) {
                                    TrackInfoDetailsRow("Loop start", formatTime(gmeLoopStartMs / 1000.0))
                                }
                                if (gmeLoopLengthMs > 0) {
                                    TrackInfoDetailsRow("Loop length", formatTime(gmeLoopLengthMs / 1000.0))
                                }
                                if (gmeCopyright.isNotBlank()) {
                                    TrackInfoDetailsRow("Copyright", gmeCopyright)
                                }
                                if (gmeDumper.isNotBlank()) {
                                    TrackInfoDetailsRow("Dumper", gmeDumper)
                                }
                                if (gmeComment.isNotBlank()) {
                                    TrackInfoDetailsRow("Comment", gmeComment)
                                }
                            }
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
private fun TrackInfoDetailsRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
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
        2 -> 12.dp
        1 -> 13.dp
        else -> 14.dp
    }
    val iconSlot = when (compactLevel) {
        2 -> 12.dp
        else -> 14.dp
    }
    val sideInset = when (compactLevel) {
        2 -> 5.dp
        1 -> 6.dp
        else -> 7.dp
    }
    val minHeight = when (compactLevel) {
        2 -> 30.dp
        1 -> 32.dp
        else -> 34.dp
    }
    val textStartPadding = iconSlot + when (compactLevel) {
        2 -> 2.dp
        else -> 3.dp
    }
    val textEndPadding = when (compactLevel) {
        2 -> 1.dp
        else -> 2.dp
    }
    val textStyle = when {
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
    filenameOnlyWhenTitleMissing: Boolean
) {
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

    AnimatedContent(
        targetState = title,
        transitionSpec = {
            fadeIn(animationSpec = tween(durationMillis = 180, delayMillis = 40)) togetherWith
                fadeOut(animationSpec = tween(durationMillis = 120))
        },
        label = "trackTitleSwap"
    ) { animatedTitle ->
        val shouldMarquee = animatedTitle.length > 30
        if (shouldMarquee) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
            ) {
                Text(
                    text = animatedTitle,
                    style = MaterialTheme.typography.headlineSmall,
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
            Text(
                text = animatedTitle,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
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
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    if (shouldShowFilename) {
        Spacer(modifier = Modifier.height(6.dp))
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
                overflow = TextOverflow.Ellipsis
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
    onCycleRepeatMode: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val tight = maxWidth < 350.dp
        val compact = !tight && maxWidth < 390.dp
        val sideButtonSize = when {
            tight -> 48.dp
            compact -> 54.dp
            else -> 62.dp
        }
        val playButtonSize = when {
            tight -> 76.dp
            compact -> 82.dp
            else -> 90.dp
        }
        val subtuneButtonSize = when {
            tight -> 46.dp
            compact -> 52.dp
            else -> 56.dp
        }
        val rowGap = when {
            tight -> 6.dp
            compact -> 8.dp
            else -> 10.dp
        }
        val subtuneGap = if (tight) 8.dp else 10.dp

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
                        enabled = canCycleRepeatMode,
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
                    enabled = hasTrack || canResumeStoppedTrack,
                    modifier = Modifier.size(playButtonSize),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
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

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onPreviousSubtune,
                    enabled = canPreviousSubtune,
                    modifier = Modifier.size(subtuneButtonSize),
                    shape = MaterialTheme.shapes.large,
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
                    shape = MaterialTheme.shapes.large,
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
                    shape = MaterialTheme.shapes.large,
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
    onOpenAudioEffects: () -> Unit
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraLarge)
                    .combinedClickable(
                        onClick = onCycleVisualizationMode,
                        onLongClick = onOpenVisualizationPicker
                    )
            ) {
                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Visualization mode"
                    )
                }
            }
            IconButton(onClick = onOpenCoreSettings, enabled = canOpenCoreSettings) {
                Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = "Open current core settings"
                )
            }
            IconButton(onClick = onOpenAudioEffects, enabled = true) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Audio effects"
                )
            }
            IconButton(onClick = {}, enabled = false) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "Playback speed (future)"
                )
            }
        }
    }
}

@Composable
private fun TimelineSection(
    sliderPosition: Double,
    elapsedPositionSeconds: Double,
    durationSeconds: Double,
    canSeek: Boolean,
    hasReliableDuration: Boolean,
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
            Text(
                text = formatTime(elapsedPositionSeconds),
                style = MaterialTheme.typography.labelMedium
            )
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

private fun formatTime(seconds: Double): String {
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

private fun formatFileSize(bytes: Long): String {
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
