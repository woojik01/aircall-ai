package com.woojik.aircallai

import com.woojik.aircallai.ai.provider.NoopAIProvider
import com.woojik.aircallai.audio.AudioError
import com.woojik.aircallai.conversation.ConversationEngine
import com.woojik.aircallai.conversation.ConversationState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationEngineAudioErrorTest {

    @Test
    fun permissionErrorMapsToPermissionKind() {
        val engine = ConversationEngine(NoopAIProvider())
        engine.reportAudioError(AudioError.Permission())
        val state = engine.state.value
        assertTrue(state is ConversationState.Error)
        assertEquals(ConversationState.ErrorKind.PERMISSION, (state as ConversationState.Error).kind)
    }

    @Test
    fun errorIsClearabl() {
        val engine = ConversationEngine(NoopAIProvider())
        engine.reportAudioError(AudioError.Network())
        engine.clearError()
        assertTrue(engine.state.value is ConversationState.Idle)
    }

    @Test
    fun speakingLifecycle() = runTest {
        val engine = ConversationEngine(NoopAIProvider())
        engine.submitUserMessage("hi")
        assertTrue(engine.state.value is ConversationState.Speaking)
        engine.markIdle()
        assertTrue(engine.state.value is ConversationState.Idle)
    }
}
