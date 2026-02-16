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
    barColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (!barOverlayArtwork) {
            drawRect(color = backgroundColor)
        }
        val count = barCount.coerceIn(8, 96)
        val edgePadPx = 8.dp.toPx()
        val topHeadroomPx = 8.dp.toPx()
        val widthPx = (size.width - (edgePadPx * 2f)).coerceAtLeast(1f)
        val heightPx = (size.height - topHeadroomPx).coerceAtLeast(1f)
        val baselineY = size.height
        if (widthPx <= 0f || heightPx <= 0f || count <= 0) return@Canvas
        val gapPx = (widthPx / count) * 0.18f
        val barWidth = ((widthPx - gapPx * (count - 1)) / count).coerceAtLeast(1f)
        val radius = barRoundnessDp.dp.toPx().coerceAtMost(barWidth * 0.45f)
        val source = if (bars.isNotEmpty()) bars else FloatArray(256) { 0f }
        val usableMinIndex = 1
        val usableMaxIndex = (source.size - 1).coerceAtLeast(usableMinIndex + 1)
        val minForLog = usableMinIndex.toFloat()
        val maxForLog = usableMaxIndex.toFloat()
        val logBase = (maxForLog / minForLog).coerceAtLeast(1.001f)
        val midShiftBias = 0.80f
        for (i in 0 until count) {
            val t0 = i.toFloat() / count.toFloat()
            val t1 = (i + 1).toFloat() / count.toFloat()
            val t0Mapped = t0.pow(midShiftBias).coerceIn(0f, 1f)
            val t1Mapped = t1.pow(midShiftBias).coerceIn(0f, 1f)
            val start = (minForLog * logBase.pow(t0Mapped)).roundToInt().coerceIn(usableMinIndex, usableMaxIndex)
            val end = (minForLog * logBase.pow(t1Mapped)).roundToInt().coerceIn(start, usableMaxIndex)

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
