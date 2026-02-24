package com.flopster101.siliconplayer

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

private const val NETWORK_NODE_TYPE_FOLDER = "folder"
private const val NETWORK_NODE_TYPE_REMOTE_SOURCE = "remote_source"

internal enum class NetworkNodeType {
    Folder,
    RemoteSource
}

internal data class NetworkNode(
    val id: Long,
    val parentId: Long?,
    val type: NetworkNodeType,
    val title: String,
    val source: String? = null,
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
                if (title.isBlank()) continue
                when (objectValue.optString("type", "")) {
                    NETWORK_NODE_TYPE_FOLDER -> {
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
                        if (source.isBlank()) continue
                        val metadataTitle = objectValue.optString("metadataTitle", "").trim().ifBlank { null }
                        val metadataArtist = objectValue.optString("metadataArtist", "").trim().ifBlank { null }
                        add(
                            NetworkNode(
                                id = id,
                                parentId = parentId,
                                type = NetworkNodeType.RemoteSource,
                                title = title,
                                source = source,
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
                objectValue
                    .put("type", NETWORK_NODE_TYPE_REMOTE_SOURCE)
                    .put("source", node.source.orEmpty())
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
        if (node.type != NetworkNodeType.RemoteSource || !samePath(node.source, sourceId)) {
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
