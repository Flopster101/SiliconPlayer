package com.flopster101.siliconplayer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.ConcurrentHashMap

internal data class ManualSmbAuthPromptState(
    val resolved: ManualSourceResolution,
    val options: ManualSourceOpenOptions,
    val expandOverride: Boolean?,
    val failureMessage: String?,
    val host: String,
    val share: String,
    val initialUsername: String?
)

internal data class ManualHttpAuthPromptState(
    val resolved: ManualSourceResolution,
    val options: ManualSourceOpenOptions,
    val expandOverride: Boolean?,
    val failureMessage: String?,
    val requestSpec: HttpSourceSpec,
    val initialUsername: String?
)

internal object ManualSmbAuthCoordinator {
    var pendingPrompt: ManualSmbAuthPromptState? by mutableStateOf(null)
        private set

    var pendingHttpPrompt: ManualHttpAuthPromptState? by mutableStateOf(null)
        private set

    private val smbSessionCredentials: MutableMap<String, Pair<String?, String?>> = ConcurrentHashMap()
    private val httpSessionCredentials: MutableMap<String, Pair<String?, String?>> = ConcurrentHashMap()

    fun requestPrompt(state: ManualSmbAuthPromptState) {
        pendingPrompt = state
    }

    fun clearPrompt() {
        pendingPrompt = null
    }

    fun requestHttpPrompt(state: ManualHttpAuthPromptState) {
        pendingHttpPrompt = state
    }

    fun clearHttpPrompt() {
        pendingHttpPrompt = null
    }

    fun credentialsFor(spec: SmbSourceSpec): Pair<String?, String?>? {
        val exactKey = smbCacheKey(spec)
        return smbSessionCredentials[exactKey]
            ?: smbSessionCredentials[smbHostCacheKey(spec)]
    }

    fun credentialsFor(spec: HttpSourceSpec): Pair<String?, String?>? {
        return httpSessionCredentials[httpCacheKey(spec)]
    }

    fun rememberCredentials(spec: SmbSourceSpec, username: String?, password: String?) {
        val credentials = Pair(username, password)
        smbSessionCredentials[smbCacheKey(spec)] = credentials
        // Also remember host-level credentials so an auth done at host/share-picker
        // level can be reused when entering a concrete share path.
        smbSessionCredentials[smbHostCacheKey(spec)] = credentials
    }

    fun rememberCredentials(spec: HttpSourceSpec, username: String?, password: String?) {
        httpSessionCredentials[httpCacheKey(spec)] = Pair(username, password)
    }

    fun clearSessionCredentials() {
        smbSessionCredentials.clear()
        httpSessionCredentials.clear()
    }

    private fun smbCacheKey(spec: SmbSourceSpec): String {
        return "${spec.host.trim().lowercase()}|${spec.share.trim().lowercase()}"
    }

    private fun smbHostCacheKey(spec: SmbSourceSpec): String {
        return "${spec.host.trim().lowercase()}|*"
    }

    private fun httpCacheKey(spec: HttpSourceSpec): String {
        return "${spec.scheme.trim().lowercase()}|${spec.host.trim().lowercase()}|${spec.port ?: -1}"
    }
}
