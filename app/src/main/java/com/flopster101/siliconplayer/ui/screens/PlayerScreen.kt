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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import kotlinx.coroutines.delay

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
    filenameDisplayMode: com.flopster101.siliconplayer.FilenameDisplayMode = com.flopster101.siliconplayer.FilenameDisplayMode.Always,
    filenameOnlyWhenTitleMissing: Boolean = false
) {
    var sliderPosition by remember(file?.absolutePath, durationSeconds) {
        mutableDoubleStateOf(positionSeconds.coerceIn(0.0, durationSeconds.coerceAtLeast(0.0)))
    }
    var isSeeking by remember { mutableStateOf(false) }
    var isTimelineTouchActive by remember { mutableStateOf(false) }
    var downwardDragPx by remember { mutableFloatStateOf(0f) }
    var isDraggingDown by remember { mutableStateOf(false) }
    var showTrackInfoDialog by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val collapseThresholdPx = with(density) { 132.dp.toPx() }

    LaunchedEffect(positionSeconds, isSeeking) {
        if (!isSeeking) {
            sliderPosition = positionSeconds.coerceIn(0.0, durationSeconds.coerceAtLeast(0.0))
        }
    }
    val animatedPanelOffsetPx by animateFloatAsState(
        targetValue = if (isDraggingDown) downwardDragPx else 0f,
        animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing),
        label = "playerCollapseDragOffset"
    )
    val animatedSliderPosition by animateFloatAsState(
        targetValue = sliderPosition.toFloat(),
        animationSpec = tween(durationMillis = 90, easing = LinearOutSlowInEasing),
        label = "playerTimelinePosition"
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

    // Focus management for keyboard input
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                // Only handle key down events to avoid double-triggering
                if (keyEvent.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) {
                    return@onPreviewKeyEvent false
                }

                when (keyEvent.key) {
                    // Spacebar: Play/Pause
                    Key.Spacebar -> {
                        if (hasTrack || canResumeStoppedTrack) {
                            if (isPlaying) onPause() else onPlay()
                            true
                        } else false
                    }
                    // Left Arrow: Seek backward 5 seconds (without Ctrl)
                    Key.DirectionLeft -> {
                        if (!keyEvent.isCtrlPressed && canSeek && hasReliableDuration) {
                            val newPosition = (positionSeconds - 5.0).coerceAtLeast(0.0)
                            onSeek(newPosition)
                            true
                        } else false
                        // Note: Ctrl+Left for previous subtune not implemented yet (subtunes not ready)
                    }
                    // Right Arrow: Seek forward 5 seconds (without Ctrl)
                    Key.DirectionRight -> {
                        if (!keyEvent.isCtrlPressed && canSeek && hasReliableDuration) {
                            val newPosition = (positionSeconds + 5.0).coerceAtMost(durationSeconds)
                            onSeek(newPosition)
                            true
                        } else false
                        // Note: Ctrl+Right for next subtune not implemented yet (subtunes not ready)
                    }
                    // Page Up: Previous track
                    Key.PageUp -> {
                        if (hasTrack && canPreviousTrack) {
                            onPreviousTrack()
                            true
                        } else false
                    }
                    // Page Down: Next track
                    Key.PageDown -> {
                        if (hasTrack && canNextTrack) {
                            onNextTrack()
                            true
                        } else false
                    }
                    // Home: Restart song (seek to 0)
                    Key.MoveHome -> {
                        if (canSeek && hasReliableDuration) {
                            onSeek(0.0)
                            true
                        } else false
                    }
                    // R: Cycle repeat mode
                    Key.R -> {
                        if (canCycleRepeatMode) {
                            onCycleRepeatMode()
                            true
                        } else false
                    }
                    // Backspace: Stop
                    Key.Backspace -> {
                        onStopAndClear()
                        true
                    }
                    else -> false
                }
            }
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
                        bitDepthLabel = bitDepthLabel,
                        onClick = { showTrackInfoDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        TrackMetadataBlock(
                            title = displayTitle,
                            artist = displayArtist,
                            filename = displayFilename,
                            filenameDisplayMode = filenameDisplayMode,
                            decoderName = decoderName,
                            filenameOnlyWhenTitleMissing = filenameOnlyWhenTitleMissing
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        TimelineSection(
                            sliderPosition = if (isSeeking) sliderPosition else animatedSliderPosition.toDouble(),
                            elapsedPositionSeconds = if (isSeeking) sliderPosition else positionSeconds,
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
                        onClick = { showTrackInfoDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TrackMetadataBlock(
                        title = displayTitle,
                        artist = displayArtist,
                        filename = displayFilename,
                        filenameDisplayMode = filenameDisplayMode,
                        decoderName = decoderName,
                        filenameOnlyWhenTitleMissing = filenameOnlyWhenTitleMissing
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TimelineSection(
                        sliderPosition = if (isSeeking) sliderPosition else animatedSliderPosition.toDouble(),
                        elapsedPositionSeconds = if (isSeeking) sliderPosition else positionSeconds,
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
    if (showTrackInfoDialog) {
        TrackInfoDetailsDialog(
            file = file,
            title = displayTitle,
            artist = displayArtist,
            decoderName = decoderName,
            sampleRateHz = sampleRateHz,
            channelCount = channelCount,
            bitDepthLabel = bitDepthLabel,
            durationSeconds = durationSeconds,
            hasReliableDuration = hasReliableDuration,
            onDismiss = { showTrackInfoDialog = false }
        )
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
    onClick: () -> Unit,
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
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .wrapContentWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(
                when (compactLevel) {
                    2 -> 3.dp
                    1 -> 4.dp
                    else -> 6.dp
                }
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
}

@Composable
private fun TrackInfoDetailsDialog(
    file: File?,
    title: String,
    artist: String,
    decoderName: String?,
    sampleRateHz: Int,
    channelCount: Int,
    bitDepthLabel: String,
    durationSeconds: Double,
    hasReliableDuration: Boolean,
    onDismiss: () -> Unit
) {
    var liveBitrate by remember { mutableLongStateOf(0L) }
    var liveIsVbr by remember { mutableStateOf(false) }
    var liveRenderRateHz by remember { mutableIntStateOf(0) }
    var liveOutputRateHz by remember { mutableIntStateOf(0) }
    var liveComposer by remember { mutableStateOf("") }
    var liveGenre by remember { mutableStateOf("") }
    var openMptTypeLong by remember { mutableStateOf("") }
    var openMptTracker by remember { mutableStateOf("") }
    var openMptSongMessage by remember { mutableStateOf("") }
    var openMptOrderCount by remember { mutableIntStateOf(0) }
    var openMptPatternCount by remember { mutableIntStateOf(0) }
    var openMptInstrumentCount by remember { mutableIntStateOf(0) }
    var openMptSampleCount by remember { mutableIntStateOf(0) }
    var openMptInstrumentNames by remember { mutableStateOf("") }
    var openMptSampleNames by remember { mutableStateOf("") }
    var vgmGameName by remember { mutableStateOf("") }
    var vgmSystemName by remember { mutableStateOf("") }
    var vgmReleaseDate by remember { mutableStateOf("") }
    var vgmEncodedBy by remember { mutableStateOf("") }
    var vgmNotes by remember { mutableStateOf("") }
    var vgmFileVersion by remember { mutableStateOf("") }
    var vgmDeviceCount by remember { mutableIntStateOf(0) }
    var vgmUsedChipList by remember { mutableStateOf("") }
    var vgmHasLoopPoint by remember { mutableStateOf(false) }
    var ffmpegCodecName by remember { mutableStateOf("") }
    var ffmpegContainerName by remember { mutableStateOf("") }
    var ffmpegSampleFormatName by remember { mutableStateOf("") }
    var ffmpegChannelLayoutName by remember { mutableStateOf("") }
    var ffmpegEncoderName by remember { mutableStateOf("") }
    var gmeSystemName by remember { mutableStateOf("") }
    var gmeGameName by remember { mutableStateOf("") }
    var gmeCopyright by remember { mutableStateOf("") }
    var gmeComment by remember { mutableStateOf("") }
    var gmeDumper by remember { mutableStateOf("") }
    var gmeTrackCount by remember { mutableIntStateOf(0) }
    var gmeVoiceCount by remember { mutableIntStateOf(0) }
    var gmeHasLoopPoint by remember { mutableStateOf(false) }
    var gmeLoopStartMs by remember { mutableIntStateOf(-1) }
    var gmeLoopLengthMs by remember { mutableIntStateOf(-1) }
    val detailsScrollState = rememberScrollState()
    var detailsViewportHeightPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(file?.absolutePath, decoderName) {
        while (true) {
            liveBitrate = NativeBridge.getTrackBitrate()
            liveIsVbr = NativeBridge.isTrackVBR()
            liveRenderRateHz = NativeBridge.getDecoderRenderSampleRateHz()
            liveOutputRateHz = NativeBridge.getOutputStreamSampleRateHz()
            liveComposer = NativeBridge.getTrackComposer()
            liveGenre = NativeBridge.getTrackGenre()
            if (decoderName.equals("LibOpenMPT", ignoreCase = true)) {
                openMptTypeLong = NativeBridge.getOpenMptModuleTypeLong()
                openMptTracker = NativeBridge.getOpenMptTracker()
                openMptSongMessage = NativeBridge.getOpenMptSongMessage()
                openMptOrderCount = NativeBridge.getOpenMptOrderCount()
                openMptPatternCount = NativeBridge.getOpenMptPatternCount()
                openMptInstrumentCount = NativeBridge.getOpenMptInstrumentCount()
                openMptSampleCount = NativeBridge.getOpenMptSampleCount()
                openMptInstrumentNames = NativeBridge.getOpenMptInstrumentNames()
                openMptSampleNames = NativeBridge.getOpenMptSampleNames()
            } else {
                openMptTypeLong = ""
                openMptTracker = ""
                openMptSongMessage = ""
                openMptOrderCount = 0
                openMptPatternCount = 0
                openMptInstrumentCount = 0
                openMptSampleCount = 0
                openMptInstrumentNames = ""
                openMptSampleNames = ""
            }
            if (decoderName.equals("VGMPlay", ignoreCase = true)) {
                vgmGameName = NativeBridge.getVgmGameName()
                vgmSystemName = NativeBridge.getVgmSystemName()
                vgmReleaseDate = NativeBridge.getVgmReleaseDate()
                vgmEncodedBy = NativeBridge.getVgmEncodedBy()
                vgmNotes = NativeBridge.getVgmNotes()
                vgmFileVersion = NativeBridge.getVgmFileVersion()
                vgmDeviceCount = NativeBridge.getVgmDeviceCount()
                vgmUsedChipList = NativeBridge.getVgmUsedChipList()
                vgmHasLoopPoint = NativeBridge.getVgmHasLoopPoint()
            } else {
                vgmGameName = ""
                vgmSystemName = ""
                vgmReleaseDate = ""
                vgmEncodedBy = ""
                vgmNotes = ""
                vgmFileVersion = ""
                vgmDeviceCount = 0
                vgmUsedChipList = ""
                vgmHasLoopPoint = false
            }
            if (decoderName.equals("FFmpeg", ignoreCase = true)) {
                ffmpegCodecName = NativeBridge.getFfmpegCodecName()
                ffmpegContainerName = NativeBridge.getFfmpegContainerName()
                ffmpegSampleFormatName = NativeBridge.getFfmpegSampleFormatName()
                ffmpegChannelLayoutName = NativeBridge.getFfmpegChannelLayoutName()
                ffmpegEncoderName = NativeBridge.getFfmpegEncoderName()
            } else {
                ffmpegCodecName = ""
                ffmpegContainerName = ""
                ffmpegSampleFormatName = ""
                ffmpegChannelLayoutName = ""
                ffmpegEncoderName = ""
            }
            if (decoderName.equals("Game Music Emu", ignoreCase = true)) {
                gmeSystemName = NativeBridge.getGmeSystemName()
                gmeGameName = NativeBridge.getGmeGameName()
                gmeCopyright = NativeBridge.getGmeCopyright()
                gmeComment = NativeBridge.getGmeComment()
                gmeDumper = NativeBridge.getGmeDumper()
                gmeTrackCount = NativeBridge.getGmeTrackCount()
                gmeVoiceCount = NativeBridge.getGmeVoiceCount()
                gmeHasLoopPoint = NativeBridge.getGmeHasLoopPoint()
                gmeLoopStartMs = NativeBridge.getGmeLoopStartMs()
                gmeLoopLengthMs = NativeBridge.getGmeLoopLengthMs()
            } else {
                gmeSystemName = ""
                gmeGameName = ""
                gmeCopyright = ""
                gmeComment = ""
                gmeDumper = ""
                gmeTrackCount = 0
                gmeVoiceCount = 0
                gmeHasLoopPoint = false
                gmeLoopStartMs = -1
                gmeLoopLengthMs = -1
            }
            delay(500)
        }
    }

    val fileSizeBytes = file?.length() ?: 0L
    val filename = file?.name ?: "No file loaded"
    val extension = file?.extension?.uppercase()?.ifBlank { "UNKNOWN" } ?: "UNKNOWN"
    val decoderLabel = decoderName?.ifBlank { "Unknown" } ?: "Unknown"
    val bitrateLabel = if (liveBitrate > 0L) {
        "${formatBitrate(liveBitrate, liveIsVbr)} (${if (liveIsVbr) "VBR" else "CBR"})"
    } else {
        "Unavailable"
    }
    val lengthLabel = if (hasReliableDuration && durationSeconds > 0.0) {
        formatTime(durationSeconds)
    } else {
        "Unavailable"
    }
    val channelsLabel = if (channelCount > 0) "$channelCount channels" else "Unknown"
    val depthLabel = bitDepthLabel.ifBlank { "Unknown" }
    val sampleRateChain =
        "${formatSampleRateForDetails(sampleRateHz)} -> " +
            "${formatSampleRateForDetails(liveRenderRateHz)} -> " +
            formatSampleRateForDetails(liveOutputRateHz)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Track and decoder info") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { detailsViewportHeightPx = it.height }
                        .padding(end = 10.dp)
                        .verticalScroll(detailsScrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TrackInfoDetailsRow("Filename", filename)
                    TrackInfoDetailsRow("Title", title)
                    TrackInfoDetailsRow("Artist", artist)
                    if (liveComposer.isNotBlank()) {
                        TrackInfoDetailsRow("Composer", liveComposer)
                    }
                    if (liveGenre.isNotBlank()) {
                        TrackInfoDetailsRow("Genre", liveGenre)
                    }
                    TrackInfoDetailsRow("Format", extension)
                    TrackInfoDetailsRow("Decoder", decoderLabel)
                    TrackInfoDetailsRow(
                        "File size",
                        if (fileSizeBytes > 0L) formatFileSize(fileSizeBytes) else "Unavailable"
                    )
                    TrackInfoDetailsRow("Sample rate chain", sampleRateChain)
                    TrackInfoDetailsRow("Bitrate", bitrateLabel)
                    TrackInfoDetailsRow("Length", lengthLabel)
                    TrackInfoDetailsRow("Audio channels", channelsLabel)
                    TrackInfoDetailsRow("Bit depth", depthLabel)
                    if (decoderName.equals("LibOpenMPT", ignoreCase = true)) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "OpenMPT",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (openMptTypeLong.isNotBlank()) {
                            TrackInfoDetailsRow("Module type", openMptTypeLong)
                        }
                        if (openMptTracker.isNotBlank()) {
                            TrackInfoDetailsRow("Tracker", openMptTracker)
                        }
                        TrackInfoDetailsRow("Orders", openMptOrderCount.toString())
                        TrackInfoDetailsRow("Patterns", openMptPatternCount.toString())
                        TrackInfoDetailsRow("Instruments", openMptInstrumentCount.toString())
                        TrackInfoDetailsRow("Samples", openMptSampleCount.toString())
                        if (openMptSongMessage.isNotBlank()) {
                            TrackInfoDetailsRow("Message", openMptSongMessage)
                        }
                        if (openMptInstrumentNames.isNotBlank()) {
                            TrackInfoDetailsRow("Instrument names", openMptInstrumentNames)
                        }
                        if (openMptSampleNames.isNotBlank()) {
                            TrackInfoDetailsRow("Sample names", openMptSampleNames)
                        }
                    }
                    if (decoderName.equals("VGMPlay", ignoreCase = true)) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "VGMPlay",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (vgmGameName.isNotBlank()) {
                            TrackInfoDetailsRow("Game", vgmGameName)
                        }
                        if (vgmSystemName.isNotBlank()) {
                            TrackInfoDetailsRow("System", vgmSystemName)
                        }
                        if (vgmReleaseDate.isNotBlank()) {
                            TrackInfoDetailsRow("Release date", vgmReleaseDate)
                        }
                        if (vgmEncodedBy.isNotBlank()) {
                            TrackInfoDetailsRow("Encoded by", vgmEncodedBy)
                        }
                        if (vgmFileVersion.isNotBlank()) {
                            TrackInfoDetailsRow("VGM version", vgmFileVersion)
                        }
                        if (vgmDeviceCount > 0) {
                            TrackInfoDetailsRow("Used chips", vgmDeviceCount.toString())
                        }
                        if (vgmUsedChipList.isNotBlank()) {
                            TrackInfoDetailsRow("Chip list", vgmUsedChipList)
                        }
                        TrackInfoDetailsRow("Has loop point", if (vgmHasLoopPoint) "Yes" else "No")
                        if (vgmNotes.isNotBlank()) {
                            TrackInfoDetailsRow("Notes", vgmNotes)
                        }
                    }
                    if (decoderName.equals("FFmpeg", ignoreCase = true)) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "FFmpeg",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (ffmpegCodecName.isNotBlank()) {
                            TrackInfoDetailsRow("Codec", ffmpegCodecName)
                        }
                        if (ffmpegContainerName.isNotBlank()) {
                            TrackInfoDetailsRow("Container", ffmpegContainerName)
                        }
                        if (ffmpegSampleFormatName.isNotBlank()) {
                            TrackInfoDetailsRow("Sample format", ffmpegSampleFormatName)
                        }
                        if (ffmpegChannelLayoutName.isNotBlank()) {
                            TrackInfoDetailsRow("Channel layout", ffmpegChannelLayoutName)
                        }
                        if (ffmpegEncoderName.isNotBlank()) {
                            TrackInfoDetailsRow("Encoder", ffmpegEncoderName)
                        }
                    }
                    if (decoderName.equals("Game Music Emu", ignoreCase = true)) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Game Music Emu",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (gmeSystemName.isNotBlank()) {
                            TrackInfoDetailsRow("System", gmeSystemName)
                        }
                        if (gmeGameName.isNotBlank()) {
                            TrackInfoDetailsRow("Game", gmeGameName)
                        }
                        if (gmeTrackCount > 0) {
                            TrackInfoDetailsRow("Track count", gmeTrackCount.toString())
                        }
                        if (gmeVoiceCount > 0) {
                            TrackInfoDetailsRow("Voice count", gmeVoiceCount.toString())
                        }
                        TrackInfoDetailsRow("Has loop point", if (gmeHasLoopPoint) "Yes" else "No")
                        if (gmeLoopStartMs >= 0) {
                            TrackInfoDetailsRow("Loop start", formatTime(gmeLoopStartMs / 1000.0))
                        }
                        if (gmeLoopLengthMs > 0) {
                            TrackInfoDetailsRow("Loop length", formatTime(gmeLoopLengthMs / 1000.0))
                        }
                        if (gmeCopyright.isNotBlank()) {
                            TrackInfoDetailsRow("Copyright", gmeCopyright)
                        }
                        if (gmeDumper.isNotBlank()) {
                            TrackInfoDetailsRow("Dumper", gmeDumper)
                        }
                        if (gmeComment.isNotBlank()) {
                            TrackInfoDetailsRow("Comment", gmeComment)
                        }
                    }
                }
                if (detailsScrollState.maxValue > 0 && detailsViewportHeightPx > 0) {
                    TrackInfoDetailsScrollbar(
                        scrollState = detailsScrollState,
                        viewportHeightPx = detailsViewportHeightPx,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun TrackInfoDetailsRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun TrackInfoDetailsScrollbar(
    scrollState: androidx.compose.foundation.ScrollState,
    viewportHeightPx: Int,
    modifier: Modifier = Modifier
) {
    val maxScroll = scrollState.maxValue
    if (maxScroll <= 0 || viewportHeightPx <= 0) return

    val viewport = viewportHeightPx.toFloat()
    val content = viewport + maxScroll.toFloat()
    val thumbHeightPx = (viewport * (viewport / content)).coerceAtLeast(24f)
    val travelRangePx = (viewport - thumbHeightPx).coerceAtLeast(0f)
    val thumbOffsetPx =
        if (maxScroll > 0) (scrollState.value.toFloat() / maxScroll.toFloat()) * travelRangePx else 0f

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f))
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                .fillMaxWidth()
                .height(with(LocalDensity.current) { thumbHeightPx.toDp() })
                .clip(RoundedCornerShape(99.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.68f))
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
    decoderName: String?,
    filenameOnlyWhenTitleMissing: Boolean
) {
    val shouldShowFilename = remember(filenameDisplayMode, decoderName, title, filenameOnlyWhenTitleMissing) {
        when (filenameDisplayMode) {
            com.flopster101.siliconplayer.FilenameDisplayMode.Always -> {
                // If "only when title missing" is enabled, check if title is blank
                if (filenameOnlyWhenTitleMissing) {
                    title.isBlank()
                } else {
                    true
                }
            }
            com.flopster101.siliconplayer.FilenameDisplayMode.Never -> false
            com.flopster101.siliconplayer.FilenameDisplayMode.TrackerOnly -> {
                val decoder = decoderName?.lowercase() ?: ""
                val isTracker = decoder.contains("openmpt") || decoder.contains("libopenmpt")
                // If tracker format, apply the "only when title missing" logic
                if (isTracker && filenameOnlyWhenTitleMissing) {
                    title.isBlank()
                } else {
                    isTracker
                }
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
                        iterations = 3,
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
                        enabled = canCycleRepeatMode,
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
    elapsedPositionSeconds: Double,
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
                text = formatTime(elapsedPositionSeconds),
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

private fun formatSampleRateForDetails(rateHz: Int): String {
    if (rateHz <= 0) return "Unknown"
    return if (rateHz % 1000 == 0) {
        "${rateHz / 1000} kHz"
    } else {
        String.format(java.util.Locale.US, "%.1f kHz", rateHz / 1000.0)
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
