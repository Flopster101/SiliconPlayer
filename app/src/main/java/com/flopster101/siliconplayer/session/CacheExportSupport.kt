package com.flopster101.siliconplayer.session

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.flopster101.siliconplayer.guessMimeTypeFromFilename
import com.flopster101.siliconplayer.sanitizeRemoteLeafName
import com.flopster101.siliconplayer.stripRemoteCacheHashPrefix
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class CacheExportResult(
    val exportedCount: Int,
    val failedCount: Int,
    val skippedCount: Int = 0,
    val cancelled: Boolean = false,
    val invalidDestination: Boolean = false
)

internal data class ExportFileItem(
    val sourceFile: File,
    val displayNameOverride: String? = null
)

internal enum class ExportConflictAction {
    Overwrite,
    Skip,
    Cancel
}

internal data class ExportConflictDecision(
    val action: ExportConflictAction,
    val applyToAll: Boolean = false
)

internal data class ExportNameConflict(
    val fileName: String
)

internal suspend fun exportCachedFilesToTree(
    context: Context,
    treeUri: Uri,
    selectedPaths: List<String>,
    onNameConflict: (suspend (ExportNameConflict) -> ExportConflictDecision)? = null
): CacheExportResult {
    val exportItems = selectedPaths.distinct().map { absolutePath ->
        val sourceFile = File(absolutePath)
        ExportFileItem(
            sourceFile = sourceFile,
            displayNameOverride = stripRemoteCacheHashPrefix(sourceFile.name)
        )
    }
    return exportFilesToTree(
        context = context,
        treeUri = treeUri,
        exportItems = exportItems,
        onNameConflict = onNameConflict
    )
}

internal suspend fun exportFilesToTree(
    context: Context,
    treeUri: Uri,
    exportItems: List<ExportFileItem>,
    onNameConflict: (suspend (ExportNameConflict) -> ExportConflictDecision)? = null
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
        failedCount = exportItems.distinctBy { it.sourceFile.absolutePath }.size,
        invalidDestination = true
    )

    val childDocumentsByName = queryChildDocumentsByName(
        context = context,
        treeUri = treeUri,
        parentDocumentUri = parentDocumentUri
    )

    var exported = 0
    var failed = 0
    var skipped = 0
    var rememberedConflictAction: ExportConflictAction? = null

    exportItems
        .distinctBy { it.sourceFile.absolutePath }
        .forEach { exportItem ->
            val sourceFile = exportItem.sourceFile
            if (!sourceFile.exists() || !sourceFile.isFile) {
                failed++
                return@forEach
            }

            val requestedName = exportItem.displayNameOverride
                ?.trim()
                .takeUnless { it.isNullOrBlank() }
                ?: sourceFile.name
            val baseName = sanitizeRemoteLeafName(requestedName)
                ?.takeUnless { it.isBlank() }
                ?: sourceFile.name
            val mimeType = guessMimeTypeFromFilename(baseName)
            val existingDocumentUri = childDocumentsByName[baseName]

            val destinationUri: Uri? = if (existingDocumentUri == null) {
                createDestinationDocument(
                    context = context,
                    parentDocumentUri = parentDocumentUri,
                    mimeType = mimeType,
                    displayName = baseName
                )?.also { createdUri ->
                    childDocumentsByName[baseName] = createdUri
                }
            } else {
                if (onNameConflict == null) {
                    val autoRenameName = nextAvailableName(baseName, childDocumentsByName.keys)
                    if (autoRenameName == null) {
                        null
                    } else {
                        createDestinationDocument(
                            context = context,
                            parentDocumentUri = parentDocumentUri,
                            mimeType = mimeType,
                            displayName = autoRenameName
                        )?.also { createdUri ->
                            childDocumentsByName[autoRenameName] = createdUri
                        }
                    }
                } else {
                    val action = rememberedConflictAction ?: run {
                        val decision = onNameConflict.invoke(ExportNameConflict(fileName = baseName))
                        if (decision.applyToAll) {
                            rememberedConflictAction = decision.action
                        }
                        decision.action
                    }
                    when (action) {
                        ExportConflictAction.Overwrite -> existingDocumentUri
                        ExportConflictAction.Skip -> {
                            skipped++
                            null
                        }

                        ExportConflictAction.Cancel -> {
                            return CacheExportResult(
                                exportedCount = exported,
                                failedCount = failed,
                                skippedCount = skipped,
                                cancelled = true,
                                invalidDestination = false
                            )
                        }
                    }
                }
            }

            if (destinationUri == null) {
                if (existingDocumentUri == null) {
                    failed++
                }
                return@forEach
            }

            val copied = copyFileToDocument(
                context = context,
                sourceFile = sourceFile,
                destinationUri = destinationUri
            )
            if (copied) exported++ else failed++
        }

    return CacheExportResult(
        exportedCount = exported,
        failedCount = failed,
        skippedCount = skipped,
        cancelled = false,
        invalidDestination = false
    )
}

private suspend fun queryChildDocumentsByName(
    context: Context,
    treeUri: Uri,
    parentDocumentUri: Uri
): MutableMap<String, Uri> = withContext(Dispatchers.IO) {
    val result = linkedMapOf<String, Uri>()
    val parentDocumentId = runCatching {
        DocumentsContract.getDocumentId(parentDocumentUri)
    }.getOrNull() ?: return@withContext result

    val childrenUri = runCatching {
        DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
    }.getOrNull() ?: return@withContext result

    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME
    )
    runCatching {
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            if (idColumn < 0 || nameColumn < 0) return@use
            while (cursor.moveToNext()) {
                val childDocumentId = cursor.getString(idColumn) ?: continue
                val displayName = cursor.getString(nameColumn) ?: continue
                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocumentId)
                result[displayName] = childUri
            }
        }
    }
    result
}

private suspend fun createDestinationDocument(
    context: Context,
    parentDocumentUri: Uri,
    mimeType: String,
    displayName: String
): Uri? = withContext(Dispatchers.IO) {
    runCatching {
        DocumentsContract.createDocument(
            context.contentResolver,
            parentDocumentUri,
            mimeType,
            displayName
        )
    }.getOrNull()
}

private suspend fun copyFileToDocument(
    context: Context,
    sourceFile: File,
    destinationUri: Uri
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openOutputStream(destinationUri, "wt")?.use { output ->
            sourceFile.inputStream().use { input ->
                input.copyTo(output)
            }
        } != null
    }.getOrDefault(false)
}

private fun nextAvailableName(
    baseName: String,
    existingNames: Collection<String>
): String? {
    if (!existingNames.contains(baseName)) return baseName
    val dotIndex = baseName.lastIndexOf('.')
    val stem = if (dotIndex > 0) baseName.substring(0, dotIndex) else baseName
    val ext = if (dotIndex > 0) baseName.substring(dotIndex) else ""
    for (attempt in 1..999) {
        val candidate = "$stem ($attempt)$ext"
        if (!existingNames.contains(candidate)) {
            return candidate
        }
    }
    return null
}
