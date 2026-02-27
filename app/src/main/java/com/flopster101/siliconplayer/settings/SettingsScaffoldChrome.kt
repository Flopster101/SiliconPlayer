package com.flopster101.siliconplayer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SettingsScaffoldShell(
    route: SettingsRoute,
    selectedPluginName: String?,
    pluginPriorityEditMode: Boolean,
    onBack: () -> Unit,
    onTogglePluginPriorityEditMode: () -> Unit,
    onRequestPluginReset: (String) -> Unit,
    onResetVisualizationBarsSettings: () -> Unit,
    onResetVisualizationOscilloscopeSettings: () -> Unit,
    onResetVisualizationVuSettings: () -> Unit,
    onResetVisualizationChannelScopeSettings: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = {
                    AnimatedContent(
                        targetState = route,
                        transitionSpec = {
                            val forward = settingsRouteOrder(targetState) >= settingsRouteOrder(initialState)
                            val enter = slideInHorizontally(
                                initialOffsetX = { fullWidth -> if (forward) fullWidth / 4 else -fullWidth / 4 },
                                animationSpec = tween(
                                    durationMillis = 300,
                                    easing = FastOutSlowInEasing
                                )
                            ) + fadeIn(
                                animationSpec = tween(
                                    durationMillis = 210,
                                    delayMillis = 60,
                                    easing = LinearOutSlowInEasing
                                )
                            )
                            val exit = slideOutHorizontally(
                                targetOffsetX = { fullWidth -> if (forward) -fullWidth / 4 else fullWidth / 4 },
                                animationSpec = tween(
                                    durationMillis = 300,
                                    easing = FastOutSlowInEasing
                                )
                            ) + fadeOut(
                                animationSpec = tween(
                                    durationMillis = 120,
                                    easing = FastOutLinearInEasing
                                )
                            )
                            enter togetherWith exit
                        },
                        label = "settingsTopBarTitle"
                    ) { targetRoute ->
                        val topTitle = settingsSecondaryTitle(targetRoute, selectedPluginName) ?: "Settings"
                        Text(topTitle)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (route == SettingsRoute.AudioPlugins) {
                        androidx.compose.material3.Surface(
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
                    } else if (
                        route == SettingsRoute.VisualizationBasicBars ||
                        route == SettingsRoute.VisualizationBasicOscilloscope ||
                        route == SettingsRoute.VisualizationBasicVuMeters ||
                        route == SettingsRoute.VisualizationAdvancedChannelScope
                    ) {
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
                },
                scrollBehavior = scrollBehavior
            )
        },
        content = content
    )
}
