package com.woojik.aircallai

import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.call.CallStatus
import com.woojik.aircallai.call.callControlsOf
import com.woojik.aircallai.call.callStatusOf
import com.woojik.aircallai.call.muteLabel
import com.woojik.aircallai.call.pauseLabel
import com.woojik.aircallai.conversation.ConversationState
import com.woojik.aircallai.session.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD-07 완료 조건: 상태 전환 시 UI 정확성.
 * 통화 화면 상태 도출 로직(callStatusOf)의 우선순위와 접근성 라벨을 검증한다.
 */
class CallUiStateTest {

    private val assistant = ChatMessage(ChatMessage.Role.ASSISTANT, "안녕하세요")

    @Test
    fun inactiveSessionWithoutProviderShowsOffline() {
        val status = callStatusOf(
            session = SessionStatus.Inactive,
            conversation = ConversationState.Idle,
            muted = false,
            providerReady = false,
        )
        assertEquals(CallStatus.OFFLINE, status)
    }

    @Test
    fun inactiveSessionWithProviderShowsIdle() {
        val status = callStatusOf(
            session = SessionStatus.Ended,
            conversation = ConversationState.Idle,
            muted = false,
            providerReady = true,
        )
        assertEquals(CallStatus.IDLE, status)
    }

    @Test
    fun runningSessionShowsListening() {
        val status = callStatusOf(
            session = SessionStatus.Running,
            conversation = ConversationState.Idle,
            muted = false,
            providerReady = true,
        )
        assertEquals(CallStatus.LISTENING, status)

        val listening = callStatusOf(
            session = SessionStatus.Running,
            conversation = ConversationState.Listening,
            muted = false,
            providerReady = true,
        )
        assertEquals(CallStatus.LISTENING, listening)
    }

    @Test
    fun processingShowsThinking() {
        val status = callStatusOf(
            session = SessionStatus.Running,
            conversation = ConversationState.Processing(null),
            muted = false,
            providerReady = true,
        )
        assertEquals(CallStatus.THINKING, status)
    }

    @Test
    fun speakingShowsSpeaking() {
        val status = callStatusOf(
            session = SessionStatus.Running,
            conversation = ConversationState.Speaking(assistant),
            muted = false,
            providerReady = true,
        )
        assertEquals(CallStatus.SPEAKING, status)
    }

    @Test
    fun mutedOverridesConversationState() {
        val status = callStatusOf(
            session = SessionStatus.Running,
            conversation = ConversationState.Speaking(assistant),
            muted = true,
            providerReady = true,
        )
        assertEquals(CallStatus.MUTED, status)
    }

    @Test
    fun pausedOverridesMuted() {
        val status = callStatusOf(
            session = SessionStatus.Paused,
            conversation = ConversationState.Listening,
            muted = true,
            providerReady = true,
        )
        assertEquals(CallStatus.PAUSED, status)
    }

    @Test
    fun errorOverridesEverything() {
        val status = callStatusOf(
            session = SessionStatus.Running,
            conversation = ConversationState.Error(
                ConversationState.ErrorKind.NETWORK,
                "network down",
            ),
            muted = true,
            providerReady = true,
        )
        assertEquals(CallStatus.ERROR, status)
    }

    @Test
    fun controlsEnabledWhileSessionActive() {
        val controls = callControlsOf(CallStatus.LISTENING, sessionActive = true)
        assertTrue(controls.canMute)
        assertTrue(controls.canPause)
        assertTrue(controls.canEnd)
    }

    @Test
    fun controlsDisabledWhenSessionInactive() {
        val controls = callControlsOf(CallStatus.IDLE, sessionActive = false)
        assertFalse(controls.canMute)
        assertFalse(controls.canPause)
        assertFalse(controls.canEnd)
    }

    @Test
    fun everyStatusHasGlyphAndLabel() {
        // PRD-07 접근성: 상태를 색상만으로 구분하지 않는다.
        CallStatus.values().forEach { status ->
            assertTrue("glyph missing for " + status, status.glyph.isNotBlank())
            assertTrue("label missing for " + status, status.label.isNotBlank())
        }
    }

    @Test
    fun buttonLabelsFollowToggledState() {
        assertEquals("음소거", muteLabel(muted = false))
        assertEquals("음소거 해제", muteLabel(muted = true))
        assertEquals("일시정지", pauseLabel(paused = false))
        assertEquals("재개", pauseLabel(paused = true))
    }
}
