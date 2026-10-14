package com.woojik.aircallai

import com.woojik.aircallai.core.security.CryptoEngine
import com.woojik.aircallai.tools.*
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class MemoryToolTest {
    // Reversible test-only codec; production receives AndroidKeystoreCrypto from AppGraph.
    private val crypto = object : CryptoEngine {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
        override fun decrypt(blob: ByteArray): ByteArray = encrypt(blob)
    }
    private fun file() = File.createTempFile("memory", ".enc").apply { delete(); deleteOnExit() }
    private fun set(value: String) = ToolRequest("memory", "set", mapOf("assignment" to value))

    @Test fun persistsUpdatesAndKeepsEqualsInValue() = runTest {
        val file = file()
        val tool = MemoryTool(FileMemoryStore(file, crypto))
        assertTrue(tool.execute(set("좋아하는색=파랑")).success)
        assertTrue(tool.execute(set("좋아하는색=연한 파랑=blue")).success)
        val reopened = MemoryTool(FileMemoryStore(file, crypto))
        assertEquals("좋아하는색=연한 파랑=blue", reopened.execute(ToolRequest("memory", "get", mapOf("name" to "좋아하는색"))).message)
        assertFalse(file.readText().contains("좋아하는색"))
        assertEquals("좋아하는색=연한 파랑=blue", reopened.execute(ToolRequest("memory", "list", mapOf("query" to "blue"))).message)
    }

    @Test fun invalidAssignmentsDoNotWrite() = runTest {
        val file = file()
        val tool = MemoryTool(FileMemoryStore(file, crypto))
        listOf("missing", "=value", "name=", "a\nb=value", "a=" + "x".repeat(4001)).forEach {
            assertFalse(tool.execute(set(it)).success)
        }
        assertFalse(file.exists())
    }

    @Test fun approvalAndDeletion() = runTest {
        val store = FileMemoryStore(file(), crypto)
        val tool = MemoryTool(store)
        val permissions = InMemoryToolPermissionStore()
        val executor = ToolExecutor(listOf(tool), permissions)
        assertFalse(executor.execute(set("theme=dark")).success)
        assertNull(store.get("theme"))
        permissions.setAllowed("memory", "set", true)
        assertTrue(executor.execute(set("theme=dark")).success)
        assertEquals("theme=dark", executor.execute(ToolRequest("memory", "get", mapOf("name" to "theme"))).message)
        val delete = ToolRequest("memory", "delete", mapOf("name" to "theme"))
        assertEquals(ToolRisk.DESTRUCTIVE, tool.riskFor("delete"))
        assertFalse(executor.execute(delete).success)
        assertEquals("dark", store.get("theme"))
        assertTrue(executor.executeApproved(delete).success)
        assertNull(store.get("theme"))
    }

    @Test fun concurrentWritesDoNotLoseEntries() = runTest {
        val store = FileMemoryStore(file(), crypto)
        (1..25).map { async { store.set("key$it", "value$it") } }.awaitAll()
        assertEquals(25, store.list("", 50).size)
    }

    @Test fun corruptFileIsPreserved() = runTest {
        val file = file().apply { writeText("broken snapshot") }
        val before = file.readBytes()
        try {
            FileMemoryStore(file, crypto).set("key", "value")
            fail("Corrupt memory must not be overwritten")
        } catch (_: org.json.JSONException) { }
        assertArrayEquals(before, file.readBytes())
    }
}
