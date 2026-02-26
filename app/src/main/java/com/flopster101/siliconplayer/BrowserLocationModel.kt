package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.data.parseArchiveLogicalPath

internal sealed interface BrowserLocationModel {
    data object Home : BrowserLocationModel

    data class Local(
        val locationId: String?,
        val directoryPath: String?
    ) : BrowserLocationModel

    data class Smb(
        val sourceNodeId: Long?,
        val spec: SmbSourceSpec
    ) : BrowserLocationModel

    data class Http(
        val sourceNodeId: Long?,
        val spec: HttpSourceSpec,
        val browserRootPath: String?
    ) : BrowserLocationModel

    data class ArchiveLogical(
        val logicalPath: String
    ) : BrowserLocationModel
}

internal fun resolveBrowserLocationModel(
    initialLocationId: String?,
    initialDirectoryPath: String?,
    initialSmbSourceNodeId: Long?,
    initialHttpSourceNodeId: Long?,
    initialHttpRootPath: String?
): BrowserLocationModel {
    val normalizedDirectoryPath = initialDirectoryPath?.trim().takeUnless { it.isNullOrBlank() }
    if (normalizedDirectoryPath == null && initialLocationId == null) {
        return BrowserLocationModel.Home
    }
    if (normalizedDirectoryPath != null && parseArchiveLogicalPath(normalizedDirectoryPath) != null) {
        return BrowserLocationModel.ArchiveLogical(
            logicalPath = normalizedDirectoryPath
        )
    }
    parseSmbSourceSpecFromInput(normalizedDirectoryPath.orEmpty())?.let { spec ->
        return BrowserLocationModel.Smb(
            sourceNodeId = initialSmbSourceNodeId,
            spec = spec
        )
    }
    parseHttpSourceSpecFromInput(normalizedDirectoryPath.orEmpty())?.let { spec ->
        return BrowserLocationModel.Http(
            sourceNodeId = initialHttpSourceNodeId,
            spec = spec,
            browserRootPath = initialHttpRootPath
        )
    }
    return BrowserLocationModel.Local(
        locationId = initialLocationId,
        directoryPath = normalizedDirectoryPath
    )
}
