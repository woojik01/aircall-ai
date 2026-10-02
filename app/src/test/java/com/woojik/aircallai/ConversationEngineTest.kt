package com.woojik.aircallai

import com.woojik.aircallai.ai.provider.AIProvider
import com.woojik.aircallai.ai.provider.AIResponse
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.ai.provider.NoopAIProvider
import com.woojik.aircallai.ai.provider.ProviderType
import com.woojik.aircallai.conversation.ConversationEngine
import com.woojik.aircallai.conversation.ConversationState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationEngineTest {

    @Test(expected = CancellationException::class)
    fun cancellationIsNotReportedAsModelFailure() = runTest {
        val provider = object : AIProvider by NoopAIProvider() {
            override suspend fun respond(history: List<ChatMessage>): AIResponse =
                throw CancellationException("session ended")
        }
        ConversationEngine(provider).submitUserMessage("hello")
    }

    @Test
    fun initialStateIsIdleWithEmptyTranscript() {
        val engine = ConversationEngine(NoopAIProvider())
        assertTrue(engine.state.value is ConversationState.Idle)
        assertTrue(engine.transcript.value.isEmpty())
    }

    @Test
    fun listeningTogglesOnAndOff() {
        val engine = ConversationEngine(NoopAIProvider())
        engine.startListening()
        assertTrue(engine.state.value is ConversationState.Listening)
        engine.stopListening()
        assertTrue(engine.state.value is ConversationState.Idle)
    }

    @Test
    fun submitUserMessageEndsInSpeakingUntilTtsCompletes() = runTest {
        val engine = ConversationEngine(NoopAIProvider())
        engine.submitUserMessage("안녕")
        val transcript = engine.transcript.value
        assertEquals(2, transcript.size)
        assertEquals(ChatMessage.Role.USER, transcript[0].role)
        assertEquals(ChatMessage.Role.ASSISTANT, transcript[1].role)
        assertTrue(engine.state.value is ConversationState.Speaking)
        assertNotNull(engine.latestAssistantMessage())
        // TTS 완료 시점에 Idle로 복귀 (VoiceSession이 호출)
        engine.markIdle()
        assertTrue(engine.state.value is ConversationState.Idle)
    }

    @Test
    fun blankMessagesAreIgnored() = runTest {
        val engine = ConversationEngine(NoopAIProvider())
        engine.submitUserMessage("   ")
        assertTrue(engine.transcript.value.isEmpty())
    }

    @Test
    fun providerFailurePutsEngineIntoErrorState() = runTest {
        val failing = object : AIProvider {
            override val type = ProviderType.CLOUD
            override val displayName = "failing"
            override suspend fun isReady() = true
            override suspend fun respond(history: List<ChatMessage>): AIResponse =
                throw IllegalStateException("boom")
        }
        val engine = ConversationEngine(failing)
        engine.submitUserMessage("hello")
        val state = engine.state.value
        assertTrue(state is ConversationState.Error)
        assertEquals("boom", (state as ConversationState.Error).message)
        engine.clearError()
        assertTrue(engine.state.value is ConversationState.Idle)
    }

    @Test
    fun resetClearsTranscriptAndState() = runTest {
        val engine = ConversationEngine(NoopAIProvider())
        engine.submitUserMessage("hi")
        engine.reset()
        assertTrue(engine.transcript.value.isEmpty())
        assertTrue(engine.state.value is ConversationState.Idle)
    }
}
