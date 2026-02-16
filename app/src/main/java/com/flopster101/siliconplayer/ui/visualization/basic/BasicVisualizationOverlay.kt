package com.flopster101.siliconplayer.ui.visualization.basic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.material3.MaterialTheme
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.VisualizationOscColorMode
import com.flopster101.siliconplayer.VisualizationVuAnchor
import kotlin.math.max
import kotlin.math.min

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

        VisualizationMode.Off -> Unit
    }
}

private fun deriveVuTrackColor(accent: Color): Color {
    val neutral = if (accent.luminance() > 0.56f) Color.Black else Color.White
    // Tonal track variant: tied to accent with contrast-aware neutral shift.
    return lerp(accent, neutral, 0.72f).copy(alpha = 0.62f)
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
