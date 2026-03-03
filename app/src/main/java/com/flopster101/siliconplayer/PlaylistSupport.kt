package com.flopster101.siliconplayer

import android.net.Uri
import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.Charset
import java.util.Locale
import java.util.UUID

private val SUPPORTED_PLAYLIST_EXTENSIONS = setOf("m3u", "m3u8")

internal enum class PlaylistStoredFormat(
    val storageValue: String,
    val label: String
) {
    Internal("internal", "Internal"),
    M3u("m3u", "M3U"),
    M3u8("m3u8", "M3U8");

    companion object {
        fun fromStorage(value: String?): PlaylistStoredFormat {
            return values().firstOrNull { it.storageValue == value } ?: Internal
        }
    }
}

internal data class PlaylistTrackEntry(
    val id: String = UUID.randomUUID().toString(),
    val source: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val artworkThumbnailCacheKey: String? = null,
    val subtuneIndex: Int? = null,
    val durationSecondsOverride: Double? = null
)

internal data class StoredPlaylist(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val format: PlaylistStoredFormat,
    val sourceIdHint: String? = null,
    val entries: List<PlaylistTrackEntry>,
    val updatedAtMs: Long = System.currentTimeMillis()
)

internal data class PlaylistLibraryState(
    val favorites: List<PlaylistTrackEntry>,
    val playlists: List<StoredPlaylist>
)

internal data class ParsedPlaylistDocument(
    val title: String,
    val format: PlaylistStoredFormat,
    val sourceIdHint: String? = null,
    val entries: List<PlaylistTrackEntry>
)

internal fun emptyPlaylistLibraryState(): PlaylistLibraryState {
    return PlaylistLibraryState(
        favorites = emptyList(),
        playlists = emptyList()
    )
}

internal fun isSupportedPlaylistFileName(name: String): Boolean {
    return inferredPrimaryExtensionForName(name)
        ?.lowercase(Locale.ROOT) in SUPPORTED_PLAYLIST_EXTENSIONS
}

internal fun isSupportedPlaylistFile(file: File?): Boolean {
    return file != null && file.isFile && isSupportedPlaylistFileName(file.name)
}

internal fun parsePlaylistDocument(
    file: File,
    sourceIdHint: String? = null
): ParsedPlaylistDocument? {
    if (!file.exists() || !file.isFile) return null
    return when (inferredPrimaryExtensionForName(file.name)?.lowercase(Locale.ROOT)) {
        "m3u" -> parseM3uPlaylist(file, sourceIdHint, PlaylistStoredFormat.M3u)
        "m3u8" -> parseM3uPlaylist(file, sourceIdHint, PlaylistStoredFormat.M3u8)
        else -> null
    }
}

internal fun resolvePlaylistEntryLocalFile(source: String): File? {
    if (parseHttpSourceSpecFromInput(source) != null || parseSmbSourceSpecFromInput(source) != null) {
        return null
    }
    val localPath = if (source.startsWith("file://", ignoreCase = true)) {
        Uri.parse(source).path
    } else {
        source
    } ?: return null
    return File(localPath)
}

internal fun playlistEntryMatchesPlayback(
    entry: PlaylistTrackEntry,
    activeSourceId: String?,
    currentSubtuneIndex: Int
): Boolean {
    if (!samePath(entry.source, activeSourceId)) return false
    val entrySubtune = entry.subtuneIndex ?: return true
    return entrySubtune == currentSubtuneIndex
}

internal fun buildInternalPlaylistCopy(
    title: String,
    entries: List<PlaylistTrackEntry>
): StoredPlaylist {
    val normalizedTitle = title.trim().ifBlank { "Playlist" }
    return StoredPlaylist(
        title = normalizedTitle,
        format = PlaylistStoredFormat.Internal,
        entries = entries
    )
}

internal fun buildImportedPlaylist(
    document: ParsedPlaylistDocument
): StoredPlaylist {
    return StoredPlaylist(
        title = document.title,
        format = document.format,
        sourceIdHint = document.sourceIdHint,
        entries = document.entries
    )
}

internal fun playlistTrackSubtitle(entry: PlaylistTrackEntry): String {
    val artist = entry.artist?.trim().orEmpty()
    val sourceTail = when {
        parseHttpSourceSpecFromInput(entry.source) != null ||
            parseSmbSourceSpecFromInput(entry.source) != null -> entry.source
        else -> resolvePlaylistEntryLocalFile(entry.source)?.name ?: entry.source
    }
    return buildString {
        if (artist.isNotBlank()) {
            append(artist)
        }
        if (entry.subtuneIndex != null) {
            if (isNotEmpty()) append(" • ")
            append("Subtune ")
            append(entry.subtuneIndex + 1)
        }
        if (sourceTail.isNotBlank()) {
            if (isNotEmpty()) append(" • ")
            append(sourceTail)
        }
    }
}

private data class PendingM3uMetadata(
    val title: String,
    val artist: String?,
    val durationSecondsOverride: Double?
)

private data class EmbeddedM3uEntryMetadata(
    val rawTarget: String,
    val subtuneIndex: Int?,
    val title: String?,
    val artist: String?,
    val durationSecondsOverride: Double?
)

private data class PlaylistTargetResolution(
    val source: String,
    val subtuneIndex: Int?
)

private fun parseM3uPlaylist(
    file: File,
    sourceIdHint: String?,
    format: PlaylistStoredFormat
): ParsedPlaylistDocument? {
    val lines = readPlaylistLines(file)
    if (lines.isEmpty()) return null
    var pendingMetadata: PendingM3uMetadata? = null
    val entries = buildList {
        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) return@forEachIndexed
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                pendingMetadata = parseExtInfMetadata(line)
                return@forEachIndexed
            }
            if (line.startsWith("#")) return@forEachIndexed
            val embeddedMetadata = parseEmbeddedM3uEntryMetadata(line)
            val playlistTarget = embeddedMetadata?.rawTarget ?: line
            val resolved = resolvePlaylistTarget(
                rawEntry = playlistTarget,
                playlistFile = file,
                sourceIdHint = sourceIdHint
            ) ?: return@forEachIndexed
            val metadata = pendingMetadata
            pendingMetadata = null
            val fallbackTitle = derivePlaylistEntryTitle(
                source = resolved.source,
                lineIndex = index
            )
            val resolvedSubtuneIndex = embeddedMetadata?.subtuneIndex ?: resolved.subtuneIndex
            add(
                PlaylistTrackEntry(
                    source = resolved.source,
                    title = metadata?.title?.takeIf { it.isNotBlank() }
                        ?: embeddedMetadata?.title?.takeIf { it.isNotBlank() }
                        ?: fallbackTitle,
                    artist = metadata?.artist ?: embeddedMetadata?.artist,
                    subtuneIndex = resolvedSubtuneIndex,
                    durationSecondsOverride = metadata?.durationSecondsOverride ?: embeddedMetadata?.durationSecondsOverride
                )
            )
        }
    }
    if (entries.isEmpty()) return null
    return ParsedPlaylistDocument(
        title = file.nameWithoutExtension.ifBlank { file.name },
        format = format,
        sourceIdHint = sourceIdHint,
        entries = entries
    )
}

private fun readPlaylistLines(file: File): List<String> {
    val bytes = runCatching { file.readBytes() }.getOrNull() ?: return emptyList()
    val text = decodePlaylistText(bytes)
    return text
        .replace("\uFEFF", "")
        .lineSequence()
        .toList()
}

private fun decodePlaylistText(bytes: ByteArray): String {
    return decodePlaylistTextWithCharset(bytes, StandardCharsets.UTF_8)
        ?: decodePlaylistTextWithCharset(bytes, Charset.forName("windows-1252"))
        ?: String(bytes, StandardCharsets.ISO_8859_1)
}

private fun decodePlaylistTextWithCharset(bytes: ByteArray, charset: Charset): String? {
    val decoder = charset
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return try {
        decoder.decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        null
    }
}

private fun parseExtInfMetadata(line: String): PendingM3uMetadata? {
    val extInfPayload = line.substringAfter(':', missingDelimiterValue = "")
    val durationToken = extInfPayload.substringBefore(',', missingDelimiterValue = "").trim()
    val payload = extInfPayload
        .substringAfter(',', missingDelimiterValue = "")
        .trim()
    val durationSecondsOverride = parsePlaylistDurationSeconds(durationToken)
    if (payload.isBlank() && durationSecondsOverride == null) return null
    val artist = payload.substringBefore(" - ", missingDelimiterValue = "")
        .trim()
        .takeIf { it.isNotBlank() }
    return PendingM3uMetadata(
        title = payload,
        artist = artist,
        durationSecondsOverride = durationSecondsOverride
    )
}

private fun parseEmbeddedM3uEntryMetadata(rawEntry: String): EmbeddedM3uEntryMetadata? {
    val separatorIndex = rawEntry.indexOf("::")
    if (separatorIndex <= 0 || separatorIndex >= rawEntry.lastIndex) return null
    val rawTarget = rawEntry.substring(0, separatorIndex).trim()
    if (rawTarget.isBlank()) return null
    val payload = rawEntry.substring(separatorIndex + 2).trim()
    if (payload.isBlank()) return null
    val fields = splitEscapedPlaylistFields(payload)
    if (fields.size < 2) return null
    val numericField = fields.getOrNull(1)?.trim()?.toIntOrNull() ?: return null
    val subtuneIndex = numericField.coerceAtLeast(0)
    return EmbeddedM3uEntryMetadata(
        rawTarget = rawTarget,
        subtuneIndex = subtuneIndex,
        title = fields.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() },
        artist = null,
        durationSecondsOverride = parsePlaylistDurationSeconds(fields.getOrNull(3))
    )
}

private fun splitEscapedPlaylistFields(payload: String): List<String> {
    val fields = mutableListOf<String>()
    val current = StringBuilder()
    var escaping = false
    payload.forEach { char ->
        when {
            escaping -> {
                current.append(char)
                escaping = false
            }
            char == '\\' -> escaping = true
            char == ',' -> {
                fields += current.toString()
                current.setLength(0)
            }
            else -> current.append(char)
        }
    }
    if (escaping) {
        current.append('\\')
    }
    fields += current.toString()
    return fields
}

private fun resolvePlaylistTarget(
    rawEntry: String,
    playlistFile: File,
    sourceIdHint: String?
): PlaylistTargetResolution? {
    val stripped = stripRecognizedPlaylistFragment(rawEntry)
    val normalized = stripped.first.trim()
    if (normalized.isBlank()) return null
    val fragmentSubtune = stripped.second
    parseHttpSourceSpecFromInput(normalized)?.let { spec ->
        return PlaylistTargetResolution(
            source = buildHttpSourceId(spec),
            subtuneIndex = fragmentSubtune
        )
    }
    parseSmbSourceSpecFromInput(normalized)?.let { spec ->
        return PlaylistTargetResolution(
            source = buildSmbSourceId(spec),
            subtuneIndex = fragmentSubtune
        )
    }
    if (normalized.startsWith("file://", ignoreCase = true)) {
        val fileUri = Uri.parse(normalized)
        val localPath = fileUri.path?.takeIf { it.isNotBlank() } ?: return null
        val resolved = File(localPath)
        if (!resolved.exists() || !resolved.isFile) return null
        return PlaylistTargetResolution(
            source = resolved.absolutePath,
            subtuneIndex = fragmentSubtune
        )
    }
    val remoteRelative = resolveRelativeRemotePlaylistSource(normalized, sourceIdHint)
    if (remoteRelative != null) {
        return PlaylistTargetResolution(
            source = remoteRelative,
            subtuneIndex = fragmentSubtune
        )
    }
    val candidate = if (File(normalized).isAbsolute || WINDOWS_ABSOLUTE_PATH_REGEX.matches(normalized)) {
        File(normalized)
    } else {
        File(playlistFile.parentFile ?: playlistFile.absoluteFile.parentFile ?: File("/"), normalized)
    }
    val normalizedFile = runCatching { candidate.canonicalFile }.getOrElse { candidate.absoluteFile.normalize() }
    if (!normalizedFile.exists() || !normalizedFile.isFile) return null
    return PlaylistTargetResolution(
        source = normalizedFile.absolutePath,
        subtuneIndex = fragmentSubtune
    )
}

private fun stripRecognizedPlaylistFragment(rawEntry: String): Pair<String, Int?> {
    val fragmentIndex = rawEntry.lastIndexOf('#')
    if (fragmentIndex < 0 || fragmentIndex == rawEntry.lastIndex) {
        return rawEntry to null
    }
    val fragment = rawEntry.substring(fragmentIndex + 1).trim()
    val subtuneIndex = parsePlaylistSubtuneFragment(fragment) ?: return rawEntry to null
    return rawEntry.substring(0, fragmentIndex) to subtuneIndex
}

private fun parsePlaylistSubtuneFragment(fragment: String): Int? {
    val normalized = fragment.trim()
    if (normalized.isBlank()) return null
    if (normalized.all { it.isDigit() }) {
        return normalizeHumanPlaylistSubtuneIndex(normalized.toIntOrNull())
    }
    val key = normalized.substringBefore('=').trim().lowercase(Locale.ROOT)
    val value = normalized.substringAfter('=', missingDelimiterValue = "").trim()
    if (value.isBlank()) return null
    return when (key) {
        "subtune", "tune", "track" -> normalizeHumanPlaylistSubtuneIndex(value.toIntOrNull())
        "subtune_index", "subtuneindex" -> value.toIntOrNull()?.coerceAtLeast(0)
        else -> null
    }
}

private fun normalizeHumanPlaylistSubtuneIndex(value: Int?): Int? {
    val parsed = value ?: return null
    return if (parsed <= 0) 0 else parsed - 1
}

private fun parsePlaylistDurationSeconds(rawValue: String?): Double? {
    val normalized = rawValue?.trim().orEmpty()
    if (normalized.isBlank()) return null
    if (normalized.contains(':')) {
        val parts = normalized
            .split(':')
            .map { it.trim() }
        if (parts.isEmpty() || parts.any { it.isEmpty() || it.any { ch -> !ch.isDigit() } }) {
            return null
        }
        var totalSeconds = 0L
        parts.forEach { part ->
            totalSeconds = (totalSeconds * 60L) + part.toLong()
        }
        return totalSeconds.toDouble().takeIf { it > 0.0 }
    }
    val numeric = normalized.toDoubleOrNull() ?: return null
    return numeric.takeIf { it > 0.0 }
}

private fun resolveRelativeRemotePlaylistSource(
    relativePath: String,
    sourceIdHint: String?
): String? {
    val sourceHint = sourceIdHint?.trim().takeUnless { it.isNullOrBlank() } ?: return null
    parseHttpSourceSpecFromInput(sourceHint)?.let { spec ->
        val baseUri = URI(buildHttpRequestUri(spec))
        val resolvedUri = runCatching {
            baseUri.resolve(relativePath)
        }.getOrNull() ?: return null
        return resolvedUri.toASCIIString()
    }
    parseSmbSourceSpecFromInput(sourceHint)?.let { spec ->
        val basePath = buildString {
            val currentPath = normalizeSmbPathForShare(spec.path)
            if (currentPath.isNullOrBlank()) {
                append("")
            } else {
                append(currentPath.substringBeforeLast('/', missingDelimiterValue = ""))
            }
        }
        val combinedPath = normalizeSmbPathForShare(
            listOf(basePath, relativePath.replace('\\', '/'))
                .filter { it.isNotBlank() }
                .joinToString("/")
        )
        val resolvedSpec = spec.copy(path = combinedPath)
        return buildSmbSourceId(resolvedSpec)
    }
    return null
}

private fun derivePlaylistEntryTitle(source: String, lineIndex: Int): String {
    return when {
        parseHttpSourceSpecFromInput(source) != null ||
            parseSmbSourceSpecFromInput(source) != null -> source.substringAfterLast('/').ifBlank {
            "Entry ${lineIndex + 1}"
        }
        else -> resolvePlaylistEntryLocalFile(source)?.nameWithoutExtension?.ifBlank {
            resolvePlaylistEntryLocalFile(source)?.name
        } ?: "Entry ${lineIndex + 1}"
    }
}

private val WINDOWS_ABSOLUTE_PATH_REGEX = Regex("^[A-Za-z]:[\\\\/].*")
