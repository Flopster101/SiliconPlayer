package com.flopster101.siliconplayer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.roundToInt

@Composable
fun PlayerScreen(
    file: File,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    durationSeconds: Double,
    positionSeconds: Double,
    title: String,
    artist: String,
    isLooping: Boolean,
    onSeek: (Double) -> Unit,
    onLoopingChanged: (Boolean) -> Unit
) {
    var isPlaying by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableDoubleStateOf(0.0) }
    var isSeeking by remember { mutableStateOf(false) }

    LaunchedEffect(positionSeconds, isSeeking) {
        if (!isSeeking) {
            sliderPosition = positionSeconds.coerceIn(0.0, durationSeconds.coerceAtLeast(0.0))
        }
    }

    val displayTitle = title.ifBlank { file.nameWithoutExtension.ifBlank { file.name } }
    val displayArtist = artist.ifBlank { "Unknown Artist" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Now Playing", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = displayTitle, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = displayArtist, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(24.dp))

        val sliderMax = durationSeconds.coerceAtLeast(0.0)
        Slider(
            value = sliderPosition.toFloat(),
            onValueChange = { value ->
                isSeeking = true
                sliderPosition = value.toDouble().coerceIn(0.0, sliderMax)
            },
            onValueChangeFinished = {
                isSeeking = false
                onSeek(sliderPosition)
            },
            valueRange = 0f..sliderMax.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "${formatTime(sliderPosition)} / ${formatTime(durationSeconds)}",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                if (isPlaying) {
                    onStop()
                    isPlaying = false
                } else {
                    onPlay()
                    isPlaying = true
                }
            }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Stop" else "Play"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = if (isPlaying) "Stop" else "Play")
            }
            FilledTonalButton(onClick = { onLoopingChanged(!isLooping) }) {
                Text(if (isLooping) "Loop On" else "Loop Off")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) {
            Text("Back to Files")
        }
    }
}

private fun formatTime(seconds: Double): String {
    val safeSeconds = seconds.coerceAtLeast(0.0).roundToInt()
    val minutes = safeSeconds / 60
    val remainingSeconds = safeSeconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}
