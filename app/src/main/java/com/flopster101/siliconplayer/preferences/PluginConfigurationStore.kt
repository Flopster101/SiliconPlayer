package com.flopster101.siliconplayer

import android.content.SharedPreferences

internal fun loadPluginConfigurations(prefs: SharedPreferences) {
    val decoderNames = NativeBridge.getRegisteredDecoderNames()

    for (decoderName in decoderNames) {
        val enabled = prefs.getBoolean(AppPreferenceKeys.decoderEnabledKey(decoderName), true)
        NativeBridge.setDecoderEnabled(decoderName, enabled)

        val currentPriority = NativeBridge.getDecoderPriority(decoderName)
        val priority = prefs.getInt(AppPreferenceKeys.decoderPriorityKey(decoderName), currentPriority)
        if (priority != currentPriority) {
            NativeBridge.setDecoderPriority(decoderName, priority)
        }

        val extensionsString = prefs.getString(AppPreferenceKeys.decoderEnabledExtensionsKey(decoderName), "")
        if (!extensionsString.isNullOrEmpty()) {
            val extensions = extensionsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray()
            NativeBridge.setDecoderEnabledExtensions(decoderName, extensions)
        }
    }

    normalizeDecoderPriorityValues()
    persistAllPluginConfigurations(prefs)
}

internal fun savePluginConfiguration(prefs: SharedPreferences, decoderName: String) {
    val editor = prefs.edit()

    val enabled = NativeBridge.isDecoderEnabled(decoderName)
    editor.putBoolean(AppPreferenceKeys.decoderEnabledKey(decoderName), enabled)

    val priority = NativeBridge.getDecoderPriority(decoderName)
    editor.putInt(AppPreferenceKeys.decoderPriorityKey(decoderName), priority)

    val enabledExtensions = NativeBridge.getDecoderEnabledExtensions(decoderName)
    val supportedExtensions = NativeBridge.getDecoderSupportedExtensions(decoderName)
    if (enabledExtensions.size < supportedExtensions.size) {
        val extensionsString = enabledExtensions.joinToString(",")
        editor.putString(AppPreferenceKeys.decoderEnabledExtensionsKey(decoderName), extensionsString)
    } else {
        editor.remove(AppPreferenceKeys.decoderEnabledExtensionsKey(decoderName))
    }

    editor.apply()
}

internal fun normalizeDecoderPriorityValues() {
    val decoderNames = NativeBridge.getRegisteredDecoderNames().toList()
    val sorted = decoderNames.sortedWith(
        compareBy<String> { NativeBridge.getDecoderPriority(it) }
            .thenBy { it }
    )
    sorted.forEachIndexed { index, decoderName ->
        if (NativeBridge.getDecoderPriority(decoderName) != index) {
            NativeBridge.setDecoderPriority(decoderName, index)
        }
    }
}

internal fun persistAllPluginConfigurations(prefs: SharedPreferences) {
    NativeBridge.getRegisteredDecoderNames().forEach { decoderName ->
        savePluginConfiguration(prefs, decoderName)
    }
}

internal fun applyDecoderPriorityOrder(
    orderedDecoderNames: List<String>,
    prefs: SharedPreferences
) {
    orderedDecoderNames.forEachIndexed { index, decoderName ->
        NativeBridge.setDecoderPriority(decoderName, index)
    }
    normalizeDecoderPriorityValues()
    persistAllPluginConfigurations(prefs)
}

internal fun readPluginVolumeForDecoder(
    prefs: SharedPreferences,
    decoderName: String?
): Float {
    if (decoderName.isNullOrBlank()) return 0f
    return prefs.getFloat(AppPreferenceKeys.decoderPluginVolumeDbKey(decoderName), 0f)
}

internal fun writePluginVolumeForDecoder(
    prefs: SharedPreferences,
    decoderName: String?,
    valueDb: Float
) {
    if (decoderName.isNullOrBlank()) return
    prefs.edit()
        .putFloat(AppPreferenceKeys.decoderPluginVolumeDbKey(decoderName), valueDb)
        .apply()
}

internal fun clearAllDecoderPluginVolumes(prefs: SharedPreferences) {
    val editor = prefs.edit()
    editor.remove(AppPreferenceKeys.AUDIO_PLUGIN_VOLUME_DB)
    val decoderNames = NativeBridge.getRegisteredDecoderNames()
    for (decoderName in decoderNames) {
        editor.remove(AppPreferenceKeys.decoderPluginVolumeDbKey(decoderName))
    }
    editor.apply()
}
