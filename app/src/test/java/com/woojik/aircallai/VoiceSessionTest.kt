package com.woojik.aircallai

import com.woojik.aircallai.ai.provider.AIProvider
import com.woojik.aircallai.ai.provider.AIResponse
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.ai.provider.NoopAIProvider
import com.woojik.aircallai.ai.provider.ProviderType
import com.woojik.aircallai.audio.AudioError
import com.woojik.aircallai.audio.SpeechRecognizer
import com.woojik.aircallai.audio.SpeechSynthesizer
import com.woojik.aircallai.conversation.ConversationEngine
import com.woojik.aircallai.conversation.ConversationState
import com.woojik.aircallai.conversation.VoiceSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSessionTest {

    private class FakeRecognizer(val result: suspend () -> String?) : SpeechRecognizer {
        override suspend fun recognizeOnce(): String? = result()
    }

    private class FakeSynthesizer : SpeechSynthesizer {
        val spoken = mutableListOf<String>()
        var stopped = 0
        var fail = false
        override suspend fun speak(text: String) {
            if (fail) throw AudioError.Unknown(IllegalStateException("tts"))
            spoken.add(text)
        }
        override fun stop() { stopped++ }
    }

    @Test
    fun fullTurnSpeaksAiResponseAndReturnsToIdle() = runTest {
        val engine = ConversationEngine(NoopAIProvider())
        val tts = FakeSynthesizer()
        val session = VoiceSession(FakeRecognizer { "안녕" }, tts, engine)
        session.runOneTurn()
        assertEquals(listOf("안녕"), engine.transcript.value.map { it.content }.filter { it == "안녕" })
        assertEquals(1, tts.spoken.size)
        assertTrue(engine.state.value is ConversationState.Idle)
        assertEquals(1, session.metrics.value.size)
    }

    @Test
    fun silenceSkipsTheTurn() = runTest {
        val engine = ConversationEngine(NoopAIProvider())
        val tts = FakeSynthesizer()
        VoiceSession(FakeRecognizer { null }, tts, engine).runOneTurn()
        assertTrue(engine.transcript.value.isEmpty())
        assertTrue(tts.spoken.isEmpty())
    }

    @Test
    fun networkSttErrorIsSurfacedAsNetworkKind() = runTest {
        val engine = ConversationEngine(NoopAIProvider())
        val tts = FakeSynthesizer()
        VoiceSession(FakeRecognizer { throw AudioError.Network() }, tts, engine).runOneTurn()
        val state = engine.state.value
        assertTrue(state is ConversationState.Error)
        assertEquals(ConversationState.ErrorKind.NETWORK, (state as ConversationState.Error).kind)
    }

    @Test
    fun recognitionErrorIsDistinguishedFromNetwork() = runTest {
        val engine = ConversationEngine(NoopAIProvider())
        VoiceSession(FakeRecognizer { throw AudioError.SpeechRecognition() }, FakeSynthesizer(), engine).runOneTurn()
        val state = engine.state.value as ConversationState.Error
        assertEquals(ConversationState.ErrorKind.SPEECH_RECOGNITION, state.kind)
    }

    @Test
    fun ttsErrorIsSurfacedButTurnIsCounted() = runTest {
        val engine = ConversationEngine(NoopAIProvider())
        val tts = FakeSynthesizer().apply { fail = true }
        VoiceSession(FakeRecognizer { "hi" }, tts, engine).runOneTurn()
        assertTrue(engine.state.value is ConversationState.Error)
    }

    @Test
    fun stopSpeakingInterruptsTts() = runTest {
        val engine = ConversationEngine(NoopAIProvider())
        val tts = FakeSynthesizer()
        val session = VoiceSession(FakeRecognizer { "x" }, tts, engine)
        session.stopSpeaking()
        assertEquals(1, tts.stopped)
        assertTrue(engine.state.value is ConversationState.Idle)
    }

    @Test
    fun tenConsecutiveTurnsSucceed() = runTest {
        val engine = ConversationEngine(NoopAIProvider())
        val tts = FakeSynthesizer()
        val session = VoiceSession(FakeRecognizer { "turn " + it }, tts, engine)
        for (i in 1..10) session.runOneTurn()
        assertEquals(10, engine.transcript.value.size)
        assertEquals(10, tts.spoken.size)
        assertEquals(10, session.metrics.value.size)
        assertTrue(engine.state.value is ConversationState.Idle)
    }
}
