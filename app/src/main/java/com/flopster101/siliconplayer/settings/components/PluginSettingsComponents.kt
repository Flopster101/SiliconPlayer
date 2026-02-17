package com.flopster101.siliconplayer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

private val PluginSettingsCardShape = RoundedCornerShape(16.dp)

@Composable
internal fun PluginListItemCard(
    pluginName: String,
    priority: Int,
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    editMode: Boolean,
    isDragging: Boolean,
    dragOffsetPx: Float,
    nudgeOffsetPx: Float,
    nudgeNonce: Int,
    contentAlpha: Float,
    enableInteractions: Boolean,
    switchEnabled: Boolean,
    onMeasuredHeight: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val rowNudgeAnim = remember(nudgeNonce) { Animatable(nudgeOffsetPx) }
    LaunchedEffect(nudgeNonce) {
        if (nudgeNonce > 0 && nudgeOffsetPx != 0f) {
            rowNudgeAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 160, easing = LinearOutSlowInEasing)
            )
        }
    }
    val animatedDragOffsetY by animateFloatAsState(
        targetValue = if (isDragging) dragOffsetPx else 0f,
        animationSpec = if (isDragging) snap() else spring(
            dampingRatio = 1.0f,
            stiffness = 520f
        ),
        label = "pluginCardDragOffset"
    )
    val displayOffsetY = animatedDragOffsetY + if (isDragging) 0f else rowNudgeAnim.value

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 100f else 0f)
            .alpha(contentAlpha)
            .clip(PluginSettingsCardShape)
            .offset { IntOffset(x = 0, y = displayOffsetY.roundToInt()) }
            .let { base ->
                if (editMode && enableInteractions) {
                    base.pointerInput(pluginName, editMode) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDragDelta(dragAmount.y)
                            }
                        )
                    }
                } else if (!editMode && enableInteractions) {
                    base.clickable(onClick = onClick)
                } else {
                    base
                }
            },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = PluginSettingsCardShape,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { onMeasuredHeight(it.height.toFloat()) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = pluginName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Priority: $priority",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                val extensionCount = remember(pluginName) {
                    NativeBridge.getDecoderEnabledExtensions(pluginName).size
                }
                Text(
                    text = "$extensionCount extensions enabled",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (editMode) {
                Icon(
                    imageVector = Icons.Default.DragIndicator,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChanged,
                    enabled = switchEnabled
                )
            }
        }
    }
}

@Composable
internal fun PluginDetailScreen(
    pluginName: String,
    onPriorityChanged: (Int) -> Unit,
    onExtensionsChanged: (Array<String>) -> Unit
) {
    var isEnabled by remember { mutableStateOf(NativeBridge.isDecoderEnabled(pluginName)) }
    var priority by remember { mutableIntStateOf(NativeBridge.getDecoderPriority(pluginName)) }
    val supportedExtensions = remember { NativeBridge.getDecoderSupportedExtensions(pluginName) }
    var enabledExtensions by remember {
        mutableStateOf(NativeBridge.getDecoderEnabledExtensions(pluginName).toSet())
    }
    val pluginCount = remember { NativeBridge.getRegisteredDecoderNames().size }
    val maxPriority = (pluginCount - 1).coerceAtLeast(0)
    var showPriorityDialog by remember { mutableStateOf(false) }
    var showExtensionsDialog by remember { mutableStateOf(false) }

    Column {
        SettingsSectionLabel("Plugin status")
        PlayerSettingToggleCard(
            title = "Enable plugin",
            description = "When disabled, this plugin will not be used for any files.",
            checked = isEnabled,
            onCheckedChange = { enabled ->
                isEnabled = enabled
                NativeBridge.setDecoderEnabled(pluginName, enabled)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSectionLabel("Priority")
        SettingsItemCard(
            title = "Plugin order index: $priority",
            description = "Priority values are kept sequential and match the plugin list order. 0 is first.",
            icon = Icons.Default.Tune,
            onClick = { showPriorityDialog = true }
        )

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSectionLabel("Handled extensions")
        val enabledCount = enabledExtensions.size
        val totalCount = supportedExtensions.size
        SettingsItemCard(
            title = "$enabledCount of $totalCount extensions enabled",
            description = "Select which file extensions this plugin should handle.",
            icon = Icons.Default.MusicNote,
            onClick = { showExtensionsDialog = true }
        )
    }

    if (showPriorityDialog) {
        PriorityPickerDialog(
            currentPriority = priority,
            maxPriority = maxPriority,
            onPrioritySelected = { newPriority ->
                priority = newPriority
                onPriorityChanged(newPriority)
                showPriorityDialog = false
            },
            onDismiss = { showPriorityDialog = false }
        )
    }

    if (showExtensionsDialog) {
        ExtensionSelectorDialog(
            supportedExtensions = supportedExtensions,
            enabledExtensions = enabledExtensions,
            onExtensionsChanged = { newEnabled ->
                enabledExtensions = newEnabled
                val extensionsArray = if (newEnabled.size == supportedExtensions.size) {
                    emptyArray()
                } else {
                    newEnabled.toTypedArray()
                }
                onExtensionsChanged(extensionsArray)
            },
            onDismiss = { showExtensionsDialog = false }
        )
    }
}

@Composable
internal fun ExtensionSelectorDialog(
    supportedExtensions: Array<String>,
    enabledExtensions: Set<String>,
    onExtensionsChanged: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var tempEnabledExtensions by remember { mutableStateOf(enabledExtensions) }
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val filteredExtensions = remember(searchQuery, supportedExtensions) {
        if (searchQuery.isBlank()) {
            supportedExtensions.toList()
        } else {
            supportedExtensions.filter { it.contains(searchQuery, ignoreCase = true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select extensions") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search extensions...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        tempEnabledExtensions = supportedExtensions.toSet()
                    }) {
                        Text("Select All")
                    }
                    TextButton(onClick = {
                        tempEnabledExtensions = emptySet()
                    }) {
                        Text("Deselect All")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(
                            items = filteredExtensions,
                            key = { it }
                        ) { ext ->
                            val isEnabled = ext in tempEnabledExtensions
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        tempEnabledExtensions = if (isEnabled) {
                                            tempEnabledExtensions - ext
                                        } else {
                                            tempEnabledExtensions + ext
                                        }
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.Checkbox(
                                    checked = isEnabled,
                                    onCheckedChange = { checked ->
                                        tempEnabledExtensions = if (checked) {
                                            tempEnabledExtensions + ext
                                        } else {
                                            tempEnabledExtensions - ext
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(ext.uppercase())
                            }
                        }
                    }

                    if (filteredExtensions.size > 8) {
                        val scrollbarPosition by remember {
                            derivedStateOf {
                                val layoutInfo = listState.layoutInfo
                                val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                                val totalItemsHeight = layoutInfo.totalItemsCount *
                                    (layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 40)

                                if (viewportHeight <= 0 || totalItemsHeight <= viewportHeight) {
                                    return@derivedStateOf Triple(300f, 300f, 0f)
                                }

                                val thumbHeight = maxOf(
                                    20f,
                                    (viewportHeight.toFloat() / totalItemsHeight.toFloat()) * 300f
                                )

                                val firstVisibleItemIndex = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
                                val firstVisibleItemOffset = layoutInfo.visibleItemsInfo.firstOrNull()?.offset ?: 0
                                val itemSize = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 40

                                val scrollOffset = if (itemSize > 0) {
                                    firstVisibleItemIndex * itemSize - firstVisibleItemOffset
                                } else {
                                    0
                                }

                                val maxScroll = totalItemsHeight - viewportHeight
                                val scrollProgress = if (maxScroll > 0) {
                                    (scrollOffset.toFloat() / maxScroll.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }

                                val thumbOffset =
                                    (scrollProgress * (300f - thumbHeight)).coerceIn(0f, 300f - thumbHeight)

                                Triple(300f, thumbHeight, thumbOffset)
                            }
                        }

                        val (viewportHeight, thumbHeight, thumbOffset) = scrollbarPosition

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .width(6.dp)
                                .height(viewportHeight.dp)
                                .padding(start = 2.dp, end = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(thumbHeight.dp)
                                    .offset(y = thumbOffset.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onExtensionsChanged(tempEnabledExtensions)
                onDismiss()
            }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun PriorityPickerDialog(
    currentPriority: Int,
    maxPriority: Int,
    onPrioritySelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val safeMaxPriority = maxPriority.coerceAtLeast(0)
    var tempPriority by remember { mutableIntStateOf(currentPriority.coerceIn(0, safeMaxPriority)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set plugin priority") },
        text = {
            Column {
                Text(
                    text = "Priorities are order indexes: 0 is first. Values stay sequential to match the plugin list.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Priority: $tempPriority",
                    style = MaterialTheme.typography.titleMedium
                )
                if (safeMaxPriority > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = tempPriority.toFloat(),
                        onValueChange = { tempPriority = it.roundToInt().coerceIn(0, safeMaxPriority) },
                        valueRange = 0f..safeMaxPriority.toFloat(),
                        steps = (safeMaxPriority - 1).coerceAtLeast(0)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0 (Highest)", style = MaterialTheme.typography.labelSmall)
                    Text("$safeMaxPriority (Lowest)", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPrioritySelected(tempPriority) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
