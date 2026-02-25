package com.flopster101.siliconplayer

import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.share.DiskShare
import com.rapid7.client.dcerpc.mssrvs.ServerService
import com.rapid7.client.dcerpc.transport.SMBTransportFactories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.nio.charset.StandardCharsets
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

internal suspend fun resolveSmbHostDisplayName(
    spec: SmbSourceSpec
): Result<String?> = withContext(Dispatchers.IO) {
    runCatching {
        val dnsHost = runCatching { InetAddress.getByName(spec.host) }.getOrNull()
        val candidates = linkedSetOf<String>()

        val remoteAddressForNbns = resolveIpv4Address(spec.host)
            ?: dnsHost?.hostAddress
            ?: spec.host
        queryNetBiosNodeStatusName(remoteAddressForNbns)?.let(candidates::add)
        sequenceOf(
            dnsHost?.hostName,
            dnsHost?.canonicalHostName,
            spec.host
        ).forEach { raw ->
            normalizeDiscoveredHostCandidate(raw)?.let(candidates::add)
        }

        runCatching {
            SMBClient().use { smbClient ->
                smbClient.connect(spec.host).use { connection ->
                    authenticateSmbSession(connection, spec).use { session ->
                        val probeShare = spec.share.ifBlank { "IPC$" }
                        runCatching { session.connectShare(probeShare).close() }
                        val context = connection.connectionContext
                        sequenceOf(
                            context.serverName,
                            context.netBiosName,
                            connection.remoteHostname
                        ).forEach { raw ->
                            normalizeDiscoveredHostCandidate(raw)?.let(candidates::add)
                        }
                        querySrvsvcCanonicalHostName(session, spec)?.let(candidates::add)
                    }
                }
            }
        }
        candidates.firstOrNull()
    }
}

private fun normalizeDiscoveredHostCandidate(raw: String?): String? {
    val cleaned = raw
        ?.trim()
        ?.removeSuffix("\u0000")
        ?.trim()
        ?.trimStart('[')
        ?.trimEnd(']')
        ?.takeUnless { it.isBlank() }
        ?: return null
    val withoutLocal = cleaned.removeSuffix(".local").removeSuffix(".LOCAL").trimEnd('.')
    val candidate = withoutLocal.ifBlank { cleaned }
    return candidate.takeUnless(::looksLikeIpLiteral)
}

private fun looksLikeIpLiteral(value: String): Boolean {
    val host = value.trim()
    if (host.isBlank()) return false
    if (host.matches(Regex("^\\d{1,3}(?:\\.\\d{1,3}){3}$"))) return true
    if (host.contains(':')) {
        val simplified = host.removePrefix("[").removeSuffix("]")
        return simplified.matches(Regex("^[0-9a-fA-F:.%]+$"))
    }
    return false
}

private fun queryNetBiosNodeStatusName(hostOrIp: String): String? {
    val targetAddress = runCatching { InetAddress.getByName(hostOrIp) }.getOrNull() ?: return null
    val request = buildNetBiosNodeStatusRequest()
    val socket = DatagramSocket()
    return try {
        socket.soTimeout = 1200
        socket.send(DatagramPacket(request, request.size, targetAddress, 137))
        val responseBuffer = ByteArray(576)
        val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
        socket.receive(responsePacket)
        parseNetBiosNodeStatusName(responseBuffer, responsePacket.length)
    } catch (_: Throwable) {
        null
    } finally {
        socket.close()
    }
}

private fun resolveIpv4Address(host: String): String? {
    val candidates = runCatching { InetAddress.getAllByName(host) }.getOrNull().orEmpty()
    return candidates.firstOrNull { it is Inet4Address }?.hostAddress
}

private fun querySrvsvcCanonicalHostName(
    session: com.hierynomus.smbj.session.Session,
    spec: SmbSourceSpec
): String? {
    val transport = runCatching {
        SMBTransportFactories.SRVSVC.getTransport(
            session,
            SmbConfig.createDefaultConfig()
        )
    }.getOrNull() ?: return null
    val service = runCatching { ServerService(transport) }.getOrNull() ?: return null
    val queryCandidates = listOf(
        "\\\\${spec.host}",
        "\\\\${spec.host}\\",
        spec.host
    )
    queryCandidates.forEach { query ->
        val canonical = runCatching {
            service.getCanonicalizedName(
                query,
                "",
                "",
                1024,
                0,
                0
            )
        }.getOrNull()
            ?.trim()
            .orEmpty()
        if (canonical.isBlank()) return@forEach
        val normalizedCanonical = canonical
            .replace('\\', '/')
            .trim('/')
        val hostPart = normalizedCanonical.substringBefore('/').trim()
        normalizeDiscoveredHostCandidate(hostPart)?.let { return it }
        normalizeDiscoveredHostCandidate(canonical)?.let { return it }
    }
    return null
}

private fun buildNetBiosNodeStatusRequest(): ByteArray {
    val packet = ByteArray(50)
    val txId = (System.nanoTime().toInt() and 0xFFFF)
    packet[0] = ((txId ushr 8) and 0xFF).toByte()
    packet[1] = (txId and 0xFF).toByte()
    packet[2] = 0x00
    packet[3] = 0x00
    packet[4] = 0x00
    packet[5] = 0x01 // QDCOUNT
    packet[6] = 0x00
    packet[7] = 0x00 // ANCOUNT
    packet[8] = 0x00
    packet[9] = 0x00 // NSCOUNT
    packet[10] = 0x00
    packet[11] = 0x00 // ARCOUNT
    packet[12] = 0x20 // NetBIOS first-level encoded name length
    val encodedName = encodeNetBiosNameForNodeStatus()
    System.arraycopy(encodedName, 0, packet, 13, encodedName.size)
    packet[45] = 0x00 // name terminator
    packet[46] = 0x00
    packet[47] = 0x21 // NBSTAT
    packet[48] = 0x00
    packet[49] = 0x01 // IN class
    return packet
}

private fun encodeNetBiosNameForNodeStatus(): ByteArray {
    val name = ByteArray(16) { 0x20 }
    name[0] = '*'.code.toByte()
    name[15] = 0x00
    val encoded = ByteArray(32)
    for (index in name.indices) {
        val value = name[index].toInt() and 0xFF
        encoded[index * 2] = ('A'.code + ((value ushr 4) and 0x0F)).toByte()
        encoded[index * 2 + 1] = ('A'.code + (value and 0x0F)).toByte()
    }
    return encoded
}

private fun parseNetBiosNodeStatusName(buffer: ByteArray, length: Int): String? {
    if (length < 12) return null
    val qdCount = readU16(buffer, 4)
    val anCount = readU16(buffer, 6)
    var offset = 12
    repeat(qdCount) {
        offset = skipDnsName(buffer, length, offset)
        if (offset < 0 || offset + 4 > length) return null
        offset += 4
    }
    repeat(anCount) {
        offset = skipDnsName(buffer, length, offset)
        if (offset < 0 || offset + 10 > length) return null
        val type = readU16(buffer, offset)
        offset += 2
        offset += 2 // class
        offset += 4 // ttl
        val rdLength = readU16(buffer, offset)
        offset += 2
        if (offset + rdLength > length) return null
        if (type == 0x0021 && rdLength > 1) {
            val namesCount = buffer[offset].toInt() and 0xFF
            var nameOffset = offset + 1
            repeat(namesCount) {
                if (nameOffset + 18 > offset + rdLength) return@repeat
                val rawName = String(buffer, nameOffset, 15, StandardCharsets.US_ASCII).trim()
                val suffix = buffer[nameOffset + 15].toInt() and 0xFF
                val flags = readU16(buffer, nameOffset + 16)
                val isGroup = (flags and 0x8000) != 0
                if (!isGroup && suffix == 0x00 && rawName.isNotBlank()) {
                    return normalizeDiscoveredHostCandidate(rawName)
                }
                nameOffset += 18
            }
        }
        offset += rdLength
    }
    return null
}

private fun skipDnsName(buffer: ByteArray, length: Int, startOffset: Int): Int {
    var offset = startOffset
    while (offset < length) {
        val labelLength = buffer[offset].toInt() and 0xFF
        if (labelLength == 0) {
            return offset + 1
        }
        if ((labelLength and 0xC0) == 0xC0) {
            return offset + 2
        }
        offset += 1 + labelLength
    }
    return -1
}

private fun readU16(buffer: ByteArray, offset: Int): Int {
    return ((buffer[offset].toInt() and 0xFF) shl 8) or
        (buffer[offset + 1].toInt() and 0xFF)
}

internal fun buildSmbEntrySourceSpec(
    rootSpec: SmbSourceSpec,
    pathInsideShare: String
): SmbSourceSpec {
    return rootSpec.copy(path = normalizeSmbPathForShare(pathInsideShare))
}
