package com.flopster101.siliconplayer

import android.webkit.MimeTypeMap
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
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.Locale

private val artworkFallbackVideoExtensions = setOf(
    "3g2", "3gp", "asf", "avi", "divx", "f4v", "flv", "m2ts", "m2v", "m4v",
    "mkv", "mov", "mp4", "mpeg", "mpg", "mts", "ogm", "ogv", "rm", "rmvb",
    "ts", "vob", "webm", "wmv"
)

@Composable
internal fun placeholderArtworkIconForFile(
    file: File?,
    decoderName: String?,
    allowCurrentDecoderFallback: Boolean = true
): ImageVector {
    val extension = file?.name?.let(::inferredPrimaryExtensionForName) ?: return Icons.Default.MusicNote
    val effectiveDecoderName = decoderName
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: if (allowCurrentDecoderFallback) {
            NativeBridge.getCurrentDecoderName().trim().takeIf { it.isNotEmpty() }
        } else {
            null
        }
    return when (decoderArtworkHintForName(effectiveDecoderName)) {
        DecoderArtworkHint.TrackedFile -> ImageVector.vectorResource(R.drawable.ic_placeholder_tracker_chip)
        DecoderArtworkHint.GameFile -> ImageVector.vectorResource(R.drawable.ic_placeholder_gamepad)
        null -> {
            if (isLikelyVideoExtension(extension)) {
                Icons.Default.Videocam
            } else {
                Icons.Default.MusicNote
            }
        }
    }
}

private fun isLikelyVideoExtension(extension: String): Boolean {
    if (extension.isBlank()) {
        return false
    }
    val normalized = extension.lowercase(Locale.ROOT)
    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(normalized)
    if (mimeType?.startsWith("video/") == true) {
        return true
    }
    return normalized in artworkFallbackVideoExtensions
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MiniPlayerBar(
    file: File?,
    title: String,
    artist: String,
    artwork: ImageBitmap?,
    noArtworkIcon: ImageVector,
    artworkCornerRadiusDp: Int,
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
    miniContainerFocusRequester: FocusRequester,
    previousButtonFocusRequester: FocusRequester,
    stopButtonFocusRequester: FocusRequester,
    playPauseButtonFocusRequester: FocusRequester,
    nextButtonFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val hasTrack = file != null
    val formatLabel = file?.name?.let(::inferredPrimaryExtensionForName)?.uppercase(Locale.ROOT) ?: "EMPTY"
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
    val artworkCornerRatio = artworkCornerRadiusDp.coerceIn(0, 48) / 48f
    val miniArtworkSize = 46.dp
    val artworkCornerRadius = miniArtworkSize * (0.5f * artworkCornerRatio)

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
                    modifier = Modifier
                        .size(miniArtworkSize)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(artworkCornerRadius)),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(artworkCornerRadius),
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
                        modifier = Modifier
                            .focusRequester(previousButtonFocusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (
                                    keyEvent.type == KeyEventType.KeyDown &&
                                    keyEvent.key == Key.DirectionLeft
                                ) {
                                    miniContainerFocusRequester.requestFocus()
                                    true
                                } else {
                                    false
                                }
                            }
                            .size(controlButtonSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous track",
                            modifier = Modifier.size(controlIconSize)
                        )
                    }
                    IconButton(
                        onClick = onStopAndClear,
                        modifier = Modifier
                            .focusRequester(stopButtonFocusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (
                                    !canPreviousTrack &&
                                    keyEvent.type == KeyEventType.KeyDown &&
                                    keyEvent.key == Key.DirectionLeft
                                ) {
                                    miniContainerFocusRequester.requestFocus()
                                    true
                                } else {
                                    false
                                }
                            }
                            .size(controlButtonSize)
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
                        modifier = Modifier
                            .focusRequester(playPauseButtonFocusRequester)
                            .size(controlButtonSize)
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
                        modifier = Modifier
                            .focusRequester(nextButtonFocusRequester)
                            .size(controlButtonSize)
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
