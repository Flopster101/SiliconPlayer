package com.flopster101.siliconplayer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

internal enum class BrowserPageNavDirection {
    Forward,
    Backward,
    Neutral
}

private const val BROWSER_SEARCH_DEBOUNCE_MS = 1_000L

internal class BrowserSearchController {
    var isVisible by mutableStateOf(false)
        private set
    var input by mutableStateOf("")
        private set
    var debouncedQuery by mutableStateOf("")
        private set

    fun show() {
        isVisible = true
    }

    fun hide() {
        isVisible = false
        input = ""
        debouncedQuery = ""
    }

    fun onInputChange(value: String) {
        input = value
    }

    internal fun setDebouncedQuery(value: String) {
        debouncedQuery = value
    }
}

internal class BrowserSelectionController<K> {
    var isSelectionMode by mutableStateOf(false)
        private set
    var selectedKeys by mutableStateOf<Set<K>>(emptySet())
        private set

    fun enterSelectionWith(key: K) {
        isSelectionMode = true
        selectedKeys = selectedKeys + key
    }

    fun toggleSelection(key: K) {
        if (!isSelectionMode) return
        selectedKeys = if (selectedKeys.contains(key)) {
            selectedKeys - key
        } else {
            selectedKeys + key
        }
    }

    fun selectAll(keys: Collection<K>) {
        isSelectionMode = true
        selectedKeys = keys.toSet()
    }

    fun deselectAll() {
        selectedKeys = emptySet()
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedKeys = emptySet()
    }
}

@Composable
internal fun <K> rememberBrowserSelectionController(): BrowserSelectionController<K> {
    return androidx.compose.runtime.remember { BrowserSelectionController<K>() }
}

internal data class BrowserSelectionActionItem(
    val label: String,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@Composable
internal fun rememberBrowserSearchController(): BrowserSearchController {
    val controller = androidx.compose.runtime.remember { BrowserSearchController() }
    LaunchedEffect(controller.isVisible, controller.input) {
        if (!controller.isVisible) {
            controller.setDebouncedQuery("")
            return@LaunchedEffect
        }
        delay(BROWSER_SEARCH_DEBOUNCE_MS)
        controller.setDebouncedQuery(controller.input.trim())
    }
    return controller
}

internal fun matchesBrowserSearchQuery(
    candidate: String,
    query: String
): Boolean {
    if (query.isBlank()) return true
    return candidate.contains(query, ignoreCase = true)
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
internal fun BrowserToolbarPathRow(
    icon: ImageVector,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        BrowserToolbarSubtitle(
            subtitle = subtitle,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun BrowserToolbarSelectorLabel(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "File Browser",
    focusRequester: FocusRequester? = null
) {
    val focusModifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .then(focusModifier)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse selector" else "Expand selector",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
internal fun BrowserToolbarSearchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search this folder",
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
internal fun BrowserSelectionToolbarControls(
    visible: Boolean,
    canSelectAny: Boolean,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    actionItems: List<BrowserSelectionActionItem>,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    var showSelectionToggleMenu by androidx.compose.runtime.remember(visible) { mutableStateOf(false) }
    var showSelectionActionsMenu by androidx.compose.runtime.remember(visible) { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onCancel,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cancel selection mode",
                modifier = Modifier.size(20.dp)
            )
        }

        Box {
            IconButton(
                onClick = { showSelectionActionsMenu = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Selection actions",
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                expanded = showSelectionActionsMenu,
                onDismissRequest = { showSelectionActionsMenu = false }
            ) {
                actionItems.forEach { actionItem ->
                    DropdownMenuItem(
                        text = { Text(actionItem.label) },
                        leadingIcon = {
                            actionItem.icon?.let {
                                Icon(imageVector = it, contentDescription = null)
                            }
                        },
                        enabled = actionItem.enabled,
                        onClick = {
                            showSelectionActionsMenu = false
                            actionItem.onClick()
                        }
                    )
                }
                if (actionItems.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Info") },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        enabled = false,
                        onClick = {}
                    )
                }
            }
        }

        Box {
            IconButton(
                onClick = { showSelectionToggleMenu = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = "Selection toggles",
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                expanded = showSelectionToggleMenu,
                onDismissRequest = { showSelectionToggleMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Select all") },
                    enabled = canSelectAny,
                    onClick = {
                        showSelectionToggleMenu = false
                        onSelectAll()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Deselect all") },
                    onClick = {
                        showSelectionToggleMenu = false
                        onDeselectAll()
                    }
                )
            }
        }
    }
}

@Composable
internal fun BrowserSearchToolbarRow(
    visible: Boolean,
    queryInput: String,
    onQueryInputChanged: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = queryInput,
                    onValueChange = onQueryInputChanged,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Search this folder") }
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close search",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun BrowserSearchNoResultsCard(
    query: String,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "No results",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "No entries match \"$query\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
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
