package com.flopster101.siliconplayer

import java.util.Locale

internal enum class CacheSizeUnit(
    val label: String,
    val bytesPerUnit: Double
) {
    MB("MB", 1024.0 * 1024.0),
    GB("GB", 1024.0 * 1024.0 * 1024.0)
}

internal fun formatCacheByteCount(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}

internal fun stripCachedFileHashPrefix(fileName: String): String {
    val hashPrefix = Regex("^[0-9a-fA-F]{40}_(.+)$")
    return hashPrefix.matchEntire(fileName)?.groupValues?.getOrNull(1) ?: fileName
}

internal data class IntChoice(val value: Int, val label: String)

internal fun formatMilliBelAsDbLabel(milliBel: Int): String {
    val db = milliBel / 100.0
    return String.format(Locale.US, "%+.1f dB", db)
}

internal enum class SettingsResetAction {
    ClearAllSettings,
    ClearPluginSettings
}

enum class CoreOptionApplyPolicy {
    Live,
    RequiresPlaybackRestart
}
