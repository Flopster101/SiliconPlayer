package com.flopster101.siliconplayer.ui.screens

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal enum class BrowserPageNavDirection {
    Forward,
    Backward,
    Neutral
}

internal fun browserPageContentTransform(
    navDirection: BrowserPageNavDirection
): ContentTransform {
    val forward = navDirection == BrowserPageNavDirection.Forward
    val backward = navDirection == BrowserPageNavDirection.Backward
    val enterOffset = when {
        forward -> { width: Int -> width / 2 }
        backward -> { width: Int -> -width / 4 }
        else -> { _: Int -> 0 }
    }
    val exitOffset = when {
        forward -> { width: Int -> -width / 5 }
        backward -> { width: Int -> width / 3 }
        else -> { _: Int -> 0 }
    }
    return (
        slideInHorizontally(
            initialOffsetX = enterOffset,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
        ) +
            fadeIn(
                animationSpec = tween(
                    durationMillis = 180,
                    delayMillis = if (forward || backward) 40 else 0,
                    easing = LinearOutSlowInEasing
                )
            )
        ) togetherWith (
        slideOutHorizontally(
            targetOffsetX = exitOffset,
            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
        ) +
            fadeOut(animationSpec = tween(durationMillis = 120, easing = FastOutLinearInEasing))
        )
}

internal fun browserContentTransform(
    navDirection: BrowserPageNavDirection,
    loadingTransition: Boolean,
    loadingPageEnabled: Boolean = true
): ContentTransform {
    return if (loadingTransition && loadingPageEnabled) {
        browserLoadingContentTransform()
    } else {
        browserPageContentTransform(navDirection)
    }
}

internal fun browserLoadingContentTransform(): ContentTransform {
    return (
        fadeIn(animationSpec = tween(durationMillis = 170, easing = LinearOutSlowInEasing)) +
            slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight / 14 },
                animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing)
            )
        ) togetherWith (
        fadeOut(animationSpec = tween(durationMillis = 120, easing = FastOutLinearInEasing)) +
            slideOutVertically(
                targetOffsetY = { fullHeight -> -fullHeight / 16 },
                animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)
            )
        )
}

@Composable
internal fun BrowserLoadingCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    logLines: List<String>,
    waitingLine: String,
    primaryActionLabel: String? = null,
    primaryActionEnabled: Boolean = true,
    onPrimaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    val logListState = rememberLazyListState()
    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty()) {
            logListState.animateScrollToItem(logLines.lastIndex)
        }
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(108.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                if (logLines.isEmpty()) {
                    Text(
                        text = waitingLine,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = logListState,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logLines.size, key = { index -> "$index:${logLines[index]}" }) { index ->
                            Text(
                                text = logLines[index],
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            if (
                (primaryActionLabel != null && onPrimaryAction != null) ||
                (secondaryActionLabel != null && onSecondaryAction != null)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (primaryActionLabel != null && onPrimaryAction != null) {
                        TextButton(
                            onClick = onPrimaryAction,
                            enabled = primaryActionEnabled
                        ) {
                            Text(primaryActionLabel)
                        }
                    }
                    if (secondaryActionLabel != null && onSecondaryAction != null) {
                        TextButton(onClick = onSecondaryAction) {
                            Text(secondaryActionLabel)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BrowserToolbarSubtitle(
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Crossfade(targetState = subtitle, label = "browserToolbarSubtitle") { text ->
        val shouldMarquee = text.length > 52
        if (shouldMarquee) {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .clipToBounds()
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = 900
                    )
                )
            }
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun BrowserLazyListScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val layoutInfo = listState.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    val totalItems = layoutInfo.totalItemsCount
    if (visibleItems.isEmpty() || totalItems <= 0) return

    val visibleCount = visibleItems.size
    if (!listState.canScrollBackward && !listState.canScrollForward && visibleCount >= totalItems) {
        return
    }

    val averageItemSizePx = (visibleItems.sumOf { it.size }.toFloat() / visibleCount.toFloat())
        .coerceAtLeast(1f)
    val fractionalFirstIndex = listState.firstVisibleItemIndex.toFloat() +
        (listState.firstVisibleItemScrollOffset.toFloat() / averageItemSizePx)
    val maxFirstIndex = (totalItems - visibleCount).coerceAtLeast(1).toFloat()
    val offsetFraction = (fractionalFirstIndex / maxFirstIndex).coerceIn(0f, 1f)
    val thumbHeightFraction = (visibleCount.toFloat() / totalItems.toFloat()).coerceIn(0.08f, 1f)

    BoxWithConstraints(
        modifier = modifier.background(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            shape = RoundedCornerShape(999.dp)
        )
    ) {
        val thumbHeight = (maxHeight * thumbHeightFraction).coerceAtLeast(18.dp)
        val thumbMaxOffset = (maxHeight - thumbHeight).coerceAtLeast(0.dp)
        val thumbOffset = thumbMaxOffset * offsetFraction
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(thumbHeight)
                .offset(y = thumbOffset)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(999.dp)
                )
        )
    }
}
