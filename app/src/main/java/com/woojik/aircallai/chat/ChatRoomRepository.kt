package com.woojik.aircallai.chat

import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.core.security.CryptoEngine
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

data class ChatRoom(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "새 채팅",
    val messages: List<ChatMessage> = emptyList(),
    val renamed: Boolean = false,
)

/** Encrypted, atomic snapshots. A failed read never silently overwrites existing history. */
class ChatRoomStore(private val file: File, private val crypto: CryptoEngine) {
    fun read(): List<ChatRoom> {
        if (!file.exists()) return emptyList()
        val plain = crypto.decrypt(file.readBytes()) ?: error("대화 기록 복호화 실패")
        val array = JSONArray(plain.toString(Charsets.UTF_8))
        return List(array.length()) { index ->
            val room = array.getJSONObject(index)
            val messages = room.getJSONArray("messages")
            ChatRoom(room.getString("id"), room.getString("title"), List(messages.length()) { i ->
                val message = messages.getJSONObject(i)
                ChatMessage(ChatMessage.Role.valueOf(message.getString("role")), message.getString("content"))
            }, room.optBoolean("renamed"))
        }
    }

    fun write(rooms: List<ChatRoom>) {
        val array = JSONArray()
        rooms.forEach { room ->
            val messages = JSONArray()
            room.messages.forEach { messages.put(JSONObject().put("role", it.role.name).put("content", it.content)) }
            array.put(JSONObject().put("id", room.id).put("title", room.title)
                .put("renamed", room.renamed).put("messages", messages))
        }
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        try {
            java.io.FileOutputStream(temp).use {
                it.write(crypto.encrypt(array.toString().toByteArray(Charsets.UTF_8)))
                it.fd.sync()
            }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally { temp.delete() }
    }
}

/** Mutations run on the app's main scope; disk snapshots are serialized on IO. */
class ChatRoomRepository(private val store: ChatRoomStore, scope: CoroutineScope) {
    private val _rooms = MutableStateFlow<List<ChatRoom>>(emptyList())
    val rooms = _rooms.asStateFlow()
    private val _activeId = MutableStateFlow<String?>(null)
    val activeId = _activeId.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val loading = Mutex()
    private val writes = Channel<List<ChatRoom>>(Channel.CONFLATED)

    init {
        scope.launch(Dispatchers.IO) {
            for (snapshot in writes) {
                try { store.write(snapshot); _error.value = null }
                catch (_: Exception) { _error.value = "채팅 저장에 실패했습니다. 저장 공간을 확인해 주세요." }
            }
        }
    }

    suspend fun initialize() = loading.withLock {
        if (_ready.value) return@withLock
        try {
            _rooms.value = withContext(Dispatchers.IO) { store.read() }
            _ready.value = true
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (_: Exception) { _error.value = "저장된 채팅을 열지 못했습니다. 기존 파일은 보존됩니다." }
    }

    fun newRoom(): ChatRoom {
        check(_ready.value)
        val room = ChatRoom()
        _activeId.value = room.id
        publish(listOf(room) + _rooms.value)
        return room
    }

    fun select(id: String): ChatRoom? = _rooms.value.firstOrNull { it.id == id }?.also { _activeId.value = id }

    fun updateMessages(messages: List<ChatMessage>) {
        val id = _activeId.value ?: return
        publish(_rooms.value.map { room ->
            if (room.id != id) room else room.copy(
                messages = messages.toList(),
                title = if (room.renamed) room.title else messages.firstOrNull { it.role == ChatMessage.Role.USER }
                    ?.content?.replace('\n', ' ')?.take(36) ?: room.title,
            )
        })
    }

    fun rename(id: String, title: String) {
        val cleaned = title.trim().take(80)
        if (cleaned.isEmpty()) return
        publish(_rooms.value.map { if (it.id == id) it.copy(title = cleaned, renamed = true) else it })
    }

    fun delete(id: String) {
        if (_activeId.value == id) _activeId.value = null
        publish(_rooms.value.filterNot { it.id == id })
    }

    private fun publish(rooms: List<ChatRoom>) {
        _rooms.value = rooms
        writes.trySend(rooms)
    }
}
