package com.flopster101.siliconplayer

import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.session.Session

internal fun authenticateSmbSession(connection: Connection, spec: SmbSourceSpec): Session {
    val attempts = buildList {
        val username = spec.username?.trim().orEmpty()
        val passwordChars = spec.password?.toCharArray() ?: CharArray(0)
        if (username.isNotBlank()) {
            add(AuthenticationContext(username, passwordChars, ""))
        } else {
            add(AuthenticationContext("", CharArray(0), ""))
            add(AuthenticationContext("guest", CharArray(0), ""))
        }
    }
    var lastFailure: Throwable? = null
    attempts.forEach { auth ->
        try {
            return connection.authenticate(auth)
        } catch (t: Throwable) {
            lastFailure = t
        }
    }
    throw lastFailure ?: IllegalStateException("Unable to authenticate SMB session")
}

internal fun normalizeSmbPathForShare(rawPath: String?): String? {
    if (rawPath.isNullOrBlank()) return null
    val normalized = rawPath
        .replace('\\', '/')
        .split('/')
        .map { it.trim() }
        .filter { it.isNotBlank() && it != "." }
        .joinToString("/")
    return normalized.ifBlank { null }
}

internal fun joinSmbRelativePath(base: String, child: String): String {
    val normalizedChild = child.trim().replace('\\', '/').trim('/')
    if (normalizedChild.isBlank()) return base
    if (base.isBlank()) return normalizedChild
    return "$base/$normalizedChild"
}
