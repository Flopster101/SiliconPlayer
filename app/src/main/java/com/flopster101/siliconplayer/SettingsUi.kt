package com.flopster101.siliconplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsScreen(
    route: SettingsRoute,
    bottomContentPadding: Dp = 0.dp,
    state: SettingsScreenState,
    actions: SettingsScreenActions
) {
    var pendingResetAction by remember { mutableStateOf<SettingsResetAction?>(null) }
    var pendingPluginResetName by remember { mutableStateOf<String?>(null) }
    var pluginPriorityEditMode by remember { mutableStateOf(false) }

    LaunchedEffect(route) {
        if (route != SettingsRoute.AudioPlugins) {
            pluginPriorityEditMode = false
        }
    }

    SettingsScaffoldShell(
        route = route,
        selectedPluginName = state.selectedPluginName,
        pluginPriorityEditMode = pluginPriorityEditMode,
        onBack = actions.onBack,
        onTogglePluginPriorityEditMode = { pluginPriorityEditMode = !pluginPriorityEditMode },
        onRequestPluginReset = { pluginName -> pendingPluginResetName = pluginName },
        onResetVisualizationBarsSettings = actions.onResetVisualizationBarsSettings,
        onResetVisualizationOscilloscopeSettings = actions.onResetVisualizationOscilloscopeSettings,
        onResetVisualizationVuSettings = actions.onResetVisualizationVuSettings,
        onResetVisualizationChannelScopeSettings = actions.onResetVisualizationChannelScopeSettings
    ) { paddingValues ->
        SettingsRouteContentHost(
            route = route,
            bottomContentPadding = bottomContentPadding,
            scaffoldPaddingValues = paddingValues,
            state = state,
            actions = actions,
            pluginPriorityEditMode = pluginPriorityEditMode,
            onRequestClearAllSettings = {
                pendingResetAction = SettingsResetAction.ClearAllSettings
            },
            onRequestClearPluginSettings = {
                pendingResetAction = SettingsResetAction.ClearPluginSettings
            }
        )
    }

    SettingsResetDialogHost(
        pendingResetAction = pendingResetAction,
        onDismiss = { pendingResetAction = null },
        onConfirmClearAllSettings = actions.onClearAllSettings,
        onConfirmClearAllPluginSettings = actions.onClearAllPluginSettings
    )

    PluginResetDialogHost(
        pendingPluginResetName = pendingPluginResetName,
        onDismiss = { pendingPluginResetName = null },
        onConfirmResetPluginSettings = actions.onResetPluginSettings
    )
}
