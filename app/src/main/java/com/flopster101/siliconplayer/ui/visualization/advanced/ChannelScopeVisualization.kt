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
        val triggerAnchorFraction = 0.5f
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
            val triggerAnchorIndex = ((history.size - 1) * triggerAnchorFraction).toInt()
                .coerceIn(0, history.size - 1)
            val triggerIndex = findTriggerIndex(history, triggerModeNative, triggerAnchorIndex)
            val phaseOffset = if (triggerModeNative == 0) 0 else triggerAnchorIndex
            val startIndex = ((triggerIndex - phaseOffset) % history.size + history.size) % history.size
            val stepX = cellWidth / (history.size - 1).coerceAtLeast(1).toFloat()

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

                for (i in 1 until history.size) {
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
            if (channels <= 4) {
                1 to channels
            } else {
                2 to ceil(channels / 2.0).toInt()
            }
        }
        VisualizationChannelScopeLayout.BalancedTwoColumn -> {
            if (channels <= 2) {
                1 to channels
            } else {
                2 to ceil(channels / 2.0).toInt()
            }
        }
    }
}

private fun findTriggerIndex(
    history: FloatArray?,
    triggerModeNative: Int,
    anchorIndex: Int
): Int {
    if (history == null || history.size < 2 || triggerModeNative == 0) return 0
    val threshold = 0.0f
    var bestIndex = -1
    var bestScore = Float.NEGATIVE_INFINITY
    for (i in 1 until history.size) {
        val prev = history[i - 1]
        val next = history[i]
        val hit = if (triggerModeNative == 1) {
            prev < threshold && next >= threshold
        } else {
            prev > threshold && next <= threshold
        }
        if (hit) {
            val slope = abs(next - prev)
            val distanceNorm = abs(i - anchorIndex).toFloat() / history.size.toFloat()
            val score = (slope * 4.0f) - distanceNorm
            if (score > bestScore) {
                bestScore = score
                bestIndex = i
            }
        }
    }
    return if (bestIndex >= 0) bestIndex else anchorIndex.coerceIn(0, history.size - 1)
}
