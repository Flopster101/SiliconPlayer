package com.flopster101.siliconplayer.ui.visualization.basic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextAlign
import com.flopster101.siliconplayer.VisualizationChannelScopeLayout
import com.flopster101.siliconplayer.VisualizationChannelScopeTextAnchor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.VisualizationNoteNameFormat
import com.flopster101.siliconplayer.VisualizationOscColorMode
import com.flopster101.siliconplayer.VisualizationRenderBackend
import com.flopster101.siliconplayer.VisualizationVuAnchor
import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeChannelTextState
import com.flopster101.siliconplayer.ui.visualization.advanced.ChannelScopeVisualization
import com.flopster101.siliconplayer.ui.visualization.gl.ChannelScopeGlVisualization
import com.flopster101.siliconplayer.ui.visualization.gl.ChannelScopeGlTextureVisualization
import kotlin.math.max
import kotlin.math.min
import kotlin.math.ceil
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flopster101.siliconplayer.NativeBridge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun BasicVisualizationOverlay(
    mode: VisualizationMode,
    bars: FloatArray,
    waveformLeft: FloatArray,
    waveformRight: FloatArray,
    vuLevels: FloatArray,
    channelCount: Int,
    barCount: Int,
    barRoundnessDp: Int,
    barOverlayArtwork: Boolean,
    barUseThemeColor: Boolean,
    barColorModeNoArtwork: VisualizationOscColorMode,
    barColorModeWithArtwork: VisualizationOscColorMode,
    barCustomColorArgb: Int,
    oscStereo: Boolean,
    artwork: ImageBitmap?,
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
    channelScopeHistories: List<FloatArray>,
    channelScopeTextStates: List<ChannelScopeChannelTextState>,
    channelScopeTriggerModeNative: Int,
    channelScopeTriggerIndices: IntArray,
    channelScopeRenderBackend: VisualizationRenderBackend,
    channelScopeLineWidthDp: Int,
    channelScopeGridWidthDp: Int,
    channelScopeVerticalGridEnabled: Boolean,
    channelScopeCenterLineEnabled: Boolean,
    channelScopeLayout: VisualizationChannelScopeLayout,
    channelScopeLineColorModeNoArtwork: VisualizationOscColorMode,
    channelScopeGridColorModeNoArtwork: VisualizationOscColorMode,
    channelScopeLineColorModeWithArtwork: VisualizationOscColorMode,
    channelScopeGridColorModeWithArtwork: VisualizationOscColorMode,
    channelScopeCustomLineColorArgb: Int,
    channelScopeCustomGridColorArgb: Int,
    channelScopeTextEnabled: Boolean,
    channelScopeTextAnchor: VisualizationChannelScopeTextAnchor,
    channelScopeTextPaddingDp: Int,
    channelScopeTextSizeSp: Int,
    channelScopeTextNoteFormat: VisualizationNoteNameFormat,
    channelScopeTextShowChannel: Boolean,
    channelScopeTextShowNote: Boolean,
    channelScopeTextShowVolume: Boolean,
    channelScopeTextShowEffect: Boolean,
    channelScopeTextShowInstrumentSample: Boolean,
    channelScopeCornerRadiusDp: Int = 0,
    channelScopeOnFrameStats: ((fps: Int, frameMs: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (mode == VisualizationMode.Off) return

    val monetBarColor = if (barUseThemeColor) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f)
    }
    val monetOscLineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
    val monetOscGridColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
    val artworkBaseColor = remember(artwork) {
        extractArtworkAccentColor(artwork)?.let(::invertColor)
    } ?: monetOscLineColor
    val artworkGridColor = artworkBaseColor.copy(alpha = 0.34f)
    val hasArtwork = artwork != null
    val barCustomColor = Color(barCustomColorArgb)
    val customLineColor = Color(oscCustomLineColorArgb)
    val customGridColor = Color(oscCustomGridColorArgb)
    val barColor = resolveOscColor(
        hasArtwork = hasArtwork,
        noArtworkMode = barColorModeNoArtwork,
        withArtworkMode = barColorModeWithArtwork,
        artworkColor = artworkBaseColor.copy(alpha = monetBarColor.alpha),
        monetColor = monetBarColor,
        customColor = barCustomColor.copy(alpha = monetBarColor.alpha)
    )
    val oscColor = resolveOscColor(
        hasArtwork = hasArtwork,
        noArtworkMode = oscLineColorModeNoArtwork,
        withArtworkMode = oscLineColorModeWithArtwork,
        artworkColor = artworkBaseColor,
        monetColor = monetOscLineColor,
        customColor = customLineColor
    )
    val oscGridColor = resolveOscColor(
        hasArtwork = hasArtwork,
        noArtworkMode = oscGridColorModeNoArtwork,
        withArtworkMode = oscGridColorModeWithArtwork,
        artworkColor = artworkGridColor,
        monetColor = monetOscGridColor,
        customColor = customGridColor
    )
    val monetVuColor = if (vuUseThemeColor) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f)
    }
    val vuCustomColor = Color(vuCustomColorArgb)
    val vuAccentColor = resolveOscColor(
        hasArtwork = hasArtwork,
        noArtworkMode = vuColorModeNoArtwork,
        withArtworkMode = vuColorModeWithArtwork,
        artworkColor = artworkBaseColor.copy(alpha = monetVuColor.alpha),
        monetColor = monetVuColor,
        customColor = vuCustomColor.copy(alpha = monetVuColor.alpha)
    )
    val vuColor = vuAccentColor
    val vuLabelColor = deriveVuLabelColor(vuAccentColor)
    val vuBackgroundColor = deriveVuTrackColor(vuAccentColor)
    val channelScopeCustomLineColor = Color(channelScopeCustomLineColorArgb)
    val channelScopeCustomGridColor = Color(channelScopeCustomGridColorArgb)
    val channelScopeCornerRadiusShape = RoundedCornerShape(channelScopeCornerRadiusDp.coerceIn(0, 48).dp)
    val channelScopeCornerRadiusPx = with(LocalDensity.current) {
        channelScopeCornerRadiusDp.coerceIn(0, 48).dp.toPx()
    }
    val channelScopeLineColor = resolveOscColor(
        hasArtwork = hasArtwork,
        noArtworkMode = channelScopeLineColorModeNoArtwork,
        withArtworkMode = channelScopeLineColorModeWithArtwork,
        artworkColor = artworkBaseColor,
        monetColor = monetOscLineColor,
        customColor = channelScopeCustomLineColor
    )
    val channelScopeGridColor = resolveOscColor(
        hasArtwork = hasArtwork,
        noArtworkMode = channelScopeGridColorModeNoArtwork,
        withArtworkMode = channelScopeGridColorModeWithArtwork,
        artworkColor = artworkGridColor,
        monetColor = monetOscGridColor,
        customColor = channelScopeCustomGridColor
    )
    val barBackgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)

    when (mode) {
        VisualizationMode.Bars -> {
            BarsVisualization(
                bars = bars,
                barCount = barCount,
                barRoundnessDp = barRoundnessDp,
                barOverlayArtwork = barOverlayArtwork,
                barColor = barColor,
                backgroundColor = barBackgroundColor,
                modifier = modifier
            )
        }

        VisualizationMode.Oscilloscope -> {
            OscilloscopeVisualization(
                waveformLeft = waveformLeft,
                waveformRight = waveformRight,
                channelCount = channelCount,
                oscStereo = oscStereo,
                oscColor = oscColor,
                gridColor = oscGridColor,
                lineWidthPx = oscLineWidthDp.toFloat(),
                gridWidthPx = oscGridWidthDp.toFloat(),
                showVerticalGrid = oscVerticalGridEnabled,
                showCenterLine = oscCenterLineEnabled,
                modifier = modifier
            )
        }

        VisualizationMode.VuMeters -> {
            VuMetersVisualization(
                vuLevels = vuLevels,
                channelCount = channelCount,
                vuAnchor = vuAnchor,
                vuColor = vuColor,
                vuLabelColor = vuLabelColor,
                vuBackgroundColor = vuBackgroundColor,
                modifier = modifier
            )
        }

        VisualizationMode.ChannelScope -> {
            Box(modifier = modifier.clip(channelScopeCornerRadiusShape)) {
                when (channelScopeRenderBackend) {
                    VisualizationRenderBackend.Compose -> {
                        ChannelScopeVisualization(
                            channelHistories = channelScopeHistories,
                            lineColor = channelScopeLineColor,
                            gridColor = channelScopeGridColor,
                            lineWidthPx = channelScopeLineWidthDp.toFloat(),
                            gridWidthPx = channelScopeGridWidthDp.toFloat(),
                            showVerticalGrid = channelScopeVerticalGridEnabled,
                            showCenterLine = channelScopeCenterLineEnabled,
                            triggerModeNative = channelScopeTriggerModeNative,
                            triggerIndices = channelScopeTriggerIndices,
                            layoutStrategy = channelScopeLayout,
                            outerCornerRadiusPx = channelScopeCornerRadiusPx,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    VisualizationRenderBackend.OpenGlTexture -> {
                        ChannelScopeGlTextureVisualization(
                            channelHistories = channelScopeHistories,
                            lineColor = channelScopeLineColor,
                            gridColor = channelScopeGridColor,
                            lineWidthPx = channelScopeLineWidthDp.toFloat(),
                            gridWidthPx = channelScopeGridWidthDp.toFloat(),
                            showVerticalGrid = channelScopeVerticalGridEnabled,
                            showCenterLine = channelScopeCenterLineEnabled,
                            triggerModeNative = channelScopeTriggerModeNative,
                            triggerIndices = channelScopeTriggerIndices,
                            layoutStrategy = channelScopeLayout,
                            outerCornerRadiusPx = channelScopeCornerRadiusPx,
                            onFrameStats = channelScopeOnFrameStats,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    VisualizationRenderBackend.OpenGlSurface -> {
                        ChannelScopeGlVisualization(
                            channelHistories = channelScopeHistories,
                            lineColor = channelScopeLineColor,
                            gridColor = channelScopeGridColor,
                            lineWidthPx = channelScopeLineWidthDp.toFloat(),
                            gridWidthPx = channelScopeGridWidthDp.toFloat(),
                            showVerticalGrid = channelScopeVerticalGridEnabled,
                            showCenterLine = channelScopeCenterLineEnabled,
                            triggerModeNative = channelScopeTriggerModeNative,
                            triggerIndices = channelScopeTriggerIndices,
                            layoutStrategy = channelScopeLayout,
                            outerCornerRadiusPx = channelScopeCornerRadiusPx,
                            onFrameStats = channelScopeOnFrameStats,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                if (channelScopeTextEnabled && channelScopeHistories.isNotEmpty()) {
                    ChannelScopeTextOverlay(
                        channelHistories = channelScopeHistories,
                        channelTextStates = channelScopeTextStates,
                        layoutStrategy = channelScopeLayout,
                        anchor = channelScopeTextAnchor,
                        paddingDp = channelScopeTextPaddingDp,
                        textSizeSp = channelScopeTextSizeSp,
                        noteFormat = channelScopeTextNoteFormat,
                        showChannel = channelScopeTextShowChannel,
                        showNote = channelScopeTextShowNote,
                        showVolume = channelScopeTextShowVolume,
                        showEffect = channelScopeTextShowEffect,
                        showInstrumentSample = channelScopeTextShowInstrumentSample,
                        textColor = channelScopeLineColor.copy(alpha = 0.93f),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        VisualizationMode.Off -> Unit
    }
}

private fun deriveVuTrackColor(accent: Color): Color {
    val neutral = if (accent.luminance() > 0.56f) Color.Black else Color.White
    // Tonal track variant: tied to accent with contrast-aware neutral shift.
    return lerp(accent, neutral, 0.72f).copy(alpha = 0.62f)
}

@Composable
private fun ChannelScopeTextOverlay(
    channelHistories: List<FloatArray>,
    channelTextStates: List<ChannelScopeChannelTextState>,
    layoutStrategy: VisualizationChannelScopeLayout,
    anchor: VisualizationChannelScopeTextAnchor,
    paddingDp: Int,
    textSizeSp: Int,
    noteFormat: VisualizationNoteNameFormat,
    showChannel: Boolean,
    showNote: Boolean,
    showVolume: Boolean,
    showEffect: Boolean,
    showInstrumentSample: Boolean,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    if (channelHistories.isEmpty()) return
    val channels = channelHistories.size
    val (columns, rows) = resolveChannelScopeTextGrid(channels, layoutStrategy)
    val sideCounts = IntArray(2)
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val safeCols = columns.coerceAtLeast(1)
        val safeRows = rows.coerceAtLeast(1)
        val cellWidth = maxWidth / safeCols
        val cellHeight = maxHeight / safeRows
        for (col in 0 until columns) {
            for (row in 0 until rows) {
                val channel = (col * rows) + row
                val content = if (channel < channels) {
                    buildChannelScopeTextFields(
                        channel = channel,
                        state = channelTextStates.getOrNull(channel),
                        noteFormat = noteFormat,
                        showChannel = showChannel,
                        showNote = showNote,
                        showVolume = showVolume,
                        showEffect = showEffect,
                        showInstrumentSample = showInstrumentSample,
                        sideCounts = sideCounts
                    )
                } else {
                    ChannelScopeTextFields(
                        channel = null,
                        note = null,
                        volume = null,
                        effect = null,
                        instrumentOrSample = null
                    )
                }
                val hasContent =
                    content.channel != null ||
                        content.note != null ||
                        content.volume != null ||
                        content.effect != null ||
                        content.instrumentOrSample != null
                Box(
                    modifier = Modifier
                        .offset(x = cellWidth * col, y = cellHeight * row)
                        .size(cellWidth, cellHeight),
                    contentAlignment = resolveTextAlignment(anchor)
                ) {
                    if (hasContent) {
                        val scale = textSizeSp.coerceIn(8, 22).toFloat() / 8f
                        val noteSlot = (24f * scale).dp
                        val volumeSlot = (30f * scale).dp
                        val effectSlot = (20f * scale).dp
                        val textStyle = MaterialTheme.typography.labelSmall.copy(
                            fontSize = textSizeSp.coerceIn(8, 22).sp,
                            lineHeight = textSizeSp.coerceIn(8, 22).sp,
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                        Row(
                            modifier = Modifier
                                .padding(paddingDp.dp)
                                .widthIn(max = cellWidth),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var hasPrevious = false
                            if (content.channel != null) {
                                Text(
                                    text = content.channel,
                                    color = textColor,
                                    style = textStyle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip
                                )
                                hasPrevious = true
                            }
                            if (content.note != null) {
                                if (hasPrevious) Text("•", color = textColor, style = textStyle)
                                Box(modifier = Modifier.width(noteSlot), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = content.note,
                                        color = textColor,
                                        style = textStyle,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip
                                    )
                                }
                                hasPrevious = true
                            }
                            if (content.volume != null) {
                                if (hasPrevious) Text("•", color = textColor, style = textStyle)
                                Box(modifier = Modifier.width(volumeSlot), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = content.volume,
                                        color = textColor,
                                        style = textStyle,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip
                                    )
                                }
                                hasPrevious = true
                            }
                            if (content.effect != null) {
                                if (hasPrevious) Text("•", color = textColor, style = textStyle)
                                Box(modifier = Modifier.width(effectSlot), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = content.effect,
                                        color = textColor,
                                        style = textStyle,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip
                                    )
                                }
                                hasPrevious = true
                            }
                            if (content.instrumentOrSample != null) {
                                if (hasPrevious) Text("•", color = textColor, style = textStyle)
                                Text(
                                    text = content.instrumentOrSample,
                                    color = textColor,
                                    style = textStyle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun resolveTextAlignment(anchor: VisualizationChannelScopeTextAnchor): Alignment {
    return when (anchor) {
        VisualizationChannelScopeTextAnchor.TopLeft -> Alignment.TopStart
        VisualizationChannelScopeTextAnchor.TopCenter -> Alignment.TopCenter
        VisualizationChannelScopeTextAnchor.TopRight -> Alignment.TopEnd
        VisualizationChannelScopeTextAnchor.BottomRight -> Alignment.BottomEnd
        VisualizationChannelScopeTextAnchor.BottomCenter -> Alignment.BottomCenter
        VisualizationChannelScopeTextAnchor.BottomLeft -> Alignment.BottomStart
    }
}

private fun resolveTextAlign(anchor: VisualizationChannelScopeTextAnchor): TextAlign {
    return when (anchor) {
        VisualizationChannelScopeTextAnchor.TopLeft,
        VisualizationChannelScopeTextAnchor.BottomLeft -> TextAlign.Left
        VisualizationChannelScopeTextAnchor.TopCenter,
        VisualizationChannelScopeTextAnchor.BottomCenter -> TextAlign.Center
        VisualizationChannelScopeTextAnchor.TopRight,
        VisualizationChannelScopeTextAnchor.BottomRight -> TextAlign.Right
    }
}

private data class ChannelScopeTextFields(
    val channel: String?,
    val note: String?,
    val volume: String?,
    val effect: String?,
    val instrumentOrSample: String?
)

private fun buildChannelScopeTextFields(
    channel: Int,
    state: ChannelScopeChannelTextState?,
    noteFormat: VisualizationNoteNameFormat,
    showChannel: Boolean,
    showNote: Boolean,
    showVolume: Boolean,
    showEffect: Boolean,
    showInstrumentSample: Boolean,
    sideCounts: IntArray
): ChannelScopeTextFields {
    return ChannelScopeTextFields(
        channel = if (showChannel) resolveChannelLabel(channel, state, sideCounts) else null,
        note = if (showNote) (formatNoteName(state?.note ?: -1, noteFormat) ?: "--") else null,
        volume = if (showVolume) formatVolume(state?.volume ?: 0) else null,
        effect = if (showEffect) formatEffect(state) else null,
        instrumentOrSample = if (showInstrumentSample) formatInstrumentOrSample(state) else null
    )
}

private fun resolveChannelLabel(
    channel: Int,
    state: ChannelScopeChannelTextState?,
    sideCounts: IntArray
): String {
    val flags = state?.flags ?: 0
    val isLeft = (flags and NativeBridge.CHANNEL_SCOPE_TEXT_FLAG_AMIGA_LEFT) != 0
    val isRight = (flags and NativeBridge.CHANNEL_SCOPE_TEXT_FLAG_AMIGA_RIGHT) != 0
    if (isLeft) {
        sideCounts[0] = sideCounts[0] + 1
        return if (sideCounts[0] <= 2) "L${sideCounts[0]}" else "Ch ${channel + 1}"
    }
    if (isRight) {
        sideCounts[1] = sideCounts[1] + 1
        return if (sideCounts[1] <= 2) "R${sideCounts[1]}" else "Ch ${channel + 1}"
    }
    return "Ch ${channel + 1}"
}

private fun formatNoteName(note: Int, format: VisualizationNoteNameFormat): String? {
    if (note <= 0) return null
    val idx = (note - 1) % 12
    val octave = (note - 1) / 12
    val names = if (format == VisualizationNoteNameFormat.International) {
        arrayOf("Do", "Do#", "Re", "Re#", "Mi", "Fa", "Fa#", "Sol", "Sol#", "La", "La#", "Si")
    } else {
        arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    }
    return "${names[idx]}$octave"
}

private fun formatVolume(volume: Int): String {
    return "V" + volume.coerceIn(0, 999).toString().padStart(3, '0')
}

private fun formatEffect(state: ChannelScopeChannelTextState?): String {
    if (state == null) return "---"
    if (state.effectLetterAscii <= 0 || state.effectParam < 0) return "---"
    val effectChar = state.effectLetterAscii.toChar()
    val paramHex = state.effectParam.coerceIn(0, 255).toString(16).uppercase().padStart(2, '0')
    return "$effectChar$paramHex"
}

private fun formatInstrumentOrSample(state: ChannelScopeChannelTextState?): String? {
    if (state == null) return null
    if (state.instrumentIndex > 0) {
        return "Ins ${state.instrumentIndex}"
    }
    if (state.sampleIndex > 0) {
        return "Smp ${state.sampleIndex}"
    }
    return null
}

private fun resolveChannelScopeTextGrid(
    channels: Int,
    strategy: VisualizationChannelScopeLayout
): Pair<Int, Int> {
    if (channels <= 1) return 1 to 1
    return when (strategy) {
        VisualizationChannelScopeLayout.ColumnFirst -> {
            val targetRowsPerColumn = 7
            val columns = if (channels <= 4) 1 else ceil(channels / targetRowsPerColumn.toDouble()).toInt().coerceAtLeast(2)
            val rows = ceil(channels / columns.toDouble()).toInt().coerceAtLeast(1)
            columns to rows
        }
        VisualizationChannelScopeLayout.BalancedTwoColumn -> {
            val columns = ceil(kotlin.math.sqrt(channels.toDouble())).toInt().coerceAtLeast(1)
            val rows = ceil(channels / columns.toDouble()).toInt().coerceAtLeast(1)
            columns to rows
        }
    }
}

private fun deriveVuLabelColor(accent: Color): Color {
    // Label should match the filled VU color.
    return accent
}

private fun resolveOscColor(
    hasArtwork: Boolean,
    noArtworkMode: VisualizationOscColorMode,
    withArtworkMode: VisualizationOscColorMode,
    artworkColor: Color,
    monetColor: Color,
    customColor: Color
): Color {
    val mode = if (hasArtwork) withArtworkMode else noArtworkMode
    return when (mode) {
        VisualizationOscColorMode.Artwork -> artworkColor
        VisualizationOscColorMode.Monet -> monetColor
        VisualizationOscColorMode.White -> Color.White.copy(alpha = monetColor.alpha)
        VisualizationOscColorMode.Custom -> customColor
    }
}

private fun extractArtworkAccentColor(artwork: ImageBitmap?): Color? {
    if (artwork == null) return null
    val pixels = artwork.toPixelMap()
    val width = pixels.width
    val height = pixels.height
    if (width <= 0 || height <= 0) return null

    val stepX = max(1, width / 32)
    val stepY = max(1, height / 32)

    var weightedR = 0.0
    var weightedG = 0.0
    var weightedB = 0.0
    var weightSum = 0.0
    var avgR = 0.0
    var avgG = 0.0
    var avgB = 0.0
    var sampleCount = 0

    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val c = pixels[x, y]
            val r = c.red.toDouble()
            val g = c.green.toDouble()
            val b = c.blue.toDouble()
            val maxCh = max(r, max(g, b))
            val minCh = min(r, min(g, b))
            val sat = if (maxCh <= 1e-6) 0.0 else (maxCh - minCh) / maxCh
            val value = maxCh
            val weight = (0.2 + (sat * 0.8)) * (0.3 + (value * 0.7))

            weightedR += r * weight
            weightedG += g * weight
            weightedB += b * weight
            weightSum += weight

            avgR += r
            avgG += g
            avgB += b
            sampleCount++
            x += stepX
        }
        y += stepY
    }

    if (sampleCount <= 0) return null
    if (weightSum > 1e-6) {
        return Color(
            red = (weightedR / weightSum).toFloat().coerceIn(0f, 1f),
            green = (weightedG / weightSum).toFloat().coerceIn(0f, 1f),
            blue = (weightedB / weightSum).toFloat().coerceIn(0f, 1f),
            alpha = 0.92f
        )
    }

    return Color(
        red = (avgR / sampleCount.toDouble()).toFloat().coerceIn(0f, 1f),
        green = (avgG / sampleCount.toDouble()).toFloat().coerceIn(0f, 1f),
        blue = (avgB / sampleCount.toDouble()).toFloat().coerceIn(0f, 1f),
        alpha = 0.92f
    )
}

private fun invertColor(color: Color): Color {
    return Color(
        red = 1f - color.red,
        green = 1f - color.green,
        blue = 1f - color.blue,
        alpha = color.alpha
    )
}
