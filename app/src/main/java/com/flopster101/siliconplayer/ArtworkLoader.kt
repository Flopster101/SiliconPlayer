package com.flopster101.siliconplayer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

internal fun loadArtworkForFile(file: File): ImageBitmap? {
    loadEmbeddedArtwork(file)?.let { return it.asImageBitmap() }
    findFolderArtworkFile(file)?.let { folderImage ->
        decodeScaledBitmapFromFile(folderImage)?.let { return it.asImageBitmap() }
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
