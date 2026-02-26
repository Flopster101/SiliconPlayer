package com.flopster101.siliconplayer

import android.content.Context
import android.webkit.MimeTypeMap
import android.widget.Toast
import java.io.File
import java.util.Locale

internal enum class RemoteLoadPhase {
    Connecting,
    Downloading,
    Opening
}

internal data class RemoteLoadUiState(
    val sourceId: String,
    val phase: RemoteLoadPhase,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val bytesPerSecond: Long? = null,
    val percent: Int? = null,
    val indeterminate: Boolean = true
)

internal data class RemoteDownloadResult(
    val file: File?,
    val errorMessage: String? = null,
    val cancelled: Boolean = false
)

internal data class SubtuneEntry(
    val index: Int,
    val title: String,
    val artist: String,
    val durationSeconds: Double
)

internal fun formatByteCount(bytes: Long): String {
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

internal fun formatShortDuration(seconds: Double): String {
    if (seconds <= 0.0 || !seconds.isFinite()) return "--:--"
    val totalSeconds = seconds.toInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val remainingSeconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, remainingSeconds)
}

internal fun guessMimeTypeFromFilename(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (extension.isBlank()) return "application/octet-stream"
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        ?: "application/octet-stream"
}

internal val selectableVisualizationModes: List<VisualizationMode> = listOf(
    VisualizationMode.Bars,
    VisualizationMode.Oscilloscope,
    VisualizationMode.VuMeters,
    VisualizationMode.ChannelScope
)

private val visualizationModeStorageAliases: Map<String, VisualizationMode> = buildMap {
    selectableVisualizationModes.forEach { mode ->
        val normalizedStorage = mode.storageValue.lowercase(Locale.ROOT)
        put(normalizedStorage, mode)
        put(mode.name.lowercase(Locale.ROOT), mode)
        put(mode.label.lowercase(Locale.ROOT), mode)
        put(normalizedStorage.replace("_", ""), mode)
    }
    put("vumeters", VisualizationMode.VuMeters)
    put("vu", VisualizationMode.VuMeters)
    put("channelscope", VisualizationMode.ChannelScope)
}
private val visualizationModeAliasStripPattern = Regex("[^a-z0-9_]")

internal fun parseEnabledVisualizationModes(raw: String?): Set<VisualizationMode> {
    if (raw.isNullOrBlank()) return selectableVisualizationModes.toSet()
    val parsed = raw
        .split(',')
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotBlank() }
        .mapNotNull { value ->
            visualizationModeStorageAliases[value]
                ?: visualizationModeStorageAliases[value.replace(visualizationModeAliasStripPattern, "")]
        }
        .toSet()
    if (parsed.isEmpty()) return selectableVisualizationModes.toSet()
    return parsed
}

internal fun serializeEnabledVisualizationModes(modes: Set<VisualizationMode>): String {
    return selectableVisualizationModes
        .filter { modes.contains(it) }
        .joinToString(",") { it.storageValue }
}

internal fun isVisualizationModeSupported(
    mode: VisualizationMode,
    coreNameForUi: String?
): Boolean {
    return when (mode) {
        VisualizationMode.ChannelScope ->
            pluginNameForCoreName(coreNameForUi) == DecoderNames.LIB_OPEN_MPT

        else -> true
    }
}

internal fun isVisualizationModeSelectable(
    mode: VisualizationMode,
    enabledModes: Set<VisualizationMode>,
    coreNameForUi: String?
): Boolean {
    // Selection is the intersection of the user-enabled visualization pool and
    // what the current core can actually render at runtime.
    if (!enabledModes.contains(mode)) return false
    return isVisualizationModeSupported(mode, coreNameForUi)
}

internal fun defaultChannelScopeTextSizeSp(context: Context): Int {
    val tabletLike = context.resources.configuration.smallestScreenWidthDp >= 600
    return if (tabletLike) 10 else AppDefaults.Visualization.ChannelScope.textSizeSp
}

internal fun applyRepeatModeToNative(mode: RepeatMode) {
    NativeBridge.setRepeatMode(
        when (mode) {
            RepeatMode.None -> 0
            RepeatMode.Track -> 1
            RepeatMode.LoopPoint -> 2
            RepeatMode.Subtune -> 3
            RepeatMode.Playlist -> 0
        }
    )
}

internal fun showRepeatModeToast(context: Context, mode: RepeatMode) {
    Toast.makeText(context, mode.label, Toast.LENGTH_SHORT).show()
}
