package com.flopster101.siliconplayer

import android.content.SharedPreferences

internal enum class ChannelScopeVisibleElementId(
    val storageId: String,
    val defaultLabel: String,
    val shortLabel: String
) {
    Volume("volume", "Volume", "Vol"),
    EffectPrimary("effect_primary", "Effect", "FX"),
    EffectSecondary("effect_secondary", "Effect 2", "FX2"),
    Chip("chip", "Chip", "Chip"),
    Instrument("instrument", "Instrument", "Inst"),
    Sample("sample", "Sample", "Sample")
}

internal data class ChannelScopeVisibleElementOption(
    val coreId: String,
    val coreLabel: String,
    val elementId: ChannelScopeVisibleElementId,
    val label: String = elementId.defaultLabel
) {
    val storageKey: String
        get() = AppPreferenceKeys.visualizationChannelScopeVisibleElementKey(coreId, elementId.storageId)
}

internal fun channelScopeVisibleElementOptions(): List<ChannelScopeVisibleElementOption> {
    return listOf(
        ChannelScopeVisibleElementOption(
            coreId = "openmpt",
            coreLabel = "OpenMPT",
            elementId = ChannelScopeVisibleElementId.Volume
        ),
        ChannelScopeVisibleElementOption(
            coreId = "openmpt",
            coreLabel = "OpenMPT",
            elementId = ChannelScopeVisibleElementId.EffectPrimary
        ),
        ChannelScopeVisibleElementOption(
            coreId = "openmpt",
            coreLabel = "OpenMPT",
            elementId = ChannelScopeVisibleElementId.Instrument
        ),
        ChannelScopeVisibleElementOption(
            coreId = "openmpt",
            coreLabel = "OpenMPT",
            elementId = ChannelScopeVisibleElementId.Sample
        ),
        ChannelScopeVisibleElementOption(
            coreId = "furnace",
            coreLabel = "Furnace",
            elementId = ChannelScopeVisibleElementId.Volume
        ),
        ChannelScopeVisibleElementOption(
            coreId = "furnace",
            coreLabel = "Furnace",
            elementId = ChannelScopeVisibleElementId.EffectPrimary
        ),
        ChannelScopeVisibleElementOption(
            coreId = "furnace",
            coreLabel = "Furnace",
            elementId = ChannelScopeVisibleElementId.EffectSecondary
        ),
        ChannelScopeVisibleElementOption(
            coreId = "furnace",
            coreLabel = "Furnace",
            elementId = ChannelScopeVisibleElementId.Instrument
        ),
        ChannelScopeVisibleElementOption(
            coreId = "furnace",
            coreLabel = "Furnace",
            elementId = ChannelScopeVisibleElementId.Sample
        ),
        ChannelScopeVisibleElementOption(
            coreId = "gme",
            coreLabel = "Game Music Emu",
            elementId = ChannelScopeVisibleElementId.Volume
        ),
        ChannelScopeVisibleElementOption(
            coreId = "gme",
            coreLabel = "Game Music Emu",
            elementId = ChannelScopeVisibleElementId.Chip
        ),
        ChannelScopeVisibleElementOption(
            coreId = "klystrack",
            coreLabel = "Klystrack",
            elementId = ChannelScopeVisibleElementId.Volume
        ),
        ChannelScopeVisibleElementOption(
            coreId = "klystrack",
            coreLabel = "Klystrack",
            elementId = ChannelScopeVisibleElementId.EffectPrimary
        ),
        ChannelScopeVisibleElementOption(
            coreId = "klystrack",
            coreLabel = "Klystrack",
            elementId = ChannelScopeVisibleElementId.Instrument
        ),
        ChannelScopeVisibleElementOption(
            coreId = "hivelytracker",
            coreLabel = "HivelyTracker",
            elementId = ChannelScopeVisibleElementId.Volume
        ),
        ChannelScopeVisibleElementOption(
            coreId = "hivelytracker",
            coreLabel = "HivelyTracker",
            elementId = ChannelScopeVisibleElementId.EffectPrimary,
            label = "Effect 1"
        ),
        ChannelScopeVisibleElementOption(
            coreId = "hivelytracker",
            coreLabel = "HivelyTracker",
            elementId = ChannelScopeVisibleElementId.EffectSecondary,
            label = "Effect 2"
        ),
        ChannelScopeVisibleElementOption(
            coreId = "hivelytracker",
            coreLabel = "HivelyTracker",
            elementId = ChannelScopeVisibleElementId.Instrument
        ),
        ChannelScopeVisibleElementOption(
            coreId = "uade",
            coreLabel = "UADE",
            elementId = ChannelScopeVisibleElementId.Volume
        ),
        ChannelScopeVisibleElementOption(
            coreId = "sc68",
            coreLabel = "SC68",
            elementId = ChannelScopeVisibleElementId.Volume
        )
    )
}

internal fun defaultChannelScopeVisibleElementSelection(): Set<String> {
    return channelScopeVisibleElementOptions()
        .mapTo(linkedSetOf()) { it.storageKey }
}

internal fun readChannelScopeVisibleElementSelection(sharedPrefs: SharedPreferences): Set<String> {
    val defaults = defaultChannelScopeVisibleElementSelection()
    return channelScopeVisibleElementOptions()
        .mapNotNullTo(linkedSetOf()) { option ->
            if (sharedPrefs.getBoolean(option.storageKey, defaults.contains(option.storageKey))) {
                option.storageKey
            } else {
                null
            }
        }
}

internal fun SharedPreferences.Editor.putChannelScopeVisibleElementSelection(
    selectedStorageKeys: Set<String>
): SharedPreferences.Editor {
    channelScopeVisibleElementOptions().forEach { option ->
        putBoolean(option.storageKey, selectedStorageKeys.contains(option.storageKey))
    }
    return this
}

internal fun channelScopeVisibleElementsSummary(selectedStorageKeys: Set<String>): String {
    val count = channelScopeVisibleElementOptions()
        .count { selectedStorageKeys.contains(it.storageKey) }
    return when {
        count <= 0 -> "None"
        count == 1 -> "1 selected"
        else -> "$count selected"
    }
}

internal fun channelScopeCoreIdForDecoderName(decoderName: String?): String? {
    return when (pluginNameForCoreName(decoderName)) {
        DecoderNames.LIB_OPEN_MPT -> "openmpt"
        DecoderNames.FURNACE -> "furnace"
        DecoderNames.GAME_MUSIC_EMU -> "gme"
        DecoderNames.SC68 -> "sc68"
        DecoderNames.KLYSTRACK -> "klystrack"
        DecoderNames.HIVELY_TRACKER -> "hivelytracker"
        DecoderNames.UADE -> "uade"
        else -> null
    }
}

internal fun isChannelScopeVisibleElementEnabled(
    selectedStorageKeys: Set<String>,
    decoderName: String?,
    elementId: ChannelScopeVisibleElementId
): Boolean {
    val coreId = channelScopeCoreIdForDecoderName(decoderName) ?: return false
    val storageKey = AppPreferenceKeys.visualizationChannelScopeVisibleElementKey(
        coreId = coreId,
        elementId = elementId.storageId
    )
    return selectedStorageKeys.contains(storageKey)
}
