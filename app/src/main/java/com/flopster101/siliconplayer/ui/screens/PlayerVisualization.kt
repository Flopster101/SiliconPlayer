package com.flopster101.siliconplayer.ui.screens

import android.content.Context
import android.os.Process
import android.hardware.display.DisplayManager
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.ArtworkSwipePreviewState
import com.flopster101.siliconplayer.AppDefaults
import com.flopster101.siliconplayer.ChannelScopeVisibleElementId
import com.flopster101.siliconplayer.DecoderNames
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.VisualizationChannelScopeLayout
import com.flopster101.siliconplayer.VisualizationChannelScopeTextAnchor
import com.flopster101.siliconplayer.VisualizationChannelScopeBackgroundMode
import com.flopster101.siliconplayer.VisualizationChannelScopeTextColorMode
import com.flopster101.siliconplayer.VisualizationChannelScopeTextFont
import com.flopster101.siliconplayer.VisualizationChannelScopeTriggerAlgorithm
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.VisualizationNoteNameFormat
import com.flopster101.siliconplayer.VisualizationOscColorMode
import com.flopster101.siliconplayer.VisualizationOscFpsMode
import com.flopster101.siliconplayer.VisualizationRenderBackend
import com.flopster101.siliconplayer.VisualizationVuAnchor
import com.flopster101.siliconplayer.isChannelScopeVisibleElementEnabled
import com.flopster101.siliconplayer.matchesDecoderName
import com.flopster101.siliconplayer.pluginNameForCoreName
import com.flopster101.siliconplayer.readChannelScopeVisibleElementSelection
import com.flopster101.siliconplayer.supportsChannelScopeVisualization
import com.flopster101.siliconplayer.visualizationRenderBackendForMode
import com.flopster101.siliconplayer.ui.visualization.basic.BasicVisualizationOverlay
import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeChannelTextState
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.locks.LockSupport
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class AlbumArtCrossfadeState(
    val trackKey: String?,
    val artwork: ImageBitmap?,
    val placeholderIcon: ImageVector
)

@Composable
private fun AlbumArtVisual(
    artwork: ImageBitmap?,
    placeholderIcon: ImageVector,
    modifier: Modifier = Modifier
) {
    if (artwork != null) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = artwork,
                contentDescription = "Album artwork",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        Box(
            modifier = modifier.background(
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

@Composable
private fun AlbumArtSurfaceContent(
    trackKey: String?,
    artwork: ImageBitmap?,
    placeholderIcon: ImageVector,
    crossfadeEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    if (!crossfadeEnabled) {
        AlbumArtVisual(
            artwork = artwork,
            placeholderIcon = placeholderIcon,
            modifier = modifier
        )
        return
    }
    val albumArtCrossfadeState = remember(trackKey, artwork, placeholderIcon) {
        AlbumArtCrossfadeState(
            trackKey = trackKey,
            artwork = artwork,
            placeholderIcon = placeholderIcon
        )
    }
    Crossfade(targetState = albumArtCrossfadeState, label = "albumArtCrossfade") { state ->
        AlbumArtVisual(
            artwork = state.artwork,
            placeholderIcon = state.placeholderIcon,
            modifier = modifier
        )
    }
}

@Composable
private fun VisualizationModeBadge(
    visualizationMode: VisualizationMode,
    visualizationModeBadgeText: String,
    badgeBackground: Color,
    badgeContentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = badgeBackground
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = when (visualizationMode) {
                    VisualizationMode.Off -> Icons.Default.GraphicEq
                    VisualizationMode.Bars -> Icons.Default.GraphicEq
                    VisualizationMode.Oscilloscope -> Icons.Default.MonitorHeart
                    VisualizationMode.VuMeters -> Icons.Default.Equalizer
                    VisualizationMode.ChannelScope -> Icons.Default.MonitorHeart
                },
                contentDescription = null,
                tint = badgeContentColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = visualizationModeBadgeText,
                style = MaterialTheme.typography.labelMedium,
                color = badgeContentColor
            )
        }
    }
}
@Composable
private fun StaticAlbumArtCard(
    file: File?,
    artwork: ImageBitmap?,
    placeholderIcon: ImageVector,
    artworkCornerRadiusDp: Int,
    visualizationMode: VisualizationMode,
    visualizationModeBadgeText: String,
    showVisualizationModeBadge: Boolean,
    crossfadeEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val artworkBrightness = remember(artwork) {
        val bitmap = artwork ?: return@remember null
        runCatching {
            val pixels = bitmap.toPixelMap()
            if (pixels.width <= 0 || pixels.height <= 0) return@runCatching 0.5f
            val stepX = maxOf(1, pixels.width / 32)
            val stepY = maxOf(1, pixels.height / 32)
            var sum = 0f
            var count = 0
            var y = 0
            while (y < pixels.height) {
                var x = 0
                while (x < pixels.width) {
                    sum += pixels[x, y].luminance()
                    count++
                    x += stepX
                }
                y += stepY
            }
            if (count > 0) (sum / count) else 0.5f
        }.getOrNull()
    }
    val badgeBackground = when {
        artworkBrightness == null -> MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
        artworkBrightness > 0.5f -> Color.Black.copy(alpha = 0.52f)
        else -> Color.White.copy(alpha = 0.4f)
    }
    val badgeContentColor = when {
        artworkBrightness == null -> MaterialTheme.colorScheme.onSurface
        artworkBrightness > 0.5f -> Color.White
        else -> Color.Black
    }

    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(artworkCornerRadiusDp.coerceIn(0, 48).dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AlbumArtSurfaceContent(
                trackKey = file?.absolutePath,
                artwork = artwork,
                placeholderIcon = placeholderIcon,
                crossfadeEnabled = crossfadeEnabled,
                modifier = Modifier.fillMaxSize()
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = showVisualizationModeBadge,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp),
                enter = fadeIn(animationSpec = tween(170)),
                exit = fadeOut(animationSpec = tween(260))
            ) {
                VisualizationModeBadge(
                    visualizationMode = visualizationMode,
                    visualizationModeBadgeText = visualizationModeBadgeText,
                    badgeBackground = badgeBackground,
                    badgeContentColor = badgeContentColor
                )
            }
        }
    }
}

@Composable
private fun SwipeableArtworkContainer(
    currentTrackKey: String?,
    swipePreviewState: ArtworkSwipePreviewState,
    artworkCornerRadiusDp: Int,
    visualizationMode: VisualizationMode,
    modifier: Modifier = Modifier,
    onSwipePreviousTrack: () -> Unit,
    onSwipeNextTrack: () -> Unit,
    currentContent: @Composable BoxScope.() -> Unit
) {
    val canSwipePrevious = swipePreviewState.canSwipePrevious
    val canSwipeNext = swipePreviewState.canSwipeNext
    val widthDependentGestureEnabled = canSwipePrevious || canSwipeNext
    val latestCurrentTrackKey by rememberUpdatedState(currentTrackKey)
    var containerWidthPx by remember { mutableIntStateOf(0) }
    var swipeOffsetPx by remember(currentTrackKey) { mutableFloatStateOf(0f) }
    var pendingSwipeOffsetPx by remember(currentTrackKey) { mutableFloatStateOf(0f) }
    var swipeAnimating by remember(currentTrackKey) { mutableStateOf(false) }
    val uiScope = rememberCoroutineScope()
    val pageOffsetPx = remember(containerWidthPx) {
        if (containerWidthPx > 0) {
            containerWidthPx.toFloat() * 1.32f
        } else {
            0f
        }
    }

    LaunchedEffect(currentTrackKey) {
        if (!swipeAnimating) {
            swipeOffsetPx = 0f
            pendingSwipeOffsetPx = 0f
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { size ->
                containerWidthPx = size.width
            }
            .then(
                if (widthDependentGestureEnabled) {
                    Modifier.pointerInput(
                        currentTrackKey,
                        swipePreviewState,
                        containerWidthPx
                    ) {
                        if (containerWidthPx <= 0) return@pointerInput
                        fun clampSwipeOffset(candidate: Float): Float {
                            val minOffset = if (canSwipeNext) -pageOffsetPx else 0f
                            val maxOffset = if (canSwipePrevious) pageOffsetPx else 0f
                            return candidate.coerceIn(minOffset, maxOffset)
                        }
                        val armThresholdPx = maxOf(
                            viewConfiguration.touchSlop * 2.25f,
                            containerWidthPx * 0.06f
                        )
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                if (swipeAnimating) return@detectHorizontalDragGestures
                                pendingSwipeOffsetPx += dragAmount
                                if (swipeOffsetPx == 0f && abs(pendingSwipeOffsetPx) < armThresholdPx) {
                                    return@detectHorizontalDragGestures
                                }
                                swipeOffsetPx = clampSwipeOffset(pendingSwipeOffsetPx)
                                change.consume()
                            },
                            onDragEnd = {
                                if (swipeAnimating || containerWidthPx <= 0) return@detectHorizontalDragGestures
                                val thresholdPx = containerWidthPx * 0.24f
                                val commitDirection = when {
                                    swipeOffsetPx >= thresholdPx && canSwipePrevious -> -1
                                    swipeOffsetPx <= -thresholdPx && canSwipeNext -> 1
                                    else -> 0
                                }
                                pendingSwipeOffsetPx = 0f
                                swipeAnimating = true
                                uiScope.launch {
                                    val targetOffsetPx = when (commitDirection) {
                                        -1 -> pageOffsetPx
                                        1 -> -pageOffsetPx
                                        else -> 0f
                                    }
                                    animate(
                                        initialValue = swipeOffsetPx,
                                        targetValue = targetOffsetPx,
                                        animationSpec = tween(
                                            durationMillis = if (commitDirection == 0) 210 else 240,
                                            easing = LinearOutSlowInEasing
                                        )
                                    ) { value, _ ->
                                        swipeOffsetPx = value
                                    }
                                    val targetTrackKey = when (commitDirection) {
                                        -1 -> swipePreviewState.previousTrackKey
                                        1 -> swipePreviewState.nextTrackKey
                                        else -> null
                                    }
                                    when (commitDirection) {
                                        -1 -> onSwipePreviousTrack()
                                        1 -> onSwipeNextTrack()
                                    }
                                    if (targetTrackKey != null) {
                                        for (frame in 0 until 18) {
                                            if (latestCurrentTrackKey == targetTrackKey) {
                                                break
                                            }
                                            withFrameNanos { }
                                        }
                                    }
                                    swipeOffsetPx = 0f
                                    swipeAnimating = false
                                }
                            },
                            onDragCancel = {
                                if (swipeAnimating) return@detectHorizontalDragGestures
                                pendingSwipeOffsetPx = 0f
                                swipeAnimating = true
                                uiScope.launch {
                                    animate(
                                        initialValue = swipeOffsetPx,
                                        targetValue = 0f,
                                        animationSpec = tween(
                                            durationMillis = 210,
                                            easing = LinearOutSlowInEasing
                                        )
                                    ) { value, _ ->
                                        swipeOffsetPx = value
                                    }
                                    swipeAnimating = false
                                }
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        if (canSwipePrevious) {
            StaticAlbumArtCard(
                file = swipePreviewState.previousTrackKey?.let(::File),
                artwork = swipePreviewState.previousArtwork,
                placeholderIcon = swipePreviewState.previousPlaceholderIcon,
                artworkCornerRadiusDp = artworkCornerRadiusDp,
                visualizationMode = visualizationMode,
                visualizationModeBadgeText = "",
                showVisualizationModeBadge = false,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        translationX = swipeOffsetPx - pageOffsetPx
                    }
            )
        }
        if (canSwipeNext) {
            StaticAlbumArtCard(
                file = swipePreviewState.nextTrackKey?.let(::File),
                artwork = swipePreviewState.nextArtwork,
                placeholderIcon = swipePreviewState.nextPlaceholderIcon,
                artworkCornerRadiusDp = artworkCornerRadiusDp,
                visualizationMode = visualizationMode,
                visualizationModeBadgeText = "",
                showVisualizationModeBadge = false,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        translationX = swipeOffsetPx + pageOffsetPx
                    }
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    translationX = swipeOffsetPx
                }
        ) {
            currentContent()
        }
    }
}

private class VisualizationDebugAccumulator {
    var frameCount: Int = 0
    var windowStartNs: Long = 0L
    var lastFrameNs: Long = 0L
    var latestFrameMs: Int = 0
    var lastUiPublishNs: Long = 0L
}

private class VisualizationSourceDebugAccumulator {
    var lastSignature: Long = Long.MIN_VALUE
    var updateCount: Int = 0
    var uniqueCount: Int = 0
    var windowStartNs: Long = 0L
    var lastUniqueFrameNs: Long = 0L
    var latestUniqueFrameMs: Int = 0
    var lastUiPublishNs: Long = 0L
}

private fun mixSignature(hash: Long, value: Long): Long {
    var h = hash xor value
    h *= 1099511628211L
    return h
}

private fun sampledFloatArraySignature(
    values: FloatArray?,
    maxSamples: Int = 48
): Long {
    if (values == null || values.isEmpty()) return -3750763034362895579L
    var hash = mixSignature(1469598103934665603L, values.size.toLong())
    val sampleCount = maxSamples.coerceIn(1, values.size)
    val step = if (sampleCount <= 1) 0f else (values.size - 1).toFloat() / (sampleCount - 1).toFloat()
    for (i in 0 until sampleCount) {
        val idx = if (sampleCount <= 1) 0 else (i.toFloat() * step).toInt().coerceIn(0, values.lastIndex)
        val bits = java.lang.Float.floatToRawIntBits(values[idx]).toLong() and 0xFFFF_FFFFL
        hash = mixSignature(hash, bits)
    }
    return hash
}

private fun sampledIntArraySignature(
    values: IntArray?,
    maxSamples: Int = 24
): Long {
    if (values == null || values.isEmpty()) return 3827332896879027647L
    var hash = mixSignature(1469598103934665603L, values.size.toLong())
    val sampleCount = maxSamples.coerceIn(1, values.size)
    val step = if (sampleCount <= 1) 0f else (values.size - 1).toFloat() / (sampleCount - 1).toFloat()
    for (i in 0 until sampleCount) {
        val idx = if (sampleCount <= 1) 0 else (i.toFloat() * step).toInt().coerceIn(0, values.lastIndex)
        hash = mixSignature(hash, values[idx].toLong())
    }
    return hash
}

private fun sampledChannelScopeSignature(
    histories: List<FloatArray>?,
    triggerIndices: IntArray?
): Long {
    if (histories.isNullOrEmpty()) return -5442059136133378759L
    var hash = mixSignature(1469598103934665603L, histories.size.toLong())
    val sampleChannels = 8.coerceAtMost(histories.size)
    val channelStep = if (sampleChannels <= 1) 0f else (histories.size - 1).toFloat() / (sampleChannels - 1).toFloat()
    for (i in 0 until sampleChannels) {
        val channel = if (sampleChannels <= 1) 0 else (i.toFloat() * channelStep).toInt().coerceIn(0, histories.lastIndex)
        hash = mixSignature(hash, channel.toLong())
        hash = mixSignature(hash, sampledFloatArraySignature(histories[channel], maxSamples = 24))
        val trigger = triggerIndices?.getOrElse(channel) { -1 } ?: -1
        hash = mixSignature(hash, trigger.toLong())
    }
    return hash
}

private fun snapshotSourceSignature(
    mode: VisualizationMode,
    snapshot: VisualizationSnapshot
): Long? {
    return when (mode) {
        VisualizationMode.Off -> null
        VisualizationMode.Oscilloscope -> {
            var hash = mixSignature(1469598103934665603L, 1L)
            hash = mixSignature(hash, sampledFloatArraySignature(snapshot.waveLeft, maxSamples = 64))
            hash = mixSignature(hash, sampledFloatArraySignature(snapshot.waveRight, maxSamples = 64))
            hash = mixSignature(hash, (snapshot.channelCount ?: 0).toLong())
            hash
        }
        VisualizationMode.Bars -> {
            mixSignature(
                mixSignature(1469598103934665603L, 2L),
                sampledFloatArraySignature(snapshot.bars, maxSamples = 48)
            )
        }
        VisualizationMode.VuMeters -> {
            var hash = mixSignature(1469598103934665603L, 3L)
            hash = mixSignature(hash, sampledFloatArraySignature(snapshot.vu, maxSamples = 8))
            hash = mixSignature(hash, (snapshot.channelCount ?: 0).toLong())
            hash
        }
        VisualizationMode.ChannelScope -> {
            var hash = mixSignature(1469598103934665603L, 4L)
            hash = mixSignature(
                hash,
                sampledChannelScopeSignature(
                    histories = snapshot.channelScopeHistories,
                    triggerIndices = snapshot.channelScopeTriggerIndices
                )
            )
            hash = mixSignature(hash, sampledIntArraySignature(snapshot.channelScopeTextRaw, maxSamples = 32))
            hash
        }
    }
}

private fun contextDisplayRefreshRateHz(context: Context): Float {
    val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    val activeDisplayRate = displayManager
        ?.displays
        ?.asSequence()
        ?.map { it.refreshRate }
        ?.firstOrNull { it.isFinite() && it > 0f }
    val rate = activeDisplayRate ?: context.display?.refreshRate ?: 60f
    return if (rate.isFinite() && rate > 0f) rate else 60f
}

private fun extractArtworkAccentColor(artwork: ImageBitmap?): Color? {
    if (artwork == null) return null
    val pixels = artwork.toPixelMap()
    val width = pixels.width
    val height = pixels.height
    if (width <= 0 || height <= 0) return null

    val stepX = maxOf(1, width / 32)
    val stepY = maxOf(1, height / 32)
    var weightedR = 0.0
    var weightedG = 0.0
    var weightedB = 0.0
    var weightSum = 0.0
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val c = pixels[x, y]
            val r = c.red.toDouble()
            val g = c.green.toDouble()
            val b = c.blue.toDouble()
            val maxCh = maxOf(r, maxOf(g, b))
            val minCh = minOf(r, minOf(g, b))
            val sat = if (maxCh <= 1e-6) 0.0 else (maxCh - minCh) / maxCh
            val value = maxCh
            val weight = (0.2 + (sat * 0.8)) * (0.3 + (value * 0.7))
            weightedR += r * weight
            weightedG += g * weight
            weightedB += b * weight
            weightSum += weight
            x += stepX
        }
        y += stepY
    }
    if (weightSum <= 1e-6) return null
    return Color(
        red = (weightedR / weightSum).toFloat().coerceIn(0f, 1f),
        green = (weightedG / weightSum).toFloat().coerceIn(0f, 1f),
        blue = (weightedB / weightSum).toFloat().coerceIn(0f, 1f),
        alpha = 1f
    )
}

private fun computeChannelScopeSampleCount(
    windowMs: Int,
    sampleRateHz: Int
): Int {
    val clampedWindowMs = windowMs.coerceIn(5, 200)
    val effectiveSampleRate = sampleRateHz.coerceIn(8_000, 192_000)
    val requested = ((clampedWindowMs.toFloat() / 1000f) * effectiveSampleRate.toFloat()).roundToInt()
    // Bound to native ring-buffer budget while still scaling with the UI window setting.
    return requested.coerceIn(128, 8192)
}

private fun computeVisualizationPollIntervalNs(
    isPlaying: Boolean,
    visualizationMode: VisualizationMode,
    visualizationOscFpsMode: VisualizationOscFpsMode,
    visualizationBarFpsMode: VisualizationOscFpsMode,
    visualizationVuFpsMode: VisualizationOscFpsMode,
    channelScopeFpsMode: VisualizationOscFpsMode,
    displayRefreshHz: Float
): Long {
    if (!isPlaying) return 90_000_000L
    val fps = when (visualizationMode) {
        VisualizationMode.Oscilloscope -> {
            when (visualizationOscFpsMode) {
                VisualizationOscFpsMode.Default -> 30f
                VisualizationOscFpsMode.Fps60 -> 60f
                VisualizationOscFpsMode.NativeRefresh -> displayRefreshHz.coerceAtLeast(30f)
            }
        }
        VisualizationMode.ChannelScope -> {
            when (channelScopeFpsMode) {
                VisualizationOscFpsMode.Default -> 30f
                VisualizationOscFpsMode.Fps60 -> 60f
                VisualizationOscFpsMode.NativeRefresh -> displayRefreshHz.coerceAtLeast(30f)
            }
        }
        VisualizationMode.Bars,
        VisualizationMode.VuMeters -> {
            val fpsMode = if (visualizationMode == VisualizationMode.Bars) {
                visualizationBarFpsMode
            } else {
                visualizationVuFpsMode
            }
            when (fpsMode) {
                VisualizationOscFpsMode.Default -> 30f
                VisualizationOscFpsMode.Fps60 -> 60f
                VisualizationOscFpsMode.NativeRefresh -> displayRefreshHz.coerceAtLeast(30f)
            }
        }
        else -> 30f
    }.coerceAtLeast(1f)
    return (1_000_000_000.0 / fps.toDouble()).roundToInt().toLong().coerceAtLeast(4_000_000L)
}

private data class VisualizationSnapshot(
    val waveLeft: FloatArray? = null,
    val waveRight: FloatArray? = null,
    val bars: FloatArray? = null,
    val vu: FloatArray? = null,
    val channelCount: Int? = null,
    val channelScopeHistories: List<FloatArray>? = null,
    val channelScopeTriggerIndices: IntArray? = null,
    val channelScopeLastChannelCount: Int? = null,
    val channelScopeTextRaw: IntArray? = null
)

private fun resolveChannelScopeGridForSnapshot(
    channels: Int,
    layout: VisualizationChannelScopeLayout
): Pair<Int, Int> {
    if (channels <= 1) return 1 to 1
    return when (layout) {
        VisualizationChannelScopeLayout.ColumnFirst -> {
            val targetRowsPerColumn = 7
            val columns = if (channels <= 4) {
                1
            } else {
                ceil(channels / targetRowsPerColumn.toDouble()).toInt().coerceAtLeast(2)
            }
            val rows = ceil(channels / columns.toDouble()).toInt().coerceAtLeast(1)
            columns to rows
        }
        VisualizationChannelScopeLayout.BalancedTwoColumn -> {
            val columns = ceil(kotlin.math.sqrt(channels.toDouble())).toInt().coerceAtLeast(1)
            val rows = ceil(channels / columns.toDouble()).toInt().coerceAtLeast(1)
            columns to rows
        }
    }
}

private fun buildScopeDisplayGridPermutation(
    channels: Int,
    layout: VisualizationChannelScopeLayout
): IntArray {
    if (channels <= 1) return IntArray(channels) { it }
    val (columns, rows) = resolveChannelScopeGridForSnapshot(channels, layout)
    val permutation = IntArray(channels) { it }
    var linearIndex = 0
    for (row in 0 until rows) {
        for (col in 0 until columns) {
            if (linearIndex >= channels) {
                return permutation
            }
            val displayIndex = (col * rows) + row
            if (displayIndex >= channels) {
                continue
            }
            permutation[displayIndex] = linearIndex
            linearIndex += 1
        }
    }
    return permutation
}

private fun remapChannelScopeHistoriesForDisplay(
    histories: List<FloatArray>,
    layout: VisualizationChannelScopeLayout
): List<FloatArray> {
    if (histories.size <= 1) return histories
    val permutation = buildScopeDisplayGridPermutation(histories.size, layout)
    return List(histories.size) { displayIndex ->
        histories[permutation[displayIndex]]
    }
}

private fun remapChannelScopeTriggersForDisplay(
    triggerIndices: IntArray,
    channels: Int,
    layout: VisualizationChannelScopeLayout
): IntArray {
    if (channels <= 1 || triggerIndices.isEmpty()) return triggerIndices
    val permutation = buildScopeDisplayGridPermutation(channels, layout)
    return IntArray(channels) { displayIndex ->
        triggerIndices.getOrElse(permutation[displayIndex]) { 0 }
    }
}

private fun remapChannelScopeTextRawForDisplay(
    flat: IntArray,
    layout: VisualizationChannelScopeLayout
): IntArray {
    val stride = NativeBridge.CHANNEL_SCOPE_TEXT_STATE_STRIDE
    if (stride <= 0 || flat.isEmpty()) return flat
    val channels = flat.size / stride
    if (channels <= 1) return flat
    val permutation = buildScopeDisplayGridPermutation(channels, layout)
    val reordered = IntArray(channels * stride)
    for (displayIndex in 0 until channels) {
        val sourceChannel = permutation[displayIndex]
        val sourceBase = sourceChannel * stride
        val destBase = displayIndex * stride
        for (offset in 0 until stride) {
            reordered[destBase + offset] = flat.getOrElse(sourceBase + offset) { -1 }
        }
    }
    return reordered
}

private fun readVisualizationSnapshot(
    visualizationMode: VisualizationMode,
    decoderName: String?,
    visualizationOscWindowMs: Int,
    visualizationOscTriggerModeNative: Int,
    channelScopeWindowMs: Int,
    channelScopeDcRemovalEnabled: Boolean,
    channelScopeGainPercent: Int,
    channelScopeTriggerModeNative: Int,
    channelScopeTriggerAlgorithmNative: Int,
    channelScopeLayout: VisualizationChannelScopeLayout,
    channelScopeTriggerStates: MutableList<ChannelScopeTriggerState>,
    sampleRateHz: Int,
    shouldPollChannelScopeText: Boolean
): VisualizationSnapshot? {
    if (NativeBridge.isSeekInProgress()) {
        return null
    }
    return when (visualizationMode) {
        VisualizationMode.Oscilloscope -> {
            val channelCount = NativeBridge.getVisualizationChannelCount().coerceAtLeast(1)
            VisualizationSnapshot(
                waveLeft = NativeBridge.getVisualizationWaveformScope(
                    0,
                    visualizationOscWindowMs,
                    visualizationOscTriggerModeNative
                ),
                waveRight = NativeBridge.getVisualizationWaveformScope(
                    1,
                    visualizationOscWindowMs,
                    visualizationOscTriggerModeNative
                ),
                channelCount = channelCount
            )
        }
        VisualizationMode.Bars -> {
            VisualizationSnapshot(
                bars = NativeBridge.getVisualizationBars()
            )
        }
        VisualizationMode.VuMeters -> {
            VisualizationSnapshot(
                vu = NativeBridge.getVisualizationVuLevels(),
                channelCount = NativeBridge.getVisualizationChannelCount().coerceAtLeast(1)
            )
        }
        VisualizationMode.ChannelScope -> {
            if (!supportsChannelScopeVisualization(decoderName)) {
                VisualizationSnapshot()
            } else {
                val scopeSamples = computeChannelScopeSampleCount(
                    windowMs = channelScopeWindowMs,
                    sampleRateHz = sampleRateHz
                )
                // Accurate mode needs a wider buffer for the correlation kernel.
                val bufferMultiplier = if (channelScopeTriggerAlgorithmNative == 1) 2.25f else 1.5f
                val nativeSamples = ((scopeSamples * bufferMultiplier).toInt()).coerceIn(scopeSamples, 8192)
                val channelScopesFlat = NativeBridge.getChannelScopeSamples(nativeSamples)
                val fullHistories = if (nativeSamples > 0 && channelScopesFlat.size >= nativeSamples) {
                    buildChannelScopeHistories(
                        flatScopes = channelScopesFlat,
                        points = nativeSamples,
                        dcRemovalEnabled = channelScopeDcRemovalEnabled,
                        gainPercent = channelScopeGainPercent
                    )
                } else {
                    emptyList()
                }
                val rawTriggerIndices = if (fullHistories.isNotEmpty() && channelScopeTriggerModeNative != 0) {
                    // Re-flatten processed histories for native trigger.
                    val flatProcessed = FloatArray(fullHistories.size * nativeSamples)
                    for (ch in fullHistories.indices) {
                        val h = fullHistories[ch]
                        System.arraycopy(h, 0, flatProcessed, ch * nativeSamples, h.size.coerceAtMost(nativeSamples))
                    }
                    val result = NativeBridge.computeChannelScopeTriggers(
                        flatScopeData = flatProcessed,
                        samplesPerChannel = nativeSamples,
                        numChannels = fullHistories.size,
                        triggerModeNative = channelScopeTriggerModeNative,
                        algorithmMode = channelScopeTriggerAlgorithmNative
                    )
                    result
                } else {
                    IntArray(fullHistories.size) { fullHistories[it].size / 2 }
                }
                // Extract display-sized windows centered on each channel's trigger.
                val displayHalf = scopeSamples / 2
                val histories = fullHistories.mapIndexed { ch, full ->
                    if (full.size <= scopeSamples) {
                        full
                    } else {
                        val t = rawTriggerIndices.getOrElse(ch) { full.size / 2 }
                        val start = (t - displayHalf).coerceIn(0, full.size - scopeSamples)
                        full.copyOfRange(start, start + scopeSamples)
                    }
                }
                // Remap trigger indices into the extracted windows (always near center).
                val triggerIndices = IntArray(histories.size) { ch ->
                    val full = fullHistories.getOrNull(ch)
                    if (full == null || full.size <= scopeSamples) {
                        histories.getOrNull(ch)?.let { it.size / 2 } ?: 0
                    } else {
                        val t = rawTriggerIndices.getOrElse(ch) { full.size / 2 }
                        val start = (t - displayHalf).coerceIn(0, full.size - scopeSamples)
                        t - start
                    }
                }
                val shouldRemapForDisplay =
                    decoderName.matchesDecoderName(DecoderNames.GAME_MUSIC_EMU)
                val displayHistories = if (shouldRemapForDisplay) {
                    remapChannelScopeHistoriesForDisplay(
                        histories = histories,
                        layout = channelScopeLayout
                    )
                } else {
                    histories
                }
                val displayTriggerIndices = if (shouldRemapForDisplay) {
                    remapChannelScopeTriggersForDisplay(
                        triggerIndices = triggerIndices,
                        channels = histories.size,
                        layout = channelScopeLayout
                    )
                } else {
                    triggerIndices
                }
                val lastChannelCount = histories.size.coerceIn(1, 64)
                val scopeChannels = if (nativeSamples > 0) {
                    (channelScopesFlat.size / nativeSamples).coerceIn(1, 64)
                } else {
                    NativeBridge.getVisualizationChannelCount().coerceAtLeast(1).coerceIn(1, 64)
                }
                VisualizationSnapshot(
                    channelScopeHistories = displayHistories,
                    channelScopeTriggerIndices = displayTriggerIndices,
                    channelScopeLastChannelCount = lastChannelCount,
                    channelScopeTextRaw = if (shouldPollChannelScopeText) {
                        val raw = NativeBridge.getChannelScopeTextState(scopeChannels)
                        if (shouldRemapForDisplay) {
                            remapChannelScopeTextRawForDisplay(
                                flat = raw,
                                layout = channelScopeLayout
                            )
                        } else {
                            raw
                        }
                    } else {
                        null
                    }
                )
            }
        }
        VisualizationMode.Off -> VisualizationSnapshot()
    }
}

private fun sleepUntilTickNs(targetTickNs: Long) {
    while (true) {
        val remainingNs = targetTickNs - System.nanoTime()
        if (remainingNs <= 0L) return
        val parkNs = when {
            remainingNs >= 4_000_000L -> remainingNs - 1_000_000L
            remainingNs >= 1_000_000L -> remainingNs - 200_000L
            remainingNs >= 200_000L -> remainingNs - 50_000L
            else -> remainingNs
        }.coerceAtLeast(1_000L)
        LockSupport.parkNanos(parkNs)
    }
}

private fun createVisualizationUpdateDispatcher(): ExecutorCoroutineDispatcher {
    val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(
            {
                runCatching {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)
                }
                runnable.run()
            },
            "sp-vis-update"
        )
    }
    return executor.asCoroutineDispatcher()
}

private fun normalizeChannelScopeChannel(
    flatScopes: FloatArray,
    start: Int,
    points: Int,
    dcRemovalEnabled: Boolean,
    gainPercent: Int
): FloatArray {
    val centered = FloatArray(points)
    val fixedGain = (gainPercent.coerceIn(25, 600).toFloat() / 100f)
    if (!dcRemovalEnabled) {
        for (i in 0 until points) {
            val sample = flatScopes[start + i].coerceIn(-1f, 1f)
            centered[i] = (sample * fixedGain).coerceIn(-1f, 1f)
        }
        return centered
    }
    var sum = 0f
    var minSample = 1f
    var maxSample = -1f
    for (i in 0 until points) {
        val sample = flatScopes[start + i].coerceIn(-1f, 1f)
        sum += sample
        if (sample < minSample) minSample = sample
        if (sample > maxSample) maxSample = sample
    }
    val frameMean = sum / points.toFloat()
    val peakMidpoint = (minSample + maxSample) * 0.5f
    val dcOffset = (frameMean * 0.7f) + (peakMidpoint * 0.3f)
    for (i in 0 until points) {
        val sample = flatScopes[start + i].coerceIn(-1f, 1f)
        centered[i] = ((sample - dcOffset) * fixedGain).coerceIn(-1f, 1f)
    }
    return centered
}

private fun buildChannelScopeHistories(
    flatScopes: FloatArray,
    points: Int,
    dcRemovalEnabled: Boolean,
    gainPercent: Int
): List<FloatArray> {
    if (points <= 0 || flatScopes.size < points) {
        return emptyList()
    }
    val channels = (flatScopes.size / points).coerceIn(1, 64)
    val histories = ArrayList<FloatArray>(channels)
    for (index in 0 until channels) {
        val start = index * points
        val end = (start + points).coerceAtMost(flatScopes.size)
        if (end - start < points) {
            histories.add(FloatArray(points))
        } else {
            val normalized = normalizeChannelScopeChannel(
                flatScopes = flatScopes,
                start = start,
                points = points,
                dcRemovalEnabled = dcRemovalEnabled,
                gainPercent = gainPercent
            )
            histories.add(normalized)
        }
    }
    return histories
}

private fun parseChannelScopeTextStates(
    flat: IntArray
): List<ChannelScopeChannelTextState> {
    val stride = NativeBridge.CHANNEL_SCOPE_TEXT_STATE_STRIDE
    if (stride <= 0 || flat.isEmpty()) return emptyList()
    val channels = flat.size / stride
    if (channels <= 0) return emptyList()
    return List(channels) { channel ->
        val base = channel * stride
        ChannelScopeChannelTextState(
            channelIndex = flat.getOrElse(base + 0) { channel },
            note = flat.getOrElse(base + 1) { -1 },
            volume = flat.getOrElse(base + 2) { 0 },
            effectPrimaryLetterAscii = flat.getOrElse(base + 3) { 0 },
            effectPrimaryParam = flat.getOrElse(base + 4) { -1 },
            effectSecondaryLetterAscii = flat.getOrElse(base + 5) { 0 },
            effectSecondaryParam = flat.getOrElse(base + 6) { -1 },
            instrumentIndex = flat.getOrElse(base + 7) { -1 },
            sampleIndex = flat.getOrElse(base + 8) { -1 },
            flags = flat.getOrElse(base + 9) { 0 }
        )
    }
}

private fun parseIndexedNames(raw: String): Map<Int, String> {
    if (raw.isBlank()) return emptyMap()
    val out = LinkedHashMap<Int, String>()
    raw.lineSequence().forEach { lineRaw ->
        val line = lineRaw.trim()
        if (line.isEmpty()) return@forEach
        val dotIndex = line.indexOf(". ")
        if (dotIndex <= 0) return@forEach
        val index = line.substring(0, dotIndex).toIntOrNull() ?: return@forEach
        val name = line.substring(dotIndex + 2).trim()
        if (index > 0) {
            out[index] = name
        }
    }
    return out
}

// Per-channel persistent state for the correlation trigger.
private class ChannelScopeTriggerState {
    // Correlation buffer — blended waveform shape used as the match kernel.
    var corrBuffer: FloatArray? = null
    // Gaussian window applied when updating the buffer (cached per period).
    var prevWindow: FloatArray? = null
    // Cached slope-finder kernel (recomputed when period changes significantly).
    var prevSlopeFinder: FloatArray? = null
    var prevPeriod: Int = 0
    // Smoothed data mean for DC removal across frames.
    var prevMean: Float = 0f
}

private fun computeChannelScopeTriggerIndices(
    histories: List<FloatArray>,
    triggerModeNative: Int,
    states: MutableList<ChannelScopeTriggerState>
): IntArray {
    if (histories.isEmpty()) return IntArray(0)
    while (states.size < histories.size) states.add(ChannelScopeTriggerState())
    if (states.size > histories.size) states.subList(histories.size, states.size).clear()
    return IntArray(histories.size) { channel ->
        findCorrelationTriggerIndex(
            history = histories[channel],
            triggerModeNative = triggerModeNative,
            state = states[channel]
        )
    }
}

/**
 * Estimate dominant period via downsampled autocorrelation.
 * Returns 0 when no clear periodicity is detected.
 */
private fun estimateSignalPeriod(data: FloatArray, subsmpPerS: Float, maxFreq: Float): Int {
    val n = data.size
    if (n < 16) return 0
    val stride = (n / 256).coerceAtLeast(1)
    val downN = n / stride
    if (downN < 8) return 0
    var meanAcc = 0.0
    for (i in 0 until downN) meanAcc += data[i * stride]
    val mean = (meanAcc / downN).toFloat()
    // Minimum period from max_freq.
    val minPeriod = if (maxFreq > 0f) (subsmpPerS / maxFreq).toInt().coerceAtLeast(2) else 2
    val minLag = (minPeriod / stride).coerceAtLeast(2)
    val maxLag = downN / 2
    if (minLag >= maxLag) return 0
    // Find the first zero crossing of autocorrelation to skip the central peak.
    var zeroCrossLag = minLag
    for (lag in minLag..maxLag) {
        var sum = 0.0
        val count = downN - lag
        for (j in 0 until count) {
            sum += (data[j * stride] - mean).toDouble() * (data[(j + lag) * stride] - mean).toDouble()
        }
        if (sum < 0.0) { zeroCrossLag = lag; break }
    }
    var bestCorr = 0.0
    var bestLag = 0
    for (lag in zeroCrossLag..maxLag) {
        var sum = 0.0
        val count = downN - lag
        for (j in 0 until count) {
            sum += (data[j * stride] - mean).toDouble() * (data[(j + lag) * stride] - mean).toDouble()
        }
        val corr = sum / count
        if (corr > bestCorr) {
            bestCorr = corr
            bestLag = lag
        }
    }
    val rawPeriod = if (bestCorr > 0.0) bestLag * stride else 0
    // For long periods, apply edge-compensation to avoid underestimating.
    if (rawPeriod > 0 && rawPeriod > n * 0.1) {
        val compensated = FloatArray(downN)
        for (i in 0 until downN) compensated[i] = data[i * stride] - mean
        val edgeComp = 0.9f
        for (i in 0 until downN) {
            val div = (1f - edgeComp * i.toFloat() / downN).coerceAtLeast(0.5f)
            compensated[i] /= div
        }
        var bestCorrComp = 0.0
        var bestLagComp = 0
        for (lag in zeroCrossLag..maxLag) {
            var sum = 0.0
            val count = downN - lag
            for (j in 0 until count) {
                sum += compensated[j].toDouble() * compensated[j + lag].toDouble()
            }
            val corr = sum / count
            if (corr > bestCorrComp) { bestCorrComp = corr; bestLagComp = lag }
        }
        return if (bestCorrComp > 0.0) bestLagComp * stride else rawPeriod
    }
    return rawPeriod
}

/** Un-normalized "valid" cross-correlation: slide kernel across data, return scores. */
private fun correlateValid(data: FloatArray, kernel: FloatArray): FloatArray {
    val dataLen = data.size
    val kernelLen = kernel.size
    val outLen = dataLen - kernelLen + 1
    if (outLen <= 0) return floatArrayOf()
    val out = FloatArray(outLen)
    for (i in 0 until outLen) {
        var sum = 0f
        for (j in 0 until kernelLen) {
            sum += data[i + j] * kernel[j]
        }
        out[i] = sum
    }
    return out
}

/** Normalize a buffer in place (divide by abs-max, avoiding division by zero). */
private fun normalizeBufferInPlace(buf: FloatArray) {
    var mx = 0f
    for (v in buf) { val a = kotlin.math.abs(v); if (a > mx) mx = a }
    if (mx < 0.01f) return
    val inv = 1f / mx
    for (i in buf.indices) buf[i] *= inv
}

/** Build Gaussian window of given size and standard deviation. */
private fun gaussianWindow(size: Int, std: Float): FloatArray {
    if (size <= 0 || std <= 0f) return FloatArray(size)
    val mid = (size - 1) / 2f
    return FloatArray(size) { i ->
        val d = (i - mid) / std
        kotlin.math.exp(-0.5f * d * d)
    }
}

/**
 * Correlation-based trigger (accurate mode, unused in hot path — native C++ version is used).
 */
private fun findCorrelationTriggerIndex(
    history: FloatArray,
    triggerModeNative: Int,
    state: ChannelScopeTriggerState
): Int {
    val n = history.size
    val center = n / 2
    if (n < 8 || triggerModeNative == 0) return center

    // Normalize edge direction (internally always work as rising-edge).
    val data = if (triggerModeNative == 2) FloatArray(n) { -history[it] } else history

    // Skip near-silence.
    var absMax = 0f
    for (s in data) { val a = kotlin.math.abs(s); if (a > absMax) absMax = a }
    if (absMax < 0.01f) return center

    // --- Sizing ---
    // The caller provides n = kernelSize + triggerDiameter samples.
    // We derive A, B, triggerDiameter from n.
    val kernelSize = (n * 2) / 3  // n ≈ kernel + 0.5*kernel = 1.5*kernel → kernel ≈ 2n/3
    val triggerDiameter = n - kernelSize
    val halfKernel = kernelSize / 2
    val corrNsamp = triggerDiameter + 1  // number of correlation offsets
    if (kernelSize < 8 || corrNsamp < 2) return center

    // --- Mean removal ---
    val meanResp = 1.0f
    var dataMean = 0f
    for (s in data) dataMean += s
    dataMean /= n
    state.prevMean += meanResp * (dataMean - state.prevMean)
    val meanRemoved = FloatArray(n) { data[it] - state.prevMean }

    // --- Period estimation ---
    val subsmpPerS = 44100f
    val maxFreq = 4000f
    val period = estimateSignalPeriod(meanRemoved, subsmpPerS, maxFreq)

    // --- Slope finder (recomputed when period changes significantly) ---
    val recalcSemitones = 1.0f
    val needRecalc = state.prevSlopeFinder == null ||
        state.prevSlopeFinder!!.size != kernelSize ||
        (state.prevPeriod > 0 && period > 0 &&
            kotlin.math.abs(kotlin.math.ln(period.toFloat() / state.prevPeriod) / kotlin.math.ln(2f) * 12f) > recalcSemitones) ||
        (state.prevPeriod == 0 && period > 0) ||
        (period == 0 && state.prevPeriod == 0 && state.prevSlopeFinder == null)

    val edgeStrength = 2.0f
    val bufferStrength = 1.0f
    val slopeWidthFraction = 0.25f

    if (needRecalc || state.prevSlopeFinder == null) {
        val slopeWidth = if (period > 0) {
            (slopeWidthFraction * period).coerceIn(1f, halfKernel / 3f)
        } else {
            (kernelSize / 12f).coerceIn(1f, halfKernel / 3f)
        }
        val slopeStrength = edgeStrength * 2f
        val sf = FloatArray(kernelSize)
        for (j in 0 until kernelSize) {
            sf[j] = if (j < halfKernel) -slopeStrength / 2f else slopeStrength / 2f
        }
        // Apply Gaussian window.
        val gw = gaussianWindow(kernelSize, slopeWidth)
        for (j in 0 until kernelSize) sf[j] *= gw[j]
        state.prevSlopeFinder = sf
        state.prevPeriod = period
    }
    val slopeFinder = state.prevSlopeFinder!!

    // --- Correlation buffer ---
    val corrBuffer = state.corrBuffer
    val corrEnabled = corrBuffer != null && corrBuffer.size == kernelSize
    val responsiveness = 0.2f

    // --- Build combined kernel ---
    val combinedKernel = if (corrEnabled) {
        FloatArray(kernelSize) { j ->
            slopeFinder[j] + corrBuffer!![j] * bufferStrength
        }
    } else {
        slopeFinder.copyOf()
    }

    // --- Un-normalized cross-correlation of data with combined kernel ---
    val corr = correlateValid(meanRemoved, combinedKernel)
    if (corr.size != corrNsamp) return center

    // --- Buffer quality (correlation of data with buffer alone, for local-max filter) ---
    val peaks = if (corrEnabled) {
        val bq = correlateValid(meanRemoved, corrBuffer!!)
        FloatArray(corrNsamp) { i -> bq.getOrElse(i) { 0f } * bufferStrength }
    } else {
        FloatArray(corrNsamp)
    }

    // --- Cumulative-sum edge score ---
    if (edgeStrength != 0f) {
        val cumsumStart = halfKernel - 1
        val cumsumLen = corrNsamp
        if (cumsumStart >= 0 && cumsumStart + cumsumLen <= n) {
            val edgeScore = FloatArray(cumsumLen)
            var cumSum = 0f
            for (i in 0 until cumsumLen) {
                cumSum += meanRemoved[cumsumStart + i]
                edgeScore[i] = -cumSum * edgeStrength
            }
            for (i in 0 until corrNsamp) peaks[i] += edgeScore.getOrElse(i) { 0f }
        }
    }

    // --- Restrict search radius by period ---
    val triggerRadiusPeriods = 1.5f
    val triggerRadius = if (period > 0) {
        (period * triggerRadiusPeriods).toInt().coerceAtMost(corrNsamp / 2)
    } else {
        corrNsamp / 2
    }

    // --- find_peak with local-maxima filtering ---
    val mid = corrNsamp / 2
    val left = (mid - triggerRadius).coerceAtLeast(0)
    val right = (mid + triggerRadius + 1).coerceAtMost(corrNsamp)
    val windowLen = right - left
    if (windowLen < 2) return center

    // Work on windowed copies.
    val wCorr = FloatArray(windowLen) { corr[left + it] }
    val wPeaks = FloatArray(windowLen) { peaks[left + it] }

    // Find minimum correlation to use as suppression value.
    var minCorr = Float.MAX_VALUE
    for (v in wCorr) if (v < minCorr) minCorr = v

    // Suppress non-local-maxima of peak score.
    for (i in 0 until windowLen - 1) {
        if (wPeaks[i] < wPeaks[i + 1]) wCorr[i] = minCorr
    }
    for (i in 1 until windowLen) {
        if (wPeaks[i] < wPeaks[i - 1]) wCorr[i] = minCorr
    }
    // Suppress boundary positions.
    wCorr[0] = minCorr
    wCorr[windowLen - 1] = minCorr

    // Pick the best local maximum.
    var bestIdx = windowLen / 2
    var bestVal = minCorr
    for (i in 0 until windowLen) {
        if (wCorr[i] > bestVal) { bestVal = wCorr[i]; bestIdx = i }
    }
    // If nothing survived, fall back to center.
    val peakOffset = if (bestVal <= minCorr) mid else left + bestIdx

    // The trigger point in the data buffer.
    val triggerIdx = (peakOffset + halfKernel).coerceIn(0, n - 1)

    // --- Update correlation buffer: align to trigger, Gaussian-window, blend ---
    val alignStart = (triggerIdx - halfKernel).coerceIn(0, n - kernelSize)
    val aligned = FloatArray(kernelSize) { j -> meanRemoved[alignStart + j] }
    val resultMean = run { var s = 0f; for (v in aligned) s += v; s / kernelSize }
    for (j in 0 until kernelSize) aligned[j] -= resultMean
    normalizeBufferInPlace(aligned)
    val bufStd = if (period > 0) (period * 0.5f) else (kernelSize / 4f)
    val window = gaussianWindow(kernelSize, bufStd)
    for (j in 0 until kernelSize) aligned[j] *= window[j]

    if (state.corrBuffer == null || state.corrBuffer!!.size != kernelSize) {
        state.corrBuffer = aligned
        state.prevWindow = window
    } else {
        val buf = state.corrBuffer!!
        normalizeBufferInPlace(buf)
        for (j in 0 until kernelSize) {
            buf[j] = buf[j] * (1f - responsiveness) + aligned[j] * responsiveness
        }
        state.prevWindow = window
    }

    return triggerIdx
}

internal data class ChannelScopePrefs(
    val windowMs: Int,
    val renderBackend: VisualizationRenderBackend,
    val dcRemovalEnabled: Boolean,
    val gainPercent: Int,
    val contrastBackdropEnabled: Boolean,
    val triggerModeNative: Int,
    val triggerAlgorithmNative: Int,
    val fpsMode: VisualizationOscFpsMode,
    val lineWidthDp: Int,
    val gridWidthDp: Int,
    val verticalGridEnabled: Boolean,
    val centerLineEnabled: Boolean,
    val layout: VisualizationChannelScopeLayout,
    val lineColorModeNoArtwork: VisualizationOscColorMode,
    val gridColorModeNoArtwork: VisualizationOscColorMode,
    val lineColorModeWithArtwork: VisualizationOscColorMode,
    val gridColorModeWithArtwork: VisualizationOscColorMode,
    val customLineColorArgb: Int,
    val customGridColorArgb: Int,
    val showArtworkBackground: Boolean,
    val backgroundMode: VisualizationChannelScopeBackgroundMode,
    val customBackgroundColorArgb: Int,
    val textEnabled: Boolean,
    val textAnchor: VisualizationChannelScopeTextAnchor,
    val textPaddingDp: Int,
    val textSizeSp: Int,
    val textHideWhenOverflow: Boolean,
    val textShadowEnabled: Boolean,
    val textFont: VisualizationChannelScopeTextFont,
    val textColorMode: VisualizationChannelScopeTextColorMode,
    val customTextColorArgb: Int,
    val textNoteFormat: VisualizationNoteNameFormat,
    val textShowChannel: Boolean,
    val textShowNote: Boolean,
    val textVisibleElementSelection: Set<String>,
    val textVuEnabled: Boolean,
    val textVuAnchor: VisualizationVuAnchor,
    val textVuColorMode: VisualizationChannelScopeTextColorMode,
    val textVuCustomColorArgb: Int
) {
    companion object {
        private const val KEY_WINDOW_MS = "visualization_channel_scope_window_ms"
        private const val KEY_RENDER_BACKEND = "visualization_channel_scope_render_backend"
        private const val KEY_DC_REMOVAL_ENABLED = "visualization_channel_scope_dc_removal_enabled"
        private const val KEY_GAIN_PERCENT = "visualization_channel_scope_gain_percent"
        private const val KEY_CONTRAST_BACKDROP_ENABLED = "visualization_channel_scope_contrast_backdrop_enabled"
        private const val KEY_TRIGGER_MODE = "visualization_channel_scope_trigger_mode"
        private const val KEY_TRIGGER_ALGORITHM = "visualization_channel_scope_trigger_algorithm"
        private const val KEY_FPS_MODE = "visualization_channel_scope_fps_mode"
        private const val KEY_LINE_WIDTH_DP = "visualization_channel_scope_line_width_dp"
        private const val KEY_GRID_WIDTH_DP = "visualization_channel_scope_grid_width_dp"
        private const val KEY_VERTICAL_GRID_ENABLED = "visualization_channel_scope_vertical_grid_enabled"
        private const val KEY_CENTER_LINE_ENABLED = "visualization_channel_scope_center_line_enabled"
        private const val KEY_SHOW_ARTWORK_BACKGROUND = "visualization_channel_scope_show_artwork_background"
        private const val KEY_BACKGROUND_MODE = "visualization_channel_scope_background_mode"
        private const val KEY_CUSTOM_BACKGROUND_COLOR_ARGB = "visualization_channel_scope_custom_background_color_argb"
        private const val KEY_LAYOUT = "visualization_channel_scope_layout"
        private const val KEY_LINE_COLOR_MODE_NO_ARTWORK = "visualization_channel_scope_line_color_mode_no_artwork"
        private const val KEY_GRID_COLOR_MODE_NO_ARTWORK = "visualization_channel_scope_grid_color_mode_no_artwork"
        private const val KEY_LINE_COLOR_MODE_WITH_ARTWORK = "visualization_channel_scope_line_color_mode_with_artwork"
        private const val KEY_GRID_COLOR_MODE_WITH_ARTWORK = "visualization_channel_scope_grid_color_mode_with_artwork"
        private const val KEY_CUSTOM_LINE_COLOR_ARGB = "visualization_channel_scope_custom_line_color_argb"
        private const val KEY_CUSTOM_GRID_COLOR_ARGB = "visualization_channel_scope_custom_grid_color_argb"
        private const val KEY_TEXT_ENABLED = "visualization_channel_scope_text_enabled"
        private const val KEY_TEXT_ANCHOR = "visualization_channel_scope_text_anchor"
        private const val KEY_TEXT_PADDING_DP = "visualization_channel_scope_text_padding_dp"
        private const val KEY_TEXT_SIZE_SP = "visualization_channel_scope_text_size_sp"
        private const val KEY_TEXT_HIDE_WHEN_OVERFLOW = "visualization_channel_scope_text_hide_when_overflow"
        private const val KEY_TEXT_SHADOW_ENABLED = "visualization_channel_scope_text_shadow_enabled"
        private const val KEY_TEXT_FONT = "visualization_channel_scope_text_font"
        private const val KEY_TEXT_COLOR_MODE = "visualization_channel_scope_text_color_mode"
        private const val KEY_CUSTOM_TEXT_COLOR_ARGB = "visualization_channel_scope_custom_text_color_argb"
        private const val KEY_TEXT_NOTE_FORMAT = "visualization_channel_scope_text_note_format"
        private const val KEY_TEXT_SHOW_CHANNEL = "visualization_channel_scope_text_show_channel"
        private const val KEY_TEXT_SHOW_NOTE = "visualization_channel_scope_text_show_note"
        private const val KEY_TEXT_VU_ENABLED = "visualization_channel_scope_text_vu_enabled"
        private const val KEY_TEXT_VU_ANCHOR = "visualization_channel_scope_text_vu_anchor"
        private const val KEY_TEXT_VU_COLOR_MODE = "visualization_channel_scope_text_vu_color_mode"
        private const val KEY_TEXT_VU_CUSTOM_COLOR_ARGB = "visualization_channel_scope_text_vu_custom_color_argb"

        fun from(sharedPrefs: android.content.SharedPreferences): ChannelScopePrefs {
            val defaultTriggerStorage = AppDefaults.Visualization.ChannelScope.triggerMode.storageValue
            val triggerModeNative = when (sharedPrefs.getString(KEY_TRIGGER_MODE, defaultTriggerStorage)) {
                "rising" -> 1
                "falling" -> 2
                else -> 0
            }
            val triggerAlgorithmNative = VisualizationChannelScopeTriggerAlgorithm.fromStorage(
                sharedPrefs.getString(
                    KEY_TRIGGER_ALGORITHM,
                    AppDefaults.Visualization.ChannelScope.triggerAlgorithm.storageValue
                )
            ).nativeValue
            return ChannelScopePrefs(
                windowMs = sharedPrefs.getInt(
                    KEY_WINDOW_MS,
                    AppDefaults.Visualization.ChannelScope.windowMs
                ).coerceIn(
                    AppDefaults.Visualization.ChannelScope.windowRangeMs.first,
                    AppDefaults.Visualization.ChannelScope.windowRangeMs.last
                ),
                renderBackend = VisualizationRenderBackend.fromStorage(
                    sharedPrefs.getString(
                        KEY_RENDER_BACKEND,
                        AppDefaults.Visualization.ChannelScope.renderBackend.storageValue
                    ),
                    AppDefaults.Visualization.ChannelScope.renderBackend
                ),
                dcRemovalEnabled = sharedPrefs.getBoolean(
                    KEY_DC_REMOVAL_ENABLED,
                    AppDefaults.Visualization.ChannelScope.dcRemovalEnabled
                ),
                gainPercent = sharedPrefs.getInt(
                    KEY_GAIN_PERCENT,
                    AppDefaults.Visualization.ChannelScope.gainPercent
                ).coerceIn(
                    AppDefaults.Visualization.ChannelScope.gainRangePercent.first,
                    AppDefaults.Visualization.ChannelScope.gainRangePercent.last
                ),
                contrastBackdropEnabled = sharedPrefs.getBoolean(
                    KEY_CONTRAST_BACKDROP_ENABLED,
                    AppDefaults.Visualization.ChannelScope.contrastBackdropEnabled
                ),
                triggerModeNative = triggerModeNative,
                triggerAlgorithmNative = triggerAlgorithmNative,
                fpsMode = VisualizationOscFpsMode.fromStorage(
                    sharedPrefs.getString(
                        KEY_FPS_MODE,
                        AppDefaults.Visualization.ChannelScope.fpsMode.storageValue
                    )
                ),
                lineWidthDp = sharedPrefs.getInt(
                    KEY_LINE_WIDTH_DP,
                    AppDefaults.Visualization.ChannelScope.lineWidthDp
                ).coerceIn(
                    AppDefaults.Visualization.ChannelScope.lineWidthRangeDp.first,
                    AppDefaults.Visualization.ChannelScope.lineWidthRangeDp.last
                ),
                gridWidthDp = sharedPrefs.getInt(
                    KEY_GRID_WIDTH_DP,
                    AppDefaults.Visualization.ChannelScope.gridWidthDp
                ).coerceIn(
                    AppDefaults.Visualization.ChannelScope.gridWidthRangeDp.first,
                    AppDefaults.Visualization.ChannelScope.gridWidthRangeDp.last
                ),
                verticalGridEnabled = sharedPrefs.getBoolean(
                    KEY_VERTICAL_GRID_ENABLED,
                    AppDefaults.Visualization.ChannelScope.verticalGridEnabled
                ),
                centerLineEnabled = sharedPrefs.getBoolean(
                    KEY_CENTER_LINE_ENABLED,
                    AppDefaults.Visualization.ChannelScope.centerLineEnabled
                ),
                layout = VisualizationChannelScopeLayout.fromStorage(
                    sharedPrefs.getString(
                        KEY_LAYOUT,
                        AppDefaults.Visualization.ChannelScope.layout.storageValue
                    )
                ),
                lineColorModeNoArtwork = VisualizationOscColorMode.fromStorage(
                    sharedPrefs.getString(
                        KEY_LINE_COLOR_MODE_NO_ARTWORK,
                        AppDefaults.Visualization.ChannelScope.lineColorModeNoArtwork.storageValue
                    ),
                    AppDefaults.Visualization.ChannelScope.lineColorModeNoArtwork
                ),
                gridColorModeNoArtwork = VisualizationOscColorMode.fromStorage(
                    sharedPrefs.getString(
                        KEY_GRID_COLOR_MODE_NO_ARTWORK,
                        AppDefaults.Visualization.ChannelScope.gridColorModeNoArtwork.storageValue
                    ),
                    AppDefaults.Visualization.ChannelScope.gridColorModeNoArtwork
                ),
                lineColorModeWithArtwork = VisualizationOscColorMode.fromStorage(
                    sharedPrefs.getString(
                        KEY_LINE_COLOR_MODE_WITH_ARTWORK,
                        AppDefaults.Visualization.ChannelScope.lineColorModeWithArtwork.storageValue
                    ),
                    AppDefaults.Visualization.ChannelScope.lineColorModeWithArtwork
                ),
                gridColorModeWithArtwork = VisualizationOscColorMode.fromStorage(
                    sharedPrefs.getString(
                        KEY_GRID_COLOR_MODE_WITH_ARTWORK,
                        AppDefaults.Visualization.ChannelScope.gridColorModeWithArtwork.storageValue
                    ),
                    AppDefaults.Visualization.ChannelScope.gridColorModeWithArtwork
                ),
                customLineColorArgb = sharedPrefs.getInt(
                    KEY_CUSTOM_LINE_COLOR_ARGB,
                    AppDefaults.Visualization.ChannelScope.customLineColorArgb
                ),
                customGridColorArgb = sharedPrefs.getInt(
                    KEY_CUSTOM_GRID_COLOR_ARGB,
                    AppDefaults.Visualization.ChannelScope.customGridColorArgb
                ),
                showArtworkBackground = sharedPrefs.getBoolean(
                    KEY_SHOW_ARTWORK_BACKGROUND,
                    AppDefaults.Visualization.ChannelScope.showArtworkBackground
                ),
                backgroundMode = VisualizationChannelScopeBackgroundMode.fromStorage(
                    sharedPrefs.getString(
                        KEY_BACKGROUND_MODE,
                        AppDefaults.Visualization.ChannelScope.backgroundMode.storageValue
                    )
                ),
                customBackgroundColorArgb = sharedPrefs.getInt(
                    KEY_CUSTOM_BACKGROUND_COLOR_ARGB,
                    AppDefaults.Visualization.ChannelScope.customBackgroundColorArgb
                ),
                textEnabled = sharedPrefs.getBoolean(
                    KEY_TEXT_ENABLED,
                    AppDefaults.Visualization.ChannelScope.textEnabled
                ),
                textAnchor = VisualizationChannelScopeTextAnchor.fromStorage(
                    sharedPrefs.getString(
                        KEY_TEXT_ANCHOR,
                        AppDefaults.Visualization.ChannelScope.textAnchor.storageValue
                    )
                ),
                textPaddingDp = sharedPrefs.getInt(
                    KEY_TEXT_PADDING_DP,
                    AppDefaults.Visualization.ChannelScope.textPaddingDp
                ).coerceIn(
                    AppDefaults.Visualization.ChannelScope.textPaddingRangeDp.first,
                    AppDefaults.Visualization.ChannelScope.textPaddingRangeDp.last
                ),
                textSizeSp = sharedPrefs.getInt(
                    KEY_TEXT_SIZE_SP,
                    AppDefaults.Visualization.ChannelScope.textSizeSp
                ).coerceIn(
                    AppDefaults.Visualization.ChannelScope.textSizeRangeSp.first,
                    AppDefaults.Visualization.ChannelScope.textSizeRangeSp.last
                ),
                textHideWhenOverflow = sharedPrefs.getBoolean(
                    KEY_TEXT_HIDE_WHEN_OVERFLOW,
                    AppDefaults.Visualization.ChannelScope.textHideWhenOverflow
                ),
                textShadowEnabled = sharedPrefs.getBoolean(
                    KEY_TEXT_SHADOW_ENABLED,
                    AppDefaults.Visualization.ChannelScope.textShadowEnabled
                ),
                textFont = VisualizationChannelScopeTextFont.fromStorage(
                    sharedPrefs.getString(
                        KEY_TEXT_FONT,
                        AppDefaults.Visualization.ChannelScope.textFont.storageValue
                    )
                ),
                textColorMode = VisualizationChannelScopeTextColorMode.fromStorage(
                    sharedPrefs.getString(
                        KEY_TEXT_COLOR_MODE,
                        AppDefaults.Visualization.ChannelScope.textColorMode.storageValue
                    )
                ),
                customTextColorArgb = sharedPrefs.getInt(
                    KEY_CUSTOM_TEXT_COLOR_ARGB,
                    AppDefaults.Visualization.ChannelScope.customTextColorArgb
                ),
                textNoteFormat = VisualizationNoteNameFormat.fromStorage(
                    sharedPrefs.getString(
                        KEY_TEXT_NOTE_FORMAT,
                        AppDefaults.Visualization.ChannelScope.textNoteFormat.storageValue
                    )
                ),
                textShowChannel = sharedPrefs.getBoolean(
                    KEY_TEXT_SHOW_CHANNEL,
                    AppDefaults.Visualization.ChannelScope.textShowChannel
                ),
                textShowNote = sharedPrefs.getBoolean(
                    KEY_TEXT_SHOW_NOTE,
                    AppDefaults.Visualization.ChannelScope.textShowNote
                ),
                textVisibleElementSelection = readChannelScopeVisibleElementSelection(sharedPrefs),
                textVuEnabled = sharedPrefs.getBoolean(
                    KEY_TEXT_VU_ENABLED,
                    AppDefaults.Visualization.ChannelScope.textVuEnabled
                ),
                textVuAnchor = VisualizationVuAnchor.fromStorage(
                    sharedPrefs.getString(
                        KEY_TEXT_VU_ANCHOR,
                        AppDefaults.Visualization.ChannelScope.textVuAnchor.storageValue
                    )
                ),
                textVuColorMode = VisualizationChannelScopeTextColorMode.fromStorage(
                    sharedPrefs.getString(
                        KEY_TEXT_VU_COLOR_MODE,
                        AppDefaults.Visualization.ChannelScope.textVuColorMode.storageValue
                    )
                ),
                textVuCustomColorArgb = sharedPrefs.getInt(
                    KEY_TEXT_VU_CUSTOM_COLOR_ARGB,
                    AppDefaults.Visualization.ChannelScope.textVuCustomColorArgb
                )
            )
        }

        fun isChannelScopeKey(key: String?): Boolean {
            return key?.startsWith("visualization_channel_scope_") == true
        }
    }
}

@Composable
internal fun rememberChannelScopePrefs(
    sharedPrefs: android.content.SharedPreferences
): ChannelScopePrefs {
    var state by remember(sharedPrefs) { mutableStateOf(ChannelScopePrefs.from(sharedPrefs)) }
    DisposableEffect(sharedPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (ChannelScopePrefs.isChannelScopeKey(key)) {
                state = ChannelScopePrefs.from(prefs)
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    return state
}

private data class ChannelScopeVisualState(
    val channelHistories: List<FloatArray>,
    val channelTextStates: List<ChannelScopeChannelTextState>,
    val instrumentNamesByIndex: Map<Int, String>,
    val sampleNamesByIndex: Map<Int, String>,
    val chipNamesByChannelIndex: Map<Int, String>,
    val triggerModeNative: Int,
    val triggerIndices: IntArray,
    val renderBackend: VisualizationRenderBackend,
    val lineWidthDp: Int,
    val gridWidthDp: Int,
    val verticalGridEnabled: Boolean,
    val centerLineEnabled: Boolean,
    val layout: VisualizationChannelScopeLayout,
    val lineColorModeNoArtwork: VisualizationOscColorMode,
    val gridColorModeNoArtwork: VisualizationOscColorMode,
    val lineColorModeWithArtwork: VisualizationOscColorMode,
    val gridColorModeWithArtwork: VisualizationOscColorMode,
    val customLineColorArgb: Int,
    val customGridColorArgb: Int,
    val textEnabled: Boolean,
    val textAnchor: VisualizationChannelScopeTextAnchor,
    val textPaddingDp: Int,
    val textSizeSp: Int,
    val textHideWhenOverflow: Boolean,
    val textShadowEnabled: Boolean,
    val textFont: VisualizationChannelScopeTextFont,
    val textColorMode: VisualizationChannelScopeTextColorMode,
    val customTextColorArgb: Int,
    val textNoteFormat: VisualizationNoteNameFormat,
    val textShowChannel: Boolean,
    val textShowNote: Boolean,
    val textShowVolume: Boolean,
    val textShowEffectPrimary: Boolean,
    val textShowEffectSecondary: Boolean,
    val textShowChip: Boolean,
    val textShowInstrument: Boolean,
    val textShowSample: Boolean,
    val textVuEnabled: Boolean,
    val textVuAnchor: VisualizationVuAnchor,
    val textVuColorMode: VisualizationChannelScopeTextColorMode,
    val textVuCustomColorArgb: Int
)

@Composable
internal fun AlbumArtPlaceholder(
    file: File?,
    isPlaying: Boolean,
    decoderName: String?,
    sampleRateHz: Int,
    artwork: ImageBitmap?,
    artworkSwipePreviewState: ArtworkSwipePreviewState = ArtworkSwipePreviewState(),
    placeholderIcon: ImageVector,
    visualizationModeBadgeText: String,
    showVisualizationModeBadge: Boolean,
    visualizationMode: VisualizationMode,
    visualizationShowDebugInfo: Boolean,
    visualizationOscWindowMs: Int,
    visualizationOscTriggerModeNative: Int,
    visualizationOscFpsMode: VisualizationOscFpsMode,
    visualizationBarFpsMode: VisualizationOscFpsMode,
    visualizationVuFpsMode: VisualizationOscFpsMode,
    visualizationOscRenderBackend: VisualizationRenderBackend,
    visualizationBarSmoothingPercent: Int,
    visualizationVuSmoothingPercent: Int,
    barCount: Int,
    barRoundnessDp: Int,
    barOverlayArtwork: Boolean,
    barUseThemeColor: Boolean,
    barFrequencyGridEnabled: Boolean,
    barRenderBackend: VisualizationRenderBackend,
    barColorModeNoArtwork: VisualizationOscColorMode,
    barColorModeWithArtwork: VisualizationOscColorMode,
    barCustomColorArgb: Int,
    barContrastBackdropEnabled: Boolean,
    oscStereo: Boolean,
    oscLineWidthDp: Int,
    oscGridWidthDp: Int,
    oscVerticalGridEnabled: Boolean,
    oscCenterLineEnabled: Boolean,
    oscLineColorModeNoArtwork: VisualizationOscColorMode,
    oscGridColorModeNoArtwork: VisualizationOscColorMode,
    oscLineColorModeWithArtwork: VisualizationOscColorMode,
    oscGridColorModeWithArtwork: VisualizationOscColorMode,
    oscCustomLineColorArgb: Int,
    oscCustomGridColorArgb: Int,
    oscContrastBackdropEnabled: Boolean,
    vuAnchor: VisualizationVuAnchor,
    vuUseThemeColor: Boolean,
    vuRenderBackend: VisualizationRenderBackend,
    vuColorModeNoArtwork: VisualizationOscColorMode,
    vuColorModeWithArtwork: VisualizationOscColorMode,
    vuCustomColorArgb: Int,
    vuContrastBackdropEnabled: Boolean,
    channelScopePrefs: ChannelScopePrefs,
    artworkCornerRadiusDp: Int = AppDefaults.Player.artworkCornerRadiusDp,
    onSwipePreviousTrack: () -> Unit = {},
    onSwipeNextTrack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentTrackKey = file?.absolutePath
    val useResolvedCurrentSwipeAsset =
        currentTrackKey != null &&
            artworkSwipePreviewState.currentTrackKey == currentTrackKey &&
            artworkSwipePreviewState.currentArtworkResolved
    val effectiveArtwork = if (useResolvedCurrentSwipeAsset) {
        artworkSwipePreviewState.currentArtwork
    } else {
        artwork
    }
    val effectivePlaceholderIcon = if (
        currentTrackKey != null &&
            artworkSwipePreviewState.currentTrackKey == currentTrackKey
    ) {
        artworkSwipePreviewState.currentPlaceholderIcon
    } else {
        placeholderIcon
    }
    if (
        visualizationMode == VisualizationMode.Off ||
            file == null ||
            (!isPlaying && visualizationMode != VisualizationMode.ChannelScope)
    ) {
        SwipeableArtworkContainer(
            currentTrackKey = currentTrackKey,
            swipePreviewState = artworkSwipePreviewState,
            artworkCornerRadiusDp = artworkCornerRadiusDp,
            visualizationMode = visualizationMode,
            modifier = modifier,
            onSwipePreviousTrack = onSwipePreviousTrack,
            onSwipeNextTrack = onSwipeNextTrack
        ) {
            StaticAlbumArtCard(
                file = file,
                artwork = effectiveArtwork,
                placeholderIcon = effectivePlaceholderIcon,
                artworkCornerRadiusDp = artworkCornerRadiusDp,
                visualizationMode = visualizationMode,
                visualizationModeBadgeText = visualizationModeBadgeText,
                showVisualizationModeBadge = showVisualizationModeBadge,
                crossfadeEnabled = !useResolvedCurrentSwipeAsset,
                modifier = Modifier.matchParentSize()
            )
        }
        return
    }

    var visWaveLeft by remember { mutableStateOf(FloatArray(0)) }
    var visWaveRight by remember { mutableStateOf(FloatArray(0)) }
    var visBars by remember { mutableStateOf(FloatArray(0)) }
    var visBarsSmoothed by remember { mutableStateOf(FloatArray(0)) }
    var visVu by remember { mutableStateOf(FloatArray(0)) }
    var visVuSmoothed by remember { mutableStateOf(FloatArray(0)) }
    var visChannelCount by remember { mutableIntStateOf(2) }
    var visChannelScopeHistories by remember { mutableStateOf<List<FloatArray>>(emptyList()) }
    var visChannelScopeLastChannelCount by remember { mutableIntStateOf(1) }
    var visChannelScopeTriggerIndices by remember { mutableStateOf(IntArray(0)) }
    var visChannelScopeTextStates by remember { mutableStateOf<List<ChannelScopeChannelTextState>>(emptyList()) }
    var visChannelScopeTextRawCache by remember { mutableStateOf(IntArray(0)) }
    var visChannelScopeLastTextPollNs by remember { mutableStateOf(0L) }
    var visChannelScopeInstrumentNamesByIndex by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var visChannelScopeSampleNamesByIndex by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var visChannelScopeChipNamesByChannelIndex by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var visDebugUpdateFps by remember { mutableIntStateOf(0) }
    var visDebugUpdateFrameMs by remember { mutableIntStateOf(0) }
    var visDebugSourceUniqueFps by remember { mutableIntStateOf(0) }
    var visDebugSourceUniqueFrameMs by remember { mutableIntStateOf(0) }
    var visDebugSourceDuplicatePercent by remember { mutableIntStateOf(0) }
    var visDebugDrawFps by remember { mutableIntStateOf(0) }
    var visDebugDrawFrameMs by remember { mutableIntStateOf(0) }
    val visDebugAccumulator = remember { VisualizationDebugAccumulator() }
    val visSourceDebugAccumulator = remember { VisualizationSourceDebugAccumulator() }
    var lastVisualizationBackend by remember {
        mutableStateOf(
            if (visualizationMode == VisualizationMode.ChannelScope) {
                channelScopePrefs.renderBackend
            } else if (visualizationMode == VisualizationMode.Oscilloscope) {
                visualizationOscRenderBackend
            } else if (visualizationMode == VisualizationMode.Bars) {
                barRenderBackend
            } else if (visualizationMode == VisualizationMode.VuMeters) {
                vuRenderBackend
            } else {
                visualizationRenderBackendForMode(visualizationMode)
            }
        )
    }
    val backendTransitionBlackAlpha = remember { Animatable(0f) }
    val context = LocalContext.current
    val visualizationUpdateDispatcher = remember { createVisualizationUpdateDispatcher() }

    DisposableEffect(visualizationUpdateDispatcher) {
        onDispose {
            visualizationUpdateDispatcher.close()
        }
    }

    LaunchedEffect(file?.absolutePath, decoderName, visualizationMode) {
        if (visualizationMode != VisualizationMode.ChannelScope) {
            visChannelScopeInstrumentNamesByIndex = emptyMap()
            visChannelScopeSampleNamesByIndex = emptyMap()
            visChannelScopeChipNamesByChannelIndex = emptyMap()
            return@LaunchedEffect
        }
        when (pluginNameForCoreName(decoderName)) {
            DecoderNames.LIB_OPEN_MPT -> {
                visChannelScopeInstrumentNamesByIndex =
                    parseIndexedNames(NativeBridge.getOpenMptInstrumentNames())
                visChannelScopeSampleNamesByIndex =
                    parseIndexedNames(NativeBridge.getOpenMptSampleNames())
                visChannelScopeChipNamesByChannelIndex = emptyMap()
            }
            DecoderNames.FURNACE -> {
                visChannelScopeInstrumentNamesByIndex =
                    parseIndexedNames(NativeBridge.getFurnaceInstrumentNames())
                visChannelScopeSampleNamesByIndex =
                    parseIndexedNames(NativeBridge.getFurnaceSampleNames())
                visChannelScopeChipNamesByChannelIndex = emptyMap()
            }
            DecoderNames.GAME_MUSIC_EMU -> {
                visChannelScopeInstrumentNamesByIndex = emptyMap()
                visChannelScopeSampleNamesByIndex = emptyMap()
                visChannelScopeChipNamesByChannelIndex =
                    NativeBridge.getDecoderToggleChannelNames()
                        .mapIndexed { index, name -> index to name }
                        .toMap()
            }
            DecoderNames.KLYSTRACK -> {
                visChannelScopeInstrumentNamesByIndex =
                    parseIndexedNames(NativeBridge.getKlystrackInstrumentNames())
                visChannelScopeSampleNamesByIndex = emptyMap()
                visChannelScopeChipNamesByChannelIndex = emptyMap()
            }
            DecoderNames.HIVELY_TRACKER -> {
                visChannelScopeInstrumentNamesByIndex =
                    parseIndexedNames(NativeBridge.getHivelyInstrumentNames())
                visChannelScopeSampleNamesByIndex = emptyMap()
                visChannelScopeChipNamesByChannelIndex = emptyMap()
            }
            else -> {
                visChannelScopeInstrumentNamesByIndex = emptyMap()
                visChannelScopeSampleNamesByIndex = emptyMap()
                visChannelScopeChipNamesByChannelIndex = emptyMap()
            }
        }
    }
    LaunchedEffect(
        visualizationMode,
        visualizationOscRenderBackend,
        barRenderBackend,
        vuRenderBackend,
        channelScopePrefs.renderBackend
    ) {
        val nextBackend = if (visualizationMode == VisualizationMode.ChannelScope) {
            channelScopePrefs.renderBackend
        } else if (visualizationMode == VisualizationMode.Oscilloscope) {
            visualizationOscRenderBackend
        } else if (visualizationMode == VisualizationMode.Bars) {
            barRenderBackend
        } else if (visualizationMode == VisualizationMode.VuMeters) {
            vuRenderBackend
        } else {
            visualizationRenderBackendForMode(visualizationMode)
        }
        try {
            if (nextBackend != lastVisualizationBackend) {
                backendTransitionBlackAlpha.snapTo(0f)
                backendTransitionBlackAlpha.animateTo(1f, animationSpec = tween(85))
                backendTransitionBlackAlpha.animateTo(0f, animationSpec = tween(145))
            } else if (backendTransitionBlackAlpha.value > 0f) {
                backendTransitionBlackAlpha.snapTo(0f)
            }
            lastVisualizationBackend = nextBackend
        } finally {
            // Rapid switches can cancel this effect mid-transition.
            // Ensure the blackout overlay never remains latched.
            withContext(NonCancellable) {
                if (backendTransitionBlackAlpha.value > 0f) {
                    backendTransitionBlackAlpha.snapTo(0f)
                }
            }
        }
    }
    LaunchedEffect(
        visualizationMode,
        file?.absolutePath,
        isPlaying,
        visualizationOscWindowMs,
        visualizationOscTriggerModeNative,
        visualizationOscFpsMode,
        channelScopePrefs.windowMs,
        channelScopePrefs.fpsMode,
        channelScopePrefs.layout,
        sampleRateHz,
        decoderName
    ) {
        val displayRefreshHz = contextDisplayRefreshRateHz(context)
        withContext(visualizationUpdateDispatcher) {
            var nextFrameTickNs = 0L
            var lastPollIntervalNs = 0L
            var localChannelScopeLastTextPollNs = 0L
            val localChannelScopeTriggerStates = mutableListOf<ChannelScopeTriggerState>()
            while (true) {
                coroutineContext.ensureActive()
                if (visualizationMode == VisualizationMode.ChannelScope && !isPlaying) {
                    val nowNs = System.nanoTime()
                    nextFrameTickNs = nowNs + 90_000_000L
                    lastPollIntervalNs = 90_000_000L
                    sleepUntilTickNs(nextFrameTickNs)
                    continue
                }
                val frameStartNs = System.nanoTime()
                val textPollIntervalNs = 120_000_000L
                val shouldPollText =
                    localChannelScopeLastTextPollNs == 0L ||
                        frameStartNs - localChannelScopeLastTextPollNs >= textPollIntervalNs
                val snapshot = readVisualizationSnapshot(
                    visualizationMode = visualizationMode,
                    decoderName = decoderName,
                    visualizationOscWindowMs = visualizationOscWindowMs,
                    visualizationOscTriggerModeNative = visualizationOscTriggerModeNative,
                    channelScopeWindowMs = channelScopePrefs.windowMs,
                    channelScopeDcRemovalEnabled = channelScopePrefs.dcRemovalEnabled,
                    channelScopeGainPercent = channelScopePrefs.gainPercent,
                    channelScopeTriggerModeNative = channelScopePrefs.triggerModeNative,
                    channelScopeTriggerAlgorithmNative = channelScopePrefs.triggerAlgorithmNative,
                    channelScopeLayout = channelScopePrefs.layout,
                    channelScopeTriggerStates = localChannelScopeTriggerStates,
                    sampleRateHz = sampleRateHz,
                    shouldPollChannelScopeText = shouldPollText
                )
                if (snapshot == null) {
                    val nowNs = System.nanoTime()
                    nextFrameTickNs = nowNs + 90_000_000L
                    lastPollIntervalNs = 90_000_000L
                    sleepUntilTickNs(nextFrameTickNs)
                    continue
                }
                val frameEndNs = System.nanoTime()
                if (snapshot.channelScopeTextRaw != null) {
                    localChannelScopeLastTextPollNs = frameStartNs
                }
                val sourceSignature = snapshotSourceSignature(
                    mode = visualizationMode,
                    snapshot = snapshot
                )
                launch(Dispatchers.Main.immediate) {
                    snapshot.waveLeft?.let { visWaveLeft = it }
                    snapshot.waveRight?.let { visWaveRight = it }
                    snapshot.bars?.let { visBars = it }
                    snapshot.vu?.let { visVu = it }
                    snapshot.channelCount?.let { visChannelCount = it }
                    snapshot.channelScopeHistories?.let { visChannelScopeHistories = it }
                    snapshot.channelScopeTriggerIndices?.let {
                        visChannelScopeTriggerIndices = it
                    }
                    snapshot.channelScopeLastChannelCount?.let { visChannelScopeLastChannelCount = it }
                    snapshot.channelScopeTextRaw?.let { rawText ->
                        if (!rawText.contentEquals(visChannelScopeTextRawCache)) {
                            visChannelScopeTextRawCache = rawText.copyOf()
                            visChannelScopeTextStates = parseChannelScopeTextStates(rawText)
                        }
                        visChannelScopeLastTextPollNs = frameStartNs
                    }
                    if (visDebugAccumulator.windowStartNs == 0L) {
                        visDebugAccumulator.windowStartNs = frameEndNs
                    }
                    if (visDebugAccumulator.lastFrameNs != 0L) {
                        visDebugAccumulator.latestFrameMs =
                            ((frameEndNs - visDebugAccumulator.lastFrameNs) / 1_000_000L).toInt().coerceAtLeast(0)
                    }
                    visDebugAccumulator.lastFrameNs = frameEndNs
                    visDebugAccumulator.frameCount += 1
                    val elapsedNs = frameEndNs - visDebugAccumulator.windowStartNs
                    if (elapsedNs >= 1_000_000_000L) {
                        visDebugUpdateFps = ((visDebugAccumulator.frameCount.toDouble() * 1_000_000_000.0) / elapsedNs.toDouble())
                            .roundToInt()
                            .coerceAtLeast(0)
                        visDebugAccumulator.frameCount = 0
                        visDebugAccumulator.windowStartNs = frameEndNs
                    }
                    // Throttle HUD state updates to reduce recomposition overhead.
                    if (frameEndNs - visDebugAccumulator.lastUiPublishNs >= 350_000_000L) {
                        visDebugUpdateFrameMs = visDebugAccumulator.latestFrameMs
                        visDebugAccumulator.lastUiPublishNs = frameEndNs
                    }
                    if (sourceSignature != null) {
                        if (visSourceDebugAccumulator.windowStartNs == 0L) {
                            visSourceDebugAccumulator.windowStartNs = frameEndNs
                        }
                        visSourceDebugAccumulator.updateCount += 1
                        val changed = sourceSignature != visSourceDebugAccumulator.lastSignature
                        if (changed) {
                            if (visSourceDebugAccumulator.lastUniqueFrameNs != 0L) {
                                visSourceDebugAccumulator.latestUniqueFrameMs =
                                    ((frameEndNs - visSourceDebugAccumulator.lastUniqueFrameNs) / 1_000_000L)
                                        .toInt()
                                        .coerceAtLeast(0)
                            }
                            visSourceDebugAccumulator.lastUniqueFrameNs = frameEndNs
                            visSourceDebugAccumulator.lastSignature = sourceSignature
                            visSourceDebugAccumulator.uniqueCount += 1
                        }
                        val sourceElapsedNs = frameEndNs - visSourceDebugAccumulator.windowStartNs
                        if (sourceElapsedNs >= 1_000_000_000L) {
                            val updates = visSourceDebugAccumulator.updateCount.coerceAtLeast(1)
                            val uniques = visSourceDebugAccumulator.uniqueCount.coerceAtLeast(0)
                            visDebugSourceUniqueFps =
                                ((uniques.toDouble() * 1_000_000_000.0) / sourceElapsedNs.toDouble())
                                    .roundToInt()
                                    .coerceAtLeast(0)
                            visDebugSourceDuplicatePercent =
                                (((updates - uniques).coerceAtLeast(0) * 100.0) / updates.toDouble())
                                    .roundToInt()
                                    .coerceIn(0, 100)
                            visSourceDebugAccumulator.updateCount = 0
                            visSourceDebugAccumulator.uniqueCount = 0
                            visSourceDebugAccumulator.windowStartNs = frameEndNs
                        }
                        if (frameEndNs - visSourceDebugAccumulator.lastUiPublishNs >= 350_000_000L) {
                            visDebugSourceUniqueFrameMs = visSourceDebugAccumulator.latestUniqueFrameMs
                            visSourceDebugAccumulator.lastUiPublishNs = frameEndNs
                        }
                    }
                }
                val pollIntervalNs = computeVisualizationPollIntervalNs(
                    isPlaying = isPlaying,
                    visualizationMode = visualizationMode,
                    visualizationOscFpsMode = visualizationOscFpsMode,
                    visualizationBarFpsMode = visualizationBarFpsMode,
                    visualizationVuFpsMode = visualizationVuFpsMode,
                    channelScopeFpsMode = channelScopePrefs.fpsMode,
                    displayRefreshHz = displayRefreshHz
                )
                val nowNs = System.nanoTime()
                if (pollIntervalNs != lastPollIntervalNs || nextFrameTickNs == 0L) {
                    nextFrameTickNs = nowNs + pollIntervalNs
                    lastPollIntervalNs = pollIntervalNs
                }
                if (nextFrameTickNs <= nowNs) {
                    nextFrameTickNs = nowNs + pollIntervalNs
                } else {
                    sleepUntilTickNs(nextFrameTickNs)
                    nextFrameTickNs += pollIntervalNs
                }
            }
        }
    }
    LaunchedEffect(visualizationMode) {
        if (visualizationMode != VisualizationMode.ChannelScope) {
            visChannelScopeHistories = emptyList()
            visChannelScopeTriggerIndices = IntArray(0)
            visChannelScopeTextStates = emptyList()
            visChannelScopeTextRawCache = IntArray(0)
            visChannelScopeLastTextPollNs = 0L
        }
    }
    LaunchedEffect(visBars, visualizationBarSmoothingPercent, visualizationMode) {
        if (visualizationMode != VisualizationMode.Bars) {
            visBarsSmoothed = visBars
            return@LaunchedEffect
        }
        if (visBars.isEmpty()) {
            visBarsSmoothed = visBars
            return@LaunchedEffect
        }
        val prev = visBarsSmoothed
        if (prev.size != visBars.size) {
            visBarsSmoothed = visBars.copyOf()
            return@LaunchedEffect
        }
        val smoothing = (visualizationBarSmoothingPercent.coerceIn(0, 95) / 100f)
        val mixed = FloatArray(visBars.size)
        for (i in visBars.indices) {
            val target = visBars[i].coerceIn(0f, 1f)
            val current = prev[i].coerceIn(0f, 1f)
            mixed[i] = (current * smoothing) + (target * (1f - smoothing))
        }
        visBarsSmoothed = mixed
    }
    LaunchedEffect(visVu, visualizationVuSmoothingPercent, visualizationMode) {
        if (visualizationMode != VisualizationMode.VuMeters) {
            visVuSmoothed = visVu
            return@LaunchedEffect
        }
        if (visVu.isEmpty()) {
            visVuSmoothed = visVu
            return@LaunchedEffect
        }
        val prev = visVuSmoothed
        if (prev.size != visVu.size) {
            visVuSmoothed = visVu.copyOf()
            return@LaunchedEffect
        }
        val smoothing = (visualizationVuSmoothingPercent.coerceIn(0, 95) / 100f)
        val mixed = FloatArray(visVu.size)
        for (i in visVu.indices) {
            val target = visVu[i].coerceIn(0f, 1f)
            val current = prev[i].coerceIn(0f, 1f)
            mixed[i] = (current * smoothing) + (target * (1f - smoothing))
        }
        visVuSmoothed = mixed
    }
    val channelScopeState = ChannelScopeVisualState(
        channelHistories = visChannelScopeHistories,
        channelTextStates = visChannelScopeTextStates,
        instrumentNamesByIndex = visChannelScopeInstrumentNamesByIndex,
        sampleNamesByIndex = visChannelScopeSampleNamesByIndex,
        chipNamesByChannelIndex = visChannelScopeChipNamesByChannelIndex,
        triggerModeNative = channelScopePrefs.triggerModeNative,
        triggerIndices = visChannelScopeTriggerIndices,
        renderBackend = channelScopePrefs.renderBackend,
        lineWidthDp = channelScopePrefs.lineWidthDp,
        gridWidthDp = channelScopePrefs.gridWidthDp,
        verticalGridEnabled = channelScopePrefs.verticalGridEnabled,
        centerLineEnabled = channelScopePrefs.centerLineEnabled,
        layout = channelScopePrefs.layout,
        lineColorModeNoArtwork = channelScopePrefs.lineColorModeNoArtwork,
        gridColorModeNoArtwork = channelScopePrefs.gridColorModeNoArtwork,
        lineColorModeWithArtwork = channelScopePrefs.lineColorModeWithArtwork,
        gridColorModeWithArtwork = channelScopePrefs.gridColorModeWithArtwork,
        customLineColorArgb = channelScopePrefs.customLineColorArgb,
        customGridColorArgb = channelScopePrefs.customGridColorArgb,
        textEnabled = channelScopePrefs.textEnabled,
        textAnchor = channelScopePrefs.textAnchor,
        textPaddingDp = channelScopePrefs.textPaddingDp,
        textSizeSp = channelScopePrefs.textSizeSp,
        textHideWhenOverflow = channelScopePrefs.textHideWhenOverflow,
        textShadowEnabled = channelScopePrefs.textShadowEnabled,
        textFont = channelScopePrefs.textFont,
        textColorMode = channelScopePrefs.textColorMode,
        customTextColorArgb = channelScopePrefs.customTextColorArgb,
        textNoteFormat = channelScopePrefs.textNoteFormat,
        textShowChannel = channelScopePrefs.textShowChannel,
        textShowNote = channelScopePrefs.textShowNote,
        textShowVolume = isChannelScopeVisibleElementEnabled(
            selectedStorageKeys = channelScopePrefs.textVisibleElementSelection,
            decoderName = decoderName,
            elementId = ChannelScopeVisibleElementId.Volume
        ),
        textShowEffectPrimary = isChannelScopeVisibleElementEnabled(
            selectedStorageKeys = channelScopePrefs.textVisibleElementSelection,
            decoderName = decoderName,
            elementId = ChannelScopeVisibleElementId.EffectPrimary
        ),
        textShowEffectSecondary = isChannelScopeVisibleElementEnabled(
            selectedStorageKeys = channelScopePrefs.textVisibleElementSelection,
            decoderName = decoderName,
            elementId = ChannelScopeVisibleElementId.EffectSecondary
        ),
        textShowChip = isChannelScopeVisibleElementEnabled(
            selectedStorageKeys = channelScopePrefs.textVisibleElementSelection,
            decoderName = decoderName,
            elementId = ChannelScopeVisibleElementId.Chip
        ),
        textShowInstrument = isChannelScopeVisibleElementEnabled(
            selectedStorageKeys = channelScopePrefs.textVisibleElementSelection,
            decoderName = decoderName,
            elementId = ChannelScopeVisibleElementId.Instrument
        ),
        textShowSample = isChannelScopeVisibleElementEnabled(
            selectedStorageKeys = channelScopePrefs.textVisibleElementSelection,
            decoderName = decoderName,
            elementId = ChannelScopeVisibleElementId.Sample
        ),
        textVuEnabled = channelScopePrefs.textVuEnabled,
        textVuAnchor = channelScopePrefs.textVuAnchor,
        textVuColorMode = channelScopePrefs.textVuColorMode,
        textVuCustomColorArgb = channelScopePrefs.textVuCustomColorArgb
    )
    val artworkBrightness = remember(artwork) {
        val bitmap = artwork ?: return@remember null
        runCatching {
            val pixels = bitmap.toPixelMap()
            if (pixels.width <= 0 || pixels.height <= 0) return@runCatching 0.5f
            val stepX = maxOf(1, pixels.width / 32)
            val stepY = maxOf(1, pixels.height / 32)
            var sum = 0f
            var count = 0
            var y = 0
            while (y < pixels.height) {
                var x = 0
                while (x < pixels.width) {
                    sum += pixels[x, y].luminance()
                    count++
                    x += stepX
                }
                y += stepY
            }
            if (count > 0) (sum / count) else 0.5f
        }.getOrNull()
    }
    val badgeBackground = when {
        artworkBrightness == null -> MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
        artworkBrightness > 0.5f -> androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.52f)
        else -> androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f)
    }
    val badgeContentColor = when {
        artworkBrightness == null -> MaterialTheme.colorScheme.onSurface
        artworkBrightness > 0.5f -> androidx.compose.ui.graphics.Color.White
        else -> androidx.compose.ui.graphics.Color.Black
    }
    val activeRenderBackend = if (visualizationMode == VisualizationMode.ChannelScope) {
        channelScopePrefs.renderBackend
    } else if (visualizationMode == VisualizationMode.Oscilloscope) {
        visualizationOscRenderBackend
    } else if (visualizationMode == VisualizationMode.Bars) {
        barRenderBackend
    } else if (visualizationMode == VisualizationMode.VuMeters) {
        vuRenderBackend
    } else {
        visualizationRenderBackendForMode(visualizationMode)
    }
    val themePrimaryColor = MaterialTheme.colorScheme.primary
    val useScopeArtworkBackground =
        visualizationMode != VisualizationMode.ChannelScope ||
            (
                channelScopePrefs.showArtworkBackground &&
                    channelScopePrefs.renderBackend != VisualizationRenderBackend.OpenGlSurface
                )
    val scopeBackgroundColor = remember(
        artwork,
        themePrimaryColor,
        channelScopePrefs.backgroundMode,
        channelScopePrefs.customBackgroundColorArgb
    ) {
        when (channelScopePrefs.backgroundMode) {
            VisualizationChannelScopeBackgroundMode.Custom -> Color(channelScopePrefs.customBackgroundColorArgb)
            VisualizationChannelScopeBackgroundMode.AutoDarkAccent -> {
                val accent = extractArtworkAccentColor(artwork)
                    ?: themePrimaryColor.copy(alpha = 1f)
                // Keep it dark for scope contrast, but avoid collapsing to plain black.
                val darkTint = lerp(accent, Color.Black, 0.62f)
                val floor = Color(0xFF101418)
                lerp(floor, darkTint, 0.70f).copy(alpha = 1f)
            }
        }
    }
    val oscStereoActive = oscStereo && visChannelCount > 1
    val vuTopAnchor = vuAnchor == VisualizationVuAnchor.Top
    val basicVisualizationMode =
        visualizationMode == VisualizationMode.Bars ||
            visualizationMode == VisualizationMode.Oscilloscope ||
            visualizationMode == VisualizationMode.VuMeters
    val basicVisualizationAlpha by animateFloatAsState(
        targetValue = if (!basicVisualizationMode || isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "basicVisualizationVisibility"
    )
    val visualizationContrastBrush = remember(visualizationMode, oscStereoActive) {
        when (visualizationMode) {
            VisualizationMode.Bars -> if (barContrastBackdropEnabled) {
                // Subtle bottom-up darkening improves bar readability on busy artwork.
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Black.copy(alpha = 0.10f),
                        0.45f to Color.Black.copy(alpha = 0.28f),
                        1.00f to Color.Black.copy(alpha = 0.55f)
                    )
                )
            } else null
            VisualizationMode.Oscilloscope -> if (oscContrastBackdropEnabled) {
                // Keep contrast strongest around waveform centerline(s).
                // Stereo lanes center around ~25% and ~75% of height.
                Brush.verticalGradient(
                    colorStops = if (oscStereoActive) {
                        arrayOf(
                            0.00f to Color.Black.copy(alpha = 0.08f),
                            0.25f to Color.Black.copy(alpha = 0.42f),
                            0.50f to Color.Black.copy(alpha = 0.10f),
                            0.75f to Color.Black.copy(alpha = 0.42f),
                            1.00f to Color.Black.copy(alpha = 0.08f)
                        )
                    } else {
                        arrayOf(
                            0.00f to Color.Black.copy(alpha = 0.08f),
                            0.50f to Color.Black.copy(alpha = 0.42f),
                            1.00f to Color.Black.copy(alpha = 0.08f)
                        )
                    }
                )
            } else null
            VisualizationMode.VuMeters -> if (vuContrastBackdropEnabled) {
                // Emphasize contrast near the active VU anchor area.
                if (vuTopAnchor) {
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Black.copy(alpha = 0.48f),
                            0.42f to Color.Black.copy(alpha = 0.24f),
                            1.00f to Color.Black.copy(alpha = 0.08f)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Black.copy(alpha = 0.08f),
                            0.58f to Color.Black.copy(alpha = 0.24f),
                            1.00f to Color.Black.copy(alpha = 0.48f)
                        )
                    )
                }
            } else null
            VisualizationMode.ChannelScope -> if (channelScopePrefs.contrastBackdropEnabled) {
                // Slightly stronger dim at center, tapering softly toward sides.
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Black.copy(alpha = 0.22f),
                        0.50f to Color.Black.copy(alpha = 0.30f),
                        1.00f to Color.Black.copy(alpha = 0.22f)
                    )
                )
            } else null
            else -> null
        }
    }

    SwipeableArtworkContainer(
        currentTrackKey = currentTrackKey,
        swipePreviewState = artworkSwipePreviewState,
        artworkCornerRadiusDp = artworkCornerRadiusDp,
        visualizationMode = visualizationMode,
        modifier = modifier,
        onSwipePreviousTrack = onSwipePreviousTrack,
        onSwipeNextTrack = onSwipeNextTrack
    ) {
        ElevatedCard(
            modifier = Modifier.matchParentSize(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(artworkCornerRadiusDp.coerceIn(0, 48).dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
            if (useScopeArtworkBackground) {
                AlbumArtSurfaceContent(
                    trackKey = currentTrackKey,
                    artwork = effectiveArtwork,
                    placeholderIcon = effectivePlaceholderIcon,
                    crossfadeEnabled = !useResolvedCurrentSwipeAsset,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(scopeBackgroundColor)
                )
            }
            if (visualizationContrastBrush != null && (!basicVisualizationMode || basicVisualizationAlpha > 0f)) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            alpha = if (basicVisualizationMode) {
                                basicVisualizationAlpha
                            } else {
                                1f
                            }
                        }
                        .background(brush = visualizationContrastBrush)
                )
            }
            if (!basicVisualizationMode || basicVisualizationAlpha > 0f) {
                BasicVisualizationOverlay(
                    mode = visualizationMode,
                    bars = visBarsSmoothed,
                    waveformLeft = visWaveLeft,
                    waveformRight = visWaveRight,
                    vuLevels = visVuSmoothed,
                    channelCount = visChannelCount,
                    barCount = barCount,
                    barRoundnessDp = barRoundnessDp,
                    barOverlayArtwork = barOverlayArtwork,
                    barUseThemeColor = barUseThemeColor,
                    barFrequencyGridEnabled = barFrequencyGridEnabled,
                    barSampleRateHz = sampleRateHz,
                    barRenderBackend = barRenderBackend,
                    barColorModeNoArtwork = barColorModeNoArtwork,
                    barColorModeWithArtwork = barColorModeWithArtwork,
                    barCustomColorArgb = barCustomColorArgb,
                    oscStereo = oscStereo,
                    oscRenderBackend = visualizationOscRenderBackend,
                    artwork = artwork,
                    oscLineWidthDp = oscLineWidthDp,
                    oscGridWidthDp = oscGridWidthDp,
                    oscVerticalGridEnabled = oscVerticalGridEnabled,
                    oscCenterLineEnabled = oscCenterLineEnabled,
                    oscLineColorModeNoArtwork = oscLineColorModeNoArtwork,
                    oscGridColorModeNoArtwork = oscGridColorModeNoArtwork,
                    oscLineColorModeWithArtwork = oscLineColorModeWithArtwork,
                    oscGridColorModeWithArtwork = oscGridColorModeWithArtwork,
                    oscCustomLineColorArgb = oscCustomLineColorArgb,
                    oscCustomGridColorArgb = oscCustomGridColorArgb,
                    vuAnchor = vuAnchor,
                    vuUseThemeColor = vuUseThemeColor,
                    vuRenderBackend = vuRenderBackend,
                    vuColorModeNoArtwork = vuColorModeNoArtwork,
                    vuColorModeWithArtwork = vuColorModeWithArtwork,
                    vuCustomColorArgb = vuCustomColorArgb,
                    channelScopeHistories = channelScopeState.channelHistories,
                    channelScopeTextStates = channelScopeState.channelTextStates,
                    channelScopeInstrumentNamesByIndex = channelScopeState.instrumentNamesByIndex,
                    channelScopeSampleNamesByIndex = channelScopeState.sampleNamesByIndex,
                    channelScopeChipNamesByChannelIndex = channelScopeState.chipNamesByChannelIndex,
                    channelScopeTriggerModeNative = channelScopeState.triggerModeNative,
                    channelScopeTriggerIndices = channelScopeState.triggerIndices,
                    channelScopeRenderBackend = channelScopeState.renderBackend,
                    channelScopeLineWidthDp = channelScopeState.lineWidthDp,
                    channelScopeGridWidthDp = channelScopeState.gridWidthDp,
                    channelScopeVerticalGridEnabled = channelScopeState.verticalGridEnabled,
                    channelScopeCenterLineEnabled = channelScopeState.centerLineEnabled,
                    channelScopeLayout = channelScopeState.layout,
                    channelScopeLineColorModeNoArtwork = channelScopeState.lineColorModeNoArtwork,
                    channelScopeGridColorModeNoArtwork = channelScopeState.gridColorModeNoArtwork,
                    channelScopeLineColorModeWithArtwork = channelScopeState.lineColorModeWithArtwork,
                    channelScopeGridColorModeWithArtwork = channelScopeState.gridColorModeWithArtwork,
                    channelScopeCustomLineColorArgb = channelScopeState.customLineColorArgb,
                    channelScopeCustomGridColorArgb = channelScopeState.customGridColorArgb,
                    channelScopeBackgroundColorArgb = scopeBackgroundColor.toArgb(),
                    channelScopeTextEnabled = channelScopeState.textEnabled,
                    channelScopeTextAnchor = channelScopeState.textAnchor,
                    channelScopeTextPaddingDp = channelScopeState.textPaddingDp,
                    channelScopeTextSizeSp = channelScopeState.textSizeSp,
                    channelScopeTextHideWhenOverflow = channelScopeState.textHideWhenOverflow,
                    channelScopeTextShadowEnabled = channelScopeState.textShadowEnabled,
                    channelScopeTextFont = channelScopeState.textFont,
                    channelScopeTextColorMode = channelScopeState.textColorMode,
                    channelScopeCustomTextColorArgb = channelScopeState.customTextColorArgb,
                    channelScopeTextNoteFormat = channelScopeState.textNoteFormat,
                    channelScopeTextShowChannel = channelScopeState.textShowChannel,
                    channelScopeTextShowNote = channelScopeState.textShowNote,
                    channelScopeTextShowVolume = channelScopeState.textShowVolume,
                    channelScopeTextShowEffectPrimary = channelScopeState.textShowEffectPrimary,
                    channelScopeTextShowEffectSecondary = channelScopeState.textShowEffectSecondary,
                    channelScopeTextShowChip = channelScopeState.textShowChip,
                    channelScopeTextShowInstrument = channelScopeState.textShowInstrument,
                    channelScopeTextShowSample = channelScopeState.textShowSample,
                    channelScopeTextVuEnabled = channelScopeState.textVuEnabled,
                    channelScopeTextVuAnchor = channelScopeState.textVuAnchor,
                    channelScopeTextVuColorMode = channelScopeState.textVuColorMode,
                    channelScopeTextVuCustomColorArgb = channelScopeState.textVuCustomColorArgb,
                    channelScopeCornerRadiusDp = artworkCornerRadiusDp.coerceIn(0, 48),
                    channelScopeOnFrameStats = { fps, frameMs ->
                        visDebugDrawFps = fps.coerceAtLeast(0)
                        visDebugDrawFrameMs = frameMs.coerceAtLeast(0)
                    },
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            alpha = if (basicVisualizationMode) {
                                basicVisualizationAlpha
                            } else {
                                1f
                            }
                        }
                )
            }
            if (backendTransitionBlackAlpha.value > 0f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = backendTransitionBlackAlpha.value.coerceIn(0f, 1f)))
                )
            }
            if (visualizationShowDebugInfo && visualizationMode != VisualizationMode.Off) {
                val drawLine = if (activeRenderBackend != VisualizationRenderBackend.Compose) {
                    "${visDebugDrawFps} fps  (${visDebugDrawFrameMs} ms)"
                } else {
                    "N/A"
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 10.dp, top = 10.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.22f)
                ) {
                    Text(
                        text = "Mode: ${visualizationMode.label}\n" +
                            "Backend: ${activeRenderBackend.label}\n" +
                            "Update: ${visDebugUpdateFps} fps  (${visDebugUpdateFrameMs} ms)\n" +
                            "Source unique: ${visDebugSourceUniqueFps} fps  (${visDebugSourceUniqueFrameMs} ms)\n" +
                            "Source duplicates: ${visDebugSourceDuplicatePercent}%\n" +
                            "Draw: $drawLine",
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = showVisualizationModeBadge,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp),
                enter = fadeIn(animationSpec = tween(170)),
                exit = fadeOut(animationSpec = tween(260))
            ) {
                VisualizationModeBadge(
                    visualizationMode = visualizationMode,
                    visualizationModeBadgeText = visualizationModeBadgeText,
                    badgeBackground = badgeBackground,
                    badgeContentColor = badgeContentColor
                )
            }
        }
    }
}
}
