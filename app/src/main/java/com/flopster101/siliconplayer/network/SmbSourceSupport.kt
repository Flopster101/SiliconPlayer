package com.flopster101.siliconplayer

import android.net.Uri
import java.util.Locale

internal data class SmbSourceSpec(
    val host: String,
    val share: String,
    val path: String? = null,
    val username: String? = null,
    val password: String? = null
)

internal fun buildSmbSourceSpec(
    host: String,
    share: String,
    path: String?,
    username: String?,
    password: String?
): SmbSourceSpec? {
    val normalizedHost = host.trim().lowercase(Locale.ROOT).ifBlank { return null }
    val normalizedShare = share.trim().trim('/').trim('\\')
    val normalizedPath = if (normalizedShare.isBlank()) {
        null
    } else {
        normalizeSmbPathForShare(path)
    }
    val normalizedUsername = username?.trim().takeUnless { it.isNullOrBlank() }
    val normalizedPassword = password?.trim().takeUnless { it.isNullOrBlank() }
    return SmbSourceSpec(
        host = normalizedHost,
        share = normalizedShare,
        path = normalizedPath,
        username = normalizedUsername,
        password = normalizedPassword
    )
}

internal fun parseSmbSourceSpecFromInput(input: String): SmbSourceSpec? {
    val trimmed = input.trim()
    if (!trimmed.startsWith("smb://", ignoreCase = true)) return null
    val withoutScheme = trimmed.substringAfter("://", missingDelimiterValue = "")
    if (withoutScheme.isBlank()) return null

    val authorityAndPath = withoutScheme
        .substringBefore('#')
        .substringBefore('?')
    val authorityPart = authorityAndPath.substringBefore('/').trim()
    val rawPathPart = authorityAndPath.substringAfter('/', missingDelimiterValue = "").trim()
    if (authorityPart.isBlank()) return null

    val userInfoPart = authorityPart.substringBefore('@', missingDelimiterValue = "")
        .takeIf { authorityPart.contains('@') }
    val hostPartRaw = authorityPart.substringAfter('@', authorityPart).trim()
    val host = hostPartRaw
        .removePrefix("[")
        .removeSuffix("]")
        .trim()
    if (host.isBlank()) return null

    val segments = rawPathPart
        .replace('\\', '/')
        .split('/')
        .map { segment -> Uri.decode(segment).trim() }
        .filter { it.isNotBlank() }
    val share = segments.firstOrNull().orEmpty()
    val relativePath = if (share.isBlank()) null else segments.drop(1).joinToString("/").ifBlank { null }
    val (username, password) = parseSmbUserInfo(userInfoPart)
    return buildSmbSourceSpec(
        host = host,
        share = share,
        path = relativePath,
        username = username,
        password = password
    )
}

internal fun buildSmbSourceId(spec: SmbSourceSpec): String {
    return buildSmbUri(spec = spec, includePassword = false)
}

internal fun buildSmbRequestUri(spec: SmbSourceSpec): String {
    return buildSmbUri(spec = spec, includePassword = true)
}

internal fun buildSmbDisplayUri(spec: SmbSourceSpec): String {
    val path = normalizeSmbPathForShare(spec.path)
    return buildString {
        append("smb://")
        append(spec.host.trim())
        val share = spec.share.trim()
        if (share.isNotBlank()) {
            append("/")
            append(share)
        }
        if (share.isNotBlank() && !path.isNullOrBlank()) {
            append("/")
            append(path)
        }
    }
}

private fun buildSmbUri(spec: SmbSourceSpec, includePassword: Boolean): String {
    val authorityHost = buildSmbAuthorityHost(spec.host)
    val normalizedUsername = spec.username?.trim().takeUnless { it.isNullOrBlank() }
    val normalizedPassword = spec.password?.trim().takeUnless { it.isNullOrBlank() }
    val encodedUserInfo = if (normalizedUsername == null) {
        null
    } else {
        buildString {
            append(Uri.encode(normalizedUsername))
            if (includePassword && normalizedPassword != null) {
                append(":")
                append(Uri.encode(normalizedPassword))
            }
        }
    }
    val authority = if (encodedUserInfo == null) {
        authorityHost
    } else {
        "$encodedUserInfo@$authorityHost"
    }
    val builder = Uri.Builder()
        .scheme("smb")
        .encodedAuthority(authority)
    val share = spec.share.trim()
    if (share.isNotBlank()) {
        builder.appendPath(share)
        normalizeSmbPathForShare(spec.path)
            ?.split('/')
            ?.filter { it.isNotBlank() }
            ?.forEach { segment ->
                builder.appendPath(segment)
            }
    }
    return builder.build().toString()
}

private fun buildSmbAuthorityHost(rawHost: String): String {
    val host = rawHost.trim()
    if (host.contains(':') && !host.startsWith("[") && !host.endsWith("]")) {
        return "[$host]"
    }
    return host
}

private fun parseSmbUserInfo(encodedUserInfo: String?): Pair<String?, String?> {
    if (encodedUserInfo.isNullOrBlank()) return Pair(null, null)
    val userPart = encodedUserInfo.substringBefore(':')
    val passwordPart = encodedUserInfo
        .substringAfter(':', missingDelimiterValue = "")
        .takeIf { it.isNotEmpty() }
    val username = Uri.decode(userPart).trim().ifBlank { null }
    val password = passwordPart?.let { Uri.decode(it).trim().ifBlank { null } }
    return Pair(username, password)
}
