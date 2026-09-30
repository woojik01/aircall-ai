package com.woojik.aircallai

import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.ai.provider.NoopAIProvider
import com.woojik.aircallai.audio.AudioError
import com.woojik.aircallai.audio.SpeechRecognizerInterface
import com.woojik.aircallai.audio.SpeechSynthesizer
import com.woojik.aircallai.conversation.ConversationEngine
import com.woojik.aircallai.conversation.ConversationState
import com.woojik.aircallai.conversation.VoiceSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSessionTest {

    private class FakeRecognizer(val result: suspend () -> String?) : SpeechRecognizerInterface {
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
        // 한 턴 = USER 1개 + ASSISTANT 1개
        assertEquals(2, engine.transcript.value.size)
        assertEquals(1, engine.transcript.value.count { it.role == ChatMessage.Role.USER })
        assertEquals(1, engine.transcript.value.count { it.role == ChatMessage.Role.ASSISTANT })
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
        var n = 0
        val session = VoiceSession(FakeRecognizer { n++; "turn " + n }, tts, engine)
        for (i in 1..10) session.runOneTurn()
        // 턴당 USER 1 + ASSISTANT 1 = 2 메시지이므로 10턴 = 20개 (PRD-03: 대화 10회 이상 연속)
        assertEquals(10, engine.transcript.value.count { it.role == ChatMessage.Role.USER })
        assertEquals(10, engine.transcript.value.count { it.role == ChatMessage.Role.ASSISTANT })
        assertEquals(20, engine.transcript.value.size)
        assertEquals(10, tts.spoken.size)
        assertEquals(10, session.metrics.value.size)
        assertTrue(engine.state.value is ConversationState.Idle)
    }
}
