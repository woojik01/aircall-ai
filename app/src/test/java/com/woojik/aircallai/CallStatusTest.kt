package com.woojik.aircallai

import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.conversation.ConversationState
import com.woojik.aircallai.conversation.ConversationState.ErrorKind
import com.woojik.aircallai.session.CallStatus
import com.woojik.aircallai.session.SessionStatus
import com.woojik.aircallai.session.formatCallDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class CallStatusTest {
    private val msg = ChatMessage(ChatMessage.Role.ASSISTANT, "hi")

    @Test fun mapsConversationStatesDuringCall() {
        val running = SessionStatus.Running
        assertEquals(CallStatus.LISTENING, CallStatus.from(ConversationState.Idle, running))
        assertEquals(CallStatus.LISTENING, CallStatus.from(ConversationState.Listening, running))
        assertEquals(CallStatus.THINKING, CallStatus.from(ConversationState.Processing(null), running))
        assertEquals(CallStatus.SPEAKING, CallStatus.from(ConversationState.Speaking(msg), running))
    }

    @Test fun pausedOverridesTurnState() {
        assertEquals(CallStatus.PAUSED, CallStatus.from(ConversationState.Listening, SessionStatus.Paused))
    }

    @Test fun errorsWinAndNetworkMeansOffline() {
        assertEquals(CallStatus.OFFLINE, CallStatus.from(ConversationState.Error(ErrorKind.NETWORK, "x"), SessionStatus.Running))
        assertEquals(CallStatus.ERROR, CallStatus.from(ConversationState.Error(ErrorKind.AI_PROVIDER, "x"), SessionStatus.Paused))
    }

    @Test fun idleOutsideCall() {
        assertEquals(CallStatus.READY, CallStatus.from(ConversationState.Idle, SessionStatus.Inactive))
        assertEquals(CallStatus.ENDED, CallStatus.from(ConversationState.Idle, SessionStatus.Ended))
    }

    @Test fun durationFormatting() {
        assertEquals("00:00", formatCallDuration(0))
        assertEquals("01:05", formatCallDuration(65))
        assertEquals("1:00:01", formatCallDuration(3601))
    }
}
