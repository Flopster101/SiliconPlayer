package com.flopster101.siliconplayer

import java.io.File
import java.util.LinkedHashSet
import java.util.Locale

private fun isLikelyExtensionToken(token: String): Boolean {
    if (token.isBlank() || token.length > 16) return false
    return token.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '_' }
}

internal fun extensionCandidatesForName(name: String): List<String> {
    val baseName = name.substringAfterLast('/').substringAfterLast('\\').trim()
    if (baseName.isBlank()) return emptyList()

    val candidates = LinkedHashSet<String>()
    val firstDot = baseName.indexOf('.')
    val lastDot = baseName.lastIndexOf('.')

    if (lastDot in 1 until baseName.lastIndex) {
        val suffix = baseName.substring(lastDot + 1).trim()
        if (isLikelyExtensionToken(suffix)) {
            candidates += suffix.lowercase(Locale.ROOT)
        }
    }

    if (lastDot > 0) {
        val secondLastDot = baseName.lastIndexOf('.', lastDot - 1)
        if (secondLastDot in 0 until lastDot) {
            val middle = baseName.substring(secondLastDot + 1, lastDot).trim()
            if (isLikelyExtensionToken(middle)) {
                candidates += middle.lowercase(Locale.ROOT)
            }
        }
    }

    if (firstDot > 0) {
        val prefix = baseName.substring(0, firstDot).trim()
        if (isLikelyExtensionToken(prefix)) {
            candidates += prefix.lowercase(Locale.ROOT)
        }
    }

    return candidates.toList()
}

internal fun inferredPrimaryExtensionForName(name: String): String? {
    return extensionCandidatesForName(name).firstOrNull()
}

internal fun fileMatchesSupportedExtensions(file: File, supportedExtensions: Set<String>): Boolean {
    if (supportedExtensions.isEmpty()) return false
    return extensionCandidatesForName(file.name).any { candidate ->
        candidate in supportedExtensions || supportedExtensions.any { it.equals(candidate, ignoreCase = true) }
    }
}

internal fun inferredDisplayTitleForName(name: String): String {
    val baseName = stripRemoteCacheHashPrefix(
        name.substringAfterLast('/').substringAfterLast('\\').trim()
    )
    if (baseName.isBlank()) return name

    val firstDot = baseName.indexOf('.')
    if (firstDot in 1 until baseName.lastIndex) {
        val prefix = baseName.substring(0, firstDot).trim()
        val remainder = baseName.substring(firstDot + 1).trim()
        if (isLikelyExtensionToken(prefix) && remainder.isNotBlank() && !isLikelyExtensionToken(remainder)) {
            return remainder
        }
    }

    val lastDot = baseName.lastIndexOf('.')
    return if (lastDot > 0) {
        baseName.substring(0, lastDot).ifBlank { baseName }
    } else {
        baseName
    }
}
