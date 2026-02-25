package com.flopster101.siliconplayer

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

private const val NETWORK_NODE_TYPE_FOLDER = "folder"
private const val NETWORK_NODE_TYPE_REMOTE_SOURCE = "remote_source"
private const val NETWORK_SOURCE_KIND_GENERIC = "generic"
private const val NETWORK_SOURCE_KIND_SMB = "smb"

internal enum class NetworkNodeType {
    Folder,
    RemoteSource
}

internal enum class NetworkSourceKind {
    Generic,
    Smb
}

internal data class NetworkNode(
    val id: Long,
    val parentId: Long?,
    val type: NetworkNodeType,
    val title: String,
    val source: String? = null,
    val sourceKind: NetworkSourceKind = NetworkSourceKind.Generic,
    val smbHost: String? = null,
    val smbShare: String? = null,
    val smbPath: String? = null,
    val smbUsername: String? = null,
    val smbPassword: String? = null,
    val smbDiscoveredHostName: String? = null,
    val metadataTitle: String? = null,
    val metadataArtist: String? = null
)

internal fun nextNetworkNodeId(nodes: List<NetworkNode>): Long {
    return (nodes.maxOfOrNull { it.id } ?: 0L) + 1L
}

internal fun readNetworkNodes(prefs: SharedPreferences): List<NetworkNode> {
    val raw = prefs.getString(AppPreferenceKeys.NETWORK_SAVED_NODES, null) ?: return emptyList()
    return try {
        val array = JSONArray(raw)
        val parsed = buildList {
            for (index in 0 until array.length()) {
                val objectValue = array.optJSONObject(index) ?: continue
                val id = objectValue.optLong("id", -1L)
                if (id < 0L) continue
                val parentId = objectValue.optLong("parentId", -1L).takeIf { it >= 0L }
                val title = objectValue.optString("title", "").trim()
                when (objectValue.optString("type", "")) {
                    NETWORK_NODE_TYPE_FOLDER -> {
                        if (title.isBlank()) continue
                        add(
                            NetworkNode(
                                id = id,
                                parentId = parentId,
                                type = NetworkNodeType.Folder,
                                title = title
                            )
                        )
                    }

                    NETWORK_NODE_TYPE_REMOTE_SOURCE -> {
                        val source = objectValue.optString("source", "").trim()
                        var sourceKind = when (objectValue.optString("sourceKind", "").trim()) {
                            NETWORK_SOURCE_KIND_SMB -> NetworkSourceKind.Smb
                            else -> NetworkSourceKind.Generic
                        }
                        if (sourceKind == NetworkSourceKind.Generic && parseSmbSourceSpecFromInput(source) != null) {
                            sourceKind = NetworkSourceKind.Smb
                        }
                        val metadataTitle = objectValue.optString("metadataTitle", "").trim().ifBlank { null }
                        val metadataArtist = objectValue.optString("metadataArtist", "").trim().ifBlank { null }
                        val smbDiscoveredHostName = objectValue
                            .optString("smbDiscoveredHostName", "")
                            .trim()
                            .ifBlank { null }
                        val smbSpec = if (sourceKind == NetworkSourceKind.Smb) {
                            val host = objectValue.optString("smbHost", "").trim()
                            val share = objectValue.optString("smbShare", "").trim()
                            val path = objectValue.optString("smbPath", "").trim().ifBlank { null }
                            val username = objectValue.optString("smbUsername", "").trim().ifBlank { null }
                            val password = objectValue.optString("smbPassword", "").trim().ifBlank { null }
                            buildSmbSourceSpec(
                                host = host,
                                share = share,
                                path = path,
                                username = username,
                                password = password
                            ) ?: parseSmbSourceSpecFromInput(source)
                        } else {
                            null
                        }
                        val normalizedSource = if (sourceKind == NetworkSourceKind.Smb) {
                            smbSpec?.let(::buildSmbSourceId)
                        } else {
                            source.takeIf { it.isNotBlank() }
                        }
                        if (normalizedSource.isNullOrBlank()) continue
                        add(
                            NetworkNode(
                                id = id,
                                parentId = parentId,
                                type = NetworkNodeType.RemoteSource,
                                title = title,
                                source = normalizedSource,
                                sourceKind = sourceKind,
                                smbHost = smbSpec?.host,
                                smbShare = smbSpec?.share,
                                smbPath = smbSpec?.path,
                                smbUsername = smbSpec?.username,
                                smbPassword = smbSpec?.password,
                                smbDiscoveredHostName = smbDiscoveredHostName,
                                metadataTitle = metadataTitle,
                                metadataArtist = metadataArtist
                            )
                        )
                    }
                }
            }
        }
        val validIds = parsed.mapTo(HashSet(parsed.size)) { it.id }
        parsed.filter { node -> node.parentId == null || validIds.contains(node.parentId) }
    } catch (_: Exception) {
        emptyList()
    }
}

internal fun writeNetworkNodes(
    prefs: SharedPreferences,
    nodes: List<NetworkNode>
) {
    val array = JSONArray()
    nodes.forEach { node ->
        val objectValue = JSONObject()
            .put("id", node.id)
            .put("parentId", node.parentId ?: -1L)
            .put("title", node.title)
        when (node.type) {
            NetworkNodeType.Folder -> {
                objectValue.put("type", NETWORK_NODE_TYPE_FOLDER)
            }

            NetworkNodeType.RemoteSource -> {
                val resolvedSmbSpec = if (node.sourceKind == NetworkSourceKind.Smb) {
                    buildSmbSourceSpec(
                        host = node.smbHost.orEmpty(),
                        share = node.smbShare.orEmpty(),
                        path = node.smbPath,
                        username = node.smbUsername,
                        password = node.smbPassword
                    ) ?: node.source?.let(::parseSmbSourceSpecFromInput)
                } else {
                    null
                }
                val sourceToWrite = if (node.sourceKind == NetworkSourceKind.Smb) {
                    resolvedSmbSpec?.let(::buildSmbSourceId)
                } else {
                    node.source
                }.orEmpty()
                objectValue
                    .put("type", NETWORK_NODE_TYPE_REMOTE_SOURCE)
                    .put("source", sourceToWrite)
                    .put(
                        "sourceKind",
                        if (node.sourceKind == NetworkSourceKind.Smb) {
                            NETWORK_SOURCE_KIND_SMB
                        } else {
                            NETWORK_SOURCE_KIND_GENERIC
                        }
                    )
                    .put("smbHost", resolvedSmbSpec?.host.orEmpty())
                    .put("smbShare", resolvedSmbSpec?.share.orEmpty())
                    .put("smbPath", resolvedSmbSpec?.path.orEmpty())
                    .put("smbUsername", resolvedSmbSpec?.username.orEmpty())
                    .put("smbPassword", resolvedSmbSpec?.password.orEmpty())
                    .put("smbDiscoveredHostName", node.smbDiscoveredHostName.orEmpty())
                    .put("metadataTitle", node.metadataTitle.orEmpty())
                    .put("metadataArtist", node.metadataArtist.orEmpty())
            }
        }
        array.put(objectValue)
    }
    prefs.edit().putString(AppPreferenceKeys.NETWORK_SAVED_NODES, array.toString()).apply()
}

internal fun mergeNetworkSourceMetadata(
    nodes: List<NetworkNode>,
    sourceId: String,
    title: String?,
    artist: String?
): List<NetworkNode> {
    val normalizedTitle = title?.trim().takeUnless { it.isNullOrBlank() }
    val normalizedArtist = artist?.trim().takeUnless { it.isNullOrBlank() }
    if (normalizedTitle == null && normalizedArtist == null) return nodes
    var changed = false
    val updated = nodes.map { node ->
        val nodeSourceId = resolveNetworkNodeSourceId(node)
        if (node.type != NetworkNodeType.RemoteSource || !samePath(nodeSourceId, sourceId)) {
            return@map node
        }
        val resolvedTitle = normalizedTitle ?: node.metadataTitle
        val resolvedArtist = normalizedArtist ?: node.metadataArtist
        if (resolvedTitle == node.metadataTitle && resolvedArtist == node.metadataArtist) {
            return@map node
        }
        changed = true
        node.copy(
            metadataTitle = resolvedTitle,
            metadataArtist = resolvedArtist
        )
    }
    return if (changed) updated else nodes
}

internal fun formatNetworkFolderSummary(
    folderCount: Int,
    sourceCount: Int
): String {
    val parts = mutableListOf<String>()
    if (folderCount > 0) {
        parts += pluralizeNetworkCount(folderCount, "folder")
    }
    if (sourceCount > 0 || folderCount == 0) {
        parts += pluralizeNetworkCount(sourceCount, "source")
    }
    return parts.joinToString(" • ")
}

private fun pluralizeNetworkCount(count: Int, singular: String): String {
    val word = if (count == 1) singular else "${singular}s"
    return "$count $word"
}

internal fun resolveNetworkNodeSmbSpec(node: NetworkNode): SmbSourceSpec? {
    if (node.type != NetworkNodeType.RemoteSource || node.sourceKind != NetworkSourceKind.Smb) return null
    return buildSmbSourceSpec(
        host = node.smbHost.orEmpty(),
        share = node.smbShare.orEmpty(),
        path = node.smbPath,
        username = node.smbUsername,
        password = node.smbPassword
    ) ?: node.source?.let(::parseSmbSourceSpecFromInput)
}

internal fun resolveNetworkNodeSourceId(node: NetworkNode): String? {
    if (node.type != NetworkNodeType.RemoteSource) return null
    return if (node.sourceKind == NetworkSourceKind.Smb) {
        resolveNetworkNodeSmbSpec(node)?.let(::buildSmbSourceId)
    } else {
        node.source?.trim().takeUnless { it.isNullOrBlank() }
    }
}

internal fun resolveNetworkNodeOpenInput(node: NetworkNode): String? {
    if (node.type != NetworkNodeType.RemoteSource) return null
    return if (node.sourceKind == NetworkSourceKind.Smb) {
        resolveNetworkNodeSmbSpec(node)?.let(::buildSmbRequestUri)
    } else {
        node.source?.trim().takeUnless { it.isNullOrBlank() }
    }
}

internal fun resolveNetworkNodeDisplaySource(node: NetworkNode): String {
    return if (node.type == NetworkNodeType.RemoteSource && node.sourceKind == NetworkSourceKind.Smb) {
        resolveNetworkNodeSmbSpec(node)
            ?.let { spec ->
                val displayHost = normalizeSmbHostDisplayLabel(
                    node.smbDiscoveredHostName?.trim().takeUnless { it.isNullOrBlank() } ?: spec.host
                )
                buildSmbDisplayUri(spec.copy(host = displayHost))
            }
            .orEmpty()
    } else {
        node.source.orEmpty()
    }
}

internal fun resolveNetworkNodeDisplayTitle(node: NetworkNode): String {
    if (node.type == NetworkNodeType.Folder) return node.title
    if (node.type != NetworkNodeType.RemoteSource) return node.title
    if (node.sourceKind != NetworkSourceKind.Smb) {
        return node.title.ifBlank { node.source.orEmpty() }
    }

    val spec = resolveNetworkNodeSmbSpec(node)
    val explicitTitle = node.title.trim()
    val hasExplicitTitle = explicitTitle.isNotBlank() && !isLegacyAutoSmbTitle(explicitTitle, spec)
    if (hasExplicitTitle) {
        return explicitTitle
    }
    val hostLabel = normalizeSmbHostDisplayLabel(
        node.smbDiscoveredHostName?.trim().takeUnless { it.isNullOrBlank() }
            ?: spec?.host.orEmpty()
    )
    return if (spec == null) {
        node.source.orEmpty()
    } else if (spec.share.isBlank()) {
        hostLabel
    } else if (spec.path.isNullOrBlank()) {
        "$hostLabel/${spec.share}"
    } else {
        "$hostLabel/${spec.share}/${spec.path}"
    }
}

private fun isLegacyAutoSmbTitle(title: String, spec: SmbSourceSpec?): Boolean {
    if (spec == null) return false
    val normalizedTitle = title.trim()
    if (normalizedTitle.isBlank()) return false
    val autoCandidates = buildList {
        add(spec.host)
        if (spec.share.isNotBlank()) {
            add("${spec.host}/${spec.share}")
            if (!spec.path.isNullOrBlank()) {
                add("${spec.host}/${spec.share}/${spec.path}")
            }
        }
    }
    return autoCandidates.any { candidate ->
        normalizedTitle.equals(candidate, ignoreCase = true)
    }
}

private fun normalizeSmbHostDisplayLabel(rawHost: String): String {
    val host = rawHost.trim()
    if (host.isBlank()) return host
    val withoutLocal = host.removeSuffix(".local").removeSuffix(".LOCAL").trimEnd('.')
    return withoutLocal.ifBlank { host }
}
