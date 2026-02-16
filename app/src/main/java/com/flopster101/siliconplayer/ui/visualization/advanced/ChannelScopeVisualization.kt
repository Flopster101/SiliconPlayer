package com.flopster101.siliconplayer.ui.visualization.advanced

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import com.flopster101.siliconplayer.VisualizationChannelScopeLayout
import kotlin.math.abs
import kotlin.math.ceil

@Composable
fun ChannelScopeVisualization(
    channelHistories: List<FloatArray>,
    lineColor: Color,
    gridColor: Color,
    lineWidthPx: Float,
    gridWidthPx: Float,
    showVerticalGrid: Boolean,
    showCenterLine: Boolean,
    triggerModeNative: Int,
    triggerIndices: IntArray,
    layoutStrategy: VisualizationChannelScopeLayout,
    modifier: Modifier = Modifier
) {
    if (channelHistories.isEmpty()) return
    Canvas(modifier = modifier.fillMaxSize()) {
        val channels = channelHistories.size
        val (columns, rows) = resolveGrid(channels, layoutStrategy)
        val cellWidth = size.width / columns.toFloat().coerceAtLeast(1f)
        val cellHeight = size.height / rows.toFloat().coerceAtLeast(1f)
        val scopeLineWidth = lineWidthPx.coerceAtLeast(1f)
        val scopeGridWidth = gridWidthPx.coerceAtLeast(0.5f)
        val channelBorderColor = gridColor.copy(alpha = 0.45f)
        for (channel in 0 until channels) {
            val col = channel / rows
            val row = channel % rows
            val left = col * cellWidth
            val top = row * cellHeight
            val centerY = top + (cellHeight * 0.5f)
            val ampScale = cellHeight * 0.48f
            val history = channelHistories[channel]
            drawRect(
                color = channelBorderColor,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight),
                style = Stroke(width = scopeGridWidth)
            )
            if (history.size < 2) {
                continue
            }
            val triggerIndex = triggerIndices
                .getOrNull(channel)
                ?.coerceIn(0, history.size - 1)
                ?: findTriggerIndex(history, triggerModeNative)
            val phaseOffset = if (triggerModeNative == 0) 0 else history.size / 2
            val startIndexRaw = ((triggerIndex - phaseOffset) % history.size + history.size) % history.size
            val edgeTrim = ((history.size * 0.04f).toInt()).coerceIn(0, ((history.size - 2) / 2).coerceAtLeast(0))
            val visibleSamples = history.size - (edgeTrim * 2)
            if (visibleSamples < 2) {
                continue
            }
            val startIndex = (startIndexRaw + edgeTrim) % history.size
            val stepX = cellWidth / (visibleSamples - 1).coerceAtLeast(1).toFloat()

            clipRect(left = left, top = top, right = left + cellWidth, bottom = top + cellHeight) {
                if (showVerticalGrid) {
                    val verticalDivisions = 4
                    for (i in 0..verticalDivisions) {
                        val x = left + (cellWidth * (i.toFloat() / verticalDivisions.toFloat()))
                        drawLine(
                            color = gridColor,
                            start = Offset(x, top),
                            end = Offset(x, top + cellHeight),
                            strokeWidth = scopeGridWidth
                        )
                    }
                }
                if (showCenterLine) {
                    drawLine(
                        color = gridColor,
                        start = Offset(left, centerY),
                        end = Offset(left + cellWidth, centerY),
                        strokeWidth = scopeGridWidth
                    )
                }

                for (i in 1 until visibleSamples) {
                    val samplePrev = history[(startIndex + i - 1) % history.size].coerceIn(-1f, 1f)
                    val sampleNext = history[(startIndex + i) % history.size].coerceIn(-1f, 1f)
                    val x0 = left + (i - 1) * stepX
                    val x1 = left + i * stepX
                    val y0 = centerY - (samplePrev * ampScale)
                    val y1 = centerY - (sampleNext * ampScale)
                    drawLine(
                        color = lineColor,
                        start = Offset(x0, y0),
                        end = Offset(x1, y1),
                        strokeWidth = scopeLineWidth
                    )
                }
            }
        }
    }
}

private fun resolveGrid(
    channels: Int,
    strategy: VisualizationChannelScopeLayout
): Pair<Int, Int> {
    if (channels <= 1) return 1 to 1
    return when (strategy) {
        VisualizationChannelScopeLayout.ColumnFirst -> {
            // Grow columns with channel count, but keep growth gradual on medium-high channel counts.
            // Examples with this curve: 4ch -> 1x4, 6ch -> 2x3, 24ch -> 4x6.
            val targetRowsPerColumn = 7
            val columns = if (channels <= 4) {
                1
            } else {
                ceil(channels / targetRowsPerColumn.toDouble()).toInt().coerceAtLeast(2)
            }
            val rows = ceil(channels / columns.toDouble()).toInt().coerceAtLeast(1)
            columns to rows
        }
        VisualizationChannelScopeLayout.BalancedTwoColumn -> {
            // Balanced layout trends toward a square-ish grid as channels increase.
            val columns = ceil(kotlin.math.sqrt(channels.toDouble())).toInt().coerceAtLeast(1)
            val rows = ceil(channels / columns.toDouble()).toInt().coerceAtLeast(1)
            columns to rows
        }
    }
}

private fun findTriggerIndex(history: FloatArray?, triggerModeNative: Int): Int {
    if (history == null || history.size < 2 || triggerModeNative == 0) return 0
    val threshold = 0.0f
    val centerIndex = history.size / 2
    var bestIndex = -1
    var bestDistance = Int.MAX_VALUE
    for (i in 1 until history.size) {
        val prev = history[i - 1]
        val next = history[i]
        val hit = if (triggerModeNative == 1) {
            prev < threshold && next >= threshold
        } else {
            prev > threshold && next <= threshold
        }
        if (hit) {
            val distance = abs(i - centerIndex)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = i
            }
        }
    }
    return if (bestIndex >= 0) bestIndex else 0
}
