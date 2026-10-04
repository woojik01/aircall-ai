package com.woojik.aircallai

import com.woojik.aircallai.ai.local.localInferenceHistory
import com.woojik.aircallai.ai.provider.*
import com.woojik.aircallai.conversation.ConversationEngine
import com.woojik.aircallai.privacy.SafetyAIProvider
import com.woojik.aircallai.tools.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSafetyPromptBudgetTest {
    @Test fun greetingWorksWithVoiceToolAndSafetyInstructionsTogether() = runTest {
        var reachedBackend = false
        val backend = object : AIProvider {
            override val type = ProviderType.LOCAL
            override val displayName = "local"
            override suspend fun isReady() = true
            override suspend fun respond(history: List<ChatMessage>): AIResponse {
                // Use the real local input guard: a unit compile cannot catch an oversized combined prompt.
                localInferenceHistory(history)
                reachedBackend = true
                return AIResponse(ChatMessage(ChatMessage.Role.ASSISTANT, "안녕하세요"), type, 0)
            }
        }
        val actions = listOf("github.read_repository", "github.read_file", "github.create_issue", "github.create_pull_request",
            "notes.add_note", "notes.search_notes", "notes.list_notes", "calendar.read_upcoming", "calendar.create_event", "gmail.send_email")
        val bridge = ToolBridgedAIProvider(SafetyAIProvider(backend),
            ToolExecutor(emptyList(), InMemoryToolPermissionStore()), ToolExecutionLogger(),
            actions.joinToString("\n") { "$it — 기능" })
        assertTrue(ConversationEngine(bridge).submitUserMessage("안녕", voiceMode = true))
        assertTrue(reachedBackend)
    }
}
