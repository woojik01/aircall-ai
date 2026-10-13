package com.woojik.aircallai.tools

import com.woojik.aircallai.core.security.CryptoEngine
import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.woojik.aircallai.ai.provider.ChatMessage

data class MemoryEntry(val id: String, val text: String)

data class MemoryConversation(val id: String, val title: String, val messages: List<ChatMessage>)
data class MemoryHistoryMessage(val room: String, val message: ChatMessage)

/** All rooms are readable; exclude only the active request, not other rooms' last turns. */
fun globalMemoryHistory(rooms: List<MemoryConversation>, activeId: String?): List<MemoryHistoryMessage> =
    rooms.flatMap { room ->
        val messages = if (room.id == activeId && room.messages.lastOrNull()?.role == ChatMessage.Role.USER)
            room.messages.dropLast(1) else room.messages
        messages.asReversed().filter { it.role != ChatMessage.Role.SYSTEM }.map { MemoryHistoryMessage(room.title, it) }
    }


/** App-private encrypted snapshot. A failed read must never overwrite saved memories. */
class MemoryStore(private val file: File, private val crypto: CryptoEngine) {
    private val mutex = Mutex()
    private val _entries = MutableStateFlow<List<MemoryEntry>>(emptyList())
    val entries = _entries.asStateFlow()

    suspend fun refresh() { list(limit = 100) }

    suspend fun list(query: String = "", limit: Int = 20): List<MemoryEntry> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val snapshot = read()
            _entries.value = snapshot
            snapshot.filter { it.text.contains(query, ignoreCase = true) }.takeLast(limit.coerceIn(1, 100))
        }
    }

    suspend fun save(text: String, id: String? = null, expectedText: String? = null): MemoryEntry = withContext(Dispatchers.IO) {
        val normalized = text.trim()
        require(normalized.isNotBlank() && normalized.toByteArray(Charsets.UTF_8).size <= 2048) { "기억은 1~2048바이트여야 합니다" }
        mutex.withLock {
            val entries = read().toMutableList()
            if (id == null) entries.firstOrNull { it.text == normalized }?.let { return@withLock it }
            val entry = MemoryEntry(id ?: UUID.randomUUID().toString(), normalized)
            if (id == null) {
                require(entries.size < 100) { "기억이 가득 찼습니다. 기존 기억을 정리하세요" }
                entries.add(entry)
            } else {
                val index = entries.indexOfFirst { it.id == id }
                require(index >= 0) { "해당 기억이 없습니다" }
                check(expectedText == null || entries[index].text == expectedText) { "기억이 변경되었습니다. 다시 열어 주세요" }
                entries.removeAt(index)
                entries.add(entry)
            }
            write(entries)
            entry
        }
    }

    suspend fun delete(id: String, expectedText: String? = null): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val entries = read()
            check(expectedText == null || entries.firstOrNull { it.id == id }?.text == expectedText) { "기억이 변경되었습니다. 다시 열어 주세요" }
            val kept = entries.filterNot { it.id == id }
            if (kept.size == entries.size) false else { write(kept); true }
        }
    }

    private fun read(): List<MemoryEntry> {
        if (!file.exists()) return emptyList()
        check(file.length() <= 512 * 1024) { "기억 파일이 너무 큽니다" }
        val plain = checkNotNull(crypto.decrypt(file.readBytes())) { "기억을 복호화하지 못했습니다" }
        return DataInputStream(ByteArrayInputStream(plain)).use { input ->
            check(input.readInt() == 1) { "지원하지 않는 기억 형식입니다" }
            val count = input.readInt()
            check(count in 0..100)
            List(count) { MemoryEntry(input.readUTF(), input.readUTF()) }.also { check(input.available() == 0) }
        }
    }

    private fun write(entries: List<MemoryEntry>) {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { output ->
            output.writeInt(1)
            output.writeInt(entries.size)
            entries.forEach { output.writeUTF(it.id); output.writeUTF(it.text) }
        }
        file.parentFile?.mkdirs()
        val pending = File(file.parentFile, file.name + ".tmp")
        try {
            FileOutputStream(pending).use { it.write(crypto.encrypt(buffer.toByteArray())); it.fd.sync() }
            Files.move(pending.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { pending.delete() }
        _entries.value = entries.toList()
    }
}

class MemoryTool(
    private val store: MemoryStore,
    private val conversation: () -> List<MemoryHistoryMessage> = { emptyList() },
) : Tool {
    override val name = "memory"
    override val description = "모든 채팅방이 공유하는 전역 기억 및 이전 발화 조회"
    override fun riskFor(action: String) = when (action) {
        "list", "search", "history" -> ToolRisk.READ
        "delete" -> ToolRisk.DESTRUCTIVE
        else -> ToolRisk.WRITE
    }

    override suspend fun execute(request: ToolRequest): ToolResult {
        fun required(key: String) = request.arguments[key]?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("필수 인자: $key")
        return when (request.action) {
            "history" -> {
                val query = request.arguments["query"].orEmpty()
                val offset = request.arguments["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val limit = (request.arguments["limit"]?.toIntOrNull() ?: 6).coerceIn(1, 20)
                val found = conversation().filter { it.message.content.contains(query, ignoreCase = true) }
                    .drop(offset).take(limit)
                ToolResult(true, found.joinToString("\n") { "[${it.room}] ${it.message.role}: ${it.message.content.take(500)}" }.ifEmpty { "이전 대화가 없습니다" })
            }
            "list", "search" -> {
                val query = if (request.action == "search") required("query") else ""
                val entries = store.list(query, request.arguments["limit"]?.toIntOrNull() ?: 20)
                ToolResult(true, entries.joinToString("\n") { "${it.id}: ${it.text}" }.ifEmpty { "저장된 기억이 없습니다" })
            }
            "add", "update" -> {
                val entry = store.save(required("text"), if (request.action == "update") required("id") else null)
                ToolResult(true, "${entry.id}: ${entry.text}")
            }
            "delete" -> {
                val removed = store.delete(required("id"))
                ToolResult(removed, if (removed) "기억을 삭제했습니다" else "해당 기억이 없습니다")
            }
            else -> ToolResult(false, "지원하지 않는 기억 작업입니다")
        }
    }
}
