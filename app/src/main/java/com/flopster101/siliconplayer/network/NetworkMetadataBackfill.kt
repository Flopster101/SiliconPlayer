package com.flopster101.siliconplayer

import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val pendingNetworkMetadataJobs = LinkedHashMap<String, Job>()

internal fun cancelPendingNetworkSourceMetadataBackfillJobs() {
    val jobs = pendingNetworkMetadataJobs.values.toList()
    pendingNetworkMetadataJobs.clear()
    jobs.forEach { it.cancel() }
}

internal fun scheduleNetworkSourceMetadataBackfill(
    scope: CoroutineScope,
    sourceId: String,
    onResolved: (sourceId: String, title: String?, artist: String?) -> Unit,
    onSettled: (sourceId: String) -> Unit = {}
) {
    val normalizedSource = normalizeSourceIdentity(sourceId) ?: run {
        onSettled(sourceId)
        return
    }
    pendingNetworkMetadataJobs.remove(normalizedSource)?.cancel()
    val job = scope.launch(Dispatchers.IO) {
        try {
            val metadata = readNetworkSourceMetadata(normalizedSource)
            withContext(Dispatchers.Main.immediate) {
                metadata?.let { resolved ->
                    onResolved(normalizedSource, resolved.first, resolved.second)
                }
                onSettled(normalizedSource)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            withContext(Dispatchers.Main.immediate) {
                onSettled(normalizedSource)
            }
        }
    }
    pendingNetworkMetadataJobs[normalizedSource] = job
    job.invokeOnCompletion {
        pendingNetworkMetadataJobs.remove(normalizedSource)
    }
}

private fun readNetworkSourceMetadata(sourceId: String): Pair<String?, String?>? {
    val uri = Uri.parse(sourceId)
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    val retriever = MediaMetadataRetriever()
    return try {
        when (scheme) {
            "http", "https" -> retriever.setDataSource(sourceId, emptyMap())
            "file" -> retriever.setDataSource(uri.path ?: return null)
            else -> {
                val localFile = File(sourceId)
                if (localFile.exists() && localFile.isFile) {
                    retriever.setDataSource(localFile.absolutePath)
                } else {
                    retriever.setDataSource(sourceId, emptyMap())
                }
            }
        }
        val title = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            ?.trim()
            .takeUnless { it.isNullOrBlank() }
        val artist = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            ?.trim()
            .takeUnless { it.isNullOrBlank() }
        if (title == null && artist == null) null else Pair(title, artist)
    } catch (_: Throwable) {
        null
    } finally {
        retriever.release()
    }
}
