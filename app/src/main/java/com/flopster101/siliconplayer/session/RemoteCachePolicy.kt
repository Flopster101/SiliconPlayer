package com.flopster101.siliconplayer

import android.content.Context
import android.util.Log
import java.io.File

internal fun applyRemoteSourceCachePolicyOnLaunch(context: Context, cacheDir: File) {
    val prefs = context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
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
