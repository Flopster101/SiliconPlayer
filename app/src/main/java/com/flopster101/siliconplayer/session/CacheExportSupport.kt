package com.flopster101.siliconplayer.session

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.flopster101.siliconplayer.guessMimeTypeFromFilename
import java.io.File

internal data class CacheExportResult(
    val exportedCount: Int,
    val failedCount: Int,
    val invalidDestination: Boolean = false
)

internal suspend fun exportCachedFilesToTree(
    context: Context,
    treeUri: Uri,
    selectedPaths: List<String>
): CacheExportResult {
    val parentDocumentUri = try {
        DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
    } catch (_: Throwable) {
        null
    } ?: return CacheExportResult(
        exportedCount = 0,
        failedCount = selectedPaths.distinct().size,
        invalidDestination = true
    )

    var exported = 0
    var failed = 0
    val hashPrefixRegex = Regex("^[0-9a-fA-F]{40}_(.+)$")
    selectedPaths.distinct().forEach { absolutePath ->
        val sourceFile = File(absolutePath)
        if (!sourceFile.exists() || !sourceFile.isFile) {
            failed++
            return@forEach
        }

        val baseName = hashPrefixRegex.matchEntire(sourceFile.name)?.groupValues?.getOrNull(1)
            ?: sourceFile.name
        val dotIndex = baseName.lastIndexOf('.')
        val stem = if (dotIndex > 0) baseName.substring(0, dotIndex) else baseName
        val ext = if (dotIndex > 0) baseName.substring(dotIndex) else ""
        val mimeType = guessMimeTypeFromFilename(baseName)
        var destinationUri: Uri? = null
        for (attempt in 0..24) {
            val candidateName = if (attempt == 0) baseName else "$stem ($attempt)$ext"
            destinationUri = try {
                DocumentsContract.createDocument(
                    context.contentResolver,
                    parentDocumentUri,
                    mimeType,
                    candidateName
                )
            } catch (_: Throwable) {
                null
            }
            if (destinationUri != null) break
        }

        if (destinationUri == null) {
            failed++
            return@forEach
        }

        val copied = try {
            context.contentResolver.openOutputStream(destinationUri, "w")?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } != null
        } catch (_: Throwable) {
            false
        }
        if (copied) exported++ else failed++
    }

    return CacheExportResult(
        exportedCount = exported,
        failedCount = failed
    )
}
