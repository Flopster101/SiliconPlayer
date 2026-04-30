package com.flopster101.siliconplayer

private val remoteCacheHashPrefixRegex = Regex("^[0-9a-fA-F]{40}_(.+)$")

private fun isLikelyExtensionToken(token: String): Boolean {
    if (token.isBlank() || token.length > 16) return false
    return token.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '_' }
}

fun stripRemoteCacheHashPrefix(rawName: String): String {
    val normalized = rawName.trim()
    if (normalized.isEmpty()) return rawName
    return remoteCacheHashPrefixRegex.matchEntire(normalized)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
        ?: normalized
}

fun extensionCandidatesForName(name: String): List<String> {
    val baseName = name.substringAfterLast('/').substringAfterLast('\\').trim()
    if (baseName.isBlank()) return emptyList()

    val candidates = linkedSetOf<String>()
    val firstDot = baseName.indexOf('.')
    val lastDot = baseName.lastIndexOf('.')

    if (lastDot in 1 until baseName.lastIndex) {
        val suffix = baseName.substring(lastDot + 1).trim()
        if (isLikelyExtensionToken(suffix)) {
            candidates += suffix.lowercase()
        }
    }

    if (lastDot > 0) {
        val secondLastDot = baseName.lastIndexOf('.', lastDot - 1)
        if (secondLastDot in 0 until lastDot) {
            val middle = baseName.substring(secondLastDot + 1, lastDot).trim()
            if (isLikelyExtensionToken(middle)) {
                candidates += middle.lowercase()
            }
        }
    }

    if (firstDot > 0) {
        val prefix = baseName.substring(0, firstDot).trim()
        if (isLikelyExtensionToken(prefix)) {
            candidates += prefix.lowercase()
        }
    }

    return candidates.toList()
}

fun inferredPrimaryExtensionForName(name: String): String? {
    return extensionCandidatesForName(name).firstOrNull()
}

fun nameMatchesSupportedExtensions(name: String, supportedExtensions: Set<String>): Boolean {
    if (supportedExtensions.isEmpty()) return false
    return extensionCandidatesForName(name).any { candidate ->
        candidate in supportedExtensions || supportedExtensions.any { it.equals(candidate, ignoreCase = true) }
    }
}

fun inferredDisplayTitleForName(name: String): String {
    val baseName = stripRemoteCacheHashPrefix(
        name.substringAfterLast('/').substringAfterLast('\\').trim()
    )
    if (baseName.isBlank()) return name

    val firstDot = baseName.indexOf('.')
    if (firstDot in 1 until baseName.lastIndex) {
        val prefix = baseName.substring(0, firstDot).trim()
        val remainder = baseName.substring(firstDot + 1).trim()
        if (isLikelyExtensionToken(prefix) && remainder.isNotBlank() && !isLikelyExtensionToken(remainder)) {
            val remainderLastDot = remainder.lastIndexOf('.')
            return if (remainderLastDot > 0) {
                remainder.substring(0, remainderLastDot).ifBlank { remainder }
            } else {
                remainder
            }
        }
    }

    val lastDot = baseName.lastIndexOf('.')
    return if (lastDot > 0) {
        baseName.substring(0, lastDot).ifBlank { baseName }
    } else {
        baseName
    }
}
