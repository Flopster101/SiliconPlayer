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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextAlign
import com.flopster101.siliconplayer.VisualizationChannelScopeLayout
import com.flopster101.siliconplayer.VisualizationChannelScopeTextAnchor
import com.flopster101.siliconplayer.VisualizationChannelScopeTextColorMode
import com.flopster101.siliconplayer.VisualizationChannelScopeTextFont
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.flopster101.siliconplayer.R

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
    channelScopeInstrumentNamesByIndex: Map<Int, String>,
    channelScopeSampleNamesByIndex: Map<Int, String>,
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
    channelScopeBackgroundColorArgb: Int,
    channelScopeTextEnabled: Boolean,
    channelScopeTextAnchor: VisualizationChannelScopeTextAnchor,
    channelScopeTextPaddingDp: Int,
    channelScopeTextSizeSp: Int,
    channelScopeTextHideWhenOverflow: Boolean,
    channelScopeTextShadowEnabled: Boolean,
    channelScopeTextFont: VisualizationChannelScopeTextFont,
    channelScopeTextColorMode: VisualizationChannelScopeTextColorMode,
    channelScopeCustomTextColorArgb: Int,
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
    val channelScopeCustomTextColor = Color(channelScopeCustomTextColorArgb)
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
    val channelScopeTextPalette = resolveChannelScopeTextPalette(
        mode = channelScopeTextColorMode,
        monetColor = monetOscLineColor,
        customColor = channelScopeCustomTextColor
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
                            backgroundColor = Color(channelScopeBackgroundColorArgb),
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
                        instrumentNamesByIndex = channelScopeInstrumentNamesByIndex,
                        sampleNamesByIndex = channelScopeSampleNamesByIndex,
                        layoutStrategy = channelScopeLayout,
                        anchor = channelScopeTextAnchor,
                        paddingDp = channelScopeTextPaddingDp,
                        textSizeSp = channelScopeTextSizeSp,
                        hideWhenOverflow = channelScopeTextHideWhenOverflow,
                        textShadowEnabled = channelScopeTextShadowEnabled,
                        textFont = channelScopeTextFont,
                        noteFormat = channelScopeTextNoteFormat,
                        showChannel = channelScopeTextShowChannel,
                        showNote = channelScopeTextShowNote,
                        showVolume = channelScopeTextShowVolume,
                        showEffect = channelScopeTextShowEffect,
                        showInstrumentSample = channelScopeTextShowInstrumentSample,
                        textPalette = channelScopeTextPalette,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        VisualizationMode.Off -> Unit
    }
}

private data class ChannelScopeTextPalette(
    val channel: Color,
    val note: Color,
    val volume: Color,
    val effect: Color,
    val instrumentOrSample: Color,
    val separator: Color
)

private fun resolveChannelScopeTextPalette(
    mode: VisualizationChannelScopeTextColorMode,
    monetColor: Color,
    customColor: Color
): ChannelScopeTextPalette {
    val uniform = when (mode) {
        VisualizationChannelScopeTextColorMode.Monet -> monetColor.copy(alpha = 0.93f)
        VisualizationChannelScopeTextColorMode.White -> Color.White
        VisualizationChannelScopeTextColorMode.Custom -> customColor
        VisualizationChannelScopeTextColorMode.OpenMptInspired -> Color.Unspecified
    }
    if (mode != VisualizationChannelScopeTextColorMode.OpenMptInspired) {
        return ChannelScopeTextPalette(
            channel = uniform,
            note = uniform,
            volume = uniform,
            effect = uniform,
            instrumentOrSample = uniform,
            separator = uniform.copy(alpha = 0.86f)
        )
    }
    // Inspired by OpenMPT pattern syntax-highlight groups (dark schemes).
    return when (mode) {
        VisualizationChannelScopeTextColorMode.OpenMptInspired -> ChannelScopeTextPalette(
            channel = Color(0xFFBABDB6),
            note = Color(0xFF729FCF),
            volume = Color(0xFF8AE234),
            effect = Color(0xFFFCAF3E),
            instrumentOrSample = Color.White,
            separator = Color.White.copy(alpha = 0.78f)
        )
        else -> error("Unhandled channel scope text color mode: $mode")
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
    instrumentNamesByIndex: Map<Int, String>,
    sampleNamesByIndex: Map<Int, String>,
    layoutStrategy: VisualizationChannelScopeLayout,
    anchor: VisualizationChannelScopeTextAnchor,
    paddingDp: Int,
    textSizeSp: Int,
    hideWhenOverflow: Boolean,
    textShadowEnabled: Boolean,
    textFont: VisualizationChannelScopeTextFont,
    noteFormat: VisualizationNoteNameFormat,
    showChannel: Boolean,
    showNote: Boolean,
    showVolume: Boolean,
    showEffect: Boolean,
    showInstrumentSample: Boolean,
    textPalette: ChannelScopeTextPalette,
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
        val selectedTextSizeSp = textSizeSp.coerceIn(6, 22)
        val textFontFamily = remember(textFont) { resolveChannelScopeTextFontFamily(textFont) }
        val minimumAutoTextSizeSp = (selectedTextSizeSp - 6).coerceAtLeast(6)
        val effectiveTextSizeSp = computeAutoChannelScopeTextSizeSp(
            selectedTextSizeSp = selectedTextSizeSp,
            minimumTextSizeSp = minimumAutoTextSizeSp,
            cellWidthDp = cellWidth.value,
            paddingDp = paddingDp.toFloat(),
            showChannel = showChannel,
            showNote = showNote,
            showVolume = showVolume,
            showEffect = showEffect,
            showInstrumentSample = showInstrumentSample
        )
        val canRenderAtEffectiveSize = estimateChannelScopeTextWidthDp(
            sp = effectiveTextSizeSp,
            paddingDp = paddingDp.toFloat(),
            showChannel = showChannel,
            showNote = showNote,
            showVolume = showVolume,
            showEffect = showEffect,
            showInstrumentSample = showInstrumentSample
        ) <= cellWidth.value
        val showText = !hideWhenOverflow || canRenderAtEffectiveSize
        if (!showText) {
            return@BoxWithConstraints
        }
        for (col in 0 until columns) {
            for (row in 0 until rows) {
                val channel = (col * rows) + row
                val content = if (channel < channels) {
                    buildChannelScopeTextFields(
                        channel = channel,
                        state = channelTextStates.getOrNull(channel),
                        instrumentNamesByIndex = instrumentNamesByIndex,
                        sampleNamesByIndex = sampleNamesByIndex,
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
                        val scale = effectiveTextSizeSp.toFloat() / 8f
                        val noteSlot = (24f * scale).dp
                        val volumeSlot = (30f * scale).dp
                        val effectSlot = (20f * scale).dp
                        val textStyle = MaterialTheme.typography.labelSmall.copy(
                            fontSize = effectiveTextSizeSp.sp,
                            lineHeight = effectiveTextSizeSp.sp,
                            fontFamily = textFontFamily,
                            shadow = if (textShadowEnabled) {
                                Shadow(
                                    color = Color.Black.copy(alpha = 0.62f),
                                    offset = Offset(0f, 1.25f),
                                    blurRadius = 2.4f
                                )
                            } else {
                                null
                            },
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
                                    color = textPalette.channel,
                                    style = textStyle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip
                                )
                                hasPrevious = true
                            }
                            if (content.note != null) {
                                if (hasPrevious) Text("•", color = textPalette.separator, style = textStyle)
                                Box(modifier = Modifier.width(noteSlot), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = content.note,
                                        color = textPalette.note,
                                        style = textStyle,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip
                                    )
                                }
                                hasPrevious = true
                            }
                            if (content.volume != null) {
                                if (hasPrevious) Text("•", color = textPalette.separator, style = textStyle)
                                Box(modifier = Modifier.width(volumeSlot), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = content.volume,
                                        color = textPalette.volume,
                                        style = textStyle,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip
                                    )
                                }
                                hasPrevious = true
                            }
                            if (content.effect != null) {
                                if (hasPrevious) Text("•", color = textPalette.separator, style = textStyle)
                                Box(modifier = Modifier.width(effectSlot), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = content.effect,
                                        color = textPalette.effect,
                                        style = textStyle,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip
                                    )
                                }
                                hasPrevious = true
                            }
                            if (content.instrumentOrSample != null) {
                                if (hasPrevious) Text("•", color = textPalette.separator, style = textStyle)
                                Text(
                                    text = content.instrumentOrSample,
                                    color = textPalette.instrumentOrSample,
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

private fun resolveChannelScopeTextFontFamily(font: VisualizationChannelScopeTextFont): FontFamily {
    return when (font) {
        VisualizationChannelScopeTextFont.System -> FontFamily.Default
        VisualizationChannelScopeTextFont.RaccoonSerif -> FontFamily(Font(R.font.raccoon_serif_base))
        VisualizationChannelScopeTextFont.RaccoonMono -> FontFamily(Font(R.font.raccoon_serif_mono))
        VisualizationChannelScopeTextFont.RetroCuteMono -> FontFamily(Font(R.font.retro_pixel_cute_mono))
        VisualizationChannelScopeTextFont.RetroThick -> FontFamily(Font(R.font.retro_pixel_thick))
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
    instrumentNamesByIndex: Map<Int, String>,
    sampleNamesByIndex: Map<Int, String>,
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
        instrumentOrSample = if (showInstrumentSample) {
            formatInstrumentOrSample(state, instrumentNamesByIndex, sampleNamesByIndex)
        } else {
            null
        }
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

private fun formatInstrumentOrSample(
    state: ChannelScopeChannelTextState?,
    instrumentNamesByIndex: Map<Int, String>,
    sampleNamesByIndex: Map<Int, String>
): String? {
    if (state == null) return null
    if (state.instrumentIndex > 0) {
        val name = instrumentNamesByIndex[state.instrumentIndex].orEmpty()
        return if (name.isNotBlank()) {
            "I#${state.instrumentIndex} $name"
        } else {
            "I#${state.instrumentIndex}"
        }
    }
    if (state.sampleIndex > 0) {
        val name = sampleNamesByIndex[state.sampleIndex].orEmpty()
        return if (name.isNotBlank()) {
            "S#${state.sampleIndex} $name"
        } else {
            "S#${state.sampleIndex}"
        }
    }
    return null
}

private fun computeAutoChannelScopeTextSizeSp(
    selectedTextSizeSp: Int,
    minimumTextSizeSp: Int,
    cellWidthDp: Float,
    paddingDp: Float,
    showChannel: Boolean,
    showNote: Boolean,
    showVolume: Boolean,
    showEffect: Boolean,
    showInstrumentSample: Boolean
): Int {
    val selected = selectedTextSizeSp.coerceIn(6, 22)
    val minimum = minimumTextSizeSp.coerceAtMost(selected).coerceAtLeast(6)
    val availableWidth = cellWidthDp.coerceAtLeast(0f)
    if (
        estimateChannelScopeTextWidthDp(
            sp = selected,
            paddingDp = paddingDp,
            showChannel = showChannel,
            showNote = showNote,
            showVolume = showVolume,
            showEffect = showEffect,
            showInstrumentSample = showInstrumentSample
        ) <= availableWidth
    ) {
        return selected
    }
    var size = selected
    while (
        size > minimum &&
            estimateChannelScopeTextWidthDp(
                sp = size,
                paddingDp = paddingDp,
                showChannel = showChannel,
                showNote = showNote,
                showVolume = showVolume,
                showEffect = showEffect,
                showInstrumentSample = showInstrumentSample
            ) > availableWidth
    ) {
        size--
    }
    return size
}

private fun estimateChannelScopeTextWidthDp(
    sp: Int,
    paddingDp: Float,
    showChannel: Boolean,
    showNote: Boolean,
    showVolume: Boolean,
    showEffect: Boolean,
    showInstrumentSample: Boolean
): Float {
    val scale = sp.toFloat() / 8f
    var fieldCount = 0
    var width = 0f
    if (showChannel) {
        width += 26f * scale
        fieldCount++
    }
    if (showNote) {
        width += 24f * scale
        fieldCount++
    }
    if (showVolume) {
        width += 30f * scale
        fieldCount++
    }
    if (showEffect) {
        width += 20f * scale
        fieldCount++
    }
    if (showInstrumentSample) {
        // Reserve a larger leading slot so autoscale triggers sooner on dense layouts.
        width += 28f * scale
        fieldCount++
    }
    val separators = (fieldCount - 1).coerceAtLeast(0)
    width += separators * (8f * scale) // Bullet glyph width estimate (conservative)
    width += separators * 3f // Row spacing estimate
    width += paddingDp * 2f
    width += 4f // safety margin
    return width
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
