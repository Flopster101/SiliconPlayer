package com.flopster101.siliconplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

internal fun loadArtworkForFile(file: File): ImageBitmap? {
    loadEmbeddedArtwork(file)?.let { return it.asImageBitmap() }
    findFolderArtworkFile(file)?.let { folderImage ->
        decodeScaledBitmapFromFile(folderImage)?.let { return it.asImageBitmap() }
    }
    return null
}

internal fun loadArtworkForSource(
    context: Context,
    displayFile: File?,
    sourceId: String?,
    requestUrl: String? = null
): ImageBitmap? {
    val normalized = sourceId?.trim()
    val scheme = normalized?.let { Uri.parse(it).scheme?.lowercase(Locale.ROOT) }
    val normalizedRequestUrl = requestUrl?.trim().takeUnless { it.isNullOrBlank() }
    val requestScheme = normalizedRequestUrl?.let { Uri.parse(it).scheme?.lowercase(Locale.ROOT) }
    val artworkRequestUrl = when (requestScheme) {
        "http", "https" -> normalizedRequestUrl
        else -> normalized
    }
    val isRemote = scheme == "http" || scheme == "https" || scheme == "smb"

    displayFile?.takeIf { it.exists() && it.isFile }?.let { local ->
        loadArtworkForFile(local)?.let { return it }
    }

    if (isRemote && !normalized.isNullOrBlank()) {
        if ((scheme == "http" || scheme == "https") && !artworkRequestUrl.isNullOrBlank()) {
            loadEmbeddedArtworkFromRemote(artworkRequestUrl)?.let { return it.asImageBitmap() }
        }
        val cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR)
        findExistingCachedFileForSource(cacheRoot, normalized)?.let { cached ->
            loadArtworkForFile(cached)?.let { return it }
        }
        if ((scheme == "http" || scheme == "https") && !artworkRequestUrl.isNullOrBlank()) {
            loadFolderArtworkFromHttpSource(artworkRequestUrl)?.let { return it.asImageBitmap() }
        }
    }
    return null
}

private fun loadEmbeddedArtwork(file: File): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val embedded = retriever.embeddedPicture ?: return null
        decodeScaledBitmapFromBytes(embedded)
    } catch (_: Exception) {
        null
    } finally {
        retriever.release()
    }
}

private fun loadEmbeddedArtworkFromRemote(url: String): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        val headers = mutableMapOf(
            "User-Agent" to "SiliconPlayer/1.0 (Android)",
            "Icy-MetaData" to "1"
        )
        parseHttpSourceSpecFromInput(url)?.let { spec ->
            httpBasicAuthorizationHeader(
                username = spec.username,
                password = spec.password
            )?.let { authHeader ->
                headers["Authorization"] = authHeader
            }
        }
        retriever.setDataSource(
            url,
            headers
        )
        val embedded = retriever.embeddedPicture ?: return null
        decodeScaledBitmapFromBytes(embedded)
    } catch (_: Exception) {
        null
    } finally {
        retriever.release()
    }
}

private fun loadFolderArtworkFromHttpSource(sourceId: String): Bitmap? {
    val spec = parseHttpSourceSpecFromInput(sourceId) ?: return null
    val normalizedPath = normalizeHttpPath(spec.path).trimEnd('/')
    if (normalizedPath.isBlank()) return null
    val parentPath = normalizedPath.substringBeforeLast('/', missingDelimiterValue = "").trim()
    val directoryPath = if (parentPath.isBlank()) "/" else normalizeHttpDirectoryPath(parentPath)
    return FOLDER_ARTWORK_FILE_NAMES.firstNotNullOfOrNull { artworkName ->
        val artworkSpec = spec.copy(
            path = normalizeHttpPath("$directoryPath$artworkName"),
            query = null
        )
        loadBitmapFromHttpUrl(buildHttpRequestUri(artworkSpec))
    }
}

private fun loadBitmapFromHttpUrl(url: String): Bitmap? {
    val connection = (URL(url).openConnection() as? HttpURLConnection) ?: return null
    return try {
        connection.connectTimeout = 8_000
        connection.readTimeout = 10_000
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "SiliconPlayer/1.0 (Android)")
        connection.setRequestProperty("Accept", "image/*,*/*;q=0.8")
        connection.setRequestProperty("Connection", "close")
        parseHttpSourceSpecFromInput(url)?.let { requestSpec ->
            httpBasicAuthorizationHeader(
                username = requestSpec.username,
                password = requestSpec.password
            )?.let { authHeader ->
                connection.setRequestProperty("Authorization", authHeader)
            }
        }
        if (connection.responseCode !in 200..299) return null
        val data = connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                if (total > MAX_REMOTE_ARTWORK_BYTES) return null
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
        decodeScaledBitmapFromBytes(data)
    } catch (_: Exception) {
        null
    } finally {
        connection.disconnect()
    }
}

private fun findFolderArtworkFile(trackFile: File): File? {
    val parent = trackFile.parentFile ?: return null
    if (!parent.isDirectory) return null

    val allowedNames = setOf(
        "cover.jpg", "cover.jpeg", "cover.png", "cover.webp",
        "folder.jpg", "folder.jpeg", "folder.png", "folder.webp",
        "album.jpg", "album.jpeg", "album.png", "album.webp",
        "front.jpg", "front.jpeg", "front.png", "front.webp",
        "artwork.jpg", "artwork.jpeg", "artwork.png", "artwork.webp"
    )

    return parent.listFiles()
        ?.firstOrNull { it.isFile && allowedNames.contains(it.name.lowercase()) }
}

private fun decodeScaledBitmapFromBytes(data: ByteArray, maxSize: Int = 1024): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
    val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSize)

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeByteArray(data, 0, data.size, options)
}

private fun decodeScaledBitmapFromFile(file: File, maxSize: Int = 1024): Bitmap? {
    val path = file.absolutePath
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSize)

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeFile(path, options)
}

private fun calculateInSampleSize(width: Int, height: Int, maxSize: Int): Int {
    var sampleSize = 1
    var currentWidth = width
    var currentHeight = height
    while (currentWidth > maxSize || currentHeight > maxSize) {
        currentWidth /= 2
        currentHeight /= 2
        sampleSize *= 2
    }
    return sampleSize.coerceAtLeast(1)
}

private const val MAX_REMOTE_ARTWORK_BYTES = 8 * 1024 * 1024

private val FOLDER_ARTWORK_FILE_NAMES = listOf(
    "cover.jpg", "cover.jpeg", "cover.png", "cover.webp",
    "folder.jpg", "folder.jpeg", "folder.png", "folder.webp",
    "album.jpg", "album.jpeg", "album.png", "album.webp",
    "front.jpg", "front.jpeg", "front.png", "front.webp",
    "artwork.jpg", "artwork.jpeg", "artwork.png", "artwork.webp"
)
