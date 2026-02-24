package com.flopster101.siliconplayer

import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.share.DiskShare
import com.rapid7.client.dcerpc.mssrvs.ServerService
import com.rapid7.client.dcerpc.transport.SMBTransportFactories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

internal data class SmbBrowserEntry(
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long
)

internal suspend fun listSmbDirectoryEntries(
    spec: SmbSourceSpec,
    pathInsideShare: String?
): Result<List<SmbBrowserEntry>> = withContext(Dispatchers.IO) {
    runCatching {
        val shareName = spec.share.trim()
        if (shareName.isBlank()) {
            throw IllegalStateException("SMB share name is required to list directories")
        }
        SMBClient().use { smbClient ->
            smbClient.connect(spec.host).use { connection ->
                authenticateSmbSession(connection, spec).use { session ->
                    val share = session.connectShare(shareName)
                    if (share !is DiskShare) {
                        share.close()
                        throw IllegalStateException("SMB share is not a disk share")
                    }
                    share.use { diskShare ->
                        val path = normalizeSmbPathForShare(pathInsideShare).orEmpty()
                        val rawEntries = diskShare.list(path)
                        rawEntries
                            .asSequence()
                            .mapNotNull { entry ->
                                val name = entry.fileName?.trim().orEmpty()
                                if (name.isBlank() || name == "." || name == "..") return@mapNotNull null
                                val attributes = entry.fileAttributes
                                val isDirectory = (attributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
                                val size = entry.endOfFile
                                SmbBrowserEntry(
                                    name = name,
                                    isDirectory = isDirectory,
                                    sizeBytes = if (isDirectory) 0L else size
                                )
                            }
                            .sortedWith(
                                compareBy<SmbBrowserEntry> { !it.isDirectory }
                                    .thenBy { it.name.lowercase(Locale.ROOT) }
                                    .thenBy { it.name }
                            )
                            .toList()
                    }
                }
            }
        }
    }
}

internal suspend fun listSmbHostShareEntries(
    spec: SmbSourceSpec
): Result<List<SmbBrowserEntry>> = withContext(Dispatchers.IO) {
    runCatching {
        SMBClient().use { smbClient ->
            smbClient.connect(spec.host).use { connection ->
                authenticateSmbSession(connection, spec).use { session ->
                    val transport = SMBTransportFactories.SRVSVC.getTransport(
                        session,
                        SmbConfig.createDefaultConfig()
                    )
                    val shares = ServerService(transport)
                        .getShares1()
                        .asSequence()
                        .filter { share ->
                            // Base share type 0 is disk tree. Hidden shares keep their base type.
                            (share.type and 0xFFFF) == 0
                        }
                        .mapNotNull { share ->
                            val name = share.netName?.trim().orEmpty()
                            if (name.isBlank()) return@mapNotNull null
                            SmbBrowserEntry(
                                name = name,
                                isDirectory = true,
                                sizeBytes = 0L
                            )
                        }
                        .sortedWith(
                            compareBy<SmbBrowserEntry> { it.name.lowercase(Locale.ROOT) }
                                .thenBy { it.name }
                        )
                        .toList()
                    shares
                }
            }
        }
    }
}

internal fun buildSmbEntrySourceSpec(
    rootSpec: SmbSourceSpec,
    pathInsideShare: String
): SmbSourceSpec {
    return rootSpec.copy(path = normalizeSmbPathForShare(pathInsideShare))
}
