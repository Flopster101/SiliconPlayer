package com.flopster101.siliconplayer

internal enum class MainView {
    Home,
    Network,
    Browser,
    Settings
}

enum class SettingsRoute {
    Root,
    AudioPlugins,
    PluginDetail,
    PluginVgmPlayChipSettings,
    PluginFfmpeg,
    PluginOpenMpt,
    PluginVgmPlay,
    UrlCache,
    CacheManager,
    GeneralAudio,
    Home,
    FileBrowser,
    Network,
    Player,
    Visualization,
    VisualizationBasic,
    VisualizationBasicBars,
    VisualizationBasicOscilloscope,
    VisualizationBasicVuMeters,
    VisualizationAdvanced,
    VisualizationAdvancedChannelScope,
    Misc,
    Ui,
    About
}

internal fun mainViewOrder(view: MainView): Int = when (view) {
    MainView.Home -> 0
    MainView.Network -> 1
    MainView.Browser -> 2
    MainView.Settings -> 3
}

internal fun settingsRouteOrder(route: SettingsRoute): Int = when (route) {
    SettingsRoute.Root -> 0
    SettingsRoute.AudioPlugins -> 1
    SettingsRoute.PluginDetail -> 2
    SettingsRoute.PluginVgmPlayChipSettings -> 3
    SettingsRoute.PluginFfmpeg -> 2
    SettingsRoute.PluginOpenMpt -> 2
    SettingsRoute.PluginVgmPlay -> 2
    SettingsRoute.UrlCache -> 1
    SettingsRoute.CacheManager -> 2
    SettingsRoute.GeneralAudio -> 1
    SettingsRoute.Home -> 1
    SettingsRoute.FileBrowser -> 1
    SettingsRoute.Network -> 1
    SettingsRoute.Player -> 1
    SettingsRoute.Visualization -> 1
    SettingsRoute.VisualizationBasic -> 2
    SettingsRoute.VisualizationBasicBars -> 3
    SettingsRoute.VisualizationBasicOscilloscope -> 3
    SettingsRoute.VisualizationBasicVuMeters -> 3
    SettingsRoute.VisualizationAdvanced -> 2
    SettingsRoute.VisualizationAdvancedChannelScope -> 3
    SettingsRoute.Misc -> 1
    SettingsRoute.Ui -> 1
    SettingsRoute.About -> 1
}
