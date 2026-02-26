package com.flopster101.siliconplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.Locale

internal enum class BrowserRouteMode {
    Local,
    Smb,
    Http
}

internal data class BrowserRouteResolution(
    val locationModel: BrowserLocationModel,
    val requestedMode: BrowserRouteMode,
    val requestedSmbSpec: SmbSourceSpec?,
    val requestedSmbSourceNodeId: Long?,
    val requestedHttpSpec: HttpSourceSpec?,
    val requestedHttpSourceNodeId: Long?,
    val requestedHttpRootPath: String?,
    val requestedLocalLocationId: String?,
    val requestedLocalDirectoryPath: String?,
    val requestedSmbSessionKey: String?,
    val requestedHttpSessionKey: String?
)

internal data class BrowserRouteRenderState(
    val renderMode: BrowserRouteMode,
    val renderSmbSpec: SmbSourceSpec?,
    val renderHttpSpec: HttpSourceSpec?,
    val renderSmbSessionKey: String?,
    val renderHttpSessionKey: String?
)

internal fun resolveBrowserRouteResolution(
    initialLocationId: String?,
    initialDirectoryPath: String?,
    initialSmbSourceNodeId: Long?,
    initialHttpSourceNodeId: Long?,
    initialHttpRootPath: String?
): BrowserRouteResolution {
    val requestedLocationModel = resolveBrowserLocationModel(
        initialLocationId = initialLocationId,
        initialDirectoryPath = initialDirectoryPath,
        initialSmbSourceNodeId = initialSmbSourceNodeId,
        initialHttpSourceNodeId = initialHttpSourceNodeId,
        initialHttpRootPath = initialHttpRootPath
    )
    val requestedSmbSpec = (requestedLocationModel as? BrowserLocationModel.Smb)?.spec
    val requestedSmbSourceNodeId = (requestedLocationModel as? BrowserLocationModel.Smb)?.sourceNodeId
        ?: initialSmbSourceNodeId
    val requestedHttpSpec = (requestedLocationModel as? BrowserLocationModel.Http)?.spec
    val requestedHttpSourceNodeId = (requestedLocationModel as? BrowserLocationModel.Http)?.sourceNodeId
        ?: initialHttpSourceNodeId
    val requestedHttpRootPath = (requestedLocationModel as? BrowserLocationModel.Http)?.browserRootPath
        ?: initialHttpRootPath
    val requestedMode = when {
        requestedSmbSpec != null -> BrowserRouteMode.Smb
        requestedHttpSpec != null -> BrowserRouteMode.Http
        else -> BrowserRouteMode.Local
    }
    val requestedSmbSessionKey = smbBrowserSessionKey(requestedSmbSpec, requestedSmbSourceNodeId)
    val requestedHttpSessionKey = httpBrowserSessionKey(
        requestedHttpSpec,
        requestedHttpSourceNodeId,
        requestedHttpRootPath
    )
    val requestedLocalLocationId = when (requestedLocationModel) {
        is BrowserLocationModel.Local -> requestedLocationModel.locationId
        else -> initialLocationId
    }
    val requestedLocalDirectoryPath = when (requestedLocationModel) {
        is BrowserLocationModel.Local -> requestedLocationModel.directoryPath
        is BrowserLocationModel.ArchiveLogical -> requestedLocationModel.logicalPath
        else -> initialDirectoryPath
    }
    return BrowserRouteResolution(
        locationModel = requestedLocationModel,
        requestedMode = requestedMode,
        requestedSmbSpec = requestedSmbSpec,
        requestedSmbSourceNodeId = requestedSmbSourceNodeId,
        requestedHttpSpec = requestedHttpSpec,
        requestedHttpSourceNodeId = requestedHttpSourceNodeId,
        requestedHttpRootPath = requestedHttpRootPath,
        requestedLocalLocationId = requestedLocalLocationId,
        requestedLocalDirectoryPath = requestedLocalDirectoryPath,
        requestedSmbSessionKey = requestedSmbSessionKey,
        requestedHttpSessionKey = requestedHttpSessionKey
    )
}

@Composable
internal fun rememberBrowserRouteRenderState(
    resolution: BrowserRouteResolution
): BrowserRouteRenderState {
    var activeMode by remember { mutableStateOf(resolution.requestedMode) }
    var activeSmbSpec by remember { mutableStateOf(resolution.requestedSmbSpec) }
    var activeHttpSpec by remember { mutableStateOf(resolution.requestedHttpSpec) }
    var activeSmbSessionKey by remember { mutableStateOf(resolution.requestedSmbSessionKey) }
    var activeHttpSessionKey by remember { mutableStateOf(resolution.requestedHttpSessionKey) }

    LaunchedEffect(
        resolution.requestedMode,
        resolution.requestedSmbSpec,
        resolution.requestedHttpSpec,
        resolution.requestedSmbSessionKey,
        resolution.requestedHttpSessionKey
    ) {
        when (resolution.requestedMode) {
            BrowserRouteMode.Smb -> {
                if (activeMode != BrowserRouteMode.Smb || activeSmbSessionKey != resolution.requestedSmbSessionKey) {
                    activeMode = BrowserRouteMode.Smb
                    activeSmbSpec = resolution.requestedSmbSpec
                    activeSmbSessionKey = resolution.requestedSmbSessionKey
                }
            }

            BrowserRouteMode.Http -> {
                if (activeMode != BrowserRouteMode.Http || activeHttpSessionKey != resolution.requestedHttpSessionKey) {
                    activeMode = BrowserRouteMode.Http
                    activeHttpSpec = resolution.requestedHttpSpec
                    activeHttpSessionKey = resolution.requestedHttpSessionKey
                }
            }

            BrowserRouteMode.Local -> {
                if (activeMode != BrowserRouteMode.Local) {
                    activeMode = BrowserRouteMode.Local
                }
            }
        }
    }

    val renderMode = if (resolution.requestedMode != activeMode) resolution.requestedMode else activeMode
    val renderSmbSpec = if (renderMode == BrowserRouteMode.Smb) {
        if (resolution.requestedMode == BrowserRouteMode.Smb) resolution.requestedSmbSpec else activeSmbSpec
    } else {
        null
    }
    val renderHttpSpec = if (renderMode == BrowserRouteMode.Http) {
        if (resolution.requestedMode == BrowserRouteMode.Http) resolution.requestedHttpSpec else activeHttpSpec
    } else {
        null
    }
    val renderSmbSessionKey = if (renderMode == BrowserRouteMode.Smb) {
        if (resolution.requestedMode == BrowserRouteMode.Smb) resolution.requestedSmbSessionKey else activeSmbSessionKey
    } else {
        null
    }
    val renderHttpSessionKey = if (renderMode == BrowserRouteMode.Http) {
        if (resolution.requestedMode == BrowserRouteMode.Http) resolution.requestedHttpSessionKey else activeHttpSessionKey
    } else {
        null
    }
    return BrowserRouteRenderState(
        renderMode = renderMode,
        renderSmbSpec = renderSmbSpec,
        renderHttpSpec = renderHttpSpec,
        renderSmbSessionKey = renderSmbSessionKey,
        renderHttpSessionKey = renderHttpSessionKey
    )
}

private fun smbBrowserSessionKey(
    spec: SmbSourceSpec?,
    sourceNodeId: Long?
): String? {
    val resolved = spec ?: return null
    val nodeScope = sourceNodeId?.toString() ?: "adhoc"
    val host = resolved.host.trim().lowercase(Locale.ROOT)
    val share = resolved.share.trim().lowercase(Locale.ROOT)
    val username = resolved.username?.trim().orEmpty()
    return "node=$nodeScope|host=$host|share=$share|user=$username"
}

private fun httpBrowserSessionKey(
    spec: HttpSourceSpec?,
    sourceNodeId: Long?,
    browserRootPath: String?
): String? {
    val resolved = spec ?: return null
    val nodeScope = sourceNodeId?.toString() ?: "adhoc"
    val rootPath = browserRootPath?.trim().takeUnless { it.isNullOrBlank() } ?: "/"
    val host = resolved.host.trim().lowercase(Locale.ROOT)
    val scheme = resolved.scheme.trim().lowercase(Locale.ROOT)
    val username = resolved.username?.trim().orEmpty()
    val port = resolved.port ?: -1
    return "node=$nodeScope|scheme=$scheme|host=$host|port=$port|user=$username|root=$rootPath"
}
