package com.woojik.aircallai

import com.woojik.aircallai.ai.provider.AIProvider
import com.woojik.aircallai.ai.provider.AIResponse
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.ai.provider.NoopAIProvider
import com.woojik.aircallai.ai.provider.ProviderType
import com.woojik.aircallai.conversation.ConversationEngine
import com.woojik.aircallai.conversation.ConversationState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationEngineTest {

    @Test
    fun `initial state is Idle with empty transcript`() {
        val engine = ConversationEngine(NoopAIProvider())
        assertTrue(engine.state.value is ConversationState.Idle)
        assertTrue(engine.transcript.value.isEmpty())
    }

    @Test
    fun `listening toggles on and off`() {
        val engine = ConversationEngine(NoopAIProvider())
        engine.startListening()
        assertTrue(engine.state.value is ConversationState.Listening)
        engine.stopListening()
        assertTrue(engine.state.value is ConversationState.Idle)
    }

    @Test
    fun `submitUserMessage appends user and assistant turns and returns to Idle`() = runTest {
        val engine = ConversationEngine(NoopAIProvider())
        engine.submitUserMessage("안녕")
        val transcript = engine.transcript.value
        assertEquals(2, transcript.size)
        assertEquals(ChatMessage.Role.USER, transcript[0].role)
        assertEquals(ChatMessage.Role.ASSISTANT, transcript[1].role)
        assertTrue(engine.state.value is ConversationState.Idle)
    }

    @Test
    fun `blank messages are ignored`() = runTest {
        val engine = ConversationEngine(NoopAIProvider())
        engine.submitUserMessage("   ")
        assertTrue(engine.transcript.value.isEmpty())
    }

    @Test
    fun `provider failure puts engine into Error state`() = runTest {
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
    fun `reset clears transcript and state`() = runTest {
        val engine = ConversationEngine(NoopAIProvider())
        engine.submitUserMessage("hi")
        engine.reset()
        assertTrue(engine.transcript.value.isEmpty())
        assertTrue(engine.state.value is ConversationState.Idle)
    }
}
