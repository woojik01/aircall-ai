package com.woojik.aircallai

import com.woojik.aircallai.core.security.CryptoEngine
import com.woojik.aircallai.tools.*
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class MemoryAssignmentTest {
    private val crypto = object : CryptoEngine {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 85).toByte() }.toByteArray()
        override fun decrypt(blob: ByteArray): ByteArray = encrypt(blob)
    }

    @Test fun assignmentsShareExistingStoreAndPreserveIdsAfterRestart() = runTest {
        val dir = Files.createTempDirectory("memory-assignment").toFile()
        try {
            val file = File(dir, "entries.enc")
            val store = MemoryStore(file, crypto)
            val old = store.save("기존 자유 형식 기억")
            val initial = store.save("좋아하는색 = 파랑")
            val changed = store.save("좋아하는색=연한 파랑=blue")
            assertEquals(initial.id, changed.id)
            val restored = MemoryStore(file, crypto)
            val tool = MemoryTool(restored)
            assertEquals("좋아하는색=연한 파랑=blue", tool.execute(ToolRequest("memory", "get", mapOf("name" to "좋아하는색"))).message)
            assertEquals(2, restored.list().size)
            assertTrue(restored.entries.value.contains(old))
            restored.save("좋아하는색=초록", initial.id, changed.text)
            assertEquals("좋아하는색=초록", tool.execute(ToolRequest("memory", "get", mapOf("name" to "좋아하는색"))).message)
            assertTrue(tool.execute(ToolRequest("memory", "delete", mapOf("name" to "좋아하는색"))).success)
            assertEquals(listOf(old), restored.list())
        } finally { dir.deleteRecursively() }
    }

    @Test fun explicitAssignmentRequestUsesApprovalAndRealStorage() = runTest {
        val dir = Files.createTempDirectory("memory-assignment").toFile()
        try {
            val store = MemoryStore(File(dir, "entries.enc"), crypto)
            val call = MemoryRequests.initialCall("좋아하는색=파랑 기억해줘")!!
            assertEquals("set", call.action)
            val request = ToolRequest(call.toolName, call.action, call.arguments)
            val executor = ToolExecutor(listOf(MemoryTool(store)), InMemoryToolPermissionStore())
            assertFalse(executor.execute(request).success)
            assertTrue(store.list().isEmpty())
            assertTrue(executor.executeApproved(request).success)
            assertEquals("좋아하는색=파랑", store.list().single().text)
            assertEquals("add", MemoryRequests.initialCall("나는 파랑을 좋아해. 기억해줘")!!.action)
        } finally { dir.deleteRecursively() }
    }

    @Test fun concurrentSameNameWritesNeverCreateDuplicates() = runTest {
        val dir = Files.createTempDirectory("memory-assignment").toFile()
        try {
            val store = MemoryStore(File(dir, "entries.enc"), crypto)
            (1..20).map { async { store.save("key=value$it") } }.awaitAll()
            assertEquals(1, store.list().size)
        } finally { dir.deleteRecursively() }
    }

    @Test fun parsingRejectsInvalidNamesAndValues() {
        listOf("missing", "=value", "name=", "a\nb=value", "x".repeat(81) + "=value").forEach {
            assertNull(MemoryAssignment.parse(it))
        }
        assertEquals("수식" to "a=b", MemoryAssignment.parse("수식=a=b"))
    }
}
