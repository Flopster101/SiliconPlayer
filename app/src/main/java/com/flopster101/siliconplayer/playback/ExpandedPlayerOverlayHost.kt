package com.flopster101.siliconplayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.ExperimentalComposeUiApi
import com.flopster101.siliconplayer.ui.screens.PlayerScreen
import com.flopster101.siliconplayer.ui.screens.LocalPlayerFocusIndicatorsEnabled
import java.io.File
import android.view.MotionEvent

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ExpandedPlayerOverlayHost(
    isPlayerSurfaceVisible: Boolean,
    isPlayerExpanded: Boolean,
    miniExpandPreviewProgress: Float,
    expandFromMiniDrag: Boolean,
    collapseFromSwipe: Boolean,
    onCollapseFromSwipeChanged: (Boolean) -> Unit,
    onCollapseDragProgressChanged: (Boolean) -> Unit,
    onExpandedOverlayCurrentVisibleChanged: (Boolean) -> Unit,
    onExpandedOverlaySettledVisibleChanged: (Boolean) -> Unit,
    onMiniExpandPreviewProgressChanged: (Float) -> Unit,
    onPlayerExpandedChanged: (Boolean) -> Unit,
    screenHeightPx: Float,
    selectedFile: File?,
    isPlaying: Boolean,
    playbackStartInProgress: Boolean,
    canResumeStoppedTrack: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStopAndClear: () -> Unit,
    durationSeconds: Double,
    positionSecondsState: State<Double>,
    canPreviousTrack: Boolean,
    canNextTrack: Boolean,
    title: String,
    artist: String,
    sampleRateHz: Int,
    channelCount: Int,
    bitDepthLabel: String,
    decoderName: String?,
    playbackSourceLabel: String?,
    pathOrUrl: String?,
    playlistTitle: String?,
    playlistFormatLabel: String?,
    playlistTrackCount: Int,
    playlistPathOrUrl: String?,
    artworkBitmap: ImageBitmap?,
    artworkSwipePreviewState: ArtworkSwipePreviewState,
    isTrackFavorited: Boolean,
    repeatMode: RepeatMode,
    playbackCapabilitiesFlags: Int,
    seekInProgress: Boolean,
    previousRestartsAfterThreshold: Boolean,
    onSeek: (Double) -> Unit,
    onPreviousTrack: () -> Unit,
    onForcePreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onPreviousSubtune: () -> Unit,
    onNextSubtune: () -> Unit,
    onOpenSubtuneSelector: () -> Unit,
    canPreviousSubtune: Boolean,
    canNextSubtune: Boolean,
    canOpenSubtuneSelector: Boolean,
    canOpenPlaylistSelector: Boolean,
    onOpenPlaylistSelector: () -> Unit,
    currentSubtuneIndex: Int,
    subtuneCount: Int,
    titleCurrentSubtuneIndex: Int,
    titleSubtuneCount: Int,
    subtuneTitleClickable: Boolean,
    onCycleRepeatMode: () -> Unit,
    canOpenCoreSettings: Boolean,
    onOpenCoreSettings: () -> Unit,
    visualizationMode: VisualizationMode,
    availableVisualizationModes: List<VisualizationMode>,
    onCycleVisualizationMode: () -> Unit,
    onSelectVisualizationMode: (VisualizationMode) -> Unit,
    onOpenVisualizationSettings: () -> Unit,
    onOpenSelectedVisualizationSettings: () -> Unit,
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
    artworkCornerRadiusDp: Int,
    onToggleFavoriteTrack: () -> Unit,
    onOpenAudioEffects: () -> Unit,
    filenameDisplayMode: FilenameDisplayMode,
    filenameOnlyWhenTitleMissing: Boolean,
    externalTrackInfoDialogRequestToken: Int,
    showFocusIndicators: Boolean,
    onHardwareNavigationInput: () -> Unit,
    onTouchInteraction: () -> Unit
) {
    val dragPreviewVisible =
        isPlayerSurfaceVisible && !isPlayerExpanded && miniExpandPreviewProgress > 0f
    val expandedOverlayVisible = isPlayerSurfaceVisible && isPlayerExpanded
    val overlayVisible = dragPreviewVisible || expandedOverlayVisible
    val noOp: () -> Unit = {}
    val noOpSeek: (Double) -> Unit = {}
    val noOpVisualizationModeSelect: (VisualizationMode) -> Unit = {}
    val expandedVisibilityState = remember { MutableTransitionState(false) }

    LaunchedEffect(overlayVisible) {
        expandedVisibilityState.targetState = overlayVisible
    }
    LaunchedEffect(expandedVisibilityState.currentState) {
        onExpandedOverlayCurrentVisibleChanged(expandedVisibilityState.currentState)
    }
    LaunchedEffect(
        expandedVisibilityState.isIdle,
        expandedVisibilityState.currentState,
        expandedVisibilityState.targetState,
        expandedOverlayVisible
    ) {
        onExpandedOverlaySettledVisibleChanged(
            expandedVisibilityState.isIdle &&
                expandedVisibilityState.currentState &&
                expandedVisibilityState.targetState &&
                expandedOverlayVisible
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            onExpandedOverlayCurrentVisibleChanged(false)
            onExpandedOverlaySettledVisibleChanged(false)
        }
    }

    LaunchedEffect(isPlayerSurfaceVisible, isPlayerExpanded) {
        if (!isPlayerSurfaceVisible || !isPlayerExpanded) {
            onCollapseDragProgressChanged(false)
        }
    }

    AnimatedVisibility(
        visibleState = expandedVisibilityState,
        enter = if (expandFromMiniDrag || dragPreviewVisible) {
            EnterTransition.None
        } else {
            slideInVertically(
                initialOffsetY = { it / 3 },
                animationSpec = tween(durationMillis = 320, easing = LinearOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = 240)) + scaleIn(
                initialScale = 0.96f,
                animationSpec = tween(durationMillis = 320, easing = LinearOutSlowInEasing)
            )
        },
        exit = if (collapseFromSwipe) {
            fadeOut(animationSpec = tween(1))
        } else if (dragPreviewVisible) {
            fadeOut(animationSpec = tween(1))
        } else {
            slideOutVertically(
                targetOffsetY = { it / 4 },
                animationSpec = tween(durationMillis = 320, easing = LinearOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = 250))
        }
    ) {
        val previewProgress = miniExpandPreviewProgress.coerceIn(0f, 1f)
        val previewMode = !expandedOverlayVisible && previewProgress > 0f
        val previewOffsetPx = (1f - previewProgress) * screenHeightPx
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (previewMode) {
                        translationY = previewOffsetPx
                        alpha = previewProgress
                    }
                }
                .pointerInteropFilter { event ->
                    if (expandedOverlayVisible && event.actionMasked == MotionEvent.ACTION_DOWN) {
                        onTouchInteraction()
                    }
                    false
                }
                .onPreviewKeyEvent { keyEvent ->
                    if (!expandedOverlayVisible || keyEvent.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent false
                    }
                    if (
                        keyEvent.key == Key.DirectionLeft ||
                        keyEvent.key == Key.DirectionRight ||
                        keyEvent.key == Key.DirectionUp ||
                        keyEvent.key == Key.DirectionDown ||
                        keyEvent.key == Key.DirectionCenter ||
                        keyEvent.key == Key.Enter ||
                        keyEvent.key == Key.NumPadEnter ||
                        keyEvent.key == Key.Tab
                    ) {
                        onHardwareNavigationInput()
                    }
                    false
                }
        ) {
            CompositionLocalProvider(LocalPlayerFocusIndicatorsEnabled provides showFocusIndicators) {
                PlayerScreen(
                    file = selectedFile,
                    onBack = if (expandedOverlayVisible) {
                        {
                            onCollapseFromSwipeChanged(false)
                            onCollapseDragProgressChanged(false)
                            onMiniExpandPreviewProgressChanged(0f)
                            onPlayerExpandedChanged(false)
                        }
                    } else {
                        noOp
                    },
                    onCollapseBySwipe = if (expandedOverlayVisible) {
                        {
                            onCollapseFromSwipeChanged(true)
                            onCollapseDragProgressChanged(false)
                            onMiniExpandPreviewProgressChanged(0f)
                            onPlayerExpandedChanged(false)
                        }
                    } else {
                        noOp
                    },
                    isPlaying = isPlaying,
                    canResumeStoppedTrack = if (expandedOverlayVisible) canResumeStoppedTrack else false,
                    onPlay = if (expandedOverlayVisible) onPlay else noOp,
                    onPause = if (expandedOverlayVisible) onPause else noOp,
                    onStopAndClear = if (expandedOverlayVisible) onStopAndClear else noOp,
                    durationSeconds = durationSeconds,
                    // Read inside AnimatedVisibility so this host only subscribes
                    // to position updates while the expanded overlay is composing.
                    positionSeconds = positionSecondsState.value,
                    canPreviousTrack = canPreviousTrack,
                    canNextTrack = canNextTrack,
                    title = title,
                    artist = artist,
                    sampleRateHz = sampleRateHz,
                    channelCount = channelCount,
                    bitDepthLabel = bitDepthLabel,
                    decoderName = decoderName,
                    playbackSourceLabel = playbackSourceLabel,
                    pathOrUrl = pathOrUrl,
                    playlistTitle = playlistTitle,
                    playlistFormatLabel = playlistFormatLabel,
                    playlistTrackCount = playlistTrackCount,
                    playlistPathOrUrl = playlistPathOrUrl,
                    artwork = artworkBitmap,
                    artworkSwipePreviewState = artworkSwipePreviewState,
                    isTrackFavorited = isTrackFavorited,
                    noArtworkIcon = placeholderArtworkIconForFile(selectedFile, decoderName),
                    repeatMode = repeatMode,
                    canCycleRepeatMode = supportsLiveRepeatMode(playbackCapabilitiesFlags),
                    canSeek = canSeekPlayback(playbackCapabilitiesFlags),
                    hasReliableDuration = hasReliableDuration(playbackCapabilitiesFlags),
                    playbackStartInProgress = playbackStartInProgress,
                    seekInProgress = seekInProgress,
                    previousRestartsAfterThreshold = previousRestartsAfterThreshold,
                    onSeek = if (expandedOverlayVisible) onSeek else noOpSeek,
                    onPreviousTrack = if (expandedOverlayVisible) onPreviousTrack else noOp,
                    onForcePreviousTrack = if (expandedOverlayVisible) onForcePreviousTrack else noOp,
                    onNextTrack = if (expandedOverlayVisible) onNextTrack else noOp,
                    onPreviousSubtune = if (expandedOverlayVisible) onPreviousSubtune else noOp,
                    onNextSubtune = if (expandedOverlayVisible) onNextSubtune else noOp,
                    onOpenSubtuneSelector = if (expandedOverlayVisible) onOpenSubtuneSelector else noOp,
                    canPreviousSubtune = if (expandedOverlayVisible) canPreviousSubtune else false,
                    canNextSubtune = if (expandedOverlayVisible) canNextSubtune else false,
                    canOpenSubtuneSelector = if (expandedOverlayVisible) canOpenSubtuneSelector else false,
                    canOpenPlaylistSelector = if (expandedOverlayVisible) canOpenPlaylistSelector else false,
                    onOpenPlaylistSelector = if (expandedOverlayVisible) onOpenPlaylistSelector else noOp,
                    currentSubtuneIndex = currentSubtuneIndex,
                    subtuneCount = subtuneCount,
                    titleCurrentSubtuneIndex = titleCurrentSubtuneIndex,
                    titleSubtuneCount = titleSubtuneCount,
                    subtuneTitleClickable = if (expandedOverlayVisible) subtuneTitleClickable else false,
                    onCycleRepeatMode = if (expandedOverlayVisible) onCycleRepeatMode else noOp,
                    canOpenCoreSettings = if (expandedOverlayVisible) canOpenCoreSettings else false,
                    onOpenCoreSettings = if (expandedOverlayVisible) onOpenCoreSettings else noOp,
                    visualizationMode = visualizationMode,
                    availableVisualizationModes = availableVisualizationModes,
                    onCycleVisualizationMode = if (expandedOverlayVisible) onCycleVisualizationMode else noOp,
                    onSelectVisualizationMode = if (expandedOverlayVisible) onSelectVisualizationMode else noOpVisualizationModeSelect,
                    onOpenVisualizationSettings = if (expandedOverlayVisible) onOpenVisualizationSettings else noOp,
                    onOpenSelectedVisualizationSettings = if (expandedOverlayVisible) onOpenSelectedVisualizationSettings else noOp,
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
                    visualizationShowDebugInfo = visualizationShowDebugInfo,
                    artworkCornerRadiusDp = artworkCornerRadiusDp,
                    onToggleFavoriteTrack = if (expandedOverlayVisible) onToggleFavoriteTrack else noOp,
                    onOpenAudioEffects = if (expandedOverlayVisible) onOpenAudioEffects else noOp,
                    filenameDisplayMode = filenameDisplayMode,
                    filenameOnlyWhenTitleMissing = filenameOnlyWhenTitleMissing,
                    externalTrackInfoDialogRequestToken = externalTrackInfoDialogRequestToken,
                    onCollapseDragProgressChanged = onCollapseDragProgressChanged
                )
            }
        }
    }
}
