package com.flopster101.siliconplayer

import android.content.SharedPreferences
import org.json.JSONObject

private const val BROWSER_PERSISTED_KIND = "kind"
private const val BROWSER_PERSISTED_KIND_LOCAL = "local"
private const val BROWSER_PERSISTED_KIND_ARCHIVE = "archive"
private const val BROWSER_PERSISTED_LOCATION_ID = "locationId"
private const val BROWSER_PERSISTED_DIRECTORY_PATH = "directoryPath"

internal fun readRememberedBrowserLaunchState(
    prefs: SharedPreferences
): BrowserLaunchState {
    val serialized = prefs.getString(AppPreferenceKeys.BROWSER_LAST_LOCATION_STATE_JSON, null)
    val parsedFromJson = parsePersistedBrowserLaunchState(serialized)
    if (parsedFromJson != null) {
        return parsedFromJson
    }

    val legacyLocationId = prefs.getString(AppPreferenceKeys.BROWSER_LAST_LOCATION_ID, null)
    val legacyDirectoryPath = prefs.getString(AppPreferenceKeys.BROWSER_LAST_DIRECTORY_PATH, null)
    val migrated = sanitizeRememberedBrowserState(
        BrowserLaunchState(
            locationId = legacyLocationId,
            directoryPath = legacyDirectoryPath
        )
    )
    if (migrated != null) {
        persistRememberedBrowserLaunchState(prefs, migrated)
        return migrated
    }
    return BrowserLaunchState()
}

internal fun persistRememberedBrowserLaunchState(
    prefs: SharedPreferences,
    state: BrowserLaunchState
) {
    val sanitized = sanitizeRememberedBrowserState(state)
    val serialized = serializedRememberedBrowserLaunchState(sanitized)
    prefs.edit()
        .putString(AppPreferenceKeys.BROWSER_LAST_LOCATION_STATE_JSON, serialized)
        .putString(AppPreferenceKeys.BROWSER_LAST_LOCATION_ID, sanitized?.locationId)
        .putString(AppPreferenceKeys.BROWSER_LAST_DIRECTORY_PATH, sanitized?.directoryPath)
        .apply()
}

internal fun clearRememberedBrowserLaunchState(
    prefs: SharedPreferences
) {
    prefs.edit()
        .remove(AppPreferenceKeys.BROWSER_LAST_LOCATION_STATE_JSON)
        .remove(AppPreferenceKeys.BROWSER_LAST_LOCATION_ID)
        .remove(AppPreferenceKeys.BROWSER_LAST_DIRECTORY_PATH)
        .apply()
}

private fun parsePersistedBrowserLaunchState(serialized: String?): BrowserLaunchState? {
    val raw = serialized?.trim().takeUnless { it.isNullOrBlank() } ?: return null
    val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    val kind = json.optString(BROWSER_PERSISTED_KIND).trim()
    val locationId = json.optString(BROWSER_PERSISTED_LOCATION_ID).ifBlank { null }
    val directoryPath = json.optString(BROWSER_PERSISTED_DIRECTORY_PATH).ifBlank { null }
    return when (kind) {
        BROWSER_PERSISTED_KIND_LOCAL,
        BROWSER_PERSISTED_KIND_ARCHIVE -> sanitizeRememberedBrowserState(
            BrowserLaunchState(
                locationId = locationId,
                directoryPath = directoryPath
            )
        )
        else -> null
    }
}

private fun serializedRememberedBrowserLaunchState(
    state: BrowserLaunchState?
): String? {
    val resolved = state ?: return null
    val model = resolveBrowserLocationModel(
        initialLocationId = resolved.locationId,
        initialDirectoryPath = resolved.directoryPath,
        initialSmbSourceNodeId = null,
        initialHttpSourceNodeId = null,
        initialHttpRootPath = null
    )
    val kind = when (model) {
        is BrowserLocationModel.Local -> BROWSER_PERSISTED_KIND_LOCAL
        is BrowserLocationModel.ArchiveLogical -> BROWSER_PERSISTED_KIND_ARCHIVE
        else -> return null
    }
    return JSONObject()
        .put(BROWSER_PERSISTED_KIND, kind)
        .put(BROWSER_PERSISTED_LOCATION_ID, resolved.locationId ?: "")
        .put(BROWSER_PERSISTED_DIRECTORY_PATH, resolved.directoryPath ?: "")
        .toString()
}

private fun sanitizeRememberedBrowserState(
    state: BrowserLaunchState?
): BrowserLaunchState? {
    val resolved = state ?: return null
    val model = resolveBrowserLocationModel(
        initialLocationId = resolved.locationId,
        initialDirectoryPath = resolved.directoryPath,
        initialSmbSourceNodeId = null,
        initialHttpSourceNodeId = null,
        initialHttpRootPath = null
    )
    return when (model) {
        is BrowserLocationModel.Local -> BrowserLaunchState(
            locationId = model.locationId,
            directoryPath = model.directoryPath
        )
        is BrowserLocationModel.ArchiveLogical -> BrowserLaunchState(
            locationId = resolved.locationId,
            directoryPath = model.logicalPath
        )
        else -> null
    }
}
