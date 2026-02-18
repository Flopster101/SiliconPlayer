package com.flopster101.siliconplayer

internal data class SettingsNavigationCoordinator(
    val openSettingsRoute: (SettingsRoute, Boolean) -> Unit,
    val popSettingsRoute: () -> Boolean,
    val exitSettingsToReturnView: () -> Unit,
    val openCurrentCoreSettings: () -> Unit,
    val openVisualizationSettings: () -> Unit,
    val openSelectedVisualizationSettings: (VisualizationMode) -> Unit
)

internal fun buildSettingsNavigationCoordinator(
    currentView: MainView,
    settingsRoute: SettingsRoute,
    settingsRouteHistory: List<SettingsRoute>,
    settingsLaunchedFromPlayer: Boolean,
    settingsReturnView: MainView,
    lastUsedCoreName: String?,
    setSettingsRoute: (SettingsRoute) -> Unit,
    setSettingsRouteHistory: (List<SettingsRoute>) -> Unit,
    setSettingsLaunchedFromPlayer: (Boolean) -> Unit,
    setSettingsReturnView: (MainView) -> Unit,
    setCurrentView: (MainView) -> Unit,
    setSelectedPluginName: (String) -> Unit,
    setPlayerExpanded: (Boolean) -> Unit
): SettingsNavigationCoordinator {
    val openSettingsRoute: (SettingsRoute, Boolean) -> Unit = { targetRoute, resetHistory ->
        if (resetHistory) {
            setSettingsRouteHistory(emptyList())
            setSettingsRoute(targetRoute)
        } else if (settingsRoute != targetRoute) {
            setSettingsRouteHistory((settingsRouteHistory + settingsRoute).takeLast(24))
            setSettingsRoute(targetRoute)
        }
    }

    val popSettingsRoute: () -> Boolean = {
        val previousRoute = settingsRouteHistory.lastOrNull()
        if (previousRoute != null) {
            setSettingsRouteHistory(settingsRouteHistory.dropLast(1))
            setSettingsRoute(previousRoute)
            true
        } else if (settingsRoute != SettingsRoute.Root) {
            setSettingsRoute(SettingsRoute.Root)
            true
        } else {
            false
        }
    }

    val exitSettingsToReturnView: () -> Unit = {
        val target = if (settingsLaunchedFromPlayer) settingsReturnView else MainView.Home
        setSettingsLaunchedFromPlayer(false)
        setSettingsRouteHistory(emptyList())
        setSettingsRoute(SettingsRoute.Root)
        setCurrentView(target)
    }

    val openCurrentCoreSettings: () -> Unit = {
        pluginNameForCoreName(lastUsedCoreName)?.let { pluginName ->
            setSettingsReturnView(if (currentView == MainView.Settings) MainView.Home else currentView)
            setSettingsLaunchedFromPlayer(true)
            setSelectedPluginName(pluginName)
            openSettingsRoute(SettingsRoute.PluginDetail, true)
            setCurrentView(MainView.Settings)
            setPlayerExpanded(false)
        }
    }

    val openVisualizationSettings: () -> Unit = {
        setSettingsReturnView(if (currentView == MainView.Settings) MainView.Home else currentView)
        setSettingsLaunchedFromPlayer(true)
        openSettingsRoute(SettingsRoute.Visualization, true)
        setCurrentView(MainView.Settings)
        setPlayerExpanded(false)
    }

    val openVisualizationBarsSettings: () -> Unit = {
        setSettingsReturnView(if (currentView == MainView.Settings) MainView.Home else currentView)
        setSettingsLaunchedFromPlayer(true)
        openSettingsRoute(SettingsRoute.VisualizationBasicBars, true)
        setCurrentView(MainView.Settings)
        setPlayerExpanded(false)
    }

    val openVisualizationOscilloscopeSettings: () -> Unit = {
        setSettingsReturnView(if (currentView == MainView.Settings) MainView.Home else currentView)
        setSettingsLaunchedFromPlayer(true)
        openSettingsRoute(SettingsRoute.VisualizationBasicOscilloscope, true)
        setCurrentView(MainView.Settings)
        setPlayerExpanded(false)
    }

    val openVisualizationVuMetersSettings: () -> Unit = {
        setSettingsReturnView(if (currentView == MainView.Settings) MainView.Home else currentView)
        setSettingsLaunchedFromPlayer(true)
        openSettingsRoute(SettingsRoute.VisualizationBasicVuMeters, true)
        setCurrentView(MainView.Settings)
        setPlayerExpanded(false)
    }

    val openVisualizationChannelScopeSettings: () -> Unit = {
        setSettingsReturnView(if (currentView == MainView.Settings) MainView.Home else currentView)
        setSettingsLaunchedFromPlayer(true)
        openSettingsRoute(SettingsRoute.VisualizationAdvancedChannelScope, true)
        setCurrentView(MainView.Settings)
        setPlayerExpanded(false)
    }

    val openSelectedVisualizationSettings: (VisualizationMode) -> Unit = { mode ->
        when (mode) {
            VisualizationMode.Bars -> openVisualizationBarsSettings()
            VisualizationMode.Oscilloscope -> openVisualizationOscilloscopeSettings()
            VisualizationMode.VuMeters -> openVisualizationVuMetersSettings()
            VisualizationMode.ChannelScope -> openVisualizationChannelScopeSettings()
            VisualizationMode.Off -> Unit
        }
    }

    return SettingsNavigationCoordinator(
        openSettingsRoute = openSettingsRoute,
        popSettingsRoute = popSettingsRoute,
        exitSettingsToReturnView = exitSettingsToReturnView,
        openCurrentCoreSettings = openCurrentCoreSettings,
        openVisualizationSettings = openVisualizationSettings,
        openSelectedVisualizationSettings = openSelectedVisualizationSettings
    )
}
