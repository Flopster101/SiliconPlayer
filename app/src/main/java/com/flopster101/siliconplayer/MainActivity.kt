package com.flopster101.siliconplayer

import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Public
import androidx.compose.runtime.* // Import for remember, mutableStateOf
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.vectorResource
import com.flopster101.siliconplayer.ui.theme.SiliconPlayerTheme
import com.flopster101.siliconplayer.ui.dialogs.AudioEffectsDialog
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import android.content.Intent
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings
import android.os.Environment
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private const val URL_SOURCE_TAG = "UrlSource"

private enum class RemoteLoadPhase {
    Connecting,
    Downloading,
    Opening
}

private data class RemoteLoadUiState(
    val sourceId: String,
    val phase: RemoteLoadPhase,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val bytesPerSecond: Long? = null,
    val percent: Int? = null,
    val indeterminate: Boolean = true
)

private data class RemoteDownloadResult(
    val file: File?,
    val errorMessage: String? = null,
    val cancelled: Boolean = false
)

private data class SubtuneEntry(
    val index: Int,
    val title: String,
    val artist: String,
    val durationSeconds: Double
)

private fun formatByteCount(bytes: Long): String {
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

private fun formatShortDuration(seconds: Double): String {
    if (seconds <= 0.0 || !seconds.isFinite()) return "--:--"
    val totalSeconds = seconds.toInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val remainingSeconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, remainingSeconds)
}

private fun guessMimeTypeFromFilename(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (extension.isBlank()) return "application/octet-stream"
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        ?: "application/octet-stream"
}

private val selectableVisualizationModes: List<VisualizationMode> = listOf(
    VisualizationMode.Bars,
    VisualizationMode.Oscilloscope,
    VisualizationMode.VuMeters,
    VisualizationMode.ChannelScope
)

private fun parseEnabledVisualizationModes(raw: String?): Set<VisualizationMode> {
    if (raw.isNullOrBlank()) return selectableVisualizationModes.toSet()
    val parsed = raw
        .split(',')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { value ->
            selectableVisualizationModes.firstOrNull { it.storageValue == value }
        }
        .toSet()
    if (parsed.isEmpty()) return selectableVisualizationModes.toSet()
    return parsed
}

private fun serializeEnabledVisualizationModes(modes: Set<VisualizationMode>): String {
    return selectableVisualizationModes
        .filter { modes.contains(it) }
        .joinToString(",") { it.storageValue }
}

private fun isVisualizationModeSupported(
    mode: VisualizationMode,
    coreNameForUi: String?
): Boolean {
    return when (mode) {
        VisualizationMode.ChannelScope ->
            pluginNameForCoreName(coreNameForUi) == "LibOpenMPT"
        else -> true
    }
}

private fun isVisualizationModeSelectable(
    mode: VisualizationMode,
    enabledModes: Set<VisualizationMode>,
    coreNameForUi: String?
): Boolean {
    if (!isVisualizationModeSupported(mode, coreNameForUi)) return false
    return when (mode) {
        // Specialized mode is availability-driven for now; per-core toggles can be added later.
        VisualizationMode.ChannelScope -> true
        else -> enabledModes.contains(mode)
    }
}


private enum class MainView {
    Home,
    Browser,
    Settings
}

enum class SettingsRoute {
    Root,
    AudioPlugins,
    PluginDetail, // Generic plugin detail screen (plugin name passed separately)
    PluginVgmPlayChipSettings,
    PluginFfmpeg,
    PluginOpenMpt,
    PluginVgmPlay,
    UrlCache,
    CacheManager,
    GeneralAudio,
    Home,
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

enum class ThemeMode(val storageValue: String, val label: String) {
    Auto("auto", "Auto"),
    Light("light", "Light"),
    Dark("dark", "Dark");

    companion object {
        fun fromStorage(value: String?): ThemeMode {
            return entries.firstOrNull { it.storageValue == value } ?: Auto
        }
    }
}

enum class AudioBackendPreference(val storageValue: String, val label: String, val nativeValue: Int) {
    AAudio("aaudio", "AAudio", 1),
    OpenSLES("opensl", "OpenSL ES", 2),
    AudioTrack("audiotrack", "AudioTrack", 3);

    companion object {
        fun fromStorage(value: String?): AudioBackendPreference {
            return entries.firstOrNull { it.storageValue == value } ?: AAudio
        }
    }
}

enum class AudioPerformanceMode(val storageValue: String, val label: String, val nativeValue: Int) {
    LowLatency("low_latency", "Low latency", 1),
    None("none", "None", 2),
    PowerSaving("power_saving", "Power saving", 3);

    companion object {
        fun fromStorage(value: String?): AudioPerformanceMode {
            return entries.firstOrNull { it.storageValue == value } ?: None
        }
    }
}

enum class AudioBufferPreset(val storageValue: String, val label: String, val nativeValue: Int) {
    Small("small", "Small", 1),
    Medium("medium", "Medium", 2),
    Large("large", "Large", 3);

    companion object {
        fun fromStorage(value: String?): AudioBufferPreset {
            return entries.firstOrNull { it.storageValue == value } ?: Medium
        }
    }
}

enum class AudioResamplerPreference(val storageValue: String, val label: String, val nativeValue: Int) {
    BuiltIn("builtin", "Built-in", 1),
    Sox("sox", "SoX (Experimental)", 2);

    companion object {
        fun fromStorage(value: String?): AudioResamplerPreference {
            return entries.firstOrNull { it.storageValue == value } ?: BuiltIn
        }
    }
}

enum class FilenameDisplayMode(val storageValue: String, val label: String) {
    Always("always", "Always"),
    Never("never", "Never"),
    TrackerOnly("tracker_only", "Tracker/Chiptune formats only");

    companion object {
        fun fromStorage(value: String?): FilenameDisplayMode {
            return entries.firstOrNull { it.storageValue == value } ?: Always
        }
    }
}

enum class EndFadeCurve(val storageValue: String, val label: String, val nativeValue: Int) {
    Linear("linear", "Linear", 0),
    EaseIn("ease_in", "Ease-in", 1),
    EaseOut("ease_out", "Ease-out", 2);

    companion object {
        fun fromStorage(value: String?): EndFadeCurve {
            return entries.firstOrNull { it.storageValue == value } ?: Linear
        }
    }
}

private fun normalizeSourceIdentity(path: String?): String? {
    if (path.isNullOrBlank()) return null
    val trimmed = path.trim()
    val uri = Uri.parse(trimmed)
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    return when (scheme) {
        "http", "https" -> uri.normalizeScheme().toString()
        "file" -> {
            val localPath = uri.path?.takeIf { it.isNotBlank() } ?: return null
            try {
                File(localPath).canonicalFile.absolutePath
            } catch (_: Exception) {
                File(localPath).absoluteFile.normalize().path
            }
        }
        else -> {
            try {
                File(trimmed).canonicalFile.absolutePath
            } catch (_: Exception) {
                File(trimmed).absoluteFile.normalize().path
            }
        }
    }
}

internal fun samePath(a: String?, b: String?): Boolean {
    val left = normalizeSourceIdentity(a) ?: return false
    val right = normalizeSourceIdentity(b) ?: return false
    return left == right
}

private enum class ManualSourceType {
    LocalFile,
    LocalDirectory,
    RemoteUrl
}

private data class ManualSourceResolution(
    val type: ManualSourceType,
    val sourceId: String,
    val requestUrl: String,
    val localFile: File?,
    val directoryPath: String?,
    val displayFile: File?
)

private data class ManualSourceOpenOptions(
    val forceCaching: Boolean = false
)

private fun resolvePlaybackSourceLabel(
    selectedFile: File?,
    sourceId: String?
): String? {
    if (selectedFile == null) return null
    val normalizedSource = normalizeSourceIdentity(sourceId ?: selectedFile.absolutePath) ?: return "Local"
    val scheme = Uri.parse(normalizedSource).scheme?.lowercase(Locale.ROOT)
    val isRemote = scheme == "http" || scheme == "https"
    if (!isRemote) return "Local"
    return if (selectedFile.absolutePath.contains("/cache/remote_sources/")) {
        "Streamed (cached)"
    } else {
        "Streamed"
    }
}

private fun resolveManualSourceInput(rawInput: String): ManualSourceResolution? {
    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) return null

    val uri = Uri.parse(trimmed)
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    if (scheme == "http" || scheme == "https") {
        if (uri.host.isNullOrBlank()) return null
        val normalizedUrl = uri.normalizeScheme().toString()
        val requestUrl = stripUrlFragment(normalizedUrl)
        val safeName = remoteFilenameHintFromUri(uri) ?: sanitizeRemoteLeafName(uri.host) ?: "remote"
        return ManualSourceResolution(
            type = ManualSourceType.RemoteUrl,
            sourceId = normalizedUrl,
            requestUrl = requestUrl,
            localFile = null,
            directoryPath = null,
            displayFile = File("/virtual/remote/$safeName")
        )
    }

    fun resolveLocalPath(path: String, sourceIdOverride: String? = null): ManualSourceResolution? {
        val file = File(path).absoluteFile
        if (!file.exists()) return null
        if (file.isDirectory) {
            return ManualSourceResolution(
                type = ManualSourceType.LocalDirectory,
                sourceId = sourceIdOverride ?: file.absolutePath,
                requestUrl = sourceIdOverride ?: file.absolutePath,
                localFile = null,
                directoryPath = file.absolutePath,
                displayFile = null
            )
        }
        if (file.isFile) {
            return ManualSourceResolution(
                type = ManualSourceType.LocalFile,
                sourceId = sourceIdOverride ?: file.absolutePath,
                requestUrl = sourceIdOverride ?: file.absolutePath,
                localFile = file,
                directoryPath = null,
                displayFile = file
            )
        }
        return null
    }

    if (scheme == "file") {
        val localPath = uri.path?.takeIf { it.isNotBlank() } ?: return null
        return resolveLocalPath(localPath, sourceIdOverride = uri.normalizeScheme().toString())
    }

    val expandedPath = when {
        trimmed == "~" -> System.getProperty("user.home") ?: trimmed
        trimmed.startsWith("~/") -> {
            val home = System.getProperty("user.home") ?: return null
            home + trimmed.removePrefix("~")
        }
        else -> trimmed
    }
    return resolveLocalPath(expandedPath)
}

internal data class RecentPathEntry(
    val path: String,
    val locationId: String?,
    val title: String? = null,
    val artist: String? = null
)

private data class StorageDescriptor(
    val rootPath: String,
    val label: String,
    val icon: ImageVector
)

internal data class StoragePresentation(
    val label: String,
    val icon: ImageVector,
    val qualifier: String? = null
)

private fun applyRepeatModeToNative(mode: RepeatMode) {
    NativeBridge.setRepeatMode(
        when (mode) {
            RepeatMode.None -> 0
            RepeatMode.Track -> 1
            RepeatMode.LoopPoint -> 2
            RepeatMode.Subtune -> 3
        }
    )
}

private fun showRepeatModeToast(context: Context, mode: RepeatMode) {
    Toast.makeText(context, mode.label, Toast.LENGTH_SHORT).show()
}

private fun resolveStorageRootFromAppDir(appSpecificDir: File): File? {
    val marker = "/Android/"
    val absolutePath = appSpecificDir.absolutePath
    val markerIndex = absolutePath.indexOf(marker)
    if (markerIndex <= 0) return null
    return File(absolutePath.substring(0, markerIndex))
}

private fun detectStorageDescriptors(context: Context): List<StorageDescriptor> {
    val descriptors = mutableListOf<StorageDescriptor>()
    val seen = mutableSetOf<String>()

    fun add(path: String, label: String, icon: ImageVector) {
        if (path in seen) return
        seen += path
        descriptors += StorageDescriptor(path, label, icon)
    }

    add("/", "Root (/)", Icons.Default.Folder)
    val internalRoot = Environment.getExternalStorageDirectory().absolutePath
    val internalIcon = if (context.resources.configuration.smallestScreenWidthDp >= 600) {
        Icons.Default.TabletAndroid
    } else {
        Icons.Default.PhoneAndroid
    }
    add(internalRoot, "Internal storage", internalIcon)

    context.getExternalFilesDirs(null)
        .orEmpty()
        .forEach { externalDir ->
            if (externalDir == null) return@forEach
            val volumeRoot = resolveStorageRootFromAppDir(externalDir) ?: return@forEach
            if (!Environment.isExternalStorageRemovable(externalDir)) return@forEach

            val lower = volumeRoot.absolutePath.lowercase()
            val isUsb = lower.contains("usb") || lower.contains("otg")
            val volumeName = volumeRoot.name.ifBlank { volumeRoot.absolutePath }
            val label = volumeName
            add(volumeRoot.absolutePath, label, if (isUsb) Icons.Default.Usb else Icons.Default.SdCard)
        }

    return descriptors
}

private fun storagePresentationForEntry(
    context: Context,
    entry: RecentPathEntry,
    descriptors: List<StorageDescriptor>
): StoragePresentation {
    val parsed = Uri.parse(entry.path)
    val scheme = parsed.scheme?.lowercase(Locale.ROOT)
    if (scheme == "http" || scheme == "https") {
        val hostLabel = parsed.host?.takeIf { it.isNotBlank() } ?: "unknown host"
        val protocolLabel = scheme.uppercase(Locale.ROOT)
        val qualifier = if (isRemoteSourceCached(context, entry.path)) "Cached" else null
        return StoragePresentation(
            label = "$protocolLabel ($hostLabel)",
            icon = Icons.Default.Public,
            qualifier = qualifier
        )
    }

    val pathForMatching = when (scheme) {
        "file" -> parsed.path?.takeIf { it.isNotBlank() } ?: entry.path
        else -> entry.path
    }

    entry.locationId?.let { locationId ->
        descriptors.firstOrNull { it.rootPath == locationId }?.let {
            return StoragePresentation(label = it.label, icon = it.icon)
        }
    }
    val matching = descriptors
        .filter { pathForMatching == it.rootPath || pathForMatching.startsWith("${it.rootPath}/") }
        .maxByOrNull { it.rootPath.length }
    return if (matching != null) {
        StoragePresentation(label = matching.label, icon = matching.icon)
    } else {
        StoragePresentation(label = "Unknown storage", icon = Icons.Default.Folder)
    }
}

private fun readRecentEntries(
    prefs: android.content.SharedPreferences,
    key: String,
    maxItems: Int
): List<RecentPathEntry> {
    val raw = prefs.getString(key, null) ?: return emptyList()
    return try {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val objectValue = array.optJSONObject(index) ?: continue
                val path = objectValue.optString("path", "").trim()
                if (path.isBlank()) continue
                val locationId = objectValue.optString("locationId", "").ifBlank { null }
                val title = objectValue.optString("title", "").ifBlank { null }
                val artist = objectValue.optString("artist", "").ifBlank { null }
                add(
                    RecentPathEntry(
                        path = path,
                        locationId = locationId,
                        title = title,
                        artist = artist
                    )
                )
                if (size >= maxItems) break
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun writeRecentEntries(
    prefs: android.content.SharedPreferences,
    key: String,
    entries: List<RecentPathEntry>,
    maxItems: Int
) {
    val trimmed = entries.take(maxItems)
    val array = JSONArray()
    trimmed.forEach { entry ->
        array.put(
            JSONObject()
                .put("path", entry.path)
                .put("locationId", entry.locationId ?: "")
                .put("title", entry.title ?: "")
                .put("artist", entry.artist ?: "")
        )
    }
    prefs.edit().putString(key, array.toString()).apply()
}

private fun mainViewOrder(view: MainView): Int = when (view) {
    MainView.Home -> 0
    MainView.Browser -> 1
    MainView.Settings -> 2
}

private fun settingsRouteOrder(route: SettingsRoute): Int = when (route) {
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

private const val PAGE_NAV_DURATION_MS = 300
private const val RECENT_FOLDERS_LIMIT_DEFAULT = 3
private const val RECENT_FILES_LIMIT_DEFAULT = 5
private const val RECENTS_LIMIT_MAX = 50
internal const val PREVIOUS_RESTART_THRESHOLD_SECONDS = 3.0

private object AppPreferenceKeys {
    const val PREFS_NAME = "silicon_player_settings"
    const val AUTO_PLAY_ON_TRACK_SELECT = "auto_play_on_track_select"
    const val OPEN_PLAYER_ON_TRACK_SELECT = "open_player_on_track_select"
    const val AUTO_PLAY_NEXT_TRACK_ON_END = "auto_play_next_track_on_end"
    const val PREVIOUS_RESTART_AFTER_THRESHOLD = "previous_restart_after_threshold"
    const val REMEMBER_BROWSER_LOCATION = "remember_browser_location"
    const val BROWSER_LAST_LOCATION_ID = "browser_last_location_id"
    const val BROWSER_LAST_DIRECTORY_PATH = "browser_last_directory_path"
    const val RESPOND_HEADPHONE_MEDIA_BUTTONS = "respond_headphone_media_buttons"
    const val PAUSE_ON_HEADPHONE_DISCONNECT = "pause_on_headphone_disconnect"
    const val AUDIO_BACKEND_PREFERENCE = "audio_backend_preference"
    const val AUDIO_PERFORMANCE_MODE = "audio_performance_mode"
    const val AUDIO_BUFFER_PRESET = "audio_buffer_preset"
    const val AUDIO_RESAMPLER_PREFERENCE = "audio_resampler_preference"
    const val AUDIO_ALLOW_BACKEND_FALLBACK = "audio_allow_backend_fallback"
    const val OPEN_PLAYER_FROM_NOTIFICATION = "open_player_from_notification"
    const val PERSIST_REPEAT_MODE = "persist_repeat_mode"
    const val PREFERRED_REPEAT_MODE = "preferred_repeat_mode"
    const val SESSION_CURRENT_PATH = "session_current_path"
    const val THEME_MODE = "theme_mode"
    const val RECENT_FOLDERS = "recent_folders"
    const val RECENT_PLAYED_FILES = "recent_played_files"
    const val RECENT_FOLDERS_LIMIT = "recent_folders_limit"
    const val RECENT_PLAYED_FILES_LIMIT = "recent_played_files_limit"
    const val KEEP_SCREEN_ON = "keep_screen_on"
    const val PLAYER_ARTWORK_CORNER_RADIUS_DP = "player_artwork_corner_radius_dp"
    const val AUDIO_FOCUS_INTERRUPT = "audio_focus_interrupt"
    const val AUDIO_DUCKING = "audio_ducking"
    const val AUDIO_MASTER_VOLUME_DB = "audio_master_volume_db"
    const val AUDIO_PLUGIN_VOLUME_DB = "audio_plugin_volume_db"
    const val AUDIO_FORCE_MONO = "audio_force_mono"
    const val URL_PATH_FORCE_CACHING = "url_path_force_caching"
    const val URL_CACHE_CLEAR_ON_LAUNCH = "url_cache_clear_on_launch"
    const val URL_CACHE_MAX_TRACKS = "url_cache_max_tracks"
    const val URL_CACHE_MAX_BYTES = "url_cache_max_bytes"
    const val FILENAME_DISPLAY_MODE = "filename_display_mode"
    const val FILENAME_ONLY_WHEN_TITLE_MISSING = "filename_only_when_title_missing"
    const val UNKNOWN_TRACK_DURATION_SECONDS = "unknown_track_duration_seconds"
    const val END_FADE_APPLY_TO_ALL_TRACKS = "end_fade_apply_to_all_tracks"
    const val END_FADE_DURATION_MS = "end_fade_duration_ms"
    const val END_FADE_CURVE = "end_fade_curve"
    const val VISUALIZATION_MODE = "visualization_mode"
    const val VISUALIZATION_ENABLED_MODES = "visualization_enabled_modes"
    const val VISUALIZATION_SHOW_DEBUG_INFO = "visualization_show_debug_info"
    const val VISUALIZATION_BAR_COUNT = "visualization_bar_count"
    const val VISUALIZATION_BAR_SMOOTHING_PERCENT = "visualization_bar_smoothing_percent"
    const val VISUALIZATION_BAR_ROUNDNESS_DP = "visualization_bar_roundness_dp"
    const val VISUALIZATION_BAR_OVERLAY_ARTWORK = "visualization_bar_overlay_artwork"
    const val VISUALIZATION_BAR_USE_THEME_COLOR = "visualization_bar_use_theme_color"
    const val VISUALIZATION_BAR_COLOR_MODE_NO_ARTWORK = "visualization_bar_color_mode_no_artwork"
    const val VISUALIZATION_BAR_COLOR_MODE_WITH_ARTWORK = "visualization_bar_color_mode_with_artwork"
    const val VISUALIZATION_BAR_CUSTOM_COLOR_ARGB = "visualization_bar_custom_color_argb"
    const val VISUALIZATION_OSC_STEREO = "visualization_osc_stereo"
    const val VISUALIZATION_OSC_WINDOW_MS = "visualization_osc_window_ms"
    const val VISUALIZATION_OSC_TRIGGER_MODE = "visualization_osc_trigger_mode"
    const val VISUALIZATION_OSC_FPS_MODE = "visualization_osc_fps_mode"
    const val VISUALIZATION_OSC_LINE_WIDTH_DP = "visualization_osc_line_width_dp"
    const val VISUALIZATION_OSC_GRID_WIDTH_DP = "visualization_osc_grid_width_dp"
    const val VISUALIZATION_OSC_VERTICAL_GRID_ENABLED = "visualization_osc_vertical_grid_enabled"
    const val VISUALIZATION_OSC_CENTER_LINE_ENABLED = "visualization_osc_center_line_enabled"
    const val VISUALIZATION_OSC_LINE_COLOR_MODE_NO_ARTWORK = "visualization_osc_line_color_mode_no_artwork"
    const val VISUALIZATION_OSC_GRID_COLOR_MODE_NO_ARTWORK = "visualization_osc_grid_color_mode_no_artwork"
    const val VISUALIZATION_OSC_LINE_COLOR_MODE_WITH_ARTWORK = "visualization_osc_line_color_mode_with_artwork"
    const val VISUALIZATION_OSC_GRID_COLOR_MODE_WITH_ARTWORK = "visualization_osc_grid_color_mode_with_artwork"
    const val VISUALIZATION_OSC_CUSTOM_LINE_COLOR_ARGB = "visualization_osc_custom_line_color_argb"
    const val VISUALIZATION_OSC_CUSTOM_GRID_COLOR_ARGB = "visualization_osc_custom_grid_color_argb"
    const val VISUALIZATION_VU_ANCHOR = "visualization_vu_anchor"
    const val VISUALIZATION_VU_USE_THEME_COLOR = "visualization_vu_use_theme_color"
    const val VISUALIZATION_VU_SMOOTHING_PERCENT = "visualization_vu_smoothing_percent"
    const val VISUALIZATION_VU_COLOR_MODE_NO_ARTWORK = "visualization_vu_color_mode_no_artwork"
    const val VISUALIZATION_VU_COLOR_MODE_WITH_ARTWORK = "visualization_vu_color_mode_with_artwork"
    const val VISUALIZATION_VU_CUSTOM_COLOR_ARGB = "visualization_vu_custom_color_argb"
    const val VISUALIZATION_CHANNEL_SCOPE_WINDOW_MS = "visualization_channel_scope_window_ms"
    const val VISUALIZATION_CHANNEL_SCOPE_RENDER_BACKEND = "visualization_channel_scope_render_backend"
    const val VISUALIZATION_CHANNEL_SCOPE_DC_REMOVAL_ENABLED = "visualization_channel_scope_dc_removal_enabled"
    const val VISUALIZATION_CHANNEL_SCOPE_GAIN_PERCENT = "visualization_channel_scope_gain_percent"
    const val VISUALIZATION_CHANNEL_SCOPE_TRIGGER_MODE = "visualization_channel_scope_trigger_mode"
    const val VISUALIZATION_CHANNEL_SCOPE_FPS_MODE = "visualization_channel_scope_fps_mode"
    const val VISUALIZATION_CHANNEL_SCOPE_LINE_WIDTH_DP = "visualization_channel_scope_line_width_dp"
    const val VISUALIZATION_CHANNEL_SCOPE_GRID_WIDTH_DP = "visualization_channel_scope_grid_width_dp"
    const val VISUALIZATION_CHANNEL_SCOPE_VERTICAL_GRID_ENABLED = "visualization_channel_scope_vertical_grid_enabled"
    const val VISUALIZATION_CHANNEL_SCOPE_CENTER_LINE_ENABLED = "visualization_channel_scope_center_line_enabled"
    const val VISUALIZATION_CHANNEL_SCOPE_SHOW_ARTWORK_BACKGROUND = "visualization_channel_scope_show_artwork_background"
    const val VISUALIZATION_CHANNEL_SCOPE_BACKGROUND_MODE = "visualization_channel_scope_background_mode"
    const val VISUALIZATION_CHANNEL_SCOPE_CUSTOM_BACKGROUND_COLOR_ARGB = "visualization_channel_scope_custom_background_color_argb"
    const val VISUALIZATION_CHANNEL_SCOPE_LAYOUT = "visualization_channel_scope_layout"
    const val VISUALIZATION_CHANNEL_SCOPE_LINE_COLOR_MODE_NO_ARTWORK = "visualization_channel_scope_line_color_mode_no_artwork"
    const val VISUALIZATION_CHANNEL_SCOPE_GRID_COLOR_MODE_NO_ARTWORK = "visualization_channel_scope_grid_color_mode_no_artwork"
    const val VISUALIZATION_CHANNEL_SCOPE_LINE_COLOR_MODE_WITH_ARTWORK = "visualization_channel_scope_line_color_mode_with_artwork"
    const val VISUALIZATION_CHANNEL_SCOPE_GRID_COLOR_MODE_WITH_ARTWORK = "visualization_channel_scope_grid_color_mode_with_artwork"
    const val VISUALIZATION_CHANNEL_SCOPE_CUSTOM_LINE_COLOR_ARGB = "visualization_channel_scope_custom_line_color_argb"
    const val VISUALIZATION_CHANNEL_SCOPE_CUSTOM_GRID_COLOR_ARGB = "visualization_channel_scope_custom_grid_color_argb"
    const val VISUALIZATION_CHANNEL_SCOPE_TEXT_ENABLED = "visualization_channel_scope_text_enabled"
    const val VISUALIZATION_CHANNEL_SCOPE_TEXT_ANCHOR = "visualization_channel_scope_text_anchor"
    const val VISUALIZATION_CHANNEL_SCOPE_TEXT_PADDING_DP = "visualization_channel_scope_text_padding_dp"
    const val VISUALIZATION_CHANNEL_SCOPE_TEXT_SIZE_SP = "visualization_channel_scope_text_size_sp"
    const val VISUALIZATION_CHANNEL_SCOPE_TEXT_NOTE_FORMAT = "visualization_channel_scope_text_note_format"
    const val VISUALIZATION_CHANNEL_SCOPE_TEXT_SHOW_CHANNEL = "visualization_channel_scope_text_show_channel"
    const val VISUALIZATION_CHANNEL_SCOPE_TEXT_SHOW_NOTE = "visualization_channel_scope_text_show_note"
    const val VISUALIZATION_CHANNEL_SCOPE_TEXT_SHOW_VOLUME = "visualization_channel_scope_text_show_volume"
    const val VISUALIZATION_CHANNEL_SCOPE_TEXT_SHOW_EFFECT = "visualization_channel_scope_text_show_effect"
    const val VISUALIZATION_CHANNEL_SCOPE_TEXT_SHOW_INSTRUMENT_SAMPLE = "visualization_channel_scope_text_show_instrument_sample"

    // Plugin management keys
    fun decoderEnabledKey(decoderName: String) = "decoder_${decoderName}_enabled"
    fun decoderPriorityKey(decoderName: String) = "decoder_${decoderName}_priority"
    fun decoderEnabledExtensionsKey(decoderName: String) = "decoder_${decoderName}_enabled_extensions"
    fun decoderPluginVolumeDbKey(decoderName: String) = "decoder_${decoderName}_plugin_volume_db"
}

// Plugin configuration helpers
private fun loadPluginConfigurations(prefs: android.content.SharedPreferences) {
    val decoderNames = NativeBridge.getRegisteredDecoderNames()

    for (decoderName in decoderNames) {
        // Load enabled state (default true)
        val enabled = prefs.getBoolean(AppPreferenceKeys.decoderEnabledKey(decoderName), true)
        NativeBridge.setDecoderEnabled(decoderName, enabled)

        // Load priority (use current priority as default)
        val currentPriority = NativeBridge.getDecoderPriority(decoderName)
        val priority = prefs.getInt(AppPreferenceKeys.decoderPriorityKey(decoderName), currentPriority)
        if (priority != currentPriority) {
            NativeBridge.setDecoderPriority(decoderName, priority)
        }

        // Load enabled extensions (empty string means all enabled)
        val extensionsString = prefs.getString(AppPreferenceKeys.decoderEnabledExtensionsKey(decoderName), "")
        if (!extensionsString.isNullOrEmpty()) {
            val extensions = extensionsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray()
            NativeBridge.setDecoderEnabledExtensions(decoderName, extensions)
        }
    }
}

private fun savePluginConfiguration(prefs: android.content.SharedPreferences, decoderName: String) {
    val editor = prefs.edit()

    // Save enabled state
    val enabled = NativeBridge.isDecoderEnabled(decoderName)
    editor.putBoolean(AppPreferenceKeys.decoderEnabledKey(decoderName), enabled)

    // Save priority
    val priority = NativeBridge.getDecoderPriority(decoderName)
    editor.putInt(AppPreferenceKeys.decoderPriorityKey(decoderName), priority)

    // Save enabled extensions
    val enabledExtensions = NativeBridge.getDecoderEnabledExtensions(decoderName)
    val supportedExtensions = NativeBridge.getDecoderSupportedExtensions(decoderName)

    // Only save if not all extensions are enabled (optimization)
    if (enabledExtensions.size < supportedExtensions.size) {
        val extensionsString = enabledExtensions.joinToString(",")
        editor.putString(AppPreferenceKeys.decoderEnabledExtensionsKey(decoderName), extensionsString)
    } else {
        editor.remove(AppPreferenceKeys.decoderEnabledExtensionsKey(decoderName))
    }

    editor.apply()
}

private fun readPluginVolumeForDecoder(
    prefs: android.content.SharedPreferences,
    decoderName: String?
): Float {
    if (decoderName.isNullOrBlank()) return 0f
    return prefs.getFloat(AppPreferenceKeys.decoderPluginVolumeDbKey(decoderName), 0f)
}

private fun writePluginVolumeForDecoder(
    prefs: android.content.SharedPreferences,
    decoderName: String?,
    valueDb: Float
) {
    if (decoderName.isNullOrBlank()) return
    prefs.edit()
        .putFloat(AppPreferenceKeys.decoderPluginVolumeDbKey(decoderName), valueDb)
        .apply()
}

private fun clearAllDecoderPluginVolumes(prefs: android.content.SharedPreferences) {
    val editor = prefs.edit()
    editor.remove(AppPreferenceKeys.AUDIO_PLUGIN_VOLUME_DB)
    val decoderNames = NativeBridge.getRegisteredDecoderNames()
    for (decoderName in decoderNames) {
        editor.remove(AppPreferenceKeys.decoderPluginVolumeDbKey(decoderName))
    }
    editor.apply()
}

class MainActivity : ComponentActivity() {
    private var initialFileToOpen: File? = null
    private var initialFileFromExternalIntent: Boolean = false

    private fun applyRemoteSourceCachePolicyOnLaunch() {
        val prefs = getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
        val clearOnLaunch = prefs.getBoolean(AppPreferenceKeys.URL_CACHE_CLEAR_ON_LAUNCH, false)
        val maxTracks = prefs.getInt(AppPreferenceKeys.URL_CACHE_MAX_TRACKS, SOURCE_CACHE_MAX_TRACKS_DEFAULT)
        val maxBytes = prefs.getLong(AppPreferenceKeys.URL_CACHE_MAX_BYTES, SOURCE_CACHE_MAX_BYTES_DEFAULT)
        val cacheRoot = File(cacheDir, REMOTE_SOURCE_CACHE_DIR)
        if (!cacheRoot.exists()) return
        val sessionPath = prefs.getString(AppPreferenceKeys.SESSION_CURRENT_PATH, null)
        val protectedPaths = buildSet {
            sessionPath
                ?.takeIf { it.startsWith(cacheRoot.absolutePath) }
                ?.let { add(it) }
        }
        if (clearOnLaunch) {
            val result = clearRemoteCacheFiles(
                cacheRoot = cacheRoot,
                protectedPaths = protectedPaths
            )
            Log.d(
                URL_SOURCE_TAG,
                "Cleared remote cache on launch: deleted=${result.deletedFiles} skipped=${result.skippedFiles} freed=${result.freedBytes} path=${cacheRoot.absolutePath}"
            )
            return
        }
        val result = enforceRemoteCacheLimits(
            cacheRoot = cacheRoot,
            maxTracks = maxTracks,
            maxBytes = maxBytes,
            protectedPaths = protectedPaths
        )
        if (result.deletedFiles > 0) {
            Log.d(
                URL_SOURCE_TAG,
                "Pruned remote cache on launch: deleted=${result.deletedFiles} freed=${result.freedBytes} path=${cacheRoot.absolutePath} limits=(tracks=$maxTracks bytes=$maxBytes)"
            )
        }
    }

    private fun consumeNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(PlaybackService.EXTRA_OPEN_PLAYER_FROM_NOTIFICATION, false) == true) {
            notificationOpenPlayerSignal++
        }
    }

    private fun consumeFileOpenIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            if (uri != null) {
                try {
                    val file = when (uri.scheme) {
                        "file" -> File(uri.path ?: return)
                        "content" -> {
                            // For content URIs, we need to get the real path
                            val path = getRealPathFromURI(uri)
                            if (path != null) File(path) else null
                        }
                        else -> null
                    }
                    if (file?.exists() == true) {
                        initialFileToOpen = file
                        initialFileFromExternalIntent = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun getRealPathFromURI(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex("_data")
                    if (columnIndex >= 0) {
                        cursor.getString(columnIndex)
                    } else {
                        // Fallback: try to use the URI path directly
                        uri.path
                    }
                } else {
                    uri.path
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            uri.path
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyRemoteSourceCachePolicyOnLaunch()
        consumeNotificationIntent(intent)
        consumeFileOpenIntent(intent)
        setContent {
            val prefs = remember {
                getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
            }
            var themeMode by remember {
                mutableStateOf(
                    ThemeMode.fromStorage(
                        prefs.getString(AppPreferenceKeys.THEME_MODE, ThemeMode.Auto.storageValue)
                    )
                )
            }
            val darkTheme = when (themeMode) {
                ThemeMode.Auto -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            SiliconPlayerTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation(
                        themeMode = themeMode,
                        onThemeModeChanged = { mode ->
                            themeMode = mode
                            prefs.edit()
                                .putString(AppPreferenceKeys.THEME_MODE, mode.storageValue)
                                .apply()
                        },
                        initialFileToOpen = initialFileToOpen,
                        initialFileFromExternalIntent = initialFileFromExternalIntent
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeNotificationIntent(intent)
        consumeFileOpenIntent(intent)
    }

    external fun getStringFromJNI(): String
    external fun startEngine()
    external fun stopEngine()
    external fun isEnginePlaying(): Boolean
    external fun loadAudio(path: String)
    external fun getSupportedExtensions(): Array<String>
    external fun getDuration(): Double
    external fun getPosition(): Double
    external fun consumeNaturalEndEvent(): Boolean
    external fun seekTo(seconds: Double)
    external fun setLooping(enabled: Boolean)
    external fun setRepeatMode(mode: Int)
    external fun getTrackTitle(): String
    external fun getTrackArtist(): String
    external fun getTrackSampleRate(): Int
    external fun getTrackChannelCount(): Int
    external fun getTrackBitDepth(): Int
    external fun getTrackBitDepthLabel(): String
    external fun getRepeatModeCapabilities(): Int
    external fun getPlaybackCapabilities(): Int
    external fun getCoreCapabilities(coreName: String): Int
    external fun getCoreOptionApplyPolicy(coreName: String, optionName: String): Int
    external fun getCoreFixedSampleRateHz(coreName: String): Int
    external fun setCoreOutputSampleRate(coreName: String, sampleRateHz: Int)
    external fun setCoreOption(coreName: String, optionName: String, optionValue: String)
    external fun setAudioPipelineConfig(
        backendPreference: Int,
        performanceMode: Int,
        bufferPreset: Int,
        resamplerPreference: Int,
        allowFallback: Boolean
    )

    companion object {
        var notificationOpenPlayerSignal by mutableIntStateOf(0)

        init {
            System.loadLibrary("siliconplayer")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppNavigation(
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    initialFileToOpen: File?,
    initialFileFromExternalIntent: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
    }
    val volumeDatabase = remember(context) {
        VolumeDatabase.getInstance(context)
    }

    // Audio effects state
    var masterVolumeDb by remember { mutableFloatStateOf(0f) }
    var pluginVolumeDb by remember { mutableFloatStateOf(0f) }
    var songVolumeDb by remember { mutableFloatStateOf(0f) }
    var forceMono by remember { mutableStateOf(false) }
    var showAudioEffectsDialog by remember { mutableStateOf(false) }

    // Temporary state for dialog (to support Cancel)
    var tempMasterVolumeDb by remember { mutableFloatStateOf(0f) }
    var tempPluginVolumeDb by remember { mutableFloatStateOf(0f) }
    var tempSongVolumeDb by remember { mutableFloatStateOf(0f) }
    var tempForceMono by remember { mutableStateOf(false) }

    var currentView by remember { mutableStateOf(MainView.Home) }
    var settingsRoute by remember { mutableStateOf(SettingsRoute.Root) }
    var settingsRouteHistory by remember { mutableStateOf<List<SettingsRoute>>(emptyList()) }
    var selectedPluginName by remember { mutableStateOf<String?>(null) }
    var settingsLaunchedFromPlayer by remember { mutableStateOf(false) }
    var settingsReturnView by remember { mutableStateOf(MainView.Home) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var lastStoppedFile by remember { mutableStateOf<File?>(null) }
    var lastStoppedSourceId by remember { mutableStateOf<String?>(null) }
    var duration by remember { mutableDoubleStateOf(0.0) }
    var position by remember { mutableDoubleStateOf(0.0) }
    var isPlaying by remember { mutableStateOf(false) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var isPlayerSurfaceVisible by remember { mutableStateOf(false) }
    var preferredRepeatMode by remember {
        mutableStateOf(
            RepeatMode.fromStorage(
                prefs.getString(AppPreferenceKeys.PREFERRED_REPEAT_MODE, RepeatMode.None.storageValue)
            )
        )
    }
    var persistRepeatMode by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.PERSIST_REPEAT_MODE, true)
        )
    }
    var activeRepeatMode by remember { mutableStateOf(RepeatMode.None) }
    var metadataTitle by remember { mutableStateOf("") }
    var metadataArtist by remember { mutableStateOf("") }
    var metadataSampleRate by remember { mutableIntStateOf(0) }
    var metadataChannelCount by remember { mutableIntStateOf(0) }
    var metadataBitDepthLabel by remember { mutableStateOf("Unknown") }
    var subtuneCount by remember { mutableIntStateOf(0) }
    var currentSubtuneIndex by remember { mutableIntStateOf(0) }
    var subtuneEntries by remember { mutableStateOf<List<SubtuneEntry>>(emptyList()) }
    var showSubtuneSelectorDialog by remember { mutableStateOf(false) }
    var repeatModeCapabilitiesFlags by remember { mutableIntStateOf(REPEAT_CAP_ALL) }
    var playbackCapabilitiesFlags by remember {
        mutableIntStateOf(
            PLAYBACK_CAP_SEEK or
                PLAYBACK_CAP_RELIABLE_DURATION or
                PLAYBACK_CAP_LIVE_REPEAT_MODE
        )
    }
    var lastUsedCoreName by remember { mutableStateOf<String?>(null) }
    var artworkBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var visiblePlayableFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var browserLaunchLocationId by remember { mutableStateOf<String?>(null) }
    var browserLaunchDirectoryPath by remember { mutableStateOf<String?>(null) }
    val storageDescriptors = remember(context) { detectStorageDescriptors(context) }
    val appScope = rememberCoroutineScope()

    var ffmpegCapabilities by remember { mutableIntStateOf(0) }
    var openMptCapabilities by remember { mutableIntStateOf(0) }
    var vgmPlayCapabilities by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            ffmpegCapabilities = NativeBridge.getCoreCapabilities("FFmpeg")
            openMptCapabilities = NativeBridge.getCoreCapabilities("LibOpenMPT")
            vgmPlayCapabilities = NativeBridge.getCoreCapabilities("VGMPlay")
        }
    }

    // Load audio effects preferences on startup
    LaunchedEffect(Unit) {
        masterVolumeDb = prefs.getFloat(AppPreferenceKeys.AUDIO_MASTER_VOLUME_DB, 0f)
        pluginVolumeDb = 0f
        forceMono = prefs.getBoolean(AppPreferenceKeys.AUDIO_FORCE_MONO, false)

        // Apply to native layer
        NativeBridge.setMasterGain(masterVolumeDb)
        NativeBridge.setPluginGain(0f)
        NativeBridge.setForceMono(forceMono)

        // Load plugin configurations
        withContext(Dispatchers.IO) {
            loadPluginConfigurations(prefs)
        }
    }

    var recentFoldersLimit by remember {
        mutableIntStateOf(
            prefs.getInt(AppPreferenceKeys.RECENT_FOLDERS_LIMIT, RECENT_FOLDERS_LIMIT_DEFAULT)
                .coerceIn(1, RECENTS_LIMIT_MAX)
        )
    }
    var recentFilesLimit by remember {
        mutableIntStateOf(
            prefs.getInt(AppPreferenceKeys.RECENT_PLAYED_FILES_LIMIT, RECENT_FILES_LIMIT_DEFAULT)
                .coerceIn(1, RECENTS_LIMIT_MAX)
        )
    }
    var recentFolders by remember {
        mutableStateOf(readRecentEntries(prefs, AppPreferenceKeys.RECENT_FOLDERS, recentFoldersLimit))
    }
    var recentPlayedFiles by remember {
        mutableStateOf(readRecentEntries(prefs, AppPreferenceKeys.RECENT_PLAYED_FILES, recentFilesLimit))
    }
    var autoPlayOnTrackSelect by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.AUTO_PLAY_ON_TRACK_SELECT, true)
        )
    }
    var openPlayerOnTrackSelect by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.OPEN_PLAYER_ON_TRACK_SELECT, true)
        )
    }
    var autoPlayNextTrackOnEnd by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.AUTO_PLAY_NEXT_TRACK_ON_END, true)
        )
    }
    var previousRestartsAfterThreshold by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.PREVIOUS_RESTART_AFTER_THRESHOLD, true)
        )
    }
    var rememberBrowserLocation by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.REMEMBER_BROWSER_LOCATION, true)
        )
    }
    var lastBrowserLocationId by remember {
        mutableStateOf(
            prefs.getString(AppPreferenceKeys.BROWSER_LAST_LOCATION_ID, null)
        )
    }
    var lastBrowserDirectoryPath by remember {
        mutableStateOf(
            prefs.getString(AppPreferenceKeys.BROWSER_LAST_DIRECTORY_PATH, null)
        )
    }
    var keepScreenOn by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.KEEP_SCREEN_ON, AppDefaults.Player.keepScreenOn)
        )
    }
    var playerArtworkCornerRadiusDp by remember {
        mutableIntStateOf(
            prefs.getInt(
                AppPreferenceKeys.PLAYER_ARTWORK_CORNER_RADIUS_DP,
                AppDefaults.Player.artworkCornerRadiusDp
            ).coerceIn(0, 48)
        )
    }
    var audioFocusInterrupt by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.AUDIO_FOCUS_INTERRUPT, true)
        )
    }
    var audioDucking by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.AUDIO_DUCKING, true)
        )
    }
    var filenameDisplayMode by remember {
        mutableStateOf(
            FilenameDisplayMode.fromStorage(
                prefs.getString(AppPreferenceKeys.FILENAME_DISPLAY_MODE, FilenameDisplayMode.Always.storageValue)
            )
        )
    }
    var filenameOnlyWhenTitleMissing by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.FILENAME_ONLY_WHEN_TITLE_MISSING, false)
        )
    }
    var unknownTrackDurationSeconds by remember {
        mutableIntStateOf(
            prefs.getInt(
                AppPreferenceKeys.UNKNOWN_TRACK_DURATION_SECONDS,
                GmeDefaults.unknownDurationSeconds
            ).coerceIn(1, 86400)
        )
    }
    var endFadeApplyToAllTracks by remember {
        mutableStateOf(
            prefs.getBoolean(
                AppPreferenceKeys.END_FADE_APPLY_TO_ALL_TRACKS,
                AppDefaults.Player.endFadeApplyToAllTracks
            )
        )
    }
    var endFadeDurationMs by remember {
        mutableIntStateOf(
            prefs.getInt(
                AppPreferenceKeys.END_FADE_DURATION_MS,
                AppDefaults.Player.endFadeDurationMs
            ).coerceIn(100, 120000)
        )
    }
    var endFadeCurve by remember {
        mutableStateOf(
            EndFadeCurve.fromStorage(
                prefs.getString(
                    AppPreferenceKeys.END_FADE_CURVE,
                    AppDefaults.Player.endFadeCurve.storageValue
                )
            )
        )
    }
    var visualizationMode by remember {
        mutableStateOf(
            VisualizationMode.fromStorage(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_MODE, VisualizationMode.Off.storageValue)
            )
        )
    }
    var enabledVisualizationModes by remember {
        mutableStateOf(
            parseEnabledVisualizationModes(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_ENABLED_MODES, null)
            )
        )
    }
    var visualizationShowDebugInfo by remember {
        mutableStateOf(
            prefs.getBoolean(
                AppPreferenceKeys.VISUALIZATION_SHOW_DEBUG_INFO,
                AppDefaults.Visualization.showDebugInfo
            )
        )
    }
    var visualizationBarCount by remember {
        mutableIntStateOf(
            prefs.getInt(
                AppPreferenceKeys.VISUALIZATION_BAR_COUNT,
                AppDefaults.Visualization.Bars.count
            ).coerceIn(AppDefaults.Visualization.Bars.countRange.first, AppDefaults.Visualization.Bars.countRange.last)
        )
    }
    var visualizationBarSmoothingPercent by remember {
        mutableIntStateOf(
            prefs.getInt(
                AppPreferenceKeys.VISUALIZATION_BAR_SMOOTHING_PERCENT,
                AppDefaults.Visualization.Bars.smoothingPercent
            ).coerceIn(
                AppDefaults.Visualization.Bars.smoothingRange.first,
                AppDefaults.Visualization.Bars.smoothingRange.last
            )
        )
    }
    var visualizationBarRoundnessDp by remember {
        mutableIntStateOf(
            prefs.getInt(
                AppPreferenceKeys.VISUALIZATION_BAR_ROUNDNESS_DP,
                AppDefaults.Visualization.Bars.roundnessDp
            ).coerceIn(
                AppDefaults.Visualization.Bars.roundnessRange.first,
                AppDefaults.Visualization.Bars.roundnessRange.last
            )
        )
    }
    var visualizationBarOverlayArtwork by remember {
        mutableStateOf(
            prefs.getBoolean(
                AppPreferenceKeys.VISUALIZATION_BAR_OVERLAY_ARTWORK,
                AppDefaults.Visualization.Bars.overlayArtwork
            )
        )
    }
    var visualizationBarUseThemeColor by remember {
        mutableStateOf(
            prefs.getBoolean(
                AppPreferenceKeys.VISUALIZATION_BAR_USE_THEME_COLOR,
                AppDefaults.Visualization.Bars.useThemeColor
            )
        )
    }
    var visualizationOscStereo by remember {
        mutableStateOf(
            prefs.getBoolean(
                AppPreferenceKeys.VISUALIZATION_OSC_STEREO,
                AppDefaults.Visualization.Oscilloscope.stereo
            )
        )
    }
    var visualizationVuAnchor by remember {
        mutableStateOf(
            VisualizationVuAnchor.fromStorage(
                prefs.getString(
                    AppPreferenceKeys.VISUALIZATION_VU_ANCHOR,
                    AppDefaults.Visualization.Vu.anchor.storageValue
                )
            )
        )
    }
    var visualizationVuUseThemeColor by remember {
        mutableStateOf(
            prefs.getBoolean(
                AppPreferenceKeys.VISUALIZATION_VU_USE_THEME_COLOR,
                AppDefaults.Visualization.Vu.useThemeColor
            )
        )
    }
    var visualizationVuSmoothingPercent by remember {
        mutableIntStateOf(
            prefs.getInt(
                AppPreferenceKeys.VISUALIZATION_VU_SMOOTHING_PERCENT,
                AppDefaults.Visualization.Vu.smoothingPercent
            ).coerceIn(
                AppDefaults.Visualization.Vu.smoothingRange.first,
                AppDefaults.Visualization.Vu.smoothingRange.last
            )
        )
    }

    LaunchedEffect(recentFoldersLimit) {
        val clamped = recentFoldersLimit.coerceIn(1, RECENTS_LIMIT_MAX)
        if (clamped != recentFoldersLimit) {
            recentFoldersLimit = clamped
            return@LaunchedEffect
        }
        prefs.edit().putInt(AppPreferenceKeys.RECENT_FOLDERS_LIMIT, clamped).apply()
        val trimmed = recentFolders.take(clamped)
        if (trimmed.size != recentFolders.size) {
            recentFolders = trimmed
        }
        writeRecentEntries(prefs, AppPreferenceKeys.RECENT_FOLDERS, recentFolders, clamped)
    }

    LaunchedEffect(recentFilesLimit) {
        val clamped = recentFilesLimit.coerceIn(1, RECENTS_LIMIT_MAX)
        if (clamped != recentFilesLimit) {
            recentFilesLimit = clamped
            return@LaunchedEffect
        }
        prefs.edit().putInt(AppPreferenceKeys.RECENT_PLAYED_FILES_LIMIT, clamped).apply()
        val trimmed = recentPlayedFiles.take(clamped)
        if (trimmed.size != recentPlayedFiles.size) {
            recentPlayedFiles = trimmed
        }
        writeRecentEntries(prefs, AppPreferenceKeys.RECENT_PLAYED_FILES, recentPlayedFiles, clamped)
    }
    var ffmpegCoreSampleRateHz by remember {
        mutableIntStateOf(
            prefs.getInt(CorePreferenceKeys.CORE_RATE_FFMPEG, FfmpegDefaults.coreSampleRateHz)
        )
    }
    var openMptCoreSampleRateHz by remember {
        mutableIntStateOf(
            prefs.getInt(CorePreferenceKeys.CORE_RATE_OPENMPT, OpenMptDefaults.coreSampleRateHz)
        )
    }
    var vgmPlayCoreSampleRateHz by remember {
        mutableIntStateOf(
            prefs.getInt(CorePreferenceKeys.CORE_RATE_VGMPLAY, VgmPlayDefaults.coreSampleRateHz)
        )
    }
    var gmeCoreSampleRateHz by remember {
        mutableIntStateOf(
            prefs.getInt(CorePreferenceKeys.CORE_RATE_GME, GmeDefaults.coreSampleRateHz)
        )
    }
    var gmeTempoPercent by remember {
        mutableIntStateOf(
            prefs.getInt(CorePreferenceKeys.GME_TEMPO_PERCENT, GmeDefaults.tempoPercent)
        )
    }
    var gmeStereoSeparationPercent by remember {
        mutableIntStateOf(
            prefs.getInt(
                CorePreferenceKeys.GME_STEREO_SEPARATION_PERCENT,
                GmeDefaults.stereoSeparationPercent
            )
        )
    }
    var gmeEchoEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(CorePreferenceKeys.GME_ECHO_ENABLED, GmeDefaults.echoEnabled)
        )
    }
    var gmeAccuracyEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(CorePreferenceKeys.GME_ACCURACY_ENABLED, GmeDefaults.accuracyEnabled)
        )
    }
    var gmeEqTrebleDecibel by remember {
        mutableIntStateOf(
            prefs.getInt(CorePreferenceKeys.GME_EQ_TREBLE_DECIBEL, GmeDefaults.eqTrebleDecibel)
        )
    }
    var gmeEqBassHz by remember {
        mutableIntStateOf(
            prefs.getInt(CorePreferenceKeys.GME_EQ_BASS_HZ, GmeDefaults.eqBassHz)
        )
    }
    var gmeSpcUseBuiltInFade by remember {
        mutableStateOf(
            prefs.getBoolean(CorePreferenceKeys.GME_SPC_USE_BUILTIN_FADE, GmeDefaults.spcUseBuiltInFade)
        )
    }
    var gmeSpcInterpolation by remember {
        mutableIntStateOf(
            prefs.getInt(CorePreferenceKeys.GME_SPC_INTERPOLATION, GmeDefaults.spcInterpolation)
        )
    }
    var gmeSpcUseNativeSampleRate by remember {
        mutableStateOf(
            prefs.getBoolean(
                CorePreferenceKeys.GME_SPC_USE_NATIVE_SAMPLE_RATE,
                GmeDefaults.spcUseNativeSampleRate
            )
        )
    }
    var vgmPlayLoopCount by remember {
        mutableIntStateOf(
            prefs.getInt(CorePreferenceKeys.VGMPLAY_LOOP_COUNT, VgmPlayDefaults.loopCount)
        )
    }
    var vgmPlayAllowNonLoopingLoop by remember {
        mutableStateOf(
            prefs.getBoolean(
                CorePreferenceKeys.VGMPLAY_ALLOW_NON_LOOPING_LOOP,
                VgmPlayDefaults.allowNonLoopingLoop
            )
        )
    }
    var vgmPlayVsyncRate by remember {
        mutableIntStateOf(
            prefs.getInt(CorePreferenceKeys.VGMPLAY_VSYNC_RATE, VgmPlayDefaults.vsyncRate)
        )
    }
    var vgmPlayResampleMode by remember {
        mutableIntStateOf(
            prefs.getInt(CorePreferenceKeys.VGMPLAY_RESAMPLE_MODE, VgmPlayDefaults.resampleMode)
        )
    }
    var vgmPlayChipSampleMode by remember {
        mutableIntStateOf(
            prefs.getInt(CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_MODE, VgmPlayDefaults.chipSampleMode)
        )
    }
    var vgmPlayChipSampleRate by remember {
        mutableIntStateOf(
            prefs.getInt(CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_RATE, VgmPlayDefaults.chipSampleRate)
        )
    }
    var vgmPlayChipCoreSelections by remember {
        mutableStateOf(
            VgmPlayConfig.defaultChipCoreSelections().mapValues { (chipKey, defaultValue) ->
                prefs.getInt(CorePreferenceKeys.vgmPlayChipCoreKey(chipKey), defaultValue)
            }
        )
    }
    var openMptStereoSeparationPercent by remember {
        mutableIntStateOf(
            prefs.getInt(
                CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_PERCENT,
                OpenMptDefaults.stereoSeparationPercent
            )
        )
    }
    var openMptStereoSeparationAmigaPercent by remember {
        mutableIntStateOf(
            prefs.getInt(
                CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_AMIGA_PERCENT,
                OpenMptDefaults.stereoSeparationAmigaPercent
            )
        )
    }
    var openMptInterpolationFilterLength by remember {
        mutableIntStateOf(
            prefs.getInt(
                CorePreferenceKeys.OPENMPT_INTERPOLATION_FILTER_LENGTH,
                OpenMptDefaults.interpolationFilterLength
            )
        )
    }
    var openMptAmigaResamplerMode by remember {
        mutableIntStateOf(
            prefs.getInt(CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_MODE, OpenMptDefaults.amigaResamplerMode)
        )
    }
    var openMptAmigaResamplerApplyAllModules by remember {
        mutableStateOf(
            prefs.getBoolean(
                CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_APPLY_ALL_MODULES,
                OpenMptDefaults.amigaResamplerApplyAllModules
            )
        )
    }
    var openMptVolumeRampingStrength by remember {
        mutableIntStateOf(
            prefs.getInt(
                CorePreferenceKeys.OPENMPT_VOLUME_RAMPING_STRENGTH,
                OpenMptDefaults.volumeRampingStrength
            )
        )
    }
    var openMptFt2XmVolumeRamping by remember {
        mutableStateOf(
            prefs.getBoolean(
                CorePreferenceKeys.OPENMPT_FT2_XM_VOLUME_RAMPING,
                OpenMptDefaults.ft2XmVolumeRamping
            )
        )
    }
    var openMptMasterGainMilliBel by remember {
        mutableIntStateOf(
            prefs.getInt(CorePreferenceKeys.OPENMPT_MASTER_GAIN_MILLIBEL, OpenMptDefaults.masterGainMilliBel)
        )
    }
    var openMptSurroundEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(CorePreferenceKeys.OPENMPT_SURROUND_ENABLED, OpenMptDefaults.surroundEnabled)
        )
    }
    var respondHeadphoneMediaButtons by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.RESPOND_HEADPHONE_MEDIA_BUTTONS, true)
        )
    }
    var pauseOnHeadphoneDisconnect by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.PAUSE_ON_HEADPHONE_DISCONNECT, true)
        )
    }
    var audioBackendPreference by remember {
        mutableStateOf(
            AudioBackendPreference.fromStorage(
                prefs.getString(
                    AppPreferenceKeys.AUDIO_BACKEND_PREFERENCE,
                    AudioBackendPreference.AAudio.storageValue
                )
            )
        )
    }
    var audioPerformanceMode by remember {
        mutableStateOf(
            AudioPerformanceMode.fromStorage(
                prefs.getString(
                    AppPreferenceKeys.AUDIO_PERFORMANCE_MODE,
                    AudioPerformanceMode.None.storageValue
                )
            )
        )
    }
    var audioBufferPreset by remember {
        mutableStateOf(
            AudioBufferPreset.fromStorage(
                prefs.getString(
                    AppPreferenceKeys.AUDIO_BUFFER_PRESET,
                    AudioBufferPreset.Medium.storageValue
                )
            )
        )
    }
    var audioResamplerPreference by remember {
        mutableStateOf(
            AudioResamplerPreference.fromStorage(
                prefs.getString(
                    AppPreferenceKeys.AUDIO_RESAMPLER_PREFERENCE,
                    AudioResamplerPreference.BuiltIn.storageValue
                )
            )
        )
    }
    var pendingSoxExperimentalDialog by remember { mutableStateOf(false) }
    var showSoxExperimentalDialog by remember { mutableStateOf(false) }
    var showUrlOrPathDialog by remember { mutableStateOf(false) }
    var urlOrPathInput by remember { mutableStateOf("") }
    var remoteLoadUiState by remember { mutableStateOf<RemoteLoadUiState?>(null) }
    var remoteLoadJob by remember { mutableStateOf<Job?>(null) }
    var urlOrPathForceCaching by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.URL_PATH_FORCE_CACHING, false)
        )
    }
    var urlCacheClearOnLaunch by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.URL_CACHE_CLEAR_ON_LAUNCH, false)
        )
    }
    var urlCacheMaxTracks by remember {
        mutableIntStateOf(
            prefs.getInt(AppPreferenceKeys.URL_CACHE_MAX_TRACKS, SOURCE_CACHE_MAX_TRACKS_DEFAULT)
        )
    }
    var urlCacheMaxBytes by remember {
        mutableLongStateOf(
            prefs.getLong(AppPreferenceKeys.URL_CACHE_MAX_BYTES, SOURCE_CACHE_MAX_BYTES_DEFAULT)
        )
    }
    var cachedSourceFiles by remember { mutableStateOf<List<CachedSourceFile>>(emptyList()) }
    var pendingCacheExportPaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var audioAllowBackendFallback by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.AUDIO_ALLOW_BACKEND_FALLBACK, true)
        )
    }
    var openPlayerFromNotification by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.OPEN_PLAYER_FROM_NOTIFICATION, true)
        )
    }
    var playbackWatchPath by remember { mutableStateOf<String?>(null) }
    var currentPlaybackSourceId by remember { mutableStateOf<String?>(null) }
    val currentTrackPathOrUrl = currentPlaybackSourceId ?: selectedFile?.absolutePath
    val playbackSourceLabel = remember(selectedFile, currentPlaybackSourceId) {
        resolvePlaybackSourceLabel(selectedFile, currentPlaybackSourceId)
    }

    // Get supported extensions from JNI
    val supportedExtensions = remember { NativeBridge.getSupportedExtensions().toSet() }
    val repository = remember { com.flopster101.siliconplayer.data.FileRepository(supportedExtensions) }

    // Handle pending file from intent
    var pendingFileToOpen by remember { mutableStateOf<File?>(initialFileToOpen) }
    var pendingFileFromExternalIntent by remember { mutableStateOf(initialFileFromExternalIntent) }

    // Helper function to load song volume before loading audio
    fun loadSongVolumeForFile(filePath: String) {
        songVolumeDb = volumeDatabase.getSongVolume(filePath) ?: 0f
        NativeBridge.setSongGain(songVolumeDb)
    }

    fun isLocalPlayableFile(file: File?): Boolean = file?.exists() == true && file.isFile

    suspend fun downloadRemoteUrlToCache(
        url: String,
        requestUrl: String,
        onStatus: suspend (RemoteLoadUiState) -> Unit
    ): RemoteDownloadResult = withContext(Dispatchers.IO) {
        val cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR)
        var target = remoteCacheFileForSource(cacheRoot, url)
        suspend fun emitStatus(state: RemoteLoadUiState) {
            withContext(Dispatchers.Main.immediate) {
                onStatus(state)
            }
        }
        findExistingCachedFileForSource(cacheRoot, url)?.let { existing ->
            existing.setLastModified(System.currentTimeMillis())
            rememberSourceForCachedFile(cacheRoot, existing.name, url)
            Log.d(URL_SOURCE_TAG, "Using existing cached file: ${existing.absolutePath} (${existing.length()} bytes)")
            return@withContext RemoteDownloadResult(file = existing)
        }

        var temp = File(target.absolutePath + ".part")
        Log.d(URL_SOURCE_TAG, "Downloading URL to cache: source=$url request=$requestUrl")
        emitStatus(
            RemoteLoadUiState(
                sourceId = url,
                phase = RemoteLoadPhase.Connecting,
                indeterminate = true
            )
        )

        suspend fun openWithRedirects(initialUrl: String, maxRedirects: Int = 6): Pair<HttpURLConnection?, String?> {
            var currentUrl = initialUrl
            repeat(maxRedirects + 1) { hop ->
                kotlin.coroutines.coroutineContext.ensureActive()
                val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "SiliconPlayer/1.0 (Android)")
                    setRequestProperty("Accept", "*/*")
                    setRequestProperty("Connection", "close")
                    setRequestProperty("Icy-MetaData", "1")
                }
                val responseCode = connection.responseCode
                if (responseCode in 300..399) {
                    val location = connection.getHeaderField("Location")
                    Log.d(
                        URL_SOURCE_TAG,
                        "Redirect hop=$hop code=$responseCode from=$currentUrl to=${location ?: "<missing>"}"
                    )
                    connection.disconnect()
                    if (location.isNullOrBlank()) {
                        return Pair(null, "Redirect missing Location header (HTTP $responseCode)")
                    }
                    currentUrl = URL(URL(currentUrl), location).toString()
                    return@repeat
                }
                Log.d(URL_SOURCE_TAG, "HTTP response code=$responseCode finalUrl=$currentUrl")
                if (responseCode !in 200..299) {
                    connection.disconnect()
                    return Pair(null, "HTTP $responseCode")
                }
                return Pair(connection, null)
            }
            Log.e(URL_SOURCE_TAG, "Too many redirects for URL: $initialUrl")
            return Pair(null, "Too many redirects")
        }

        var connection: HttpURLConnection? = null
        return@withContext try {
            val (openedConnection, openError) = openWithRedirects(requestUrl)
            connection = openedConnection
            if (connection == null) {
                Log.e(URL_SOURCE_TAG, "HTTP open failed for URL: source=$url request=$requestUrl")
                RemoteDownloadResult(
                    file = null,
                    errorMessage = openError ?: "Connection failed"
                )
            } else {
                val contentDispositionName = filenameFromContentDisposition(
                    connection.getHeaderField("Content-Disposition")
                )
                if (!contentDispositionName.isNullOrBlank()) {
                    val resolvedTarget = File(cacheRoot, "${sha1Hex(url)}_$contentDispositionName")
                    if (resolvedTarget.absolutePath != target.absolutePath) {
                        target = resolvedTarget
                        temp = File(target.absolutePath + ".part")
                        findExistingCachedFileForSource(cacheRoot, url)?.let { existing ->
                            existing.setLastModified(System.currentTimeMillis())
                            rememberSourceForCachedFile(cacheRoot, existing.name, url)
                            Log.d(
                                URL_SOURCE_TAG,
                                "Using existing cached file after Content-Disposition resolution: ${existing.absolutePath} (${existing.length()} bytes)"
                            )
                            return@withContext RemoteDownloadResult(file = existing)
                        }
                    }
                }
                val expectedBytes = connection.contentLengthLong
                var totalBytes = 0L
                var latestBytesPerSecond: Long? = null
                val startedAtMs = System.currentTimeMillis()
                var lastSpeedSampleBytes = 0L
                var lastSpeedSampleMs = startedAtMs
                var lastUiUpdateMs = 0L
                suspend fun publishDownloadStatus(force: Boolean = false) {
                    val now = System.currentTimeMillis()
                    if (!force && now - lastUiUpdateMs < 120L) return
                    lastUiUpdateMs = now
                    val hasKnownTotal = expectedBytes > 0L
                    val percentValue = if (hasKnownTotal) {
                        ((totalBytes * 100L) / expectedBytes).toInt().coerceIn(0, 100)
                    } else {
                        null
                    }
                    emitStatus(
                        RemoteLoadUiState(
                            sourceId = url,
                            phase = RemoteLoadPhase.Downloading,
                            downloadedBytes = totalBytes,
                            totalBytes = expectedBytes.takeIf { it > 0L },
                            bytesPerSecond = latestBytesPerSecond,
                            percent = percentValue,
                            indeterminate = !hasKnownTotal
                        )
                    )
                }

                publishDownloadStatus(force = true)
                BufferedInputStream(connection.inputStream).use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            kotlin.coroutines.coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            totalBytes += read
                            val now = System.currentTimeMillis()
                            val deltaMs = now - lastSpeedSampleMs
                            if (deltaMs >= 350L) {
                                val deltaBytes = totalBytes - lastSpeedSampleBytes
                                latestBytesPerSecond = ((deltaBytes * 1000L) / deltaMs).coerceAtLeast(0L)
                                lastSpeedSampleBytes = totalBytes
                                lastSpeedSampleMs = now
                            }
                            publishDownloadStatus()
                        }
                        output.flush()
                    }
                }
                publishDownloadStatus(force = true)
                Log.d(
                    URL_SOURCE_TAG,
                    "Download complete bytes=$totalBytes expected=$expectedBytes temp=${temp.absolutePath}"
                )
                if (totalBytes <= 0L) {
                    return@withContext RemoteDownloadResult(
                        file = null,
                        errorMessage = "Downloaded 0 bytes"
                    )
                }
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
                rememberSourceForCachedFile(cacheRoot, target.name, url)
                Log.d(URL_SOURCE_TAG, "Cached file ready: ${target.absolutePath} (${target.length()} bytes)")
                val elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L)
                val avgSpeed = (totalBytes * 1000L) / elapsedMs
                emitStatus(
                    RemoteLoadUiState(
                        sourceId = url,
                        phase = RemoteLoadPhase.Downloading,
                        downloadedBytes = totalBytes,
                        totalBytes = expectedBytes.takeIf { it > 0L },
                        bytesPerSecond = latestBytesPerSecond ?: avgSpeed,
                        percent = if (expectedBytes > 0L) 100 else null,
                        indeterminate = expectedBytes <= 0L
                    )
                )
                RemoteDownloadResult(file = target)
            }
        } catch (_: CancellationException) {
            Log.d(URL_SOURCE_TAG, "Download cancelled for URL: $url")
            RemoteDownloadResult(file = null, errorMessage = "Cancelled", cancelled = true)
        } catch (t: Throwable) {
            Log.e(URL_SOURCE_TAG, "Download failed for URL: $url (${t::class.java.simpleName}: ${t.message})")
            RemoteDownloadResult(
                file = null,
                errorMessage = "${t::class.java.simpleName}: ${t.message ?: "unknown error"}"
            )
        } finally {
            connection?.disconnect()
            if (temp.exists() && (target.length() <= 0L)) {
                temp.delete()
            }
        }
    }

    fun refreshCachedSourceFiles() {
        appScope.launch(Dispatchers.IO) {
            val files = listCachedSourceFiles(File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR))
            withContext(Dispatchers.Main.immediate) {
                cachedSourceFiles = files
            }
        }
    }

    val cacheExportDirectoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        val selectedPaths = pendingCacheExportPaths
        pendingCacheExportPaths = emptyList()
        if (treeUri == null || selectedPaths.isEmpty()) {
            if (selectedPaths.isNotEmpty()) {
                Toast.makeText(context, "Export canceled", Toast.LENGTH_SHORT).show()
            }
            return@rememberLauncherForActivityResult
        }
        appScope.launch(Dispatchers.IO) {
            var exported = 0
            var failed = 0
            val parentDocumentUri = try {
                DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri)
                )
            } catch (_: Throwable) {
                null
            }
            if (parentDocumentUri == null) {
                withContext(Dispatchers.Main.immediate) {
                    Toast.makeText(context, "Export failed: invalid destination", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val hashPrefixRegex = Regex("^[0-9a-fA-F]{40}_(.+)$")
            selectedPaths.distinct().forEach { absolutePath ->
                val sourceFile = File(absolutePath)
                if (!sourceFile.exists() || !sourceFile.isFile) {
                    failed++
                    return@forEach
                }
                val baseName = hashPrefixRegex.matchEntire(sourceFile.name)?.groupValues?.getOrNull(1)
                    ?: sourceFile.name
                val dotIndex = baseName.lastIndexOf('.')
                val stem = if (dotIndex > 0) baseName.substring(0, dotIndex) else baseName
                val ext = if (dotIndex > 0) baseName.substring(dotIndex) else ""
                val mimeType = guessMimeTypeFromFilename(baseName)
                var destinationUri: Uri? = null
                for (attempt in 0..24) {
                    val candidateName = if (attempt == 0) baseName else "$stem ($attempt)$ext"
                    destinationUri = try {
                        DocumentsContract.createDocument(
                            context.contentResolver,
                            parentDocumentUri,
                            mimeType,
                            candidateName
                        )
                    } catch (_: Throwable) {
                        null
                    }
                    if (destinationUri != null) break
                }
                if (destinationUri == null) {
                    failed++
                    return@forEach
                }
                val copied = try {
                    context.contentResolver.openOutputStream(destinationUri, "w")?.use { output ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    } != null
                } catch (_: Throwable) {
                    false
                }
                if (copied) exported++ else failed++
            }
            withContext(Dispatchers.Main.immediate) {
                Toast.makeText(
                    context,
                    "Exported $exported file(s)" + if (failed > 0) " ($failed failed)" else "",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    LaunchedEffect(currentView, settingsRoute) {
        if (currentView == MainView.Settings &&
            (settingsRoute == SettingsRoute.UrlCache || settingsRoute == SettingsRoute.CacheManager)
        ) {
            refreshCachedSourceFiles()
        }
    }

    LaunchedEffect(pendingFileToOpen) {
        pendingFileToOpen?.let { file ->
            if (file.exists() && supportedExtensions.contains(file.extension.lowercase())) {
                selectedFile = file

                // Load and start playback immediately
                if (autoPlayOnTrackSelect) {
                    loadSongVolumeForFile(file.absolutePath)
                    NativeBridge.loadAudio(file.absolutePath)
                    applyRepeatModeToNative(activeRepeatMode)
                    NativeBridge.startEngine()
                    isPlaying = true
                }
                if (openPlayerOnTrackSelect) {
                    isPlayerExpanded = true
                }
                isPlayerSurfaceVisible = true

                // Async: Load directory context for prev/next controls if from external intent
                if (pendingFileFromExternalIntent) {
                    launch {
                        try {
                            withTimeout(15_000) { // 15 second timeout
                                val parentDir = file.parentFile
                                if (parentDir?.exists() == true && parentDir.isDirectory) {
                                    val loadedFiles = withContext(Dispatchers.IO) {
                                        repository.getFiles(parentDir)
                                    }
                                    // Filter to playable files only
                                    val playableFiles = loadedFiles
                                        .asSequence()
                                        .filter { !it.isDirectory }
                                        .map { it.file }
                                        .toList()
                                    visiblePlayableFiles = playableFiles
                                }
                            }
                        } catch (e: TimeoutCancellationException) {
                            // Timeout - prev/next stay disabled, no error shown
                        } catch (e: Exception) {
                            // Other error - prev/next stay disabled
                            e.printStackTrace()
                        }
                    }
                }

                pendingFileToOpen = null
                pendingFileFromExternalIntent = false
            }
        }
    }

    val storagePermissionState = rememberStoragePermissionState(context)

    fun syncPlaybackService() {
        val cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR)
        val resolvedSourceId = currentPlaybackSourceId
            ?: selectedFile
                ?.takeIf { it.absolutePath.startsWith(cacheRoot.absolutePath) }
                ?.let { sourceIdForCachedFileName(cacheRoot, it.name) }
        syncPlaybackServiceForState(
            context = context,
            selectedFile = selectedFile,
            sourceId = resolvedSourceId,
            metadataTitle = metadataTitle,
            metadataArtist = metadataArtist,
            durationSeconds = duration,
            positionSeconds = position,
            isPlaying = isPlaying
        )
    }

    fun refreshSubtuneState() {
        val currentFile = selectedFile
        if (currentFile == null) {
            subtuneCount = 0
            currentSubtuneIndex = 0
            subtuneEntries = emptyList()
            showSubtuneSelectorDialog = false
            return
        }
        val count = NativeBridge.getSubtuneCount().coerceAtLeast(0)
        subtuneCount = count
        currentSubtuneIndex = if (count <= 0) {
            0
        } else {
            NativeBridge.getCurrentSubtuneIndex().coerceIn(0, count - 1)
        }
    }

    fun refreshSubtuneEntries() {
        val count = subtuneCount
        if (count <= 0) {
            subtuneEntries = emptyList()
            return
        }
        subtuneEntries = (0 until count).map { index ->
            val title = NativeBridge.getSubtuneTitle(index).trim()
            val artist = NativeBridge.getSubtuneArtist(index).trim()
            val durationSeconds = NativeBridge.getSubtuneDurationSeconds(index)
            SubtuneEntry(
                index = index,
                title = title.ifBlank { "Subtune ${index + 1}" },
                artist = artist,
                durationSeconds = durationSeconds
            )
        }
    }

    fun resolveShareableFileForRecent(entry: RecentPathEntry): File? {
        val normalized = normalizeSourceIdentity(entry.path) ?: return null
        val uri = Uri.parse(normalized)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        return when (scheme) {
            "http", "https" -> {
                findExistingCachedFileForSource(File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR), normalized)
                    ?.takeIf { it.exists() && it.isFile }
            }
            "file" -> {
                uri.path?.let { File(it) }?.takeIf { it.exists() && it.isFile }
            }
            else -> {
                File(normalized).takeIf { it.exists() && it.isFile }
            }
        }
    }

    fun addRecentFolder(path: String, locationId: String?) {
        val normalized = normalizeSourceIdentity(path) ?: path
        val updated = listOf(
            RecentPathEntry(path = normalized, locationId = locationId, title = null, artist = null)
        ) + recentFolders.filterNot { samePath(it.path, normalized) }
        recentFolders = updated.take(recentFoldersLimit)
        writeRecentEntries(prefs, AppPreferenceKeys.RECENT_FOLDERS, recentFolders, recentFoldersLimit)
    }

    fun addRecentPlayedTrack(path: String, locationId: String?, title: String? = null, artist: String? = null) {
        val normalized = normalizeSourceIdentity(path) ?: path
        val existing = recentPlayedFiles.firstOrNull { samePath(it.path, normalized) }
        val resolvedTitle = title?.trim().takeUnless { it.isNullOrBlank() } ?: existing?.title
        val resolvedArtist = artist?.trim().takeUnless { it.isNullOrBlank() } ?: existing?.artist
        val updated = listOf(
            RecentPathEntry(
                path = normalized,
                locationId = locationId ?: existing?.locationId,
                title = resolvedTitle,
                artist = resolvedArtist
            )
        ) +
            recentPlayedFiles.filterNot { samePath(it.path, normalized) }
        recentPlayedFiles = updated.take(recentFilesLimit)
        writeRecentEntries(prefs, AppPreferenceKeys.RECENT_PLAYED_FILES, recentPlayedFiles, recentFilesLimit)
    }

    fun scheduleRecentTrackMetadataRefresh(sourceId: String, locationId: String?) {
        appScope.launch {
            repeat(8) { attempt ->
                delay(if (attempt == 0) 120L else 220L)
                val current = selectedFile ?: return@launch
                val activeSource = currentPlaybackSourceId ?: current.absolutePath
                if (!samePath(activeSource, sourceId)) return@launch
                val refreshedTitle = NativeBridge.getTrackTitle()
                val refreshedArtist = NativeBridge.getTrackArtist()
                addRecentPlayedTrack(
                    path = sourceId,
                    locationId = locationId,
                    title = refreshedTitle,
                    artist = refreshedArtist
                )
                if (refreshedTitle.isNotBlank() || refreshedArtist.isNotBlank()) {
                    return@launch
                }
            }
        }
    }

    fun refreshRepeatModeForTrack() {
        val allowSubtuneRepeat = selectedFile == null || subtuneCount > 1
        activeRepeatMode = resolveActiveRepeatMode(
            preferredRepeatMode = preferredRepeatMode,
            repeatModeCapabilitiesFlags = repeatModeCapabilitiesFlags,
            includeSubtuneRepeat = allowSubtuneRepeat
        )
        applyRepeatModeToNative(activeRepeatMode)
    }

    fun cycleRepeatMode() {
        if (!supportsLiveRepeatMode(playbackCapabilitiesFlags)) return
        val next = cycleRepeatModeValue(
            activeRepeatMode = activeRepeatMode,
            repeatModeCapabilitiesFlags = repeatModeCapabilitiesFlags,
            includeSubtuneRepeat = selectedFile == null || subtuneCount > 1
        ) ?: return
        preferredRepeatMode = next
        activeRepeatMode = next
        applyRepeatModeToNative(next)
        showRepeatModeToast(context, next)
    }

    fun applyCoreOptionWithPolicy(
        coreName: String,
        optionName: String,
        optionValue: String,
        policy: CoreOptionApplyPolicy,
        optionLabel: String? = null
    ) {
        NativeBridge.setCoreOption(coreName, optionName, optionValue)
        val resolvedPolicy = try {
            when (NativeBridge.getCoreOptionApplyPolicy(coreName, optionName)) {
                1 -> CoreOptionApplyPolicy.RequiresPlaybackRestart
                else -> CoreOptionApplyPolicy.Live
            }
        } catch (_: Throwable) {
            policy
        }
        maybeShowCoreOptionRestartToast(
            context,
            coreName = coreName,
            selectedFile = selectedFile,
            isPlaying = isPlaying,
            policy = resolvedPolicy,
            optionLabel = optionLabel
        )
    }

    fun applyNativeTrackSnapshot(snapshot: NativeTrackSnapshot) {
        val decoderName = snapshot.decoderName
        decoderName?.let {
            lastUsedCoreName = it
            val decoderPluginVolumeDb = readPluginVolumeForDecoder(prefs, it)
            pluginVolumeDb = decoderPluginVolumeDb
            NativeBridge.setPluginGain(decoderPluginVolumeDb)
        }
        metadataTitle = snapshot.title
        metadataArtist = snapshot.artist
        metadataSampleRate = snapshot.sampleRateHz
        metadataChannelCount = snapshot.channelCount
        metadataBitDepthLabel = snapshot.bitDepthLabel
        repeatModeCapabilitiesFlags = snapshot.repeatModeCapabilitiesFlags
        playbackCapabilitiesFlags = snapshot.playbackCapabilitiesFlags
        duration = snapshot.durationSeconds
    }

    fun selectSubtune(index: Int): Boolean {
        if (selectedFile == null) return false
        if (!NativeBridge.selectSubtune(index)) {
            Toast.makeText(context, "Unable to switch subtune", Toast.LENGTH_SHORT).show()
            return false
        }
        applyNativeTrackSnapshot(readNativeTrackSnapshot())
        position = 0.0
        duration = NativeBridge.getDuration()
        isPlaying = NativeBridge.isEnginePlaying()
        refreshRepeatModeForTrack()
        refreshSubtuneState()
        val sourceId = currentPlaybackSourceId ?: selectedFile?.absolutePath
        if (sourceId != null) {
            addRecentPlayedTrack(
                path = sourceId,
                locationId = if (isLocalPlayableFile(selectedFile)) lastBrowserLocationId else null,
                title = metadataTitle,
                artist = metadataArtist
            )
        }
        syncPlaybackService()
        return true
    }

    fun clearPlaybackMetadataState() {
        selectedFile = null
        currentPlaybackSourceId = null
        duration = 0.0
        position = 0.0
        isPlaying = false
        metadataTitle = ""
        metadataArtist = ""
        metadataSampleRate = 0
        metadataChannelCount = 0
        metadataBitDepthLabel = "Unknown"
        subtuneCount = 0
        currentSubtuneIndex = 0
        subtuneEntries = emptyList()
        showSubtuneSelectorDialog = false
        repeatModeCapabilitiesFlags = REPEAT_CAP_ALL
        playbackCapabilitiesFlags = PLAYBACK_CAP_SEEK or
            PLAYBACK_CAP_RELIABLE_DURATION or
            PLAYBACK_CAP_LIVE_REPEAT_MODE
        artworkBitmap = null
    }

    fun resetAndOptionallyKeepLastTrack(keepLastTrack: Boolean) {
        if (keepLastTrack && (selectedFile != null || currentPlaybackSourceId != null)) {
            lastStoppedFile = selectedFile
            lastStoppedSourceId = currentPlaybackSourceId ?: selectedFile?.absolutePath
        }
        NativeBridge.stopEngine()
        clearPlaybackMetadataState()
    }

    fun applyTrackSelection(
        file: File,
        autoStart: Boolean,
        expandOverride: Boolean? = null,
        sourceIdOverride: String? = null,
        locationIdOverride: String? = lastBrowserLocationId,
        useSongVolumeLookup: Boolean = true
    ) {
        resetAndOptionallyKeepLastTrack(keepLastTrack = false)
        selectedFile = file
        currentPlaybackSourceId = sourceIdOverride ?: file.absolutePath
        isPlayerSurfaceVisible = true
        if (useSongVolumeLookup) {
            loadSongVolumeForFile(file.absolutePath)
        } else {
            songVolumeDb = 0f
            NativeBridge.setSongGain(0f)
        }
        NativeBridge.loadAudio(file.absolutePath)
        applyNativeTrackSnapshot(readNativeTrackSnapshot())
        refreshSubtuneState()
        position = 0.0
        artworkBitmap = null
        refreshRepeatModeForTrack()
        if (autoStart) {
            addRecentPlayedTrack(
                path = currentPlaybackSourceId ?: file.absolutePath,
                locationId = locationIdOverride,
                title = metadataTitle,
                artist = metadataArtist
            )
            NativeBridge.startEngine()
            isPlaying = true
            scheduleRecentTrackMetadataRefresh(
                sourceId = currentPlaybackSourceId ?: file.absolutePath,
                locationId = locationIdOverride
            )
        }
        expandOverride?.let { isPlayerExpanded = it }
        syncPlaybackService()
    }

    fun applyManualInputSelection(
        rawInput: String,
        options: ManualSourceOpenOptions = ManualSourceOpenOptions(),
        expandOverride: Boolean? = null
    ) {
        val resolved = resolveManualSourceInput(rawInput)
        if (resolved == null) {
            Toast.makeText(
                context,
                "Enter a valid file/folder path, file:// path, or http(s) URL",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        fun storageLocationForPath(path: String): String? {
            return storageDescriptors
                .filter { path == it.rootPath || path.startsWith("${it.rootPath}/") }
                .maxByOrNull { it.rootPath.length }
                ?.rootPath
        }

        if (resolved.type == ManualSourceType.LocalDirectory) {
            val directoryPath = resolved.directoryPath ?: return
            browserLaunchLocationId = storageLocationForPath(directoryPath)
            browserLaunchDirectoryPath = directoryPath
            currentView = MainView.Browser
            addRecentFolder(
                path = directoryPath,
                locationId = browserLaunchLocationId
            )
            return
        }

        if (resolved.type == ManualSourceType.LocalFile) {
            val localFile = resolved.localFile ?: return
            appScope.launch {
                val contextualPlayableFiles = withContext(Dispatchers.IO) {
                    val parent = localFile.parentFile
                    if (parent != null && parent.exists() && parent.isDirectory) {
                        repository.getFiles(parent)
                            .asSequence()
                            .filterNot { it.isDirectory }
                            .map { it.file }
                            .toList()
                    } else {
                        listOf(localFile)
                    }
                }
                visiblePlayableFiles = contextualPlayableFiles
                applyTrackSelection(
                    file = localFile,
                    autoStart = true,
                    expandOverride = openPlayerOnTrackSelect,
                    sourceIdOverride = resolved.sourceId
                )
            }
            return
        }

        remoteLoadJob?.cancel()
        remoteLoadJob = appScope.launch {
            fun snapshotAppearsValid(snapshot: NativeTrackSnapshot): Boolean {
                return snapshot.sampleRateHz > 0 ||
                    snapshot.durationSeconds > 0.0 ||
                    snapshot.title.isNotBlank() ||
                    snapshot.artist.isNotBlank()
            }

            fun publishOpenFromSnapshot(displayFile: File, sourceId: String, snapshot: NativeTrackSnapshot) {
                selectedFile = displayFile
                currentPlaybackSourceId = sourceId
                visiblePlayableFiles = emptyList()
                isPlayerSurfaceVisible = true
                songVolumeDb = 0f
                NativeBridge.setSongGain(0f)
                applyNativeTrackSnapshot(snapshot)
                refreshSubtuneState()
                position = 0.0
                artworkBitmap = null
                refreshRepeatModeForTrack()
                addRecentPlayedTrack(
                    path = sourceId,
                    locationId = null,
                    title = metadataTitle,
                    artist = metadataArtist
                )
                applyRepeatModeToNative(activeRepeatMode)
                NativeBridge.startEngine()
                isPlaying = true
                scheduleRecentTrackMetadataRefresh(
                    sourceId = sourceId,
                    locationId = null
                )
                isPlayerExpanded = expandOverride ?: openPlayerOnTrackSelect
                syncPlaybackService()
            }

            fun failOpen(reason: String) {
                Toast.makeText(
                    context,
                    "Unable to open source: $reason",
                    Toast.LENGTH_LONG
                ).show()
            }

            try {
                resetAndOptionallyKeepLastTrack(keepLastTrack = false)
                selectedFile = resolved.displayFile
                currentPlaybackSourceId = resolved.sourceId
                visiblePlayableFiles = emptyList()
                isPlayerSurfaceVisible = true
                songVolumeDb = 0f
                NativeBridge.setSongGain(0f)
                remoteLoadUiState = RemoteLoadUiState(
                    sourceId = resolved.sourceId,
                    phase = RemoteLoadPhase.Connecting,
                    indeterminate = true
                )

                var streamingFailureReason: String? = null
                if (!options.forceCaching) {
                    try {
                        Log.d(
                            URL_SOURCE_TAG,
                            "Attempting direct stream open: source=${resolved.sourceId} request=${resolved.requestUrl}"
                        )
                        val snapshot = withContext(Dispatchers.IO) {
                            withTimeout(20_000L) {
                                NativeBridge.loadAudio(resolved.requestUrl)
                                readNativeTrackSnapshot()
                            }
                        }
                        if (snapshotAppearsValid(snapshot)) {
                            Log.d(
                                URL_SOURCE_TAG,
                                "Direct stream open appears successful (sampleRate=${snapshot.sampleRateHz}, duration=${snapshot.durationSeconds})"
                            )
                            publishOpenFromSnapshot(
                                displayFile = resolved.displayFile ?: File("/virtual/remote/stream"),
                                sourceId = resolved.sourceId,
                                snapshot = snapshot
                            )
                            return@launch
                        }
                        streamingFailureReason = "direct streaming returned no playable metadata"
                        Log.d(URL_SOURCE_TAG, "Direct stream open returned empty snapshot; falling back to cache download")
                    } catch (_: TimeoutCancellationException) {
                        streamingFailureReason = "direct streaming timed out"
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (t: Throwable) {
                        streamingFailureReason = "direct streaming error (${t::class.java.simpleName}: ${t.message ?: "unknown"})"
                        Log.e(
                            URL_SOURCE_TAG,
                            "Direct stream open threw ${t::class.java.simpleName}: ${t.message}; falling back to cache download"
                        )
                    }
                } else {
                    Log.d(URL_SOURCE_TAG, "Force caching enabled; skipping direct stream open for: ${resolved.sourceId}")
                }

                val downloadResult = downloadRemoteUrlToCache(
                    url = resolved.sourceId,
                    requestUrl = resolved.requestUrl,
                    onStatus = { state ->
                        remoteLoadUiState = state
                    }
                )
                if (downloadResult.cancelled) {
                    Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val cachedFile = downloadResult.file
                if (cachedFile == null) {
                    val reason = buildString {
                        streamingFailureReason?.let {
                            append(it)
                            append("; ")
                        }
                        append("cache download failed")
                        downloadResult.errorMessage?.let { append(" ($it)") }
                    }
                    Log.e(URL_SOURCE_TAG, "Cache download/open failed for URL: ${resolved.sourceId} reason=$reason")
                    failOpen(reason)
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    val protectedPaths = buildSet {
                        add(cachedFile.absolutePath)
                        selectedFile?.absolutePath?.let { add(it) }
                    }
                    val pruned = enforceRemoteCacheLimits(
                        cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR),
                        maxTracks = urlCacheMaxTracks,
                        maxBytes = urlCacheMaxBytes,
                        protectedPaths = protectedPaths
                    )
                    if (pruned.deletedFiles > 0) {
                        Log.d(
                            URL_SOURCE_TAG,
                            "Pruned remote cache after download: deleted=${pruned.deletedFiles} freed=${pruned.freedBytes} limits=(tracks=$urlCacheMaxTracks bytes=$urlCacheMaxBytes)"
                        )
                    }
                }

                Log.d(URL_SOURCE_TAG, "Opening downloaded cached file: ${cachedFile.absolutePath}")
                remoteLoadUiState = RemoteLoadUiState(
                    sourceId = resolved.sourceId,
                    phase = RemoteLoadPhase.Opening,
                    downloadedBytes = cachedFile.length(),
                    totalBytes = cachedFile.length(),
                    percent = 100,
                    indeterminate = false
                )

                val cachedSnapshot = withContext(Dispatchers.IO) {
                    withTimeout(20_000L) {
                        NativeBridge.loadAudio(cachedFile.absolutePath)
                        readNativeTrackSnapshot()
                    }
                }
                if (!snapshotAppearsValid(cachedSnapshot)) {
                    failOpen("cached file opened but returned no playable metadata")
                    return@launch
                }
                publishOpenFromSnapshot(
                    displayFile = cachedFile,
                    sourceId = resolved.sourceId,
                    snapshot = cachedSnapshot
                )
            } catch (_: CancellationException) {
                Log.d(URL_SOURCE_TAG, "Remote open cancelled for source=${resolved.sourceId}")
            } finally {
                remoteLoadUiState = null
                remoteLoadJob = null
            }
        }
    }

    fun resumeLastStoppedTrack(autoStart: Boolean = true): Boolean {
        val resumable = lastStoppedFile?.takeIf { it.exists() && it.isFile }
        if (resumable != null) {
            applyTrackSelection(
                file = resumable,
                autoStart = autoStart,
                expandOverride = null
            )
            return true
        }
        val sourceId = lastStoppedSourceId?.takeIf { it.isNotBlank() } ?: run {
            lastStoppedFile = null
            lastStoppedSourceId = null
            return false
        }
        applyManualInputSelection(
            rawInput = sourceId,
            options = ManualSourceOpenOptions(forceCaching = urlOrPathForceCaching),
            expandOverride = isPlayerExpanded
        )
        return true
    }

    fun currentTrackIndex(): Int {
        return currentTrackIndexForList(
            selectedFile = selectedFile,
            visiblePlayableFiles = visiblePlayableFiles
        )
    }

    fun playAdjacentTrack(offset: Int): Boolean {
        val target = adjacentTrackForOffset(
            selectedFile = selectedFile,
            visiblePlayableFiles = visiblePlayableFiles,
            offset = offset
        ) ?: return false
        applyTrackSelection(
            file = target,
            autoStart = true,
            expandOverride = null
        )
        return true
    }

    fun handlePreviousTrackAction(): Boolean {
        val hasTrackLoaded = selectedFile != null
        val shouldRestartCurrent = shouldRestartCurrentTrackOnPrevious(
            previousRestartsAfterThreshold = previousRestartsAfterThreshold,
            hasTrackLoaded = hasTrackLoaded,
            positionSeconds = position
        )

        if (shouldRestartCurrent) {
            NativeBridge.seekTo(0.0)
            position = 0.0
            syncPlaybackService()
            return true
        }

        if (playAdjacentTrack(-1)) return true

        if (hasTrackLoaded) {
            NativeBridge.seekTo(0.0)
            position = 0.0
            syncPlaybackService()
            return true
        }
        return false
    }

    fun restorePlayerStateFromSessionAndNative(openExpanded: Boolean) {
        var sourcePath = prefs.getString(AppPreferenceKeys.SESSION_CURRENT_PATH, null)?.trim()
            ?.takeIf { it.isNotBlank() } ?: return
        val cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR)
        if (sourcePath.startsWith("/virtual/remote/")) {
            val legacyName = sourcePath.substringAfterLast('/').trim()
            val recovered = sourceIdForCachedFileName(cacheRoot, legacyName)
            if (!recovered.isNullOrBlank()) {
                sourcePath = recovered
            } else {
                // Legacy virtual-only source has no recoverable URL; avoid restoring broken state.
                return
            }
        }
        if (sourcePath.startsWith(cacheRoot.absolutePath)) {
            val recovered = sourceIdForCachedFileName(cacheRoot, File(sourcePath).name)
            if (!recovered.isNullOrBlank()) {
                sourcePath = recovered
            }
        }
        val normalizedSource = normalizeSourceIdentity(sourcePath) ?: sourcePath
        val sourceUri = Uri.parse(normalizedSource)
        val sourceScheme = sourceUri.scheme?.lowercase(Locale.ROOT)
        val isRemoteSource = sourceScheme == "http" || sourceScheme == "https"
        val displayFile = if (isRemoteSource) {
            findExistingCachedFileForSource(cacheRoot, normalizedSource)
                ?: File("/virtual/remote/${remoteFilenameHintFromUri(sourceUri) ?: "remote"}")
        } else {
            val localPath = when (sourceScheme) {
                "file" -> sourceUri.path
                else -> normalizedSource
            }
            localPath?.let { File(it) } ?: File(normalizedSource)
        }
        selectedFile = displayFile
        currentPlaybackSourceId = normalizedSource
        isPlayerSurfaceVisible = true
        isPlayerExpanded = openExpanded

        val isLoaded = NativeBridge.getTrackSampleRate() > 0
        if (!isLoaded && displayFile.exists() && displayFile.isFile) {
            loadSongVolumeForFile(displayFile.absolutePath)
            NativeBridge.loadAudio(displayFile.absolutePath)
        }

        applyNativeTrackSnapshot(readNativeTrackSnapshot())
        refreshSubtuneState()
        position = NativeBridge.getPosition()
        isPlaying = NativeBridge.isEnginePlaying()
        artworkBitmap = null
        refreshRepeatModeForTrack()
    }

    LaunchedEffect(selectedFile) {
        var metadataPollElapsedMs = 0L
        while (selectedFile != null) {
            val currentFile = selectedFile
            val pollDelayMs = if (isPlaying) 180L else 320L
            val nextDuration = NativeBridge.getDuration()
            val nextPosition = NativeBridge.getPosition()
            val nextIsPlaying = NativeBridge.isEnginePlaying()
            duration = nextDuration
            position = nextPosition
            isPlaying = nextIsPlaying

            val nativeSubtuneCount = NativeBridge.getSubtuneCount().coerceAtLeast(1)
            val nativeSubtuneIndex = NativeBridge.getCurrentSubtuneIndex()
                .coerceIn(0, nativeSubtuneCount - 1)
            if (nativeSubtuneCount != subtuneCount || nativeSubtuneIndex != currentSubtuneIndex) {
                applyNativeTrackSnapshot(readNativeTrackSnapshot())
                refreshSubtuneState()
                refreshRepeatModeForTrack()
                val recentSourceId = currentPlaybackSourceId ?: currentFile?.absolutePath
                if (recentSourceId != null) {
                    addRecentPlayedTrack(
                        path = recentSourceId,
                        locationId = if (isLocalPlayableFile(currentFile)) lastBrowserLocationId else null,
                        title = metadataTitle,
                        artist = metadataArtist
                    )
                }
            }

            val currentPath = currentPlaybackSourceId ?: currentFile?.absolutePath
            if (currentPath != playbackWatchPath) {
                playbackWatchPath = currentPath
            } else {
                val endedNaturally = NativeBridge.consumeNaturalEndEvent()
                if (endedNaturally && autoPlayNextTrackOnEnd && playAdjacentTrack(1)) {
                    continue
                }
            }
            metadataPollElapsedMs += pollDelayMs
            if (metadataPollElapsedMs >= 540L || metadataTitle.isBlank() || metadataArtist.isBlank()) {
                metadataPollElapsedMs = 0L
                val nextTitle = NativeBridge.getTrackTitle()
                val nextArtist = NativeBridge.getTrackArtist()
                val titleChanged = nextTitle != metadataTitle
                val artistChanged = nextArtist != metadataArtist
                if (titleChanged) metadataTitle = nextTitle
                if (artistChanged) metadataArtist = nextArtist
                val recentSourceId = currentPlaybackSourceId ?: currentFile?.absolutePath
                if ((titleChanged || artistChanged) && recentSourceId != null) {
                    addRecentPlayedTrack(
                        path = recentSourceId,
                        locationId = if (isLocalPlayableFile(currentFile)) lastBrowserLocationId else null,
                        title = nextTitle,
                        artist = nextArtist
                    )
                }
            }
            delay(pollDelayMs)
        }
    }

    LaunchedEffect(selectedFile, preferredRepeatMode) {
        refreshRepeatModeForTrack()
    }

    LaunchedEffect(selectedFile, currentPlaybackSourceId) {
        artworkBitmap = withContext(Dispatchers.IO) {
            loadArtworkForSource(
                context = context,
                displayFile = selectedFile,
                sourceId = currentPlaybackSourceId
            )
        }
    }

    LaunchedEffect(selectedFile, currentPlaybackSourceId, isPlayerSurfaceVisible) {
        if (!isPlayerSurfaceVisible || selectedFile == null) {
            subtuneCount = 0
            currentSubtuneIndex = 0
            subtuneEntries = emptyList()
            showSubtuneSelectorDialog = false
        } else {
            refreshSubtuneState()
        }
    }

    LaunchedEffect(autoPlayOnTrackSelect) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.AUTO_PLAY_ON_TRACK_SELECT, autoPlayOnTrackSelect)
            .apply()
    }

    LaunchedEffect(openPlayerOnTrackSelect) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.OPEN_PLAYER_ON_TRACK_SELECT, openPlayerOnTrackSelect)
            .apply()
    }

    LaunchedEffect(autoPlayNextTrackOnEnd) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.AUTO_PLAY_NEXT_TRACK_ON_END, autoPlayNextTrackOnEnd)
            .apply()
    }

    LaunchedEffect(previousRestartsAfterThreshold) {
        prefs.edit()
            .putBoolean(
                AppPreferenceKeys.PREVIOUS_RESTART_AFTER_THRESHOLD,
                previousRestartsAfterThreshold
            )
            .apply()
    }

    LaunchedEffect(rememberBrowserLocation) {
        val editor = prefs.edit()
            .putBoolean(AppPreferenceKeys.REMEMBER_BROWSER_LOCATION, rememberBrowserLocation)
        if (!rememberBrowserLocation) {
            lastBrowserLocationId = null
            lastBrowserDirectoryPath = null
            editor
                .remove(AppPreferenceKeys.BROWSER_LAST_LOCATION_ID)
                .remove(AppPreferenceKeys.BROWSER_LAST_DIRECTORY_PATH)
        }
        editor.apply()
    }

    LaunchedEffect(ffmpegCoreSampleRateHz) {
        prefs.edit()
            .putInt(CorePreferenceKeys.CORE_RATE_FFMPEG, ffmpegCoreSampleRateHz)
            .apply()
        NativeBridge.setCoreOutputSampleRate("FFmpeg", ffmpegCoreSampleRateHz)
    }

    LaunchedEffect(openMptCoreSampleRateHz) {
        prefs.edit()
            .putInt(CorePreferenceKeys.CORE_RATE_OPENMPT, openMptCoreSampleRateHz)
            .apply()
        NativeBridge.setCoreOutputSampleRate("LibOpenMPT", openMptCoreSampleRateHz)
    }

    LaunchedEffect(vgmPlayCoreSampleRateHz) {
        prefs.edit()
            .putInt(CorePreferenceKeys.CORE_RATE_VGMPLAY, vgmPlayCoreSampleRateHz)
            .apply()
        NativeBridge.setCoreOutputSampleRate("VGMPlay", vgmPlayCoreSampleRateHz)
    }

    LaunchedEffect(gmeCoreSampleRateHz) {
        prefs.edit()
            .putInt(CorePreferenceKeys.CORE_RATE_GME, gmeCoreSampleRateHz)
            .apply()
        NativeBridge.setCoreOutputSampleRate("Game Music Emu", gmeCoreSampleRateHz)
    }

    LaunchedEffect(gmeTempoPercent) {
        val normalized = gmeTempoPercent.coerceIn(50, 200)
        if (normalized != gmeTempoPercent) {
            gmeTempoPercent = normalized
            return@LaunchedEffect
        }
        prefs.edit()
            .putInt(CorePreferenceKeys.GME_TEMPO_PERCENT, normalized)
            .apply()
        applyCoreOptionWithPolicy(
            coreName = "Game Music Emu",
            optionName = GmeOptionKeys.TEMPO,
            optionValue = String.format(Locale.US, "%.2f", normalized / 100.0),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Tempo"
        )
    }

    LaunchedEffect(gmeStereoSeparationPercent) {
        val normalized = gmeStereoSeparationPercent.coerceIn(0, 100)
        if (normalized != gmeStereoSeparationPercent) {
            gmeStereoSeparationPercent = normalized
            return@LaunchedEffect
        }
        prefs.edit()
            .putInt(CorePreferenceKeys.GME_STEREO_SEPARATION_PERCENT, normalized)
            .apply()
        applyCoreOptionWithPolicy(
            coreName = "Game Music Emu",
            optionName = GmeOptionKeys.STEREO_SEPARATION,
            optionValue = String.format(Locale.US, "%.2f", normalized / 100.0),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Stereo separation"
        )
    }

    LaunchedEffect(gmeEchoEnabled) {
        prefs.edit()
            .putBoolean(CorePreferenceKeys.GME_ECHO_ENABLED, gmeEchoEnabled)
            .apply()
        applyCoreOptionWithPolicy(
            coreName = "Game Music Emu",
            optionName = GmeOptionKeys.ECHO_ENABLED,
            optionValue = gmeEchoEnabled.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "SPC echo"
        )
    }

    LaunchedEffect(gmeAccuracyEnabled) {
        prefs.edit()
            .putBoolean(CorePreferenceKeys.GME_ACCURACY_ENABLED, gmeAccuracyEnabled)
            .apply()
        applyCoreOptionWithPolicy(
            coreName = "Game Music Emu",
            optionName = GmeOptionKeys.ACCURACY_ENABLED,
            optionValue = gmeAccuracyEnabled.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "High accuracy emulation"
        )
    }

    LaunchedEffect(gmeEqTrebleDecibel) {
        val normalized = gmeEqTrebleDecibel.coerceIn(-50, 5)
        if (normalized != gmeEqTrebleDecibel) {
            gmeEqTrebleDecibel = normalized
            return@LaunchedEffect
        }
        prefs.edit()
            .putInt(CorePreferenceKeys.GME_EQ_TREBLE_DECIBEL, normalized)
            .apply()
        applyCoreOptionWithPolicy(
            coreName = "Game Music Emu",
            optionName = GmeOptionKeys.EQ_TREBLE_DB,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "EQ treble"
        )
    }

    LaunchedEffect(gmeEqBassHz) {
        val normalized = gmeEqBassHz.coerceIn(1, 1000)
        if (normalized != gmeEqBassHz) {
            gmeEqBassHz = normalized
            return@LaunchedEffect
        }
        prefs.edit()
            .putInt(CorePreferenceKeys.GME_EQ_BASS_HZ, normalized)
            .apply()
        applyCoreOptionWithPolicy(
            coreName = "Game Music Emu",
            optionName = GmeOptionKeys.EQ_BASS_HZ,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "EQ bass"
        )
    }

    LaunchedEffect(gmeSpcUseBuiltInFade) {
        prefs.edit()
            .putBoolean(CorePreferenceKeys.GME_SPC_USE_BUILTIN_FADE, gmeSpcUseBuiltInFade)
            .apply()
        applyCoreOptionWithPolicy(
            coreName = "Game Music Emu",
            optionName = GmeOptionKeys.SPC_USE_BUILTIN_FADE,
            optionValue = gmeSpcUseBuiltInFade.toString(),
            policy = CoreOptionApplyPolicy.RequiresPlaybackRestart,
            optionLabel = "SPC built-in fade"
        )
    }

    LaunchedEffect(gmeSpcInterpolation) {
        val normalized = gmeSpcInterpolation.coerceIn(-2, 2)
        if (normalized != gmeSpcInterpolation) {
            gmeSpcInterpolation = normalized
            return@LaunchedEffect
        }
        prefs.edit()
            .putInt(CorePreferenceKeys.GME_SPC_INTERPOLATION, normalized)
            .apply()
        applyCoreOptionWithPolicy(
            coreName = "Game Music Emu",
            optionName = GmeOptionKeys.SPC_INTERPOLATION,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.RequiresPlaybackRestart,
            optionLabel = "SPC interpolation"
        )
    }

    LaunchedEffect(gmeSpcUseNativeSampleRate) {
        prefs.edit()
            .putBoolean(CorePreferenceKeys.GME_SPC_USE_NATIVE_SAMPLE_RATE, gmeSpcUseNativeSampleRate)
            .apply()
        applyCoreOptionWithPolicy(
            coreName = "Game Music Emu",
            optionName = GmeOptionKeys.SPC_USE_NATIVE_SAMPLE_RATE,
            optionValue = gmeSpcUseNativeSampleRate.toString(),
            policy = CoreOptionApplyPolicy.RequiresPlaybackRestart,
            optionLabel = "Use native SPC sample rate"
        )
    }

    LaunchedEffect(unknownTrackDurationSeconds) {
        val normalized = unknownTrackDurationSeconds.coerceIn(1, 86400)
        if (normalized != unknownTrackDurationSeconds) {
            unknownTrackDurationSeconds = normalized
            return@LaunchedEffect
        }
        prefs.edit()
            .putInt(AppPreferenceKeys.UNKNOWN_TRACK_DURATION_SECONDS, normalized)
            .apply()
        applyCoreOptionWithPolicy(
            coreName = "Game Music Emu",
            optionName = GmeOptionKeys.UNKNOWN_DURATION_SECONDS,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Unknown track duration"
        )
    }

    LaunchedEffect(endFadeApplyToAllTracks) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.END_FADE_APPLY_TO_ALL_TRACKS, endFadeApplyToAllTracks)
            .apply()
        NativeBridge.setEndFadeApplyToAllTracks(endFadeApplyToAllTracks)
    }

    LaunchedEffect(endFadeDurationMs) {
        val normalized = endFadeDurationMs.coerceIn(100, 120000)
        if (normalized != endFadeDurationMs) {
            endFadeDurationMs = normalized
            return@LaunchedEffect
        }
        prefs.edit()
            .putInt(AppPreferenceKeys.END_FADE_DURATION_MS, normalized)
            .apply()
        NativeBridge.setEndFadeDurationMs(normalized)
    }

    LaunchedEffect(endFadeCurve) {
        prefs.edit()
            .putString(AppPreferenceKeys.END_FADE_CURVE, endFadeCurve.storageValue)
            .apply()
        NativeBridge.setEndFadeCurve(endFadeCurve.nativeValue)
    }

    LaunchedEffect(visualizationMode) {
        prefs.edit()
            .putString(AppPreferenceKeys.VISUALIZATION_MODE, visualizationMode.storageValue)
            .apply()
    }

    LaunchedEffect(enabledVisualizationModes) {
        val normalized = enabledVisualizationModes.intersect(selectableVisualizationModes.toSet())
        if (normalized != enabledVisualizationModes) {
            enabledVisualizationModes = normalized
            return@LaunchedEffect
        }
        prefs.edit()
            .putString(
                AppPreferenceKeys.VISUALIZATION_ENABLED_MODES,
                serializeEnabledVisualizationModes(normalized)
            )
            .apply()
        if (visualizationMode != VisualizationMode.Off && !normalized.contains(visualizationMode)) {
            visualizationMode = VisualizationMode.Off
        }
    }
    LaunchedEffect(visualizationShowDebugInfo) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.VISUALIZATION_SHOW_DEBUG_INFO, visualizationShowDebugInfo)
            .apply()
    }

    LaunchedEffect(visualizationBarCount) {
        val normalized = visualizationBarCount.coerceIn(
            AppDefaults.Visualization.Bars.countRange.first,
            AppDefaults.Visualization.Bars.countRange.last
        )
        if (normalized != visualizationBarCount) {
            visualizationBarCount = normalized
            return@LaunchedEffect
        }
        prefs.edit().putInt(AppPreferenceKeys.VISUALIZATION_BAR_COUNT, normalized).apply()
    }

    LaunchedEffect(visualizationBarSmoothingPercent) {
        val normalized = visualizationBarSmoothingPercent.coerceIn(
            AppDefaults.Visualization.Bars.smoothingRange.first,
            AppDefaults.Visualization.Bars.smoothingRange.last
        )
        if (normalized != visualizationBarSmoothingPercent) {
            visualizationBarSmoothingPercent = normalized
            return@LaunchedEffect
        }
        prefs.edit().putInt(AppPreferenceKeys.VISUALIZATION_BAR_SMOOTHING_PERCENT, normalized).apply()
    }

    LaunchedEffect(visualizationBarRoundnessDp) {
        val normalized = visualizationBarRoundnessDp.coerceIn(
            AppDefaults.Visualization.Bars.roundnessRange.first,
            AppDefaults.Visualization.Bars.roundnessRange.last
        )
        if (normalized != visualizationBarRoundnessDp) {
            visualizationBarRoundnessDp = normalized
            return@LaunchedEffect
        }
        prefs.edit().putInt(AppPreferenceKeys.VISUALIZATION_BAR_ROUNDNESS_DP, normalized).apply()
    }

    LaunchedEffect(visualizationBarOverlayArtwork) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.VISUALIZATION_BAR_OVERLAY_ARTWORK, visualizationBarOverlayArtwork)
            .apply()
    }

    LaunchedEffect(visualizationBarUseThemeColor) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.VISUALIZATION_BAR_USE_THEME_COLOR, visualizationBarUseThemeColor)
            .apply()
    }

    LaunchedEffect(visualizationOscStereo) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.VISUALIZATION_OSC_STEREO, visualizationOscStereo)
            .apply()
    }

    LaunchedEffect(visualizationVuAnchor) {
        prefs.edit()
            .putString(AppPreferenceKeys.VISUALIZATION_VU_ANCHOR, visualizationVuAnchor.storageValue)
            .apply()
    }

    LaunchedEffect(visualizationVuUseThemeColor) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.VISUALIZATION_VU_USE_THEME_COLOR, visualizationVuUseThemeColor)
            .apply()
    }

    LaunchedEffect(visualizationVuSmoothingPercent) {
        val normalized = visualizationVuSmoothingPercent.coerceIn(
            AppDefaults.Visualization.Vu.smoothingRange.first,
            AppDefaults.Visualization.Vu.smoothingRange.last
        )
        if (normalized != visualizationVuSmoothingPercent) {
            visualizationVuSmoothingPercent = normalized
            return@LaunchedEffect
        }
        prefs.edit()
            .putInt(AppPreferenceKeys.VISUALIZATION_VU_SMOOTHING_PERCENT, normalized)
            .apply()
    }

    LaunchedEffect(vgmPlayLoopCount) {
        val normalized = vgmPlayLoopCount.coerceIn(1, 99)
        if (normalized != vgmPlayLoopCount) {
            vgmPlayLoopCount = normalized
            return@LaunchedEffect
        }
        prefs.edit()
            .putInt(CorePreferenceKeys.VGMPLAY_LOOP_COUNT, normalized)
            .apply()
        applyCoreOptionWithPolicy(
            coreName = "VGMPlay",
            optionName = VgmPlayOptionKeys.LOOP_COUNT,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Loop count"
        )
    }

    LaunchedEffect(vgmPlayAllowNonLoopingLoop) {
        prefs.edit()
            .putBoolean(CorePreferenceKeys.VGMPLAY_ALLOW_NON_LOOPING_LOOP, vgmPlayAllowNonLoopingLoop)
            .apply()
        applyCoreOptionWithPolicy(
            coreName = "VGMPlay",
            optionName = VgmPlayOptionKeys.ALLOW_NON_LOOPING_LOOP,
            optionValue = vgmPlayAllowNonLoopingLoop.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Allow non-looping loop"
        )
    }

    LaunchedEffect(vgmPlayVsyncRate) {
        val normalized = when (vgmPlayVsyncRate) {
            50, 60 -> vgmPlayVsyncRate
            else -> 0
        }
        if (normalized != vgmPlayVsyncRate) {
            vgmPlayVsyncRate = normalized
            return@LaunchedEffect
        }
        prefs.edit()
            .putInt(CorePreferenceKeys.VGMPLAY_VSYNC_RATE, normalized)
            .apply()
        applyCoreOptionWithPolicy(
            coreName = "VGMPlay",
            optionName = VgmPlayOptionKeys.VSYNC_RATE_HZ,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "VSync mode"
        )
    }

    LaunchedEffect(vgmPlayResampleMode) {
        val normalized = vgmPlayResampleMode.coerceIn(0, 2)
        if (normalized != vgmPlayResampleMode) {
            vgmPlayResampleMode = normalized
            return@LaunchedEffect
        }
        prefs.edit()
            .putInt(CorePreferenceKeys.VGMPLAY_RESAMPLE_MODE, normalized)
            .apply()
        applyCoreOptionWithPolicy(
            coreName = "VGMPlay",
            optionName = VgmPlayOptionKeys.RESAMPLE_MODE,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.RequiresPlaybackRestart,
            optionLabel = "Resampling mode"
        )
    }

    LaunchedEffect(vgmPlayChipSampleMode) {
        val normalized = vgmPlayChipSampleMode.coerceIn(0, 2)
        if (normalized != vgmPlayChipSampleMode) {
            vgmPlayChipSampleMode = normalized
            return@LaunchedEffect
        }
        prefs.edit()
            .putInt(CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_MODE, normalized)
            .apply()
        applyCoreOptionWithPolicy(
            coreName = "VGMPlay",
            optionName = VgmPlayOptionKeys.CHIP_SAMPLE_MODE,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.RequiresPlaybackRestart,
            optionLabel = "Chip sample mode"
        )
    }

    LaunchedEffect(vgmPlayChipSampleRate) {
        val normalized = vgmPlayChipSampleRate.coerceIn(8000, 192000)
        if (normalized != vgmPlayChipSampleRate) {
            vgmPlayChipSampleRate = normalized
            return@LaunchedEffect
        }
        prefs.edit()
            .putInt(CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_RATE, normalized)
            .apply()
        applyCoreOptionWithPolicy(
            coreName = "VGMPlay",
            optionName = VgmPlayOptionKeys.CHIP_SAMPLE_RATE_HZ,
            optionValue = normalized.toString(),
            policy = CoreOptionApplyPolicy.RequiresPlaybackRestart,
            optionLabel = "Chip sample rate"
        )
    }

    LaunchedEffect(vgmPlayChipCoreSelections) {
        val editor = prefs.edit()
        vgmPlayChipCoreSelections.forEach { (chipKey, selectedValue) ->
            editor.putInt(CorePreferenceKeys.vgmPlayChipCoreKey(chipKey), selectedValue)
        }
        editor.apply()
        vgmPlayChipCoreSelections.forEach { (chipKey, selectedValue) ->
            applyCoreOptionWithPolicy(
                coreName = "VGMPlay",
                optionName = "${VgmPlayOptionKeys.CHIP_CORE_PREFIX}$chipKey",
                optionValue = selectedValue.toString(),
                policy = CoreOptionApplyPolicy.RequiresPlaybackRestart,
                optionLabel = "$chipKey emulator core"
            )
        }
    }

    LaunchedEffect(openMptStereoSeparationPercent) {
        prefs.edit()
            .putInt(
                CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_PERCENT,
                openMptStereoSeparationPercent
            )
            .apply()
        applyCoreOptionWithPolicy(
            "LibOpenMPT",
            "openmpt.stereo_separation_percent",
            openMptStereoSeparationPercent.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Stereo separation"
        )
    }

    LaunchedEffect(openMptStereoSeparationAmigaPercent) {
        prefs.edit()
            .putInt(
                CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_AMIGA_PERCENT,
                openMptStereoSeparationAmigaPercent
            )
            .apply()
        applyCoreOptionWithPolicy(
            "LibOpenMPT",
            "openmpt.stereo_separation_amiga_percent",
            openMptStereoSeparationAmigaPercent.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Amiga stereo separation"
        )
    }

    LaunchedEffect(openMptInterpolationFilterLength) {
        prefs.edit()
            .putInt(
                CorePreferenceKeys.OPENMPT_INTERPOLATION_FILTER_LENGTH,
                openMptInterpolationFilterLength
            )
            .apply()
        applyCoreOptionWithPolicy(
            "LibOpenMPT",
            "openmpt.interpolation_filter_length",
            openMptInterpolationFilterLength.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Interpolation filter"
        )
    }

    LaunchedEffect(openMptAmigaResamplerMode) {
        prefs.edit()
            .putInt(
                CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_MODE,
                openMptAmigaResamplerMode
            )
            .apply()
        applyCoreOptionWithPolicy(
            "LibOpenMPT",
            "openmpt.amiga_resampler_mode",
            openMptAmigaResamplerMode.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Amiga resampler"
        )
    }

    LaunchedEffect(openMptAmigaResamplerApplyAllModules) {
        prefs.edit()
            .putBoolean(
                CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_APPLY_ALL_MODULES,
                openMptAmigaResamplerApplyAllModules
            )
            .apply()
        applyCoreOptionWithPolicy(
            "LibOpenMPT",
            "openmpt.amiga_resampler_apply_all_modules",
            openMptAmigaResamplerApplyAllModules.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Apply Amiga resampler to all modules"
        )
    }

    LaunchedEffect(openMptVolumeRampingStrength) {
        prefs.edit()
            .putInt(
                CorePreferenceKeys.OPENMPT_VOLUME_RAMPING_STRENGTH,
                openMptVolumeRampingStrength
            )
            .apply()
        applyCoreOptionWithPolicy(
            "LibOpenMPT",
            "openmpt.volume_ramping_strength",
            openMptVolumeRampingStrength.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Volume ramping strength"
        )
    }

    LaunchedEffect(openMptFt2XmVolumeRamping) {
        prefs.edit()
            .putBoolean(
                CorePreferenceKeys.OPENMPT_FT2_XM_VOLUME_RAMPING,
                openMptFt2XmVolumeRamping
            )
            .apply()
        applyCoreOptionWithPolicy(
            "LibOpenMPT",
            "openmpt.ft2_xm_volume_ramping",
            openMptFt2XmVolumeRamping.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "FT2 5ms XM ramping"
        )
    }

    LaunchedEffect(openMptMasterGainMilliBel) {
        prefs.edit()
            .putInt(
                CorePreferenceKeys.OPENMPT_MASTER_GAIN_MILLIBEL,
                openMptMasterGainMilliBel
            )
            .apply()
        applyCoreOptionWithPolicy(
            "LibOpenMPT",
            "openmpt.master_gain_millibel",
            openMptMasterGainMilliBel.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Master gain"
        )
    }

    LaunchedEffect(openMptSurroundEnabled) {
        prefs.edit()
            .putBoolean(
                CorePreferenceKeys.OPENMPT_SURROUND_ENABLED,
                openMptSurroundEnabled
            )
            .apply()
        applyCoreOptionWithPolicy(
            "LibOpenMPT",
            "openmpt.surround_enabled",
            openMptSurroundEnabled.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Enable surround sound"
        )
    }

    LaunchedEffect(respondHeadphoneMediaButtons) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.RESPOND_HEADPHONE_MEDIA_BUTTONS, respondHeadphoneMediaButtons)
            .apply()
        PlaybackService.refreshSettings(context)
    }

    LaunchedEffect(pauseOnHeadphoneDisconnect) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.PAUSE_ON_HEADPHONE_DISCONNECT, pauseOnHeadphoneDisconnect)
            .apply()
        PlaybackService.refreshSettings(context)
    }

    LaunchedEffect(audioBackendPreference) {
        prefs.edit()
            .putString(
                AppPreferenceKeys.AUDIO_BACKEND_PREFERENCE,
                audioBackendPreference.storageValue
            )
            .apply()
    }

    LaunchedEffect(audioPerformanceMode) {
        prefs.edit()
            .putString(
                AppPreferenceKeys.AUDIO_PERFORMANCE_MODE,
                audioPerformanceMode.storageValue
            )
            .apply()
    }

    LaunchedEffect(audioBufferPreset) {
        prefs.edit()
            .putString(
                AppPreferenceKeys.AUDIO_BUFFER_PRESET,
                audioBufferPreset.storageValue
            )
            .apply()
    }

    LaunchedEffect(audioResamplerPreference) {
        prefs.edit()
            .putString(
                AppPreferenceKeys.AUDIO_RESAMPLER_PREFERENCE,
                audioResamplerPreference.storageValue
            )
            .apply()
    }

    LaunchedEffect(pendingSoxExperimentalDialog) {
        if (!pendingSoxExperimentalDialog) return@LaunchedEffect
        // Defer so the enum picker can close first, then show warning popup.
        delay(120)
        showSoxExperimentalDialog = true
        pendingSoxExperimentalDialog = false
    }

    LaunchedEffect(audioAllowBackendFallback) {
        prefs.edit()
            .putBoolean(
                AppPreferenceKeys.AUDIO_ALLOW_BACKEND_FALLBACK,
                audioAllowBackendFallback
            )
            .apply()
    }

    LaunchedEffect(
        audioBackendPreference,
        audioPerformanceMode,
        audioBufferPreset,
        audioResamplerPreference,
        audioAllowBackendFallback
    ) {
        NativeBridge.setAudioPipelineConfig(
            backendPreference = audioBackendPreference.nativeValue,
            performanceMode = audioPerformanceMode.nativeValue,
            bufferPreset = audioBufferPreset.nativeValue,
            resamplerPreference = audioResamplerPreference.nativeValue,
            allowFallback = audioAllowBackendFallback
        )
    }

    LaunchedEffect(openPlayerFromNotification) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.OPEN_PLAYER_FROM_NOTIFICATION, openPlayerFromNotification)
            .apply()
    }

    LaunchedEffect(persistRepeatMode) {
        val editor = prefs.edit().putBoolean(AppPreferenceKeys.PERSIST_REPEAT_MODE, persistRepeatMode)
        if (!persistRepeatMode) {
            editor.remove(AppPreferenceKeys.PREFERRED_REPEAT_MODE)
        }
        editor.apply()
    }

    LaunchedEffect(preferredRepeatMode, persistRepeatMode) {
        if (persistRepeatMode) {
            prefs.edit()
                .putString(AppPreferenceKeys.PREFERRED_REPEAT_MODE, preferredRepeatMode.storageValue)
                .apply()
        }
    }

    LaunchedEffect(selectedFile, currentPlaybackSourceId, isPlaying, metadataTitle, metadataArtist, duration) {
        if (selectedFile != null) {
            syncPlaybackService()
        }
    }

    val notificationOpenSignal = MainActivity.notificationOpenPlayerSignal

    LaunchedEffect(Unit, notificationOpenSignal, openPlayerFromNotification) {
        val shouldOpenExpandedFromSignal = notificationOpenSignal > 0 && openPlayerFromNotification
        restorePlayerStateFromSessionAndNative(openExpanded = shouldOpenExpandedFromSignal)
    }

    LaunchedEffect(notificationOpenSignal, openPlayerFromNotification, selectedFile) {
        if (notificationOpenSignal <= 0) return@LaunchedEffect
        if (selectedFile == null) return@LaunchedEffect
        if (openPlayerFromNotification) {
            restorePlayerStateFromSessionAndNative(openExpanded = true)
        }
    }

    if (!storagePermissionState.hasPermission) {
        StoragePermissionRequiredScreen(
            onRequestPermission = storagePermissionState.requestPermission
        )
        return
    }

    val miniPlayerInsetTarget = when {
        currentView == MainView.Browser && isPlayerSurfaceVisible -> 108.dp
        currentView == MainView.Home && isPlayerSurfaceVisible -> 108.dp
        currentView == MainView.Settings && isPlayerSurfaceVisible -> 108.dp
        else -> 0.dp
    }
    val miniPlayerListInset by animateDpAsState(
        targetValue = miniPlayerInsetTarget,
        label = "miniPlayerListInset"
    )
    var miniExpandPreviewProgress by remember { mutableFloatStateOf(0f) }
    var expandFromMiniDrag by remember { mutableStateOf(false) }
    var collapseFromSwipe by remember { mutableStateOf(false) }
    val uiScope = rememberCoroutineScope()
    val screenHeightPx = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val miniPreviewLiftPx = with(LocalDensity.current) { 28.dp.toPx() }
    val stopAndEmptyTrack: () -> Unit = {
        resetAndOptionallyKeepLastTrack(keepLastTrack = true)
        context.startService(
            Intent(context, PlaybackService::class.java).setAction(PlaybackService.ACTION_STOP_CLEAR)
        )
    }
    val currentDecoderName = NativeBridge.getCurrentDecoderName().takeIf { it.isNotBlank() }
    val activeCoreNameForUi = currentDecoderName ?: lastUsedCoreName
    val currentCorePluginName = pluginNameForCoreName(activeCoreNameForUi)
    val canOpenCurrentCoreSettings = currentCorePluginName != null
    val availableVisualizationModes = remember(enabledVisualizationModes, activeCoreNameForUi) {
        listOf(VisualizationMode.Off) + selectableVisualizationModes.filter { mode ->
            isVisualizationModeSelectable(mode, enabledVisualizationModes, activeCoreNameForUi)
        }
    }
    LaunchedEffect(availableVisualizationModes, visualizationMode) {
        if (!availableVisualizationModes.contains(visualizationMode)) {
            visualizationMode = VisualizationMode.Off
        }
    }
    val cycleVisualizationMode: () -> Unit = {
        val modes = availableVisualizationModes
        val currentIndex = modes.indexOf(visualizationMode).takeIf { it >= 0 } ?: 0
        visualizationMode = modes[(currentIndex + 1) % modes.size]
    }
    val setVisualizationMode: (VisualizationMode) -> Unit = { mode ->
        if (availableVisualizationModes.contains(mode)) {
            visualizationMode = mode
        }
    }
    val setEnabledVisualizationModes: (Set<VisualizationMode>) -> Unit = { requested ->
        val normalized = requested.intersect(selectableVisualizationModes.toSet())
        val currentMode = visualizationMode
        val previousModes = availableVisualizationModes
        enabledVisualizationModes = normalized
        if (
            currentMode != VisualizationMode.Off &&
            !isVisualizationModeSelectable(currentMode, normalized, activeCoreNameForUi)
        ) {
            val currentIndex = previousModes.indexOf(currentMode)
            val fallback = if (currentIndex > 0) {
                previousModes
                    .subList(0, currentIndex)
                    .lastOrNull {
                        it == VisualizationMode.Off ||
                            isVisualizationModeSelectable(it, normalized, activeCoreNameForUi)
                    }
            } else {
                null
            } ?: VisualizationMode.Off
            visualizationMode = fallback
        }
    }
    val openSettingsRoute: (SettingsRoute, Boolean) -> Unit = { targetRoute, resetHistory ->
        if (resetHistory) {
            settingsRouteHistory = emptyList()
            settingsRoute = targetRoute
        } else if (settingsRoute != targetRoute) {
            settingsRouteHistory = (settingsRouteHistory + settingsRoute).takeLast(24)
            settingsRoute = targetRoute
        }
    }
    val popSettingsRoute: () -> Boolean = {
        val previousRoute = settingsRouteHistory.lastOrNull()
        if (previousRoute != null) {
            settingsRouteHistory = settingsRouteHistory.dropLast(1)
            settingsRoute = previousRoute
            true
        } else if (settingsRoute != SettingsRoute.Root) {
            settingsRoute = SettingsRoute.Root
            true
        } else {
            false
        }
    }
    val exitSettingsToReturnView: () -> Unit = {
        val target = if (settingsLaunchedFromPlayer) settingsReturnView else MainView.Home
        settingsLaunchedFromPlayer = false
        settingsRouteHistory = emptyList()
        settingsRoute = SettingsRoute.Root
        currentView = target
    }
    val openCurrentCoreSettings: () -> Unit = {
        pluginNameForCoreName(lastUsedCoreName)?.let { pluginName ->
            settingsReturnView = if (currentView == MainView.Settings) MainView.Home else currentView
            settingsLaunchedFromPlayer = true
            selectedPluginName = pluginName
            openSettingsRoute(SettingsRoute.PluginDetail, true)
            currentView = MainView.Settings
            isPlayerExpanded = false
        }
    }
    val openVisualizationSettings: () -> Unit = {
        settingsReturnView = if (currentView == MainView.Settings) MainView.Home else currentView
        settingsLaunchedFromPlayer = true
        openSettingsRoute(SettingsRoute.Visualization, true)
        currentView = MainView.Settings
        isPlayerExpanded = false
    }
    val openVisualizationBarsSettings: () -> Unit = {
        settingsReturnView = if (currentView == MainView.Settings) MainView.Home else currentView
        settingsLaunchedFromPlayer = true
        openSettingsRoute(SettingsRoute.VisualizationBasicBars, true)
        currentView = MainView.Settings
        isPlayerExpanded = false
    }
    val openVisualizationOscilloscopeSettings: () -> Unit = {
        settingsReturnView = if (currentView == MainView.Settings) MainView.Home else currentView
        settingsLaunchedFromPlayer = true
        openSettingsRoute(SettingsRoute.VisualizationBasicOscilloscope, true)
        currentView = MainView.Settings
        isPlayerExpanded = false
    }
    val openVisualizationVuMetersSettings: () -> Unit = {
        settingsReturnView = if (currentView == MainView.Settings) MainView.Home else currentView
        settingsLaunchedFromPlayer = true
        openSettingsRoute(SettingsRoute.VisualizationBasicVuMeters, true)
        currentView = MainView.Settings
        isPlayerExpanded = false
    }
    val openVisualizationChannelScopeSettings: () -> Unit = {
        settingsReturnView = if (currentView == MainView.Settings) MainView.Home else currentView
        settingsLaunchedFromPlayer = true
        openSettingsRoute(SettingsRoute.VisualizationAdvancedChannelScope, true)
        currentView = MainView.Settings
        isPlayerExpanded = false
    }
    val openSelectedVisualizationSettings: () -> Unit = {
        when (visualizationMode) {
            VisualizationMode.Bars -> openVisualizationBarsSettings()
            VisualizationMode.Oscilloscope -> openVisualizationOscilloscopeSettings()
            VisualizationMode.VuMeters -> openVisualizationVuMetersSettings()
            VisualizationMode.ChannelScope -> openVisualizationChannelScopeSettings()
            VisualizationMode.Off -> Unit
        }
    }

    LaunchedEffect(keepScreenOn, isPlayerExpanded) {
        val window = (context as? ComponentActivity)?.window ?: return@LaunchedEffect
        if (keepScreenOn && isPlayerExpanded) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(keepScreenOn) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.KEEP_SCREEN_ON, keepScreenOn)
            .apply()
    }
    LaunchedEffect(playerArtworkCornerRadiusDp) {
        val normalized = playerArtworkCornerRadiusDp.coerceIn(0, 48)
        if (normalized != playerArtworkCornerRadiusDp) {
            playerArtworkCornerRadiusDp = normalized
            return@LaunchedEffect
        }
        prefs.edit()
            .putInt(AppPreferenceKeys.PLAYER_ARTWORK_CORNER_RADIUS_DP, normalized)
            .apply()
    }

    val hidePlayerSurface: () -> Unit = {
        stopAndEmptyTrack()
        isPlayerExpanded = false
        isPlayerSurfaceVisible = false
    }
    LaunchedEffect(isPlayerExpanded) {
        if (!isPlayerExpanded) {
            miniExpandPreviewProgress = 0f
            delay(160)
            expandFromMiniDrag = false
            collapseFromSwipe = false
        }
    }

    RegisterPlaybackBroadcastReceiver(
        context = context,
        onCleared = { resetAndOptionallyKeepLastTrack(keepLastTrack = true) },
        onPreviousTrackRequested = { handlePreviousTrackAction() },
        onNextTrackRequested = { playAdjacentTrack(1) }
    )

    BackHandler(enabled = isPlayerExpanded || currentView == MainView.Settings) {
        when {
            isPlayerExpanded -> isPlayerExpanded = false
            currentView == MainView.Settings && settingsLaunchedFromPlayer -> exitSettingsToReturnView()
            currentView == MainView.Settings && popSettingsRoute() -> Unit
            currentView == MainView.Settings -> exitSettingsToReturnView()
        }
    }

    val shouldShowBrowserHomeAction = currentView == MainView.Browser
    val shouldShowSettingsAction = currentView != MainView.Settings

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.material3.Scaffold(
            topBar = {
                AnimatedVisibility(
                    visible = currentView != MainView.Settings,
                    enter = fadeIn(animationSpec = tween(140)),
                    exit = fadeOut(animationSpec = tween(140))
                ) {
                    androidx.compose.material3.TopAppBar(
                        title = {
                            Text(
                                text = "Silicon Player",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable {
                                        isPlayerSurfaceVisible = true
                                        isPlayerExpanded = true
                                        collapseFromSwipe = false
                                        expandFromMiniDrag = false
                                        miniExpandPreviewProgress = 0f
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        },
                        actions = {
                            AnimatedVisibility(
                                visible = shouldShowBrowserHomeAction,
                                enter = fadeIn(animationSpec = tween(150)),
                                exit = fadeOut(animationSpec = tween(120))
                            ) {
                                IconButton(onClick = { currentView = MainView.Home }) {
                                    Icon(
                                        imageVector = Icons.Default.Home,
                                        contentDescription = "Go to app home"
                                    )
                                }
                            }
                            if (shouldShowSettingsAction) {
                                IconButton(onClick = {
                                    settingsLaunchedFromPlayer = false
                                    openSettingsRoute(SettingsRoute.Root, true)
                                    currentView = MainView.Settings
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Open settings"
                                    )
                                }
                            }
                        }
                    )
                }
            }
        ) { mainPadding ->
            AnimatedContent(
                targetState = currentView,
                transitionSpec = {
                    val forward = mainViewOrder(targetState) >= mainViewOrder(initialState)
                    val enter = slideInHorizontally(
                        initialOffsetX = { fullWidth -> if (forward) fullWidth else -fullWidth / 4 },
                        animationSpec = tween(
                            durationMillis = PAGE_NAV_DURATION_MS,
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
                            durationMillis = PAGE_NAV_DURATION_MS,
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
                label = "mainViewTransition",
                modifier = Modifier
            ) { targetView ->
                when (targetView) {
                    MainView.Home -> Box(modifier = Modifier.padding(mainPadding)) {
                        HomeScreen(
                            currentTrackPath = currentPlaybackSourceId ?: selectedFile?.absolutePath,
                            currentTrackTitle = metadataTitle,
                            currentTrackArtist = metadataArtist,
                            recentFolders = recentFolders,
                            recentPlayedFiles = recentPlayedFiles,
                            storagePresentationForEntry = { entry ->
                                storagePresentationForEntry(context, entry, storageDescriptors)
                            },
                            bottomContentPadding = miniPlayerListInset,
                            onOpenLibrary = {
                                browserLaunchLocationId = null
                                browserLaunchDirectoryPath = null
                                currentView = MainView.Browser
                            },
                            onOpenUrlOrPath = {
                                urlOrPathInput = ""
                                showUrlOrPathDialog = true
                            },
                            onOpenRecentFolder = { entry ->
                                browserLaunchLocationId = entry.locationId
                                browserLaunchDirectoryPath = entry.path
                                currentView = MainView.Browser
                            },
                            onPlayRecentFile = { entry ->
                                val normalized = normalizeSourceIdentity(entry.path)
                                val uri = normalized?.let { Uri.parse(it) }
                                val scheme = uri?.scheme?.lowercase(Locale.ROOT)
                                val isRemote = scheme == "http" || scheme == "https"
                                if (isRemote && !normalized.isNullOrBlank()) {
                                    val cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR)
                                    val cached = findExistingCachedFileForSource(cacheRoot, normalized)
                                    if (cached != null) {
                                        applyTrackSelection(
                                            file = cached,
                                            autoStart = true,
                                            expandOverride = openPlayerOnTrackSelect,
                                            sourceIdOverride = normalized,
                                            locationIdOverride = null,
                                            useSongVolumeLookup = false
                                        )
                                    } else {
                                        applyManualInputSelection(entry.path)
                                    }
                                } else {
                                    applyManualInputSelection(entry.path)
                                }
                            },
                            onRecentFolderAction = { entry, action ->
                                when (action) {
                                    FolderEntryAction.DeleteFromRecents -> {
                                        recentFolders = recentFolders
                                            .filterNot { samePath(it.path, entry.path) }
                                        writeRecentEntries(
                                            prefs,
                                            AppPreferenceKeys.RECENT_FOLDERS,
                                            recentFolders,
                                            recentFoldersLimit
                                        )
                                        Toast.makeText(context, "Removed from recents", Toast.LENGTH_SHORT).show()
                                    }
                                    FolderEntryAction.CopyPath -> {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(
                                            ClipData.newPlainText("Path", entry.path)
                                        )
                                        Toast.makeText(context, "Copied path", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onRecentFileAction = { entry, action ->
                                when (action) {
                                    SourceEntryAction.DeleteFromRecents -> {
                                        recentPlayedFiles = recentPlayedFiles
                                            .filterNot { samePath(it.path, entry.path) }
                                        writeRecentEntries(
                                            prefs,
                                            AppPreferenceKeys.RECENT_PLAYED_FILES,
                                            recentPlayedFiles,
                                            recentFilesLimit
                                        )
                                        Toast.makeText(context, "Removed from recents", Toast.LENGTH_SHORT).show()
                                    }
                                    SourceEntryAction.ShareFile -> {
                                        val shareFile = resolveShareableFileForRecent(entry)
                                        if (shareFile == null) {
                                            Toast.makeText(
                                                context,
                                                "Share is only available for local or cached files",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            try {
                                                val uri = FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    shareFile
                                                )
                                                val intent = Intent(Intent.ACTION_SEND).apply {
                                                    type = guessMimeTypeFromFilename(shareFile.name)
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(
                                                    Intent.createChooser(intent, "Share file")
                                                )
                                            } catch (_: Throwable) {
                                                Toast.makeText(
                                                    context,
                                                    "Unable to share file",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                    SourceEntryAction.CopySource -> {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(
                                            ClipData.newPlainText("URL or path", entry.path)
                                        )
                                        Toast.makeText(context, "Copied URL/path", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            canShareRecentFile = { entry ->
                                resolveShareableFileForRecent(entry) != null
                            }
                        )
                    }
                    MainView.Browser -> Box(modifier = Modifier.padding(mainPadding)) {
                        com.flopster101.siliconplayer.ui.screens.FileBrowserScreen(
                            repository = repository,
                            initialLocationId = browserLaunchLocationId
                                ?: if (rememberBrowserLocation) lastBrowserLocationId else null,
                            initialDirectoryPath = browserLaunchDirectoryPath
                                ?: if (rememberBrowserLocation) lastBrowserDirectoryPath else null,
                            onVisiblePlayableFilesChanged = { files -> visiblePlayableFiles = files },
                            bottomContentPadding = miniPlayerListInset,
                            backHandlingEnabled = !isPlayerExpanded,
                            onExitBrowser = { currentView = MainView.Home },
                            onOpenSettings = null,
                            showPrimaryTopBar = false,
                            playingFile = selectedFile,
                            onBrowserLocationChanged = { locationId, directoryPath ->
                                if (locationId != null && directoryPath != null) {
                                    addRecentFolder(directoryPath, locationId)
                                }
                                if (browserLaunchLocationId != null || browserLaunchDirectoryPath != null) {
                                    browserLaunchLocationId = null
                                    browserLaunchDirectoryPath = null
                                }
                                if (!rememberBrowserLocation) return@FileBrowserScreen
                                lastBrowserLocationId = locationId
                                lastBrowserDirectoryPath = directoryPath
                                prefs.edit()
                                    .putString(AppPreferenceKeys.BROWSER_LAST_LOCATION_ID, locationId)
                                    .putString(AppPreferenceKeys.BROWSER_LAST_DIRECTORY_PATH, directoryPath)
                                    .apply()
                            },
                            onFileSelected = { file ->
                                applyTrackSelection(
                                    file = file,
                                    autoStart = autoPlayOnTrackSelect,
                                    expandOverride = openPlayerOnTrackSelect
                                )
                            }
                        )
                    }
                    MainView.Settings -> Box(modifier = Modifier.padding(mainPadding)) {
                        SettingsScreen(
                        route = settingsRoute,
                        bottomContentPadding = miniPlayerListInset,
                        onBack = {
                            if (settingsLaunchedFromPlayer) {
                                exitSettingsToReturnView()
                                return@SettingsScreen
                            }
                            if (!popSettingsRoute()) {
                                exitSettingsToReturnView()
                            }
                        },
                        onOpenAudioPlugins = { openSettingsRoute(SettingsRoute.AudioPlugins, false) },
                        onOpenGeneralAudio = { openSettingsRoute(SettingsRoute.GeneralAudio, false) },
                        onOpenHome = { openSettingsRoute(SettingsRoute.Home, false) },
                        onOpenAudioEffects = {
                            tempMasterVolumeDb = masterVolumeDb
                            tempPluginVolumeDb = pluginVolumeDb
                            tempSongVolumeDb = songVolumeDb
                            tempForceMono = forceMono
                            showAudioEffectsDialog = true
                        },
                        onClearAllAudioParameters = {
                            // Clear all: master, plugin volumes and force mono from preferences, and all song volumes from database
                            prefs.edit().apply {
                                remove(AppPreferenceKeys.AUDIO_MASTER_VOLUME_DB)
                                remove(AppPreferenceKeys.AUDIO_FORCE_MONO)
                                apply()
                            }
                            clearAllDecoderPluginVolumes(prefs)
                            volumeDatabase.resetAllSongVolumes()
                            // Reset state and native layer
                            masterVolumeDb = 0f
                            pluginVolumeDb = 0f
                            songVolumeDb = 0f
                            forceMono = false
                            NativeBridge.setMasterGain(0f)
                            NativeBridge.setPluginGain(0f)
                            NativeBridge.setSongGain(0f)
                            NativeBridge.setForceMono(false)
                            Toast.makeText(context, "All audio parameters cleared", Toast.LENGTH_SHORT).show()
                        },
                        onClearPluginAudioParameters = {
                            // Clear plugin volume only
                            clearAllDecoderPluginVolumes(prefs)
                            pluginVolumeDb = 0f
                            NativeBridge.setPluginGain(0f)
                            Toast.makeText(context, "Plugin volume cleared", Toast.LENGTH_SHORT).show()
                        },
                        onClearSongAudioParameters = {
                            // Clear all song volumes from database
                            volumeDatabase.resetAllSongVolumes()
                            songVolumeDb = 0f
                            NativeBridge.setSongGain(0f)
                            Toast.makeText(context, "All song volumes cleared", Toast.LENGTH_SHORT).show()
                        },
                        onOpenPlayer = { openSettingsRoute(SettingsRoute.Player, false) },
                        onOpenVisualization = { openSettingsRoute(SettingsRoute.Visualization, false) },
                        onOpenVisualizationBasic = { openSettingsRoute(SettingsRoute.VisualizationBasic, false) },
                        onOpenVisualizationBasicBars = { openSettingsRoute(SettingsRoute.VisualizationBasicBars, false) },
                        onOpenVisualizationBasicOscilloscope = { openSettingsRoute(SettingsRoute.VisualizationBasicOscilloscope, false) },
                        onOpenVisualizationBasicVuMeters = { openSettingsRoute(SettingsRoute.VisualizationBasicVuMeters, false) },
                        onOpenVisualizationAdvanced = { openSettingsRoute(SettingsRoute.VisualizationAdvanced, false) },
                        onOpenVisualizationAdvancedChannelScope = {
                            openSettingsRoute(SettingsRoute.VisualizationAdvancedChannelScope, false)
                        },
                        onOpenMisc = { openSettingsRoute(SettingsRoute.Misc, false) },
                        onOpenUrlCache = { openSettingsRoute(SettingsRoute.UrlCache, false) },
                        onOpenCacheManager = {
                            refreshCachedSourceFiles()
                            openSettingsRoute(SettingsRoute.CacheManager, false)
                        },
                        onOpenUi = { openSettingsRoute(SettingsRoute.Ui, false) },
                        onOpenAbout = { openSettingsRoute(SettingsRoute.About, false) },
                        onOpenVgmPlayChipSettings = { openSettingsRoute(SettingsRoute.PluginVgmPlayChipSettings, false) },
                        selectedPluginName = selectedPluginName,
                        onPluginSelected = { pluginName ->
                            selectedPluginName = pluginName
                            openSettingsRoute(SettingsRoute.PluginDetail, false)
                        },
                        onPluginEnabledChanged = { pluginName, enabled ->
                            NativeBridge.setDecoderEnabled(pluginName, enabled)
                            savePluginConfiguration(prefs, pluginName)
                        },
                        onPluginPriorityChanged = { pluginName, priority ->
                            NativeBridge.setDecoderPriority(pluginName, priority)
                            savePluginConfiguration(prefs, pluginName)
                        },
                        onPluginExtensionsChanged = { pluginName, extensions ->
                            NativeBridge.setDecoderEnabledExtensions(pluginName, extensions)
                            savePluginConfiguration(prefs, pluginName)
                        },
                        autoPlayOnTrackSelect = autoPlayOnTrackSelect,
                        onAutoPlayOnTrackSelectChanged = { autoPlayOnTrackSelect = it },
                        openPlayerOnTrackSelect = openPlayerOnTrackSelect,
                        onOpenPlayerOnTrackSelectChanged = { openPlayerOnTrackSelect = it },
                        autoPlayNextTrackOnEnd = autoPlayNextTrackOnEnd,
                        onAutoPlayNextTrackOnEndChanged = { autoPlayNextTrackOnEnd = it },
                        previousRestartsAfterThreshold = previousRestartsAfterThreshold,
                        onPreviousRestartsAfterThresholdChanged = { previousRestartsAfterThreshold = it },
                        respondHeadphoneMediaButtons = respondHeadphoneMediaButtons,
                        onRespondHeadphoneMediaButtonsChanged = { respondHeadphoneMediaButtons = it },
                        pauseOnHeadphoneDisconnect = pauseOnHeadphoneDisconnect,
                        onPauseOnHeadphoneDisconnectChanged = { pauseOnHeadphoneDisconnect = it },
                        audioBackendPreference = audioBackendPreference,
                        onAudioBackendPreferenceChanged = { audioBackendPreference = it },
                        audioPerformanceMode = audioPerformanceMode,
                        onAudioPerformanceModeChanged = { audioPerformanceMode = it },
                        audioBufferPreset = audioBufferPreset,
                        onAudioBufferPresetChanged = { audioBufferPreset = it },
                        audioResamplerPreference = audioResamplerPreference,
                        onAudioResamplerPreferenceChanged = {
                            audioResamplerPreference = it
                            if (it == AudioResamplerPreference.Sox) {
                                pendingSoxExperimentalDialog = true
                            }
                        },
                        audioAllowBackendFallback = audioAllowBackendFallback,
                        onAudioAllowBackendFallbackChanged = { audioAllowBackendFallback = it },
                        openPlayerFromNotification = openPlayerFromNotification,
                        onOpenPlayerFromNotificationChanged = { openPlayerFromNotification = it },
                        persistRepeatMode = persistRepeatMode,
                        onPersistRepeatModeChanged = { persistRepeatMode = it },
                        themeMode = themeMode,
                        onThemeModeChanged = onThemeModeChanged,
                        rememberBrowserLocation = rememberBrowserLocation,
                        onRememberBrowserLocationChanged = { rememberBrowserLocation = it },
                        recentFoldersLimit = recentFoldersLimit,
                        onRecentFoldersLimitChanged = { recentFoldersLimit = it.coerceIn(1, RECENTS_LIMIT_MAX) },
                        recentFilesLimit = recentFilesLimit,
                        onRecentFilesLimitChanged = { recentFilesLimit = it.coerceIn(1, RECENTS_LIMIT_MAX) },
                        urlCacheClearOnLaunch = urlCacheClearOnLaunch,
                        onUrlCacheClearOnLaunchChanged = { enabled ->
                            urlCacheClearOnLaunch = enabled
                            prefs.edit().putBoolean(AppPreferenceKeys.URL_CACHE_CLEAR_ON_LAUNCH, enabled).apply()
                        },
                        urlCacheMaxTracks = urlCacheMaxTracks,
                        onUrlCacheMaxTracksChanged = { value ->
                            urlCacheMaxTracks = value.coerceAtLeast(1)
                            prefs.edit().putInt(AppPreferenceKeys.URL_CACHE_MAX_TRACKS, urlCacheMaxTracks).apply()
                            appScope.launch(Dispatchers.IO) {
                                enforceRemoteCacheLimits(
                                    cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR),
                                    maxTracks = urlCacheMaxTracks,
                                    maxBytes = urlCacheMaxBytes
                                )
                                withContext(Dispatchers.Main.immediate) {
                                    refreshCachedSourceFiles()
                                }
                            }
                        },
                        urlCacheMaxBytes = urlCacheMaxBytes,
                        onUrlCacheMaxBytesChanged = { value ->
                            urlCacheMaxBytes = value.coerceAtLeast(1L)
                            prefs.edit().putLong(AppPreferenceKeys.URL_CACHE_MAX_BYTES, urlCacheMaxBytes).apply()
                            appScope.launch(Dispatchers.IO) {
                                enforceRemoteCacheLimits(
                                    cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR),
                                    maxTracks = urlCacheMaxTracks,
                                    maxBytes = urlCacheMaxBytes
                                )
                                withContext(Dispatchers.Main.immediate) {
                                    refreshCachedSourceFiles()
                                }
                            }
                        },
                        onClearUrlCacheNow = {
                            appScope.launch(Dispatchers.IO) {
                                val cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR)
                                val sessionPath = prefs.getString(AppPreferenceKeys.SESSION_CURRENT_PATH, null)
                                val protectedPaths = buildSet {
                                    selectedFile?.absolutePath
                                        ?.takeIf { it.startsWith(cacheRoot.absolutePath) }
                                        ?.let { add(it) }
                                    sessionPath
                                        ?.takeIf { it.startsWith(cacheRoot.absolutePath) }
                                        ?.let { add(it) }
                                }
                                val result = clearRemoteCacheFiles(
                                    cacheRoot = cacheRoot,
                                    protectedPaths = protectedPaths
                                )
                                withContext(Dispatchers.Main) {
                                    val suffix = if (result.skippedFiles > 0) " (kept current track)" else ""
                                    Toast.makeText(
                                        context,
                                        "Cache cleared (${result.deletedFiles} files)$suffix",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    refreshCachedSourceFiles()
                                }
                            }
                        },
                        cachedSourceFiles = cachedSourceFiles,
                        onRefreshCachedSourceFiles = { refreshCachedSourceFiles() },
                        onDeleteCachedSourceFiles = { paths ->
                            appScope.launch(Dispatchers.IO) {
                                val cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR)
                                val sessionPath = prefs.getString(AppPreferenceKeys.SESSION_CURRENT_PATH, null)
                                val protectedPaths = buildSet {
                                    selectedFile?.absolutePath
                                        ?.takeIf { it.startsWith(cacheRoot.absolutePath) }
                                        ?.let { add(it) }
                                    sessionPath
                                        ?.takeIf { it.startsWith(cacheRoot.absolutePath) }
                                        ?.let { add(it) }
                                }
                                val result = deleteSpecificRemoteCacheFiles(
                                    cacheRoot = cacheRoot,
                                    absolutePaths = paths.toSet(),
                                    protectedPaths = protectedPaths
                                )
                                withContext(Dispatchers.Main.immediate) {
                                    val suffix = if (result.skippedFiles > 0) " (${result.skippedFiles} protected)" else ""
                                    Toast.makeText(
                                        context,
                                        "Deleted ${result.deletedFiles} file(s)$suffix",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    refreshCachedSourceFiles()
                                }
                            }
                        },
                        onExportCachedSourceFiles = { paths ->
                            if (paths.isEmpty()) {
                                Toast.makeText(context, "No files selected", Toast.LENGTH_SHORT).show()
                            } else {
                                pendingCacheExportPaths = paths
                                cacheExportDirectoryLauncher.launch(null)
                            }
                        },
                        keepScreenOn = keepScreenOn,
                        onKeepScreenOnChanged = { keepScreenOn = it },
                        playerArtworkCornerRadiusDp = playerArtworkCornerRadiusDp,
                        onPlayerArtworkCornerRadiusDpChanged = { value ->
                            playerArtworkCornerRadiusDp = value.coerceIn(0, 48)
                        },
                        filenameDisplayMode = filenameDisplayMode,
                        onFilenameDisplayModeChanged = { mode ->
                            filenameDisplayMode = mode
                            prefs.edit().putString(AppPreferenceKeys.FILENAME_DISPLAY_MODE, mode.storageValue).apply()
                        },
                        filenameOnlyWhenTitleMissing = filenameOnlyWhenTitleMissing,
                        onFilenameOnlyWhenTitleMissingChanged = { enabled ->
                            filenameOnlyWhenTitleMissing = enabled
                            prefs.edit().putBoolean(AppPreferenceKeys.FILENAME_ONLY_WHEN_TITLE_MISSING, enabled).apply()
                        },
                        unknownTrackDurationSeconds = unknownTrackDurationSeconds,
                        onUnknownTrackDurationSecondsChanged = { value ->
                            unknownTrackDurationSeconds = value
                        },
                        endFadeApplyToAllTracks = endFadeApplyToAllTracks,
                        onEndFadeApplyToAllTracksChanged = { enabled ->
                            endFadeApplyToAllTracks = enabled
                        },
                        endFadeDurationMs = endFadeDurationMs,
                        onEndFadeDurationMsChanged = { value ->
                            endFadeDurationMs = value
                        },
                        endFadeCurve = endFadeCurve,
                        onEndFadeCurveChanged = { curve ->
                            endFadeCurve = curve
                        },
                        visualizationMode = visualizationMode,
                        onVisualizationModeChanged = { mode ->
                            setVisualizationMode(mode)
                        },
                        enabledVisualizationModes = enabledVisualizationModes,
                        onEnabledVisualizationModesChanged = { modes ->
                            setEnabledVisualizationModes(modes)
                        },
                        visualizationShowDebugInfo = visualizationShowDebugInfo,
                        onVisualizationShowDebugInfoChanged = { enabled ->
                            visualizationShowDebugInfo = enabled
                        },
                        visualizationBarCount = visualizationBarCount,
                        onVisualizationBarCountChanged = { value ->
                            visualizationBarCount = value
                        },
                        visualizationBarSmoothingPercent = visualizationBarSmoothingPercent,
                        onVisualizationBarSmoothingPercentChanged = { value ->
                            visualizationBarSmoothingPercent = value
                        },
                        visualizationBarRoundnessDp = visualizationBarRoundnessDp,
                        onVisualizationBarRoundnessDpChanged = { value ->
                            visualizationBarRoundnessDp = value
                        },
                        visualizationBarOverlayArtwork = visualizationBarOverlayArtwork,
                        onVisualizationBarOverlayArtworkChanged = { enabled ->
                            visualizationBarOverlayArtwork = enabled
                        },
                        visualizationBarUseThemeColor = visualizationBarUseThemeColor,
                        onVisualizationBarUseThemeColorChanged = { enabled ->
                            visualizationBarUseThemeColor = enabled
                        },
                        visualizationOscStereo = visualizationOscStereo,
                        onVisualizationOscStereoChanged = { enabled ->
                            visualizationOscStereo = enabled
                        },
                        visualizationVuAnchor = visualizationVuAnchor,
                        onVisualizationVuAnchorChanged = { anchor ->
                            visualizationVuAnchor = anchor
                        },
                        visualizationVuUseThemeColor = visualizationVuUseThemeColor,
                        onVisualizationVuUseThemeColorChanged = { enabled ->
                            visualizationVuUseThemeColor = enabled
                        },
                        visualizationVuSmoothingPercent = visualizationVuSmoothingPercent,
                        onVisualizationVuSmoothingPercentChanged = { value ->
                            visualizationVuSmoothingPercent = value
                        },
                        onResetVisualizationBarsSettings = {
                            visualizationBarCount = AppDefaults.Visualization.Bars.count
                            visualizationBarSmoothingPercent = AppDefaults.Visualization.Bars.smoothingPercent
                            visualizationBarRoundnessDp = AppDefaults.Visualization.Bars.roundnessDp
                            visualizationBarOverlayArtwork = AppDefaults.Visualization.Bars.overlayArtwork
                            visualizationBarUseThemeColor = AppDefaults.Visualization.Bars.useThemeColor
                            prefs.edit()
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_BAR_COLOR_MODE_NO_ARTWORK,
                                    AppDefaults.Visualization.Bars.colorModeNoArtwork.storageValue
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_BAR_COLOR_MODE_WITH_ARTWORK,
                                    AppDefaults.Visualization.Bars.colorModeWithArtwork.storageValue
                                )
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_BAR_CUSTOM_COLOR_ARGB,
                                    AppDefaults.Visualization.Bars.customColorArgb
                                )
                                .apply()
                        },
                        onResetVisualizationOscilloscopeSettings = {
                            visualizationOscStereo = AppDefaults.Visualization.Oscilloscope.stereo
                            prefs.edit()
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_OSC_WINDOW_MS,
                                    AppDefaults.Visualization.Oscilloscope.windowMs
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_OSC_TRIGGER_MODE,
                                    AppDefaults.Visualization.Oscilloscope.triggerMode.storageValue
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_OSC_FPS_MODE,
                                    AppDefaults.Visualization.Oscilloscope.fpsMode.storageValue
                                )
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_OSC_LINE_WIDTH_DP,
                                    AppDefaults.Visualization.Oscilloscope.lineWidthDp
                                )
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_OSC_GRID_WIDTH_DP,
                                    AppDefaults.Visualization.Oscilloscope.gridWidthDp
                                )
                                .putBoolean(
                                    AppPreferenceKeys.VISUALIZATION_OSC_VERTICAL_GRID_ENABLED,
                                    AppDefaults.Visualization.Oscilloscope.verticalGridEnabled
                                )
                                .putBoolean(
                                    AppPreferenceKeys.VISUALIZATION_OSC_CENTER_LINE_ENABLED,
                                    AppDefaults.Visualization.Oscilloscope.centerLineEnabled
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_OSC_LINE_COLOR_MODE_NO_ARTWORK,
                                    AppDefaults.Visualization.Oscilloscope.lineColorModeNoArtwork.storageValue
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_OSC_GRID_COLOR_MODE_NO_ARTWORK,
                                    AppDefaults.Visualization.Oscilloscope.gridColorModeNoArtwork.storageValue
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_OSC_LINE_COLOR_MODE_WITH_ARTWORK,
                                    AppDefaults.Visualization.Oscilloscope.lineColorModeWithArtwork.storageValue
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_OSC_GRID_COLOR_MODE_WITH_ARTWORK,
                                    AppDefaults.Visualization.Oscilloscope.gridColorModeWithArtwork.storageValue
                                )
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_OSC_CUSTOM_LINE_COLOR_ARGB,
                                    AppDefaults.Visualization.Oscilloscope.customLineColorArgb
                                )
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_OSC_CUSTOM_GRID_COLOR_ARGB,
                                    AppDefaults.Visualization.Oscilloscope.customGridColorArgb
                                )
                                .apply()
                        },
                        onResetVisualizationVuSettings = {
                            visualizationVuAnchor = AppDefaults.Visualization.Vu.anchor
                            visualizationVuUseThemeColor = AppDefaults.Visualization.Vu.useThemeColor
                            visualizationVuSmoothingPercent = AppDefaults.Visualization.Vu.smoothingPercent
                            prefs.edit()
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_VU_COLOR_MODE_NO_ARTWORK,
                                    AppDefaults.Visualization.Vu.colorModeNoArtwork.storageValue
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_VU_COLOR_MODE_WITH_ARTWORK,
                                    AppDefaults.Visualization.Vu.colorModeWithArtwork.storageValue
                                )
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_VU_CUSTOM_COLOR_ARGB,
                                    AppDefaults.Visualization.Vu.customColorArgb
                                )
                                .apply()
                        },
                        onResetVisualizationChannelScopeSettings = {
                            prefs.edit()
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_WINDOW_MS,
                                    AppDefaults.Visualization.ChannelScope.windowMs
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_RENDER_BACKEND,
                                    AppDefaults.Visualization.ChannelScope.renderBackend.storageValue
                                )
                                .putBoolean(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_DC_REMOVAL_ENABLED,
                                    AppDefaults.Visualization.ChannelScope.dcRemovalEnabled
                                )
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_GAIN_PERCENT,
                                    AppDefaults.Visualization.ChannelScope.gainPercent
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TRIGGER_MODE,
                                    AppDefaults.Visualization.ChannelScope.triggerMode.storageValue
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_FPS_MODE,
                                    AppDefaults.Visualization.ChannelScope.fpsMode.storageValue
                                )
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_LINE_WIDTH_DP,
                                    AppDefaults.Visualization.ChannelScope.lineWidthDp
                                )
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_GRID_WIDTH_DP,
                                    AppDefaults.Visualization.ChannelScope.gridWidthDp
                                )
                                .putBoolean(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_VERTICAL_GRID_ENABLED,
                                    AppDefaults.Visualization.ChannelScope.verticalGridEnabled
                                )
                                .putBoolean(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_CENTER_LINE_ENABLED,
                                    AppDefaults.Visualization.ChannelScope.centerLineEnabled
                                )
                                .putBoolean(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_SHOW_ARTWORK_BACKGROUND,
                                    AppDefaults.Visualization.ChannelScope.showArtworkBackground
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_BACKGROUND_MODE,
                                    AppDefaults.Visualization.ChannelScope.backgroundMode.storageValue
                                )
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_CUSTOM_BACKGROUND_COLOR_ARGB,
                                    AppDefaults.Visualization.ChannelScope.customBackgroundColorArgb
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_LAYOUT,
                                    AppDefaults.Visualization.ChannelScope.layout.storageValue
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_LINE_COLOR_MODE_NO_ARTWORK,
                                    AppDefaults.Visualization.ChannelScope.lineColorModeNoArtwork.storageValue
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_GRID_COLOR_MODE_NO_ARTWORK,
                                    AppDefaults.Visualization.ChannelScope.gridColorModeNoArtwork.storageValue
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_LINE_COLOR_MODE_WITH_ARTWORK,
                                    AppDefaults.Visualization.ChannelScope.lineColorModeWithArtwork.storageValue
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_GRID_COLOR_MODE_WITH_ARTWORK,
                                    AppDefaults.Visualization.ChannelScope.gridColorModeWithArtwork.storageValue
                                )
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_CUSTOM_LINE_COLOR_ARGB,
                                    AppDefaults.Visualization.ChannelScope.customLineColorArgb
                                )
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_CUSTOM_GRID_COLOR_ARGB,
                                    AppDefaults.Visualization.ChannelScope.customGridColorArgb
                                )
                                .putBoolean(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_ENABLED,
                                    AppDefaults.Visualization.ChannelScope.textEnabled
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_ANCHOR,
                                    AppDefaults.Visualization.ChannelScope.textAnchor.storageValue
                                )
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_PADDING_DP,
                                    AppDefaults.Visualization.ChannelScope.textPaddingDp
                                )
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SIZE_SP,
                                    AppDefaults.Visualization.ChannelScope.textSizeSp
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_NOTE_FORMAT,
                                    AppDefaults.Visualization.ChannelScope.textNoteFormat.storageValue
                                )
                                .putBoolean(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SHOW_CHANNEL,
                                    AppDefaults.Visualization.ChannelScope.textShowChannel
                                )
                                .putBoolean(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SHOW_NOTE,
                                    AppDefaults.Visualization.ChannelScope.textShowNote
                                )
                                .putBoolean(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SHOW_VOLUME,
                                    AppDefaults.Visualization.ChannelScope.textShowVolume
                                )
                                .putBoolean(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SHOW_EFFECT,
                                    AppDefaults.Visualization.ChannelScope.textShowEffect
                                )
                                .putBoolean(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SHOW_INSTRUMENT_SAMPLE,
                                    AppDefaults.Visualization.ChannelScope.textShowInstrumentSample
                                )
                                .apply()
                        },
                        audioFocusInterrupt = audioFocusInterrupt,
                        onAudioFocusInterruptChanged = {
                            audioFocusInterrupt = it
                            prefs.edit().putBoolean(AppPreferenceKeys.AUDIO_FOCUS_INTERRUPT, it).apply()
                            com.flopster101.siliconplayer.PlaybackService.refreshSettings(context)
                        },
                        audioDucking = audioDucking,
                        onAudioDuckingChanged = {
                            audioDucking = it
                            prefs.edit().putBoolean(AppPreferenceKeys.AUDIO_DUCKING, it).apply()
                            com.flopster101.siliconplayer.PlaybackService.refreshSettings(context)
                        },
                        ffmpegSampleRateHz = ffmpegCoreSampleRateHz,
                        ffmpegCapabilities = ffmpegCapabilities,
                        onFfmpegSampleRateChanged = { ffmpegCoreSampleRateHz = it },
                        openMptSampleRateHz = openMptCoreSampleRateHz,
                        openMptCapabilities = openMptCapabilities,
                        onOpenMptSampleRateChanged = { openMptCoreSampleRateHz = it },
                        vgmPlaySampleRateHz = vgmPlayCoreSampleRateHz,
                        vgmPlayCapabilities = vgmPlayCapabilities,
                        onVgmPlaySampleRateChanged = { vgmPlayCoreSampleRateHz = it },
                        gmeSampleRateHz = gmeCoreSampleRateHz,
                        onGmeSampleRateChanged = { gmeCoreSampleRateHz = it },
                        gmeTempoPercent = gmeTempoPercent,
                        onGmeTempoPercentChanged = { gmeTempoPercent = it },
                        gmeStereoSeparationPercent = gmeStereoSeparationPercent,
                        onGmeStereoSeparationPercentChanged = { gmeStereoSeparationPercent = it },
                        gmeEchoEnabled = gmeEchoEnabled,
                        onGmeEchoEnabledChanged = { gmeEchoEnabled = it },
                        gmeAccuracyEnabled = gmeAccuracyEnabled,
                        onGmeAccuracyEnabledChanged = { gmeAccuracyEnabled = it },
                        gmeEqTrebleDecibel = gmeEqTrebleDecibel,
                        onGmeEqTrebleDecibelChanged = { gmeEqTrebleDecibel = it },
                        gmeEqBassHz = gmeEqBassHz,
                        onGmeEqBassHzChanged = { gmeEqBassHz = it },
                        gmeSpcUseBuiltInFade = gmeSpcUseBuiltInFade,
                        onGmeSpcUseBuiltInFadeChanged = { gmeSpcUseBuiltInFade = it },
                        gmeSpcInterpolation = gmeSpcInterpolation,
                        onGmeSpcInterpolationChanged = { gmeSpcInterpolation = it },
                        gmeSpcUseNativeSampleRate = gmeSpcUseNativeSampleRate,
                        onGmeSpcUseNativeSampleRateChanged = { gmeSpcUseNativeSampleRate = it },
                        vgmPlayLoopCount = vgmPlayLoopCount,
                        onVgmPlayLoopCountChanged = { vgmPlayLoopCount = it },
                        vgmPlayAllowNonLoopingLoop = vgmPlayAllowNonLoopingLoop,
                        onVgmPlayAllowNonLoopingLoopChanged = { vgmPlayAllowNonLoopingLoop = it },
                        vgmPlayVsyncRate = vgmPlayVsyncRate,
                        onVgmPlayVsyncRateChanged = { vgmPlayVsyncRate = it },
                        vgmPlayResampleMode = vgmPlayResampleMode,
                        onVgmPlayResampleModeChanged = { vgmPlayResampleMode = it },
                        vgmPlayChipSampleMode = vgmPlayChipSampleMode,
                        onVgmPlayChipSampleModeChanged = { vgmPlayChipSampleMode = it },
                        vgmPlayChipSampleRate = vgmPlayChipSampleRate,
                        onVgmPlayChipSampleRateChanged = { vgmPlayChipSampleRate = it },
                        vgmPlayChipCoreSelections = vgmPlayChipCoreSelections,
                        onVgmPlayChipCoreChanged = { chipKey, selectedValue ->
                            vgmPlayChipCoreSelections = vgmPlayChipCoreSelections + (chipKey to selectedValue)
                        },
                        openMptStereoSeparationPercent = openMptStereoSeparationPercent,
                        onOpenMptStereoSeparationPercentChanged = { openMptStereoSeparationPercent = it },
                        openMptStereoSeparationAmigaPercent = openMptStereoSeparationAmigaPercent,
                        onOpenMptStereoSeparationAmigaPercentChanged = { openMptStereoSeparationAmigaPercent = it },
                        openMptInterpolationFilterLength = openMptInterpolationFilterLength,
                        onOpenMptInterpolationFilterLengthChanged = { openMptInterpolationFilterLength = it },
                        openMptAmigaResamplerMode = openMptAmigaResamplerMode,
                        onOpenMptAmigaResamplerModeChanged = { openMptAmigaResamplerMode = it },
                        openMptAmigaResamplerApplyAllModules = openMptAmigaResamplerApplyAllModules,
                        onOpenMptAmigaResamplerApplyAllModulesChanged = { openMptAmigaResamplerApplyAllModules = it },
                        openMptVolumeRampingStrength = openMptVolumeRampingStrength,
                        onOpenMptVolumeRampingStrengthChanged = { openMptVolumeRampingStrength = it },
                        openMptFt2XmVolumeRamping = openMptFt2XmVolumeRamping,
                        onOpenMptFt2XmVolumeRampingChanged = { openMptFt2XmVolumeRamping = it },
                        openMptMasterGainMilliBel = openMptMasterGainMilliBel,
                        onOpenMptMasterGainMilliBelChanged = { openMptMasterGainMilliBel = it },
                        openMptSurroundEnabled = openMptSurroundEnabled,
                        onOpenMptSurroundEnabledChanged = { openMptSurroundEnabled = it },
                        onClearRecentHistory = {
                            recentFolders = emptyList()
                            recentPlayedFiles = emptyList()
                            prefs.edit()
                                .remove(AppPreferenceKeys.RECENT_FOLDERS)
                                .remove(AppPreferenceKeys.RECENT_PLAYED_FILES)
                                .apply()
                            Toast.makeText(
                                context,
                                "Home recents cleared",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onClearAllSettings = {
                            val pluginSnapshot = mapOf(
                                CorePreferenceKeys.CORE_RATE_FFMPEG to ffmpegCoreSampleRateHz,
                                CorePreferenceKeys.CORE_RATE_OPENMPT to openMptCoreSampleRateHz,
                                CorePreferenceKeys.CORE_RATE_VGMPLAY to vgmPlayCoreSampleRateHz,
                                CorePreferenceKeys.CORE_RATE_GME to gmeCoreSampleRateHz,
                                CorePreferenceKeys.VGMPLAY_LOOP_COUNT to vgmPlayLoopCount,
                                CorePreferenceKeys.VGMPLAY_VSYNC_RATE to vgmPlayVsyncRate,
                                CorePreferenceKeys.VGMPLAY_RESAMPLE_MODE to vgmPlayResampleMode,
                                CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_MODE to vgmPlayChipSampleMode,
                                CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_RATE to vgmPlayChipSampleRate,
                                CorePreferenceKeys.GME_TEMPO_PERCENT to gmeTempoPercent,
                                CorePreferenceKeys.GME_STEREO_SEPARATION_PERCENT to gmeStereoSeparationPercent,
                                CorePreferenceKeys.GME_EQ_TREBLE_DECIBEL to gmeEqTrebleDecibel,
                                CorePreferenceKeys.GME_EQ_BASS_HZ to gmeEqBassHz,
                                CorePreferenceKeys.GME_SPC_INTERPOLATION to gmeSpcInterpolation,
                                CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_PERCENT to openMptStereoSeparationPercent,
                                CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_AMIGA_PERCENT to openMptStereoSeparationAmigaPercent,
                                CorePreferenceKeys.OPENMPT_INTERPOLATION_FILTER_LENGTH to openMptInterpolationFilterLength,
                                CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_MODE to openMptAmigaResamplerMode,
                                CorePreferenceKeys.OPENMPT_VOLUME_RAMPING_STRENGTH to openMptVolumeRampingStrength,
                                CorePreferenceKeys.OPENMPT_MASTER_GAIN_MILLIBEL to openMptMasterGainMilliBel
                            )
                            val pluginBooleanSnapshot = mapOf(
                                CorePreferenceKeys.VGMPLAY_ALLOW_NON_LOOPING_LOOP to vgmPlayAllowNonLoopingLoop,
                                CorePreferenceKeys.GME_ECHO_ENABLED to gmeEchoEnabled,
                                CorePreferenceKeys.GME_ACCURACY_ENABLED to gmeAccuracyEnabled,
                                CorePreferenceKeys.GME_SPC_USE_BUILTIN_FADE to gmeSpcUseBuiltInFade,
                                CorePreferenceKeys.GME_SPC_USE_NATIVE_SAMPLE_RATE to gmeSpcUseNativeSampleRate,
                                CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_APPLY_ALL_MODULES to openMptAmigaResamplerApplyAllModules,
                                CorePreferenceKeys.OPENMPT_FT2_XM_VOLUME_RAMPING to openMptFt2XmVolumeRamping,
                                CorePreferenceKeys.OPENMPT_SURROUND_ENABLED to openMptSurroundEnabled
                            )
                            val vgmChipCoreSnapshot = vgmPlayChipCoreSelections

                            prefs.edit().clear().apply()

                            val restoreEditor = prefs.edit()
                            pluginSnapshot.forEach { (key, value) -> restoreEditor.putInt(key, value) }
                            pluginBooleanSnapshot.forEach { (key, value) -> restoreEditor.putBoolean(key, value) }
                            vgmChipCoreSnapshot.forEach { (chipKey, value) ->
                                restoreEditor.putInt(CorePreferenceKeys.vgmPlayChipCoreKey(chipKey), value)
                            }
                            restoreEditor.apply()

                            autoPlayOnTrackSelect = true
                            openPlayerOnTrackSelect = true
                            autoPlayNextTrackOnEnd = true
                            previousRestartsAfterThreshold = true
                            respondHeadphoneMediaButtons = true
                            pauseOnHeadphoneDisconnect = true
                            audioBackendPreference = AudioBackendPreference.AAudio
                            audioPerformanceMode = AudioPerformanceMode.None
                            audioBufferPreset = AudioBufferPreset.Medium
                            audioResamplerPreference = AudioResamplerPreference.BuiltIn
                            audioAllowBackendFallback = true
                            openPlayerFromNotification = true
                            persistRepeatMode = true
                            preferredRepeatMode = RepeatMode.None
                            rememberBrowserLocation = true
                            urlCacheClearOnLaunch = false
                            urlCacheMaxTracks = SOURCE_CACHE_MAX_TRACKS_DEFAULT
                            urlCacheMaxBytes = SOURCE_CACHE_MAX_BYTES_DEFAULT
                            lastBrowserLocationId = null
                            lastBrowserDirectoryPath = null
                            recentFoldersLimit = RECENT_FOLDERS_LIMIT_DEFAULT
                            recentFilesLimit = RECENT_FILES_LIMIT_DEFAULT
                            recentFolders = emptyList()
                            recentPlayedFiles = emptyList()
                            keepScreenOn = AppDefaults.Player.keepScreenOn
                            playerArtworkCornerRadiusDp = AppDefaults.Player.artworkCornerRadiusDp
                            filenameDisplayMode = FilenameDisplayMode.Always
                            filenameOnlyWhenTitleMissing = false
                            unknownTrackDurationSeconds = GmeDefaults.unknownDurationSeconds
                            endFadeApplyToAllTracks = AppDefaults.Player.endFadeApplyToAllTracks
                            endFadeDurationMs = AppDefaults.Player.endFadeDurationMs
                            endFadeCurve = AppDefaults.Player.endFadeCurve
                            visualizationMode = VisualizationMode.Off
                            enabledVisualizationModes = selectableVisualizationModes.toSet()
                            visualizationShowDebugInfo = AppDefaults.Visualization.showDebugInfo
                            visualizationBarCount = AppDefaults.Visualization.Bars.count
                            visualizationBarSmoothingPercent = AppDefaults.Visualization.Bars.smoothingPercent
                            visualizationBarRoundnessDp = AppDefaults.Visualization.Bars.roundnessDp
                            visualizationBarOverlayArtwork = AppDefaults.Visualization.Bars.overlayArtwork
                            visualizationBarUseThemeColor = AppDefaults.Visualization.Bars.useThemeColor
                            prefs.edit()
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_BAR_COLOR_MODE_NO_ARTWORK,
                                    AppDefaults.Visualization.Bars.colorModeNoArtwork.storageValue
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_BAR_COLOR_MODE_WITH_ARTWORK,
                                    AppDefaults.Visualization.Bars.colorModeWithArtwork.storageValue
                                )
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_BAR_CUSTOM_COLOR_ARGB,
                                    AppDefaults.Visualization.Bars.customColorArgb
                                )
                                .apply()
                            visualizationOscStereo = AppDefaults.Visualization.Oscilloscope.stereo
                            prefs.edit()
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_OSC_WINDOW_MS,
                                    AppDefaults.Visualization.Oscilloscope.windowMs
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_OSC_TRIGGER_MODE,
                                    AppDefaults.Visualization.Oscilloscope.triggerMode.storageValue
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_OSC_FPS_MODE,
                                    AppDefaults.Visualization.Oscilloscope.fpsMode.storageValue
                                )
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_OSC_LINE_WIDTH_DP,
                                    AppDefaults.Visualization.Oscilloscope.lineWidthDp
                                )
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_OSC_GRID_WIDTH_DP,
                                    AppDefaults.Visualization.Oscilloscope.gridWidthDp
                                )
                                .putBoolean(
                                    AppPreferenceKeys.VISUALIZATION_OSC_VERTICAL_GRID_ENABLED,
                                    AppDefaults.Visualization.Oscilloscope.verticalGridEnabled
                                )
                                .putBoolean(
                                    AppPreferenceKeys.VISUALIZATION_OSC_CENTER_LINE_ENABLED,
                                    AppDefaults.Visualization.Oscilloscope.centerLineEnabled
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_OSC_LINE_COLOR_MODE_NO_ARTWORK,
                                    AppDefaults.Visualization.Oscilloscope.lineColorModeNoArtwork.storageValue
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_OSC_GRID_COLOR_MODE_NO_ARTWORK,
                                    AppDefaults.Visualization.Oscilloscope.gridColorModeNoArtwork.storageValue
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_OSC_LINE_COLOR_MODE_WITH_ARTWORK,
                                    AppDefaults.Visualization.Oscilloscope.lineColorModeWithArtwork.storageValue
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_OSC_GRID_COLOR_MODE_WITH_ARTWORK,
                                    AppDefaults.Visualization.Oscilloscope.gridColorModeWithArtwork.storageValue
                                )
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_OSC_CUSTOM_LINE_COLOR_ARGB,
                                    AppDefaults.Visualization.Oscilloscope.customLineColorArgb
                                )
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_OSC_CUSTOM_GRID_COLOR_ARGB,
                                    AppDefaults.Visualization.Oscilloscope.customGridColorArgb
                                )
                                .apply()
                            visualizationVuAnchor = AppDefaults.Visualization.Vu.anchor
                            visualizationVuUseThemeColor = AppDefaults.Visualization.Vu.useThemeColor
                            visualizationVuSmoothingPercent = AppDefaults.Visualization.Vu.smoothingPercent
                            prefs.edit()
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_VU_COLOR_MODE_NO_ARTWORK,
                                    AppDefaults.Visualization.Vu.colorModeNoArtwork.storageValue
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_VU_COLOR_MODE_WITH_ARTWORK,
                                    AppDefaults.Visualization.Vu.colorModeWithArtwork.storageValue
                                )
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_VU_CUSTOM_COLOR_ARGB,
                                    AppDefaults.Visualization.Vu.customColorArgb
                                )
                                .apply()
                            prefs.edit()
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_WINDOW_MS,
                                    AppDefaults.Visualization.ChannelScope.windowMs
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_RENDER_BACKEND,
                                    AppDefaults.Visualization.ChannelScope.renderBackend.storageValue
                                )
                                .putBoolean(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_DC_REMOVAL_ENABLED,
                                    AppDefaults.Visualization.ChannelScope.dcRemovalEnabled
                                )
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_GAIN_PERCENT,
                                    AppDefaults.Visualization.ChannelScope.gainPercent
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TRIGGER_MODE,
                                    AppDefaults.Visualization.ChannelScope.triggerMode.storageValue
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_FPS_MODE,
                                    AppDefaults.Visualization.ChannelScope.fpsMode.storageValue
                                )
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_LINE_WIDTH_DP,
                                    AppDefaults.Visualization.ChannelScope.lineWidthDp
                                )
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_GRID_WIDTH_DP,
                                    AppDefaults.Visualization.ChannelScope.gridWidthDp
                                )
                                .putBoolean(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_VERTICAL_GRID_ENABLED,
                                    AppDefaults.Visualization.ChannelScope.verticalGridEnabled
                                )
                                .putBoolean(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_CENTER_LINE_ENABLED,
                                    AppDefaults.Visualization.ChannelScope.centerLineEnabled
                                )
                                .putBoolean(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_SHOW_ARTWORK_BACKGROUND,
                                    AppDefaults.Visualization.ChannelScope.showArtworkBackground
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_BACKGROUND_MODE,
                                    AppDefaults.Visualization.ChannelScope.backgroundMode.storageValue
                                )
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_CUSTOM_BACKGROUND_COLOR_ARGB,
                                    AppDefaults.Visualization.ChannelScope.customBackgroundColorArgb
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_LAYOUT,
                                    AppDefaults.Visualization.ChannelScope.layout.storageValue
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_LINE_COLOR_MODE_NO_ARTWORK,
                                    AppDefaults.Visualization.ChannelScope.lineColorModeNoArtwork.storageValue
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_GRID_COLOR_MODE_NO_ARTWORK,
                                    AppDefaults.Visualization.ChannelScope.gridColorModeNoArtwork.storageValue
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_LINE_COLOR_MODE_WITH_ARTWORK,
                                    AppDefaults.Visualization.ChannelScope.lineColorModeWithArtwork.storageValue
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_GRID_COLOR_MODE_WITH_ARTWORK,
                                    AppDefaults.Visualization.ChannelScope.gridColorModeWithArtwork.storageValue
                                )
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_CUSTOM_LINE_COLOR_ARGB,
                                    AppDefaults.Visualization.ChannelScope.customLineColorArgb
                                )
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_CUSTOM_GRID_COLOR_ARGB,
                                    AppDefaults.Visualization.ChannelScope.customGridColorArgb
                                )
                                .putBoolean(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_ENABLED,
                                    AppDefaults.Visualization.ChannelScope.textEnabled
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_ANCHOR,
                                    AppDefaults.Visualization.ChannelScope.textAnchor.storageValue
                                )
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_PADDING_DP,
                                    AppDefaults.Visualization.ChannelScope.textPaddingDp
                                )
                                .putInt(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SIZE_SP,
                                    AppDefaults.Visualization.ChannelScope.textSizeSp
                                )
                                .putString(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_NOTE_FORMAT,
                                    AppDefaults.Visualization.ChannelScope.textNoteFormat.storageValue
                                )
                                .putBoolean(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SHOW_CHANNEL,
                                    AppDefaults.Visualization.ChannelScope.textShowChannel
                                )
                                .putBoolean(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SHOW_NOTE,
                                    AppDefaults.Visualization.ChannelScope.textShowNote
                                )
                                .putBoolean(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SHOW_VOLUME,
                                    AppDefaults.Visualization.ChannelScope.textShowVolume
                                )
                                .putBoolean(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SHOW_EFFECT,
                                    AppDefaults.Visualization.ChannelScope.textShowEffect
                                )
                                .putBoolean(
                                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SHOW_INSTRUMENT_SAMPLE,
                                    AppDefaults.Visualization.ChannelScope.textShowInstrumentSample
                                )
                                .apply()
                            browserLaunchLocationId = null
                            browserLaunchDirectoryPath = null
                            onThemeModeChanged(ThemeMode.Auto)

                            Toast.makeText(
                                context,
                                "All app settings cleared",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onClearAllPluginSettings = {
                            ffmpegCoreSampleRateHz = 0
                            openMptCoreSampleRateHz = OpenMptDefaults.coreSampleRateHz
                            vgmPlayCoreSampleRateHz = VgmPlayDefaults.coreSampleRateHz
                            gmeCoreSampleRateHz = GmeDefaults.coreSampleRateHz
                            gmeTempoPercent = GmeDefaults.tempoPercent
                            gmeStereoSeparationPercent = GmeDefaults.stereoSeparationPercent
                            gmeEchoEnabled = GmeDefaults.echoEnabled
                            gmeAccuracyEnabled = GmeDefaults.accuracyEnabled
                            gmeEqTrebleDecibel = GmeDefaults.eqTrebleDecibel
                            gmeEqBassHz = GmeDefaults.eqBassHz
                            gmeSpcUseBuiltInFade = GmeDefaults.spcUseBuiltInFade
                            gmeSpcInterpolation = GmeDefaults.spcInterpolation
                            gmeSpcUseNativeSampleRate = GmeDefaults.spcUseNativeSampleRate
                            vgmPlayLoopCount = VgmPlayDefaults.loopCount
                            vgmPlayAllowNonLoopingLoop = VgmPlayDefaults.allowNonLoopingLoop
                            vgmPlayVsyncRate = VgmPlayDefaults.vsyncRate
                            vgmPlayResampleMode = VgmPlayDefaults.resampleMode
                            vgmPlayChipSampleMode = VgmPlayDefaults.chipSampleMode
                            vgmPlayChipSampleRate = VgmPlayDefaults.chipSampleRate
                            vgmPlayChipCoreSelections = VgmPlayConfig.defaultChipCoreSelections()
                            openMptStereoSeparationPercent = OpenMptDefaults.stereoSeparationPercent
                            openMptStereoSeparationAmigaPercent = OpenMptDefaults.stereoSeparationAmigaPercent
                            openMptInterpolationFilterLength = OpenMptDefaults.interpolationFilterLength
                            openMptAmigaResamplerMode = OpenMptDefaults.amigaResamplerMode
                            openMptAmigaResamplerApplyAllModules = OpenMptDefaults.amigaResamplerApplyAllModules
                            openMptVolumeRampingStrength = OpenMptDefaults.volumeRampingStrength
                            openMptFt2XmVolumeRamping = OpenMptDefaults.ft2XmVolumeRamping
                            openMptMasterGainMilliBel = OpenMptDefaults.masterGainMilliBel
                            openMptSurroundEnabled = OpenMptDefaults.surroundEnabled

                            prefs.edit().apply {
                                remove(CorePreferenceKeys.CORE_RATE_FFMPEG)
                                remove(CorePreferenceKeys.CORE_RATE_OPENMPT)
                                remove(CorePreferenceKeys.CORE_RATE_VGMPLAY)
                                remove(CorePreferenceKeys.CORE_RATE_GME)
                                remove(CorePreferenceKeys.GME_TEMPO_PERCENT)
                                remove(CorePreferenceKeys.GME_STEREO_SEPARATION_PERCENT)
                                remove(CorePreferenceKeys.GME_ECHO_ENABLED)
                                remove(CorePreferenceKeys.GME_ACCURACY_ENABLED)
                                remove(CorePreferenceKeys.GME_EQ_TREBLE_DECIBEL)
                                remove(CorePreferenceKeys.GME_EQ_BASS_HZ)
                                remove(CorePreferenceKeys.GME_SPC_USE_BUILTIN_FADE)
                                remove(CorePreferenceKeys.GME_SPC_INTERPOLATION)
                                remove(CorePreferenceKeys.GME_SPC_USE_NATIVE_SAMPLE_RATE)
                                remove(CorePreferenceKeys.VGMPLAY_LOOP_COUNT)
                                remove(CorePreferenceKeys.VGMPLAY_ALLOW_NON_LOOPING_LOOP)
                                remove(CorePreferenceKeys.VGMPLAY_VSYNC_RATE)
                                remove(CorePreferenceKeys.VGMPLAY_RESAMPLE_MODE)
                                remove(CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_MODE)
                                remove(CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_RATE)
                                remove(CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_PERCENT)
                                remove(CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_AMIGA_PERCENT)
                                remove(CorePreferenceKeys.OPENMPT_INTERPOLATION_FILTER_LENGTH)
                                remove(CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_MODE)
                                remove(CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_APPLY_ALL_MODULES)
                                remove(CorePreferenceKeys.OPENMPT_VOLUME_RAMPING_STRENGTH)
                                remove(CorePreferenceKeys.OPENMPT_FT2_XM_VOLUME_RAMPING)
                                remove(CorePreferenceKeys.OPENMPT_MASTER_GAIN_MILLIBEL)
                                remove(CorePreferenceKeys.OPENMPT_SURROUND_ENABLED)
                                VgmPlayConfig.chipCoreSpecs.forEach { spec ->
                                    remove(CorePreferenceKeys.vgmPlayChipCoreKey(spec.key))
                                }
                                apply()
                            }

                            Toast.makeText(
                                context,
                                "Plugin settings cleared",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onResetPluginSettings = { pluginName ->
                            val optionNamesForReset = when (pluginName) {
                                "LibOpenMPT" -> listOf(
                                    "openmpt.stereo_separation_percent",
                                    "openmpt.stereo_separation_amiga_percent",
                                    "openmpt.interpolation_filter_length",
                                    "openmpt.amiga_resampler_mode",
                                    "openmpt.amiga_resampler_apply_all_modules",
                                    "openmpt.volume_ramping_strength",
                                    "openmpt.ft2_xm_volume_ramping",
                                    "openmpt.master_gain_millibel",
                                    "openmpt.surround_enabled"
                                )
                                "VGMPlay" -> buildList {
                                    add(VgmPlayOptionKeys.LOOP_COUNT)
                                    add(VgmPlayOptionKeys.ALLOW_NON_LOOPING_LOOP)
                                    add(VgmPlayOptionKeys.VSYNC_RATE_HZ)
                                    add(VgmPlayOptionKeys.RESAMPLE_MODE)
                                    add(VgmPlayOptionKeys.CHIP_SAMPLE_MODE)
                                    add(VgmPlayOptionKeys.CHIP_SAMPLE_RATE_HZ)
                                    VgmPlayConfig.chipCoreSpecs.forEach { spec ->
                                        add("${VgmPlayOptionKeys.CHIP_CORE_PREFIX}${spec.key}")
                                    }
                                }
                                "Game Music Emu" -> listOf(
                                    GmeOptionKeys.TEMPO,
                                    GmeOptionKeys.STEREO_SEPARATION,
                                    GmeOptionKeys.ECHO_ENABLED,
                                    GmeOptionKeys.ACCURACY_ENABLED,
                                    GmeOptionKeys.EQ_TREBLE_DB,
                                    GmeOptionKeys.EQ_BASS_HZ,
                                    GmeOptionKeys.SPC_USE_BUILTIN_FADE,
                                    GmeOptionKeys.SPC_INTERPOLATION,
                                    GmeOptionKeys.SPC_USE_NATIVE_SAMPLE_RATE
                                )
                                else -> emptyList()
                            }
                            val requiresPlaybackRestart = optionNamesForReset.any { optionName ->
                                try {
                                    NativeBridge.getCoreOptionApplyPolicy(pluginName, optionName) == 1
                                } catch (_: Throwable) {
                                    false
                                }
                            }
                            when (pluginName) {
                                "FFmpeg" -> {
                                    ffmpegCoreSampleRateHz = FfmpegDefaults.coreSampleRateHz
                                    prefs.edit().remove(CorePreferenceKeys.CORE_RATE_FFMPEG).apply()
                                }
                                "LibOpenMPT" -> {
                                    openMptCoreSampleRateHz = OpenMptDefaults.coreSampleRateHz
                                    openMptStereoSeparationPercent = OpenMptDefaults.stereoSeparationPercent
                                    openMptStereoSeparationAmigaPercent = OpenMptDefaults.stereoSeparationAmigaPercent
                                    openMptInterpolationFilterLength = OpenMptDefaults.interpolationFilterLength
                                    openMptAmigaResamplerMode = OpenMptDefaults.amigaResamplerMode
                                    openMptAmigaResamplerApplyAllModules = OpenMptDefaults.amigaResamplerApplyAllModules
                                    openMptVolumeRampingStrength = OpenMptDefaults.volumeRampingStrength
                                    openMptFt2XmVolumeRamping = OpenMptDefaults.ft2XmVolumeRamping
                                    openMptMasterGainMilliBel = OpenMptDefaults.masterGainMilliBel
                                    openMptSurroundEnabled = OpenMptDefaults.surroundEnabled
                                    prefs.edit().apply {
                                        remove(CorePreferenceKeys.CORE_RATE_OPENMPT)
                                        remove(CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_PERCENT)
                                        remove(CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_AMIGA_PERCENT)
                                        remove(CorePreferenceKeys.OPENMPT_INTERPOLATION_FILTER_LENGTH)
                                        remove(CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_MODE)
                                        remove(CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_APPLY_ALL_MODULES)
                                        remove(CorePreferenceKeys.OPENMPT_VOLUME_RAMPING_STRENGTH)
                                        remove(CorePreferenceKeys.OPENMPT_FT2_XM_VOLUME_RAMPING)
                                        remove(CorePreferenceKeys.OPENMPT_MASTER_GAIN_MILLIBEL)
                                        remove(CorePreferenceKeys.OPENMPT_SURROUND_ENABLED)
                                        apply()
                                    }
                                }
                                "VGMPlay" -> {
                                    vgmPlayCoreSampleRateHz = VgmPlayDefaults.coreSampleRateHz
                                    vgmPlayLoopCount = VgmPlayDefaults.loopCount
                                    vgmPlayAllowNonLoopingLoop = VgmPlayDefaults.allowNonLoopingLoop
                                    vgmPlayVsyncRate = VgmPlayDefaults.vsyncRate
                                    vgmPlayResampleMode = VgmPlayDefaults.resampleMode
                                    vgmPlayChipSampleMode = VgmPlayDefaults.chipSampleMode
                                    vgmPlayChipSampleRate = VgmPlayDefaults.chipSampleRate
                                    vgmPlayChipCoreSelections = VgmPlayConfig.defaultChipCoreSelections()
                                    prefs.edit().apply {
                                        remove(CorePreferenceKeys.CORE_RATE_VGMPLAY)
                                        remove(CorePreferenceKeys.VGMPLAY_LOOP_COUNT)
                                        remove(CorePreferenceKeys.VGMPLAY_ALLOW_NON_LOOPING_LOOP)
                                        remove(CorePreferenceKeys.VGMPLAY_VSYNC_RATE)
                                        remove(CorePreferenceKeys.VGMPLAY_RESAMPLE_MODE)
                                        remove(CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_MODE)
                                        remove(CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_RATE)
                                        VgmPlayConfig.chipCoreSpecs.forEach { spec ->
                                            remove(CorePreferenceKeys.vgmPlayChipCoreKey(spec.key))
                                        }
                                        apply()
                                    }
                                }
                                "Game Music Emu" -> {
                                    gmeCoreSampleRateHz = GmeDefaults.coreSampleRateHz
                                    gmeTempoPercent = GmeDefaults.tempoPercent
                                    gmeStereoSeparationPercent = GmeDefaults.stereoSeparationPercent
                                    gmeEchoEnabled = GmeDefaults.echoEnabled
                                    gmeAccuracyEnabled = GmeDefaults.accuracyEnabled
                                    gmeEqTrebleDecibel = GmeDefaults.eqTrebleDecibel
                                    gmeEqBassHz = GmeDefaults.eqBassHz
                                    gmeSpcUseBuiltInFade = GmeDefaults.spcUseBuiltInFade
                                    gmeSpcInterpolation = GmeDefaults.spcInterpolation
                                    gmeSpcUseNativeSampleRate = GmeDefaults.spcUseNativeSampleRate
                                    prefs.edit().apply {
                                        remove(CorePreferenceKeys.CORE_RATE_GME)
                                        remove(CorePreferenceKeys.GME_TEMPO_PERCENT)
                                        remove(CorePreferenceKeys.GME_STEREO_SEPARATION_PERCENT)
                                        remove(CorePreferenceKeys.GME_ECHO_ENABLED)
                                        remove(CorePreferenceKeys.GME_ACCURACY_ENABLED)
                                        remove(CorePreferenceKeys.GME_EQ_TREBLE_DECIBEL)
                                        remove(CorePreferenceKeys.GME_EQ_BASS_HZ)
                                        remove(CorePreferenceKeys.GME_SPC_USE_BUILTIN_FADE)
                                        remove(CorePreferenceKeys.GME_SPC_INTERPOLATION)
                                        remove(CorePreferenceKeys.GME_SPC_USE_NATIVE_SAMPLE_RATE)
                                        apply()
                                    }
                                }
                            }
                            Toast.makeText(
                                context,
                                if (requiresPlaybackRestart) {
                                    "Settings reset. Playback restart needed for some changes."
                                } else {
                                    "$pluginName settings reset"
                                },
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        )
                    }
                }
            }
        }

        run {
            val canResumeStoppedTrack = lastStoppedFile?.exists() == true || !lastStoppedSourceId.isNullOrBlank()
            if (isPlayerSurfaceVisible && !isPlayerExpanded && miniExpandPreviewProgress > 0f) {
                val previewProgress = miniExpandPreviewProgress.coerceIn(0f, 1f)
                val previewOffsetPx = (1f - previewProgress) * screenHeightPx
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.BottomCenter)
                        .graphicsLayer {
                            translationY = previewOffsetPx
                            alpha = (previewProgress * 0.98f).coerceIn(0f, 1f)
                        }
                ) {
                    com.flopster101.siliconplayer.ui.screens.PlayerScreen(
                        file = selectedFile,
                        onBack = {},
                        enableCollapseGesture = false,
                        isPlaying = isPlaying,
                        canResumeStoppedTrack = false,
                        onPlay = {},
                        onPause = {},
                        onStopAndClear = {},
                        durationSeconds = duration,
                        positionSeconds = position,
                        canPreviousTrack = currentTrackIndex() > 0,
                        canNextTrack = currentTrackIndex() in 0 until (visiblePlayableFiles.size - 1),
                        title = metadataTitle,
                        artist = metadataArtist,
                        sampleRateHz = metadataSampleRate,
                        channelCount = metadataChannelCount,
                        bitDepthLabel = metadataBitDepthLabel,
                        decoderName = NativeBridge.getCurrentDecoderName().takeIf { it.isNotBlank() },
                        playbackSourceLabel = playbackSourceLabel,
                        pathOrUrl = currentTrackPathOrUrl,
                        artwork = artworkBitmap,
                        noArtworkIcon = placeholderArtworkIconForFile(selectedFile),
                        repeatMode = activeRepeatMode,
                        canCycleRepeatMode = supportsLiveRepeatMode(playbackCapabilitiesFlags),
                        canSeek = canSeekPlayback(playbackCapabilitiesFlags),
                        hasReliableDuration = hasReliableDuration(playbackCapabilitiesFlags),
                        onSeek = {},
                        onPreviousTrack = {},
                        onNextTrack = {},
                        onPreviousSubtune = {},
                        onNextSubtune = {},
                        onOpenSubtuneSelector = {},
                        canPreviousSubtune = false,
                        canNextSubtune = false,
                        canOpenSubtuneSelector = false,
                        onCycleRepeatMode = {},
                        canOpenCoreSettings = canOpenCurrentCoreSettings,
                        onOpenCoreSettings = openCurrentCoreSettings,
                        visualizationMode = visualizationMode,
                        availableVisualizationModes = availableVisualizationModes,
                        onCycleVisualizationMode = cycleVisualizationMode,
                        onSelectVisualizationMode = setVisualizationMode,
                        onOpenVisualizationSettings = openVisualizationSettings,
                        onOpenSelectedVisualizationSettings = openSelectedVisualizationSettings,
                        visualizationBarCount = visualizationBarCount,
                        visualizationBarSmoothingPercent = visualizationBarSmoothingPercent,
                        visualizationBarRoundnessDp = visualizationBarRoundnessDp,
                        visualizationBarOverlayArtwork = visualizationBarOverlayArtwork,
                        visualizationBarUseThemeColor = visualizationBarUseThemeColor,
                        visualizationOscStereo = visualizationOscStereo,
                        visualizationVuAnchor = visualizationVuAnchor,
                        visualizationVuUseThemeColor = visualizationVuUseThemeColor,
                        visualizationVuSmoothingPercent = visualizationVuSmoothingPercent,
                        onOpenAudioEffects = {
                            tempMasterVolumeDb = masterVolumeDb
                            tempPluginVolumeDb = pluginVolumeDb
                            tempSongVolumeDb = songVolumeDb
                            tempForceMono = forceMono
                            showAudioEffectsDialog = true
                        },
                        visualizationShowDebugInfo = visualizationShowDebugInfo,
                        artworkCornerRadiusDp = playerArtworkCornerRadiusDp,
                        filenameDisplayMode = filenameDisplayMode,
                        filenameOnlyWhenTitleMissing = filenameOnlyWhenTitleMissing
                    )
                }
            }
            AnimatedVisibility(
                visible = isPlayerSurfaceVisible && !isPlayerExpanded,
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn() + scaleIn(initialScale = 0.96f),
                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val dismissState = rememberSwipeToDismissBoxState(
                    positionalThreshold = { totalDistance -> totalDistance * 0.6f },
                    confirmValueChange = { targetValue ->
                        val isDismiss = targetValue == SwipeToDismissBoxValue.StartToEnd ||
                            targetValue == SwipeToDismissBoxValue.EndToStart
                        if (isDismiss && !isPlaying) {
                            hidePlayerSurface()
                            true
                        } else {
                            false
                        }
                    }
                )
                val miniPlayerModifier = Modifier
                    .graphicsLayer {
                        val dragProgress = miniExpandPreviewProgress.coerceIn(0f, 1f)
                        val hideMini = expandFromMiniDrag || isPlayerExpanded
                        val alphaFloor = if (hideMini) 0f else 0.16f
                        alpha = (1f - dragProgress).coerceIn(alphaFloor, 1f)
                        translationY = -miniPreviewLiftPx * dragProgress
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp)

                val miniPlayerContent: @Composable () -> Unit = {
                    MiniPlayerBar(
                        file = selectedFile,
                        title = metadataTitle.ifBlank {
                            selectedFile?.nameWithoutExtension?.ifBlank { selectedFile?.name ?: "" }
                                ?: "No track selected"
                        },
                        artist = metadataArtist.ifBlank {
                            if (selectedFile != null) "Unknown Artist" else "Tap a file to play"
                        },
                        artwork = artworkBitmap,
                        noArtworkIcon = placeholderArtworkIconForFile(selectedFile),
                        isPlaying = isPlaying,
                        canResumeStoppedTrack = canResumeStoppedTrack,
                        positionSeconds = position,
                        durationSeconds = duration,
                        hasReliableDuration = hasReliableDuration(playbackCapabilitiesFlags),
                        canPreviousTrack = currentTrackIndex() > 0,
                        canNextTrack = currentTrackIndex() in 0 until (visiblePlayableFiles.size - 1),
                        onExpand = {
                            collapseFromSwipe = false
                            expandFromMiniDrag = miniExpandPreviewProgress > 0f
                            miniExpandPreviewProgress = 0f
                            isPlayerExpanded = true
                        },
                        onExpandDragProgress = { progress ->
                            miniExpandPreviewProgress = progress
                        },
                        onExpandDragCommit = {
                            val start = miniExpandPreviewProgress.coerceIn(0f, 1f)
                            uiScope.launch {
                                expandFromMiniDrag = true
                                val anim = Animatable(start)
                                anim.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing)
                                ) {
                                    miniExpandPreviewProgress = value
                                }
                                collapseFromSwipe = false
                                isPlayerExpanded = true
                            }
                        },
                        onPreviousTrack = { handlePreviousTrackAction() },
                        onNextTrack = { playAdjacentTrack(1) },
                        onPlayPause = {
                            if (selectedFile == null) {
                                resumeLastStoppedTrack(autoStart = true)
                            } else {
                                if (isPlaying) {
                                    NativeBridge.stopEngine()
                                    isPlaying = false
                                } else {
                                    val sourceId = currentPlaybackSourceId ?: selectedFile?.absolutePath
                                    if (sourceId != null) {
                                        addRecentPlayedTrack(
                                            path = sourceId,
                                            locationId = if (isLocalPlayableFile(selectedFile)) lastBrowserLocationId else null,
                                            title = metadataTitle,
                                            artist = metadataArtist
                                        )
                                    }
                                    applyRepeatModeToNative(activeRepeatMode)
                                    NativeBridge.startEngine()
                                    isPlaying = true
                                    if (sourceId != null) {
                                        scheduleRecentTrackMetadataRefresh(
                                            sourceId = sourceId,
                                            locationId = if (isLocalPlayableFile(selectedFile)) lastBrowserLocationId else null
                                        )
                                    }
                                }
                                syncPlaybackService()
                            }
                        },
                        onStopAndClear = stopAndEmptyTrack
                    )
                }

                SwipeToDismissBox(
                    state = dismissState,
                    modifier = miniPlayerModifier,
                    backgroundContent = {},
                    enableDismissFromStartToEnd = !isPlaying,
                    enableDismissFromEndToStart = !isPlaying
                ) {
                    miniPlayerContent()
                }
            }
            AnimatedVisibility(
                visible = isPlayerSurfaceVisible && isPlayerExpanded,
                enter = if (expandFromMiniDrag) {
                    EnterTransition.None
                } else {
                    slideInVertically(initialOffsetY = { it / 3 }) + fadeIn() + scaleIn(initialScale = 0.96f)
                },
                exit = if (collapseFromSwipe) {
                    fadeOut(animationSpec = tween(120))
                } else {
                    slideOutVertically(targetOffsetY = { it / 3 }) + fadeOut()
                }
            ) {
                com.flopster101.siliconplayer.ui.screens.PlayerScreen(
                    file = selectedFile,
                    onBack = {
                        collapseFromSwipe = false
                        miniExpandPreviewProgress = 0f
                        isPlayerExpanded = false
                    },
                    onCollapseBySwipe = {
                        collapseFromSwipe = true
                        miniExpandPreviewProgress = 0f
                        isPlayerExpanded = false
                    },
                    isPlaying = isPlaying,
                    canResumeStoppedTrack = canResumeStoppedTrack,
                    onPlay = {
                        if (selectedFile == null) {
                            resumeLastStoppedTrack(autoStart = true)
                        } else {
                            val sourceId = currentPlaybackSourceId ?: selectedFile?.absolutePath
                            if (sourceId != null) {
                                addRecentPlayedTrack(
                                    path = sourceId,
                                    locationId = if (isLocalPlayableFile(selectedFile)) lastBrowserLocationId else null,
                                    title = metadataTitle,
                                    artist = metadataArtist
                                )
                            }
                            applyRepeatModeToNative(activeRepeatMode)
                            NativeBridge.startEngine()
                            isPlaying = true
                            if (sourceId != null) {
                                scheduleRecentTrackMetadataRefresh(
                                    sourceId = sourceId,
                                    locationId = if (isLocalPlayableFile(selectedFile)) lastBrowserLocationId else null
                                )
                            }
                            syncPlaybackService()
                        }
                    },
                    onPause = {
                        NativeBridge.stopEngine()
                        isPlaying = false
                        syncPlaybackService()
                    },
                    onStopAndClear = stopAndEmptyTrack,
                    durationSeconds = duration,
                    positionSeconds = position,
                    canPreviousTrack = currentTrackIndex() > 0,
                    canNextTrack = currentTrackIndex() in 0 until (visiblePlayableFiles.size - 1),
                    title = metadataTitle,
                    artist = metadataArtist,
                    sampleRateHz = metadataSampleRate,
                    channelCount = metadataChannelCount,
                    bitDepthLabel = metadataBitDepthLabel,
                    decoderName = NativeBridge.getCurrentDecoderName().takeIf { it.isNotBlank() },
                    playbackSourceLabel = playbackSourceLabel,
                    pathOrUrl = currentTrackPathOrUrl,
                    artwork = artworkBitmap,
                    noArtworkIcon = placeholderArtworkIconForFile(selectedFile),
                    repeatMode = activeRepeatMode,
                    canCycleRepeatMode = supportsLiveRepeatMode(playbackCapabilitiesFlags),
                    canSeek = canSeekPlayback(playbackCapabilitiesFlags),
                    hasReliableDuration = hasReliableDuration(playbackCapabilitiesFlags),
                    onSeek = { seconds ->
                        NativeBridge.seekTo(seconds)
                        position = seconds
                        syncPlaybackService()
                    },
                    onPreviousTrack = { handlePreviousTrackAction() },
                    onNextTrack = { playAdjacentTrack(1) },
                    onPreviousSubtune = {
                        val target = (currentSubtuneIndex - 1).coerceAtLeast(0)
                        if (target != currentSubtuneIndex) {
                            selectSubtune(target)
                        }
                    },
                    onNextSubtune = {
                        val maxIndex = (subtuneCount - 1).coerceAtLeast(0)
                        val target = (currentSubtuneIndex + 1).coerceAtMost(maxIndex)
                        if (target != currentSubtuneIndex) {
                            selectSubtune(target)
                        }
                    },
                    onOpenSubtuneSelector = {
                        if (subtuneCount > 1) {
                            refreshSubtuneEntries()
                            showSubtuneSelectorDialog = true
                        } else {
                            Toast.makeText(context, "No subtunes available", Toast.LENGTH_SHORT).show()
                        }
                    },
                    canPreviousSubtune = subtuneCount > 1 && currentSubtuneIndex > 0,
                    canNextSubtune = subtuneCount > 1 && currentSubtuneIndex < (subtuneCount - 1),
                    canOpenSubtuneSelector = subtuneCount > 1,
                    onCycleRepeatMode = { cycleRepeatMode() },
                    canOpenCoreSettings = canOpenCurrentCoreSettings,
                    onOpenCoreSettings = openCurrentCoreSettings,
                    visualizationMode = visualizationMode,
                    availableVisualizationModes = availableVisualizationModes,
                    onCycleVisualizationMode = cycleVisualizationMode,
                    onSelectVisualizationMode = setVisualizationMode,
                    onOpenVisualizationSettings = openVisualizationSettings,
                    onOpenSelectedVisualizationSettings = openSelectedVisualizationSettings,
                    visualizationBarCount = visualizationBarCount,
                    visualizationBarSmoothingPercent = visualizationBarSmoothingPercent,
                    visualizationBarRoundnessDp = visualizationBarRoundnessDp,
                    visualizationBarOverlayArtwork = visualizationBarOverlayArtwork,
                    visualizationBarUseThemeColor = visualizationBarUseThemeColor,
                    visualizationOscStereo = visualizationOscStereo,
                    visualizationVuAnchor = visualizationVuAnchor,
                    visualizationVuUseThemeColor = visualizationVuUseThemeColor,
                    visualizationVuSmoothingPercent = visualizationVuSmoothingPercent,
                    visualizationShowDebugInfo = visualizationShowDebugInfo,
                    artworkCornerRadiusDp = playerArtworkCornerRadiusDp,
                    onOpenAudioEffects = {
                        tempMasterVolumeDb = masterVolumeDb
                        tempPluginVolumeDb = pluginVolumeDb
                        tempSongVolumeDb = songVolumeDb
                        tempForceMono = forceMono
                        showAudioEffectsDialog = true
                    },
                    filenameDisplayMode = filenameDisplayMode,
                    filenameOnlyWhenTitleMissing = filenameOnlyWhenTitleMissing
                )
            }

            if (showUrlOrPathDialog) {
                AlertDialog(
                    onDismissRequest = { showUrlOrPathDialog = false },
                    title = { Text("Open URL or path") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Supported: /unix/path, file:///path/to/file, http(s)://example/file"
                            )
                            OutlinedTextField(
                                value = urlOrPathInput,
                                onValueChange = { urlOrPathInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Path or URL") },
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val next = !urlOrPathForceCaching
                                        urlOrPathForceCaching = next
                                        prefs.edit()
                                            .putBoolean(AppPreferenceKeys.URL_PATH_FORCE_CACHING, next)
                                            .apply()
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = urlOrPathForceCaching,
                                    onCheckedChange = { checked ->
                                        urlOrPathForceCaching = checked
                                        prefs.edit()
                                            .putBoolean(AppPreferenceKeys.URL_PATH_FORCE_CACHING, checked)
                                            .apply()
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Force caching",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = urlOrPathInput.isNotBlank(),
                            onClick = {
                                val input = urlOrPathInput
                                val openOptions = ManualSourceOpenOptions(forceCaching = urlOrPathForceCaching)
                                showUrlOrPathDialog = false
                                applyManualInputSelection(input, openOptions)
                            }
                        ) {
                            Text("Open")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUrlOrPathDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            remoteLoadUiState?.let { loadState ->
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Opening remote source") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val phaseText = when (loadState.phase) {
                                RemoteLoadPhase.Connecting -> "Connecting..."
                                RemoteLoadPhase.Downloading -> "Downloading..."
                                RemoteLoadPhase.Opening -> "Opening..."
                            }
                            Text(phaseText)
                            if (loadState.indeterminate) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            } else {
                                val progress = (loadState.percent ?: 0).coerceIn(0, 100) / 100f
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (loadState.phase == RemoteLoadPhase.Downloading || loadState.phase == RemoteLoadPhase.Opening) {
                                val downloadedLabel = formatByteCount(loadState.downloadedBytes)
                                val sizeLabel = loadState.totalBytes?.let { total ->
                                    "$downloadedLabel / ${formatByteCount(total)}"
                                } ?: downloadedLabel
                                Text(
                                    text = sizeLabel,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                loadState.percent?.let { percent ->
                                    Text(
                                        text = "$percent%",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                loadState.bytesPerSecond?.takeIf { it > 0L }?.let { speed ->
                                    Text(
                                        text = "${formatByteCount(speed)}/s",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            remoteLoadJob?.cancel()
                            remoteLoadUiState = null
                        }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showSoxExperimentalDialog) {
                AlertDialog(
                    onDismissRequest = { showSoxExperimentalDialog = false },
                    title = { Text("SoX is experimental") },
                    text = {
                        Text(
                            "SoX output resampling may cause unstable timeline behavior on some content. " +
                                "For discontinuous timeline cores (like module pattern jumps/loop points), " +
                                "the engine automatically falls back to Built-in for stability."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showSoxExperimentalDialog = false }) {
                            Text("OK")
                        }
                    }
                )
            }

            if (showSubtuneSelectorDialog) {
                AlertDialog(
                    onDismissRequest = { showSubtuneSelectorDialog = false },
                    title = { Text("Subtunes") },
                    text = {
                        if (subtuneEntries.isEmpty()) {
                            Text("No subtunes available.")
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 360.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                subtuneEntries.forEach { entry ->
                                    val isCurrent = entry.index == currentSubtuneIndex
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(MaterialTheme.shapes.medium)
                                            .clickable {
                                                selectSubtune(entry.index)
                                                showSubtuneSelectorDialog = false
                                            },
                                        shape = MaterialTheme.shapes.medium,
                                        color = if (isCurrent) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        }
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                        ) {
                                            Text(
                                                text = "${entry.index + 1}. ${entry.title}",
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            val details = buildString {
                                                append(formatShortDuration(entry.durationSeconds))
                                                if (entry.artist.isNotBlank()) {
                                                    append(" • ")
                                                    append(entry.artist)
                                                }
                                            }
                                            Text(
                                                text = details,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSubtuneSelectorDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }

            if (showAudioEffectsDialog) {
                AudioEffectsDialog(
                    masterVolumeDb = tempMasterVolumeDb,
                    pluginVolumeDb = tempPluginVolumeDb,
                    songVolumeDb = tempSongVolumeDb,
                    forceMono = tempForceMono,
                    hasActiveCore = lastUsedCoreName != null,
                    hasActiveSong = selectedFile != null,
                    currentCoreName = lastUsedCoreName,
                    onMasterVolumeChange = {
                        tempMasterVolumeDb = it
                        NativeBridge.setMasterGain(it)
                    },
                    onPluginVolumeChange = {
                        tempPluginVolumeDb = it
                        NativeBridge.setPluginGain(it)
                    },
                    onSongVolumeChange = {
                        tempSongVolumeDb = it
                        NativeBridge.setSongGain(it)
                    },
                    onForceMonoChange = {
                        tempForceMono = it
                        NativeBridge.setForceMono(it)
                    },
                    onReset = {
                        // Reset all values to 0dB / false
                        tempMasterVolumeDb = 0f
                        tempPluginVolumeDb = 0f
                        tempSongVolumeDb = 0f
                        tempForceMono = false
                        NativeBridge.setMasterGain(0f)
                        NativeBridge.setPluginGain(0f)
                        NativeBridge.setSongGain(0f)
                        NativeBridge.setForceMono(false)
                    },
                    onDismiss = {
                        // Cancel: revert to original values
                        NativeBridge.setMasterGain(masterVolumeDb)
                        NativeBridge.setPluginGain(readPluginVolumeForDecoder(prefs, lastUsedCoreName))
                        NativeBridge.setSongGain(songVolumeDb)
                        NativeBridge.setForceMono(forceMono)
                        showAudioEffectsDialog = false
                    },
                    onConfirm = {
                        // OK: save changes
                        masterVolumeDb = tempMasterVolumeDb
                        pluginVolumeDb = tempPluginVolumeDb
                        songVolumeDb = tempSongVolumeDb
                        forceMono = tempForceMono

                        // Save to preferences
                        prefs.edit().apply {
                            putFloat(AppPreferenceKeys.AUDIO_MASTER_VOLUME_DB, masterVolumeDb)
                            putBoolean(AppPreferenceKeys.AUDIO_FORCE_MONO, forceMono)
                            apply()
                        }
                        writePluginVolumeForDecoder(
                            prefs = prefs,
                            decoderName = lastUsedCoreName,
                            valueDb = pluginVolumeDb
                        )

                        // Save song volume to database
                        selectedFile?.absolutePath?.let { path ->
                            volumeDatabase.setSongVolume(path, songVolumeDb)
                        }

                        showAudioEffectsDialog = false
                    }
                )
            }
        }
    }
}
