package com.flopster101.siliconplayer

import android.view.MotionEvent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.ExperimentalComposeUiApi
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
internal fun rememberScrollStateScrollbarDragHandler(
    scrollState: ScrollState
): (Float) -> Unit {
    return remember(scrollState) {
        { requestedFraction ->
            val targetFraction = requestedFraction.coerceIn(0f, 1f)
            val targetScroll = (scrollState.maxValue.toFloat() * targetFraction).roundToInt()
            val delta = (targetScroll - scrollState.value).toFloat()
            if (delta != 0f) {
                scrollState.dispatchRawDelta(delta)
            }
        }
    }
}

@Composable
internal fun rememberLazyListScrollbarDragHandler(
    listState: LazyListState,
    totalItems: Int,
    visibleCount: Int,
    averageItemSizePx: Float
): (Float) -> Unit {
    return remember(listState, totalItems, visibleCount, averageItemSizePx) {
        { requestedFraction ->
            val clampedFraction = requestedFraction.coerceIn(0f, 1f)
            val maxFirstIndex = (totalItems - visibleCount).coerceAtLeast(0)
            val targetFirstIndex = maxFirstIndex.toFloat() * clampedFraction
            val targetIndex = floor(targetFirstIndex).toInt().coerceIn(0, maxFirstIndex)
            val targetOffsetPx = ((targetFirstIndex - targetIndex.toFloat()) * averageItemSizePx)
                .roundToInt()
                .coerceAtLeast(0)
            val currentAbsoluteScrollPx = (
                (listState.firstVisibleItemIndex.toFloat() * averageItemSizePx) +
                    listState.firstVisibleItemScrollOffset.toFloat()
                ).coerceAtLeast(0f)
            val targetAbsoluteScrollPx = (
                (targetIndex.toFloat() * averageItemSizePx) +
                    targetOffsetPx.toFloat()
                ).coerceAtLeast(0f)
            val delta = targetAbsoluteScrollPx - currentAbsoluteScrollPx
            if (delta != 0f) {
                listState.dispatchRawDelta(delta)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun VerticalScrollbarTrack(
    thumbFraction: Float,
    offsetFraction: Float,
    modifier: Modifier = Modifier,
    trackThickness: Dp = 4.dp,
    minThumbHeight: Dp = 18.dp,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    thumbColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
    onDragFractionChanged: ((Float) -> Unit)? = null,
    onDragActiveChanged: ((Boolean) -> Unit)? = null
) {
    val shape = RoundedCornerShape(999.dp)
    var activePointerId by remember { mutableIntStateOf(-1) }
    var dragAnchorFromThumbTop by remember { mutableFloatStateOf(Float.NaN) }
    var measuredTrackHeightPx by remember { mutableFloatStateOf(1f) }
    BoxWithConstraints(
        modifier = modifier
            .onSizeChanged { measuredTrackHeightPx = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInteropFilter { event ->
                val dragCallback = onDragFractionChanged ?: return@pointerInteropFilter false

                fun trackHeightPx(): Float {
                    return measuredTrackHeightPx
                }

                fun thumbHeightPx(trackHeightPx: Float): Float {
                    return (trackHeightPx * thumbFraction.coerceIn(0f, 1f))
                        .coerceAtLeast(1f)
                        .coerceAtMost(trackHeightPx)
                }

                fun dispatchDrag(targetY: Float, anchorFromTop: Float) {
                    val trackHeight = trackHeightPx()
                    val thumbHeight = thumbHeightPx(trackHeight)
                    val travelRangePx = (trackHeight - thumbHeight).coerceAtLeast(0f)
                    val targetThumbTop = (targetY - anchorFromTop).coerceIn(0f, travelRangePx)
                    val targetFraction = if (travelRangePx <= 0f) 0f else targetThumbTop / travelRangePx
                    dragCallback(targetFraction)
                }

                fun resolveAnchorFromTouch(targetY: Float): Float {
                    val trackHeight = trackHeightPx()
                    val thumbHeight = thumbHeightPx(trackHeight)
                    val travelRangePx = (trackHeight - thumbHeight).coerceAtLeast(0f)
                    val currentThumbTop = travelRangePx * offsetFraction.coerceIn(0f, 1f)
                    val currentThumbBottom = currentThumbTop + thumbHeight
                    return if (targetY in currentThumbTop..currentThumbBottom) {
                        targetY - currentThumbTop
                    } else {
                        thumbHeight / 2f
                    }
                }

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        activePointerId = event.getPointerId(0)
                        dragAnchorFromThumbTop = resolveAnchorFromTouch(event.y)
                        onDragActiveChanged?.invoke(true)
                        dispatchDrag(event.y, dragAnchorFromThumbTop)
                        true
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        if (activePointerId < 0) {
                            val index = event.actionIndex
                            activePointerId = event.getPointerId(index)
                            dragAnchorFromThumbTop = resolveAnchorFromTouch(event.getY(index))
                            dispatchDrag(event.getY(index), dragAnchorFromThumbTop)
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (activePointerId < 0) return@pointerInteropFilter false
                        val pointerIndex = event.findPointerIndex(activePointerId)
                        if (pointerIndex < 0) return@pointerInteropFilter true
                        dispatchDrag(event.getY(pointerIndex), dragAnchorFromThumbTop)
                        true
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        val liftedIndex = event.actionIndex
                        val liftedId = event.getPointerId(liftedIndex)
                        if (liftedId == activePointerId) {
                            val replacementIndex = (0 until event.pointerCount)
                                .firstOrNull { it != liftedIndex }
                            if (replacementIndex != null) {
                                activePointerId = event.getPointerId(replacementIndex)
                                dragAnchorFromThumbTop = resolveAnchorFromTouch(event.getY(replacementIndex))
                                dispatchDrag(event.getY(replacementIndex), dragAnchorFromThumbTop)
                            } else {
                                activePointerId = -1
                                dragAnchorFromThumbTop = Float.NaN
                                onDragActiveChanged?.invoke(false)
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        activePointerId = -1
                        dragAnchorFromThumbTop = Float.NaN
                        onDragActiveChanged?.invoke(false)
                        true
                    }
                    else -> false
                }
            }
    ) {
        val clampedThumbFraction = thumbFraction.coerceIn(0f, 1f)
        val clampedOffsetFraction = offsetFraction.coerceIn(0f, 1f)
        val thumbHeight = (maxHeight * clampedThumbFraction)
            .coerceAtLeast(minThumbHeight)
            .coerceAtMost(maxHeight)
        val thumbMaxOffset = (maxHeight - thumbHeight).coerceAtLeast(0.dp)
        val thumbOffset = thumbMaxOffset * clampedOffsetFraction

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(trackThickness)
                .fillMaxHeight()
                .clip(shape)
                .background(trackColor, shape)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(thumbHeight)
                    .offset(y = thumbOffset)
                    .background(thumbColor, shape)
            )
        }
    }
}
