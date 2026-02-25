package com.flopster101.siliconplayer

import android.net.Uri

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
    val normalized = normalizeSourceIdentity(rawPath) ?: rawPath.trim()
    if (normalized.isBlank()) return null

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
