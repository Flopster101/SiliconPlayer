package com.flopster101.siliconplayer.ui.visualization.gl

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal object GlSimplePrimitives {
    fun createProgram(): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
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

    fun rectToTrianglesNdc(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        surfaceWidth: Float,
        surfaceHeight: Float
    ): FloatArray {
        if (w <= 0f || h <= 0f || surfaceWidth <= 0f || surfaceHeight <= 0f) return FloatArray(0)
        val x0 = toNdcX(x, surfaceWidth)
        val y0 = toNdcY(y, surfaceHeight)
        val x1 = toNdcX(x + w, surfaceWidth)
        val y1 = toNdcY(y + h, surfaceHeight)
        return floatArrayOf(
            x0, y0, x1, y0, x0, y1,
            x1, y0, x1, y1, x0, y1
        )
    }

    fun roundRectToTrianglesNdc(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        radius: Float,
        surfaceWidth: Float,
        surfaceHeight: Float,
        cornerSegments: Int = 6
    ): FloatArray {
        if (w <= 0f || h <= 0f || surfaceWidth <= 0f || surfaceHeight <= 0f) return FloatArray(0)
        val r = radius.coerceAtLeast(0f).coerceAtMost(minOf(w, h) * 0.5f)
        if (r <= 0.5f) {
            return rectToTrianglesNdc(x, y, w, h, surfaceWidth, surfaceHeight)
        }

        val out = ArrayList<Float>(256)
        fun appendRect(px: Float, py: Float, pw: Float, ph: Float) {
            if (pw <= 0f || ph <= 0f) return
            val rect = rectToTrianglesNdc(px, py, pw, ph, surfaceWidth, surfaceHeight)
            for (v in rect) out.add(v)
        }
        fun appendCornerFan(
            cx: Float,
            cy: Float,
            startAngle: Double,
            endAngle: Double
        ) {
            var prevX = cx + (cos(startAngle) * r).toFloat()
            var prevY = cy + (sin(startAngle) * r).toFloat()
            for (i in 1..cornerSegments) {
                val t = i.toDouble() / cornerSegments.toDouble()
                val a = startAngle + ((endAngle - startAngle) * t)
                val nextX = cx + (cos(a) * r).toFloat()
                val nextY = cy + (sin(a) * r).toFloat()
                out.add(toNdcX(cx, surfaceWidth))
                out.add(toNdcY(cy, surfaceHeight))
                out.add(toNdcX(prevX, surfaceWidth))
                out.add(toNdcY(prevY, surfaceHeight))
                out.add(toNdcX(nextX, surfaceWidth))
                out.add(toNdcY(nextY, surfaceHeight))
                prevX = nextX
                prevY = nextY
            }
        }

        appendRect(x + r, y, w - (2f * r), h)
        appendRect(x, y + r, r, h - (2f * r))
        appendRect(x + w - r, y + r, r, h - (2f * r))

        appendCornerFan(x + r, y + r, PI, PI * 1.5)           // top-left
        appendCornerFan(x + w - r, y + r, PI * 1.5, PI * 2.0) // top-right
        appendCornerFan(x + w - r, y + h - r, 0.0, PI * 0.5)  // bottom-right
        appendCornerFan(x + r, y + h - r, PI * 0.5, PI)       // bottom-left

        return out.toFloatArray()
    }

    fun drawTriangles(vertices: FloatArray, argb: Int, positionHandle: Int, colorHandle: Int) {
        if (vertices.isEmpty() || positionHandle < 0 || colorHandle < 0) return
        val buffer: FloatBuffer = ByteBuffer
            .allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        buffer.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, buffer)
        GLES20.glEnableVertexAttribArray(positionHandle)
        val a = ((argb ushr 24) and 0xFF) / 255f
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        GLES20.glUniform4f(colorHandle, r, g, b, a)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertices.size / 2)
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

    private fun toNdcX(x: Float, width: Float): Float = ((x / width) * 2f) - 1f
    private fun toNdcY(y: Float, height: Float): Float = 1f - ((y / height) * 2f)

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
