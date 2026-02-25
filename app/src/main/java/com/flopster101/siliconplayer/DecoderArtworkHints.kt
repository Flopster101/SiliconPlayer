package com.flopster101.siliconplayer

import java.util.LinkedHashMap
import java.util.Locale

enum class DecoderArtworkHint {
    TrackedFile,
    GameFile
}

internal fun decoderArtworkHintForName(decoderName: String?): DecoderArtworkHint? {
    val normalizedPlugin = pluginNameForCoreName(decoderName)
    return when (normalizedPlugin ?: decoderName?.trim()) {
        in DecoderNames.trackedFileDecoders -> DecoderArtworkHint.TrackedFile
        in DecoderNames.gameFileDecoders -> DecoderArtworkHint.GameFile
        else -> null
    }
}

private data class DecoderArtworkRule(
    val priority: Int,
    val hint: DecoderArtworkHint,
    val extensions: Set<String>
)

internal fun buildDecoderExtensionArtworkHintMap(): Map<String, DecoderArtworkHint> {
    val rules = NativeBridge.getRegisteredDecoderNames()
        .asSequence()
        .mapNotNull { decoderName ->
            val hint = decoderArtworkHintForName(decoderName) ?: return@mapNotNull null
            if (!NativeBridge.isDecoderEnabled(decoderName)) return@mapNotNull null

            val priority = NativeBridge.getDecoderPriority(decoderName)
            val enabledExtensions = NativeBridge.getDecoderEnabledExtensions(decoderName)
            val sourceExtensions = if (enabledExtensions.isNotEmpty()) {
                enabledExtensions
            } else {
                NativeBridge.getDecoderSupportedExtensions(decoderName)
            }
            val normalizedExtensions = sourceExtensions
                .asSequence()
                .map { it.trim().lowercase(Locale.ROOT) }
                .filter { it.isNotBlank() }
                .toSet()
            if (normalizedExtensions.isEmpty()) return@mapNotNull null

            DecoderArtworkRule(
                priority = priority,
                hint = hint,
                extensions = normalizedExtensions
            )
        }
        .sortedBy { it.priority }
        .toList()

    val extensionHints = LinkedHashMap<String, DecoderArtworkHint>()
    rules.forEach { rule ->
        rule.extensions.forEach { extension ->
            if (!extensionHints.containsKey(extension)) {
                extensionHints[extension] = rule.hint
            }
        }
    }
    return extensionHints
}

internal fun resolveDecoderArtworkHintForFileName(
    fileName: String,
    extensionHints: Map<String, DecoderArtworkHint>
): DecoderArtworkHint? {
    if (extensionHints.isEmpty()) return null
    return extensionCandidatesForName(fileName).firstNotNullOfOrNull { candidate ->
        extensionHints[candidate.lowercase(Locale.ROOT)]
    }
}
