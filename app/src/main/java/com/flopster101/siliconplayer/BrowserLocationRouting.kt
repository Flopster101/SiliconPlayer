package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.data.parseArchiveLogicalPath

internal data class BrowserRecentFolderUpdate(
    val path: String,
    val locationId: String?,
    val sourceNodeId: Long?
)

internal data class BrowserLocationChangedUpdate(
    val recentFolderUpdate: BrowserRecentFolderUpdate?,
    val launchLocationId: String?,
    val launchDirectoryPath: String?,
    val shouldPersistRememberedLocation: Boolean,
    val rememberedLocationId: String?,
    val rememberedDirectoryPath: String?
)

internal fun isNetworkBrowserDirectoryPath(path: String?): Boolean {
    return when (
        resolveBrowserLocationModel(
            initialLocationId = null,
            initialDirectoryPath = path,
            initialSmbSourceNodeId = null,
            initialHttpSourceNodeId = null,
            initialHttpRootPath = null
        )
    ) {
        is BrowserLocationModel.Smb,
        is BrowserLocationModel.Http -> true
        is BrowserLocationModel.ArchiveLogical -> {
            val archiveLocation = parseArchiveLogicalPath(path)?.first
            archiveLocation != null && (
                parseSmbSourceSpecFromInput(archiveLocation) != null ||
                    parseHttpSourceSpecFromInput(archiveLocation) != null
                )
        }
        else -> false
    }
}

internal fun buildBrowserLocationChangedUpdate(
    launchState: BrowserLaunchState,
    rememberBrowserLocation: Boolean,
    networkNodes: List<NetworkNode>
): BrowserLocationChangedUpdate {
    val model = resolveBrowserLocationModel(
        initialLocationId = launchState.locationId,
        initialDirectoryPath = launchState.directoryPath,
        initialSmbSourceNodeId = launchState.smbSourceNodeId,
        initialHttpSourceNodeId = launchState.httpSourceNodeId,
        initialHttpRootPath = launchState.httpRootPath
    )
    val normalizedDirectoryPath = when (model) {
        is BrowserLocationModel.Home -> null
        is BrowserLocationModel.Local -> model.directoryPath
        is BrowserLocationModel.Smb -> buildSmbRequestUri(model.spec)
        is BrowserLocationModel.Http -> buildHttpRequestUri(model.spec)
        is BrowserLocationModel.ArchiveLogical -> model.logicalPath
    }

    val recentFolderUpdate = if (
        normalizedDirectoryPath != null &&
        isEligibleForRecentFolderUpdate(model) &&
        (
            launchState.locationId != null ||
                model is BrowserLocationModel.Smb ||
                model is BrowserLocationModel.Http ||
                model is BrowserLocationModel.ArchiveLogical
            )
    ) {
        val recentPath = when (model) {
            is BrowserLocationModel.Smb -> buildSmbSourceId(model.spec)
            is BrowserLocationModel.Http -> buildHttpSourceId(model.spec)
            is BrowserLocationModel.ArchiveLogical -> model.logicalPath
            else -> normalizedDirectoryPath
        }
        val sourceNodeId = when (model) {
            is BrowserLocationModel.Smb -> launchState.smbSourceNodeId
            is BrowserLocationModel.Http -> launchState.httpSourceNodeId
            is BrowserLocationModel.ArchiveLogical -> {
                val archiveLocation = parseArchiveLogicalPath(model.logicalPath)?.first
                when {
                    archiveLocation != null && parseSmbSourceSpecFromInput(archiveLocation) != null ->
                        launchState.smbSourceNodeId
                    archiveLocation != null && parseHttpSourceSpecFromInput(archiveLocation) != null ->
                        launchState.httpSourceNodeId
                    else -> null
                }
            }
            else -> null
        }
        BrowserRecentFolderUpdate(
            path = recentPath,
            locationId = launchState.locationId,
            sourceNodeId = sourceNodeId
        )
    } else {
        null
    }

    val launchLocationId = when (model) {
        is BrowserLocationModel.Local -> model.locationId
        else -> null
    }
    val launchDirectoryPath = when (model) {
        is BrowserLocationModel.Smb -> resolveSmbLaunchRequestUriForRestore(
            spec = model.spec,
            sourceNodeId = launchState.smbSourceNodeId,
            networkNodes = networkNodes
        )
        is BrowserLocationModel.Http -> resolveHttpLaunchRequestUriForRestore(
            spec = model.spec,
            sourceNodeId = launchState.httpSourceNodeId,
            networkNodes = networkNodes
        )
        is BrowserLocationModel.Local -> model.directoryPath
        is BrowserLocationModel.ArchiveLogical -> model.logicalPath
        is BrowserLocationModel.Home -> null
    }

    val archiveLocation = (model as? BrowserLocationModel.ArchiveLogical)
        ?.let { parseArchiveLogicalPath(it.logicalPath)?.first }
    val archiveIsRemote = archiveLocation != null && (
        parseSmbSourceSpecFromInput(archiveLocation) != null ||
            parseHttpSourceSpecFromInput(archiveLocation) != null
        )
    val shouldPersistRememberedLocation = rememberBrowserLocation &&
        when (model) {
            is BrowserLocationModel.Local -> true
            is BrowserLocationModel.ArchiveLogical -> !archiveIsRemote
            else -> false
        } &&
        launchDirectoryPath != null

    return BrowserLocationChangedUpdate(
        recentFolderUpdate = recentFolderUpdate,
        launchLocationId = launchLocationId,
        launchDirectoryPath = launchDirectoryPath,
        shouldPersistRememberedLocation = shouldPersistRememberedLocation,
        rememberedLocationId = launchLocationId,
        rememberedDirectoryPath = launchDirectoryPath
    )
}

private fun isEligibleForRecentFolderUpdate(model: BrowserLocationModel): Boolean {
    if (model !is BrowserLocationModel.ArchiveLogical) return true
    val parsed = parseArchiveLogicalPath(model.logicalPath) ?: return true
    val archiveLocation = parsed.first
    val inArchivePath = parsed.second
    val isRemoteArchive = parseSmbSourceSpecFromInput(archiveLocation) != null ||
        parseHttpSourceSpecFromInput(archiveLocation) != null
    if (!isRemoteArchive) return true
    // Remote archive subfolder recents are currently unstable across reopen/auth paths.
    // Keep track recents, but skip folder-recents for nested in-archive directories.
    return inArchivePath.isNullOrBlank()
}

private fun resolveSmbLaunchRequestUriForRestore(
    spec: SmbSourceSpec,
    sourceNodeId: Long?,
    networkNodes: List<NetworkNode>
): String {
    val resolvedNodeSpec = sourceNodeId
        ?.let { id -> networkNodes.firstOrNull { it.id == id } }
        ?.let(::resolveNetworkNodeSmbSpec)
    return if (resolvedNodeSpec != null) {
        buildSmbRequestUri(
            spec.copy(
                username = resolvedNodeSpec.username ?: spec.username,
                password = resolvedNodeSpec.password
            )
        )
    } else {
        buildSmbRequestUri(spec)
    }
}

private fun resolveHttpLaunchRequestUriForRestore(
    spec: HttpSourceSpec,
    sourceNodeId: Long?,
    networkNodes: List<NetworkNode>
): String {
    val nodeHttpSpec = sourceNodeId
        ?.let { id -> networkNodes.firstOrNull { it.id == id } }
        ?.let(::resolveNetworkNodeSourceId)
        ?.let(::parseHttpSourceSpecFromInput)
    return if (nodeHttpSpec != null) {
        buildHttpRequestUri(
            spec.copy(
                username = nodeHttpSpec.username ?: spec.username,
                password = nodeHttpSpec.password ?: spec.password
            )
        )
    } else {
        buildHttpRequestUri(spec)
    }
}
