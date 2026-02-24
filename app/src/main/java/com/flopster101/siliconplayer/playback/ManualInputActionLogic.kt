package com.flopster101.siliconplayer

import java.io.File

internal const val MANUAL_INPUT_INVALID_MESSAGE =
    "Enter a valid file/folder path, file:// path, http(s) URL, or smb:// source"

internal sealed class ManualInputAction {
    data class OpenDirectory(
        val directoryPath: String,
        val locationId: String?
    ) : ManualInputAction()

    data class OpenLocalFile(
        val file: File,
        val sourceId: String
    ) : ManualInputAction()

    data class OpenRemote(
        val resolved: ManualSourceResolution
    ) : ManualInputAction()

    data object Invalid : ManualInputAction()
}

internal fun resolveManualInputAction(
    rawInput: String,
    storageDescriptors: List<StorageDescriptor>
): ManualInputAction {
    val resolved = resolveManualSourceInput(rawInput) ?: return ManualInputAction.Invalid

    return when (resolved.type) {
        ManualSourceType.LocalDirectory -> {
            val directoryPath = resolved.directoryPath ?: return ManualInputAction.Invalid
            ManualInputAction.OpenDirectory(
                directoryPath = directoryPath,
                locationId = resolveStorageLocationForPath(directoryPath, storageDescriptors)
            )
        }

        ManualSourceType.LocalFile -> {
            val localFile = resolved.localFile ?: return ManualInputAction.Invalid
            ManualInputAction.OpenLocalFile(
                file = localFile,
                sourceId = resolved.sourceId
            )
        }

        ManualSourceType.RemoteUrl -> {
            ManualInputAction.OpenRemote(resolved)
        }

        ManualSourceType.Smb -> {
            ManualInputAction.OpenRemote(resolved)
        }
    }
}
