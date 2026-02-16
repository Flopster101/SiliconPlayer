package com.flopster101.siliconplayer.ui.visualization.basic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.VisualizationVuAnchor
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun VuMetersVisualization(
    vuLevels: FloatArray,
    channelCount: Int,
    vuAnchor: VisualizationVuAnchor,
    vuColor: Color,
    vuBackgroundColor: Color,
    modifier: Modifier = Modifier
) {
    val showStereo = channelCount > 1
    val rows = if (showStereo) 2 else 1
    val align = when (vuAnchor) {
        VisualizationVuAnchor.Top -> Alignment.TopCenter
        VisualizationVuAnchor.Center -> Alignment.Center
        VisualizationVuAnchor.Bottom -> Alignment.BottomCenter
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = align
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(rows) { idx ->
                val label = when {
                    showStereo && idx == 0 -> "Left"
                    showStereo && idx == 1 -> "Right"
                    else -> "Mono"
                }
                val raw = vuLevels.getOrElse(idx) { 0f }.coerceIn(0f, 1f)
                val db = 20f * log10(raw.coerceAtLeast(0.0001f))
                val dbFloor = -58f
                val norm = ((db - dbFloor) / -dbFloor).coerceIn(0f, 1f)
                val value = norm.toDouble().pow(0.62).toFloat().coerceIn(0f, 1f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(42.dp)
                    )
                    LinearProgressIndicator(
                        progress = { value },
                        modifier = Modifier
                            .height(10.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(50)),
                        color = vuColor,
                        trackColor = vuBackgroundColor
                    )
                }
            }
        }
    }
}
