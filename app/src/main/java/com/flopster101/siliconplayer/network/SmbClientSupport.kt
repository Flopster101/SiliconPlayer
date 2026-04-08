package com.flopster101.siliconplayer

import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.session.Session
import java.io.IOException
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap

internal enum class SmbAuthenticationFailureReason {
    WrongPassword,
    WrongCredentials,
    AccessDenied,
    AccountRestricted,
    AuthenticationRequired,
    Unknown
}

private val sharedAppSmbClient by lazy { SMBClient() }
internal data class OpenedSmbSession(
    val connection: Connection,
    val session: Session
)
private data class SmbSessionCacheKey(
    val host: String,
    val username: String,
    val password: String
)
private val sharedAppSmbSessions = ConcurrentHashMap<SmbSessionCacheKey, OpenedSmbSession>()
private val sharedAppSmbSessionLocks = ConcurrentHashMap<SmbSessionCacheKey, Any>()

private fun smbSessionCacheKey(spec: SmbSourceSpec): SmbSessionCacheKey {
    return SmbSessionCacheKey(
        host = spec.host.trim(),
        username = spec.username?.trim().orEmpty(),
        password = spec.password?.trim().orEmpty()
    )
}

private fun createOpenedSmbSession(spec: SmbSourceSpec): OpenedSmbSession {
    val connection = sharedAppSmbClient.connect(spec.host)
    return try {
        val session = authenticateSmbSession(connection, spec)
        OpenedSmbSession(
            connection = connection,
            session = session
        )
    } catch (t: Throwable) {
        runCatching { connection.close() }
        throw t
    }
}

internal fun openDedicatedSmbSession(spec: SmbSourceSpec): OpenedSmbSession {
    val credentialedSpec = NetworkCredentialStore.applyTo(spec)
    var shouldRetry = true
    while (true) {
        try {
            return createOpenedSmbSession(credentialedSpec)
        } catch (t: Throwable) {
            if (!shouldRetry || !isRetryableSmbTransportFailure(t)) {
                throw t
            }
            shouldRetry = false
        }
    }
}

private fun closeOpenedSmbSession(opened: OpenedSmbSession) {
    runCatching { opened.session.close() }
    runCatching { opened.connection.close() }
}

internal fun isRetryableSmbTransportFailure(throwable: Throwable?): Boolean {
    var current = throwable
    while (current != null) {
        val message = current.message.orEmpty()
        if (current is IOException || current is SocketException) {
            return true
        }
        if (current::class.java.name.contains("TransportException", ignoreCase = true)) {
            return true
        }
        if (
            message.contains("STATUS_USER_SESSION_DELETED", ignoreCase = true) ||
            message.contains("already been closed", ignoreCase = true) ||
            message.contains("broken pipe", ignoreCase = true) ||
            message.contains("socket closed", ignoreCase = true) ||
            message.contains("connection reset", ignoreCase = true) ||
            message.contains("connection abort", ignoreCase = true) ||
            message.contains("transportexception", ignoreCase = true)
        ) {
            return true
        }
        current = current.cause
    }
    return false
}

internal fun invalidateAppSmbSession(spec: SmbSourceSpec) {
    val key = smbSessionCacheKey(spec)
    val cached = sharedAppSmbSessions.remove(key) ?: return
    closeOpenedSmbSession(cached)
}

internal fun getOrCreateAppSmbSession(spec: SmbSourceSpec): OpenedSmbSession {
    val key = smbSessionCacheKey(spec)
    sharedAppSmbSessions[key]?.let { return it }
    val lock = sharedAppSmbSessionLocks.getOrPut(key) { Any() }
    synchronized(lock) {
        sharedAppSmbSessions[key]?.let { return it }
        return createOpenedSmbSession(spec).also { openedSession ->
            sharedAppSmbSessions[key] = openedSession
        }
    }
}

internal inline fun <T> withAppSmbSession(
    spec: SmbSourceSpec,
    block: (Session) -> T
): T {
    val credentialedSpec = NetworkCredentialStore.applyTo(spec)
    var shouldRetryAfterInvalidation = true
    while (true) {
        val openedSession = getOrCreateAppSmbSession(credentialedSpec)
        try {
            return block(openedSession.session)
        } catch (t: Throwable) {
            val retryable = isRetryableSmbTransportFailure(t)
            if (!retryable || !shouldRetryAfterInvalidation) {
                throw t
            }
            invalidateAppSmbSession(credentialedSpec)
            shouldRetryAfterInvalidation = false
        }
    }
}

internal fun authenticateSmbSession(connection: Connection, spec: SmbSourceSpec): Session {
    val username = spec.username?.trim().orEmpty()
    val hasExplicitCredentials = username.isNotBlank()
    val attempts = buildList {
        val passwordChars = spec.password?.toCharArray() ?: CharArray(0)
        if (hasExplicitCredentials) {
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
    if (!hasExplicitCredentials) {
        throw IllegalStateException("Unable to authenticate SMB session")
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
        if (message.contains("Unable to authenticate SMB session", ignoreCase = true)) {
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
