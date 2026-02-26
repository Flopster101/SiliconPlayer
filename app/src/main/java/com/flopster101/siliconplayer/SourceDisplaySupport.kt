package com.flopster101.siliconplayer

import android.net.Uri
import com.flopster101.siliconplayer.data.parseArchiveLogicalPath
import com.flopster101.siliconplayer.data.parseArchiveSourceId

internal fun decodePercentEncodedForDisplay(raw: String?): String? {
    val trimmed = raw?.trim().takeUnless { it.isNullOrBlank() } ?: return null
    if (!trimmed.contains('%')) return trimmed
    return runCatching { Uri.decode(trimmed) }
        .getOrNull()
        ?.trim()
        .takeUnless { it.isNullOrBlank() }
        ?: trimmed
}

internal fun sourceLeafNameForDisplay(rawPath: String): String? {
    sourceLeafNameForArchiveLogicalPath(rawPath)?.let { return it }

    val normalized = normalizeSourceIdentity(rawPath) ?: rawPath.trim()
    if (normalized.isBlank()) return null

    sourceLeafNameForArchiveLogicalPath(normalized)?.let { return it }

    parseArchiveSourceId(normalized)?.let { parsedArchive ->
        return decodePercentEncodedForDisplay(parsedArchive.entryPath.substringAfterLast('/'))
    }

    parseSmbSourceSpecFromInput(normalized)?.let { smbSpec ->
        val leaf = when {
            !smbSpec.path.isNullOrBlank() -> smbSpec.path.substringAfterLast('/')
            smbSpec.share.isNotBlank() -> smbSpec.share
            else -> smbSpec.host
        }
        return decodePercentEncodedForDisplay(leaf)
    }

    parseHttpSourceSpecFromInput(normalized)?.let { httpSpec ->
        val normalizedPath = normalizeHttpPath(httpSpec.path)
        if (normalizedPath == "/") {
            return decodePercentEncodedForDisplay(httpSpec.host)
        }
        val leaf = normalizedPath
            .trimEnd('/')
            .substringAfterLast('/')
        return decodePercentEncodedForDisplay(leaf)
    }

    val parsed = Uri.parse(normalized)
    val leaf = when {
        parsed.scheme.equals("file", ignoreCase = true) -> {
            parsed.path
                ?.substringAfterLast('/')
                ?.trim()
                .orEmpty()
        }

        !parsed.scheme.isNullOrBlank() -> {
            parsed.lastPathSegment
                ?.trim()
                .orEmpty()
        }

        else -> {
            normalized
                .substringBefore('#')
                .substringBefore('?')
                .substringAfterLast('/')
                .trim()
        }
    }
    return decodePercentEncodedForDisplay(leaf)
}

internal fun folderTitleForDisplay(rawPath: String): String {
    return sourceLeafNameForDisplay(rawPath).takeUnless { it.isNullOrBlank() } ?: rawPath
}

private fun sourceLeafNameForArchiveLogicalPath(path: String): String? {
    val parsed = parseArchiveLogicalPath(path) ?: return null
    val inArchivePath = parsed.second
    if (!inArchivePath.isNullOrBlank()) {
        return decodePercentEncodedForDisplay(inArchivePath.substringAfterLast('/'))
    }
    val archiveLocation = parsed.first
    parseArchiveSourceId(archiveLocation)?.let { parsedArchive ->
        return decodePercentEncodedForDisplay(parsedArchive.entryPath.substringAfterLast('/'))
    }
    return when {
        parseSmbSourceSpecFromInput(archiveLocation) != null ->
            sourceLeafNameForDisplay(parseSmbSourceSpecFromInput(archiveLocation)?.let(::buildSmbDisplayUri).orEmpty())
        parseHttpSourceSpecFromInput(archiveLocation) != null ->
            sourceLeafNameForDisplay(parseHttpSourceSpecFromInput(archiveLocation)?.let(::buildHttpDisplayUri).orEmpty())
        else -> {
            val fromUri = Uri.parse(archiveLocation).lastPathSegment?.trim().orEmpty()
            val fromPath = archiveLocation.substringAfterLast('/').trim()
            decodePercentEncodedForDisplay(fromUri.ifBlank { fromPath })
        }
    }
}
