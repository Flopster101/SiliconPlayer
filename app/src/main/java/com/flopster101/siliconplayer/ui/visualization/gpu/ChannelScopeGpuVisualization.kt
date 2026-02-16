package com.flopster101.siliconplayer.ui.visualization.gpu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.flopster101.siliconplayer.VisualizationChannelScopeLayout
import kotlin.math.ceil

@Composable
fun ChannelScopeGpuVisualization(
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
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ChannelScopeGpuCanvasView(context).apply {
                setWillNotDraw(false)
            }
        },
        update = { view ->
            view.updateFrame(
                channelHistories = channelHistories,
                lineColorArgb = lineColor.toArgb(),
                gridColorArgb = gridColor.toArgb(),
                lineWidthPx = lineWidthPx,
                gridWidthPx = gridWidthPx,
                showVerticalGrid = showVerticalGrid,
                showCenterLine = showCenterLine,
                triggerModeNative = triggerModeNative,
                triggerIndices = triggerIndices,
                layoutStrategy = layoutStrategy
            )
        }
    )
}

private class ChannelScopeGpuCanvasView(
    context: Context
) : View(context) {
    private var channelHistories: List<FloatArray> = emptyList()
    private var triggerIndices: IntArray = IntArray(0)
    private var triggerModeNative: Int = 0
    private var showVerticalGrid: Boolean = false
    private var showCenterLine: Boolean = false
    private var layoutStrategy: VisualizationChannelScopeLayout = VisualizationChannelScopeLayout.ColumnFirst

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        strokeJoin = Paint.Join.MITER
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val waveformPath = Path()

    init {
        // Keep rendering on hardware path when available.
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun updateFrame(
        channelHistories: List<FloatArray>,
        lineColorArgb: Int,
        gridColorArgb: Int,
        lineWidthPx: Float,
        gridWidthPx: Float,
        showVerticalGrid: Boolean,
        showCenterLine: Boolean,
        triggerModeNative: Int,
        triggerIndices: IntArray,
        layoutStrategy: VisualizationChannelScopeLayout
    ) {
        this.channelHistories = channelHistories
        this.triggerIndices = triggerIndices
        this.triggerModeNative = triggerModeNative
        this.showVerticalGrid = showVerticalGrid
        this.showCenterLine = showCenterLine
        this.layoutStrategy = layoutStrategy
        linePaint.color = lineColorArgb
        gridPaint.color = gridColorArgb
        linePaint.strokeWidth = lineWidthPx.coerceAtLeast(1f)
        gridPaint.strokeWidth = gridWidthPx.coerceAtLeast(0.5f)
        borderPaint.color = (gridColorArgb and 0x00FFFFFF) or (0x73 shl 24)
        borderPaint.strokeWidth = gridPaint.strokeWidth
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val histories = channelHistories
        if (histories.isEmpty()) return
        val channels = histories.size
        val (columns, rows) = resolveGrid(channels, layoutStrategy)
        val safeColumns = columns.coerceAtLeast(1)
        val safeRows = rows.coerceAtLeast(1)
        val cellWidth = width.toFloat() / safeColumns.toFloat()
        val cellHeight = height.toFloat() / safeRows.toFloat()
        val ampScale = cellHeight * 0.48f

        for (channel in 0 until channels) {
            val col = channel / safeRows
            val row = channel % safeRows
            val left = col * cellWidth
            val top = row * cellHeight
            val right = left + cellWidth
            val bottom = top + cellHeight
            val centerY = top + (cellHeight * 0.5f)

            canvas.drawRect(left, top, right, bottom, borderPaint)
            val history = histories[channel]
            if (history.size < 2) continue

            val triggerIndex = triggerIndices.getOrNull(channel)?.coerceIn(0, history.size - 1)
                ?: (history.size / 2)
            val phaseOffset = if (triggerModeNative == 0) 0 else history.size / 2
            val startIndex = ((triggerIndex - phaseOffset) % history.size + history.size) % history.size
            val stepX = cellWidth / (history.size - 1).coerceAtLeast(1).toFloat()

            val saveCount = canvas.save()
            canvas.clipRect(left, top, right, bottom)
            if (showVerticalGrid) {
                val divisions = 4
                for (i in 0..divisions) {
                    val x = left + (cellWidth * (i.toFloat() / divisions.toFloat()))
                    canvas.drawLine(x, top, x, bottom, gridPaint)
                }
            }
            if (showCenterLine) {
                canvas.drawLine(left, centerY, right, centerY, gridPaint)
            }
            val firstSample = history[startIndex].coerceIn(-1f, 1f)
            waveformPath.reset()
            waveformPath.moveTo(left, centerY - (firstSample * ampScale))
            for (i in 1 until history.size) {
                val sample = history[(startIndex + i) % history.size].coerceIn(-1f, 1f)
                val x = left + i * stepX
                val y = centerY - (sample * ampScale)
                waveformPath.lineTo(x, y)
            }
            canvas.drawPath(waveformPath, linePaint)
            canvas.restoreToCount(saveCount)
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
            val columns = ceil(kotlin.math.sqrt(channels.toDouble())).toInt().coerceAtLeast(1)
            val rows = ceil(channels / columns.toDouble()).toInt().coerceAtLeast(1)
            columns to rows
        }
    }
}
