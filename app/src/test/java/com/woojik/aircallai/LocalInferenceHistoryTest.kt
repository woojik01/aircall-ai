package com.woojik.aircallai

import com.woojik.aircallai.ai.local.localInferenceHistory
import com.woojik.aircallai.ai.provider.AIProviderException
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.ai.provider.ProviderErrorKind
import org.junit.Assert.*
import org.junit.Test

class LocalInferenceHistoryTest {
    private fun user(text: String) = ChatMessage(ChatMessage.Role.USER, text)
    private fun reply(text: String) = ChatMessage(ChatMessage.Role.ASSISTANT, text)

    @Test fun longHistoryKeepsRecentCompletePairsAndInstructions() {
        val system = ChatMessage(ChatMessage.Role.SYSTEM, "한국어로 답한다")
        val recent = listOf(user("안녕"), reply("안녕하세요"), user("다음 질문"))
        val history = listOf(system, user("가".repeat(1000)), reply("나".repeat(1000))) + recent
        assertEquals(listOf(system) + recent, localInferenceHistory(history))
    }

    @Test fun oversizedCurrentInputIsRejectedWithoutTruncation() {
        try {
            localInferenceHistory(listOf(user("가".repeat(1000))))
            fail("Expected input limit")
        } catch (error: AIProviderException) {
            assertEquals(ProviderErrorKind.INPUT_TOO_LONG, error.kind)
        }
    }

    @Test fun failedPreviousTurnDoesNotCreateConsecutiveUserRoles() {
        assertEquals(listOf(user("다시")), localInferenceHistory(listOf(user("실패"), user("다시"))))
    }
}
