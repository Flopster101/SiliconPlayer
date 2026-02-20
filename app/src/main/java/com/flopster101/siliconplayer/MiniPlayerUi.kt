package com.flopster101.siliconplayer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
internal fun placeholderArtworkIconForFile(file: File?): ImageVector {
    val extension = file?.extension?.lowercase()?.ifBlank { return Icons.Default.MusicNote }
        ?: return Icons.Default.MusicNote
    return if (extension in trackerModuleExtensions) {
        ImageVector.vectorResource(R.drawable.ic_placeholder_tracker_chip)
    } else {
        Icons.Default.MusicNote
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MiniPlayerBar(
    file: File?,
    title: String,
    artist: String,
    artwork: ImageBitmap?,
    noArtworkIcon: ImageVector,
    isPlaying: Boolean,
    playbackStartInProgress: Boolean,
    seekInProgress: Boolean,
    canResumeStoppedTrack: Boolean,
    positionSeconds: Double,
    durationSeconds: Double,
    hasReliableDuration: Boolean,
    canPreviousTrack: Boolean,
    canNextTrack: Boolean,
    onExpand: () -> Unit,
    onExpandDragProgress: (Float) -> Unit,
    onExpandDragCommit: () -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onPlayPause: () -> Unit,
    onStopAndClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasTrack = file != null
    val formatLabel = file?.extension?.uppercase()?.ifBlank { "UNKNOWN" } ?: "EMPTY"
    val positionLabel = formatTimeForMini(positionSeconds)
    val durationLabel = if (durationSeconds > 0.0) {
        if (hasReliableDuration) formatTimeForMini(durationSeconds) else "${formatTimeForMini(durationSeconds)}?"
    } else {
        "-:--"
    }
    val progress = if (durationSeconds > 0.0) {
        (positionSeconds / durationSeconds).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    val compactControls = LocalConfiguration.current.screenWidthDp <= 420
    val controlButtonSize = if (compactControls) 36.dp else 40.dp
    val controlIconSize = if (compactControls) 20.dp else 22.dp
    val density = LocalDensity.current
    val expandSwipeThresholdPx = with(density) { 112.dp.toPx() }
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val previewDistancePx = screenHeightPx * 0.72f
    var upwardDragPx by remember { mutableFloatStateOf(0f) }

    Surface(
        modifier = modifier,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(expandSwipeThresholdPx) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                val next = (upwardDragPx - dragAmount).coerceAtLeast(0f)
                                if (next > 0f || upwardDragPx > 0f) {
                                    upwardDragPx = next
                                    onExpandDragProgress((upwardDragPx / previewDistancePx).coerceIn(0f, 1f))
                                    change.consume()
                                }
                            },
                            onDragEnd = {
                                if (upwardDragPx >= expandSwipeThresholdPx) {
                                    onExpandDragCommit()
                                    upwardDragPx = 0f
                                    return@detectVerticalDragGestures
                                }
                                upwardDragPx = 0f
                                onExpandDragProgress(0f)
                            },
                            onDragCancel = {
                                upwardDragPx = 0f
                                onExpandDragProgress(0f)
                            }
                        )
                    }
                    .clickable(onClick = onExpand)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    if (artwork != null) {
                        Image(
                            bitmap = artwork,
                            contentDescription = "Mini player artwork",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = noArtworkIcon,
                                contentDescription = "No artwork",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.size(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    AnimatedContent(
                        targetState = title,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(durationMillis = 170, delayMillis = 35)) togetherWith
                                fadeOut(animationSpec = tween(durationMillis = 110))
                        },
                        label = "miniTitleSwap"
                    ) { animatedTitle ->
                        val marquee = animatedTitle.length > 26
                        Text(
                            text = animatedTitle,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = if (marquee) TextOverflow.Clip else TextOverflow.Ellipsis,
                            modifier = if (marquee) {
                                Modifier.basicMarquee(
                                    iterations = 3,
                                    initialDelayMillis = 900
                                )
                            } else {
                                Modifier
                            }
                        )
                    }
                    AnimatedContent(
                        targetState = artist,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(durationMillis = 170, delayMillis = 25)) togetherWith
                                fadeOut(animationSpec = tween(durationMillis = 100))
                        },
                        label = "miniArtistSwap"
                    ) { animatedArtist ->
                        val marquee = animatedArtist.length > 30
                        Text(
                            text = animatedArtist,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = if (marquee) TextOverflow.Clip else TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = if (marquee) {
                                Modifier.basicMarquee(
                                    iterations = 3,
                                    initialDelayMillis = 1100
                                )
                            } else {
                                Modifier
                            }
                        )
                    }
                    Text(
                        text = "$formatLabel • $positionLabel / $durationLabel",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPreviousTrack,
                        enabled = hasTrack && canPreviousTrack,
                        modifier = Modifier.size(controlButtonSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous track",
                            modifier = Modifier.size(controlIconSize)
                        )
                    }
                    IconButton(
                        onClick = onStopAndClear,
                        modifier = Modifier.size(controlButtonSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            modifier = Modifier.size(controlIconSize)
                        )
                    }
                    IconButton(
                        onClick = onPlayPause,
                        enabled = (hasTrack || canResumeStoppedTrack) &&
                            !seekInProgress &&
                            !playbackStartInProgress,
                        modifier = Modifier.size(controlButtonSize)
                    ) {
                        if (playbackStartInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size((controlIconSize.value + 2f).dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            AnimatedContent(
                                targetState = isPlaying,
                                transitionSpec = {
                                    fadeIn() togetherWith fadeOut()
                                },
                                label = "miniPlayPauseIcon"
                            ) { playing ->
                                Icon(
                                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playing) "Pause" else "Play",
                                    modifier = Modifier.size(controlIconSize)
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = onNextTrack,
                        enabled = hasTrack && canNextTrack,
                        modifier = Modifier.size(controlButtonSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next track",
                            modifier = Modifier.size(controlIconSize)
                        )
                    }
                }
            }

            if (playbackStartInProgress) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

internal fun formatTimeForMini(seconds: Double): String {
    val safeSeconds = seconds.coerceAtLeast(0.0).toInt()
    val minutes = safeSeconds / 60
    val remainingSeconds = safeSeconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}
