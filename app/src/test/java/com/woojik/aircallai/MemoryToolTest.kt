package com.woojik.aircallai

import com.woojik.aircallai.core.security.CryptoEngine
import com.woojik.aircallai.tools.*
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class MemoryToolTest {
    private val crypto = object : CryptoEngine {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 85).toByte() }.toByteArray()
        override fun decrypt(blob: ByteArray): ByteArray? = encrypt(blob)
    }
    @Test fun encryptedCrudSurvivesRestartWithoutDuplicateMemories() = runTest {
        val dir = Files.createTempDirectory("memory-test").toFile()
        try {
            val file = File(dir, "entries.enc")
            val store = MemoryStore(file, crypto)
            val entry = store.save("좋아하는 색은 노란색")
            assertEquals(entry.id, store.save(entry.text).id)
            assertFalse(file.readText().contains(entry.text))
            val restored = MemoryStore(file, crypto)
            assertEquals(entry, restored.list("노란색").single())
            restored.save("좋아하는 색은 파란색", entry.id)
            assertTrue(restored.list("노란색").isEmpty())
            assertEquals("좋아하는 색은 파란색", restored.list().single().text)
            assertTrue(restored.delete(entry.id))
            assertTrue(MemoryStore(file, crypto).list().isEmpty())
            assertFalse(restored.delete(entry.id))
        } finally { dir.deleteRecursively() }
    }
    @Test fun unreadableMemoryCannotBeOverwritten() = runTest {
        val dir = Files.createTempDirectory("memory-test").toFile()
        try {
            val file = File(dir, "entries.enc").apply { writeText("damaged") }
            val before = file.readBytes()
            try { MemoryStore(file, crypto).save("new"); fail("Must reject corrupt snapshot") }
            catch (_: Exception) { }
            assertArrayEquals(before, file.readBytes())
        } finally { dir.deleteRecursively() }
    }
    @Test fun approvedToolsManageMemoryAndReadOlderTurns() = runTest {
        val dir = Files.createTempDirectory("memory-test").toFile()
        try {
            val store = MemoryStore(File(dir, "entries.enc"), crypto)
            val user = com.woojik.aircallai.ai.provider.ChatMessage.Role.USER
            val assistant = com.woojik.aircallai.ai.provider.ChatMessage.Role.ASSISTANT
            val messages = listOf(
                com.woojik.aircallai.ai.provider.ChatMessage(user, "내 이름은 유닉"),
                com.woojik.aircallai.ai.provider.ChatMessage(assistant, "안녕하세요"),
                com.woojik.aircallai.ai.provider.ChatMessage(user, "지금 질문"),
            )
            val executor = ToolExecutor(listOf(MemoryTool(store) { messages }), InMemoryToolPermissionStore())
            val saved = executor.executeApproved(ToolRequest("memory", "add", mapOf("text" to "노란색")))
            assertTrue(saved.success)
            val id = store.list().single().id
            assertTrue(executor.executeApproved(ToolRequest("memory", "update", mapOf("id" to id, "text" to "파란색"))).success)
            assertTrue(executor.execute(ToolRequest("memory", "search", mapOf("query" to "파란색"))).message.contains(id))
            val older = executor.execute(ToolRequest("memory", "history", mapOf("query" to "이름")))
            assertTrue(older.success)
            assertTrue(older.message.contains("유닉"))
            assertFalse(older.message.contains("지금 질문"))
            assertTrue(executor.executeApproved(ToolRequest("memory", "delete", mapOf("id" to id))).success)
            assertTrue(store.list().isEmpty())
        } finally { dir.deleteRecursively() }
    }
    @Test fun memoryWritesUseExistingApprovalGate() = runTest {
        val dir = Files.createTempDirectory("memory-test").toFile()
        try {
            val store = MemoryStore(File(dir, "entries.enc"), crypto)
            val executor = ToolExecutor(listOf(MemoryTool(store)), InMemoryToolPermissionStore())
            assertFalse(executor.execute(ToolRequest("memory", "add", mapOf("text" to "기억"))).success)
            assertTrue(store.list().isEmpty())
            assertTrue(executor.execute(ToolRequest("memory", "list")).success)
        } finally { dir.deleteRecursively() }
    }
}
