package com.flopster101.siliconplayer.data

import java.io.File
import java.util.concurrent.ConcurrentHashMap

private data class ArchiveBrowserMountRoute(
    val returnTargetPath: String,
    val logicalArchivePath: String?
)

private val archiveRoutesByMountRoot = ConcurrentHashMap<String, ArchiveBrowserMountRoute>()

private fun normalizeMountRootKey(path: String): String {
    val trimmed = path.trim()
    if (trimmed.isBlank()) return ""
    return runCatching { File(trimmed).canonicalPath }.getOrElse { File(trimmed).absolutePath }
}

internal fun registerArchiveBrowserReturnTarget(
    mountRootPath: String,
    returnTargetPath: String,
    logicalArchivePath: String? = null
) {
    val normalizedMountRoot = normalizeMountRootKey(mountRootPath)
    val normalizedTarget = returnTargetPath.trim()
    if (normalizedMountRoot.isBlank() || normalizedTarget.isBlank()) return
    archiveRoutesByMountRoot[normalizedMountRoot] = ArchiveBrowserMountRoute(
        returnTargetPath = normalizedTarget,
        logicalArchivePath = logicalArchivePath?.trim()?.takeIf { it.isNotBlank() }
    )
}

internal fun resolveArchiveBrowserReturnTarget(
    mountRootPath: String
): String? {
    return archiveRoutesByMountRoot[normalizeMountRootKey(mountRootPath)]?.returnTargetPath
}

internal fun resolveArchiveBrowserLogicalArchivePath(
    mountRootPath: String
): String? {
    return archiveRoutesByMountRoot[normalizeMountRootKey(mountRootPath)]?.logicalArchivePath
}
