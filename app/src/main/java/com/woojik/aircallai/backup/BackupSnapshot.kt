package com.woojik.aircallai.backup

import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.chat.ChatRoom
import org.json.JSONArray
import org.json.JSONObject

data class BackupSnapshot(val rooms: List<ChatRoom>, val notes: List<String>, val memory: String,
    val preferences: Map<String, String> = emptyMap()) {
    fun encode(): ByteArray {
        val chats = JSONArray()
        rooms.filter { it.hasConversation }.forEach { room ->
            val messages = JSONArray()
            room.messages.filter { it.role != ChatMessage.Role.SYSTEM }.forEach {
                messages.put(JSONObject().put("role", it.role.name).put("content", it.content))
            }
            chats.put(JSONObject().put("id", room.id).put("title", room.title).put("renamed", room.renamed).put("messages", messages))
        }
        val result = JSONObject().put("format", "aircall-data-v1").put("chats", chats).put("notes", JSONArray(notes))
            .put("memory", memory).put("preferences", JSONObject(preferences.filterKeys { it in ALLOWED_PREFERENCES })).toString().toByteArray(Charsets.UTF_8)
        require(result.size <= BackupCodec.MAX_BYTES)
        return result
    }
    companion object {
        val ALLOWED_PREFERENCES = setOf("theme", "aiMode", "endpoint", "model", "gpu", "streaming", "nativeTools")
        fun decode(bytes: ByteArray): BackupSnapshot {
            require(bytes.size <= BackupCodec.MAX_BYTES)
            val root = JSONObject(bytes.toString(Charsets.UTF_8))
            require(root.getString("format") == "aircall-data-v1")
            val chats = root.getJSONArray("chats")
            require(chats.length() <= 10_000)
            val rooms = List(chats.length()) { index ->
                val chat = chats.getJSONObject(index)
                val id = chat.getString("id")
                val title = chat.getString("title")
                require(id.length in 1..128 && title.length <= 80)
                val entries = chat.getJSONArray("messages")
                require(entries.length() <= 100_000)
                val messages = List(entries.length()) { i ->
                    val message = entries.getJSONObject(i)
                    val role = ChatMessage.Role.valueOf(message.getString("role"))
                    require(role != ChatMessage.Role.SYSTEM)
                    ChatMessage(role, message.getString("content"))
                }
                ChatRoom(id, title, messages, chat.optBoolean("renamed"))
            }
            val items = root.getJSONArray("notes")
            require(items.length() <= 100_000)
            val notes = List(items.length()) { items.getString(it).also { line -> require(!line.contains('\n') && !line.contains('\r')) } }
            val memory = root.optString("memory").also { require(it.length <= 150) }
            val settings = root.optJSONObject("preferences") ?: JSONObject()
            val preferences = settings.keys().asSequence().filter { it in ALLOWED_PREFERENCES }.associateWith { settings.getString(it) }
            require(preferences["theme"] == null || preferences["theme"] in setOf("light", "dark", "system"))
            require(preferences["aiMode"] == null || preferences["aiMode"] in setOf("local", "cloud"))
            listOf("gpu", "streaming", "nativeTools").forEach { name ->
                require(preferences[name] == null || preferences[name] in setOf("true", "false"))
            }
            require(preferences["model"].orEmpty().length <= 200)
            preferences["endpoint"]?.let { require(com.woojik.aircallai.privacy.HttpsEndpoint.isHttpsEndpoint(it)) }
            return BackupSnapshot(rooms, notes, memory, preferences)
        }
    }
}
