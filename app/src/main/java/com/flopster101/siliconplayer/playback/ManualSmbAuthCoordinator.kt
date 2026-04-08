package com.flopster101.siliconplayer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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
        val resolved = NetworkCredentialStore.applyTo(spec)
        val username = resolved.username?.trim().takeUnless { it.isNullOrBlank() }
        val password = resolved.password?.trim().takeUnless { it.isNullOrBlank() }
        if (username == null && password == null) return null
        return username to password
    }

    fun credentialsFor(spec: HttpSourceSpec): Pair<String?, String?>? {
        val resolved = NetworkCredentialStore.applyTo(spec)
        val username = resolved.username?.trim().takeUnless { it.isNullOrBlank() }
        val password = resolved.password?.trim().takeUnless { it.isNullOrBlank() }
        if (username == null && password == null) return null
        return username to password
    }

    fun rememberCredentials(spec: SmbSourceSpec, username: String?, password: String?) {
        NetworkCredentialStore.remember(spec, username, password)
    }

    fun rememberCredentials(spec: HttpSourceSpec, username: String?, password: String?) {
        NetworkCredentialStore.remember(spec, username, password)
    }

    fun clearSessionCredentials() {
        NetworkCredentialStore.clearAll()
    }
}
