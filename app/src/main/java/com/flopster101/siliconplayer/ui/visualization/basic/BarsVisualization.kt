package com.flopster101.siliconplayer.ui.visualization.basic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun BarsVisualization(
    bars: FloatArray,
    barCount: Int,
    barRoundnessDp: Int,
    barOverlayArtwork: Boolean,
    barFrequencyGridEnabled: Boolean,
    sampleRateHz: Int,
    barColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (!barOverlayArtwork) {
            drawRect(color = backgroundColor)
        }
        val count = barCount.coerceIn(8, 96)
        val edgePadPx = 0f
        val topHeadroomPx = 8.dp.toPx()
        val widthPx = (size.width - (edgePadPx * 2f)).coerceAtLeast(1f)
        val heightPx = (size.height - topHeadroomPx).coerceAtLeast(1f)
        val baselineY = size.height
        if (widthPx <= 0f || heightPx <= 0f || count <= 0) return@Canvas
        val gapPx = (widthPx / count) * 0.18f
        val barWidth = ((widthPx - gapPx * (count - 1)) / count).coerceAtLeast(1f)
        val radius = barRoundnessDp.dp.toPx().coerceAtMost(barWidth * 0.45f)
        val source = if (bars.isNotEmpty()) bars else FloatArray(256) { 0f }
        val mapping = resolveBarsFrequencyMapping(
            sampleRateHz = sampleRateHz,
            sourceSize = source.size
        )
        val usableMinIndex = 0
        val usableMaxIndex = (source.size - 1).coerceAtLeast(usableMinIndex + 1)
        val midShiftBias = BARS_FREQUENCY_MID_SHIFT_BIAS

        if (barFrequencyGridEnabled) {
            drawBarsFrequencyGuide(
                edgePadPx = edgePadPx,
                widthPx = widthPx,
                heightPx = heightPx,
                baselineY = baselineY,
                sampleRateHz = sampleRateHz,
                sourceSize = source.size,
                midShiftBias = midShiftBias,
                lineColor = barColor
            )
        }

        for (i in 0 until count) {
            val t0 = i.toFloat() / count.toFloat()
            val t1 = (i + 1).toFloat() / count.toFloat()
            val t0Mapped = t0.pow(midShiftBias).coerceIn(0f, 1f)
            val t1Mapped = t1.pow(midShiftBias).coerceIn(0f, 1f)
            val startFrequencyHz = mapping.logPositionToFrequencyHz(t0Mapped)
            val endFrequencyHz = mapping.logPositionToFrequencyHz(t1Mapped)
            val start = mapping.frequencyHzToSourceIndex(startFrequencyHz).roundToInt()
                .coerceIn(usableMinIndex, usableMaxIndex)
            val end = mapping.frequencyHzToSourceIndex(endFrequencyHz).roundToInt()
                .coerceIn(start, usableMaxIndex)

            var sum = 0f
            var bandSumSq = 0.0
            var bandCount = 0
            for (idx in start..end) {
                val v = source[idx].coerceAtLeast(0f)
                sum += v
                bandSumSq += (v * v).toDouble()
                bandCount += 1
            }
            val mean = if (bandCount > 0) sum / bandCount.toFloat() else 0f
            val rms = if (bandCount > 0) kotlin.math.sqrt(bandSumSq / bandCount).toFloat() else 0f
            val combined = (rms * 0.9f) + (mean * 0.1f)
            val level = combined.coerceIn(0f, 1f)
            val h = level * heightPx
            val x = edgePadPx + (i * (barWidth + gapPx))
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, baselineY - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(radius, radius)
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBarsFrequencyGuide(
    edgePadPx: Float,
    widthPx: Float,
    heightPx: Float,
    baselineY: Float,
    sampleRateHz: Int,
    sourceSize: Int,
    midShiftBias: Float,
    lineColor: Color
) {
    if (widthPx <= 0f || heightPx <= 0f || sourceSize < 2) return

    val horizontalMinorColor = lineColor.copy(alpha = (lineColor.alpha * 0.14f).coerceIn(0f, 1f))
    val horizontalMajorColor = lineColor.copy(alpha = (lineColor.alpha * 0.20f).coerceIn(0f, 1f))
    val verticalMinorColor = lineColor.copy(alpha = (lineColor.alpha * 0.18f).coerceIn(0f, 1f))
    val verticalMajorColor = lineColor.copy(alpha = (lineColor.alpha * 0.28f).coerceIn(0f, 1f))
    val lineWidthPx = 1.dp.toPx().coerceAtLeast(1f)

    listOf(0.25f, 0.5f, 0.75f, 1f).forEach { level ->
        val y = baselineY - (heightPx * level)
        drawLine(
            color = if (level == 1f) horizontalMajorColor else horizontalMinorColor,
            start = Offset(edgePadPx, y),
            end = Offset(edgePadPx + widthPx, y),
            strokeWidth = lineWidthPx
        )
    }

    val ticks = computeBarsFrequencyGuideTicks(
        sampleRateHz = sampleRateHz,
        sourceSize = sourceSize,
        midShiftBias = midShiftBias,
        minimumSpacingFraction = 0f
    )

    ticks.forEach { tick ->
        val x = edgePadPx + (tick.xFraction * widthPx)
        drawLine(
            color = if (tick.isMajor) verticalMajorColor else verticalMinorColor,
            start = Offset(x, baselineY - heightPx),
            end = Offset(x, baselineY),
            strokeWidth = lineWidthPx
        )
    }
}
