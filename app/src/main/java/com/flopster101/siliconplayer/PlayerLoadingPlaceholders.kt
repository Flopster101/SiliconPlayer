package com.flopster101.siliconplayer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun AnimatedMetadataPlaceholderLine(
    widthFraction: Float,
    height: Dp,
    modifier: Modifier = Modifier
) {
    val placeholderBase = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f)
    val placeholderHighlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val shimmerTransition = rememberInfiniteTransition(label = "metadataPlaceholderShimmer")
    val shimmerOffset by shimmerTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1050, easing = LinearEasing)
        ),
        label = "metadataPlaceholderShimmerOffset"
    )
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction.coerceIn(0f, 1f))
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        placeholderBase,
                        placeholderHighlight,
                        placeholderBase
                    ),
                    startX = shimmerOffset * 460f,
                    endX = (shimmerOffset * 460f) + 220f
                )
            )
    )
}
