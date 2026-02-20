package com.flopster101.siliconplayer

import android.content.Context
import android.util.Log
import com.flopster101.siliconplayer.data.ARCHIVE_CACHE_MAX_AGE_DAYS_DEFAULT
import com.flopster101.siliconplayer.data.ARCHIVE_CACHE_MAX_BYTES_DEFAULT
import com.flopster101.siliconplayer.data.ARCHIVE_CACHE_MAX_MOUNTS_DEFAULT
import com.flopster101.siliconplayer.data.clearArchiveMountCache
import com.flopster101.siliconplayer.data.enforceArchiveMountCacheLimits

private const val ARCHIVE_CACHE_TAG = "ArchiveCachePolicy"

internal fun applyArchiveMountCachePolicyOnLaunch(context: Context, cacheDir: java.io.File) {
    val prefs = context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
    val clearOnLaunch = prefs.getBoolean(AppPreferenceKeys.ARCHIVE_CACHE_CLEAR_ON_LAUNCH, false)
    if (clearOnLaunch) {
        val result = clearArchiveMountCache(cacheDir)
        if (result.deletedMounts > 0) {
            Log.d(
                ARCHIVE_CACHE_TAG,
                "Cleared archive mounts on launch: deleted=${result.deletedMounts} freed=${result.freedBytes}"
            )
        }
        return
    }
    val maxMounts = prefs.getInt(AppPreferenceKeys.ARCHIVE_CACHE_MAX_MOUNTS, ARCHIVE_CACHE_MAX_MOUNTS_DEFAULT)
    val maxBytes = prefs.getLong(AppPreferenceKeys.ARCHIVE_CACHE_MAX_BYTES, ARCHIVE_CACHE_MAX_BYTES_DEFAULT)
    val maxAgeDays = prefs.getInt(AppPreferenceKeys.ARCHIVE_CACHE_MAX_AGE_DAYS, ARCHIVE_CACHE_MAX_AGE_DAYS_DEFAULT)
    val result = enforceArchiveMountCacheLimits(
        cacheDir = cacheDir,
        maxMounts = maxMounts,
        maxBytes = maxBytes,
        maxAgeDays = maxAgeDays
    )
    if (result.deletedMounts > 0) {
        Log.d(
            ARCHIVE_CACHE_TAG,
            "Pruned archive mounts on launch: deleted=${result.deletedMounts} freed=${result.freedBytes} limits=(mounts=$maxMounts bytes=$maxBytes ageDays=$maxAgeDays)"
        )
    }
}
