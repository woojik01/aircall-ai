package com.woojik.aircallai

import com.woojik.aircallai.ai.local.localInferenceHistory
import com.woojik.aircallai.ai.provider.*
import com.woojik.aircallai.conversation.ConversationEngine
import com.woojik.aircallai.core.security.CryptoEngine
import com.woojik.aircallai.privacy.SafetyAIProvider
import com.woojik.aircallai.tools.*
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class MemoryActionFlowTest {
    private val crypto = object : CryptoEngine {
        override fun encrypt(plain: ByteArray) = plain
        override fun decrypt(blob: ByteArray) = blob
    }
    private val catalog = """
        memory.history [query=VALUE] [offset=VALUE] [limit=VALUE] — 전체 대화
        memory.list [limit=VALUE] — 기억 조회
        memory.search query=VALUE [limit=VALUE] — 검색
        memory.add text=VALUE — 저장
        memory.update id=VALUE text=VALUE — 수정
        memory.delete id=VALUE — 삭제
        github.read_file owner=VALUE repo=VALUE path=VALUE — 파일
        notes.list_notes [limit=VALUE] — 메모
        gmail.send_email to=VALUE subject=VALUE body=VALUE — 메일
    """.trimIndent()

    @Test fun explicitSaveUsesActualApprovalAndPersistenceWithoutModelToolSyntax() = runTest {
        val dir = Files.createTempDirectory("memory-flow").toFile()
        try {
            for (type in listOf(ProviderType.LOCAL, ProviderType.CLOUD)) {
                val store = MemoryStore(File(dir, "$type.enc"), crypto)
                val tool = MemoryTool(store)
                val permissions = InMemoryToolPermissionStore()
                val executor = ToolExecutor(listOf(tool), permissions)
                val approval = ToolApprovalCoordinator(permissions, executor, this)
                val backend = object : AIProvider {
                    override val type = type
                    override val displayName = "no tool syntax"
                    override suspend fun isReady() = true
                    override suspend fun respond(history: List<ChatMessage>): AIResponse = error("Saving must not depend on model calls")
                }
                val bridge = ToolBridgedAIProvider(backend, executor, ToolExecutionLogger(), catalog,
                    tools = listOf(tool), approvalHandler = { approval.awaitResult(it) })
                val turn = async { bridge.respond(listOf(ChatMessage(ChatMessage.Role.USER, "내 이름은 유닉이야. 기억해줘"))) }
                runCurrent()
                assertEquals("memory", approval.pending.value?.toolName)
                assertTrue(store.list().isEmpty())
                approval.approve()
                assertTrue(turn.await().message.content.contains("저장했어요"))
                assertEquals("내 이름은 유닉이야.", MemoryStore(File(dir, "$type.enc"), crypto).list().single().text)
            }
        } finally { dir.deleteRecursively() }
    }

    @Test fun historyIsActuallyReadEvenWhenModelWouldDenyToolAccess() = runTest {
        val dir = Files.createTempDirectory("memory-flow").toFile()
        try {
            val store = MemoryStore(File(dir, "entries.enc"), crypto)
            val old = MemoryConversation("old", "다른 채팅", listOf(ChatMessage(ChatMessage.Role.USER, "내 이름은 유닉")))
            val tool = MemoryTool(store) { globalMemoryHistory(listOf(old), "new") }
            var input: List<ChatMessage> = emptyList()
            val backend = object : AIProvider {
                override val type = ProviderType.LOCAL
                override val displayName = "weak model"
                override suspend fun isReady() = true
                override suspend fun respond(history: List<ChatMessage>): AIResponse {
                    input = localInferenceHistory(history)
                    return AIResponse(ChatMessage(ChatMessage.Role.ASSISTANT, "채팅 기록에 접근할 수 없어요"), type, 0)
                }
            }
            val logger = ToolExecutionLogger()
            val bridge = ToolBridgedAIProvider(SafetyAIProvider(backend), ToolExecutor(listOf(tool), InMemoryToolPermissionStore()),
                logger, catalog, tools = listOf(tool))
            val engine = ConversationEngine(bridge)
            assertTrue(engine.submitUserMessage("다른 채팅에서 내 이름이 뭐라고 했어?", voiceMode = true))
            assertTrue(engine.latestAssistantMessage()?.content.orEmpty().contains("유닉"))
            assertFalse(engine.latestAssistantMessage()?.content.orEmpty().contains("접근할 수 없"))
            assertTrue(input.last().content.contains("유닉"))
            assertEquals("history", logger.entries.value.single().action)
            assertTrue(logger.entries.value.single().success)
        } finally { dir.deleteRecursively() }
    }

    @Test fun completeMemorySignaturesReachVoiceLocalBackendWithoutMemoryKeywords() = runTest {
        var input: List<ChatMessage> = emptyList()
        val backend = object : AIProvider {
            override val type = ProviderType.LOCAL
            override val displayName = "test"
            override suspend fun isReady() = true
            override suspend fun respond(history: List<ChatMessage>): AIResponse {
                input = localInferenceHistory(history)
                return AIResponse(ChatMessage(ChatMessage.Role.ASSISTANT, "안녕하세요"), type, 0)
            }
        }
        val bridge = ToolBridgedAIProvider(SafetyAIProvider(backend), ToolExecutor(emptyList(), InMemoryToolPermissionStore()),
            ToolExecutionLogger(), catalog)
        assertTrue(ConversationEngine(bridge).submitUserMessage("안녕", voiceMode = true))
        assertTrue(input.filter { it.role == ChatMessage.Role.SYSTEM }.any { it.content.contains("memory.add text=VALUE") && it.content.contains("memory.history") })
    }

    @Test fun ambiguousRequestsNeverGenerateWriteArguments() {
        assertNull(MemoryRequests.initialCall("이걸 기억해줘"))
        assertNull(MemoryRequests.initialCall("비밀번호는 secret. 기억해줘"))
        assertNull(MemoryRequests.initialCall("내 이름을 기억하니?"))
        assertNull(MemoryRequests.initialCall("내 이름은 뭐지?"))
        assertEquals("add", MemoryRequests.initialCall("내 이름은 유닉이야")?.action)
        assertEquals("add", MemoryRequests.initialCall("나는 스타레일을 좋아해")?.action)
        assertEquals("history", MemoryRequests.initialCall("채팅 기록 보여줘")?.action)
    }
}
