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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.AppDefaults
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.VisualizationChannelScopeLayout
import com.flopster101.siliconplayer.VisualizationChannelScopeTextAnchor
import com.flopster101.siliconplayer.VisualizationChannelScopeBackgroundMode
import com.flopster101.siliconplayer.VisualizationChannelScopeTextColorMode
import com.flopster101.siliconplayer.VisualizationChannelScopeTextFont
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.VisualizationNoteNameFormat
import com.flopster101.siliconplayer.VisualizationOscColorMode
import com.flopster101.siliconplayer.VisualizationOscFpsMode
import com.flopster101.siliconplayer.VisualizationRenderBackend
import com.flopster101.siliconplayer.VisualizationVuAnchor
import com.flopster101.siliconplayer.pluginNameForCoreName
import com.flopster101.siliconplayer.visualizationRenderBackendForMode
import com.flopster101.siliconplayer.ui.visualization.basic.BasicVisualizationOverlay
import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeChannelTextState
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

private class VisualizationDebugAccumulator {
    var frameCount: Int = 0
    var windowStartNs: Long = 0L
    var lastFrameNs: Long = 0L
    var latestFrameMs: Int = 0
    var lastUiPublishNs: Long = 0L
}

private fun extractArtworkAccentColor(artwork: ImageBitmap?): Color? {
    if (artwork == null) return null
    val pixels = artwork.toPixelMap()
    val width = pixels.width
    val height = pixels.height
    if (width <= 0 || height <= 0) return null

    val stepX = maxOf(1, width / 32)
    val stepY = maxOf(1, height / 32)
    var weightedR = 0.0
    var weightedG = 0.0
    var weightedB = 0.0
    var weightSum = 0.0
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val c = pixels[x, y]
            val r = c.red.toDouble()
            val g = c.green.toDouble()
            val b = c.blue.toDouble()
            val maxCh = maxOf(r, maxOf(g, b))
            val minCh = minOf(r, minOf(g, b))
            val sat = if (maxCh <= 1e-6) 0.0 else (maxCh - minCh) / maxCh
            val value = maxCh
            val weight = (0.2 + (sat * 0.8)) * (0.3 + (value * 0.7))
            weightedR += r * weight
            weightedG += g * weight
            weightedB += b * weight
            weightSum += weight
            x += stepX
        }
        y += stepY
    }
    if (weightSum <= 1e-6) return null
    return Color(
        red = (weightedR / weightSum).toFloat().coerceIn(0f, 1f),
        green = (weightedG / weightSum).toFloat().coerceIn(0f, 1f),
        blue = (weightedB / weightSum).toFloat().coerceIn(0f, 1f),
        alpha = 1f
    )
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

private fun computeVisualizationPollIntervalNs(
    isPlaying: Boolean,
    visualizationMode: VisualizationMode,
    visualizationOscFpsMode: VisualizationOscFpsMode,
    channelScopeFpsMode: VisualizationOscFpsMode,
    displayRefreshHz: Float
): Long {
    if (!isPlaying) return 90_000_000L
    val fps = when (visualizationMode) {
        VisualizationMode.Oscilloscope -> {
            when (visualizationOscFpsMode) {
                VisualizationOscFpsMode.Default -> 30f
                VisualizationOscFpsMode.Fps60 -> 60f
                VisualizationOscFpsMode.NativeRefresh -> displayRefreshHz.coerceAtLeast(30f)
            }
        }
        VisualizationMode.ChannelScope -> {
            when (channelScopeFpsMode) {
                VisualizationOscFpsMode.Default -> 30f
                VisualizationOscFpsMode.Fps60 -> 60f
                VisualizationOscFpsMode.NativeRefresh -> displayRefreshHz.coerceAtLeast(30f)
            }
        }
        else -> 30f
    }.coerceAtLeast(1f)
    return (1_000_000_000.0 / fps.toDouble()).roundToInt().toLong().coerceAtLeast(4_000_000L)
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

private fun parseChannelScopeTextStates(
    flat: IntArray
): List<ChannelScopeChannelTextState> {
    val stride = NativeBridge.CHANNEL_SCOPE_TEXT_STATE_STRIDE
    if (stride <= 0 || flat.isEmpty()) return emptyList()
    val channels = flat.size / stride
    if (channels <= 0) return emptyList()
    return List(channels) { channel ->
        val base = channel * stride
        ChannelScopeChannelTextState(
            channelIndex = flat.getOrElse(base + 0) { channel },
            note = flat.getOrElse(base + 1) { -1 },
            volume = flat.getOrElse(base + 2) { 0 },
            effectLetterAscii = flat.getOrElse(base + 3) { 0 },
            effectParam = flat.getOrElse(base + 4) { -1 },
            instrumentIndex = flat.getOrElse(base + 5) { -1 },
            sampleIndex = flat.getOrElse(base + 6) { -1 },
            flags = flat.getOrElse(base + 7) { 0 }
        )
    }
}

private fun parseOpenMptIndexedNames(raw: String): Map<Int, String> {
    if (raw.isBlank()) return emptyMap()
    val out = LinkedHashMap<Int, String>()
    raw.lineSequence().forEach { lineRaw ->
        val line = lineRaw.trim()
        if (line.isEmpty()) return@forEach
        val dotIndex = line.indexOf(". ")
        if (dotIndex <= 0) return@forEach
        val index = line.substring(0, dotIndex).toIntOrNull() ?: return@forEach
        val name = line.substring(dotIndex + 2).trim()
        if (index > 0) {
            out[index] = name
        }
    }
    return out
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
    val customGridColorArgb: Int,
    val showArtworkBackground: Boolean,
    val backgroundMode: VisualizationChannelScopeBackgroundMode,
    val customBackgroundColorArgb: Int,
    val textEnabled: Boolean,
    val textAnchor: VisualizationChannelScopeTextAnchor,
    val textPaddingDp: Int,
    val textSizeSp: Int,
    val textHideWhenOverflow: Boolean,
    val textShadowEnabled: Boolean,
    val textFont: VisualizationChannelScopeTextFont,
    val textColorMode: VisualizationChannelScopeTextColorMode,
    val customTextColorArgb: Int,
    val textNoteFormat: VisualizationNoteNameFormat,
    val textShowChannel: Boolean,
    val textShowNote: Boolean,
    val textShowVolume: Boolean,
    val textShowEffect: Boolean,
    val textShowInstrumentSample: Boolean,
    val textVuEnabled: Boolean,
    val textVuAnchor: VisualizationVuAnchor,
    val textVuColorMode: VisualizationChannelScopeTextColorMode,
    val textVuCustomColorArgb: Int
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
        private const val KEY_SHOW_ARTWORK_BACKGROUND = "visualization_channel_scope_show_artwork_background"
        private const val KEY_BACKGROUND_MODE = "visualization_channel_scope_background_mode"
        private const val KEY_CUSTOM_BACKGROUND_COLOR_ARGB = "visualization_channel_scope_custom_background_color_argb"
        private const val KEY_LAYOUT = "visualization_channel_scope_layout"
        private const val KEY_LINE_COLOR_MODE_NO_ARTWORK = "visualization_channel_scope_line_color_mode_no_artwork"
        private const val KEY_GRID_COLOR_MODE_NO_ARTWORK = "visualization_channel_scope_grid_color_mode_no_artwork"
        private const val KEY_LINE_COLOR_MODE_WITH_ARTWORK = "visualization_channel_scope_line_color_mode_with_artwork"
        private const val KEY_GRID_COLOR_MODE_WITH_ARTWORK = "visualization_channel_scope_grid_color_mode_with_artwork"
        private const val KEY_CUSTOM_LINE_COLOR_ARGB = "visualization_channel_scope_custom_line_color_argb"
        private const val KEY_CUSTOM_GRID_COLOR_ARGB = "visualization_channel_scope_custom_grid_color_argb"
        private const val KEY_TEXT_ENABLED = "visualization_channel_scope_text_enabled"
        private const val KEY_TEXT_ANCHOR = "visualization_channel_scope_text_anchor"
        private const val KEY_TEXT_PADDING_DP = "visualization_channel_scope_text_padding_dp"
        private const val KEY_TEXT_SIZE_SP = "visualization_channel_scope_text_size_sp"
        private const val KEY_TEXT_HIDE_WHEN_OVERFLOW = "visualization_channel_scope_text_hide_when_overflow"
        private const val KEY_TEXT_SHADOW_ENABLED = "visualization_channel_scope_text_shadow_enabled"
        private const val KEY_TEXT_FONT = "visualization_channel_scope_text_font"
        private const val KEY_TEXT_COLOR_MODE = "visualization_channel_scope_text_color_mode"
        private const val KEY_CUSTOM_TEXT_COLOR_ARGB = "visualization_channel_scope_custom_text_color_argb"
        private const val KEY_TEXT_NOTE_FORMAT = "visualization_channel_scope_text_note_format"
        private const val KEY_TEXT_SHOW_CHANNEL = "visualization_channel_scope_text_show_channel"
        private const val KEY_TEXT_SHOW_NOTE = "visualization_channel_scope_text_show_note"
        private const val KEY_TEXT_SHOW_VOLUME = "visualization_channel_scope_text_show_volume"
        private const val KEY_TEXT_SHOW_EFFECT = "visualization_channel_scope_text_show_effect"
        private const val KEY_TEXT_SHOW_INSTRUMENT_SAMPLE = "visualization_channel_scope_text_show_instrument_sample"
        private const val KEY_TEXT_VU_ENABLED = "visualization_channel_scope_text_vu_enabled"
        private const val KEY_TEXT_VU_ANCHOR = "visualization_channel_scope_text_vu_anchor"
        private const val KEY_TEXT_VU_COLOR_MODE = "visualization_channel_scope_text_vu_color_mode"
        private const val KEY_TEXT_VU_CUSTOM_COLOR_ARGB = "visualization_channel_scope_text_vu_custom_color_argb"

        fun from(sharedPrefs: android.content.SharedPreferences): ChannelScopePrefs {
            val defaultTriggerStorage = AppDefaults.Visualization.ChannelScope.triggerMode.storageValue
            val triggerModeNative = when (sharedPrefs.getString(KEY_TRIGGER_MODE, defaultTriggerStorage)) {
                "rising" -> 1
                "falling" -> 2
                else -> 0
            }
            return ChannelScopePrefs(
                windowMs = sharedPrefs.getInt(
                    KEY_WINDOW_MS,
                    AppDefaults.Visualization.ChannelScope.windowMs
                ).coerceIn(
                    AppDefaults.Visualization.ChannelScope.windowRangeMs.first,
                    AppDefaults.Visualization.ChannelScope.windowRangeMs.last
                ),
                renderBackend = VisualizationRenderBackend.fromStorage(
                    sharedPrefs.getString(
                        KEY_RENDER_BACKEND,
                        AppDefaults.Visualization.ChannelScope.renderBackend.storageValue
                    ),
                    AppDefaults.Visualization.ChannelScope.renderBackend
                ),
                dcRemovalEnabled = sharedPrefs.getBoolean(
                    KEY_DC_REMOVAL_ENABLED,
                    AppDefaults.Visualization.ChannelScope.dcRemovalEnabled
                ),
                gainPercent = sharedPrefs.getInt(
                    KEY_GAIN_PERCENT,
                    AppDefaults.Visualization.ChannelScope.gainPercent
                ).coerceIn(
                    AppDefaults.Visualization.ChannelScope.gainRangePercent.first,
                    AppDefaults.Visualization.ChannelScope.gainRangePercent.last
                ),
                triggerModeNative = triggerModeNative,
                fpsMode = VisualizationOscFpsMode.fromStorage(
                    sharedPrefs.getString(
                        KEY_FPS_MODE,
                        AppDefaults.Visualization.ChannelScope.fpsMode.storageValue
                    )
                ),
                lineWidthDp = sharedPrefs.getInt(
                    KEY_LINE_WIDTH_DP,
                    AppDefaults.Visualization.ChannelScope.lineWidthDp
                ).coerceIn(
                    AppDefaults.Visualization.ChannelScope.lineWidthRangeDp.first,
                    AppDefaults.Visualization.ChannelScope.lineWidthRangeDp.last
                ),
                gridWidthDp = sharedPrefs.getInt(
                    KEY_GRID_WIDTH_DP,
                    AppDefaults.Visualization.ChannelScope.gridWidthDp
                ).coerceIn(
                    AppDefaults.Visualization.ChannelScope.gridWidthRangeDp.first,
                    AppDefaults.Visualization.ChannelScope.gridWidthRangeDp.last
                ),
                verticalGridEnabled = sharedPrefs.getBoolean(
                    KEY_VERTICAL_GRID_ENABLED,
                    AppDefaults.Visualization.ChannelScope.verticalGridEnabled
                ),
                centerLineEnabled = sharedPrefs.getBoolean(
                    KEY_CENTER_LINE_ENABLED,
                    AppDefaults.Visualization.ChannelScope.centerLineEnabled
                ),
                layout = VisualizationChannelScopeLayout.fromStorage(
                    sharedPrefs.getString(
                        KEY_LAYOUT,
                        AppDefaults.Visualization.ChannelScope.layout.storageValue
                    )
                ),
                lineColorModeNoArtwork = VisualizationOscColorMode.fromStorage(
                    sharedPrefs.getString(
                        KEY_LINE_COLOR_MODE_NO_ARTWORK,
                        AppDefaults.Visualization.ChannelScope.lineColorModeNoArtwork.storageValue
                    ),
                    AppDefaults.Visualization.ChannelScope.lineColorModeNoArtwork
                ),
                gridColorModeNoArtwork = VisualizationOscColorMode.fromStorage(
                    sharedPrefs.getString(
                        KEY_GRID_COLOR_MODE_NO_ARTWORK,
                        AppDefaults.Visualization.ChannelScope.gridColorModeNoArtwork.storageValue
                    ),
                    AppDefaults.Visualization.ChannelScope.gridColorModeNoArtwork
                ),
                lineColorModeWithArtwork = VisualizationOscColorMode.fromStorage(
                    sharedPrefs.getString(
                        KEY_LINE_COLOR_MODE_WITH_ARTWORK,
                        AppDefaults.Visualization.ChannelScope.lineColorModeWithArtwork.storageValue
                    ),
                    AppDefaults.Visualization.ChannelScope.lineColorModeWithArtwork
                ),
                gridColorModeWithArtwork = VisualizationOscColorMode.fromStorage(
                    sharedPrefs.getString(
                        KEY_GRID_COLOR_MODE_WITH_ARTWORK,
                        AppDefaults.Visualization.ChannelScope.gridColorModeWithArtwork.storageValue
                    ),
                    AppDefaults.Visualization.ChannelScope.gridColorModeWithArtwork
                ),
                customLineColorArgb = sharedPrefs.getInt(
                    KEY_CUSTOM_LINE_COLOR_ARGB,
                    AppDefaults.Visualization.ChannelScope.customLineColorArgb
                ),
                customGridColorArgb = sharedPrefs.getInt(
                    KEY_CUSTOM_GRID_COLOR_ARGB,
                    AppDefaults.Visualization.ChannelScope.customGridColorArgb
                ),
                showArtworkBackground = sharedPrefs.getBoolean(
                    KEY_SHOW_ARTWORK_BACKGROUND,
                    AppDefaults.Visualization.ChannelScope.showArtworkBackground
                ),
                backgroundMode = VisualizationChannelScopeBackgroundMode.fromStorage(
                    sharedPrefs.getString(
                        KEY_BACKGROUND_MODE,
                        AppDefaults.Visualization.ChannelScope.backgroundMode.storageValue
                    )
                ),
                customBackgroundColorArgb = sharedPrefs.getInt(
                    KEY_CUSTOM_BACKGROUND_COLOR_ARGB,
                    AppDefaults.Visualization.ChannelScope.customBackgroundColorArgb
                ),
                textEnabled = sharedPrefs.getBoolean(
                    KEY_TEXT_ENABLED,
                    AppDefaults.Visualization.ChannelScope.textEnabled
                ),
                textAnchor = VisualizationChannelScopeTextAnchor.fromStorage(
                    sharedPrefs.getString(
                        KEY_TEXT_ANCHOR,
                        AppDefaults.Visualization.ChannelScope.textAnchor.storageValue
                    )
                ),
                textPaddingDp = sharedPrefs.getInt(
                    KEY_TEXT_PADDING_DP,
                    AppDefaults.Visualization.ChannelScope.textPaddingDp
                ).coerceIn(
                    AppDefaults.Visualization.ChannelScope.textPaddingRangeDp.first,
                    AppDefaults.Visualization.ChannelScope.textPaddingRangeDp.last
                ),
                textSizeSp = sharedPrefs.getInt(
                    KEY_TEXT_SIZE_SP,
                    AppDefaults.Visualization.ChannelScope.textSizeSp
                ).coerceIn(
                    AppDefaults.Visualization.ChannelScope.textSizeRangeSp.first,
                    AppDefaults.Visualization.ChannelScope.textSizeRangeSp.last
                ),
                textHideWhenOverflow = sharedPrefs.getBoolean(
                    KEY_TEXT_HIDE_WHEN_OVERFLOW,
                    AppDefaults.Visualization.ChannelScope.textHideWhenOverflow
                ),
                textShadowEnabled = sharedPrefs.getBoolean(
                    KEY_TEXT_SHADOW_ENABLED,
                    AppDefaults.Visualization.ChannelScope.textShadowEnabled
                ),
                textFont = VisualizationChannelScopeTextFont.fromStorage(
                    sharedPrefs.getString(
                        KEY_TEXT_FONT,
                        AppDefaults.Visualization.ChannelScope.textFont.storageValue
                    )
                ),
                textColorMode = VisualizationChannelScopeTextColorMode.fromStorage(
                    sharedPrefs.getString(
                        KEY_TEXT_COLOR_MODE,
                        AppDefaults.Visualization.ChannelScope.textColorMode.storageValue
                    )
                ),
                customTextColorArgb = sharedPrefs.getInt(
                    KEY_CUSTOM_TEXT_COLOR_ARGB,
                    AppDefaults.Visualization.ChannelScope.customTextColorArgb
                ),
                textNoteFormat = VisualizationNoteNameFormat.fromStorage(
                    sharedPrefs.getString(
                        KEY_TEXT_NOTE_FORMAT,
                        AppDefaults.Visualization.ChannelScope.textNoteFormat.storageValue
                    )
                ),
                textShowChannel = sharedPrefs.getBoolean(
                    KEY_TEXT_SHOW_CHANNEL,
                    AppDefaults.Visualization.ChannelScope.textShowChannel
                ),
                textShowNote = sharedPrefs.getBoolean(
                    KEY_TEXT_SHOW_NOTE,
                    AppDefaults.Visualization.ChannelScope.textShowNote
                ),
                textShowVolume = sharedPrefs.getBoolean(
                    KEY_TEXT_SHOW_VOLUME,
                    AppDefaults.Visualization.ChannelScope.textShowVolume
                ),
                textShowEffect = sharedPrefs.getBoolean(
                    KEY_TEXT_SHOW_EFFECT,
                    AppDefaults.Visualization.ChannelScope.textShowEffect
                ),
                textShowInstrumentSample = sharedPrefs.getBoolean(
                    KEY_TEXT_SHOW_INSTRUMENT_SAMPLE,
                    AppDefaults.Visualization.ChannelScope.textShowInstrumentSample
                ),
                textVuEnabled = sharedPrefs.getBoolean(
                    KEY_TEXT_VU_ENABLED,
                    AppDefaults.Visualization.ChannelScope.textVuEnabled
                ),
                textVuAnchor = VisualizationVuAnchor.fromStorage(
                    sharedPrefs.getString(
                        KEY_TEXT_VU_ANCHOR,
                        AppDefaults.Visualization.ChannelScope.textVuAnchor.storageValue
                    )
                ),
                textVuColorMode = VisualizationChannelScopeTextColorMode.fromStorage(
                    sharedPrefs.getString(
                        KEY_TEXT_VU_COLOR_MODE,
                        AppDefaults.Visualization.ChannelScope.textVuColorMode.storageValue
                    )
                ),
                textVuCustomColorArgb = sharedPrefs.getInt(
                    KEY_TEXT_VU_CUSTOM_COLOR_ARGB,
                    AppDefaults.Visualization.ChannelScope.textVuCustomColorArgb
                )
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
    val channelTextStates: List<ChannelScopeChannelTextState>,
    val instrumentNamesByIndex: Map<Int, String>,
    val sampleNamesByIndex: Map<Int, String>,
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
    val customGridColorArgb: Int,
    val textEnabled: Boolean,
    val textAnchor: VisualizationChannelScopeTextAnchor,
    val textPaddingDp: Int,
    val textSizeSp: Int,
    val textHideWhenOverflow: Boolean,
    val textShadowEnabled: Boolean,
    val textFont: VisualizationChannelScopeTextFont,
    val textColorMode: VisualizationChannelScopeTextColorMode,
    val customTextColorArgb: Int,
    val textNoteFormat: VisualizationNoteNameFormat,
    val textShowChannel: Boolean,
    val textShowNote: Boolean,
    val textShowVolume: Boolean,
    val textShowEffect: Boolean,
    val textShowInstrumentSample: Boolean,
    val textVuEnabled: Boolean,
    val textVuAnchor: VisualizationVuAnchor,
    val textVuColorMode: VisualizationChannelScopeTextColorMode,
    val textVuCustomColorArgb: Int
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
    visualizationOscRenderBackend: VisualizationRenderBackend,
    visualizationBarSmoothingPercent: Int,
    visualizationVuSmoothingPercent: Int,
    barCount: Int,
    barRoundnessDp: Int,
    barOverlayArtwork: Boolean,
    barUseThemeColor: Boolean,
    barRenderBackend: VisualizationRenderBackend,
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
    vuRenderBackend: VisualizationRenderBackend,
    vuColorModeNoArtwork: VisualizationOscColorMode,
    vuColorModeWithArtwork: VisualizationOscColorMode,
    vuCustomColorArgb: Int,
    channelScopePrefs: ChannelScopePrefs,
    artworkCornerRadiusDp: Int = AppDefaults.Player.artworkCornerRadiusDp,
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
    var visChannelScopeTextStates by remember { mutableStateOf<List<ChannelScopeChannelTextState>>(emptyList()) }
    var visChannelScopeTextRawCache by remember { mutableStateOf(IntArray(0)) }
    var visChannelScopeLastTextPollNs by remember { mutableStateOf(0L) }
    var visOpenMptInstrumentNamesByIndex by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var visOpenMptSampleNamesByIndex by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var visDebugUpdateFps by remember { mutableIntStateOf(0) }
    var visDebugUpdateFrameMs by remember { mutableIntStateOf(0) }
    var visDebugDrawFps by remember { mutableIntStateOf(0) }
    var visDebugDrawFrameMs by remember { mutableIntStateOf(0) }
    val visDebugAccumulator = remember { VisualizationDebugAccumulator() }
    var lastVisualizationBackend by remember {
        mutableStateOf(
            if (visualizationMode == VisualizationMode.ChannelScope) {
                channelScopePrefs.renderBackend
            } else if (visualizationMode == VisualizationMode.Oscilloscope) {
                visualizationOscRenderBackend
            } else if (visualizationMode == VisualizationMode.Bars) {
                barRenderBackend
            } else if (visualizationMode == VisualizationMode.VuMeters) {
                vuRenderBackend
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
        visChannelScopeTextStates = emptyList()
        visChannelScopeTextRawCache = IntArray(0)
        visChannelScopeLastTextPollNs = 0L
        visOpenMptInstrumentNamesByIndex = emptyMap()
        visOpenMptSampleNamesByIndex = emptyMap()
        visDebugUpdateFps = 0
        visDebugUpdateFrameMs = 0
        visDebugDrawFps = 0
        visDebugDrawFrameMs = 0
        visDebugAccumulator.frameCount = 0
        visDebugAccumulator.windowStartNs = 0L
        visDebugAccumulator.lastFrameNs = 0L
        visDebugAccumulator.latestFrameMs = 0
        visDebugAccumulator.lastUiPublishNs = 0L
    }
    LaunchedEffect(file?.absolutePath, decoderName) {
        if (file == null || pluginNameForCoreName(decoderName) != "LibOpenMPT") {
            visOpenMptInstrumentNamesByIndex = emptyMap()
            visOpenMptSampleNamesByIndex = emptyMap()
            return@LaunchedEffect
        }
        visOpenMptInstrumentNamesByIndex = parseOpenMptIndexedNames(NativeBridge.getOpenMptInstrumentNames())
        visOpenMptSampleNamesByIndex = parseOpenMptIndexedNames(NativeBridge.getOpenMptSampleNames())
    }
    LaunchedEffect(
        visualizationMode,
        visualizationOscRenderBackend,
        barRenderBackend,
        vuRenderBackend,
        channelScopePrefs.renderBackend
    ) {
        val nextBackend = if (visualizationMode == VisualizationMode.ChannelScope) {
            channelScopePrefs.renderBackend
        } else if (visualizationMode == VisualizationMode.Oscilloscope) {
            visualizationOscRenderBackend
        } else if (visualizationMode == VisualizationMode.Bars) {
            barRenderBackend
        } else if (visualizationMode == VisualizationMode.VuMeters) {
            vuRenderBackend
        } else {
            visualizationRenderBackendForMode(visualizationMode)
        }
        try {
            if (nextBackend != lastVisualizationBackend) {
                backendTransitionBlackAlpha.snapTo(0f)
                backendTransitionBlackAlpha.animateTo(1f, animationSpec = tween(85))
                backendTransitionBlackAlpha.animateTo(0f, animationSpec = tween(145))
            } else if (backendTransitionBlackAlpha.value > 0f) {
                backendTransitionBlackAlpha.snapTo(0f)
            }
            lastVisualizationBackend = nextBackend
        } finally {
            // Rapid switches can cancel this effect mid-transition.
            // Ensure the blackout overlay never remains latched.
            withContext(NonCancellable) {
                if (backendTransitionBlackAlpha.value > 0f) {
                    backendTransitionBlackAlpha.snapTo(0f)
                }
            }
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
        data class VisualizationSnapshot(
            val waveLeft: FloatArray,
            val waveRight: FloatArray,
            val bars: FloatArray,
            val vu: FloatArray,
            val channelCount: Int,
            val channelScopesFlat: FloatArray,
            val channelScopeTextRaw: IntArray?
        )
        var nextFrameTickNs = 0L
        var lastPollIntervalNs = 0L
        while (true) {
            val frameStartNs = System.nanoTime()
            if (visualizationMode != VisualizationMode.Off && file != null && isPlaying) {
                val textPollIntervalNs = 120_000_000L
                val shouldPollText =
                    visChannelScopeLastTextPollNs == 0L ||
                        frameStartNs - visChannelScopeLastTextPollNs >= textPollIntervalNs
                val snapshot = withContext(Dispatchers.Default) {
                    if (NativeBridge.isSeekInProgress()) {
                        null
                    } else {
                        val waveLeft = NativeBridge.getVisualizationWaveformScope(
                            0,
                            visualizationOscWindowMs,
                            visualizationOscTriggerModeNative
                        )
                        val waveRight = NativeBridge.getVisualizationWaveformScope(
                            1,
                            visualizationOscWindowMs,
                            visualizationOscTriggerModeNative
                        )
                        val bars = NativeBridge.getVisualizationBars()
                        val vu = NativeBridge.getVisualizationVuLevels()
                        val channelCount = NativeBridge.getVisualizationChannelCount().coerceAtLeast(1)
                        var channelScopesFlat = FloatArray(0)
                        var channelScopeTextRaw: IntArray? = null
                        if (
                            visualizationMode == VisualizationMode.ChannelScope &&
                            pluginNameForCoreName(decoderName) == "LibOpenMPT"
                        ) {
                            val scopeSamples = computeChannelScopeSampleCount(
                                windowMs = channelScopePrefs.windowMs,
                                sampleRateHz = sampleRateHz
                            )
                            channelScopesFlat = NativeBridge.getChannelScopeSamples(scopeSamples)
                            val scopeChannels = if (scopeSamples > 0) {
                                (channelScopesFlat.size / scopeSamples).coerceIn(1, 64)
                            } else {
                                channelCount.coerceIn(1, 64)
                            }
                            if (shouldPollText) {
                                channelScopeTextRaw = NativeBridge.getChannelScopeTextState(scopeChannels)
                            }
                        }
                        VisualizationSnapshot(
                            waveLeft = waveLeft,
                            waveRight = waveRight,
                            bars = bars,
                            vu = vu,
                            channelCount = channelCount,
                            channelScopesFlat = channelScopesFlat,
                            channelScopeTextRaw = channelScopeTextRaw
                        )
                    }
                }
                if (snapshot == null) {
                    val delayMs = 90L
                    delay(delayMs)
                    nextFrameTickNs = 0L
                    lastPollIntervalNs = 0L
                    continue
                }
                visWaveLeft = snapshot.waveLeft
                visWaveRight = snapshot.waveRight
                visBars = snapshot.bars
                visVu = snapshot.vu
                visChannelCount = snapshot.channelCount
                if (snapshot.channelScopesFlat.isNotEmpty()) {
                    visChannelScopesFlat = snapshot.channelScopesFlat
                }
                snapshot.channelScopeTextRaw?.let { rawText ->
                    if (!rawText.contentEquals(visChannelScopeTextRawCache)) {
                        visChannelScopeTextRawCache = rawText.copyOf()
                        visChannelScopeTextStates = parseChannelScopeTextStates(rawText)
                    }
                    visChannelScopeLastTextPollNs = frameStartNs
                }
                val frameEndNs = System.nanoTime()
                if (visDebugAccumulator.windowStartNs == 0L) {
                    visDebugAccumulator.windowStartNs = frameEndNs
                }
                if (visDebugAccumulator.lastFrameNs != 0L) {
                    visDebugAccumulator.latestFrameMs =
                        ((frameEndNs - visDebugAccumulator.lastFrameNs) / 1_000_000L).toInt().coerceAtLeast(0)
                }
                visDebugAccumulator.lastFrameNs = frameEndNs
                visDebugAccumulator.frameCount += 1
                val elapsedNs = frameEndNs - visDebugAccumulator.windowStartNs
                if (elapsedNs >= 1_000_000_000L) {
                    visDebugUpdateFps = ((visDebugAccumulator.frameCount.toDouble() * 1_000_000_000.0) / elapsedNs.toDouble())
                        .roundToInt()
                        .coerceAtLeast(0)
                    visDebugAccumulator.frameCount = 0
                    visDebugAccumulator.windowStartNs = frameEndNs
                }
                // Throttle HUD state updates to reduce recomposition overhead.
                if (frameEndNs - visDebugAccumulator.lastUiPublishNs >= 350_000_000L) {
                    visDebugUpdateFrameMs = visDebugAccumulator.latestFrameMs
                    visDebugAccumulator.lastUiPublishNs = frameEndNs
                }
            }
            val pollIntervalNs = computeVisualizationPollIntervalNs(
                isPlaying = isPlaying,
                visualizationMode = visualizationMode,
                visualizationOscFpsMode = visualizationOscFpsMode,
                channelScopeFpsMode = channelScopePrefs.fpsMode,
                displayRefreshHz = context.display?.refreshRate ?: 60f
            )
            val nowNs = System.nanoTime()
            if (pollIntervalNs != lastPollIntervalNs || nextFrameTickNs == 0L) {
                nextFrameTickNs = nowNs + pollIntervalNs
                lastPollIntervalNs = pollIntervalNs
            } else {
                while (nextFrameTickNs <= nowNs) {
                    nextFrameTickNs += pollIntervalNs
                }
            }
            val sleepNs = (nextFrameTickNs - nowNs).coerceAtLeast(0L)
            if (sleepNs >= 1_000_000L) {
                delay(sleepNs / 1_000_000L)
            } else if (sleepNs > 0L) {
                yield()
            }
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
            visChannelScopeTextStates = emptyList()
            visChannelScopeTextRawCache = IntArray(0)
            visChannelScopeLastTextPollNs = 0L
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
            visChannelScopeTextStates = emptyList()
            visChannelScopeTextRawCache = IntArray(0)
            visChannelScopeLastTextPollNs = 0L
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
            visChannelScopeTextStates = emptyList()
            visChannelScopeTextRawCache = IntArray(0)
            visChannelScopeLastTextPollNs = 0L
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
        channelTextStates = visChannelScopeTextStates,
        instrumentNamesByIndex = visOpenMptInstrumentNamesByIndex,
        sampleNamesByIndex = visOpenMptSampleNamesByIndex,
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
        customGridColorArgb = channelScopePrefs.customGridColorArgb,
        textEnabled = channelScopePrefs.textEnabled,
        textAnchor = channelScopePrefs.textAnchor,
        textPaddingDp = channelScopePrefs.textPaddingDp,
        textSizeSp = channelScopePrefs.textSizeSp,
        textHideWhenOverflow = channelScopePrefs.textHideWhenOverflow,
        textShadowEnabled = channelScopePrefs.textShadowEnabled,
        textFont = channelScopePrefs.textFont,
        textColorMode = channelScopePrefs.textColorMode,
        customTextColorArgb = channelScopePrefs.customTextColorArgb,
        textNoteFormat = channelScopePrefs.textNoteFormat,
        textShowChannel = channelScopePrefs.textShowChannel,
        textShowNote = channelScopePrefs.textShowNote,
        textShowVolume = channelScopePrefs.textShowVolume,
        textShowEffect = channelScopePrefs.textShowEffect,
        textShowInstrumentSample = channelScopePrefs.textShowInstrumentSample,
        textVuEnabled = channelScopePrefs.textVuEnabled,
        textVuAnchor = channelScopePrefs.textVuAnchor,
        textVuColorMode = channelScopePrefs.textVuColorMode,
        textVuCustomColorArgb = channelScopePrefs.textVuCustomColorArgb
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
    } else if (visualizationMode == VisualizationMode.Oscilloscope) {
        visualizationOscRenderBackend
    } else if (visualizationMode == VisualizationMode.Bars) {
        barRenderBackend
    } else if (visualizationMode == VisualizationMode.VuMeters) {
        vuRenderBackend
    } else {
        visualizationRenderBackendForMode(visualizationMode)
    }
    val themePrimaryColor = MaterialTheme.colorScheme.primary
    val useScopeArtworkBackground =
        visualizationMode != VisualizationMode.ChannelScope ||
            (
                channelScopePrefs.showArtworkBackground &&
                    channelScopePrefs.renderBackend != VisualizationRenderBackend.OpenGlSurface
                )
    val scopeBackgroundColor = remember(
        artwork,
        themePrimaryColor,
        channelScopePrefs.backgroundMode,
        channelScopePrefs.customBackgroundColorArgb
    ) {
        when (channelScopePrefs.backgroundMode) {
            VisualizationChannelScopeBackgroundMode.Custom -> Color(channelScopePrefs.customBackgroundColorArgb)
            VisualizationChannelScopeBackgroundMode.AutoDarkAccent -> {
                val accent = extractArtworkAccentColor(artwork)
                    ?: themePrimaryColor.copy(alpha = 1f)
                // Keep it dark for scope contrast, but avoid collapsing to plain black.
                val darkTint = lerp(accent, Color.Black, 0.62f)
                val floor = Color(0xFF101418)
                lerp(floor, darkTint, 0.70f).copy(alpha = 1f)
            }
        }
    }

    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(artworkCornerRadiusDp.coerceIn(0, 48).dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (useScopeArtworkBackground) {
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
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(scopeBackgroundColor)
                )
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
                barRenderBackend = barRenderBackend,
                barColorModeNoArtwork = barColorModeNoArtwork,
                barColorModeWithArtwork = barColorModeWithArtwork,
                barCustomColorArgb = barCustomColorArgb,
                oscStereo = oscStereo,
                oscRenderBackend = visualizationOscRenderBackend,
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
                vuRenderBackend = vuRenderBackend,
                vuColorModeNoArtwork = vuColorModeNoArtwork,
                vuColorModeWithArtwork = vuColorModeWithArtwork,
                vuCustomColorArgb = vuCustomColorArgb,
                channelScopeHistories = channelScopeState.channelHistories,
                channelScopeTextStates = channelScopeState.channelTextStates,
                channelScopeInstrumentNamesByIndex = channelScopeState.instrumentNamesByIndex,
                channelScopeSampleNamesByIndex = channelScopeState.sampleNamesByIndex,
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
                channelScopeBackgroundColorArgb = scopeBackgroundColor.toArgb(),
                channelScopeTextEnabled = channelScopeState.textEnabled,
                channelScopeTextAnchor = channelScopeState.textAnchor,
                channelScopeTextPaddingDp = channelScopeState.textPaddingDp,
                channelScopeTextSizeSp = channelScopeState.textSizeSp,
                channelScopeTextHideWhenOverflow = channelScopeState.textHideWhenOverflow,
                channelScopeTextShadowEnabled = channelScopeState.textShadowEnabled,
                channelScopeTextFont = channelScopeState.textFont,
                channelScopeTextColorMode = channelScopeState.textColorMode,
                channelScopeCustomTextColorArgb = channelScopeState.customTextColorArgb,
                channelScopeTextNoteFormat = channelScopeState.textNoteFormat,
                channelScopeTextShowChannel = channelScopeState.textShowChannel,
                channelScopeTextShowNote = channelScopeState.textShowNote,
                channelScopeTextShowVolume = channelScopeState.textShowVolume,
                channelScopeTextShowEffect = channelScopeState.textShowEffect,
                channelScopeTextShowInstrumentSample = channelScopeState.textShowInstrumentSample,
                channelScopeTextVuEnabled = channelScopeState.textVuEnabled,
                channelScopeTextVuAnchor = channelScopeState.textVuAnchor,
                channelScopeTextVuColorMode = channelScopeState.textVuColorMode,
                channelScopeTextVuCustomColorArgb = channelScopeState.textVuCustomColorArgb,
                channelScopeCornerRadiusDp = artworkCornerRadiusDp.coerceIn(0, 48),
                channelScopeOnFrameStats = { fps, frameMs ->
                    visDebugDrawFps = fps.coerceAtLeast(0)
                    visDebugDrawFrameMs = frameMs.coerceAtLeast(0)
                },
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
                        text = "Mode: ${visualizationMode.label}\nBackend: ${activeRenderBackend.label}\nUpdate: ${visDebugUpdateFps} fps  (${visDebugUpdateFrameMs} ms)\nDraw: ${
                            if (activeRenderBackend != VisualizationRenderBackend.Compose) {
                                "${visDebugDrawFps} fps  (${visDebugDrawFrameMs} ms)"
                            } else {
                                "N/A"
                            }
                        }",
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
