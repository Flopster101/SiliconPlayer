package com.flopster101.siliconplayer.pluginsettings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Implementation of PluginSettingsBuilder.
 * Collects sections and renders them with proper labels.
 */
class PluginSettingsBuilderImpl : PluginSettingsBuilder {
    private val coreOptionsContent = mutableListOf<@Composable () -> Unit>()
    private val genericOutputOptionsContent = mutableListOf<@Composable () -> Unit>()

    override fun coreOptions(block: PluginSettingsSectionBuilder.() -> Unit) {
        val sectionBuilder = PluginSettingsSectionBuilderImpl()
        sectionBuilder.block()
        coreOptionsContent.addAll(sectionBuilder.getContent())
    }

    override fun genericOutputOptions(block: PluginSettingsSectionBuilder.() -> Unit) {
        val sectionBuilder = PluginSettingsSectionBuilderImpl()
        sectionBuilder.block()
        genericOutputOptionsContent.addAll(sectionBuilder.getContent())
    }

    /**
     * Check if core options section has content.
     */
    fun hasCoreOptions(): Boolean = coreOptionsContent.isNotEmpty()

    /**
     * Check if generic output options section has content.
     */
    fun hasGenericOutputOptions(): Boolean = genericOutputOptionsContent.isNotEmpty()

    /**
     * Get the core options content.
     */
    fun getCoreOptionsContent(): List<@Composable () -> Unit> = coreOptionsContent

    /**
     * Get the generic output options content.
     */
    fun getGenericOutputOptionsContent(): List<@Composable () -> Unit> = genericOutputOptionsContent
}

/**
 * Implementation of PluginSettingsSectionBuilder.
 * Collects composables for a section.
 */
class PluginSettingsSectionBuilderImpl : PluginSettingsSectionBuilder {
    private val content = mutableListOf<@Composable () -> Unit>()

    override fun custom(content: @Composable () -> Unit) {
        this.content.add(content)
    }

    override fun spacer(height: Int) {
        content.add {
            Spacer(modifier = Modifier.height(height.dp))
        }
    }

    /**
     * Get all the content added to this section.
     */
    fun getContent(): List<@Composable () -> Unit> = content
}


/**
 * Render plugin settings using the builder pattern.
 * This composable handles the section labels and layout.
 */
@Composable
fun RenderPluginSettings(
    pluginSettings: PluginSettings,
    settingsSectionLabel: @Composable (String) -> Unit
) {
    val builder = PluginSettingsBuilderImpl()
    pluginSettings.buildSettings(builder)

    // Render core options section if it has content
    if (builder.hasCoreOptions()) {
        Spacer(modifier = Modifier.height(16.dp))
        settingsSectionLabel("Core options")
        builder.getCoreOptionsContent().forEach { content ->
            content()
        }
    }

    // Render generic output options section if it has content
    if (builder.hasGenericOutputOptions()) {
        Spacer(modifier = Modifier.height(16.dp))
        settingsSectionLabel("Generic output options")
        builder.getGenericOutputOptionsContent().forEach { content ->
            content()
        }
    }
}
