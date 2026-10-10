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
        val bridge = ToolBridgedAIProvider(SafetyAIProvider(backend),
            ToolExecutor(emptyList(), InMemoryToolPermissionStore()), ToolExecutionLogger(),
            AppToolCatalog.DESCRIPTION)
        assertTrue(ConversationEngine(bridge, memoryProvider = { "가".repeat(150) }).submitUserMessage("안녕", voiceMode = true))
        assertTrue(reachedBackend)
    }
}
