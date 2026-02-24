package com.flopster101.siliconplayer.ui.visualization.basic

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

@Composable
internal fun BarsFrequencyGridLabelOverlay(
    sampleRateHz: Int,
    sourceSize: Int,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val edgePadPx = with(density) { 8.dp.toPx() }
    val minGapPx = with(density) { 3.dp.toPx() }
    val labelPadPx = with(density) { 2.dp.toPx() }
    val labelTopPx = with(density) { 3.dp.toPx() }
    val labelRadiusPx = with(density) { 4.dp.toPx() }
    val textSizePx = with(density) { 9.sp.toPx() }
    val baselineOffsetPx = with(density) { 8.dp.toPx() }
    val rowGapPx = with(density) { 2.dp.toPx() }
    val labelBgColor = Color.Black.copy(alpha = 0.30f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val widthPx = (size.width - (edgePadPx * 2f)).coerceAtLeast(1f)
        if (widthPx <= 1f) return@Canvas

        val ticks = computeBarsFrequencyGuideTicks(
            sampleRateHz = sampleRateHz,
            sourceSize = max(2, sourceSize),
            midShiftBias = 0.80f,
            minimumSpacingFraction = 0f
        )
        if (ticks.isEmpty()) return@Canvas

        drawIntoCanvas { canvas ->
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor.copy(alpha = 0.92f).toArgb()
                textSize = textSizePx
                style = Paint.Style.FILL
                setShadowLayer(2f, 0f, 1f, Color.Black.copy(alpha = 0.48f).toArgb())
            }
            val rowCount = 2
            val lastRightByRow = FloatArray(rowCount) { Float.NEGATIVE_INFINITY }
            val boxHeight = baselineOffsetPx + labelPadPx

            ticks.forEach { tick ->
                val label = formatBarsFrequencyLabel(tick.frequencyHz)
                val textWidth = textPaint.measureText(label)
                if (textWidth <= 0f) return@forEach
                val boxWidth = textWidth + (labelPadPx * 2f).coerceAtLeast(1f)
                val idealLeft = edgePadPx + (tick.xFraction * widthPx) - (boxWidth * 0.5f)
                val maxLeft = (edgePadPx + widthPx - boxWidth).coerceAtLeast(edgePadPx)
                val anchoredLeft = idealLeft.coerceIn(edgePadPx, maxLeft)
                var placed = false
                for (row in 0 until rowCount) {
                    val shiftedLeft = max(anchoredLeft, lastRightByRow[row] + minGapPx)
                    val left = shiftedLeft.coerceAtMost(maxLeft)
                    if (left < lastRightByRow[row] + minGapPx) continue
                    val right = left + boxWidth
                    lastRightByRow[row] = right
                    val top = labelTopPx + (row * (boxHeight + rowGapPx))
                    val baseline = top + baselineOffsetPx
                    drawRoundRect(
                        color = labelBgColor,
                        topLeft = Offset(left, top),
                        size = Size(boxWidth, boxHeight),
                        cornerRadius = CornerRadius(labelRadiusPx, labelRadiusPx)
                    )
                    canvas.nativeCanvas.drawText(label, left + labelPadPx, baseline, textPaint)
                    placed = true
                    break
                }
                if (!placed) return@forEach
            }
        }
    }
}
