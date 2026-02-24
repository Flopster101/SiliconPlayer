package com.flopster101.siliconplayer.ui.visualization.basic

import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

private const val VISUALIZATION_FFT_SIZE = 2048
private const val VISUALIZATION_MIN_DISPLAY_HZ = 35f

internal data class BarsFrequencyMapping(
    val sourceSize: Int,
    val minFrequencyHz: Float,
    val maxFrequencyHz: Float
) {
    val maxSourceIndex: Int = (sourceSize - 1).coerceAtLeast(0)
    private val frequencySpanHz: Float = (maxFrequencyHz - minFrequencyHz).coerceAtLeast(1f)
    private val ratio: Float = (maxFrequencyHz / minFrequencyHz).coerceAtLeast(1.001f)
    private val logRatio: Double = ln(ratio.toDouble())

    fun sourceIndexToFrequencyHz(index: Float): Float {
        val safeMax = maxSourceIndex.coerceAtLeast(1)
        val t = (index / safeMax.toFloat()).coerceIn(0f, 1f)
        return minFrequencyHz + (t * frequencySpanHz)
    }

    fun frequencyHzToSourceIndex(frequencyHz: Float): Float {
        val safeMax = maxSourceIndex.coerceAtLeast(1)
        val clamped = frequencyHz.coerceIn(minFrequencyHz, maxFrequencyHz)
        val t = ((clamped - minFrequencyHz) / frequencySpanHz).coerceIn(0f, 1f)
        return t * safeMax.toFloat()
    }

    fun logPositionToFrequencyHz(logPosition: Float): Float {
        val mapped = logPosition.coerceIn(0f, 1f)
        return minFrequencyHz * ratio.pow(mapped)
    }

    fun frequencyHzToLogPosition(frequencyHz: Float): Float {
        val clamped = frequencyHz.coerceIn(minFrequencyHz, maxFrequencyHz)
        val mapped = ln((clamped / minFrequencyHz).toDouble()) / logRatio
        return mapped.toFloat().coerceIn(0f, 1f)
    }
}

internal data class BarsFrequencyGuideTick(
    val frequencyHz: Float,
    val xFraction: Float,
    val isMajor: Boolean
)

private val BAR_FREQUENCY_GUIDE_HZ = floatArrayOf(
    31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f
)

internal fun resolveBarsFrequencyMapping(
    sampleRateHz: Int,
    sourceSize: Int
): BarsFrequencyMapping {
    val safeSampleRate = sampleRateHz.coerceAtLeast(8_000)
    val fftHalf = VISUALIZATION_FFT_SIZE / 2
    val minBin = (
        (VISUALIZATION_MIN_DISPLAY_HZ / safeSampleRate.toFloat()) * VISUALIZATION_FFT_SIZE.toFloat()
        ).toInt().coerceIn(1, fftHalf - 2)
    val maxBin = fftHalf - 1
    val minFrequencyHz = (minBin.toFloat() * safeSampleRate.toFloat()) / VISUALIZATION_FFT_SIZE.toFloat()
    val maxFrequencyHz = (maxBin.toFloat() * safeSampleRate.toFloat()) / VISUALIZATION_FFT_SIZE.toFloat()
    return BarsFrequencyMapping(
        sourceSize = sourceSize.coerceAtLeast(2),
        minFrequencyHz = minFrequencyHz.coerceAtLeast(1f),
        maxFrequencyHz = maxFrequencyHz.coerceAtLeast(minFrequencyHz + 1f)
    )
}

internal fun computeBarsFrequencyGuideTicks(
    sampleRateHz: Int,
    sourceSize: Int,
    midShiftBias: Float,
    minimumSpacingFraction: Float = 0f
): List<BarsFrequencyGuideTick> {
    if (sourceSize < 2) return emptyList()
    val mapping = resolveBarsFrequencyMapping(sampleRateHz, sourceSize)
    val invMidShiftBias = (1f / midShiftBias).coerceAtLeast(1f)

    var lastX = Float.NEGATIVE_INFINITY
    return buildList(BAR_FREQUENCY_GUIDE_HZ.size) {
        BAR_FREQUENCY_GUIDE_HZ.forEachIndexed { index, frequencyHz ->
            if (frequencyHz <= mapping.minFrequencyHz || frequencyHz >= mapping.maxFrequencyHz) {
                return@forEachIndexed
            }
            val mapped = mapping.frequencyHzToLogPosition(frequencyHz)
            val xFraction = mapped.pow(invMidShiftBias).coerceIn(0f, 1f)
            if (minimumSpacingFraction > 0f && xFraction - lastX < minimumSpacingFraction) return@forEachIndexed
            lastX = xFraction
            add(
                BarsFrequencyGuideTick(
                    frequencyHz = frequencyHz,
                    xFraction = xFraction,
                    isMajor = index % 3 == 0
                )
            )
        }
    }
}

internal fun formatBarsFrequencyLabel(frequencyHz: Float): String {
    return if (frequencyHz >= 1000f) {
        val value = frequencyHz / 1000f
        val formatted = if (value % 1f == 0f) {
            value.roundToInt().toString()
        } else if ((value * 10f) % 1f == 0f) {
            String.format(Locale.US, "%.1f", value).trimEnd('0').trimEnd('.')
        } else {
            String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
        }
        "${formatted}kHz"
    } else {
        val formatted = if (frequencyHz % 1f == 0f) {
            frequencyHz.roundToInt().toString()
        } else {
            String.format(Locale.US, "%.1f", frequencyHz).trimEnd('0').trimEnd('.')
        }
        "${formatted}Hz"
    }
}
