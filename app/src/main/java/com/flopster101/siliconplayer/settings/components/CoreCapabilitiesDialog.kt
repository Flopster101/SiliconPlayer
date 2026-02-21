package com.flopster101.siliconplayer

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun CoreCapabilitiesDialog(
    sections: List<CoreCapabilitySection>,
    isLiveSnapshot: Boolean,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val maxHeight = configuration.screenHeightDp.dp * 0.60f
    val scrollState = rememberScrollState()
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    AlertDialog(
        modifier = Modifier
            .fillMaxWidth(0.85f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = { Text("Core capabilities") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .onSizeChanged { viewportHeightPx = it.height }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = if (isLiveSnapshot) {
                            "Showing a runtime capability snapshot for this core (it is currently active)."
                        } else {
                            "Showing reported baseline capabilities for this core."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    sections.forEachIndexed { sectionIndex, section ->
                        if (sectionIndex > 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        section.items.forEach { item ->
                            val line = buildAnnotatedString {
                                withStyle(
                                    style = androidx.compose.ui.text.SpanStyle(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                ) {
                                    append(item.id.replace("_", "_\u200B"))
                                }
                                append(": ")
                                append(item.description)
                            }
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }

                if (viewportHeightPx > 0 && scrollState.maxValue > 0) {
                    val viewportHeightDp = with(density) { viewportHeightPx.toDp() }
                    CoreCapabilitiesScrollbar(
                        scrollState = scrollState,
                        viewportHeightPx = viewportHeightPx,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .width(4.dp)
                            .height(viewportHeightDp)
                            .offset(x = (-2).dp)
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
private fun CoreCapabilitiesScrollbar(
    scrollState: ScrollState,
    viewportHeightPx: Int,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val totalContentPx = viewportHeightPx + scrollState.maxValue
    if (totalContentPx <= 0) return
    val thumbHeightPx = (viewportHeightPx.toFloat() * (viewportHeightPx.toFloat() / totalContentPx.toFloat()))
        .coerceAtLeast(18f)
        .coerceAtMost(viewportHeightPx.toFloat())
    val maxOffsetPx = (viewportHeightPx - thumbHeightPx).coerceAtLeast(0f)
    val offsetFraction = if (scrollState.maxValue == 0) 0f else {
        scrollState.value.toFloat() / scrollState.maxValue.toFloat()
    }
    val thumbOffsetPx = maxOffsetPx * offsetFraction
    val thumbHeightDp = with(density) { thumbHeightPx.toDp() }
    val thumbOffsetDp = with(density) { thumbOffsetPx.roundToInt().toDp() }

    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                shape = RoundedCornerShape(999.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = thumbOffsetDp)
                .height(thumbHeightDp)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(999.dp)
                )
        )
    }
}
