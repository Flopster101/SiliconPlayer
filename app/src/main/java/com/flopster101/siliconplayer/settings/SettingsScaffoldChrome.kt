package com.flopster101.siliconplayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SettingsScaffoldShell(
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        content = content
    )
}

@Composable
internal fun SettingsSecondaryHeader(
    secondaryTitle: String?,
    route: SettingsRoute,
    pluginPriorityEditMode: Boolean,
    onTogglePluginPriorityEditMode: () -> Unit,
    selectedPluginName: String?,
    onRequestPluginReset: (String) -> Unit,
    onResetVisualizationBarsSettings: () -> Unit,
    onResetVisualizationOscilloscopeSettings: () -> Unit,
    onResetVisualizationVuSettings: () -> Unit,
    onResetVisualizationChannelScopeSettings: () -> Unit
) {
    AnimatedVisibility(
        visible = secondaryTitle != null,
        enter = fadeIn(animationSpec = tween(160)) + expandVertically(
            animationSpec = tween(180, easing = LinearOutSlowInEasing),
            expandFrom = Alignment.Top
        ),
        exit = fadeOut(animationSpec = tween(120)) + shrinkVertically(
            animationSpec = tween(150, easing = FastOutLinearInEasing),
            shrinkTowards = Alignment.Top
        )
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = secondaryTitle.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (route == SettingsRoute.AudioPlugins) {
                        Surface(
                            shape = CircleShape,
                            color = if (pluginPriorityEditMode) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            }
                        ) {
                            IconButton(
                                onClick = onTogglePluginPriorityEditMode,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = if (pluginPriorityEditMode) "Finish reorder mode" else "Edit core order",
                                    tint = if (pluginPriorityEditMode) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else if (route == SettingsRoute.PluginDetail && selectedPluginName != null) {
                        Surface(shape = CircleShape, color = Color.Transparent) {
                            IconButton(
                                onClick = { onRequestPluginReset(selectedPluginName) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Restore,
                                    contentDescription = "Reset core settings",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else if (
                        route == SettingsRoute.VisualizationBasicBars ||
                        route == SettingsRoute.VisualizationBasicOscilloscope ||
                        route == SettingsRoute.VisualizationBasicVuMeters ||
                        route == SettingsRoute.VisualizationAdvancedChannelScope
                    ) {
                        Surface(shape = CircleShape, color = Color.Transparent) {
                            IconButton(
                                onClick = {
                                    when (route) {
                                        SettingsRoute.VisualizationBasicBars -> onResetVisualizationBarsSettings()
                                        SettingsRoute.VisualizationBasicOscilloscope -> onResetVisualizationOscilloscopeSettings()
                                        SettingsRoute.VisualizationBasicVuMeters -> onResetVisualizationVuSettings()
                                        SettingsRoute.VisualizationAdvancedChannelScope -> onResetVisualizationChannelScopeSettings()
                                        else -> Unit
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Restore,
                                    contentDescription = "Reset visualization settings",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
