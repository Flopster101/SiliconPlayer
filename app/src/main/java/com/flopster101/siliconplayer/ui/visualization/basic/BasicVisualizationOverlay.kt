package com.flopster101.siliconplayer.ui.visualization.basic

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.VisualizationVuAnchor

@Composable
fun BasicVisualizationOverlay(
    mode: VisualizationMode,
    bars: FloatArray,
    waveformLeft: FloatArray,
    waveformRight: FloatArray,
    vuLevels: FloatArray,
    channelCount: Int,
    barCount: Int,
    barRoundnessDp: Int,
    barOverlayArtwork: Boolean,
    barUseThemeColor: Boolean,
    oscStereo: Boolean,
    vuAnchor: VisualizationVuAnchor,
    vuUseThemeColor: Boolean,
    modifier: Modifier = Modifier
) {
    if (mode == VisualizationMode.Off) return

    val barColor = if (barUseThemeColor) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f)
    }
    val oscColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
    val vuColor = if (vuUseThemeColor) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f)
    }
    val vuBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val barBackgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)

    when (mode) {
        VisualizationMode.Bars -> {
            BarsVisualization(
                bars = bars,
                barCount = barCount,
                barRoundnessDp = barRoundnessDp,
                barOverlayArtwork = barOverlayArtwork,
                barColor = barColor,
                backgroundColor = barBackgroundColor,
                modifier = modifier
            )
        }

        VisualizationMode.Oscilloscope -> {
            OscilloscopeVisualization(
                waveformLeft = waveformLeft,
                waveformRight = waveformRight,
                channelCount = channelCount,
                oscStereo = oscStereo,
                oscColor = oscColor,
                modifier = modifier
            )
        }

        VisualizationMode.VuMeters -> {
            VuMetersVisualization(
                vuLevels = vuLevels,
                channelCount = channelCount,
                vuAnchor = vuAnchor,
                vuColor = vuColor,
                vuBackgroundColor = vuBackgroundColor,
                modifier = modifier
            )
        }

        VisualizationMode.Off -> Unit
    }
}
