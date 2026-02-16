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

        for (i in 1 until left.size) {
            val x0 = (i - 1) * stepX
            val x1 = i * stepX
            val y0Left = centerLeft - (left[i - 1].coerceIn(-1f, 1f) * ampScale)
            val y1Left = centerLeft - (left[i].coerceIn(-1f, 1f) * ampScale)
            drawLine(
                color = oscColor,
                start = Offset(x0, y0Left),
                end = Offset(x1, y1Left),
                strokeWidth = 2f
            )
            if (stereo) {
                val y0Right = centerRight - (right[i - 1].coerceIn(-1f, 1f) * ampScale)
                val y1Right = centerRight - (right[i].coerceIn(-1f, 1f) * ampScale)
                drawLine(
                    color = oscColor.copy(alpha = 0.78f),
                    start = Offset(x0, y0Right),
                    end = Offset(x1, y1Right),
                    strokeWidth = 2f
                )
            }
        }
        if (stereo) {
            drawLine(
                color = oscColor.copy(alpha = 0.18f),
                start = Offset(0f, half),
                end = Offset(size.width, half),
                strokeWidth = 1f
            )
        }
    }
}
