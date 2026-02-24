package com.flopster101.siliconplayer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun AboutEntityDialog(
    entity: AboutEntity,
    onDismiss: () -> Unit
) {
    AlertDialog(
        modifier = adaptiveDialogModifier(),
        properties = adaptiveDialogProperties(),
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = entity.name,
                    style = MaterialTheme.typography.headlineSmall
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = if (entity.kind == AboutEntityKind.Core) "Core" else "Library",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            AboutEntityDialogContent(entity = entity)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun AboutEntityDialogContent(
    entity: AboutEntity
) {
    val context = LocalContext.current
    val versionLabel = remember(entity.id) { AboutCatalog.resolveVersion(entity.id) }
    val configuration = LocalConfiguration.current
    val maxHeight = configuration.screenHeightDp.dp * 0.60f
    val scrollState = rememberScrollState()
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val scrollbarAlpha = rememberDialogScrollbarAlpha(
        enabled = true,
        scrollState = scrollState,
        label = "aboutEntityScrollbarAlpha"
    )

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
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = entity.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            AboutEntityInfoLine(
                label = "Author",
                value = entity.author
            )
            AboutEntityInfoLine(
                label = "License",
                value = entity.license
            )
            if (!versionLabel.isNullOrBlank()) {
                AboutEntityInfoLine(
                    label = "Version",
                    value = versionLabel
                )
            }
            if (entity.links.isNotEmpty()) {
                Text(
                    text = "Upstream links",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    entity.links.forEach { link ->
                        Text(
                            text = link.url,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                                    }
                                }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
        }

        if (viewportHeightPx > 0 && scrollState.maxValue > 0) {
            val viewportHeightDp = with(density) { viewportHeightPx.toDp() }
            AboutEntityScrollbar(
                scrollState = scrollState,
                viewportHeightPx = viewportHeightPx,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(4.dp)
                    .height(viewportHeightDp)
                    .offset(x = (-2).dp)
                    .graphicsLayer(alpha = scrollbarAlpha)
            )
        }
    }
}

@Composable
private fun AboutEntityInfoLine(
    label: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun AboutEntityScrollbar(
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
