package com.flopster101.siliconplayer

import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.session.Session

internal enum class SmbAuthenticationFailureReason {
    WrongPassword,
    WrongCredentials,
    AccessDenied,
    AccountRestricted,
    AuthenticationRequired,
    Unknown
}

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

internal fun isSmbAuthenticationFailure(throwable: Throwable?): Boolean {
    return resolveSmbAuthenticationFailureReason(throwable) != null
}

internal fun resolveSmbAuthenticationFailureReason(
    throwable: Throwable?
): SmbAuthenticationFailureReason? {
    if (throwable == null) return null
    var current: Throwable? = throwable
    while (current != null) {
        val message = current.message.orEmpty()
        if (message.contains("STATUS_WRONG_PASSWORD", ignoreCase = true)) {
            return SmbAuthenticationFailureReason.WrongPassword
        }
        if (message.contains("STATUS_LOGON_FAILURE", ignoreCase = true)) {
            return SmbAuthenticationFailureReason.WrongCredentials
        }
        if (message.contains("STATUS_ACCESS_DENIED", ignoreCase = true)) {
            return SmbAuthenticationFailureReason.AccessDenied
        }
        if (message.contains("STATUS_ACCOUNT_RESTRICTION", ignoreCase = true)) {
            return SmbAuthenticationFailureReason.AccountRestricted
        }
        if (message.contains("Authentication failed", ignoreCase = true)) {
            return SmbAuthenticationFailureReason.Unknown
        }
        if (current.message == "Unable to authenticate SMB session") {
            return SmbAuthenticationFailureReason.AuthenticationRequired
        }
        current = current.cause
    }
    return null
}

internal fun smbAuthenticationFailureMessage(
    reason: SmbAuthenticationFailureReason
): String {
    return when (reason) {
        SmbAuthenticationFailureReason.WrongPassword ->
            "Wrong password. Check it and try again."
        SmbAuthenticationFailureReason.WrongCredentials ->
            "Wrong username or password. Check credentials and try again."
        SmbAuthenticationFailureReason.AccessDenied ->
            "Access denied for this account on the selected SMB source."
        SmbAuthenticationFailureReason.AccountRestricted ->
            "This SMB account is restricted and cannot access this source."
        SmbAuthenticationFailureReason.AuthenticationRequired ->
            "This SMB source requires authentication."
        SmbAuthenticationFailureReason.Unknown ->
            "Authentication failed. Check credentials and try again."
    }
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
