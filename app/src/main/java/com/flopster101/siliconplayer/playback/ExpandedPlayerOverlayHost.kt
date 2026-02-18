package com.flopster101.siliconplayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import com.flopster101.siliconplayer.ui.screens.PlayerScreen
import java.io.File

@Composable
internal fun ExpandedPlayerOverlayHost(
    isPlayerSurfaceVisible: Boolean,
    isPlayerExpanded: Boolean,
    expandFromMiniDrag: Boolean,
    collapseFromSwipe: Boolean,
    onCollapseFromSwipeChanged: (Boolean) -> Unit,
    onMiniExpandPreviewProgressChanged: (Float) -> Unit,
    onPlayerExpandedChanged: (Boolean) -> Unit,
    selectedFile: File?,
    isPlaying: Boolean,
    canResumeStoppedTrack: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStopAndClear: () -> Unit,
    durationSeconds: Double,
    positionSeconds: Double,
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
    artworkBitmap: ImageBitmap?,
    repeatMode: RepeatMode,
    playbackCapabilitiesFlags: Int,
    seekInProgress: Boolean,
    onSeek: (Double) -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onPreviousSubtune: () -> Unit,
    onNextSubtune: () -> Unit,
    onOpenSubtuneSelector: () -> Unit,
    canPreviousSubtune: Boolean,
    canNextSubtune: Boolean,
    canOpenSubtuneSelector: Boolean,
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
    onOpenAudioEffects: () -> Unit,
    filenameDisplayMode: FilenameDisplayMode,
    filenameOnlyWhenTitleMissing: Boolean
) {
    AnimatedVisibility(
        visible = isPlayerSurfaceVisible && isPlayerExpanded,
        enter = if (expandFromMiniDrag) {
            EnterTransition.None
        } else {
            slideInVertically(initialOffsetY = { it / 3 }) + fadeIn() + scaleIn(initialScale = 0.96f)
        },
        exit = if (collapseFromSwipe) {
            fadeOut(animationSpec = tween(120))
        } else {
            slideOutVertically(targetOffsetY = { it / 3 }) + fadeOut()
        }
    ) {
        PlayerScreen(
            file = selectedFile,
            onBack = {
                onCollapseFromSwipeChanged(false)
                onMiniExpandPreviewProgressChanged(0f)
                onPlayerExpandedChanged(false)
            },
            onCollapseBySwipe = {
                onCollapseFromSwipeChanged(true)
                onMiniExpandPreviewProgressChanged(0f)
                onPlayerExpandedChanged(false)
            },
            isPlaying = isPlaying,
            canResumeStoppedTrack = canResumeStoppedTrack,
            onPlay = onPlay,
            onPause = onPause,
            onStopAndClear = onStopAndClear,
            durationSeconds = durationSeconds,
            positionSeconds = positionSeconds,
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
            artwork = artworkBitmap,
            noArtworkIcon = placeholderArtworkIconForFile(selectedFile),
            repeatMode = repeatMode,
            canCycleRepeatMode = supportsLiveRepeatMode(playbackCapabilitiesFlags),
            canSeek = canSeekPlayback(playbackCapabilitiesFlags),
            hasReliableDuration = hasReliableDuration(playbackCapabilitiesFlags),
            seekInProgress = seekInProgress,
            onSeek = onSeek,
            onPreviousTrack = onPreviousTrack,
            onNextTrack = onNextTrack,
            onPreviousSubtune = onPreviousSubtune,
            onNextSubtune = onNextSubtune,
            onOpenSubtuneSelector = onOpenSubtuneSelector,
            canPreviousSubtune = canPreviousSubtune,
            canNextSubtune = canNextSubtune,
            canOpenSubtuneSelector = canOpenSubtuneSelector,
            onCycleRepeatMode = onCycleRepeatMode,
            canOpenCoreSettings = canOpenCoreSettings,
            onOpenCoreSettings = onOpenCoreSettings,
            visualizationMode = visualizationMode,
            availableVisualizationModes = availableVisualizationModes,
            onCycleVisualizationMode = onCycleVisualizationMode,
            onSelectVisualizationMode = onSelectVisualizationMode,
            onOpenVisualizationSettings = onOpenVisualizationSettings,
            onOpenSelectedVisualizationSettings = onOpenSelectedVisualizationSettings,
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
            onOpenAudioEffects = onOpenAudioEffects,
            filenameDisplayMode = filenameDisplayMode,
            filenameOnlyWhenTitleMissing = filenameOnlyWhenTitleMissing
        )
    }
}
