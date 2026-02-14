package com.flopster101.siliconplayer.ui.screens

import com.flopster101.siliconplayer.NativeBridge
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import android.view.MotionEvent
import com.flopster101.siliconplayer.RepeatMode
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    file: File?,
    onBack: () -> Unit,
    onCollapseBySwipe: () -> Unit = onBack,
    enableCollapseGesture: Boolean = true,
    isPlaying: Boolean,
    canResumeStoppedTrack: Boolean = false,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStopAndClear: () -> Unit,
    canPreviousTrack: Boolean,
    canNextTrack: Boolean,
    durationSeconds: Double,
    positionSeconds: Double,
    title: String,
    artist: String,
    sampleRateHz: Int,
    channelCount: Int,
    bitDepthLabel: String,
    decoderName: String?,
    artwork: ImageBitmap?,
    noArtworkIcon: ImageVector = Icons.Default.MusicNote,
    repeatMode: RepeatMode,
    canCycleRepeatMode: Boolean,
    canSeek: Boolean,
    hasReliableDuration: Boolean,
    onSeek: (Double) -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onPreviousSubtune: () -> Unit,
    onNextSubtune: () -> Unit,
    onOpenSubtuneSelector: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    canOpenCoreSettings: Boolean,
    onOpenCoreSettings: () -> Unit,
    onOpenAudioEffects: () -> Unit,
    filenameDisplayMode: com.flopster101.siliconplayer.FilenameDisplayMode = com.flopster101.siliconplayer.FilenameDisplayMode.Always
) {
    var sliderPosition by remember(file?.absolutePath, durationSeconds) {
        mutableDoubleStateOf(positionSeconds.coerceIn(0.0, durationSeconds.coerceAtLeast(0.0)))
    }
    var isSeeking by remember { mutableStateOf(false) }
    var isTimelineTouchActive by remember { mutableStateOf(false) }
    var downwardDragPx by remember { mutableFloatStateOf(0f) }
    var isDraggingDown by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val collapseThresholdPx = with(density) { 132.dp.toPx() }

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
    val animatedPanelOffsetPx by animateFloatAsState(
        targetValue = if (isDraggingDown) downwardDragPx else 0f,
        animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing),
        label = "playerCollapseDragOffset"
    )
    val panelOffsetPx = if (isDraggingDown) downwardDragPx else animatedPanelOffsetPx
    val dragFadeProgress = (panelOffsetPx / (collapseThresholdPx * 1.4f)).coerceIn(0f, 1f)
    val panelAlpha = 1f - (0.22f * dragFadeProgress)

    val hasTrack = file != null
    val displayTitle = title.ifBlank {
        if (file != null) file.nameWithoutExtension.ifBlank { file.name } else "No track selected"
    }
    val displayArtist = artist.ifBlank { if (hasTrack) "Unknown Artist" else "Tap a file to play" }
    val displayFilename = file?.name ?: "No file loaded"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, panelOffsetPx.roundToInt()) }
            .graphicsLayer(alpha = panelAlpha)
            .then(
                if (enableCollapseGesture) {
                    Modifier.pointerInput(collapseThresholdPx, isTimelineTouchActive) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                if (isTimelineTouchActive) return@detectVerticalDragGestures
                                val next = (downwardDragPx + dragAmount).coerceAtLeast(0f)
                                if (next > 0f || downwardDragPx > 0f) {
                                    isDraggingDown = true
                                    downwardDragPx = next
                                    change.consume()
                                }
                            },
                            onDragEnd = {
                                if (isTimelineTouchActive) return@detectVerticalDragGestures
                                val shouldCollapse = downwardDragPx >= collapseThresholdPx
                                if (shouldCollapse) {
                                    onCollapseBySwipe()
                                } else {
                                    isDraggingDown = false
                                    downwardDragPx = 0f
                                }
                            },
                            onDragCancel = {
                                if (isTimelineTouchActive) return@detectVerticalDragGestures
                                isDraggingDown = false
                                downwardDragPx = 0f
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Now Playing") },
                    navigationIcon = {
                        IconButton(onClick = onBack, enabled = enableCollapseGesture) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Minimize player"
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
                        decoderName = decoderName,
                        fileSizeBytes = file?.length() ?: 0L,
                        sampleRateHz = sampleRateHz,
                        channelCount = channelCount,
                        bitDepthLabel = bitDepthLabel
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        TrackMetadataBlock(
                            title = displayTitle,
                            artist = displayArtist,
                            filename = displayFilename,
                            filenameDisplayMode = filenameDisplayMode,
                            decoderName = decoderName
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        TimelineSection(
                            sliderPosition = if (isSeeking) sliderPosition else animatedSliderPosition.toDouble(),
                            durationSeconds = durationSeconds,
                            canSeek = canSeek,
                            hasReliableDuration = hasReliableDuration,
                            onSeekInteractionChanged = { isTimelineTouchActive = it },
                            onSliderValueChange = { value ->
                                isSeeking = true
                                val sliderMax = durationSeconds.coerceAtLeast(0.0)
                                sliderPosition = value.toDouble().coerceIn(0.0, sliderMax)
                            },
                            onSliderValueChangeFinished = {
                                isSeeking = false
                                if (canSeek && hasReliableDuration) {
                                    onSeek(sliderPosition)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        TransportControls(
                            hasTrack = hasTrack,
                            isPlaying = isPlaying,
                            canResumeStoppedTrack = canResumeStoppedTrack,
                            repeatMode = repeatMode,
                            canPreviousTrack = canPreviousTrack,
                            canNextTrack = canNextTrack,
                            canCycleRepeatMode = canCycleRepeatMode,
                            onPlayPause = {
                                if (isPlaying) {
                                    onPause()
                                } else {
                                    onPlay()
                                }
                            },
                            onPreviousTrack = onPreviousTrack,
                            onNextTrack = onNextTrack,
                            onPreviousSubtune = onPreviousSubtune,
                            onNextSubtune = onNextSubtune,
                            onOpenSubtuneSelector = onOpenSubtuneSelector,
                            onStopAndClear = onStopAndClear,
                            onCycleRepeatMode = onCycleRepeatMode
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FutureActionStrip(
                            canOpenCoreSettings = canOpenCoreSettings,
                            onOpenCoreSettings = onOpenCoreSettings,
                            onOpenAudioEffects = onOpenAudioEffects
                        )
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
                        decoderName = decoderName,
                        fileSizeBytes = file?.length() ?: 0L,
                        sampleRateHz = sampleRateHz,
                        channelCount = channelCount,
                        bitDepthLabel = bitDepthLabel,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TrackMetadataBlock(
                        title = displayTitle,
                        artist = displayArtist,
                        filename = displayFilename,
                        filenameDisplayMode = filenameDisplayMode,
                        decoderName = decoderName
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TimelineSection(
                        sliderPosition = if (isSeeking) sliderPosition else animatedSliderPosition.toDouble(),
                        durationSeconds = durationSeconds,
                        canSeek = canSeek,
                        hasReliableDuration = hasReliableDuration,
                        onSeekInteractionChanged = { isTimelineTouchActive = it },
                        onSliderValueChange = { value ->
                            isSeeking = true
                            val sliderMax = durationSeconds.coerceAtLeast(0.0)
                            sliderPosition = value.toDouble().coerceIn(0.0, sliderMax)
                        },
                        onSliderValueChangeFinished = {
                            isSeeking = false
                            if (canSeek && hasReliableDuration) {
                                onSeek(sliderPosition)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TransportControls(
                        hasTrack = hasTrack,
                        isPlaying = isPlaying,
                        canResumeStoppedTrack = canResumeStoppedTrack,
                        repeatMode = repeatMode,
                        canPreviousTrack = canPreviousTrack,
                        canNextTrack = canNextTrack,
                        canCycleRepeatMode = canCycleRepeatMode,
                        onPlayPause = {
                            if (isPlaying) {
                                onPause()
                            } else {
                                onPlay()
                            }
                        },
                        onPreviousTrack = onPreviousTrack,
                        onNextTrack = onNextTrack,
                        onPreviousSubtune = onPreviousSubtune,
                        onNextSubtune = onNextSubtune,
                        onOpenSubtuneSelector = onOpenSubtuneSelector,
                        onStopAndClear = onStopAndClear,
                        onCycleRepeatMode = onCycleRepeatMode
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FutureActionStrip(
                        modifier = Modifier.fillMaxWidth(0.94f),
                        canOpenCoreSettings = canOpenCoreSettings,
                        onOpenCoreSettings = onOpenCoreSettings,
                        onOpenAudioEffects = onOpenAudioEffects
                    )
                }
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
    decoderName: String?,
    fileSizeBytes: Long,
    sampleRateHz: Int,
    channelCount: Int,
    bitDepthLabel: String,
    modifier: Modifier = Modifier
) {
    val formatLabel = file?.extension?.uppercase()?.ifBlank { "UNKNOWN" } ?: "EMPTY"

    // Bitrate or file size based on decoder
    val bitrateOrSize = remember(file, decoderName, fileSizeBytes) {
        when {
            decoderName.equals("FFmpeg", ignoreCase = true) -> {
                val bitrate = NativeBridge.getTrackBitrate()
                val isVBR = NativeBridge.isTrackVBR()
                if (bitrate > 0) {
                    formatBitrate(bitrate, isVBR)
                } else null
            }
            fileSizeBytes > 0 -> formatFileSize(fileSizeBytes)
            else -> null
        }
    }

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
        if (bitrateOrSize != null) {
            TrackInfoChip(
                icon = if (decoderName.equals("FFmpeg", ignoreCase = true))
                    Icons.Default.Speed else Icons.Default.Info,
                text = bitrateOrSize,
                compactLevel = compactLevel
            )
        }
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
private fun TrackMetadataBlock(
    title: String,
    artist: String,
    filename: String,
    filenameDisplayMode: com.flopster101.siliconplayer.FilenameDisplayMode,
    decoderName: String?
) {
    val shouldShowFilename = remember(filenameDisplayMode, decoderName) {
        when (filenameDisplayMode) {
            com.flopster101.siliconplayer.FilenameDisplayMode.Always -> true
            com.flopster101.siliconplayer.FilenameDisplayMode.Never -> false
            com.flopster101.siliconplayer.FilenameDisplayMode.TrackerOnly -> {
                val decoder = decoderName?.lowercase() ?: ""
                decoder.contains("openmpt") || decoder.contains("libopenmpt")
            }
        }
    }

    AnimatedContent(
        targetState = title,
        transitionSpec = {
            fadeIn(animationSpec = tween(durationMillis = 180, delayMillis = 40)) togetherWith
                fadeOut(animationSpec = tween(durationMillis = 120))
        },
        label = "trackTitleSwap"
    ) { animatedTitle ->
        val shouldMarquee = animatedTitle.length > 30
        if (shouldMarquee) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
            ) {
                Text(
                    text = animatedTitle,
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
                text = animatedTitle,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    AnimatedContent(
        targetState = artist,
        transitionSpec = {
            fadeIn(animationSpec = tween(durationMillis = 180, delayMillis = 30)) togetherWith
                fadeOut(animationSpec = tween(durationMillis = 110))
        },
        label = "trackArtistSwap"
    ) { animatedArtist ->
        Text(
            text = animatedArtist,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    if (shouldShowFilename) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = filename,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TransportControls(
    hasTrack: Boolean,
    isPlaying: Boolean,
    canResumeStoppedTrack: Boolean,
    repeatMode: RepeatMode,
    canPreviousTrack: Boolean,
    canNextTrack: Boolean,
    canCycleRepeatMode: Boolean,
    onPlayPause: () -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onPreviousSubtune: () -> Unit,
    onNextSubtune: () -> Unit,
    onOpenSubtuneSelector: () -> Unit,
    onStopAndClear: () -> Unit,
    onCycleRepeatMode: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val tight = maxWidth < 350.dp
        val compact = !tight && maxWidth < 390.dp
        val sideButtonSize = when {
            tight -> 48.dp
            compact -> 54.dp
            else -> 62.dp
        }
        val playButtonSize = when {
            tight -> 76.dp
            compact -> 82.dp
            else -> 90.dp
        }
        val subtuneButtonSize = when {
            tight -> 46.dp
            compact -> 52.dp
            else -> 56.dp
        }
        val rowGap = when {
            tight -> 6.dp
            compact -> 8.dp
            else -> 10.dp
        }
        val subtuneGap = if (tight) 8.dp else 10.dp

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = onPreviousTrack,
                        enabled = hasTrack && canPreviousTrack,
                        modifier = Modifier.size(sideButtonSize),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous track"
                        )
                    }

                    Spacer(modifier = Modifier.width(rowGap))

                    FilledTonalIconButton(
                        onClick = onCycleRepeatMode,
                        enabled = hasTrack && canCycleRepeatMode,
                        modifier = Modifier.size(sideButtonSize),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (repeatMode != RepeatMode.None) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        val modeBadge = when (repeatMode) {
                            RepeatMode.None -> ""
                            RepeatMode.Track -> "1"
                            RepeatMode.LoopPoint -> "LP"
                        }
                        BadgedBox(
                            badge = {
                                if (modeBadge.isNotEmpty()) {
                                    Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                        Text(
                                            text = modeBadge,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Loop,
                                contentDescription = "Repeat mode: ${repeatMode.label}",
                                modifier = Modifier.size(
                                    when {
                                        tight -> 20.dp
                                        compact -> 22.dp
                                        else -> 24.dp
                                    }
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(rowGap))

                FilledIconButton(
                    onClick = onPlayPause,
                    enabled = hasTrack || canResumeStoppedTrack,
                    modifier = Modifier.size(playButtonSize),
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
                            modifier = Modifier.size(
                                when {
                                    tight -> 28.dp
                                    compact -> 30.dp
                                    else -> 34.dp
                                }
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(rowGap))

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = onStopAndClear,
                        modifier = Modifier.size(sideButtonSize),
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

                    Spacer(modifier = Modifier.width(rowGap))

                    FilledTonalIconButton(
                        onClick = onNextTrack,
                        enabled = hasTrack && canNextTrack,
                        modifier = Modifier.size(sideButtonSize),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next track"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onPreviousSubtune,
                    enabled = false,
                    modifier = Modifier.size(subtuneButtonSize),
                    shape = MaterialTheme.shapes.large,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardDoubleArrowLeft,
                        contentDescription = "Previous subtune"
                    )
                }
                Spacer(modifier = Modifier.width(subtuneGap))
                FilledTonalIconButton(
                    onClick = onOpenSubtuneSelector,
                    enabled = false,
                    modifier = Modifier.size(subtuneButtonSize),
                    shape = MaterialTheme.shapes.large,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = "Subtune selector"
                    )
                }
                Spacer(modifier = Modifier.width(subtuneGap))
                FilledTonalIconButton(
                    onClick = onNextSubtune,
                    enabled = false,
                    modifier = Modifier.size(subtuneButtonSize),
                    shape = MaterialTheme.shapes.large,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardDoubleArrowRight,
                        contentDescription = "Next subtune"
                    )
                }
            }
        }
    }
}

@Composable
private fun FutureActionStrip(
    modifier: Modifier = Modifier,
    canOpenCoreSettings: Boolean,
    onOpenCoreSettings: () -> Unit,
    onOpenAudioEffects: () -> Unit
) {
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
            IconButton(onClick = onOpenCoreSettings, enabled = canOpenCoreSettings) {
                Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = "Open current core settings"
                )
            }
            IconButton(onClick = onOpenAudioEffects, enabled = true) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Audio effects"
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
    canSeek: Boolean,
    hasReliableDuration: Boolean,
    onSeekInteractionChanged: (Boolean) -> Unit,
    onSliderValueChange: (Float) -> Unit,
    onSliderValueChangeFinished: () -> Unit
) {
    val sliderMax = durationSeconds.coerceAtLeast(0.0).toFloat()
    val normalizedValue = sliderPosition.toFloat().coerceIn(0f, sliderMax)
    val seekEnabled = canSeek && hasReliableDuration && durationSeconds > 0.0
    val durationText = if (hasReliableDuration && durationSeconds > 0.0) {
        formatTime(durationSeconds)
    } else {
        "-:--"
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        LineageStyleSeekBar(
            value = normalizedValue,
            maxValue = sliderMax,
            enabled = seekEnabled,
            onSeekInteractionChanged = onSeekInteractionChanged,
            onValueChange = onSliderValueChange,
            onValueChangeFinished = onSliderValueChangeFinished,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
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
                text = durationText,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun LineageStyleSeekBar(
    value: Float,
    maxValue: Float,
    enabled: Boolean,
    onSeekInteractionChanged: (Boolean) -> Unit,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val trackHeightPx = with(density) { 8.dp.toPx() }
    val thumbWidthPx = with(density) { 6.dp.toPx() }
    val thumbHeightPx = with(density) { 26.dp.toPx() }
    val thumbGrabRadiusPx = with(density) { 20.dp.toPx() }
    val tapLaneHalfHeightPx = with(density) { 22.dp.toPx() }
    var barWidthPx by remember { mutableFloatStateOf(0f) }
    var barHeightPx by remember { mutableFloatStateOf(0f) }
    var draggingThumb by remember { mutableStateOf(false) }
    var thumbPressed by remember { mutableStateOf(false) }
    var thumbHovered by remember { mutableStateOf(false) }

    fun xToValue(x: Float): Float {
        if (barWidthPx <= 0f || maxValue <= 0f) return 0f
        val ratio = (x / barWidthPx).coerceIn(0f, 1f)
        return ratio * maxValue
    }

    Canvas(
        modifier = modifier
            .pointerInteropFilter { event ->
                if (!enabled || barWidthPx <= 0f || maxValue <= 0f) return@pointerInteropFilter false
                val centerY = barHeightPx / 2f
                val valueRatio = if (maxValue > 0f) (value / maxValue).coerceIn(0f, 1f) else 0f
                val thumbCenterX = valueRatio * barWidthPx
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        thumbHovered = false
                        val nearTrackLane = kotlin.math.abs(event.y - centerY) <= tapLaneHalfHeightPx
                        if (!nearTrackLane) return@pointerInteropFilter false
                        val nearThumb = kotlin.math.abs(event.x - thumbCenterX) <= thumbGrabRadiusPx
                        return@pointerInteropFilter if (nearThumb) {
                            draggingThumb = true
                            thumbPressed = true
                            onSeekInteractionChanged(true)
                            onValueChange(xToValue(event.x))
                            true
                        } else {
                            onValueChange(xToValue(event.x))
                            onValueChangeFinished()
                            true
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!draggingThumb) return@pointerInteropFilter false
                        onValueChange(xToValue(event.x))
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (!draggingThumb) return@pointerInteropFilter false
                        draggingThumb = false
                        thumbPressed = false
                        onSeekInteractionChanged(false)
                        onValueChangeFinished()
                        true
                    }
                    MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_HOVER_ENTER -> {
                        val nearThumb = kotlin.math.abs(event.x - thumbCenterX) <= thumbGrabRadiusPx
                        thumbHovered = nearThumb
                        false
                    }
                    MotionEvent.ACTION_HOVER_EXIT -> {
                        thumbHovered = false
                        false
                    }
                    else -> false
                }
            }
            .onSizeChanged { canvasSize ->
                barWidthPx = canvasSize.width.toFloat()
                barHeightPx = canvasSize.height.toFloat()
            }
    ) {
        val centerY = size.height / 2f
        val top = centerY - trackHeightPx / 2f
        val trackCorner = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)
        val activeColor = colorScheme.primary
        val inactiveColor = colorScheme.surfaceVariant
        val ratio = if (maxValue > 0f) (value / maxValue).coerceIn(0f, 1f) else 0f
        val activeWidth = size.width * ratio

        drawRoundRect(
            color = inactiveColor,
            topLeft = Offset(0f, top),
            size = Size(size.width, trackHeightPx),
            cornerRadius = trackCorner
        )
        if (activeWidth > 0f) {
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(0f, top),
                size = Size(activeWidth, trackHeightPx),
                cornerRadius = trackCorner
            )
        }

        val thumbX = activeWidth.coerceIn(0f, size.width)
        if (thumbHovered || thumbPressed || draggingThumb) {
            drawCircle(
                color = activeColor.copy(alpha = 0.22f),
                radius = with(density) { 14.dp.toPx() },
                center = Offset(thumbX, centerY)
            )
        }
        val thumbLeft = (thumbX - thumbWidthPx / 2f).coerceIn(0f, size.width - thumbWidthPx)
        val thumbTop = centerY - thumbHeightPx / 2f
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(thumbLeft, thumbTop),
            size = Size(thumbWidthPx, thumbHeightPx),
            cornerRadius = CornerRadius(thumbWidthPx / 2f, thumbWidthPx / 2f)
        )
    }
}

private fun formatTime(seconds: Double): String {
    val safeSeconds = seconds.coerceAtLeast(0.0).roundToInt()
    val minutes = safeSeconds / 60
    val remainingSeconds = safeSeconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}

private fun formatBitrate(bitrateInBitsPerSecond: Long, isVBR: Boolean): String {
    val kbps = bitrateInBitsPerSecond / 1000.0
    val prefix = if (isVBR) "~" else ""

    return when {
        kbps >= 1000 -> String.format(java.util.Locale.US, "%s%.1f Mbps", prefix, kbps / 1000.0)
        else -> String.format(java.util.Locale.US, "%s%.0f kbps", prefix, kbps)
    }
}

private fun formatFileSize(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble().coerceAtLeast(0.0)
    var unitIndex = 0

    while (size >= 1024.0 && unitIndex < units.lastIndex) {
        size /= 1024.0
        unitIndex++
    }

    return if (unitIndex == 0) {
        String.format(java.util.Locale.US, "%.0f %s", size, units[unitIndex])
    } else {
        String.format(java.util.Locale.US, "%.1f %s", size, units[unitIndex])
    }
}
