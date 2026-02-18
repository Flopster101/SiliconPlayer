package com.flopster101.siliconplayer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
        SettingsSectionLabel("Core status")
        PlayerSettingToggleCard(
            title = "Enable core",
            description = "When disabled, this core will not be used for any files.",
            checked = isEnabled,
            onCheckedChange = { enabled ->
                isEnabled = enabled
                NativeBridge.setDecoderEnabled(pluginName, enabled)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSectionLabel("Priority")
        SettingsItemCard(
            title = "Core order index: $priority",
            description = "Priority values are kept sequential and match the core list order. 0 is first.",
            icon = Icons.Default.Tune,
            onClick = { showPriorityDialog = true }
        )

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSectionLabel("Handled extensions")
        val enabledCount = enabledExtensions.size
        val totalCount = supportedExtensions.size
        SettingsItemCard(
            title = "$enabledCount of $totalCount extensions enabled",
            description = "Select which file extensions this core should handle.",
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
    val options = remember(supportedExtensions) {
        supportedExtensions.map { ext ->
            SettingsMultiSelectOption(value = ext, label = ext.uppercase())
        }
    }
    SettingsSearchableMultiSelectDialog(
        title = "Select extensions",
        options = options,
        selectedValues = enabledExtensions,
        onDismiss = onDismiss,
        onConfirm = onExtensionsChanged,
        searchPlaceholder = "Search extensions..."
    )
}

@Composable
internal fun PriorityPickerDialog(
    currentPriority: Int,
    maxPriority: Int,
    onPrioritySelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    SettingsPriorityPickerDialog(
        currentValue = currentPriority,
        maxValue = maxPriority,
        onDismiss = onDismiss,
        onConfirm = onPrioritySelected
    )
}
