package com.flopster101.siliconplayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.dp
import java.io.File

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
    miniPreviewLiftPx: Float,
    selectedFile: File?,
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
    isTrackFavorited: Boolean,
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
    onMiniPlayerNavigateUpRequested: () -> Unit,
    onMiniPlayerExpandRequested: () -> Unit,
    canResumeStoppedTrack: Boolean,
    onHidePlayerSurface: () -> Unit,
    onPreviousTrack: () -> Boolean,
    onForcePreviousTrack: () -> Boolean,
    onNextTrack: () -> Boolean,
    onPlayPause: () -> Unit,
    onStopAndClear: () -> Unit,
    onToggleFavoriteTrack: () -> Unit,
    onOpenAudioEffects: () -> Unit,
    canPreviousTrack: Boolean,
    canNextTrack: Boolean,
    previousRestartsAfterThreshold: Boolean,
    onPreviousSubtune: () -> Unit,
    onNextSubtune: () -> Unit,
    canPreviousSubtune: Boolean,
    canNextSubtune: Boolean,
    currentSubtuneIndex: Int,
    subtuneCount: Int
) {
    var dragExpandCommitInProgress by remember { mutableStateOf(false) }
    LaunchedEffect(isPlayerExpanded, miniExpandPreviewProgress) {
        if (!isPlayerExpanded && miniExpandPreviewProgress <= 0f) {
            dragExpandCommitInProgress = false
        }
    }

    AnimatedVisibility(
        visible = isPlayerSurfaceVisible && !isPlayerExpanded && !dragExpandCommitInProgress,
        enter = slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = tween(durationMillis = 320, easing = LinearOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(durationMillis = 240)) + scaleIn(
            initialScale = 0.96f,
            animationSpec = tween(durationMillis = 320, easing = LinearOutSlowInEasing)
        ),
        exit = if (dragExpandCommitInProgress || expandFromMiniDrag) {
            fadeOut(animationSpec = tween(1))
        } else {
            slideOutVertically(
                targetOffsetY = { it / 2 },
                animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = 230))
        },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        val previousButtonFocusRequester = remember { FocusRequester() }
        val stopButtonFocusRequester = remember { FocusRequester() }
        val playPauseButtonFocusRequester = remember { FocusRequester() }
        val nextButtonFocusRequester = remember { FocusRequester() }
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
                val hideMini = dragExpandCommitInProgress || expandFromMiniDrag || isPlayerExpanded
                alpha = if (hideMini) 0f else (1f - dragProgress).coerceIn(0f, 1f)
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
                        onMiniPlayerNavigateUpRequested()
                        true
                    }
                    Key.DirectionRight -> {
                        if (canPreviousTrack) {
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
            val sanitizedTitle = sanitizeRemoteCachedMetadataTitle(metadataTitle, selectedFile)
            MiniPlayerBar(
                file = selectedFile,
                title = sanitizedTitle.ifBlank {
                    selectedFile?.name?.let(::inferredDisplayTitleForName)
                        ?: "No track loaded"
                },
                artist = metadataArtist.ifBlank {
                    if (selectedFile != null) "Unknown Artist" else "Unknown"
                },
                metadataTitleResolved = sanitizedTitle.isNotBlank(),
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
                previousRestartsAfterThreshold = previousRestartsAfterThreshold,
                canPreviousTrack = canPreviousTrack,
                canNextTrack = canNextTrack,
                canPreviousSubtune = canPreviousSubtune,
                canNextSubtune = canNextSubtune,
                currentSubtuneIndex = currentSubtuneIndex,
                subtuneCount = subtuneCount,
                onExpand = {
                    onMiniPlayerExpandRequested()
                    onCollapseFromSwipeChanged(false)
                    onExpandFromMiniDragChanged(miniExpandPreviewProgress > 0f)
                    onMiniExpandPreviewProgressChanged(0f)
                    onPlayerExpandedChanged(true)
                },
                onExpandDragProgress = onMiniExpandPreviewProgressChanged,
                onExpandDragCommit = {
                    if (dragExpandCommitInProgress) {
                        return@MiniPlayerBar
                    }
                    dragExpandCommitInProgress = true
                    onMiniPlayerExpandRequested()
                    onExpandFromMiniDragChanged(true)
                    onCollapseFromSwipeChanged(false)
                    onPlayerExpandedChanged(true)
                    onMiniExpandPreviewProgressChanged(0f)
                },
                onPreviousTrack = { onPreviousTrack(); Unit },
                onForcePreviousTrack = { onForcePreviousTrack(); Unit },
                onNextTrack = { onNextTrack(); Unit },
                onPreviousSubtune = onPreviousSubtune,
                onNextSubtune = onNextSubtune,
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
