package com.woojik.aircallai

import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.core.security.CryptoEngine
import com.woojik.aircallai.settings.InMemorySettingsStore
import com.woojik.aircallai.settings.SettingsRepository
import com.woojik.aircallai.tools.*
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class GlobalMemoryTest {
    private val crypto = object : CryptoEngine {
        override fun encrypt(plain: ByteArray) = plain
        override fun decrypt(blob: ByteArray) = blob
    }
    private fun user(text: String) = ChatMessage(ChatMessage.Role.USER, text)
    private fun assistant(text: String) = ChatMessage(ChatMessage.Role.ASSISTANT, text)

    @Test fun historySearchReadsOtherRoomsAndPreservesTheirLastMessage() = runTest {
        val rooms = listOf(
            MemoryConversation("new", "새 채팅", listOf(user("지금 질문"))),
            MemoryConversation("old", "이전 채팅", listOf(user("내 이름은 유닉"), assistant("안녕하세요"))),
        )
        val history = globalMemoryHistory(rooms, "new")
        assertEquals(2, history.size)
        assertEquals("안녕하세요", history.first().message.content)
        val dir = Files.createTempDirectory("global-memory").toFile()
        try {
            val store = MemoryStore(File(dir, "entries.enc"), crypto)
            val tool = MemoryTool(store) { history }
            assertTrue(tool.execute(ToolRequest("memory", "history", mapOf("query" to "유닉"))).message.contains("[이전 채팅]"))
            assertTrue(tool.execute(ToolRequest("memory", "history", mapOf("offset" to "1", "limit" to "1"))).message.contains("유닉"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun userEditsAndAiEditsUpdateTheSameObservableGlobalEntries() = runTest {
        val dir = Files.createTempDirectory("global-memory").toFile()
        try {
            val store = MemoryStore(File(dir, "entries.enc"), crypto)
            val entry = store.save("  노란색  ")
            assertEquals("노란색", store.entries.value.single().text)
            val ai = MemoryTool(store)
            assertTrue(ai.execute(ToolRequest("memory", "update", mapOf("id" to entry.id, "text" to "파란색"))).success)
            assertEquals("파란색", store.entries.value.single().text)
            try { store.save("오래된 수정", entry.id, entry.text); fail("stale edit must fail") }
            catch (_: IllegalStateException) { }
            try { store.delete(entry.id, entry.text); fail("stale deletion must fail") }
            catch (_: IllegalStateException) { }
            store.save("초록색", entry.id, "파란색")
            assertTrue(ai.execute(ToolRequest("memory", "list")).message.contains("초록색"))
            store.delete(entry.id, "초록색")
            assertTrue(store.entries.value.isEmpty())
            val reopened = MemoryStore(File(dir, "entries.enc"), crypto)
            reopened.refresh()
            assertTrue(reopened.entries.value.isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun memoryMentionsWithoutActionsDoNotRequestMemoryChanges() {
        assertFalse("memory" in ToolIntent.toolsRequestedIn("How much GPU memory do I have?"))
        assertTrue("memory" in ToolIntent.toolsRequestedIn("save this to memory"))
        assertTrue("memory" in ToolIntent.toolsRequestedIn("list memories"))
        assertTrue("memory" in ToolIntent.toolsRequestedIn("기억을 삭제해"))
    }

    @Test fun oldCloudConsentDoesNotAuthorizeGlobalMemoryDisclosure() {
        val prefs = InMemorySettingsStore()
        val settings = SettingsRepository(prefs)
        prefs.putString("privacy_cloud_v1", settings.cloudBaseUrl())
        assertFalse(settings.hasCloudDisclosure())
        settings.acceptCloudDisclosure()
        assertTrue(settings.hasCloudDisclosure())
    }
}
