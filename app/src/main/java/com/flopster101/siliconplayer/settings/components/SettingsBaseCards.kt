package com.flopster101.siliconplayer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

private enum class SettingsRowRole {
    Standalone,
    Top,
    Middle,
    Bottom
}

private class SettingsRowSequencer {
    private var lastRowRoleState: MutableState<SettingsRowRole>? = null
    private var nextRowHasPrevious: Boolean = false

    fun onSectionBoundary() {
        lastRowRoleState = null
        nextRowHasPrevious = false
    }

    fun onRow(roleState: MutableState<SettingsRowRole>) {
        roleState.value = if (nextRowHasPrevious) SettingsRowRole.Bottom else SettingsRowRole.Standalone
        lastRowRoleState = roleState
        nextRowHasPrevious = false
    }

    fun onSpacer() {
        val last = lastRowRoleState ?: return
        last.value = when (last.value) {
            SettingsRowRole.Standalone -> SettingsRowRole.Top
            SettingsRowRole.Bottom -> SettingsRowRole.Middle
            SettingsRowRole.Top -> SettingsRowRole.Top
            SettingsRowRole.Middle -> SettingsRowRole.Middle
        }
        nextRowHasPrevious = true
    }
}

private val LocalSettingsRowSequencer = compositionLocalOf<SettingsRowSequencer?> { null }
private val SettingsOuterCorner = 20.dp
private val SettingsInnerCorner = 4.dp
private val SettingsInterRowSpacing = 2.dp
private val SettingsSectionLabelStartInset = 8.dp

@Composable
internal fun SettingsRowsHost(content: @Composable () -> Unit) {
    val sequencer = remember { SettingsRowSequencer() }
    CompositionLocalProvider(LocalSettingsRowSequencer provides sequencer) {
        content()
    }
}

@Composable
internal fun SettingsRowContainer(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val sequencer = LocalSettingsRowSequencer.current
    val rowRole = remember { mutableStateOf(SettingsRowRole.Standalone) }
    DisposableEffect(sequencer) {
        sequencer?.onRow(rowRole)
        onDispose { }
    }
    val shape = when (rowRole.value) {
        SettingsRowRole.Standalone -> RoundedCornerShape(SettingsOuterCorner)
        SettingsRowRole.Top -> RoundedCornerShape(
            topStart = SettingsOuterCorner,
            topEnd = SettingsOuterCorner,
            bottomStart = SettingsInnerCorner,
            bottomEnd = SettingsInnerCorner
        )
        SettingsRowRole.Middle -> RoundedCornerShape(SettingsInnerCorner)
        SettingsRowRole.Bottom -> RoundedCornerShape(
            topStart = SettingsInnerCorner,
            topEnd = SettingsInnerCorner,
            bottomStart = SettingsOuterCorner,
            bottomEnd = SettingsOuterCorner
        )
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
internal fun SettingsRowSpacer() {
    val sequencer = LocalSettingsRowSequencer.current
    DisposableEffect(sequencer) {
        sequencer?.onSpacer()
        onDispose { }
    }
    Spacer(modifier = Modifier.height(SettingsInterRowSpacing))
}

@Composable
internal fun SettingsSectionLabel(text: String) {
    val sequencer = LocalSettingsRowSequencer.current
    DisposableEffect(sequencer, text) {
        sequencer?.onSectionBoundary()
        onDispose { }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = SettingsSectionLabelStartInset)
    )
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
internal fun SettingsItemCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    SettingsRowContainer(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun SettingsValuePickerCard(
    title: String,
    description: String,
    value: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    SettingsRowContainer(
        onClick = onClick,
        enabled = enabled
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
        )
    }
}

@Composable
internal fun PlayerSettingToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    var localChecked by remember(title) { mutableStateOf(checked) }
    LaunchedEffect(checked) {
        localChecked = checked
    }

    SettingsRowContainer(
        onClick = {
            if (!enabled) return@SettingsRowContainer
            val toggled = !localChecked
            localChecked = toggled
            onCheckedChange(toggled)
        },
        enabled = enabled
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = localChecked,
            onCheckedChange = { newValue ->
                if (!enabled) return@Switch
                localChecked = newValue
                onCheckedChange(newValue)
            },
            enabled = enabled
        )
    }
}
