package com.flopster101.siliconplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream

internal const val LOCAL_BROWSER_THUMBNAIL_CACHE_DIR = "browser_local_thumbnails"
private const val LOCAL_BROWSER_THUMBNAIL_MAX_SIZE_PX = 160
private const val LOCAL_BROWSER_THUMBNAIL_CACHE_MAX_FILES = 240
private const val LOCAL_BROWSER_THUMBNAIL_CACHE_MAX_BYTES = 32L * 1024L * 1024L

internal fun resolveLocalBrowserThumbnailPreview(
    context: Context,
    file: File
): ImageBitmap? {
    if (!file.exists() || !file.isFile) return null
    val cacheRoot = File(context.cacheDir, LOCAL_BROWSER_THUMBNAIL_CACHE_DIR)
    if (!cacheRoot.exists() && !cacheRoot.mkdirs()) return null
    val cacheFile = File(cacheRoot, localBrowserThumbnailCacheKey(file))
    val now = System.currentTimeMillis()

    if (cacheFile.exists() && cacheFile.isFile && cacheFile.length() > 0L) {
        cacheFile.setLastModified(now)
        return BitmapFactory.decodeFile(cacheFile.absolutePath)?.asImageBitmap()
    }

    val artwork = loadArtworkForFile(file) ?: return null
    val artworkBitmap = try {
        artwork.asAndroidBitmap()
    } catch (_: Throwable) {
        return null
    }
    val scaledBitmap = scaleBitmapForLocalBrowserThumbnail(
        bitmap = artworkBitmap,
        maxSize = LOCAL_BROWSER_THUMBNAIL_MAX_SIZE_PX
    )

    return try {
        FileOutputStream(cacheFile).use { output ->
            if (!scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)) {
                return null
            }
        }
        if (cacheFile.length() <= 0L) {
            cacheFile.delete()
            null
        } else {
            cacheFile.setLastModified(now)
            pruneLocalBrowserThumbnailCache(cacheRoot)
            scaledBitmap.asImageBitmap()
        }
    } catch (_: Throwable) {
        cacheFile.delete()
        null
    }
}

internal fun clearLocalBrowserThumbnailCache(context: Context): Int {
    val cacheRoot = File(context.cacheDir, LOCAL_BROWSER_THUMBNAIL_CACHE_DIR)
    if (!cacheRoot.exists() || !cacheRoot.isDirectory) return 0
    val files = cacheRoot.listFiles().orEmpty().filter { it.isFile }
    var deletedCount = 0
    files.forEach { file ->
        if (file.delete()) {
            deletedCount++
        }
    }
    return deletedCount
}

private fun localBrowserThumbnailCacheKey(file: File): String {
    val stamp = buildString {
        append(file.absolutePath)
        append('|')
        append(file.length())
        append('|')
        append(file.lastModified())
    }
    return "${sha1Hex(stamp)}.jpg"
}

private fun scaleBitmapForLocalBrowserThumbnail(
    bitmap: Bitmap,
    maxSize: Int
): Bitmap {
    val sourceWidth = bitmap.width.coerceAtLeast(1)
    val sourceHeight = bitmap.height.coerceAtLeast(1)
    if (sourceWidth <= maxSize && sourceHeight <= maxSize) return bitmap
    val scale = maxSize.toFloat() / maxOf(sourceWidth, sourceHeight).toFloat()
    val targetWidth = (sourceWidth * scale).toInt().coerceAtLeast(1)
    val targetHeight = (sourceHeight * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
}

private fun pruneLocalBrowserThumbnailCache(cacheRoot: File) {
    val files = cacheRoot.listFiles()
        ?.filter { it.isFile }
        ?.sortedByDescending { it.lastModified() }
        ?: return

    var totalBytes = 0L
    files.forEachIndexed { index, file ->
        totalBytes += file.length().coerceAtLeast(0L)
        val shouldDelete = index >= LOCAL_BROWSER_THUMBNAIL_CACHE_MAX_FILES ||
            totalBytes > LOCAL_BROWSER_THUMBNAIL_CACHE_MAX_BYTES
        if (shouldDelete) {
            totalBytes -= file.length().coerceAtLeast(0L)
            file.delete()
        }
    }
}
