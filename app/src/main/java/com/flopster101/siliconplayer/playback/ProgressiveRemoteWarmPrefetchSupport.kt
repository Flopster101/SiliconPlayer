package com.flopster101.siliconplayer

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.min

internal const val NEXT_TRACK_WARM_PREFETCH_START_DELAY_MS = 12_000L
internal const val NEXT_TRACK_WARM_PREFETCH_MAX_BYTES = 12L * 1024L * 1024L
internal const val NEXT_TRACK_WARM_PREFETCH_RATE_LIMIT_BYTES_PER_SECOND = 256L * 1024L

private fun resolvedCredentialedSmbSpecForWarmPrefetch(
    sourceId: String,
    credentialHintRequestUrl: String?
): SmbSourceSpec? {
    val parsedSourceSpec = parseSmbSourceSpecFromInput(sourceId) ?: return null
    if (!parsedSourceSpec.username.isNullOrBlank() || !parsedSourceSpec.password.isNullOrBlank()) {
        return parsedSourceSpec
    }

    ManualSmbAuthCoordinator.credentialsFor(parsedSourceSpec)?.let { cachedCredentials ->
        return parsedSourceSpec.copy(
            username = cachedCredentials.first?.trim().takeUnless { it.isNullOrBlank() },
            password = cachedCredentials.second?.trim().takeUnless { it.isNullOrBlank() }
        )
    }

    val hintSpec = credentialHintRequestUrl
        ?.let(::parseSmbSourceSpecFromInput)
        ?.takeIf { hint ->
            hint.host.equals(parsedSourceSpec.host, ignoreCase = true) &&
                hint.share.equals(parsedSourceSpec.share, ignoreCase = true)
        }
        ?: return parsedSourceSpec

    if (hintSpec.username.isNullOrBlank() && hintSpec.password.isNullOrBlank()) {
        return parsedSourceSpec
    }

    return parsedSourceSpec.copy(
        username = hintSpec.username?.trim().takeUnless { it.isNullOrBlank() },
        password = hintSpec.password?.trim().takeUnless { it.isNullOrBlank() }
    )
}

internal suspend fun warmProgressiveSmbSourcePrefix(
    context: Context,
    sourceId: String,
    credentialHintRequestUrl: String?,
    maxBytesToPrefetch: Long = NEXT_TRACK_WARM_PREFETCH_MAX_BYTES,
    rateLimitBytesPerSecond: Long = NEXT_TRACK_WARM_PREFETCH_RATE_LIMIT_BYTES_PER_SECOND
): Boolean = withContext(Dispatchers.IO) {
    val spec = resolvedCredentialedSmbSpecForWarmPrefetch(
        sourceId = sourceId,
        credentialHintRequestUrl = credentialHintRequestUrl
    ) ?: return@withContext false
    val remotePath = spec.path?.trim().orEmpty()
    if (spec.share.isBlank() || remotePath.isBlank()) {
        return@withContext false
    }

    val normalizedMaxBytes = maxBytesToPrefetch.coerceAtLeast(0L)
    val normalizedRateLimit = rateLimitBytesPerSecond.coerceAtLeast(1L)
    var cache: ProgressiveRandomAccessCache? = null
    return@withContext try {
        cache = ProgressiveRandomAccessCache(
            context = context.applicationContext,
            transport = SmbProgressiveRandomAccessTransport(
                spec = spec,
                remotePath = remotePath
            ),
            prefetchTransportFactory = {
                SmbProgressiveRandomAccessTransport(
                    spec = spec,
                    remotePath = remotePath
                )
            },
            prefetchConfig = ProgressiveRandomAccessPrefetchConfig(
                maxBytesToPrefetch = normalizedMaxBytes,
                rateLimitBytesPerSecond = normalizedRateLimit
            )
        )
        val targetBytes = min(normalizedMaxBytes, cache.sizeBytes)
        if (targetBytes <= 0L) {
            true
        } else {
            while (cache.cachedPrefixBytes < targetBytes) {
                currentCoroutineContext().ensureActive()
                delay(250L)
            }
            true
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        false
    } finally {
        cache?.close()
    }
}
