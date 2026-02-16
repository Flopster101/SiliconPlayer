package com.flopster101.siliconplayer.ui.visualization.gl

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.flopster101.siliconplayer.VisualizationChannelScopeLayout
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.ceil

@Composable
fun ChannelScopeGlVisualization(
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
    onFrameStats: ((fps: Int, frameMs: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var glView by remember { mutableStateOf<ChannelScopeGlSurfaceView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            ChannelScopeGlSurfaceView(context).also { view ->
                glView = view
            }
        },
        update = { view ->
            view.onFrameStats = onFrameStats
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

    DisposableEffect(lifecycleOwner, glView) {
        val view = glView
        if (view == null) {
            onDispose {}
        } else {
            view.onResume()
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> view.onResume()
                    Lifecycle.Event.ON_PAUSE -> view.onPause()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                view.onPause()
            }
        }
    }
}

private data class ChannelScopeGlFrame(
    val channelHistories: List<FloatArray>,
    val triggerIndices: IntArray,
    val triggerModeNative: Int,
    val showVerticalGrid: Boolean,
    val showCenterLine: Boolean,
    val layoutStrategy: VisualizationChannelScopeLayout,
    val lineColorArgb: Int,
    val gridColorArgb: Int,
    val lineWidthPx: Float,
    val gridWidthPx: Float
)

private class ChannelScopeGlSurfaceView(context: Context) : GLSurfaceView(context) {
    private val renderer = ChannelScopeGlRenderer(this)
    var onFrameStats: ((fps: Int, frameMs: Int) -> Unit)? = null

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        preserveEGLContextOnPause = true
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
        renderer.setFrameData(
            ChannelScopeGlFrame(
                channelHistories = channelHistories,
                triggerIndices = triggerIndices.copyOf(),
                triggerModeNative = triggerModeNative,
                showVerticalGrid = showVerticalGrid,
                showCenterLine = showCenterLine,
                layoutStrategy = layoutStrategy,
                lineColorArgb = lineColorArgb,
                gridColorArgb = gridColorArgb,
                lineWidthPx = lineWidthPx.coerceAtLeast(1f),
                gridWidthPx = gridWidthPx.coerceAtLeast(0.5f)
            )
        )
        requestRender()
    }

    fun dispatchFrameStats(fps: Int, frameMs: Int) {
        post {
            onFrameStats?.invoke(fps, frameMs)
        }
    }
}

private class ChannelScopeGlRenderer(
    private val owner: ChannelScopeGlSurfaceView
) : GLSurfaceView.Renderer {
    @Volatile
    private var frameData: ChannelScopeGlFrame? = null
    private var surfaceWidth: Int = 1
    private var surfaceHeight: Int = 1

    private var program: Int = 0
    private var positionHandle: Int = -1
    private var resolutionHandle: Int = -1
    private var colorHandle: Int = -1

    private var waveformBuffer: FloatBuffer? = null
    private var waveformVertexCount: Int = 0
    private var gridBuffer: FloatBuffer? = null
    private var gridVertexCount: Int = 0

    private var drawFrameCount: Int = 0
    private var drawWindowStartNs: Long = 0L
    private var lastDrawNs: Long = 0L
    private var latestDrawFps: Int = 0
    private var latestFrameMs: Int = 0
    private var lastHudPublishNs: Long = 0L
    private var rendererReady: Boolean = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        rendererReady = runCatching {
            program = createProgram(VERTEX_SHADER_SOURCE, FRAGMENT_SHADER_SOURCE)
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            resolutionHandle = GLES20.glGetUniformLocation(program, "uResolution")
            colorHandle = GLES20.glGetUniformLocation(program, "uColor")
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            GLES20.glDisable(GLES20.GL_CULL_FACE)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            true
        }.getOrElse {
            false
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        val nowNs = System.nanoTime()
        if (drawWindowStartNs == 0L) {
            drawWindowStartNs = nowNs
        }
        if (lastDrawNs != 0L) {
            latestFrameMs = ((nowNs - lastDrawNs) / 1_000_000L).toInt().coerceAtLeast(0)
        }
        lastDrawNs = nowNs
        drawFrameCount += 1
        val elapsedNs = nowNs - drawWindowStartNs
        if (elapsedNs >= 1_000_000_000L) {
            latestDrawFps = ((drawFrameCount.toDouble() * 1_000_000_000.0) / elapsedNs.toDouble())
                .toInt()
                .coerceAtLeast(0)
            drawFrameCount = 0
            drawWindowStartNs = nowNs
        }
        if (nowNs - lastHudPublishNs >= 350_000_000L) {
            owner.dispatchFrameStats(latestDrawFps, latestFrameMs)
            lastHudPublishNs = nowNs
        }

        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (!rendererReady || program == 0) return

        val data = frameData ?: return
        val histories = data.channelHistories
        if (histories.isEmpty()) return

        buildGeometry(data)

        GLES20.glUseProgram(program)
        GLES20.glUniform2f(resolutionHandle, surfaceWidth.toFloat(), surfaceHeight.toFloat())
        drawLines(
            buffer = gridBuffer,
            vertexCount = gridVertexCount,
            colorArgb = data.gridColorArgb,
            widthPx = data.gridWidthPx
        )
        drawLines(
            buffer = waveformBuffer,
            vertexCount = waveformVertexCount,
            colorArgb = data.lineColorArgb,
            widthPx = data.lineWidthPx
        )
    }

    fun setFrameData(data: ChannelScopeGlFrame) {
        frameData = data
    }

    private fun buildGeometry(data: ChannelScopeGlFrame) {
        val histories = data.channelHistories
        val channels = histories.size
        if (channels <= 0) {
            waveformVertexCount = 0
            gridVertexCount = 0
            return
        }
        val (columns, rows) = resolveGrid(channels, data.layoutStrategy)
        val safeColumns = columns.coerceAtLeast(1)
        val safeRows = rows.coerceAtLeast(1)
        val cellWidth = surfaceWidth.toFloat() / safeColumns.toFloat()
        val cellHeight = surfaceHeight.toFloat() / safeRows.toFloat()
        val ampScale = cellHeight * 0.48f

        val waveformBuilder = FloatLineBuilder(channels * 1024)
        val gridBuilder = FloatLineBuilder(channels * 64)

        for (channel in 0 until channels) {
            val col = channel / safeRows
            val row = channel % safeRows
            val left = col * cellWidth
            val top = row * cellHeight
            val right = left + cellWidth
            val bottom = top + cellHeight
            val centerY = top + (cellHeight * 0.5f)

            // Cell border
            gridBuilder.addLine(left, top, right, top)
            gridBuilder.addLine(right, top, right, bottom)
            gridBuilder.addLine(right, bottom, left, bottom)
            gridBuilder.addLine(left, bottom, left, top)

            if (data.showVerticalGrid) {
                val divisions = 4
                for (i in 0..divisions) {
                    val x = left + (cellWidth * (i.toFloat() / divisions.toFloat()))
                    gridBuilder.addLine(x, top, x, bottom)
                }
            }
            if (data.showCenterLine) {
                gridBuilder.addLine(left, centerY, right, centerY)
            }

            val history = histories[channel]
            if (history.size < 2) continue
            val triggerIndex = data.triggerIndices.getOrNull(channel)?.coerceIn(0, history.size - 1)
                ?: (history.size / 2)
            val phaseOffset = if (data.triggerModeNative == 0) 0 else history.size / 2
            val startIndex = ((triggerIndex - phaseOffset) % history.size + history.size) % history.size
            val stepX = cellWidth / (history.size - 1).coerceAtLeast(1).toFloat()

            var prevSample = history[startIndex].coerceIn(-1f, 1f)
            var prevX = left
            var prevY = centerY - (prevSample * ampScale)
            for (i in 1 until history.size) {
                val sample = history[(startIndex + i) % history.size].coerceIn(-1f, 1f)
                val x = left + i * stepX
                val y = centerY - (sample * ampScale)
                waveformBuilder.addLine(prevX, prevY, x, y)
                prevSample = sample
                prevX = x
                prevY = y
            }
        }

        val waveData = waveformBuilder.toFloatArray()
        val gridData = gridBuilder.toFloatArray()
        waveformVertexCount = waveData.size / 2
        gridVertexCount = gridData.size / 2
        waveformBuffer = ensureBuffer(waveformBuffer, waveData.size).apply {
            clear()
            put(waveData)
            position(0)
        }
        gridBuffer = ensureBuffer(gridBuffer, gridData.size).apply {
            clear()
            put(gridData)
            position(0)
        }
    }

    private fun ensureBuffer(existing: FloatBuffer?, requiredFloats: Int): FloatBuffer {
        if (requiredFloats <= 0) {
            return existing ?: ByteBuffer
                .allocateDirect(4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        }
        if (existing != null && existing.capacity() >= requiredFloats) return existing
        val buffer = ByteBuffer
            .allocateDirect(requiredFloats * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        return buffer
    }

    private fun drawLines(
        buffer: FloatBuffer?,
        vertexCount: Int,
        colorArgb: Int,
        widthPx: Float
    ) {
        if (buffer == null || vertexCount <= 0) return
        val a = ((colorArgb ushr 24) and 0xFF) / 255f
        val r = ((colorArgb ushr 16) and 0xFF) / 255f
        val g = ((colorArgb ushr 8) and 0xFF) / 255f
        val b = (colorArgb and 0xFF) / 255f
        GLES20.glUniform4f(colorHandle, r, g, b, a)
        GLES20.glLineWidth(widthPx.coerceAtLeast(1f))
        buffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, buffer)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, vertexCount)
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val programId = GLES20.glCreateProgram()
        GLES20.glAttachShader(programId, vertexShader)
        GLES20.glAttachShader(programId, fragmentShader)
        GLES20.glLinkProgram(programId)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val message = GLES20.glGetProgramInfoLog(programId)
            GLES20.glDeleteProgram(programId)
            throw IllegalStateException("OpenGL program link failed: $message")
        }
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return programId
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val message = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("OpenGL shader compile failed: $message")
        }
        return shader
    }

    companion object {
        private const val VERTEX_SHADER_SOURCE = """
            attribute vec2 aPosition;
            uniform vec2 uResolution;
            void main() {
                vec2 zeroToOne = aPosition / uResolution;
                vec2 zeroToTwo = zeroToOne * 2.0;
                vec2 clipSpace = zeroToTwo - 1.0;
                gl_Position = vec4(clipSpace.x, -clipSpace.y, 0.0, 1.0);
            }
        """

        private const val FRAGMENT_SHADER_SOURCE = """
            precision mediump float;
            uniform vec4 uColor;
            void main() {
                gl_FragColor = uColor;
            }
        """
    }
}

private class FloatLineBuilder(initialFloatCapacity: Int) {
    private var data = FloatArray(initialFloatCapacity.coerceAtLeast(16))
    private var size = 0

    fun addLine(x0: Float, y0: Float, x1: Float, y1: Float) {
        ensureCapacity(4)
        data[size++] = x0
        data[size++] = y0
        data[size++] = x1
        data[size++] = y1
    }

    private fun ensureCapacity(extra: Int) {
        val needed = size + extra
        if (needed <= data.size) return
        var newSize = data.size
        while (newSize < needed) {
            newSize *= 2
        }
        data = data.copyOf(newSize)
    }

    fun toFloatArray(): FloatArray = data.copyOf(size)
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
