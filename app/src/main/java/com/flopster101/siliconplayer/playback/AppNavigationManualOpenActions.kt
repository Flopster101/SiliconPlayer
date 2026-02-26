package com.flopster101.siliconplayer

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.flopster101.siliconplayer.data.resolveArchiveSourceToMountedFile
import com.flopster101.siliconplayer.data.FileRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun primeManualRemoteOpenStateAction(
    resolved: ManualSourceResolution,
    onResetPlayback: () -> Unit,
    onSelectedFileChanged: (File?) -> Unit,
    onCurrentPlaybackSourceIdChanged: (String) -> Unit,
    onCurrentPlaybackRequestUrlChanged: (String) -> Unit,
    onVisiblePlayableFilesChanged: (List<File>) -> Unit,
    onPlayerSurfaceVisibleChanged: (Boolean) -> Unit,
    onSongVolumeDbChanged: (Float) -> Unit,
    onSongGainChanged: (Float) -> Unit,
    onRemoteLoadUiStateChanged: (RemoteLoadUiState?) -> Unit
) {
    onResetPlayback()
    onSelectedFileChanged(resolved.displayFile)
    onCurrentPlaybackSourceIdChanged(resolved.sourceId)
    onCurrentPlaybackRequestUrlChanged(resolved.requestUrl)
    onVisiblePlayableFilesChanged(emptyList())
    onPlayerSurfaceVisibleChanged(true)
    onSongVolumeDbChanged(0f)
    onSongGainChanged(0f)
    onRemoteLoadUiStateChanged(
        RemoteLoadUiState(
            sourceId = resolved.sourceId,
            phase = RemoteLoadPhase.Connecting,
            indeterminate = true
        )
    )
}

internal fun applyManualRemoteOpenSuccessAction(
    result: ManualRemoteOpenSuccess,
    expandOverride: Boolean?,
    playerExpandedAtLaunch: Boolean,
    currentPlayerExpanded: Boolean,
    openPlayerOnTrackSelect: Boolean,
    activeRepeatMode: RepeatMode,
    onSelectedFileChanged: (File) -> Unit,
    onCurrentPlaybackSourceIdChanged: (String) -> Unit,
    onCurrentPlaybackRequestUrlChanged: (String) -> Unit,
    onVisiblePlayableFilesChanged: (List<File>) -> Unit,
    onPlayerSurfaceVisibleChanged: (Boolean) -> Unit,
    onSongVolumeDbChanged: (Float) -> Unit,
    onSongGainChanged: (Float) -> Unit,
    onResolvedDecoderState: (String?) -> Unit,
    applyNativeTrackSnapshot: (NativeTrackSnapshot) -> Unit,
    refreshSubtuneState: () -> Unit,
    onPositionChanged: (Double) -> Unit,
    onArtworkBitmapCleared: () -> Unit,
    refreshRepeatModeForTrack: () -> Unit,
    onAddRecentPlayedTrack: (String, String?, String?, String?) -> Unit,
    metadataTitleProvider: () -> String,
    metadataArtistProvider: () -> String,
    applyRepeatModeToNative: (RepeatMode) -> Unit,
    onStartEngine: () -> Unit,
    onIsPlayingChanged: (Boolean) -> Unit,
    scheduleRecentTrackMetadataRefresh: (String, String?) -> Unit,
    onPlayerExpandedChanged: (Boolean) -> Unit,
    syncPlaybackService: () -> Unit
) {
    onSelectedFileChanged(result.displayFile)
    onCurrentPlaybackSourceIdChanged(result.sourceId)
    onCurrentPlaybackRequestUrlChanged(result.requestUrl)
    onVisiblePlayableFilesChanged(emptyList())
    onPlayerSurfaceVisibleChanged(true)
    onSongVolumeDbChanged(0f)
    onSongGainChanged(0f)
    onResolvedDecoderState(result.decoderName)
    applyNativeTrackSnapshot(result.snapshot)
    refreshSubtuneState()
    onPositionChanged(0.0)
    onArtworkBitmapCleared()
    refreshRepeatModeForTrack()
    onAddRecentPlayedTrack(result.sourceId, null, metadataTitleProvider(), metadataArtistProvider())
    applyRepeatModeToNative(activeRepeatMode)
    onStartEngine()
    onIsPlayingChanged(true)
    scheduleRecentTrackMetadataRefresh(result.sourceId, null)
    // If the user changed expanded/collapsed state while the remote load was in progress,
    // preserve that explicit choice instead of applying the stale launch-time target.
    if (currentPlayerExpanded == playerExpandedAtLaunch) {
        onPlayerExpandedChanged(expandOverride ?: openPlayerOnTrackSelect)
    }
    syncPlaybackService()
}

internal fun failManualOpenAction(context: Context, reason: String) {
    Toast.makeText(context, "Unable to open source: $reason", Toast.LENGTH_LONG).show()
}

internal fun launchManualRemoteSelectionAction(
    context: Context,
    appScope: CoroutineScope,
    currentJob: Job?,
    resolved: ManualSourceResolution,
    options: ManualSourceOpenOptions,
    expandOverride: Boolean?,
    cacheRoot: File,
    selectedFileAbsolutePathProvider: () -> String?,
    currentPlayerExpandedProvider: () -> Boolean,
    urlCacheMaxTracks: Int,
    urlCacheMaxBytes: Long,
    onRemoteLoadUiStateChanged: (RemoteLoadUiState?) -> Unit,
    onRemoteLoadJobChanged: (Job?) -> Unit,
    onPrimeManualRemoteOpenState: (ManualSourceResolution) -> Unit,
    onApplyManualRemoteOpenSuccess: (ManualRemoteOpenSuccess, Boolean?, Boolean) -> Unit,
    onFailManualOpen: (String) -> Unit
) {
    currentJob?.cancel()
    val playerExpandedAtLaunch = currentPlayerExpandedProvider()
    val launchedJob = appScope.launch {
        try {
            onPrimeManualRemoteOpenState(resolved)
            val remoteResult = when (resolved.type) {
                ManualSourceType.RemoteUrl -> {
                    var effectiveResolved = resolved
                    val requestSpec = parseHttpSourceSpecFromInput(resolved.requestUrl)
                    if (requestSpec != null && requestSpec.username.isNullOrBlank() && requestSpec.password.isNullOrBlank()) {
                        val cachedCredentials = ManualSmbAuthCoordinator.credentialsFor(requestSpec)
                        if (cachedCredentials != null) {
                            val normalizedUsername = cachedCredentials.first?.trim().takeUnless { it.isNullOrBlank() }
                            val normalizedPassword = cachedCredentials.second?.trim().takeUnless { it.isNullOrBlank() }
                            val updatedSpec = requestSpec.copy(
                                username = normalizedUsername,
                                password = normalizedPassword
                            )
                            effectiveResolved = resolved.copy(
                                sourceId = buildHttpSourceId(updatedSpec),
                                requestUrl = buildHttpRequestUri(updatedSpec)
                            )
                        }
                    }

                    val remoteAttempt = executeManualRemoteOpen(
                        resolved = effectiveResolved,
                        forceCaching = options.forceCaching,
                        cacheRoot = cacheRoot,
                        selectedFileAbsolutePath = selectedFileAbsolutePathProvider(),
                        urlCacheMaxTracks = urlCacheMaxTracks,
                        urlCacheMaxBytes = urlCacheMaxBytes,
                        onStatus = { state -> onRemoteLoadUiStateChanged(state) },
                        downloadToCache = { sourceId, requestUrl, onStatus ->
                            downloadRemoteUrlToCache(
                                context = context,
                                url = sourceId,
                                requestUrl = requestUrl,
                                onStatus = onStatus
                            )
                        }
                    )
                    if (remoteAttempt is ManualRemoteOpenResult.Failed) {
                        val authFailureReason = resolveHttpAuthenticationFailureReasonFromErrorMessage(remoteAttempt.reason)
                        if (authFailureReason != null) {
                            val promptSpec = parseHttpSourceSpecFromInput(effectiveResolved.requestUrl)
                                ?: parseHttpSourceSpecFromInput(effectiveResolved.sourceId)
                            if (promptSpec != null) {
                                val noCredentialsWereProvided =
                                    promptSpec.username.isNullOrBlank() &&
                                        promptSpec.password.isNullOrBlank()
                                val promptReason = if (noCredentialsWereProvided) {
                                    HttpAuthenticationFailureReason.AuthenticationRequired
                                } else {
                                    authFailureReason
                                }
                                promptForManualHttpCredentials(
                                    resolved = effectiveResolved,
                                    options = options,
                                    expandOverride = expandOverride,
                                    failureMessage = httpAuthenticationFailureMessage(promptReason),
                                    requestSpec = promptSpec,
                                    initialUsername = promptSpec.username
                                )
                                ManualRemoteOpenResult.Failed(MANUAL_HTTP_AUTH_PROMPT_PENDING)
                            } else {
                                remoteAttempt
                            }
                        } else {
                            remoteAttempt
                        }
                    } else {
                        remoteAttempt
                    }
                }

                ManualSourceType.Smb -> {
                    val smbSpec = resolved.smbSpec
                    if (smbSpec == null) {
                        ManualRemoteOpenResult.Failed("Invalid SMB share configuration")
                    } else {
                        var effectiveSpec = smbSpec
                        if (effectiveSpec.username.isNullOrBlank() && effectiveSpec.password.isNullOrBlank()) {
                            val cachedCredentials = ManualSmbAuthCoordinator.credentialsFor(smbSpec)
                            if (cachedCredentials != null) {
                                effectiveSpec = effectiveSpec.copy(
                                    username = cachedCredentials.first,
                                    password = cachedCredentials.second
                                )
                            }
                        }
                        var resolvedSource = resolved.copy(
                            requestUrl = buildSmbRequestUri(effectiveSpec),
                            smbSpec = effectiveSpec
                        )
                        val firstAttemptSpec = effectiveSpec
                        var remoteAttempt = executeManualRemoteOpen(
                            resolved = resolvedSource,
                            forceCaching = true,
                            cacheRoot = cacheRoot,
                            selectedFileAbsolutePath = selectedFileAbsolutePathProvider(),
                            urlCacheMaxTracks = urlCacheMaxTracks,
                            urlCacheMaxBytes = urlCacheMaxBytes,
                            onStatus = { state -> onRemoteLoadUiStateChanged(state) },
                            downloadToCache = { sourceId, _, onStatus ->
                                downloadSmbSourceToCache(
                                    context = context,
                                    sourceId = sourceId,
                                    spec = firstAttemptSpec,
                                    onStatus = onStatus
                                )
                            }
                        )
                        if (remoteAttempt is ManualRemoteOpenResult.Failed) {
                            val authFailureReason = resolveSmbAuthenticationFailureReasonFromErrorMessage(remoteAttempt.reason)
                            if (authFailureReason != null) {
                                val noCredentialsWereProvided =
                                    firstAttemptSpec.username.isNullOrBlank() &&
                                        firstAttemptSpec.password.isNullOrBlank()
                                val promptFailureReason = if (noCredentialsWereProvided) {
                                    SmbAuthenticationFailureReason.AuthenticationRequired
                                } else {
                                    authFailureReason
                                }
                                promptForManualSmbCredentials(
                                    resolved = resolvedSource,
                                    options = options,
                                    expandOverride = expandOverride,
                                    failureMessage = smbAuthenticationFailureMessage(promptFailureReason),
                                    host = smbSpec.host,
                                    share = smbSpec.share,
                                    initialUsername = effectiveSpec.username
                                )
                                ManualRemoteOpenResult.Failed(MANUAL_SMB_AUTH_PROMPT_PENDING)
                            } else {
                                remoteAttempt
                            }
                        } else {
                            remoteAttempt
                        }
                    }
                }

                else -> {
                    ManualRemoteOpenResult.Failed("Unsupported manual source type")
                }
            }
            when (remoteResult) {
                is ManualRemoteOpenResult.Success -> {
                    onApplyManualRemoteOpenSuccess(
                        remoteResult.value,
                        expandOverride,
                        playerExpandedAtLaunch
                    )
                }

                ManualRemoteOpenResult.DownloadCancelled -> {
                    Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                is ManualRemoteOpenResult.Failed -> {
                    if (remoteResult.reason == MANUAL_SMB_AUTH_PROMPT_PENDING) {
                        return@launch
                    }
                    if (remoteResult.reason == MANUAL_HTTP_AUTH_PROMPT_PENDING) {
                        return@launch
                    }
                    Log.e(
                        URL_SOURCE_TAG,
                        "Cache download/open failed for URL: ${resolved.sourceId} reason=${remoteResult.reason}"
                    )
                    onFailManualOpen(remoteResult.reason)
                    return@launch
                }
            }
        } catch (_: CancellationException) {
            Log.d(URL_SOURCE_TAG, "Remote open cancelled for source=${resolved.sourceId}")
        } finally {
            onRemoteLoadUiStateChanged(null)
            onRemoteLoadJobChanged(null)
        }
    }
    onRemoteLoadJobChanged(launchedJob)
}

private const val MANUAL_SMB_AUTH_PROMPT_PENDING = "__manual_smb_auth_prompt_pending__"
private const val MANUAL_HTTP_AUTH_PROMPT_PENDING = "__manual_http_auth_prompt_pending__"

internal fun clearManualSmbSessionCredentialCache() {
    ManualSmbAuthCoordinator.clearSessionCredentials()
    ManualSmbAuthCoordinator.clearPrompt()
    ManualSmbAuthCoordinator.clearHttpPrompt()
}

private suspend fun promptForManualSmbCredentials(
    resolved: ManualSourceResolution,
    options: ManualSourceOpenOptions,
    expandOverride: Boolean?,
    failureMessage: String?,
    host: String,
    share: String,
    initialUsername: String?
): Unit = withContext(Dispatchers.Main.immediate) {
    ManualSmbAuthCoordinator.requestPrompt(
        ManualSmbAuthPromptState(
            resolved = resolved,
            options = options,
            expandOverride = expandOverride,
            failureMessage = failureMessage,
            host = host,
            share = share,
            initialUsername = initialUsername
        )
    )
}

private suspend fun promptForManualHttpCredentials(
    resolved: ManualSourceResolution,
    options: ManualSourceOpenOptions,
    expandOverride: Boolean?,
    failureMessage: String?,
    requestSpec: HttpSourceSpec,
    initialUsername: String?
): Unit = withContext(Dispatchers.Main.immediate) {
    ManualSmbAuthCoordinator.requestHttpPrompt(
        ManualHttpAuthPromptState(
            resolved = resolved,
            options = options,
            expandOverride = expandOverride,
            failureMessage = failureMessage,
            requestSpec = requestSpec,
            initialUsername = initialUsername
        )
    )
}

private fun resolveSmbAuthenticationFailureReasonFromErrorMessage(
    reason: String?
): SmbAuthenticationFailureReason? {
    val message = reason.orEmpty()
    if (message.isBlank()) return null
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
    if (message.contains("Unable to authenticate SMB session", ignoreCase = true)) {
        return SmbAuthenticationFailureReason.AuthenticationRequired
    }
    if (
        message.contains("authentication failed", ignoreCase = true) ||
        message.contains("logon", ignoreCase = true) ||
        message.contains("credential", ignoreCase = true)
    ) {
        return SmbAuthenticationFailureReason.Unknown
    }
    return null
}

private fun resolveHttpAuthenticationFailureReasonFromErrorMessage(
    reason: String?
): HttpAuthenticationFailureReason? {
    val message = reason.orEmpty()
    if (message.isBlank()) return null
    return when {
        message.contains("HTTP 401", ignoreCase = true) -> HttpAuthenticationFailureReason.AuthenticationRequired
        message.contains("HTTP_UNAUTHORIZED", ignoreCase = true) -> HttpAuthenticationFailureReason.AuthenticationRequired
        message.contains("HTTP 403", ignoreCase = true) -> HttpAuthenticationFailureReason.AccessDenied
        message.contains("HTTP_FORBIDDEN", ignoreCase = true) -> HttpAuthenticationFailureReason.AccessDenied
        message.contains("auth", ignoreCase = true) -> HttpAuthenticationFailureReason.Unknown
        message.contains("credential", ignoreCase = true) -> HttpAuthenticationFailureReason.Unknown
        else -> null
    }
}

internal fun applyManualInputSelectionAction(
    context: Context,
    appScope: CoroutineScope,
    repository: FileRepository,
    rawInput: String,
    options: ManualSourceOpenOptions,
    expandOverride: Boolean?,
    storageDescriptors: List<StorageDescriptor>,
    openPlayerOnTrackSelect: Boolean,
    onBrowserLaunchTargetChanged: (BrowserLaunchState) -> Unit,
    onCurrentViewChanged: (MainView) -> Unit,
    onAddRecentFolder: (String, String?, Long?) -> Unit,
    onVisiblePlayableFilesChanged: (List<File>) -> Unit,
    onApplyTrackSelection: (File, Boolean, Boolean?, String?) -> Unit,
    onLaunchManualRemoteSelection: (ManualSourceResolution, ManualSourceOpenOptions, Boolean?) -> Unit
) {
    val archiveFile = resolveArchiveSourceToMountedFile(context, rawInput)
    if (archiveFile != null) {
        appScope.launch {
            val contextualPlayableFiles = loadContextualPlayableFilesForManualSelection(
                repository = repository,
                localFile = archiveFile
            )
            onVisiblePlayableFilesChanged(contextualPlayableFiles)
            onApplyTrackSelection(archiveFile, true, openPlayerOnTrackSelect, rawInput.trim())
        }
        return
    }

    when (val action = resolveManualInputAction(rawInput, storageDescriptors)) {
        ManualInputAction.Invalid -> {
            Toast.makeText(context, MANUAL_INPUT_INVALID_MESSAGE, Toast.LENGTH_SHORT).show()
            return
        }

        is ManualInputAction.OpenDirectory -> {
            val launchLocationId = action.locationId ?: "/"
            onBrowserLaunchTargetChanged(
                BrowserLaunchState(
                    locationId = launchLocationId,
                    directoryPath = action.directoryPath
                )
            )
            onCurrentViewChanged(MainView.Browser)
            onAddRecentFolder(action.directoryPath, launchLocationId, null)
            return
        }

        is ManualInputAction.OpenLocalFile -> {
            appScope.launch {
                val localFile = action.file
                val contextualPlayableFiles = loadContextualPlayableFilesForManualSelection(
                    repository = repository,
                    localFile = localFile
                )
                onVisiblePlayableFilesChanged(contextualPlayableFiles)
                onApplyTrackSelection(localFile, true, openPlayerOnTrackSelect, action.sourceId)
            }
            return
        }

        is ManualInputAction.OpenRemote -> {
            appScope.launch {
                val directoryLaunch = resolveRemoteDirectoryLaunchAction(
                    rawInput = rawInput,
                    resolved = action.resolved
                )
                if (directoryLaunch != null) {
                    onBrowserLaunchTargetChanged(directoryLaunch.launchState)
                    onCurrentViewChanged(MainView.Browser)
                    onAddRecentFolder(
                        directoryLaunch.recentFolderSourceId,
                        null,
                        null
                    )
                    return@launch
                }
                onLaunchManualRemoteSelection(action.resolved, options, expandOverride)
            }
            return
        }
    }
}

private data class ManualRemoteDirectoryLaunch(
    val launchState: BrowserLaunchState,
    val recentFolderSourceId: String
)

private suspend fun resolveRemoteDirectoryLaunchAction(
    rawInput: String,
    resolved: ManualSourceResolution
): ManualRemoteDirectoryLaunch? = withContext(Dispatchers.IO) {
    when (resolved.type) {
        ManualSourceType.Smb -> {
            val smbSpec = resolved.smbSpec ?: return@withContext null
            val normalizedInput = rawInput.trim()
            val explicitTrailingSlash = normalizedInput.endsWith("/")
            val smbPath = normalizeSmbPathForShare(smbSpec.path)
            val likelyFilePath = smbPath
                ?.substringAfterLast('/')
                ?.let(::looksLikeFileLeaf)
                ?: false
            val isExplicitDirectory = smbSpec.share.isBlank() || smbSpec.path.isNullOrBlank() || explicitTrailingSlash
            val shouldTreatAsDirectory = if (isExplicitDirectory) {
                true
            } else if (likelyFilePath) {
                false
            } else {
                val listingResult = listSmbDirectoryEntries(
                    spec = smbSpec,
                    pathInsideShare = smbPath
                )
                val listingFailure = listingResult.exceptionOrNull()
                val authFailure = resolveSmbAuthenticationFailureReason(listingFailure) != null
                listingResult.isSuccess || authFailure || !likelyFilePath
            }
            if (!shouldTreatAsDirectory) return@withContext null

            val requestUri = buildSmbRequestUri(smbSpec)
            val sourceId = buildSmbSourceId(smbSpec)
            ManualRemoteDirectoryLaunch(
                launchState = BrowserLaunchState(
                    locationId = null,
                    directoryPath = requestUri,
                    smbSourceNodeId = null
                ),
                recentFolderSourceId = sourceId
            )
        }

        ManualSourceType.RemoteUrl -> {
            val requestSpec = parseHttpSourceSpecFromInput(resolved.requestUrl) ?: return@withContext null
            val listingResult = listHttpDirectoryEntries(requestSpec)
            val authFailure = resolveHttpAuthenticationFailureReason(listingResult.exceptionOrNull()) != null
            val normalizedInput = rawInput.trim()
            val explicitTrailingSlash = normalizedInput.endsWith("/")
            val likelyFilePath = requestSpec.path
                .substringAfterLast('/')
                .let(::looksLikeFileLeaf)
            val resolvedDirectorySpec = listingResult.getOrNull()?.resolvedDirectorySpec
                ?: requestSpec.copy(path = normalizeHttpDirectoryPath(requestSpec.path))
            val looksDirectory = isLikelyHttpDirectorySource(buildHttpRequestUri(requestSpec))
            val shouldTreatAsDirectory = when {
                explicitTrailingSlash -> listingResult.isSuccess || authFailure || looksDirectory
                likelyFilePath -> false
                else -> listingResult.isSuccess || looksDirectory
            }
            if (!shouldTreatAsDirectory) return@withContext null

            val normalizedDirectorySpec = resolvedDirectorySpec.copy(
                path = normalizeHttpDirectoryPath(resolvedDirectorySpec.path)
            )
            ManualRemoteDirectoryLaunch(
                launchState = BrowserLaunchState(
                    locationId = null,
                    directoryPath = buildHttpRequestUri(normalizedDirectorySpec),
                    httpSourceNodeId = null,
                    httpRootPath = normalizeHttpDirectoryPath(normalizedDirectorySpec.path)
                ),
                recentFolderSourceId = buildHttpSourceId(normalizedDirectorySpec)
            )
        }

        else -> null
    }
}

private fun looksLikeFileLeaf(leaf: String): Boolean {
    val normalized = leaf.trim().substringBefore('?').substringBefore('#')
    if (normalized.isBlank()) return false
    if (normalized.endsWith("/")) return false
    val dotIndex = normalized.lastIndexOf('.')
    if (dotIndex <= 0 || dotIndex >= normalized.length - 1) return false
    val extension = normalized.substring(dotIndex + 1)
    return extension.length in 1..8 && extension.all { it.isLetterOrDigit() }
}
