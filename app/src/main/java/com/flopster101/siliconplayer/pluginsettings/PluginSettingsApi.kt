package com.flopster101.siliconplayer.pluginsettings

import androidx.compose.runtime.Composable

/**
 * Interface for plugin-specific settings.
 * Each decoder plugin implements this to provide its settings UI.
 */
interface PluginSettings {
    /**
     * Build the settings UI for this plugin.
     * @param builder The builder to add settings sections and controls to
     */
    @Composable
    fun buildSettings(builder: PluginSettingsBuilder)
}

/**
 * Builder for constructing plugin settings UI in a structured way.
 * Provides sections for core options and generic output options.
 */
interface PluginSettingsBuilder {
    /**
     * Add core-specific options section.
     * This section only appears if options are added to it.
     */
    fun coreOptions(block: PluginSettingsSectionBuilder.() -> Unit)

    /**
     * Add generic output options section.
     * This section always appears and typically contains sample rate settings.
     */
    fun genericOutputOptions(block: PluginSettingsSectionBuilder.() -> Unit)
}

/**
 * Builder for a settings section (Core options or Generic output options).
 * Provides methods to add various types of settings controls.
 */
interface PluginSettingsSectionBuilder {
    /**
     * Add a composable directly to the section.
     * Use this for custom UI or when the provided helpers don't fit.
     */
    fun custom(content: @Composable () -> Unit)

    /**
     * Add spacing between items.
     */
    fun spacer(height: Int = 10)
}

/**
 * Registry for plugin settings.
 * Plugins register themselves here so the UI can look them up by name.
 */
object PluginSettingsRegistry {
    private val registry = mutableMapOf<String, PluginSettings>()

    /**
     * Register a plugin's settings.
     * @param pluginName The name of the plugin (e.g., "FFmpeg", "LibOpenMPT")
     * @param settings The settings implementation for this plugin
     */
    fun register(pluginName: String, settings: PluginSettings) {
        registry[pluginName] = settings
    }

    /**
     * Get the settings for a plugin by name.
     * @param pluginName The name of the plugin
     * @return The plugin settings, or null if not registered
     */
    fun get(pluginName: String): PluginSettings? {
        return registry[pluginName]
    }

    /**
     * Check if a plugin has registered settings.
     */
    fun hasSettings(pluginName: String): Boolean {
        return registry.containsKey(pluginName)
    }
}
