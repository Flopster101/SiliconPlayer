package com.flopster101.siliconplayer

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

internal suspend fun downloadRemoteUrlToCache(
    context: Context,
    url: String,
    requestUrl: String,
    onStatus: suspend (RemoteLoadUiState) -> Unit
): RemoteDownloadResult = withContext(Dispatchers.IO) {
    val cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR)
    var target = remoteCacheFileForSource(cacheRoot, url)

    suspend fun emitStatus(state: RemoteLoadUiState) {
        withContext(Dispatchers.Main.immediate) {
            onStatus(state)
        }
    }

    findExistingCachedFileForSource(cacheRoot, url)?.let { existing ->
        existing.setLastModified(System.currentTimeMillis())
        rememberSourceForCachedFile(cacheRoot, existing.name, url)
        Log.d(URL_SOURCE_TAG, "Using existing cached file: ${existing.absolutePath} (${existing.length()} bytes)")
        return@withContext RemoteDownloadResult(file = existing)
    }

    var temp = File(target.absolutePath + ".part")
    Log.d(
        URL_SOURCE_TAG,
        "Downloading URL to cache: source=${sanitizeHttpUrlForLog(url)} request=${sanitizeHttpUrlForLog(requestUrl)}"
    )
    emitStatus(
        RemoteLoadUiState(
            sourceId = url,
            phase = RemoteLoadPhase.Connecting,
            indeterminate = true
        )
    )

    suspend fun openWithRedirects(initialUrl: String, maxRedirects: Int = 6): Pair<HttpURLConnection?, String?> {
        var currentUrl = initialUrl
        repeat(maxRedirects + 1) { hop ->
            kotlin.coroutines.coroutineContext.ensureActive()
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("User-Agent", "SiliconPlayer/1.0 (Android)")
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Connection", "close")
                setRequestProperty("Icy-MetaData", "1")
                parseHttpSourceSpecFromInput(currentUrl)?.let { spec ->
                    httpBasicAuthorizationHeader(
                        username = spec.username,
                        password = spec.password
                    )?.let { header ->
                        setRequestProperty("Authorization", header)
                    }
                }
            }
            val responseCode = connection.responseCode
            if (responseCode in 300..399) {
                val location = connection.getHeaderField("Location")
                Log.d(
                    URL_SOURCE_TAG,
                    "Redirect hop=$hop code=$responseCode from=${sanitizeHttpUrlForLog(currentUrl)} to=${location ?: "<missing>"}"
                )
                connection.disconnect()
                if (location.isNullOrBlank()) {
                    return Pair(null, "Redirect missing Location header (HTTP $responseCode)")
                }
                currentUrl = URL(URL(currentUrl), location).toString()
                return@repeat
            }
            Log.d(
                URL_SOURCE_TAG,
                "HTTP response code=$responseCode finalUrl=${sanitizeHttpUrlForLog(currentUrl)}"
            )
            if (responseCode !in 200..299) {
                connection.disconnect()
                return Pair(null, "HTTP $responseCode")
            }
            return Pair(connection, null)
        }
        Log.e(URL_SOURCE_TAG, "Too many redirects for URL: ${sanitizeHttpUrlForLog(initialUrl)}")
        return Pair(null, "Too many redirects")
    }

    var connection: HttpURLConnection? = null
    return@withContext try {
        val (openedConnection, openError) = openWithRedirects(requestUrl)
        connection = openedConnection
        if (connection == null) {
            Log.e(
                URL_SOURCE_TAG,
                "HTTP open failed for URL: source=${sanitizeHttpUrlForLog(url)} request=${sanitizeHttpUrlForLog(requestUrl)}"
            )
            RemoteDownloadResult(
                file = null,
                errorMessage = openError ?: "Connection failed"
            )
        } else {
            val contentDispositionName = filenameFromContentDisposition(
                connection.getHeaderField("Content-Disposition")
            )
            if (!contentDispositionName.isNullOrBlank()) {
                val resolvedTarget = File(cacheRoot, "${sha1Hex(url)}_$contentDispositionName")
                if (resolvedTarget.absolutePath != target.absolutePath) {
                    target = resolvedTarget
                    temp = File(target.absolutePath + ".part")
                    findExistingCachedFileForSource(cacheRoot, url)?.let { existing ->
                        existing.setLastModified(System.currentTimeMillis())
                        rememberSourceForCachedFile(cacheRoot, existing.name, url)
                        Log.d(
                            URL_SOURCE_TAG,
                            "Using existing cached file after Content-Disposition resolution: ${existing.absolutePath} (${existing.length()} bytes)"
                        )
                        return@withContext RemoteDownloadResult(file = existing)
                    }
                }
            }
            val expectedBytes = connection.contentLengthLong
            var totalBytes = 0L
            var latestBytesPerSecond: Long? = null
            val startedAtMs = System.currentTimeMillis()
            var lastSpeedSampleBytes = 0L
            var lastSpeedSampleMs = startedAtMs
            var lastUiUpdateMs = 0L
            suspend fun publishDownloadStatus(force: Boolean = false) {
                val now = System.currentTimeMillis()
                if (!force && now - lastUiUpdateMs < 120L) return
                lastUiUpdateMs = now
                val hasKnownTotal = expectedBytes > 0L
                val percentValue = if (hasKnownTotal) {
                    ((totalBytes * 100L) / expectedBytes).toInt().coerceIn(0, 100)
                } else {
                    null
                }
                emitStatus(
                    RemoteLoadUiState(
                        sourceId = url,
                        phase = RemoteLoadPhase.Downloading,
                        downloadedBytes = totalBytes,
                        totalBytes = expectedBytes.takeIf { it > 0L },
                        bytesPerSecond = latestBytesPerSecond,
                        percent = percentValue,
                        indeterminate = !hasKnownTotal
                    )
                )
            }

            publishDownloadStatus(force = true)
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        kotlin.coroutines.coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        totalBytes += read
                        val now = System.currentTimeMillis()
                        val deltaMs = now - lastSpeedSampleMs
                        if (deltaMs >= 350L) {
                            val deltaBytes = totalBytes - lastSpeedSampleBytes
                            latestBytesPerSecond = ((deltaBytes * 1000L) / deltaMs).coerceAtLeast(0L)
                            lastSpeedSampleBytes = totalBytes
                            lastSpeedSampleMs = now
                        }
                        publishDownloadStatus()
                    }
                    output.flush()
                }
            }
            publishDownloadStatus(force = true)
            Log.d(
                URL_SOURCE_TAG,
                "Download complete bytes=$totalBytes expected=$expectedBytes temp=${temp.absolutePath}"
            )
            if (totalBytes <= 0L) {
                return@withContext RemoteDownloadResult(
                    file = null,
                    errorMessage = "Downloaded 0 bytes"
                )
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            rememberSourceForCachedFile(cacheRoot, target.name, url)
            Log.d(URL_SOURCE_TAG, "Cached file ready: ${target.absolutePath} (${target.length()} bytes)")
            val elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L)
            val avgSpeed = (totalBytes * 1000L) / elapsedMs
            emitStatus(
                RemoteLoadUiState(
                    sourceId = url,
                    phase = RemoteLoadPhase.Downloading,
                    downloadedBytes = totalBytes,
                    totalBytes = expectedBytes.takeIf { it > 0L },
                    bytesPerSecond = latestBytesPerSecond ?: avgSpeed,
                    percent = if (expectedBytes > 0L) 100 else null,
                    indeterminate = expectedBytes <= 0L
                )
            )
            RemoteDownloadResult(file = target)
        }
    } catch (_: CancellationException) {
        Log.d(URL_SOURCE_TAG, "Download cancelled for URL: ${sanitizeHttpUrlForLog(url)}")
        RemoteDownloadResult(file = null, errorMessage = "Cancelled", cancelled = true)
    } catch (t: Throwable) {
        Log.e(
            URL_SOURCE_TAG,
            "Download failed for URL: ${sanitizeHttpUrlForLog(url)} (${t::class.java.simpleName}: ${t.message})"
        )
        RemoteDownloadResult(
            file = null,
            errorMessage = "${t::class.java.simpleName}: ${t.message ?: "unknown error"}"
        )
    } finally {
        connection?.disconnect()
        if (temp.exists() && (target.length() <= 0L)) {
            temp.delete()
        }
    }
}
