package com.woojik.aircallai

import com.woojik.aircallai.ai.provider.AIProvider
import com.woojik.aircallai.ai.provider.AIResponse
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.ai.provider.ProviderType
import com.woojik.aircallai.audio.SpeechRecognizerInterface
import com.woojik.aircallai.audio.SpeechSynthesizer
import com.woojik.aircallai.conversation.ConversationEngine
import com.woojik.aircallai.conversation.VoiceSession
import com.woojik.aircallai.session.MutedSynthesizer
import com.woojik.aircallai.session.SessionAudioHooks
import com.woojik.aircallai.session.SessionController
import com.woojik.aircallai.session.SessionRepository
import com.woojik.aircallai.session.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD-05 완료 조건 중 JVM에서 검증 가능한 항목:
 * - 백그라운드 세션이 턴을 연속 수행하고 종료 가능
 * - 일시정지/재개가 다음 턴부터 적용
 * - 음소거 시 TTS 재생만 건너뛴다
 * - 세션 종료 후 재시작 가능
 * - Repository가 UI에 동일한 상태를 노출
 */
class BackgroundSessionTest {

    private class MockProvider : AIProvider {
        override val type = ProviderType.CLOUD
        override val displayName = "mock"
        override suspend fun isReady() = true
        override suspend fun respond(history: List<ChatMessage>): AIResponse {
            val user = history.last { it.role == ChatMessage.Role.USER }
            return AIResponse(ChatMessage(ChatMessage.Role.ASSISTANT, "echo: " + user.content), type, 1)
        }
    }

    /** 음성 입력을 채널로 흉내낸다. 말이 없으면(채널 비었으면) 마이크 대기처럼 suspend 된다. */
    private class ChannelRecognizer : SpeechRecognizerInterface {
        val utterances: Channel<String> = Channel(Channel.UNLIMITED)
        var calls = 0
        override suspend fun recognizeOnce(): String? {
            calls++
            return utterances.receive()
        }
    }

    private class RecordingSynthesizer : SpeechSynthesizer {
        val spoken: MutableList<String> = mutableListOf()
        var stopped = 0
        override suspend fun speak(text: String) { spoken.add(text) }
        override fun stop() { stopped++ }
    }

    @Test
    fun backgroundSessionRunsContinuousTurns() = runTest {
        val recognizer = ChannelRecognizer()
        val synthesizer = RecordingSynthesizer()
        val engine = ConversationEngine(MockProvider())
        val session = VoiceSession(recognizer, synthesizer, engine)
        val controller = SessionController(backgroundScope)

        controller.start { session.runOneTurn() }
        recognizer.utterances.send("a")
        recognizer.utterances.send("b")
        engine.transcript.first { it.size >= 4 }

        assertEquals(listOf("echo: a", "echo: b"), synthesizer.spoken)
        controller.end()
        assertEquals(SessionStatus.Ended, controller.status.value)
    }

    @Test
    fun pauseSuspendsLoopUntilResumed() = runTest {
        var turns = 0
        val controller = SessionController(backgroundScope)
        controller.start { turns++ }

        runCurrent()
        assertEquals(1, turns)
        assertEquals(SessionStatus.Running, controller.status.value)

        controller.pause()
        advanceTimeBy(5_000)
        assertEquals(1, turns)
        assertEquals(SessionStatus.Paused, controller.status.value)

        controller.resume()
        advanceTimeBy(1_000)
        assertTrue("resume 후 턴이 다시 실행되어야 한다", turns > 1)
        assertEquals(SessionStatus.Running, controller.status.value)
    }

    @Test
    fun endStopsLoopAndSessionCanBeRestarted() = runTest {
        var turns = 0
        val controller = SessionController(backgroundScope)
        controller.start { turns++ }
        runCurrent()
        assertTrue(turns >= 1)

        controller.end()
        assertEquals(SessionStatus.Ended, controller.status.value)
        val turnsAtEnd = turns
        advanceTimeBy(5_000)
        assertEquals(turnsAtEnd, turns)
        assertFalse(controller.isRunning)

        // 재시작: 새 세션 정상 시작
        var restarted = 0
        controller.start { restarted++ }
        runCurrent()
        assertTrue(restarted >= 1)
        assertEquals(SessionStatus.Running, controller.status.value)
        controller.end()
    }

    @Test
    fun muteSuppressesTtsButKeepsConversationState() = runTest {
        val synthesizer = RecordingSynthesizer()
        var muted = true
        val mutedSynthesizer = MutedSynthesizer(synthesizer) { muted }

        // 음소거 상태: 재생 건너뛰기
        mutedSynthesizer.speak("secret")
        assertEquals(emptyList<String>(), synthesizer.spoken)

        // 음소거 해제: 재생
        muted = false
        mutedSynthesizer.speak("hello")
        assertEquals(listOf("hello"), synthesizer.spoken)

        // stop은 위임된다
        mutedSynthesizer.stop()
        assertEquals(1, synthesizer.stopped)
    }

    @Test
    fun repositoryExposesSameStateToUiAndService() {
        val engine = ConversationEngine(MockProvider())
        val controller = SessionController(CoroutineScope(Dispatchers.Unconfined))
        val repository = SessionRepository(engine, controller)

        assertSame(engine, repository.engine)
        assertSame(controller.status, repository.status)
        assertSame(controller.muted, repository.muted)

        controller.setMuted(true)
        assertTrue(repository.muted.value)
    }

    @Test
    fun uiStopSpeakingRoutesThroughRepositoryToServiceHooks() {
        val engine = ConversationEngine(MockProvider())
        val controller = SessionController(CoroutineScope(Dispatchers.Unconfined))
        val repository = SessionRepository(engine, controller)
        var hookCalls = 0
        val hooks = object : SessionAudioHooks {
            override fun stopSpeaking() { hookCalls++ }
        }

        // Service 부착 전에는 안전하게 no-op
        repository.audioHooks = null
        repository.stopSpeaking()
        assertEquals(0, hookCalls)

        repository.audioHooks = hooks
        repository.stopSpeaking()
        assertEquals(1, hookCalls)
    }
}
