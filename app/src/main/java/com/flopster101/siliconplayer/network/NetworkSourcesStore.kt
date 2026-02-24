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
    val source: String? = null
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
                        add(
                            NetworkNode(
                                id = id,
                                parentId = parentId,
                                type = NetworkNodeType.RemoteSource,
                                title = title,
                                source = source
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
            }
        }
        array.put(objectValue)
    }
    prefs.edit().putString(AppPreferenceKeys.NETWORK_SAVED_NODES, array.toString()).apply()
}
