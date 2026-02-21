package com.flopster101.siliconplayer

import java.io.File
import java.util.LinkedHashSet
import java.util.Locale

internal fun extensionCandidatesForName(name: String): List<String> {
    val baseName = name.substringAfterLast('/').substringAfterLast('\\').trim()
    if (baseName.isBlank()) return emptyList()

    val candidates = LinkedHashSet<String>()
    val firstDot = baseName.indexOf('.')
    val lastDot = baseName.lastIndexOf('.')

    if (lastDot in 1 until baseName.lastIndex) {
        candidates += baseName.substring(lastDot + 1).lowercase(Locale.ROOT)
    }

    if (lastDot > 0) {
        val secondLastDot = baseName.lastIndexOf('.', lastDot - 1)
        if (secondLastDot in 0 until lastDot) {
            candidates += baseName.substring(secondLastDot + 1).lowercase(Locale.ROOT)
        }
    }

    if (firstDot > 0) {
        candidates += baseName.substring(0, firstDot).lowercase(Locale.ROOT)
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
