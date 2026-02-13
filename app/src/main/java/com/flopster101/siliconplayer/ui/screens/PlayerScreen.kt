package com.flopster101.siliconplayer.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    file: File?,
    onBack: () -> Unit,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStopAndClear: () -> Unit,
    durationSeconds: Double,
    positionSeconds: Double,
    title: String,
    artist: String,
    sampleRateHz: Int,
    channelCount: Int,
    bitDepthLabel: String,
    artwork: ImageBitmap?,
    noArtworkIcon: ImageVector = Icons.Default.MusicNote,
    isLooping: Boolean,
    onSeek: (Double) -> Unit,
    onLoopingChanged: (Boolean) -> Unit
) {
    var sliderPosition by remember { mutableDoubleStateOf(0.0) }
    var isSeeking by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    LaunchedEffect(positionSeconds, isSeeking) {
        if (!isSeeking) {
            sliderPosition = positionSeconds.coerceIn(0.0, durationSeconds.coerceAtLeast(0.0))
        }
    }
    val animatedSliderPosition by animateFloatAsState(
        targetValue = sliderPosition.toFloat(),
        animationSpec = tween(durationMillis = 140, easing = LinearOutSlowInEasing),
        label = "playerTimelinePosition"
    )

    val hasTrack = file != null
    val displayTitle = title.ifBlank {
        if (file != null) file.nameWithoutExtension.ifBlank { file.name } else "No track selected"
    }
    val displayArtist = artist.ifBlank { if (hasTrack) "Unknown Artist" else "Tap a file to play" }
    val displayFilename = file?.name ?: "No file loaded"

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Now Playing") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to files"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        )
                    )
                )
        ) {
            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AlbumArtPlaceholder(
                        artwork = artwork,
                        placeholderIcon = noArtworkIcon,
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight(0.84f)
                    )

                    Column(
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight(0.86f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start
                    ) {
                        TrackInfoChips(
                            file = file,
                            durationSeconds = durationSeconds,
                            sampleRateHz = sampleRateHz,
                            channelCount = channelCount,
                            bitDepthLabel = bitDepthLabel
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        TrackMetadataBlock(
                            title = displayTitle,
                            artist = displayArtist,
                            filename = displayFilename
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        TimelineSection(
                            sliderPosition = if (isSeeking) sliderPosition else animatedSliderPosition.toDouble(),
                            durationSeconds = durationSeconds,
                            onSliderValueChange = { value ->
                                isSeeking = true
                                val sliderMax = durationSeconds.coerceAtLeast(0.0)
                                sliderPosition = value.toDouble().coerceIn(0.0, sliderMax)
                            },
                            onSliderValueChangeFinished = {
                                isSeeking = false
                                onSeek(sliderPosition)
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        TransportControls(
                            hasTrack = hasTrack,
                            isPlaying = isPlaying,
                            isLooping = isLooping,
                            onPlayPause = {
                                if (isPlaying) {
                                    onPause()
                                } else {
                                    onPlay()
                                }
                            },
                            onStopAndClear = onStopAndClear,
                            onLoopingChanged = { onLoopingChanged(!isLooping) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FutureActionStrip()
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    AlbumArtPlaceholder(
                        artwork = artwork,
                        placeholderIcon = noArtworkIcon,
                        modifier = Modifier
                            .fillMaxWidth(0.86f)
                            .aspectRatio(1f)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    TrackInfoChips(
                        file = file,
                        durationSeconds = durationSeconds,
                        sampleRateHz = sampleRateHz,
                        channelCount = channelCount,
                        bitDepthLabel = bitDepthLabel,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TrackMetadataBlock(
                        title = displayTitle,
                        artist = displayArtist,
                        filename = displayFilename
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TimelineSection(
                        sliderPosition = if (isSeeking) sliderPosition else animatedSliderPosition.toDouble(),
                        durationSeconds = durationSeconds,
                        onSliderValueChange = { value ->
                            isSeeking = true
                            val sliderMax = durationSeconds.coerceAtLeast(0.0)
                            sliderPosition = value.toDouble().coerceIn(0.0, sliderMax)
                        },
                        onSliderValueChangeFinished = {
                            isSeeking = false
                            onSeek(sliderPosition)
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TransportControls(
                        hasTrack = hasTrack,
                        isPlaying = isPlaying,
                        isLooping = isLooping,
                        onPlayPause = {
                            if (isPlaying) {
                                onPause()
                            } else {
                                onPlay()
                            }
                        },
                        onStopAndClear = onStopAndClear,
                        onLoopingChanged = { onLoopingChanged(!isLooping) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FutureActionStrip(modifier = Modifier.fillMaxWidth(0.94f))
                }
            }
        }
    }
}

@Composable
private fun AlbumArtPlaceholder(
    artwork: ImageBitmap?,
    placeholderIcon: ImageVector,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Crossfade(targetState = artwork, label = "albumArtCrossfade") { art ->
            if (art != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = art,
                        contentDescription = "Album artwork",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                    MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = placeholderIcon,
                            contentDescription = "No album artwork",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(72.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackInfoChips(
    file: File?,
    durationSeconds: Double,
    sampleRateHz: Int,
    channelCount: Int,
    bitDepthLabel: String,
    modifier: Modifier = Modifier
) {
    val formatLabel = file?.extension?.uppercase()?.ifBlank { "UNKNOWN" } ?: "EMPTY"
    val durationLabel = if (durationSeconds > 0.0) formatTime(durationSeconds) else "--:--"
    val sampleRateLabel = if (sampleRateHz > 0) {
        if (sampleRateHz % 1000 == 0) {
            "${sampleRateHz / 1000}kHz"
        } else {
            String.format("%.1fkHz", sampleRateHz / 1000.0)
        }
    } else {
        "--kHz"
    }
    val depthDisplay = bitDepthLabel.ifBlank { "Unknown" }
    val channelsAndDepth = if (channelCount > 0) {
        "${channelCount}ch/$depthDisplay"
    } else {
        depthDisplay
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val compactLevel = when {
        screenWidthDp <= 400 -> 2
        screenWidthDp <= 460 -> 1
        else -> 0
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            when (compactLevel) {
                2 -> 3.dp
                1 -> 4.dp
                else -> 6.dp
            },
            Alignment.CenterHorizontally
        )
    ) {
        TrackInfoChip(
            icon = Icons.Default.AudioFile,
            text = formatLabel,
            compactLevel = compactLevel
        )
        TrackInfoChip(
            icon = Icons.Default.Timer,
            text = durationLabel,
            compactLevel = compactLevel
        )
        TrackInfoChip(
            icon = Icons.Default.Equalizer,
            text = sampleRateLabel,
            compactLevel = compactLevel
        )
        TrackInfoChip(
            icon = Icons.Default.Info,
            text = channelsAndDepth,
            compactLevel = compactLevel
        )
    }
}

@Composable
private fun TrackInfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    compactLevel: Int
) {
    val iconSize = when (compactLevel) {
        2 -> 12.dp
        1 -> 13.dp
        else -> 14.dp
    }
    val iconSlot = when (compactLevel) {
        2 -> 12.dp
        else -> 14.dp
    }
    val sideInset = when (compactLevel) {
        2 -> 5.dp
        1 -> 6.dp
        else -> 7.dp
    }
    val minHeight = when (compactLevel) {
        2 -> 30.dp
        1 -> 32.dp
        else -> 34.dp
    }
    val textStartPadding = iconSlot + when (compactLevel) {
        2 -> 2.dp
        else -> 3.dp
    }
    val textEndPadding = when (compactLevel) {
        2 -> 1.dp
        else -> 2.dp
    }
    val textStyle = when {
        compactLevel == 2 -> MaterialTheme.typography.labelSmall
        compactLevel == 1 && text.length >= 10 -> MaterialTheme.typography.labelSmall
        else -> MaterialTheme.typography.labelMedium
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
        shape = MaterialTheme.shapes.large
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = minHeight)
                .padding(horizontal = sideInset),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(iconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = text,
                style = textStyle,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                softWrap = false,
                modifier = Modifier.padding(start = textStartPadding, end = textEndPadding)
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TrackMetadataBlock(title: String, artist: String, filename: String) {
    val shouldMarquee = title.length > 30
    if (shouldMarquee) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Start,
                modifier = Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    initialDelayMillis = 900
                )
            )
        }
    } else {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = artist,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = filename,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun TransportControls(
    hasTrack: Boolean,
    isPlaying: Boolean,
    isLooping: Boolean,
    onPlayPause: () -> Unit,
    onStopAndClear: () -> Unit,
    onLoopingChanged: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = onLoopingChanged,
            modifier = Modifier.size(72.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (isLooping) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Icon(
                imageVector = Icons.Default.Loop,
                contentDescription = if (isLooping) "Disable loop" else "Enable loop",
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.width(18.dp))

        FilledIconButton(
            onClick = onPlayPause,
            enabled = hasTrack,
            modifier = Modifier.size(96.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "playerPlayPauseIcon"
            ) { playing ->
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(18.dp))

        FilledTonalIconButton(
            onClick = onStopAndClear,
            modifier = Modifier.size(72.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "Stop"
            )
        }
    }
}

@Composable
private fun FutureActionStrip(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text("1x") }
            )
            IconButton(onClick = {}, enabled = false) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Track info (future)"
                )
            }
            IconButton(onClick = {}, enabled = false) {
                Icon(
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = "Core details (future)"
                )
            }
            IconButton(onClick = {}, enabled = false) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "Playback speed (future)"
                )
            }
        }
    }
}

@Composable
private fun TimelineSection(
    sliderPosition: Double,
    durationSeconds: Double,
    onSliderValueChange: (Float) -> Unit,
    onSliderValueChangeFinished: () -> Unit
) {
    val sliderMax = durationSeconds.coerceAtLeast(0.0).toFloat()
    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = sliderPosition.toFloat().coerceIn(0f, sliderMax),
            onValueChange = onSliderValueChange,
            onValueChangeFinished = onSliderValueChangeFinished,
            valueRange = 0f..sliderMax,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(sliderPosition),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = formatTime(durationSeconds),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

private fun formatTime(seconds: Double): String {
    val safeSeconds = seconds.coerceAtLeast(0.0).roundToInt()
    val minutes = safeSeconds / 60
    val remainingSeconds = safeSeconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}
