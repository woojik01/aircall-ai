package com.woojik.aircallai.conversation

import com.woojik.aircallai.ai.provider.AIProvider
import com.woojik.aircallai.ai.provider.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * PRD-01 skeleton: holds transcript + state and delegates turn handling to an AIProvider.
 * Audio (STT/TTS) is attached in PRD-03; foreground service in PRD-05.
 */
class ConversationEngine(
    private val provider: AIProvider,
) {
    private val _state = MutableStateFlow<ConversationState>(ConversationState.Idle)
    val state: StateFlow<ConversationState> = _state.asStateFlow()

    private val _transcript = MutableStateFlow<List<ChatMessage>>(emptyList())
    val transcript: StateFlow<List<ChatMessage>> = _transcript.asStateFlow()

    fun startListening() {
        _state.update { if (it is ConversationState.Error || it is ConversationState.Idle) ConversationState.Listening else it }
    }

    fun stopListening() {
        _state.update { if (it is ConversationState.Listening) ConversationState.Idle else it }
    }

    fun clearError() {
        _state.update { if (it is ConversationState.Error) ConversationState.Idle else it }
    }

    suspend fun submitUserMessage(text: String) {
        if (text.isBlank()) return
        val userMessage = ChatMessage(ChatMessage.Role.USER, text)
        _transcript.update { it + userMessage }
        _state.value = ConversationState.Processing(userMessage)
        try {
            val response = provider.respond(_transcript.value)
            _transcript.update { it + response.message }
            _state.value = ConversationState.Speaking(response.message)
            _state.value = ConversationState.Idle
        } catch (t: Throwable) {
            _state.value = ConversationState.Error(
                kind = ConversationState.ErrorKind.AI_PROVIDER,
                message = t.message ?: "AI 응답 생성 실패",
            )
        }
    }

    fun reset() {
        _transcript.value = emptyList()
        _state.value = ConversationState.Idle
    }
}
