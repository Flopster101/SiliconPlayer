package com.flopster101.siliconplayer

import android.net.Uri
import android.util.Base64
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Locale

internal data class HttpSourceSpec(
    val scheme: String,
    val host: String,
    val port: Int?,
    val path: String,
    val query: String?,
    val username: String?,
    val password: String?
)

internal fun parseHttpSourceSpecFromInput(input: String): HttpSourceSpec? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null
    val parsed = Uri.parse(trimmed)
    val scheme = parsed.scheme?.lowercase(Locale.ROOT)
    if (scheme != "http" && scheme != "https") return null
    val host = parsed.host?.trim().takeUnless { it.isNullOrBlank() } ?: return null
    val (username, password) = parseHttpUserInfo(parsed.encodedUserInfo)
    return HttpSourceSpec(
        scheme = scheme,
        host = host.removePrefix("[").removeSuffix("]"),
        port = parsed.port.takeIf { it > 0 },
        path = normalizeHttpPath(parsed.path),
        query = parsed.query?.trim().takeUnless { it.isNullOrBlank() },
        username = username,
        password = password
    )
}

internal fun buildHttpSourceId(spec: HttpSourceSpec): String {
    return buildHttpUri(spec = spec, includePassword = false)
}

internal fun buildHttpRequestUri(spec: HttpSourceSpec): String {
    return buildHttpUri(spec = spec, includePassword = true)
}

internal fun buildHttpDisplayUri(spec: HttpSourceSpec): String {
    return buildHttpUri(
        spec = spec.copy(
            username = null,
            password = null
        ),
        includePassword = false
    )
}

internal fun httpBasicAuthorizationHeader(
    username: String?,
    password: String?
): String? {
    val normalizedUsername = username?.trim().orEmpty()
    val normalizedPassword = password?.trim().orEmpty()
    if (normalizedUsername.isBlank() && normalizedPassword.isBlank()) return null
    val token = "$normalizedUsername:$normalizedPassword"
    val encoded = Base64.encodeToString(
        token.toByteArray(StandardCharsets.UTF_8),
        Base64.NO_WRAP
    )
    return "Basic $encoded"
}

internal fun sanitizeHttpUrlForLog(rawUrl: String): String {
    val parsed = parseHttpSourceSpecFromInput(rawUrl) ?: return rawUrl
    return buildHttpDisplayUri(parsed)
}

internal fun isLikelyHttpDirectorySource(sourceId: String): Boolean {
    val spec = parseHttpSourceSpecFromInput(sourceId) ?: return false
    val normalizedPath = normalizeHttpPath(spec.path)
    if (normalizedPath.endsWith("/")) return true
    val leaf = normalizedPath.substringAfterLast('/').trim()
    if (leaf.isBlank()) return true
    return inferredPrimaryExtensionForName(leaf) == null
}

internal fun normalizeHttpPath(path: String?): String {
    val normalized = path?.trim().orEmpty()
    if (normalized.isBlank()) return "/"
    val withLeadingSlash = if (normalized.startsWith("/")) normalized else "/$normalized"
    return withLeadingSlash.replace('\\', '/')
}

internal fun normalizeHttpDirectoryPath(path: String?): String {
    val normalized = normalizeHttpPath(path)
    if (normalized == "/") return "/"
    return if (normalized.endsWith("/")) normalized else "$normalized/"
}

private fun buildHttpUri(spec: HttpSourceSpec, includePassword: Boolean): String {
    val scheme = spec.scheme.lowercase(Locale.ROOT)
    val host = spec.host.trim().removePrefix("[").removeSuffix("]")
    val userInfo = buildString {
        val username = spec.username?.trim().takeUnless { it.isNullOrBlank() }
        val password = spec.password?.trim().takeUnless { it.isNullOrBlank() }
        if (username != null) {
            append(username)
            if (includePassword && password != null) {
                append(':')
                append(password)
            }
        }
    }.ifBlank { null }
    val uri = URI(
        scheme,
        userInfo,
        host,
        spec.port ?: -1,
        normalizeHttpPath(spec.path),
        spec.query,
        null
    )
    return uri.toASCIIString()
}

private fun parseHttpUserInfo(encodedUserInfo: String?): Pair<String?, String?> {
    if (encodedUserInfo.isNullOrBlank()) return null to null
    val decoded = Uri.decode(encodedUserInfo)
    val split = decoded.split(':', limit = 2)
    val username = split.getOrNull(0)?.trim().takeUnless { it.isNullOrBlank() }
    val password = split.getOrNull(1)?.trim().takeUnless { it.isNullOrBlank() }
    return username to password
}
