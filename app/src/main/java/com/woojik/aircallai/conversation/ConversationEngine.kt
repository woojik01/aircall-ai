package com.woojik.aircallai.conversation

import com.woojik.aircallai.ai.provider.AIProvider
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.audio.AudioError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * PRD-01 skeleton + PRD-03 audio integration.
 * 대화 상태는 Activity/Composable 밖에 살아있다 (화면 회전/프로세스 재생성 대응).
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

    /** PRD-03: STT/TTS 오류를 네트워크/음성 인식 오류로 구분해 표시한다. */
    fun reportAudioError(error: AudioError) {
        _state.value = ConversationState.Error(
            kind = when (error) {
                is AudioError.Network -> ConversationState.ErrorKind.NETWORK
                is AudioError.SpeechRecognition -> ConversationState.ErrorKind.SPEECH_RECOGNITION
                is AudioError.Permission -> ConversationState.ErrorKind.PERMISSION
                is AudioError.Unknown -> ConversationState.ErrorKind.UNKNOWN
            },
            message = error.userMessage,
        )
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
        } catch (t: Throwable) {
            _state.value = ConversationState.Error(
                kind = ConversationState.ErrorKind.AI_PROVIDER,
                message = t.message ?: "AI 응답 생성 실패",
            )
        }
    }

    /** PRD-03: TTS 시작 직전 호출 — VoiceSession이 재생 완료를 알린다. */
    fun markSpeaking() {
        _state.update { if (it is ConversationState.Processing || it is ConversationState.Idle) ConversationState.Speaking(latestAssistantMessage() ?: ChatMessage(ChatMessage.Role.ASSISTANT, "")) else it }
    }

    fun markIdle() {
        _state.update { if (it is ConversationState.Speaking) ConversationState.Idle else it }
    }

    fun latestAssistantMessage(): ChatMessage? =
        _transcript.value.lastOrNull { it.role == ChatMessage.Role.ASSISTANT }

    fun reset() {
        _transcript.value = emptyList()
        _state.value = ConversationState.Idle
    }
}
