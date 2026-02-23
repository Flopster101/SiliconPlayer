package com.flopster101.siliconplayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.ui.screens.PlayerScreen
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoxScope.MiniPlayerOverlayHost(
    miniPlayerFocusRequester: FocusRequester,
    isPlayerSurfaceVisible: Boolean,
    isPlayerExpanded: Boolean,
    miniExpandPreviewProgress: Float,
    onMiniExpandPreviewProgressChanged: (Float) -> Unit,
    expandFromMiniDrag: Boolean,
    onExpandFromMiniDragChanged: (Boolean) -> Unit,
    onCollapseFromSwipeChanged: (Boolean) -> Unit,
    onPlayerExpandedChanged: (Boolean) -> Unit,
    screenHeightPx: Float,
    miniPreviewLiftPx: Float,
    selectedFile: File?,
    visiblePlayableFiles: List<File>,
    isPlaying: Boolean,
    playbackStartInProgress: Boolean,
    seekUiBusy: Boolean,
    durationSeconds: Double,
    positionSeconds: Double,
    metadataTitle: String,
    metadataArtist: String,
    metadataSampleRate: Int,
    metadataChannelCount: Int,
    metadataBitDepthLabel: String,
    decoderName: String?,
    playbackSourceLabel: String?,
    pathOrUrl: String?,
    artworkBitmap: ImageBitmap?,
    activeRepeatMode: RepeatMode,
    playbackCapabilitiesFlags: Int,
    canOpenCurrentCoreSettings: Boolean,
    openCurrentCoreSettings: () -> Unit,
    visualizationMode: VisualizationMode,
    availableVisualizationModes: List<VisualizationMode>,
    cycleVisualizationMode: () -> Unit,
    setVisualizationMode: (VisualizationMode) -> Unit,
    openVisualizationSettings: () -> Unit,
    openSelectedVisualizationSettings: () -> Unit,
    visualizationBarCount: Int,
    visualizationBarSmoothingPercent: Int,
    visualizationBarRoundnessDp: Int,
    visualizationBarOverlayArtwork: Boolean,
    visualizationBarUseThemeColor: Boolean,
    visualizationBarRenderBackend: VisualizationRenderBackend,
    visualizationOscStereo: Boolean,
    visualizationVuAnchor: VisualizationVuAnchor,
    visualizationVuUseThemeColor: Boolean,
    visualizationVuSmoothingPercent: Int,
    visualizationVuRenderBackend: VisualizationRenderBackend,
    visualizationShowDebugInfo: Boolean,
    playerArtworkCornerRadiusDp: Int,
    filenameDisplayMode: FilenameDisplayMode,
    filenameOnlyWhenTitleMissing: Boolean,
    showMiniPlayerFocusHighlight: Boolean,
    onMiniPlayerExpandRequested: () -> Unit,
    canResumeStoppedTrack: Boolean,
    onHidePlayerSurface: () -> Unit,
    onPreviousTrack: () -> Boolean,
    onNextTrack: () -> Boolean,
    onPlayPause: () -> Unit,
    onStopAndClear: () -> Unit,
    onOpenAudioEffects: () -> Unit
) {
    val uiScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    if (isPlayerSurfaceVisible && !isPlayerExpanded && miniExpandPreviewProgress > 0f) {
        val previewProgress = miniExpandPreviewProgress.coerceIn(0f, 1f)
        val previewOffsetPx = (1f - previewProgress) * screenHeightPx
        Box(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    translationY = previewOffsetPx
                    alpha = (previewProgress * 0.98f).coerceIn(0f, 1f)
                }
        ) {
            PlayerScreen(
                file = selectedFile,
                onBack = {},
                enableCollapseGesture = false,
                isPlaying = isPlaying,
                canResumeStoppedTrack = false,
                onPlay = {},
                onPause = {},
                onStopAndClear = {},
                durationSeconds = durationSeconds,
                positionSeconds = positionSeconds,
                canPreviousTrack = currentTrackIndexForList(selectedFile, visiblePlayableFiles) > 0,
                canNextTrack = currentTrackIndexForList(selectedFile, visiblePlayableFiles) in 0 until (visiblePlayableFiles.size - 1),
                title = metadataTitle,
                artist = metadataArtist,
                sampleRateHz = metadataSampleRate,
                channelCount = metadataChannelCount,
                bitDepthLabel = metadataBitDepthLabel,
                decoderName = decoderName,
                playbackSourceLabel = playbackSourceLabel,
                pathOrUrl = pathOrUrl,
                artwork = artworkBitmap,
                noArtworkIcon = placeholderArtworkIconForFile(selectedFile, decoderName),
                repeatMode = activeRepeatMode,
                canCycleRepeatMode = supportsLiveRepeatMode(playbackCapabilitiesFlags),
                canSeek = canSeekPlayback(playbackCapabilitiesFlags),
                hasReliableDuration = hasReliableDuration(playbackCapabilitiesFlags),
                playbackStartInProgress = playbackStartInProgress,
                seekInProgress = seekUiBusy,
                onSeek = {},
                onPreviousTrack = {},
                onNextTrack = {},
                onPreviousSubtune = {},
                onNextSubtune = {},
                onOpenSubtuneSelector = {},
                canPreviousSubtune = false,
                canNextSubtune = false,
                canOpenSubtuneSelector = false,
                onCycleRepeatMode = {},
                canOpenCoreSettings = canOpenCurrentCoreSettings,
                onOpenCoreSettings = openCurrentCoreSettings,
                visualizationMode = visualizationMode,
                availableVisualizationModes = availableVisualizationModes,
                onCycleVisualizationMode = cycleVisualizationMode,
                onSelectVisualizationMode = setVisualizationMode,
                onOpenVisualizationSettings = openVisualizationSettings,
                onOpenSelectedVisualizationSettings = openSelectedVisualizationSettings,
                visualizationBarCount = visualizationBarCount,
                visualizationBarSmoothingPercent = visualizationBarSmoothingPercent,
                visualizationBarRoundnessDp = visualizationBarRoundnessDp,
                visualizationBarOverlayArtwork = visualizationBarOverlayArtwork,
                visualizationBarUseThemeColor = visualizationBarUseThemeColor,
                visualizationBarRenderBackend = visualizationBarRenderBackend,
                visualizationOscStereo = visualizationOscStereo,
                visualizationVuAnchor = visualizationVuAnchor,
                visualizationVuUseThemeColor = visualizationVuUseThemeColor,
                visualizationVuSmoothingPercent = visualizationVuSmoothingPercent,
                visualizationVuRenderBackend = visualizationVuRenderBackend,
                onOpenAudioEffects = onOpenAudioEffects,
                visualizationShowDebugInfo = visualizationShowDebugInfo,
                artworkCornerRadiusDp = playerArtworkCornerRadiusDp,
                filenameDisplayMode = filenameDisplayMode,
                filenameOnlyWhenTitleMissing = filenameOnlyWhenTitleMissing
            )
        }
    }

    AnimatedVisibility(
        visible = isPlayerSurfaceVisible && !isPlayerExpanded,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn() + scaleIn(initialScale = 0.96f),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        val previousButtonFocusRequester = remember { FocusRequester() }
        val stopButtonFocusRequester = remember { FocusRequester() }
        val playPauseButtonFocusRequester = remember { FocusRequester() }
        val nextButtonFocusRequester = remember { FocusRequester() }
        val canPreviousMiniTrack = currentTrackIndexForList(selectedFile, visiblePlayableFiles) > 0
        val canNextMiniTrack = currentTrackIndexForList(selectedFile, visiblePlayableFiles) in 0 until (visiblePlayableFiles.size - 1)
        var miniPlayerHasFocus by remember { mutableStateOf(false) }
        val miniPlayerFocusHighlight by animateFloatAsState(
            targetValue = if (miniPlayerHasFocus && showMiniPlayerFocusHighlight) 1f else 0f,
            animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing),
            label = "miniPlayerFocusHighlight"
        )
        val dismissState = rememberSwipeToDismissBoxState(
            positionalThreshold = { totalDistance -> totalDistance * 0.6f },
            confirmValueChange = { targetValue ->
                val isDismiss = targetValue == SwipeToDismissBoxValue.StartToEnd ||
                    targetValue == SwipeToDismissBoxValue.EndToStart
                if (isDismiss && !isPlaying) {
                    onHidePlayerSurface()
                    true
                } else {
                    false
                }
            }
        )
        val miniPlayerModifier = Modifier
            .graphicsLayer {
                val dragProgress = miniExpandPreviewProgress.coerceIn(0f, 1f)
                val hideMini = expandFromMiniDrag || isPlayerExpanded
                val alphaFloor = if (hideMini) 0f else 0.16f
                alpha = (1f - dragProgress).coerceIn(alphaFloor, 1f)
                translationY = -miniPreviewLiftPx * dragProgress
            }
            .onFocusChanged { state -> miniPlayerHasFocus = state.hasFocus }
            .focusRequester(miniPlayerFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when (keyEvent.key) {
                    Key.DirectionUp -> {
                        focusManager.moveFocus(FocusDirection.Up)
                        true
                    }
                    Key.DirectionRight -> {
                        if (canPreviousMiniTrack) {
                            previousButtonFocusRequester.requestFocus()
                        } else {
                            stopButtonFocusRequester.requestFocus()
                        }
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        onMiniPlayerExpandRequested()
                        onCollapseFromSwipeChanged(false)
                        onExpandFromMiniDragChanged(miniExpandPreviewProgress > 0f)
                        onMiniExpandPreviewProgressChanged(0f)
                        onPlayerExpandedChanged(true)
                        true
                    }
                    else -> false
                }
            }
            .clip(MaterialTheme.shapes.large)
            .border(
                width = (1.4f * miniPlayerFocusHighlight).dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.58f * miniPlayerFocusHighlight),
                shape = MaterialTheme.shapes.large
            )

        val miniPlayerContent: @Composable () -> Unit = {
            MiniPlayerBar(
                file = selectedFile,
                title = metadataTitle.ifBlank {
                    selectedFile?.name?.let(::inferredDisplayTitleForName)
                        ?: "No track selected"
                },
                artist = metadataArtist.ifBlank {
                    if (selectedFile != null) "Unknown Artist" else "Tap a file to play"
                },
                artwork = artworkBitmap,
                noArtworkIcon = placeholderArtworkIconForFile(selectedFile, decoderName),
                artworkCornerRadiusDp = playerArtworkCornerRadiusDp,
                isPlaying = isPlaying,
                playbackStartInProgress = playbackStartInProgress,
                seekInProgress = seekUiBusy,
                canResumeStoppedTrack = canResumeStoppedTrack,
                positionSeconds = positionSeconds,
                durationSeconds = durationSeconds,
                hasReliableDuration = hasReliableDuration(playbackCapabilitiesFlags),
                canPreviousTrack = canPreviousMiniTrack,
                canNextTrack = canNextMiniTrack,
                onExpand = {
                    onMiniPlayerExpandRequested()
                    onCollapseFromSwipeChanged(false)
                    onExpandFromMiniDragChanged(miniExpandPreviewProgress > 0f)
                    onMiniExpandPreviewProgressChanged(0f)
                    onPlayerExpandedChanged(true)
                },
                onExpandDragProgress = { progress ->
                    onMiniExpandPreviewProgressChanged(progress)
                },
                onExpandDragCommit = {
                    val start = miniExpandPreviewProgress.coerceIn(0f, 1f)
                    uiScope.launch {
                        onMiniPlayerExpandRequested()
                        onExpandFromMiniDragChanged(true)
                        val anim = Animatable(start)
                        anim.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing)
                        ) {
                            onMiniExpandPreviewProgressChanged(value)
                        }
                        onCollapseFromSwipeChanged(false)
                        onPlayerExpandedChanged(true)
                    }
                },
                onPreviousTrack = { onPreviousTrack(); Unit },
                onNextTrack = { onNextTrack(); Unit },
                onPlayPause = onPlayPause,
                onStopAndClear = onStopAndClear,
                miniContainerFocusRequester = miniPlayerFocusRequester,
                previousButtonFocusRequester = previousButtonFocusRequester,
                stopButtonFocusRequester = stopButtonFocusRequester,
                playPauseButtonFocusRequester = playPauseButtonFocusRequester,
                nextButtonFocusRequester = nextButtonFocusRequester
            )
        }

        SwipeToDismissBox(
            state = dismissState,
            modifier = miniPlayerModifier,
            backgroundContent = {},
            enableDismissFromStartToEnd = !isPlaying,
            enableDismissFromEndToStart = !isPlaying
        ) {
            miniPlayerContent()
        }
    }
}
