package com.flopster101.siliconplayer

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.coroutineContext

internal data class HttpBrowserEntry(
    val name: String,
    val sourceId: String,
    val requestUrl: String,
    val isDirectory: Boolean
)

internal data class HttpDirectoryListingResult(
    val resolvedDirectorySpec: HttpSourceSpec,
    val entries: List<HttpBrowserEntry>
)

internal enum class HttpAuthenticationFailureReason {
    AuthenticationRequired,
    AccessDenied,
    Unknown
}

internal suspend fun listHttpDirectoryEntries(
    spec: HttpSourceSpec,
    onProgress: (suspend (loadedCount: Int, partialEntries: List<HttpBrowserEntry>) -> Unit)? = null
): Result<HttpDirectoryListingResult> = withContext(Dispatchers.IO) {
    runCatching {
        val listing = openHttpDirectoryListingConnection(spec)
        val resolvedDirectorySpec = listing.resolvedSpec.copy(
            path = normalizeHttpDirectoryPath(listing.resolvedSpec.path)
        )
        listing.connection.useConnection { connection ->
            val entriesByKey = LinkedHashMap<String, HttpBrowserEntry>()
            connection.inputStream.bufferedReader().use { reader ->
                readHttpDirectoryListingIncremental(
                    reader = reader,
                    baseRequestSpec = resolvedDirectorySpec,
                    entriesByKey = entriesByKey,
                    onProgress = onProgress
                )
            }
            val entries = entriesByKey.values
                .sortedWith(
                    compareBy<HttpBrowserEntry> { !it.isDirectory }
                        .thenBy { it.name.lowercase(Locale.ROOT) }
                )
            HttpDirectoryListingResult(
                resolvedDirectorySpec = resolvedDirectorySpec,
                entries = entries
            )
        }
    }
}

internal fun resolveHttpAuthenticationFailureReason(
    throwable: Throwable?
): HttpAuthenticationFailureReason? {
    val auth = throwable as? HttpAuthenticationException ?: return null
    return when (auth.statusCode) {
        HttpURLConnection.HTTP_UNAUTHORIZED -> HttpAuthenticationFailureReason.AuthenticationRequired
        HttpURLConnection.HTTP_FORBIDDEN -> HttpAuthenticationFailureReason.AccessDenied
        else -> HttpAuthenticationFailureReason.Unknown
    }
}

internal fun httpAuthenticationFailureMessage(reason: HttpAuthenticationFailureReason): String {
    return when (reason) {
        HttpAuthenticationFailureReason.AuthenticationRequired ->
            "This HTTP source requires valid username/password credentials."
        HttpAuthenticationFailureReason.AccessDenied ->
            "Access denied by server (HTTP 403). Check credentials and permissions."
        HttpAuthenticationFailureReason.Unknown ->
            "Authentication failed for this HTTP source."
    }
}

private data class HttpDirectoryConnection(
    val resolvedSpec: HttpSourceSpec,
    val connection: HttpURLConnection
)

private class HttpAuthenticationException(
    val statusCode: Int,
    statusMessage: String?
) : IllegalStateException(
    if (statusMessage.isNullOrBlank()) {
        "HTTP $statusCode"
    } else {
        "HTTP $statusCode: $statusMessage"
    }
)

private fun openHttpDirectoryListingConnection(
    initialSpec: HttpSourceSpec,
    maxRedirects: Int = 6
): HttpDirectoryConnection {
    var currentSpec = initialSpec
    repeat(maxRedirects + 1) {
        val requestUrl = buildHttpRequestUri(currentSpec)
        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("User-Agent", "SiliconPlayer/1.0 (Android)")
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            setRequestProperty("Connection", "close")
            httpBasicAuthorizationHeader(
                username = currentSpec.username,
                password = currentSpec.password
            )?.let { header ->
                setRequestProperty("Authorization", header)
            }
        }
        val responseCode = connection.responseCode
        if (responseCode in 300..399) {
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (location.isNullOrBlank()) {
                throw IllegalStateException("Redirect missing Location header (HTTP $responseCode)")
            }
            val redirectedUrl = URL(URL(requestUrl), location).toString()
            val redirectedSpec = parseHttpSourceSpecFromInput(redirectedUrl)
                ?: throw IllegalStateException("Invalid redirect URL: $redirectedUrl")
            currentSpec = if (
                redirectedSpec.username.isNullOrBlank() &&
                redirectedSpec.password.isNullOrBlank()
            ) {
                redirectedSpec.copy(
                    username = currentSpec.username,
                    password = currentSpec.password
                )
            } else {
                redirectedSpec
            }
            return@repeat
        }
        if (
            responseCode == HttpURLConnection.HTTP_UNAUTHORIZED ||
            responseCode == HttpURLConnection.HTTP_FORBIDDEN
        ) {
            connection.disconnect()
            throw HttpAuthenticationException(responseCode, connection.responseMessage)
        }
        if (responseCode !in 200..299) {
            connection.disconnect()
            throw IllegalStateException(
                if (connection.responseMessage.isNullOrBlank()) {
                    "HTTP $responseCode"
                } else {
                    "HTTP $responseCode: ${connection.responseMessage}"
                }
            )
        }
        return HttpDirectoryConnection(
            resolvedSpec = currentSpec,
            connection = connection
        )
    }
    throw IllegalStateException("Too many redirects")
}

private fun parseHttpDirectoryListing(
    html: String,
    baseRequestSpec: HttpSourceSpec
): List<HttpBrowserEntry> {
    val entriesByKey = LinkedHashMap<String, HttpBrowserEntry>()
    parseHttpDirectoryListingInto(
        html = html,
        baseRequestSpec = baseRequestSpec,
        entriesByKey = entriesByKey
    )
    return entriesByKey.values
        .sortedWith(
            compareBy<HttpBrowserEntry> { !it.isDirectory }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        )
}

private suspend fun readHttpDirectoryListingIncremental(
    reader: java.io.Reader,
    baseRequestSpec: HttpSourceSpec,
    entriesByKey: LinkedHashMap<String, HttpBrowserEntry>,
    onProgress: (suspend (loadedCount: Int, partialEntries: List<HttpBrowserEntry>) -> Unit)?
) {
    val chunkBuffer = CharArray(4096)
    val rollingBuffer = StringBuilder()
    var lastEmittedCount = 0
    var lastEmitNs = System.nanoTime()
    while (true) {
        coroutineContext.ensureActive()
        val readCount = reader.read(chunkBuffer)
        if (readCount <= 0) break
        rollingBuffer.append(chunkBuffer, 0, readCount)
        val completeAnchorEndIndex = findLastCompleteAnchorEndIndex(rollingBuffer)
        if (completeAnchorEndIndex > 0) {
            val completeChunk = rollingBuffer.substring(0, completeAnchorEndIndex)
            parseHttpDirectoryListingInto(
                html = completeChunk,
                baseRequestSpec = baseRequestSpec,
                entriesByKey = entriesByKey
            )
            rollingBuffer.delete(0, completeAnchorEndIndex)
            if (onProgress != null) {
                val nowNs = System.nanoTime()
                val newCount = entriesByKey.size
                val shouldEmit = newCount > lastEmittedCount && (
                    (newCount - lastEmittedCount) >= 8 ||
                        nowNs - lastEmitNs >= 150_000_000L
                    )
                if (shouldEmit) {
                    emitHttpDirectoryListingProgress(entriesByKey, onProgress)
                    lastEmittedCount = newCount
                    lastEmitNs = nowNs
                }
            }
        }
    }
    if (rollingBuffer.isNotEmpty()) {
        parseHttpDirectoryListingInto(
            html = rollingBuffer.toString(),
            baseRequestSpec = baseRequestSpec,
            entriesByKey = entriesByKey
        )
    }
    if (onProgress != null && entriesByKey.isNotEmpty()) {
        emitHttpDirectoryListingProgress(entriesByKey, onProgress)
    }
}

private suspend fun emitHttpDirectoryListingProgress(
    entriesByKey: LinkedHashMap<String, HttpBrowserEntry>,
    onProgress: suspend (loadedCount: Int, partialEntries: List<HttpBrowserEntry>) -> Unit
) {
    val snapshot = entriesByKey.values
        .sortedWith(
            compareBy<HttpBrowserEntry> { !it.isDirectory }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        )
    withContext(Dispatchers.Main.immediate) {
        onProgress(snapshot.size, snapshot)
    }
}

private fun parseHttpDirectoryListingInto(
    html: String,
    baseRequestSpec: HttpSourceSpec,
    entriesByKey: LinkedHashMap<String, HttpBrowserEntry>
) {
    val baseRequestUrl = buildHttpRequestUri(
        baseRequestSpec.copy(path = normalizeHttpDirectoryPath(baseRequestSpec.path))
    )
    HTTP_ANCHOR_REGEX.findAll(html).forEach { match ->
        val href = match.groups[2]?.value
            ?: match.groups[3]?.value
            ?: match.groups[4]?.value
            ?: return@forEach
        val normalizedHref = href.trim()
        if (normalizedHref.isBlank()) return@forEach
        if (normalizedHref.startsWith("#")) return@forEach
        if (normalizedHref.equals(".", ignoreCase = true)) return@forEach
        if (normalizedHref.equals("..", ignoreCase = true) || normalizedHref.startsWith("../")) {
            return@forEach
        }
        if (normalizedHref.startsWith("javascript:", ignoreCase = true)) return@forEach

        val absoluteUrl = URL(URL(baseRequestUrl), normalizedHref).toString()
        val absoluteSpec = parseHttpSourceSpecFromInput(absoluteUrl) ?: return@forEach
        val normalizedPath = normalizeHttpPath(absoluteSpec.path)
        val rawLeaf = normalizedPath.substringAfterLast('/').trim()
        if (rawLeaf == "..") return@forEach

        val renderedLabel = decodeHtmlEntities(
            HTTP_TAG_REGEX.replace(match.groups[5]?.value.orEmpty(), "")
        ).trim()
        val normalizedLabel = normalizeHttpListingLabel(renderedLabel)
        if (shouldIgnoreHttpListingEntry(normalizedHref, absoluteSpec, baseRequestSpec, normalizedLabel)) {
            return@forEach
        }

        val fallbackLeaf = Uri.decode(rawLeaf).ifBlank {
            absoluteSpec.host
        }
        val isDirectory = normalizedHref.endsWith("/") || renderedLabel.endsWith("/")
        val displayName = renderedLabel
            .removeSuffix("/")
            .ifBlank { fallbackLeaf.removeSuffix("/") }
            .ifBlank { absoluteSpec.host }
        val normalizedSpec = if (isDirectory) {
            absoluteSpec.copy(path = normalizeHttpDirectoryPath(absoluteSpec.path))
        } else {
            absoluteSpec.copy(path = normalizeHttpPath(absoluteSpec.path))
        }
        val sourceId = buildHttpSourceId(normalizedSpec)
        entriesByKey[sourceId] = HttpBrowserEntry(
            name = displayName,
            sourceId = sourceId,
            requestUrl = buildHttpRequestUri(normalizedSpec),
            isDirectory = isDirectory
        )
    }
}

private fun findLastCompleteAnchorEndIndex(buffer: StringBuilder): Int {
    val lower = buffer.toString().lowercase(Locale.ROOT)
    val index = lower.lastIndexOf("</a>")
    return if (index < 0) -1 else index + 4
}

private fun decodeHtmlEntities(value: String): String {
    return value
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&darr;", "↓")
        .replace("&uarr;", "↑")
        .replace("&nbsp;", " ")
}

private fun shouldIgnoreHttpListingEntry(
    normalizedHref: String,
    absoluteSpec: HttpSourceSpec,
    baseRequestSpec: HttpSourceSpec,
    normalizedLabel: String
): Boolean {
    if (normalizedLabel.isBlank()) return false
    if (normalizedLabel == "parent directory") return true
    if (normalizedLabel in HTTP_DECORATIVE_ARROW_LABELS) return true
    val samePath = normalizeHttpDirectoryPath(absoluteSpec.path) == normalizeHttpDirectoryPath(baseRequestSpec.path)
    if (!samePath) return false
    if (normalizedLabel in HTTP_LISTING_HEADER_LABELS) return true
    if (normalizedHref.startsWith("?")) return true
    if (!absoluteSpec.query.isNullOrBlank()) {
        val queryLower = absoluteSpec.query.lowercase(Locale.ROOT)
        if (
            "c=" in queryLower ||
            "o=" in queryLower ||
            "sort" in queryLower ||
            "order" in queryLower
        ) {
            return true
        }
    }
    return false
}

private fun normalizeHttpListingLabel(raw: String): String {
    return decodeHtmlEntities(raw)
        .replace(Regex("\\s+"), " ")
        .trim()
        .trimEnd(':')
        .lowercase(Locale.ROOT)
}

private val HTTP_LISTING_HEADER_LABELS = setOf(
    "name",
    "file name",
    "file size",
    "size",
    "date",
    "last modified",
    "description"
)

private val HTTP_DECORATIVE_ARROW_LABELS = setOf(
    "↓",
    "↑",
    "darr",
    "uarr",
    "&darr;",
    "&uarr;"
)

private val HTTP_ANCHOR_REGEX = Regex(
    pattern = "<a\\b[^>]*?href\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))[^>]*>(.*?)</a>",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val HTTP_TAG_REGEX = Regex("<[^>]+>", setOf(RegexOption.IGNORE_CASE))

private inline fun <T> HttpURLConnection.useConnection(block: (HttpURLConnection) -> T): T {
    return try {
        block(this)
    } finally {
        disconnect()
    }
}
