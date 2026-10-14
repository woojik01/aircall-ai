package com.woojik.aircallai

import com.woojik.aircallai.ai.local.localInferenceHistory
import com.woojik.aircallai.ai.provider.*
import com.woojik.aircallai.conversation.ConversationEngine
import com.woojik.aircallai.privacy.SafetyAIProvider
import com.woojik.aircallai.tools.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ConversationMemoryContextTest {
    @Test fun previousTurnAndSavedMemoryReachActualLocalReducer() = runTest {
        val received = mutableListOf<List<ChatMessage>>()
        val backend = object : AIProvider {
            override val type = ProviderType.LOCAL
            override val displayName = "test"
            override suspend fun isReady() = true
            override suspend fun respond(history: List<ChatMessage>): AIResponse {
                received += localInferenceHistory(history)
                return AIResponse(ChatMessage(ChatMessage.Role.ASSISTANT, "알겠어요"), type, 0)
            }
        }
        val provider = ToolBridgedAIProvider(SafetyAIProvider(backend),
            ToolExecutor(emptyList(), InMemoryToolPermissionStore()), ToolExecutionLogger(),
            "memory.list [limit=VALUE] — 기억 조회\ngithub.read_file owner=VALUE repo=VALUE path=VALUE — 파일\nnotes.list_notes — 메모\ngmail.send_email to=VALUE subject=VALUE body=VALUE — 메일", memoryContext = { "id: 좋아하는 게임은 스타레일" })
        val engine = ConversationEngine(provider)
        assertTrue(engine.submitUserMessage("내 이름은 유닉이야", voiceMode = true))
        engine.markIdle()
        assertTrue(engine.submitUserMessage("방금 말한 내 이름이 뭐야?", voiceMode = true))
        val bounded = received.last()
        assertTrue(bounded.any { it.role == ChatMessage.Role.USER && it.content.contains("내 이름은 유닉이야") })
        assertTrue(bounded.any { it.role == ChatMessage.Role.ASSISTANT && it.content == "알겠어요" })
        assertTrue(bounded.last().content.contains("스타레일"))
        assertTrue(bounded.last().content.contains("방금 말한 내 이름"))
    }
}
