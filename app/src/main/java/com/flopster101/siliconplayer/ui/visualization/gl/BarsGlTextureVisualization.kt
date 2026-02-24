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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun BarsGlTextureVisualization(
    bars: FloatArray,
    barCount: Int,
    barRoundnessDp: Int,
    barOverlayArtwork: Boolean,
    barColor: Color,
    backgroundColor: Color,
    onFrameStats: ((fps: Int, frameMs: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val edgePadPx = with(density) { 8.dp.toPx() }
    val topHeadroomPx = with(density) { 8.dp.toPx() }
    val roundnessPx = with(density) { barRoundnessDp.dp.toPx() }
    var glView by remember { mutableStateOf<BarsGlTextureView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            BarsGlTextureView(context).also { glView = it }
        },
        update = { view ->
            view.onFrameStats = onFrameStats
            view.updateFrame(
                BarsGlTextureFrame(
                    bars = bars,
                    barCount = barCount.coerceIn(8, 96),
                    barOverlayArtwork = barOverlayArtwork,
                    barColorArgb = barColor.toArgb(),
                    backgroundColorArgb = backgroundColor.toArgb(),
                    barRoundnessPx = roundnessPx,
                    edgePadPx = edgePadPx,
                    topHeadroomPx = topHeadroomPx
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

private data class BarsGlTextureFrame(
    val bars: FloatArray,
    val barCount: Int,
    val barOverlayArtwork: Boolean,
    val barColorArgb: Int,
    val backgroundColorArgb: Int,
    val barRoundnessPx: Float,
    val edgePadPx: Float,
    val topHeadroomPx: Float
)

private class BarsGlTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var renderThread: BarsTextureRenderThread? = null
    private var latestFrame: BarsGlTextureFrame? = null
    private var lifecyclePaused: Boolean = false
    var onFrameStats: ((fps: Int, frameMs: Int) -> Unit)? = null

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    fun updateFrame(frame: BarsGlTextureFrame) {
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
        if (!lifecyclePaused) startRenderThread(surface, width, height)
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
        val thread = BarsTextureRenderThread(
            outputSurface = surface,
            initialWidth = width.coerceAtLeast(1),
            initialHeight = height.coerceAtLeast(1),
            onFrameStats = { fps, frameMs -> post { onFrameStats?.invoke(fps, frameMs) } }
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
}

private class BarsTextureRenderThread(
    private val outputSurface: Surface,
    initialWidth: Int,
    initialHeight: Int,
    private val onFrameStats: (fps: Int, frameMs: Int) -> Unit
) : Thread("BarsGlTextureRenderThread") {
    private val lock = Object()
    private var running = true
    private var frameData: BarsGlTextureFrame? = null
    private var surfaceWidth = initialWidth
    private var surfaceHeight = initialHeight
    private var surfaceSizeChanged = true

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private val renderer = BarsGlCoreRenderer()
    private data class LoopState(
        val frame: BarsGlTextureFrame?,
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

    fun setFrameData(frame: BarsGlTextureFrame) {
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
        if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)) return false
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
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        }
        if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
        if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) EGL14.eglTerminate(eglDisplay)
    }
}

private class BarsGlCoreRenderer {
    private var program = 0
    private var positionHandle = -1
    private var colorHandle = -1
    private var surfaceWidth = 1
    private var surfaceHeight = 1
    private var onFrameStats: ((fps: Int, frameMs: Int) -> Unit)? = null
    private var frameCount = 0
    private var frameWindowStartNs = 0L
    private var lastFrameNs = 0L

    fun onSurfaceCreated(onFrameStats: ((fps: Int, frameMs: Int) -> Unit)?) {
        this.onFrameStats = onFrameStats
        program = GlSimplePrimitives.createProgram()
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

    fun drawFrame(frame: BarsGlTextureFrame) {
        val width = surfaceWidth.toFloat()
        val height = surfaceHeight.toFloat()
        if (width <= 1f || height <= 1f) return

        GLES20.glUseProgram(program)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (!frame.barOverlayArtwork) {
            val bgVertices = GlSimplePrimitives.rectToTrianglesNdc(0f, 0f, width, height, width, height)
            GlSimplePrimitives.drawTriangles(bgVertices, frame.backgroundColorArgb, positionHandle, colorHandle)
        }

        val count = frame.barCount.coerceIn(8, 96)
        val widthPx = (width - (frame.edgePadPx * 2f)).coerceAtLeast(1f)
        val heightPx = (height - frame.topHeadroomPx).coerceAtLeast(1f)
        if (count <= 0 || widthPx <= 0f || heightPx <= 0f) return
        val gapPx = (widthPx / count) * 0.18f
        val barWidth = ((widthPx - gapPx * (count - 1)) / count).coerceAtLeast(1f)
        val baselineY = height

        val source = if (frame.bars.isNotEmpty()) frame.bars else FloatArray(256)
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
            val x = frame.edgePadPx + (i * (barWidth + gapPx))
            val y = baselineY - h
            val radius = frame.barRoundnessPx.coerceAtMost(barWidth * 0.45f).coerceAtMost(h * 0.5f)
            val vertices = GlSimplePrimitives.roundRectToTrianglesNdc(
                x = x,
                y = y,
                w = barWidth,
                h = h,
                radius = radius,
                surfaceWidth = width,
                surfaceHeight = height
            )
            GlSimplePrimitives.drawTriangles(vertices, frame.barColorArgb, positionHandle, colorHandle)
        }

        reportFrameStats()
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
}
