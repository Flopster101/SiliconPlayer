package com.flopster101.siliconplayer

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

internal data class AudioPluginsRouteState(
    val pluginPriorityEditMode: Boolean
)

internal data class AudioPluginsRouteActions(
    val onPluginSelected: (String) -> Unit,
    val onPluginEnabledChanged: (String, Boolean) -> Unit,
    val onPluginPriorityOrderChanged: (List<String>) -> Unit,
    val onRequestClearPluginSettings: () -> Unit
)

@Composable
internal fun AudioPluginsRouteContent(
    state: AudioPluginsRouteState,
    actions: AudioPluginsRouteActions
) {
    val pluginPriorityEditMode = state.pluginPriorityEditMode
    val onPluginSelected = actions.onPluginSelected
    val onPluginEnabledChanged = actions.onPluginEnabledChanged
    val onPluginPriorityOrderChanged = actions.onPluginPriorityOrderChanged
    val onRequestClearPluginSettings = actions.onRequestClearPluginSettings

    val context = LocalContext.current
    SettingsSectionLabel("Registered cores")

    val registeredPluginNames = remember { NativeBridge.getRegisteredDecoderNames().toList() }
    val pluginEnabledState = remember(registeredPluginNames) {
        mutableStateMapOf<String, Boolean>().apply {
            registeredPluginNames.forEach { pluginName ->
                this[pluginName] = NativeBridge.isDecoderEnabled(pluginName)
            }
        }
    }
    val defaultPluginOrder = remember(registeredPluginNames) {
        registeredPluginNames.sortedBy { pluginName ->
            NativeBridge.getDecoderDefaultPriority(pluginName)
        }
    }
    var orderedPluginNames by remember {
        mutableStateOf(
            registeredPluginNames.sortedBy { pluginName ->
                NativeBridge.getDecoderPriority(pluginName)
            }
        )
    }
    var draggingPluginName by remember { mutableStateOf<String?>(null) }
    var dragVisualOffsetPx by remember { mutableFloatStateOf(0f) }
    var dragSwapRemainderPx by remember { mutableFloatStateOf(0f) }
    var dragOriginTopPx by remember { mutableFloatStateOf(0f) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var rowHeightPx by remember { mutableFloatStateOf(0f) }
    var orderDirty by remember { mutableStateOf(false) }
    val rowNudgeOffsetPx = remember { mutableStateMapOf<String, Float>() }
    val rowNudgeNonce = remember { mutableStateMapOf<String, Int>() }
    val rowTopPx = remember { mutableStateMapOf<String, Float>() }
    val spacerPx = with(LocalDensity.current) { 2.dp.toPx() }
    val fallbackStepPx = with(LocalDensity.current) { 84.dp.toPx() }
    val edgeOverscrollPx = with(LocalDensity.current) { 14.dp.toPx() }
    val itemStepPx = if (rowHeightPx > 0f) rowHeightPx + spacerPx else fallbackStepPx
    val swapThresholdPx = itemStepPx * 0.62f

    fun persistPriorityOrder(order: List<String>) {
        onPluginPriorityOrderChanged(order)
    }

    fun movePlugin(pluginName: String, direction: Int): Boolean {
        val currentIndex = orderedPluginNames.indexOf(pluginName)
        if (currentIndex < 0) return false
        val targetIndex = (currentIndex + direction).coerceIn(0, orderedPluginNames.lastIndex)
        if (targetIndex == currentIndex) return false
        val mutable = orderedPluginNames.toMutableList()
        val displacedPlugin = mutable[targetIndex]
        val item = mutable.removeAt(currentIndex)
        mutable.add(targetIndex, item)
        val displacedStartOffset = if (direction > 0) itemStepPx * 0.42f else -itemStepPx * 0.42f
        Snapshot.withMutableSnapshot {
            rowNudgeOffsetPx[displacedPlugin] = displacedStartOffset
            rowNudgeNonce[displacedPlugin] = (rowNudgeNonce[displacedPlugin] ?: 0) + 1
            orderedPluginNames = mutable
            orderDirty = true
        }
        return true
    }

    fun finishDragging(pluginName: String) {
        if (draggingPluginName != pluginName) return
        if (orderDirty) {
            persistPriorityOrder(orderedPluginNames)
        }
        draggingPluginName = null
        dragOriginTopPx = 0f
        dragVisualOffsetPx = 0f
        dragSwapRemainderPx = 0f
        dragStartIndex = -1
        orderDirty = false
    }

    if (pluginPriorityEditMode) {
        Text(
            text = "Drag cores to set priority order. Top item has highest priority.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SettingsRowSpacer()
    }

    LaunchedEffect(pluginPriorityEditMode) {
        if (!pluginPriorityEditMode) {
            draggingPluginName?.let { finishDragging(it) }
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            orderedPluginNames.forEachIndexed { index, pluginName ->
                key(pluginName) {
                    val isEnabled = pluginEnabledState[pluginName] ?: NativeBridge.isDecoderEnabled(pluginName)
                    val priority = index
                    val isDraggedRow = draggingPluginName == pluginName

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                rowTopPx[pluginName] = coords.positionInParent().y
                            }
                    ) {
                        PluginListItemCard(
                            pluginName = pluginName,
                            priority = priority,
                            enabled = isEnabled,
                            onEnabledChanged = { enabled ->
                                pluginEnabledState[pluginName] = enabled
                                onPluginEnabledChanged(pluginName, enabled)
                            },
                            onClick = {
                                if (!pluginPriorityEditMode) onPluginSelected(pluginName)
                            },
                            editMode = pluginPriorityEditMode,
                            isDragging = false,
                            dragOffsetPx = 0f,
                            nudgeOffsetPx = if (isDraggedRow) 0f else (rowNudgeOffsetPx[pluginName] ?: 0f),
                            nudgeNonce = if (isDraggedRow) 0 else (rowNudgeNonce[pluginName] ?: 0),
                            contentAlpha = if (isDraggedRow) 0f else 1f,
                            enableInteractions = (draggingPluginName == null || draggingPluginName == pluginName),
                            switchEnabled = !isDraggedRow,
                            onMeasuredHeight = { h ->
                                if (h > 0f) rowHeightPx = h
                            },
                            onDragStart = {
                                if (draggingPluginName == null || draggingPluginName == pluginName) {
                                    draggingPluginName = pluginName
                                    dragVisualOffsetPx = 0f
                                    dragSwapRemainderPx = 0f
                                    dragStartIndex = orderedPluginNames.indexOf(pluginName)
                                    dragOriginTopPx = rowTopPx[pluginName] ?: 0f
                                    orderDirty = false
                                }
                            },
                            onDragDelta = { deltaY ->
                                if (draggingPluginName != pluginName) return@PluginListItemCard
                                dragVisualOffsetPx += deltaY
                                dragSwapRemainderPx += deltaY
                                while (dragSwapRemainderPx >= swapThresholdPx) {
                                    if (!movePlugin(pluginName, 1)) {
                                        dragSwapRemainderPx = edgeOverscrollPx
                                        break
                                    }
                                    dragSwapRemainderPx -= itemStepPx
                                }
                                while (dragSwapRemainderPx <= -swapThresholdPx) {
                                    if (!movePlugin(pluginName, -1)) {
                                        dragSwapRemainderPx = -edgeOverscrollPx
                                        break
                                    }
                                    dragSwapRemainderPx += itemStepPx
                                }
                                val totalSteps = orderedPluginNames.lastIndex.coerceAtLeast(0)
                                val startIndex = dragStartIndex.coerceIn(0, totalSteps)
                                val minVisualOffset = (-startIndex * itemStepPx) - edgeOverscrollPx
                                val maxVisualOffset = ((totalSteps - startIndex) * itemStepPx) + edgeOverscrollPx
                                dragVisualOffsetPx = dragVisualOffsetPx.coerceIn(minVisualOffset, maxVisualOffset)
                            },
                            onDragEnd = {
                                finishDragging(pluginName)
                            }
                        )
                    }

                    if (index < orderedPluginNames.size - 1) {
                        SettingsRowSpacer()
                    }
                }
            }
        }

        val draggedName = draggingPluginName
        if (draggedName != null) {
            val draggedIndex = orderedPluginNames.indexOf(draggedName)
            val draggedPriority = if (draggedIndex >= 0) {
                draggedIndex
            } else {
                NativeBridge.getDecoderPriority(draggedName)
            }
            val draggedEnabled = pluginEnabledState[draggedName] ?: NativeBridge.isDecoderEnabled(draggedName)
            PluginListItemCard(
                pluginName = draggedName,
                priority = draggedPriority,
                enabled = draggedEnabled,
                onEnabledChanged = { },
                onClick = { },
                editMode = pluginPriorityEditMode,
                isDragging = true,
                dragOffsetPx = dragOriginTopPx + dragVisualOffsetPx,
                nudgeOffsetPx = 0f,
                nudgeNonce = 0,
                contentAlpha = 1f,
                enableInteractions = false,
                switchEnabled = false,
                onMeasuredHeight = { },
                onDragStart = { },
                onDragDelta = { },
                onDragEnd = { }
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    SettingsSectionLabel("Danger zone")
    SettingsItemCard(
        title = "Clear all core settings",
        description = "Reset all core settings to defaults without changing app settings.",
        icon = Icons.Default.MoreHoriz,
        onClick = onRequestClearPluginSettings
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "Reset core priority order",
        description = "Restore core order to built-in defaults and renumber priorities sequentially.",
        icon = Icons.Default.Tune,
        onClick = {
            orderedPluginNames = defaultPluginOrder
            persistPriorityOrder(defaultPluginOrder)
            draggingPluginName = null
            dragVisualOffsetPx = 0f
            dragSwapRemainderPx = 0f
            dragStartIndex = -1
            orderDirty = false
            Toast.makeText(context, "Core priority order reset", Toast.LENGTH_SHORT).show()
        }
    )
}
