package com.flopster101.siliconplayer.ui.visualization.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.view.Surface
import android.view.TextureView
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.sqrt

@Composable
fun ChannelScopeGlTextureVisualization(
    channelHistories: List<FloatArray>,
    lineColor: Color,
    gridColor: Color,
    lineWidthPx: Float,
    gridWidthPx: Float,
    showVerticalGrid: Boolean,
    showCenterLine: Boolean,
    triggerModeNative: Int,
    triggerIndices: IntArray,
    layoutStrategy: com.flopster101.siliconplayer.VisualizationChannelScopeLayout,
    outerCornerRadiusPx: Float = 0f,
    onFrameStats: ((fps: Int, frameMs: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var glView by remember { mutableStateOf<ChannelScopeGlTextureView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            ChannelScopeGlTextureView(context).also { view ->
                glView = view
            }
        },
        update = { view ->
            view.onFrameStats = onFrameStats
            view.updateFrame(
                ChannelScopeGlTextureFrame(
                    channelHistories = channelHistories,
                    triggerIndices = triggerIndices,
                    triggerModeNative = triggerModeNative,
                    showVerticalGrid = showVerticalGrid,
                    showCenterLine = showCenterLine,
                    layoutStrategy = layoutStrategy,
                    outerCornerRadiusPx = outerCornerRadiusPx,
                    lineColorArgb = lineColor.toArgb(),
                    gridColorArgb = gridColor.toArgb(),
                    lineWidthPx = lineWidthPx.coerceAtLeast(1f),
                    gridWidthPx = gridWidthPx.coerceAtLeast(0.5f)
                )
            )
        }
    )

    DisposableEffect(lifecycleOwner, glView) {
        val view = glView
        if (view == null) {
            onDispose {}
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> view.setLifecyclePaused(false)
                    Lifecycle.Event.ON_PAUSE -> view.setLifecyclePaused(true)
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                view.setLifecyclePaused(true)
                view.shutdown()
            }
        }
    }
}

private data class ChannelScopeGlTextureFrame(
    val channelHistories: List<FloatArray>,
    val triggerIndices: IntArray,
    val triggerModeNative: Int,
    val showVerticalGrid: Boolean,
    val showCenterLine: Boolean,
    val layoutStrategy: com.flopster101.siliconplayer.VisualizationChannelScopeLayout,
    val outerCornerRadiusPx: Float,
    val lineColorArgb: Int,
    val gridColorArgb: Int,
    val lineWidthPx: Float,
    val gridWidthPx: Float
)

private class ChannelScopeGlTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var renderThread: ChannelScopeTextureRenderThread? = null
    private var latestFrame: ChannelScopeGlTextureFrame? = null
    private var lifecyclePaused: Boolean = false
    var onFrameStats: ((fps: Int, frameMs: Int) -> Unit)? = null

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    fun updateFrame(frame: ChannelScopeGlTextureFrame) {
        latestFrame = frame
        renderThread?.setFrameData(frame)
    }

    fun setLifecyclePaused(paused: Boolean) {
        lifecyclePaused = paused
        if (paused) {
            stopRenderThread()
        } else if (isAvailable) {
            startRenderThread(surfaceTexture, width, height)
        }
    }

    fun shutdown() {
        stopRenderThread()
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (!lifecyclePaused) {
            startRenderThread(surface, width, height)
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        renderThread?.setSurfaceSize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        stopRenderThread()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    private fun startRenderThread(surfaceTexture: SurfaceTexture?, width: Int, height: Int) {
        if (surfaceTexture == null) return
        stopRenderThread()
        val surface = Surface(surfaceTexture)
        val thread = ChannelScopeTextureRenderThread(
            outputSurface = surface,
            initialWidth = width.coerceAtLeast(1),
            initialHeight = height.coerceAtLeast(1),
            onFrameStats = { fps, frameMs ->
                post { onFrameStats?.invoke(fps, frameMs) }
            }
        )
        renderThread = thread
        thread.start()
        latestFrame?.let { thread.setFrameData(it) }
    }

    private fun stopRenderThread() {
        val thread = renderThread ?: return
        renderThread = null
        thread.requestStop()
        runCatching { thread.join(350L) }
    }

    override fun onDetachedFromWindow() {
        shutdown()
        super.onDetachedFromWindow()
    }
}

private class ChannelScopeTextureRenderThread(
    private val outputSurface: Surface,
    initialWidth: Int,
    initialHeight: Int,
    private val onFrameStats: (fps: Int, frameMs: Int) -> Unit
) : Thread("ChannelScopeGlTextureRenderThread") {
    private val lock = Object()
    private var running = true
    private var frameData: ChannelScopeGlTextureFrame? = null
    private var frameSequence: Long = 0L
    private var renderedFrameSequence: Long = -1L
    private var surfaceWidth = initialWidth
    private var surfaceHeight = initialHeight
    private var surfaceSizeChanged = true

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private val coreRenderer = ChannelScopeGlCoreRenderer()
    private data class LoopState(
        val frame: ChannelScopeGlTextureFrame?,
        val frameSequence: Long,
        val width: Int,
        val height: Int,
        val surfaceSizeChanged: Boolean,
        val shouldStop: Boolean
    )

    override fun run() {
        if (!initEgl()) {
            releaseEgl()
            outputSurface.release()
            return
        }
        try {
            while (true) {
                val state = synchronized(lock) {
                    while (
                        running &&
                        !surfaceSizeChanged &&
                        (frameData == null || frameSequence == renderedFrameSequence)
                    ) {
                        lock.wait()
                    }
                    if (!running) {
                        LoopState(
                            frame = null,
                            frameSequence = frameSequence,
                            width = 0,
                            height = 0,
                            surfaceSizeChanged = false,
                            shouldStop = true
                        )
                    } else {
                        LoopState(
                            frame = frameData,
                            frameSequence = frameSequence,
                            width = surfaceWidth,
                            height = surfaceHeight,
                            surfaceSizeChanged = surfaceSizeChanged,
                            shouldStop = false
                        ).also {
                            surfaceSizeChanged = false
                        }
                    }
                }
                if (state.shouldStop) break
                val frame = state.frame ?: continue
                if (state.surfaceSizeChanged) {
                    coreRenderer.onSurfaceChanged(state.width, state.height)
                }
                coreRenderer.drawFrame(frame)
                EGL14.eglSwapBuffers(eglDisplay, eglSurface)
                synchronized(lock) {
                    renderedFrameSequence = state.frameSequence
                }
            }
        } finally {
            releaseEgl()
            outputSurface.release()
        }
    }

    fun setFrameData(frame: ChannelScopeGlTextureFrame) {
        synchronized(lock) {
            frameData = frame
            frameSequence += 1L
            lock.notifyAll()
        }
    }

    fun setSurfaceSize(width: Int, height: Int) {
        synchronized(lock) {
            surfaceWidth = width.coerceAtLeast(1)
            surfaceHeight = height.coerceAtLeast(1)
            surfaceSizeChanged = true
            lock.notifyAll()
        }
    }

    fun requestStop() {
        synchronized(lock) {
            running = false
            lock.notifyAll()
        }
    }

    private fun initEgl(): Boolean {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) return false
        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) return false

        val configAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)) {
            return false
        }
        val eglConfig = configs[0] ?: return false

        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) return false

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, outputSurface, surfaceAttribs, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) return false

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) return false
        EGL14.eglSwapInterval(eglDisplay, 1)
        coreRenderer.onSurfaceCreated(onFrameStats)
        coreRenderer.onSurfaceChanged(surfaceWidth, surfaceHeight)
        return true
    }

    private fun releaseEgl() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                eglDisplay,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
        }
        if (eglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
        }
        if (eglContext != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglDestroyContext(eglDisplay, eglContext)
        }
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }
}

private class ChannelScopeGlCoreRenderer {
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
    private val waveformBuilder = TextureFloatLineBuilder(16_384)
    private val gridBuilder = TextureFloatLineBuilder(4_096)
    private var rendererReady: Boolean = false
    private var frameStatsCallback: ((fps: Int, frameMs: Int) -> Unit)? = null
    private var drawFrameCount: Int = 0
    private var drawWindowStartNs: Long = 0L
    private var lastDrawNs: Long = 0L
    private var latestDrawFps: Int = 0
    private var latestFrameMs: Int = 0
    private var lastHudPublishNs: Long = 0L

    fun onSurfaceCreated(onFrameStats: ((fps: Int, frameMs: Int) -> Unit)?) {
        frameStatsCallback = onFrameStats
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
        }.getOrElse { false }
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
    }

    fun drawFrame(frame: ChannelScopeGlTextureFrame?) {
        val nowNs = System.nanoTime()
        if (drawWindowStartNs == 0L) drawWindowStartNs = nowNs
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
            frameStatsCallback?.invoke(latestDrawFps, latestFrameMs)
            lastHudPublishNs = nowNs
        }

        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (!rendererReady || program == 0 || frame == null) return
        if (frame.channelHistories.isEmpty()) return

        buildGeometry(frame)

        GLES20.glUseProgram(program)
        GLES20.glUniform2f(resolutionHandle, surfaceWidth.toFloat(), surfaceHeight.toFloat())
        drawLines(
            buffer = gridBuffer,
            vertexCount = gridVertexCount,
            colorArgb = frame.gridColorArgb,
            widthPx = frame.gridWidthPx
        )
        drawLines(
            buffer = waveformBuffer,
            vertexCount = waveformVertexCount,
            colorArgb = frame.lineColorArgb,
            widthPx = frame.lineWidthPx
        )
    }

    private fun buildGeometry(frame: ChannelScopeGlTextureFrame) {
        val histories = frame.channelHistories
        val channels = histories.size
        if (channels <= 0) {
            waveformVertexCount = 0
            gridVertexCount = 0
            return
        }

        val (columns, rows) = resolveGridTexture(channels, frame.layoutStrategy)
        val safeColumns = columns.coerceAtLeast(1)
        val safeRows = rows.coerceAtLeast(1)
        val cellWidth = surfaceWidth.toFloat() / safeColumns.toFloat()
        val cellHeight = surfaceHeight.toFloat() / safeRows.toFloat()
        val ampScale = cellHeight * 0.48f
        waveformBuilder.reset()
        gridBuilder.reset()

        for (col in 1 until safeColumns) {
            val x = col * cellWidth
            gridBuilder.addLine(x, 0f, x, surfaceHeight.toFloat())
        }
        for (row in 1 until safeRows) {
            val y = row * cellHeight
            gridBuilder.addLine(0f, y, surfaceWidth.toFloat(), y)
        }
        val borderInset = frame.gridWidthPx * 0.5f
        addRoundedRectBorder(
            builder = gridBuilder,
            left = borderInset,
            top = borderInset,
            right = surfaceWidth.toFloat() - borderInset,
            bottom = surfaceHeight.toFloat() - borderInset,
            radius = (frame.outerCornerRadiusPx - borderInset).coerceAtLeast(0f)
        )

        for (channel in 0 until channels) {
            val col = channel / safeRows
            val row = channel % safeRows
            val left = col * cellWidth
            val top = row * cellHeight
            val right = left + cellWidth
            val bottom = top + cellHeight
            val centerY = top + (cellHeight * 0.5f)

            if (frame.showVerticalGrid) {
                val divisions = 4
                for (i in 0..divisions) {
                    val x = left + (cellWidth * (i.toFloat() / divisions.toFloat()))
                    gridBuilder.addLine(x, top, x, bottom)
                }
            }
            if (frame.showCenterLine) {
                gridBuilder.addLine(left, centerY, right, centerY)
            }

            val history = histories[channel]
            if (history.size < 2) continue
            val triggerIndex = frame.triggerIndices.getOrNull(channel)?.coerceIn(0, history.size - 1)
                ?: (history.size / 2)
            val phaseOffset = if (frame.triggerModeNative == 0) 0 else history.size / 2
            val startIndexRaw = ((triggerIndex - phaseOffset) % history.size + history.size) % history.size
            val edgeTrim = ((history.size * 0.04f).toInt()).coerceIn(0, ((history.size - 2) / 2).coerceAtLeast(0))
            val visibleSamples = history.size - (edgeTrim * 2)
            if (visibleSamples < 2) continue
            val startIndex = (startIndexRaw + edgeTrim) % history.size
            val maxRenderSamples = cellWidth.toInt().coerceIn(96, 1024)
            val renderSamples = visibleSamples.coerceIn(2, maxRenderSamples)
            val sampleStep = if (renderSamples <= 1) {
                0f
            } else {
                (visibleSamples - 1).toFloat() / (renderSamples - 1).toFloat()
            }
            val stepX = if (renderSamples <= 1) {
                0f
            } else {
                cellWidth / (renderSamples - 1).toFloat()
            }
            val firstSample = sampleHistoryAtOffset(
                history = history,
                startIndex = startIndex,
                sampleOffset = 0f
            )
            var prevX = left
            var prevY = centerY - (firstSample * ampScale)
            for (i in 1 until renderSamples) {
                val sample = sampleHistoryAtOffset(
                    history = history,
                    startIndex = startIndex,
                    sampleOffset = i.toFloat() * sampleStep
                )
                val x = left + i * stepX
                val y = centerY - (sample * ampScale)
                waveformBuilder.addLine(prevX, prevY, x, y)
                prevX = x
                prevY = y
            }
        }

        val waveSize = waveformBuilder.size
        val gridSize = gridBuilder.size
        waveformVertexCount = waveSize / 2
        gridVertexCount = gridSize / 2
        waveformBuffer = ensureBuffer(waveformBuffer, waveSize).apply {
            clear()
            put(waveformBuilder.data, 0, waveSize)
            position(0)
        }
        gridBuffer = ensureBuffer(gridBuffer, gridSize).apply {
            clear()
            put(gridBuilder.data, 0, gridSize)
            position(0)
        }
    }

    private fun sampleHistoryAtOffset(
        history: FloatArray,
        startIndex: Int,
        sampleOffset: Float
    ): Float {
        if (history.isEmpty()) return 0f
        val size = history.size
        val clampedOffset = sampleOffset.coerceIn(0f, (size - 1).toFloat())
        val base = clampedOffset.toInt().coerceIn(0, size - 1)
        val frac = (clampedOffset - base.toFloat()).coerceIn(0f, 1f)
        val idx0 = (startIndex + base) % size
        val idx1 = (idx0 + 1) % size
        val s0 = history[idx0].coerceIn(-1f, 1f)
        val s1 = history[idx1].coerceIn(-1f, 1f)
        return s0 + ((s1 - s0) * frac)
    }

    private fun addRoundedRectBorder(
        builder: TextureFloatLineBuilder,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float
    ) {
        val w = (right - left).coerceAtLeast(0f)
        val h = (bottom - top).coerceAtLeast(0f)
        val r = radius.coerceAtLeast(0f).coerceAtMost(minOf(w, h) * 0.5f)
        if (r <= 0.5f) {
            builder.addLine(left, top, right, top)
            builder.addLine(right, top, right, bottom)
            builder.addLine(right, bottom, left, bottom)
            builder.addLine(left, bottom, left, top)
            return
        }
        builder.addLine(left + r, top, right - r, top)
        builder.addLine(right, top + r, right, bottom - r)
        builder.addLine(right - r, bottom, left + r, bottom)
        builder.addLine(left, bottom - r, left, top + r)

        val segments = 8
        addArcPolyline(builder, right - r, top + r, r, -90f, 0f, segments)
        addArcPolyline(builder, right - r, bottom - r, r, 0f, 90f, segments)
        addArcPolyline(builder, left + r, bottom - r, r, 90f, 180f, segments)
        addArcPolyline(builder, left + r, top + r, r, 180f, 270f, segments)
    }

    private fun addArcPolyline(
        builder: TextureFloatLineBuilder,
        cx: Float,
        cy: Float,
        radius: Float,
        startDeg: Float,
        endDeg: Float,
        segments: Int
    ) {
        if (radius <= 0f || segments <= 0) return
        var prevX = cx + (kotlin.math.cos(Math.toRadians(startDeg.toDouble())).toFloat() * radius)
        var prevY = cy + (kotlin.math.sin(Math.toRadians(startDeg.toDouble())).toFloat() * radius)
        for (i in 1..segments) {
            val t = i.toFloat() / segments.toFloat()
            val deg = startDeg + ((endDeg - startDeg) * t)
            val x = cx + (kotlin.math.cos(Math.toRadians(deg.toDouble())).toFloat() * radius)
            val y = cy + (kotlin.math.sin(Math.toRadians(deg.toDouble())).toFloat() * radius)
            builder.addLine(prevX, prevY, x, y)
            prevX = x
            prevY = y
        }
    }

    private fun ensureBuffer(existing: FloatBuffer?, requiredFloats: Int): FloatBuffer {
        if (requiredFloats <= 0) {
            return existing ?: ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        }
        if (existing != null && existing.capacity() >= requiredFloats) return existing
        return ByteBuffer
            .allocateDirect(requiredFloats * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
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

private class TextureFloatLineBuilder(initialFloatCapacity: Int) {
    var data = FloatArray(initialFloatCapacity.coerceAtLeast(16))
        private set
    var size = 0
        private set

    fun addLine(x0: Float, y0: Float, x1: Float, y1: Float) {
        ensureCapacity(4)
        data[size++] = x0
        data[size++] = y0
        data[size++] = x1
        data[size++] = y1
    }

    fun reset() {
        size = 0
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

}

private fun resolveGridTexture(
    channels: Int,
    strategy: com.flopster101.siliconplayer.VisualizationChannelScopeLayout
): Pair<Int, Int> {
    if (channels <= 1) return 1 to 1
    return when (strategy) {
        com.flopster101.siliconplayer.VisualizationChannelScopeLayout.ColumnFirst -> {
            val targetRowsPerColumn = 7
            val columns = if (channels <= 4) {
                1
            } else {
                ceil(channels / targetRowsPerColumn.toDouble()).toInt().coerceAtLeast(2)
            }
            val rows = ceil(channels / columns.toDouble()).toInt().coerceAtLeast(1)
            columns to rows
        }
        com.flopster101.siliconplayer.VisualizationChannelScopeLayout.BalancedTwoColumn -> {
            val columns = ceil(sqrt(channels.toDouble())).toInt().coerceAtLeast(1)
            val rows = ceil(channels / columns.toDouble()).toInt().coerceAtLeast(1)
            columns to rows
        }
    }
}
