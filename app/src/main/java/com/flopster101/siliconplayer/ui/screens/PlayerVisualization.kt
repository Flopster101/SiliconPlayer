package com.flopster101.siliconplayer.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.VisualizationChannelScopeLayout
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.VisualizationOscColorMode
import com.flopster101.siliconplayer.VisualizationOscFpsMode
import com.flopster101.siliconplayer.VisualizationRenderBackend
import com.flopster101.siliconplayer.VisualizationVuAnchor
import com.flopster101.siliconplayer.pluginNameForCoreName
import com.flopster101.siliconplayer.visualizationRenderBackendForMode
import com.flopster101.siliconplayer.ui.visualization.basic.BasicVisualizationOverlay
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private class VisualizationDebugAccumulator {
    var frameCount: Int = 0
    var windowStartNs: Long = 0L
    var lastFrameNs: Long = 0L
    var latestFrameMs: Int = 0
    var lastUiPublishNs: Long = 0L
}

private fun computeChannelScopeSampleCount(
    windowMs: Int,
    sampleRateHz: Int
): Int {
    val clampedWindowMs = windowMs.coerceIn(5, 200)
    val effectiveSampleRate = sampleRateHz.coerceIn(8_000, 192_000)
    val requested = ((clampedWindowMs.toFloat() / 1000f) * effectiveSampleRate.toFloat()).roundToInt()
    // Bound to native ring-buffer budget while still scaling with the UI window setting.
    return requested.coerceIn(128, 8192)
}

private fun normalizeChannelScopeChannel(
    flatScopes: FloatArray,
    start: Int,
    points: Int,
    dcRemovalEnabled: Boolean,
    gainPercent: Int
): FloatArray {
    val centered = FloatArray(points)
    val fixedGain = (gainPercent.coerceIn(25, 600).toFloat() / 100f)
    if (!dcRemovalEnabled) {
        for (i in 0 until points) {
            val sample = flatScopes[start + i].coerceIn(-1f, 1f)
            centered[i] = (sample * fixedGain).coerceIn(-1f, 1f)
        }
        return centered
    }
    var sum = 0f
    var minSample = 1f
    var maxSample = -1f
    for (i in 0 until points) {
        val sample = flatScopes[start + i].coerceIn(-1f, 1f)
        sum += sample
        if (sample < minSample) minSample = sample
        if (sample > maxSample) maxSample = sample
    }
    val frameMean = sum / points.toFloat()
    val peakMidpoint = (minSample + maxSample) * 0.5f
    val dcOffset = (frameMean * 0.7f) + (peakMidpoint * 0.3f)
    for (i in 0 until points) {
        val sample = flatScopes[start + i].coerceIn(-1f, 1f)
        centered[i] = ((sample - dcOffset) * fixedGain).coerceIn(-1f, 1f)
    }
    return centered
}

private suspend fun buildChannelScopeHistoriesAsync(
    flatScopes: FloatArray,
    points: Int,
    dcRemovalEnabled: Boolean,
    gainPercent: Int
): List<FloatArray> {
    if (points <= 0 || flatScopes.size < points) {
        return emptyList()
    }
    return withContext(Dispatchers.Default) {
        val channels = (flatScopes.size / points).coerceIn(1, 64)
        val histories = ArrayList<FloatArray>(channels)
        for (index in 0 until channels) {
            coroutineContext.ensureActive()
            val start = index * points
            val end = (start + points).coerceAtMost(flatScopes.size)
            if (end - start < points) {
                histories.add(FloatArray(points))
            } else {
                val normalized = normalizeChannelScopeChannel(
                    flatScopes = flatScopes,
                    start = start,
                    points = points,
                    dcRemovalEnabled = dcRemovalEnabled,
                    gainPercent = gainPercent
                )
                histories.add(normalized)
            }
        }
        histories
    }
}

private fun computeChannelScopeTriggerIndices(
    histories: List<FloatArray>,
    triggerModeNative: Int,
    previousIndices: IntArray
): IntArray {
    if (histories.isEmpty()) return IntArray(0)
    return IntArray(histories.size) { channel ->
        val history = histories[channel]
        val anchorIndex = (history.size / 2).coerceAtLeast(0)
        val previous = previousIndices.getOrElse(channel) { -1 }
        findScopedTriggerIndex(
            history = history,
            triggerModeNative = triggerModeNative,
            anchorIndex = anchorIndex,
            previousIndex = previous
        )
    }
}

private fun findScopedTriggerIndex(
    history: FloatArray,
    triggerModeNative: Int,
    anchorIndex: Int,
    previousIndex: Int
): Int {
    if (history.size < 2 || triggerModeNative == 0) {
        return anchorIndex.coerceIn(0, history.size - 1)
    }
    val n = history.size
    val anchor = anchorIndex.coerceIn(0, n - 1)
    val previous = if (previousIndex in 0 until n) previousIndex else -1
    fun circularDistance(a: Int, b: Int): Int {
        val d = kotlin.math.abs(a - b)
        return kotlin.math.min(d, n - d)
    }

    var bestIndex = -1
    var bestScore = Float.NEGATIVE_INFINITY
    for (i in 1 until n) {
        val prev = history[i - 1]
        val curr = history[i]
        val crossed = if (triggerModeNative == 1) {
            prev < 0f && curr >= 0f
        } else {
            prev > 0f && curr <= 0f
        }
        if (!crossed) continue

        val left = history[(i - 2 + n) % n]
        val right = history[(i + 1) % n]
        val slope = kotlin.math.abs(curr - prev)
        val edgeEnergy = 0.5f * (kotlin.math.abs(curr) + kotlin.math.abs(prev))
        val curvature = kotlin.math.abs((right - curr) - (curr - left))
        val anchorPenalty = circularDistance(i, anchor).toFloat() / n.toFloat()
        val continuityPenalty = if (previous >= 0) {
            circularDistance(i, previous).toFloat() / n.toFloat()
        } else {
            0f
        }

        val score =
            (slope * 2.8f) +
                (edgeEnergy * 0.9f) +
                (curvature * 0.35f) -
                (anchorPenalty * 1.6f) -
                (continuityPenalty * 1.1f)
        if (score > bestScore) {
            bestScore = score
            bestIndex = i
        }
    }

    if (bestIndex >= 0) return bestIndex

    // Fallback near-zero center lock for stable idle/noise behavior.
    var fallbackIndex = anchor
    var fallbackRank = Float.POSITIVE_INFINITY
    for (i in history.indices) {
        val sample = kotlin.math.abs(history[i])
        val anchorPenalty = circularDistance(i, anchor).toFloat() / n.toFloat()
        val continuityPenalty = if (previous >= 0) {
            circularDistance(i, previous).toFloat() / n.toFloat()
        } else {
            0f
        }
        val rank = sample + (anchorPenalty * 0.10f) + (continuityPenalty * 0.08f)
        if (rank < fallbackRank) {
            fallbackRank = rank
            fallbackIndex = i
        }
    }
    return fallbackIndex
}

internal data class ChannelScopePrefs(
    val windowMs: Int,
    val renderBackend: VisualizationRenderBackend,
    val dcRemovalEnabled: Boolean,
    val gainPercent: Int,
    val triggerModeNative: Int,
    val fpsMode: VisualizationOscFpsMode,
    val lineWidthDp: Int,
    val gridWidthDp: Int,
    val verticalGridEnabled: Boolean,
    val centerLineEnabled: Boolean,
    val layout: VisualizationChannelScopeLayout,
    val lineColorModeNoArtwork: VisualizationOscColorMode,
    val gridColorModeNoArtwork: VisualizationOscColorMode,
    val lineColorModeWithArtwork: VisualizationOscColorMode,
    val gridColorModeWithArtwork: VisualizationOscColorMode,
    val customLineColorArgb: Int,
    val customGridColorArgb: Int
) {
    companion object {
        private const val KEY_WINDOW_MS = "visualization_channel_scope_window_ms"
        private const val KEY_RENDER_BACKEND = "visualization_channel_scope_render_backend"
        private const val KEY_DC_REMOVAL_ENABLED = "visualization_channel_scope_dc_removal_enabled"
        private const val KEY_GAIN_PERCENT = "visualization_channel_scope_gain_percent"
        private const val KEY_TRIGGER_MODE = "visualization_channel_scope_trigger_mode"
        private const val KEY_FPS_MODE = "visualization_channel_scope_fps_mode"
        private const val KEY_LINE_WIDTH_DP = "visualization_channel_scope_line_width_dp"
        private const val KEY_GRID_WIDTH_DP = "visualization_channel_scope_grid_width_dp"
        private const val KEY_VERTICAL_GRID_ENABLED = "visualization_channel_scope_vertical_grid_enabled"
        private const val KEY_CENTER_LINE_ENABLED = "visualization_channel_scope_center_line_enabled"
        private const val KEY_LAYOUT = "visualization_channel_scope_layout"
        private const val KEY_LINE_COLOR_MODE_NO_ARTWORK = "visualization_channel_scope_line_color_mode_no_artwork"
        private const val KEY_GRID_COLOR_MODE_NO_ARTWORK = "visualization_channel_scope_grid_color_mode_no_artwork"
        private const val KEY_LINE_COLOR_MODE_WITH_ARTWORK = "visualization_channel_scope_line_color_mode_with_artwork"
        private const val KEY_GRID_COLOR_MODE_WITH_ARTWORK = "visualization_channel_scope_grid_color_mode_with_artwork"
        private const val KEY_CUSTOM_LINE_COLOR_ARGB = "visualization_channel_scope_custom_line_color_argb"
        private const val KEY_CUSTOM_GRID_COLOR_ARGB = "visualization_channel_scope_custom_grid_color_argb"

        fun from(sharedPrefs: android.content.SharedPreferences): ChannelScopePrefs {
            val triggerModeNative = when (sharedPrefs.getString(KEY_TRIGGER_MODE, "rising")) {
                "rising" -> 1
                "falling" -> 2
                else -> 0
            }
            return ChannelScopePrefs(
                windowMs = sharedPrefs.getInt(KEY_WINDOW_MS, 30).coerceIn(5, 200),
                renderBackend = VisualizationRenderBackend.fromStorage(
                    sharedPrefs.getString(KEY_RENDER_BACKEND, VisualizationRenderBackend.Gpu.storageValue),
                    VisualizationRenderBackend.Gpu
                ),
                dcRemovalEnabled = sharedPrefs.getBoolean(KEY_DC_REMOVAL_ENABLED, true),
                gainPercent = sharedPrefs.getInt(KEY_GAIN_PERCENT, 240).coerceIn(25, 600),
                triggerModeNative = triggerModeNative,
                fpsMode = VisualizationOscFpsMode.fromStorage(
                    sharedPrefs.getString(KEY_FPS_MODE, VisualizationOscFpsMode.Default.storageValue)
                ),
                lineWidthDp = sharedPrefs.getInt(KEY_LINE_WIDTH_DP, 3).coerceIn(1, 12),
                gridWidthDp = sharedPrefs.getInt(KEY_GRID_WIDTH_DP, 2).coerceIn(1, 8),
                verticalGridEnabled = sharedPrefs.getBoolean(KEY_VERTICAL_GRID_ENABLED, false),
                centerLineEnabled = sharedPrefs.getBoolean(KEY_CENTER_LINE_ENABLED, false),
                layout = VisualizationChannelScopeLayout.fromStorage(
                    sharedPrefs.getString(KEY_LAYOUT, VisualizationChannelScopeLayout.ColumnFirst.storageValue)
                ),
                lineColorModeNoArtwork = VisualizationOscColorMode.fromStorage(
                    sharedPrefs.getString(KEY_LINE_COLOR_MODE_NO_ARTWORK, VisualizationOscColorMode.Monet.storageValue),
                    VisualizationOscColorMode.Monet
                ),
                gridColorModeNoArtwork = VisualizationOscColorMode.fromStorage(
                    sharedPrefs.getString(KEY_GRID_COLOR_MODE_NO_ARTWORK, VisualizationOscColorMode.Monet.storageValue),
                    VisualizationOscColorMode.Monet
                ),
                lineColorModeWithArtwork = VisualizationOscColorMode.fromStorage(
                    sharedPrefs.getString(KEY_LINE_COLOR_MODE_WITH_ARTWORK, VisualizationOscColorMode.Artwork.storageValue),
                    VisualizationOscColorMode.Artwork
                ),
                gridColorModeWithArtwork = VisualizationOscColorMode.fromStorage(
                    sharedPrefs.getString(KEY_GRID_COLOR_MODE_WITH_ARTWORK, VisualizationOscColorMode.Artwork.storageValue),
                    VisualizationOscColorMode.Artwork
                ),
                customLineColorArgb = sharedPrefs.getInt(KEY_CUSTOM_LINE_COLOR_ARGB, 0xFF6BD8FF.toInt()),
                customGridColorArgb = sharedPrefs.getInt(KEY_CUSTOM_GRID_COLOR_ARGB, 0x66FFFFFF)
            )
        }

        fun isChannelScopeKey(key: String?): Boolean {
            return key?.startsWith("visualization_channel_scope_") == true
        }
    }
}

@Composable
internal fun rememberChannelScopePrefs(
    sharedPrefs: android.content.SharedPreferences
): ChannelScopePrefs {
    var state by remember(sharedPrefs) { mutableStateOf(ChannelScopePrefs.from(sharedPrefs)) }
    DisposableEffect(sharedPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (ChannelScopePrefs.isChannelScopeKey(key)) {
                state = ChannelScopePrefs.from(prefs)
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    return state
}

private data class ChannelScopeVisualState(
    val channelHistories: List<FloatArray>,
    val triggerModeNative: Int,
    val triggerIndices: IntArray,
    val renderBackend: VisualizationRenderBackend,
    val lineWidthDp: Int,
    val gridWidthDp: Int,
    val verticalGridEnabled: Boolean,
    val centerLineEnabled: Boolean,
    val layout: VisualizationChannelScopeLayout,
    val lineColorModeNoArtwork: VisualizationOscColorMode,
    val gridColorModeNoArtwork: VisualizationOscColorMode,
    val lineColorModeWithArtwork: VisualizationOscColorMode,
    val gridColorModeWithArtwork: VisualizationOscColorMode,
    val customLineColorArgb: Int,
    val customGridColorArgb: Int
)

@Composable
internal fun AlbumArtPlaceholder(
    file: File?,
    isPlaying: Boolean,
    decoderName: String?,
    sampleRateHz: Int,
    artwork: ImageBitmap?,
    placeholderIcon: ImageVector,
    visualizationModeBadgeText: String,
    showVisualizationModeBadge: Boolean,
    visualizationMode: VisualizationMode,
    visualizationShowDebugInfo: Boolean,
    visualizationOscWindowMs: Int,
    visualizationOscTriggerModeNative: Int,
    visualizationOscFpsMode: VisualizationOscFpsMode,
    visualizationBarSmoothingPercent: Int,
    visualizationVuSmoothingPercent: Int,
    barCount: Int,
    barRoundnessDp: Int,
    barOverlayArtwork: Boolean,
    barUseThemeColor: Boolean,
    barColorModeNoArtwork: VisualizationOscColorMode,
    barColorModeWithArtwork: VisualizationOscColorMode,
    barCustomColorArgb: Int,
    oscStereo: Boolean,
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
    vuAnchor: VisualizationVuAnchor,
    vuUseThemeColor: Boolean,
    vuColorModeNoArtwork: VisualizationOscColorMode,
    vuColorModeWithArtwork: VisualizationOscColorMode,
    vuCustomColorArgb: Int,
    channelScopePrefs: ChannelScopePrefs,
    modifier: Modifier = Modifier
) {
    var visWaveLeft by remember { mutableStateOf(FloatArray(0)) }
    var visWaveRight by remember { mutableStateOf(FloatArray(0)) }
    var visBars by remember { mutableStateOf(FloatArray(0)) }
    var visBarsSmoothed by remember { mutableStateOf(FloatArray(0)) }
    var visVu by remember { mutableStateOf(FloatArray(0)) }
    var visVuSmoothed by remember { mutableStateOf(FloatArray(0)) }
    var visChannelCount by remember { mutableIntStateOf(2) }
    var visChannelScopesFlat by remember { mutableStateOf(FloatArray(0)) }
    var visChannelScopeHistories by remember { mutableStateOf<List<FloatArray>>(emptyList()) }
    var visChannelScopeLastChannelCount by remember { mutableIntStateOf(1) }
    var visChannelScopeTriggerIndices by remember { mutableStateOf(IntArray(0)) }
    var visDebugFps by remember { mutableIntStateOf(0) }
    var visDebugFrameMs by remember { mutableIntStateOf(0) }
    val visDebugAccumulator = remember { VisualizationDebugAccumulator() }
    var lastVisualizationBackend by remember {
        mutableStateOf(
            if (visualizationMode == VisualizationMode.ChannelScope) {
                channelScopePrefs.renderBackend
            } else {
                visualizationRenderBackendForMode(visualizationMode)
            }
        )
    }
    val backendTransitionBlackAlpha = remember { Animatable(0f) }
    val context = LocalContext.current

    LaunchedEffect(file?.absolutePath, isPlaying, channelScopePrefs.windowMs, sampleRateHz) {
        if (file != null && isPlaying) return@LaunchedEffect
        visWaveLeft = FloatArray(256)
        visWaveRight = FloatArray(256)
        visBars = FloatArray(256)
        visBarsSmoothed = FloatArray(256)
        visVu = FloatArray(2)
        visVuSmoothed = FloatArray(2)
        visChannelCount = 2
        visChannelScopesFlat = FloatArray(0)
        val points = computeChannelScopeSampleCount(
            windowMs = channelScopePrefs.windowMs,
            sampleRateHz = sampleRateHz
        )
        val channels = visChannelScopeHistories.size
            .takeIf { it > 0 }
            ?: visChannelScopeLastChannelCount.coerceIn(1, 64)
        visChannelScopeHistories = List(channels) { FloatArray(points) }
        visChannelScopeTriggerIndices = IntArray(channels) { points / 2 }
        visDebugFps = 0
        visDebugFrameMs = 0
        visDebugAccumulator.frameCount = 0
        visDebugAccumulator.windowStartNs = 0L
        visDebugAccumulator.lastFrameNs = 0L
        visDebugAccumulator.latestFrameMs = 0
        visDebugAccumulator.lastUiPublishNs = 0L
    }
    LaunchedEffect(
        visualizationMode,
        channelScopePrefs.renderBackend
    ) {
        val nextBackend = if (visualizationMode == VisualizationMode.ChannelScope) {
            channelScopePrefs.renderBackend
        } else {
            visualizationRenderBackendForMode(visualizationMode)
        }
        if (nextBackend != lastVisualizationBackend) {
            backendTransitionBlackAlpha.snapTo(0f)
            backendTransitionBlackAlpha.animateTo(1f, animationSpec = tween(85))
            lastVisualizationBackend = nextBackend
            backendTransitionBlackAlpha.animateTo(0f, animationSpec = tween(145))
        } else {
            lastVisualizationBackend = nextBackend
        }
    }
    LaunchedEffect(
        visualizationMode,
        file?.absolutePath,
        isPlaying,
        visualizationOscWindowMs,
        visualizationOscTriggerModeNative,
        visualizationOscFpsMode,
        channelScopePrefs.windowMs,
        channelScopePrefs.fpsMode,
        sampleRateHz,
        decoderName
    ) {
        while (true) {
            if (visualizationMode != VisualizationMode.Off && file != null && isPlaying) {
                visWaveLeft = NativeBridge.getVisualizationWaveformScope(
                    0,
                    visualizationOscWindowMs,
                    visualizationOscTriggerModeNative
                )
                visWaveRight = NativeBridge.getVisualizationWaveformScope(
                    1,
                    visualizationOscWindowMs,
                    visualizationOscTriggerModeNative
                )
                visBars = NativeBridge.getVisualizationBars()
                visVu = NativeBridge.getVisualizationVuLevels()
                visChannelCount = NativeBridge.getVisualizationChannelCount().coerceAtLeast(1)
                if (
                    visualizationMode == VisualizationMode.ChannelScope &&
                    pluginNameForCoreName(decoderName) == "LibOpenMPT"
                ) {
                    val scopeSamples = computeChannelScopeSampleCount(
                        windowMs = channelScopePrefs.windowMs,
                        sampleRateHz = sampleRateHz
                    )
                    visChannelScopesFlat = NativeBridge.getChannelScopeSamples(scopeSamples)
                }
                val nowNs = System.nanoTime()
                if (visDebugAccumulator.windowStartNs == 0L) {
                    visDebugAccumulator.windowStartNs = nowNs
                }
                if (visDebugAccumulator.lastFrameNs != 0L) {
                    visDebugAccumulator.latestFrameMs =
                        ((nowNs - visDebugAccumulator.lastFrameNs) / 1_000_000L).toInt().coerceAtLeast(0)
                }
                visDebugAccumulator.lastFrameNs = nowNs
                visDebugAccumulator.frameCount += 1
                val elapsedNs = nowNs - visDebugAccumulator.windowStartNs
                if (elapsedNs >= 1_000_000_000L) {
                    visDebugFps = ((visDebugAccumulator.frameCount.toDouble() * 1_000_000_000.0) / elapsedNs.toDouble())
                        .roundToInt()
                        .coerceAtLeast(0)
                    visDebugAccumulator.frameCount = 0
                    visDebugAccumulator.windowStartNs = nowNs
                }
                // Throttle HUD state updates to reduce recomposition overhead.
                if (nowNs - visDebugAccumulator.lastUiPublishNs >= 350_000_000L) {
                    visDebugFrameMs = visDebugAccumulator.latestFrameMs
                    visDebugAccumulator.lastUiPublishNs = nowNs
                }
            }
            val delayMs = if (!isPlaying) {
                90L
            } else if (
                visualizationMode != VisualizationMode.Oscilloscope &&
                visualizationMode != VisualizationMode.ChannelScope
            ) {
                33L
            } else {
                val fpsMode = if (visualizationMode == VisualizationMode.ChannelScope) {
                    channelScopePrefs.fpsMode
                } else {
                    visualizationOscFpsMode
                }
                when (fpsMode) {
                    VisualizationOscFpsMode.Default -> 33L
                    VisualizationOscFpsMode.Fps60 -> 16L
                    VisualizationOscFpsMode.NativeRefresh -> {
                        val refreshHz = (context.display?.refreshRate ?: 60f).coerceAtLeast(30f)
                        (1000f / refreshHz).roundToInt().coerceAtLeast(4).toLong()
                    }
                }
            }
            delay(delayMs)
        }
    }
    LaunchedEffect(
        visChannelScopesFlat,
        visualizationMode,
        channelScopePrefs.windowMs,
        channelScopePrefs.triggerModeNative,
        sampleRateHz,
        isPlaying
    ) {
        if (visualizationMode != VisualizationMode.ChannelScope) {
            visChannelScopeHistories = emptyList()
            visChannelScopeTriggerIndices = IntArray(0)
            return@LaunchedEffect
        }
        val points = computeChannelScopeSampleCount(
            windowMs = channelScopePrefs.windowMs,
            sampleRateHz = sampleRateHz
        )
        if (visChannelScopesFlat.isEmpty()) {
            if (!isPlaying) {
                val channels = visChannelScopeHistories.size
                    .takeIf { it > 0 }
                    ?: visChannelScopeLastChannelCount.coerceIn(1, 64)
                visChannelScopeHistories = List(channels) { FloatArray(points) }
                visChannelScopeTriggerIndices = IntArray(channels) { points / 2 }
                return@LaunchedEffect
            }
            visChannelScopeHistories = emptyList()
            visChannelScopeTriggerIndices = IntArray(0)
            return@LaunchedEffect
        }
        if (visChannelScopesFlat.size < points) {
            if (!isPlaying) {
                val channels = visChannelScopeHistories.size
                    .takeIf { it > 0 }
                    ?: visChannelScopeLastChannelCount.coerceIn(1, 64)
                visChannelScopeHistories = List(channels) { FloatArray(points) }
                visChannelScopeTriggerIndices = IntArray(channels) { points / 2 }
                return@LaunchedEffect
            }
            visChannelScopeHistories = emptyList()
            visChannelScopeTriggerIndices = IntArray(0)
            return@LaunchedEffect
        }
        val histories = buildChannelScopeHistoriesAsync(
            flatScopes = visChannelScopesFlat,
            points = points,
            dcRemovalEnabled = channelScopePrefs.dcRemovalEnabled,
            gainPercent = channelScopePrefs.gainPercent
        )
        visChannelScopeLastChannelCount = histories.size.coerceIn(1, 64)
        visChannelScopeHistories = histories
        visChannelScopeTriggerIndices = computeChannelScopeTriggerIndices(
            histories = histories,
            triggerModeNative = channelScopePrefs.triggerModeNative,
            previousIndices = visChannelScopeTriggerIndices
        )
    }
    LaunchedEffect(visBars, visualizationBarSmoothingPercent, visualizationMode) {
        if (visualizationMode != VisualizationMode.Bars) {
            visBarsSmoothed = visBars
            return@LaunchedEffect
        }
        if (visBars.isEmpty()) {
            visBarsSmoothed = visBars
            return@LaunchedEffect
        }
        val prev = visBarsSmoothed
        if (prev.size != visBars.size) {
            visBarsSmoothed = visBars.copyOf()
            return@LaunchedEffect
        }
        val smoothing = (visualizationBarSmoothingPercent.coerceIn(0, 95) / 100f)
        val mixed = FloatArray(visBars.size)
        for (i in visBars.indices) {
            val target = visBars[i].coerceIn(0f, 1f)
            val current = prev[i].coerceIn(0f, 1f)
            mixed[i] = (current * smoothing) + (target * (1f - smoothing))
        }
        visBarsSmoothed = mixed
    }
    LaunchedEffect(visVu, visualizationVuSmoothingPercent, visualizationMode) {
        if (visualizationMode != VisualizationMode.VuMeters) {
            visVuSmoothed = visVu
            return@LaunchedEffect
        }
        if (visVu.isEmpty()) {
            visVuSmoothed = visVu
            return@LaunchedEffect
        }
        val prev = visVuSmoothed
        if (prev.size != visVu.size) {
            visVuSmoothed = visVu.copyOf()
            return@LaunchedEffect
        }
        val smoothing = (visualizationVuSmoothingPercent.coerceIn(0, 95) / 100f)
        val mixed = FloatArray(visVu.size)
        for (i in visVu.indices) {
            val target = visVu[i].coerceIn(0f, 1f)
            val current = prev[i].coerceIn(0f, 1f)
            mixed[i] = (current * smoothing) + (target * (1f - smoothing))
        }
        visVuSmoothed = mixed
    }
    val channelScopeState = ChannelScopeVisualState(
        channelHistories = visChannelScopeHistories,
        triggerModeNative = channelScopePrefs.triggerModeNative,
        triggerIndices = visChannelScopeTriggerIndices,
        renderBackend = channelScopePrefs.renderBackend,
        lineWidthDp = channelScopePrefs.lineWidthDp,
        gridWidthDp = channelScopePrefs.gridWidthDp,
        verticalGridEnabled = channelScopePrefs.verticalGridEnabled,
        centerLineEnabled = channelScopePrefs.centerLineEnabled,
        layout = channelScopePrefs.layout,
        lineColorModeNoArtwork = channelScopePrefs.lineColorModeNoArtwork,
        gridColorModeNoArtwork = channelScopePrefs.gridColorModeNoArtwork,
        lineColorModeWithArtwork = channelScopePrefs.lineColorModeWithArtwork,
        gridColorModeWithArtwork = channelScopePrefs.gridColorModeWithArtwork,
        customLineColorArgb = channelScopePrefs.customLineColorArgb,
        customGridColorArgb = channelScopePrefs.customGridColorArgb
    )
    val artworkBrightness = remember(artwork) {
        val bitmap = artwork ?: return@remember null
        runCatching {
            val pixels = bitmap.toPixelMap()
            if (pixels.width <= 0 || pixels.height <= 0) return@runCatching 0.5f
            val stepX = maxOf(1, pixels.width / 32)
            val stepY = maxOf(1, pixels.height / 32)
            var sum = 0f
            var count = 0
            var y = 0
            while (y < pixels.height) {
                var x = 0
                while (x < pixels.width) {
                    sum += pixels[x, y].luminance()
                    count++
                    x += stepX
                }
                y += stepY
            }
            if (count > 0) (sum / count) else 0.5f
        }.getOrNull()
    }
    val badgeBackground = when {
        artworkBrightness == null -> MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
        artworkBrightness > 0.5f -> androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.52f)
        else -> androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f)
    }
    val badgeContentColor = when {
        artworkBrightness == null -> MaterialTheme.colorScheme.onSurface
        artworkBrightness > 0.5f -> androidx.compose.ui.graphics.Color.White
        else -> androidx.compose.ui.graphics.Color.Black
    }
    val activeRenderBackend = if (visualizationMode == VisualizationMode.ChannelScope) {
        channelScopePrefs.renderBackend
    } else {
        visualizationRenderBackendForMode(visualizationMode)
    }

    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Crossfade(targetState = artwork, label = "albumArtCrossfade") { art ->
                if (art != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = art,
                            contentDescription = "Album artwork",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                        MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = placeholderIcon,
                                contentDescription = "No album artwork",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(72.dp)
                            )
                        }
                    }
                }
            }
            BasicVisualizationOverlay(
                mode = visualizationMode,
                bars = visBarsSmoothed,
                waveformLeft = visWaveLeft,
                waveformRight = visWaveRight,
                vuLevels = visVuSmoothed,
                channelCount = visChannelCount,
                barCount = barCount,
                barRoundnessDp = barRoundnessDp,
                barOverlayArtwork = barOverlayArtwork,
                barUseThemeColor = barUseThemeColor,
                barColorModeNoArtwork = barColorModeNoArtwork,
                barColorModeWithArtwork = barColorModeWithArtwork,
                barCustomColorArgb = barCustomColorArgb,
                oscStereo = oscStereo,
                artwork = artwork,
                oscLineWidthDp = oscLineWidthDp,
                oscGridWidthDp = oscGridWidthDp,
                oscVerticalGridEnabled = oscVerticalGridEnabled,
                oscCenterLineEnabled = oscCenterLineEnabled,
                oscLineColorModeNoArtwork = oscLineColorModeNoArtwork,
                oscGridColorModeNoArtwork = oscGridColorModeNoArtwork,
                oscLineColorModeWithArtwork = oscLineColorModeWithArtwork,
                oscGridColorModeWithArtwork = oscGridColorModeWithArtwork,
                oscCustomLineColorArgb = oscCustomLineColorArgb,
                oscCustomGridColorArgb = oscCustomGridColorArgb,
                vuAnchor = vuAnchor,
                vuUseThemeColor = vuUseThemeColor,
                vuColorModeNoArtwork = vuColorModeNoArtwork,
                vuColorModeWithArtwork = vuColorModeWithArtwork,
                vuCustomColorArgb = vuCustomColorArgb,
                channelScopeHistories = channelScopeState.channelHistories,
                channelScopeTriggerModeNative = channelScopeState.triggerModeNative,
                channelScopeTriggerIndices = channelScopeState.triggerIndices,
                channelScopeRenderBackend = channelScopeState.renderBackend,
                channelScopeLineWidthDp = channelScopeState.lineWidthDp,
                channelScopeGridWidthDp = channelScopeState.gridWidthDp,
                channelScopeVerticalGridEnabled = channelScopeState.verticalGridEnabled,
                channelScopeCenterLineEnabled = channelScopeState.centerLineEnabled,
                channelScopeLayout = channelScopeState.layout,
                channelScopeLineColorModeNoArtwork = channelScopeState.lineColorModeNoArtwork,
                channelScopeGridColorModeNoArtwork = channelScopeState.gridColorModeNoArtwork,
                channelScopeLineColorModeWithArtwork = channelScopeState.lineColorModeWithArtwork,
                channelScopeGridColorModeWithArtwork = channelScopeState.gridColorModeWithArtwork,
                channelScopeCustomLineColorArgb = channelScopeState.customLineColorArgb,
                channelScopeCustomGridColorArgb = channelScopeState.customGridColorArgb,
                modifier = Modifier.matchParentSize()
            )
            if (backendTransitionBlackAlpha.value > 0f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = backendTransitionBlackAlpha.value.coerceIn(0f, 1f)))
                )
            }
            if (visualizationShowDebugInfo && visualizationMode != VisualizationMode.Off) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 10.dp, top = 10.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.22f)
                ) {
                    Text(
                        text = "Mode: ${visualizationMode.label}\nBackend: ${activeRenderBackend.label}\nFPS: ${visDebugFps}\nFrame: ${visDebugFrameMs} ms",
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = showVisualizationModeBadge,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp),
                enter = fadeIn(animationSpec = tween(170)),
                exit = fadeOut(animationSpec = tween(260))
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = badgeBackground
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = when (visualizationMode) {
                                VisualizationMode.Off -> Icons.Default.GraphicEq
                                VisualizationMode.Bars -> Icons.Default.GraphicEq
                                VisualizationMode.Oscilloscope -> Icons.Default.MonitorHeart
                                VisualizationMode.VuMeters -> Icons.Default.Equalizer
                                VisualizationMode.ChannelScope -> Icons.Default.MonitorHeart
                            },
                            contentDescription = null,
                            tint = badgeContentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = visualizationModeBadgeText,
                            style = MaterialTheme.typography.labelMedium,
                            color = badgeContentColor
                        )
                    }
                }
            }
        }
    }
}
