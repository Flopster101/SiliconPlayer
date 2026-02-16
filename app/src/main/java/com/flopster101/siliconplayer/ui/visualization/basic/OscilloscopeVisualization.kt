package com.flopster101.siliconplayer.ui.visualization.basic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

@Composable
fun OscilloscopeVisualization(
    waveformLeft: FloatArray,
    waveformRight: FloatArray,
    channelCount: Int,
    oscStereo: Boolean,
    oscColor: Color,
    gridColor: Color,
    lineWidthPx: Float,
    gridWidthPx: Float,
    showVerticalGrid: Boolean,
    showCenterLine: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val stereo = oscStereo && channelCount > 1
        val left = if (waveformLeft.isNotEmpty()) waveformLeft else FloatArray(256)
        val right = if (waveformRight.isNotEmpty()) waveformRight else left
        if (left.isEmpty()) return@Canvas

        val half = size.height / 2f
        val centerLeft = if (stereo) size.height * 0.30f else half
        val centerRight = if (stereo) size.height * 0.72f else half
        val ampScale = if (stereo) size.height * 0.20f else size.height * 0.34f
        val stepX = size.width / (left.size - 1).coerceAtLeast(1)
        val scopeLineWidth = lineWidthPx.coerceAtLeast(1f)
        val scopeGridWidth = gridWidthPx.coerceAtLeast(0.5f)

        // Vertical grid (optional)
        if (showVerticalGrid) {
            val verticalDivisions = 8
            for (i in 0..verticalDivisions) {
                val x = size.width * (i.toFloat() / verticalDivisions.toFloat())
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = scopeGridWidth
                )
            }
        }
        // Keep the stereo inter-channel separator as part of the grid.
        if (stereo) {
            drawLine(
                color = gridColor.copy(alpha = (gridColor.alpha * 0.85f).coerceIn(0f, 1f)),
                start = Offset(0f, half),
                end = Offset(size.width, half),
                strokeWidth = scopeGridWidth
            )
        }

        // Optional waveform centerline(s).
        if (showCenterLine) {
            if (stereo) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, centerLeft),
                    end = Offset(size.width, centerLeft),
                    strokeWidth = scopeGridWidth
                )
                drawLine(
                    color = gridColor,
                    start = Offset(0f, centerRight),
                    end = Offset(size.width, centerRight),
                    strokeWidth = scopeGridWidth
                )
            } else {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, half),
                    end = Offset(size.width, half),
                    strokeWidth = scopeGridWidth
                )
            }
        }

        for (i in 1 until left.size) {
            val x0 = (i - 1) * stepX
            val x1 = i * stepX
            val y0Left = centerLeft - (left[i - 1].coerceIn(-1f, 1f) * ampScale)
            val y1Left = centerLeft - (left[i].coerceIn(-1f, 1f) * ampScale)
            drawLine(
                color = oscColor,
                start = Offset(x0, y0Left),
                end = Offset(x1, y1Left),
                strokeWidth = scopeLineWidth
            )
            if (stereo) {
                val y0Right = centerRight - (right[i - 1].coerceIn(-1f, 1f) * ampScale)
                val y1Right = centerRight - (right[i].coerceIn(-1f, 1f) * ampScale)
                drawLine(
                    color = oscColor.copy(alpha = 0.78f),
                    start = Offset(x0, y0Right),
                    end = Offset(x1, y1Right),
                    strokeWidth = scopeLineWidth
                )
            }
        }
    }
}
