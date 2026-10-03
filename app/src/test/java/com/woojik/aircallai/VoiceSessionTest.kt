package com.woojik.aircallai

import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.ai.provider.AIProvider
import com.woojik.aircallai.ai.provider.AIProviderException
import com.woojik.aircallai.ai.provider.AIResponse
import com.woojik.aircallai.ai.provider.ProviderErrorKind
import com.woojik.aircallai.ai.provider.NoopAIProvider
import com.woojik.aircallai.audio.AudioError
import com.woojik.aircallai.audio.SpeechRecognizerInterface
import com.woojik.aircallai.audio.SpeechSynthesizer
import com.woojik.aircallai.conversation.ConversationEngine
import com.woojik.aircallai.conversation.ConversationState
import com.woojik.aircallai.conversation.VoiceSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
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
    fun modelLoadFailureDoesNotReplayPreviousResponse() = runTest {
        val engine = ConversationEngine(NoopAIProvider())
        val tts = FakeSynthesizer()
        val session = VoiceSession(FakeRecognizer { "hello" }, tts, engine)
        session.runOneTurn()
        val failing = object : AIProvider by NoopAIProvider() {
            override suspend fun respond(history: List<ChatMessage>): AIResponse =
                throw AIProviderException(ProviderErrorKind.LOAD_FAILED)
        }
        engine.updateProvider(failing)
        session.runOneTurn()
        assertEquals(1, tts.spoken.size)
        assertEquals(1, session.metrics.value.size)
        assertTrue(engine.state.value is ConversationState.Error)
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
    fun cancelledTtsReturnsToIdle() = runTest {
        val engine = ConversationEngine(NoopAIProvider())
        val gate = CompletableDeferred<Unit>()
        val tts = object : SpeechSynthesizer {
            override suspend fun speak(text: String) { gate.await() }
            override fun stop() {}
        }
        val session = VoiceSession(FakeRecognizer { "hi" }, tts, engine)
        val turn = launch { session.runOneTurn() }
        runCurrent()
        assertTrue(engine.state.value is ConversationState.Speaking)
        turn.cancel()
        turn.join()
        assertTrue(engine.state.value is ConversationState.Idle)
    }

    @Test
    fun concurrentVoiceTurnDoesNotRecognizeTwice() = runTest {
        val gate = CompletableDeferred<Unit>()
        var recognitions = 0
        val session = VoiceSession(FakeRecognizer {
            recognitions++
            gate.await()
            "hi"
        }, FakeSynthesizer(), ConversationEngine(NoopAIProvider()))
        val turn = launch { session.runOneTurn() }
        runCurrent()
        session.runOneTurn()
        assertEquals(1, recognitions)
        gate.complete(Unit)
        turn.join()
    }

    @Test
    fun metricsRemainBoundedDuringLongCalls() = runTest {
        val session = VoiceSession(FakeRecognizer { "hi" }, FakeSynthesizer(), ConversationEngine(NoopAIProvider()))
        repeat(120) { session.runOneTurn() }
        assertEquals(100, session.metrics.value.size)
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
