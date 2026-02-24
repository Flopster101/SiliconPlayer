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

@Composable
fun OscilloscopeGlTextureVisualization(
    waveformLeft: FloatArray,
    waveformRight: FloatArray,
    channelCount: Int,
    oscStereo: Boolean,
    lineColor: Color,
    gridColor: Color,
    lineWidthPx: Float,
    gridWidthPx: Float,
    showVerticalGrid: Boolean,
    showCenterLine: Boolean,
    onFrameStats: ((fps: Int, frameMs: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var glView by remember { mutableStateOf<OscilloscopeGlTextureView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            OscilloscopeGlTextureView(context).also { view ->
                glView = view
            }
        },
        update = { view ->
            view.onFrameStats = onFrameStats
            view.updateFrame(
                OscilloscopeGlTextureFrame(
                    waveformLeft = waveformLeft,
                    waveformRight = waveformRight,
                    channelCount = channelCount,
                    stereo = oscStereo && channelCount > 1,
                    lineColorArgb = lineColor.toArgb(),
                    rightLineColorArgb = lineColor.copy(alpha = 0.78f).toArgb(),
                    gridColorArgb = gridColor.toArgb(),
                    lineWidthPx = lineWidthPx.coerceAtLeast(1f),
                    gridWidthPx = gridWidthPx.coerceAtLeast(0.5f),
                    showVerticalGrid = showVerticalGrid,
                    showCenterLine = showCenterLine
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

private data class OscilloscopeGlTextureFrame(
    val waveformLeft: FloatArray,
    val waveformRight: FloatArray,
    val channelCount: Int,
    val stereo: Boolean,
    val lineColorArgb: Int,
    val rightLineColorArgb: Int,
    val gridColorArgb: Int,
    val lineWidthPx: Float,
    val gridWidthPx: Float,
    val showVerticalGrid: Boolean,
    val showCenterLine: Boolean
)

private class OscilloscopeGlTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var renderThread: OscilloscopeTextureRenderThread? = null
    private var latestFrame: OscilloscopeGlTextureFrame? = null
    private var lifecyclePaused: Boolean = false
    var onFrameStats: ((fps: Int, frameMs: Int) -> Unit)? = null

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    fun updateFrame(frame: OscilloscopeGlTextureFrame) {
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
        val thread = OscilloscopeTextureRenderThread(
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

private class OscilloscopeTextureRenderThread(
    private val outputSurface: Surface,
    initialWidth: Int,
    initialHeight: Int,
    private val onFrameStats: (fps: Int, frameMs: Int) -> Unit
) : Thread("OscilloscopeGlTextureRenderThread") {
    private val lock = Object()
    private var running = true
    private var frameData: OscilloscopeGlTextureFrame? = null
    private var surfaceWidth = initialWidth
    private var surfaceHeight = initialHeight
    private var surfaceSizeChanged = true

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private val renderer = OscilloscopeGlCoreRenderer()
    private data class LoopState(
        val frame: OscilloscopeGlTextureFrame?,
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
                    while (running && frameData == null) {
                        lock.wait(8L)
                    }
                    if (!running) {
                        LoopState(
                            frame = null,
                            width = 0,
                            height = 0,
                            surfaceSizeChanged = false,
                            shouldStop = true
                        )
                    } else {
                        LoopState(
                            frame = frameData,
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
                    renderer.onSurfaceChanged(state.width, state.height)
                }
                renderer.drawFrame(frame)
                EGL14.eglSwapBuffers(eglDisplay, eglSurface)
            }
        } finally {
            releaseEgl()
            outputSurface.release()
        }
    }

    fun setFrameData(frame: OscilloscopeGlTextureFrame) {
        synchronized(lock) {
            frameData = frame
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
        renderer.onSurfaceCreated(onFrameStats)
        renderer.onSurfaceChanged(surfaceWidth, surfaceHeight)
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
        eglSurface = EGL14.EGL_NO_SURFACE
        eglContext = EGL14.EGL_NO_CONTEXT
        eglDisplay = EGL14.EGL_NO_DISPLAY
    }
}

private class OscilloscopeGlCoreRenderer {
    private var program = 0
    private var positionHandle = -1
    private var colorHandle = -1
    private var surfaceWidth = 1
    private var surfaceHeight = 1
    private var lineBuffer: FloatBuffer? = null
    private val gridBuilder = OscFloatLineBuilder(192)
    private val waveBuilder = OscFloatLineBuilder(8_192)
    private var onFrameStats: ((fps: Int, frameMs: Int) -> Unit)? = null
    private var frameCount = 0
    private var frameWindowStartNs = 0L
    private var lastFrameNs = 0L

    fun onSurfaceCreated(onFrameStats: ((fps: Int, frameMs: Int) -> Unit)?) {
        this.onFrameStats = onFrameStats
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
    }

    fun drawFrame(frame: OscilloscopeGlTextureFrame) {
        val width = surfaceWidth.toFloat()
        val height = surfaceHeight.toFloat()
        if (width <= 1f || height <= 1f) return

        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        val left = if (frame.waveformLeft.isNotEmpty()) frame.waveformLeft else FloatArray(256)
        val right = if (frame.waveformRight.isNotEmpty()) frame.waveformRight else left
        if (left.size < 2) return

        val stereo = frame.stereo && frame.channelCount > 1
        val gridWidth = frame.gridWidthPx.coerceAtLeast(0.5f)
        val half = height * 0.5f
        val separatorHalfThickness = if (stereo) gridWidth * 0.5f else 0f
        val topLaneMin = 0f
        val topLaneMax = (half - separatorHalfThickness).coerceAtLeast(0f)
        val bottomLaneMin = (half + separatorHalfThickness).coerceAtMost(height)
        val bottomLaneMax = height
        val centerLeft = if (stereo) (topLaneMin + topLaneMax) * 0.5f else half
        val centerRight = if (stereo) (bottomLaneMin + bottomLaneMax) * 0.5f else half
        val stereoLaneHalfExtent = ((topLaneMax - topLaneMin) * 0.5f).coerceAtLeast(1f)
        val ampScale = if (stereo) stereoLaneHalfExtent * 0.8f else height * 0.34f
        val stepX = width / (left.size - 1).coerceAtLeast(1).toFloat()

        if (frame.showVerticalGrid || frame.showCenterLine || stereo) {
            val gridVertexCount = buildGridVertices(
                width = width,
                height = height,
                half = half,
                centerLeft = centerLeft,
                centerRight = centerRight,
                stereo = stereo,
                showVerticalGrid = frame.showVerticalGrid,
                showCenterLine = frame.showCenterLine
            )
            if (gridVertexCount > 0) {
                drawLines(
                    vertices = gridBuilder.data,
                    vertexFloatCount = gridBuilder.size,
                    argb = frame.gridColorArgb,
                    widthPx = frame.gridWidthPx.coerceAtLeast(0.5f)
                )
            }
        }

        val leftVertexCount = buildWaveVertices(left, width, height, stepX, centerLeft, ampScale)
        if (leftVertexCount > 0) {
            drawLines(
                vertices = waveBuilder.data,
                vertexFloatCount = waveBuilder.size,
                argb = frame.lineColorArgb,
                widthPx = frame.lineWidthPx.coerceAtLeast(1f)
            )
        }
        if (stereo) {
            val rightVertexCount = buildWaveVertices(right, width, height, stepX, centerRight, ampScale)
            if (rightVertexCount > 0) {
                drawLines(
                    vertices = waveBuilder.data,
                    vertexFloatCount = waveBuilder.size,
                    argb = frame.rightLineColorArgb,
                    widthPx = frame.lineWidthPx.coerceAtLeast(1f)
                )
            }
        }
        reportFrameStats()
    }

    private fun buildGridVertices(
        width: Float,
        height: Float,
        half: Float,
        centerLeft: Float,
        centerRight: Float,
        stereo: Boolean,
        showVerticalGrid: Boolean,
        showCenterLine: Boolean
    ): Int {
        gridBuilder.reset()
        fun appendLine(x0: Float, y0: Float, x1: Float, y1: Float) {
            gridBuilder.addLine(
                toNdcX(x0, width),
                toNdcY(y0, height),
                toNdcX(x1, width),
                toNdcY(y1, height)
            )
        }
        if (showVerticalGrid) {
            val verticalDivisions = 8
            for (i in 0..verticalDivisions) {
                val x = width * (i.toFloat() / verticalDivisions.toFloat())
                appendLine(x, 0f, x, height)
            }
        }
        if (stereo) {
            appendLine(0f, half, width, half)
        }
        if (showCenterLine) {
            if (stereo) {
                appendLine(0f, centerLeft, width, centerLeft)
                appendLine(0f, centerRight, width, centerRight)
            } else {
                appendLine(0f, half, width, half)
            }
        }
        return gridBuilder.size / 2
    }

    private fun buildWaveVertices(
        wave: FloatArray,
        width: Float,
        height: Float,
        stepX: Float,
        center: Float,
        ampScale: Float
    ): Int {
        waveBuilder.reset()
        if (wave.size < 2) return 0
        for (i in 1 until wave.size) {
            val x0 = (i - 1) * stepX
            val x1 = i * stepX
            val y0 = center - (wave[i - 1].coerceIn(-1f, 1f) * ampScale)
            val y1 = center - (wave[i].coerceIn(-1f, 1f) * ampScale)
            waveBuilder.addLine(
                toNdcX(x0, width),
                toNdcY(y0, height),
                toNdcX(x1, width),
                toNdcY(y1, height)
            )
        }
        return waveBuilder.size / 2
    }

    private fun drawLines(vertices: FloatArray, vertexFloatCount: Int, argb: Int, widthPx: Float) {
        if (vertexFloatCount <= 0 || positionHandle < 0 || colorHandle < 0) return
        val buffer = ensureBuffer(vertexFloatCount).apply {
            clear()
            put(vertices, 0, vertexFloatCount)
        }
        buffer.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, buffer)
        GLES20.glEnableVertexAttribArray(positionHandle)
        val a = ((argb ushr 24) and 0xFF) / 255f
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        GLES20.glUniform4f(colorHandle, r, g, b, a)
        GLES20.glLineWidth(widthPx.coerceAtLeast(1f))
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, vertexFloatCount / 2)
    }

    private fun ensureBuffer(requiredFloats: Int): FloatBuffer {
        if (requiredFloats <= 0) {
            val existing = lineBuffer
            if (existing != null) return existing
            return ByteBuffer
                .allocateDirect(4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer().also { lineBuffer = it }
        }
        val existing = lineBuffer
        if (existing != null && existing.capacity() >= requiredFloats) {
            return existing
        }
        return ByteBuffer
            .allocateDirect(requiredFloats * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .also { lineBuffer = it }
    }

    private fun reportFrameStats() {
        val callback = onFrameStats ?: return
        val now = System.nanoTime()
        if (frameWindowStartNs == 0L) {
            frameWindowStartNs = now
            lastFrameNs = now
        }
        frameCount += 1
        val frameMs = if (lastFrameNs == 0L) 0 else ((now - lastFrameNs) / 1_000_000L).toInt().coerceAtLeast(0)
        lastFrameNs = now
        val elapsed = now - frameWindowStartNs
        if (elapsed >= 1_000_000_000L) {
            val fps = (frameCount * 1_000_000_000L / elapsed).toInt().coerceAtLeast(0)
            callback(fps, frameMs)
            frameWindowStartNs = now
            frameCount = 0
        }
    }

    private fun toNdcX(x: Float, width: Float): Float = ((x / width) * 2f) - 1f
    private fun toNdcY(y: Float, height: Float): Float = 1f - ((y / height) * 2f)

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            GLES20.glDeleteProgram(program)
            return 0
        }
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            void main() {
                gl_FragColor = uColor;
            }
        """
    }
}

private class OscFloatLineBuilder(initialFloatCapacity: Int) {
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
