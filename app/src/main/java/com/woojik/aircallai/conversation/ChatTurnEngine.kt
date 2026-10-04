package com.woojik.aircallai.conversation

import com.woojik.aircallai.ai.provider.AIProvider
import com.woojik.aircallai.ai.provider.AIProviderException
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.audio.AudioError
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ConversationEngine(
    private var provider: AIProvider,
) {
    private val turnMutex = Mutex()
    private val revision = AtomicLong()
    private val _state = MutableStateFlow<ConversationState>(ConversationState.Idle)
    val state: StateFlow<ConversationState> = _state.asStateFlow()
    private val _transcript = MutableStateFlow<List<ChatMessage>>(emptyList())
    val transcript: StateFlow<List<ChatMessage>> = _transcript.asStateFlow()
    val activeProvider: AIProvider get() = provider

    fun updateProvider(next: AIProvider) {
        if (_state.value is ConversationState.Processing) return
        provider = next
    }

    fun startListening() {
        _state.update { if (it is ConversationState.Error || it is ConversationState.Idle) ConversationState.Listening else it }
    }

    fun stopListening() {
        _state.update { if (it is ConversationState.Listening) ConversationState.Idle else it }
    }

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

    suspend fun submitUserMessage(text: String, voiceMode: Boolean = false): Boolean {
        if (text.isBlank() || !turnMutex.tryLock()) return false
        val turnRevision = revision.get()
        val active = provider
        val userMessage = ChatMessage(ChatMessage.Role.USER, text)
        _transcript.update { it + userMessage }
        _state.value = ConversationState.Processing(userMessage)

        try {
            val requestHistory = if (voiceMode) {
                listOf(ChatMessage(ChatMessage.Role.SYSTEM, VOICE_SYSTEM_PROMPT)) + _transcript.value
            } else {
                _transcript.value
            }
            val response = active.respond(requestHistory)
            if (revision.get() != turnRevision) return false
            val responseText = if (voiceMode) {
                VoiceResponseSanitizer.sanitize(response.message.content)
            } else {
                response.message.content
            }
            val assistantMessage = response.message.copy(content = responseText)
            _transcript.update { it + assistantMessage }
            _state.value = ConversationState.Speaking(assistantMessage)
            return true
        } catch (e: CancellationException) {
            if (revision.get() == turnRevision) _state.value = ConversationState.Idle
            throw e
        } catch (e: AIProviderException) {
            if (revision.get() != turnRevision) return false
            _state.value = ConversationState.Error(
                ConversationState.ErrorKind.AI_PROVIDER,
                e.message ?: e.kind.userMessage,
            )
        } catch (t: Throwable) {
            if (revision.get() != turnRevision) return false
            _state.value = ConversationState.Error(
                ConversationState.ErrorKind.AI_PROVIDER,
                t.message ?: "AI 응답 생성 실패",
            )
        } finally {
            turnMutex.unlock()
        }
        return false
    }

    fun markSpeaking() {
        _state.update {
            if (it is ConversationState.Processing || it is ConversationState.Idle) {
                ConversationState.Speaking(
                    latestAssistantMessage() ?: ChatMessage(ChatMessage.Role.ASSISTANT, ""),
                )
            } else it
        }
    }

    fun markIdle() {
        _state.update { if (it is ConversationState.Speaking) ConversationState.Idle else it }
    }

    fun latestAssistantMessage(): ChatMessage? =
        _transcript.value.lastOrNull { it.role == ChatMessage.Role.ASSISTANT }

    fun reset() {
        revision.incrementAndGet()
        _transcript.value = emptyList()
        _state.value = ConversationState.Idle
    }

    companion object {
        private const val VOICE_SYSTEM_PROMPT = """
너는 자연스러운 음성 대화를 하는 AI다.
실제 사람과 대화하듯 짧고 자연스럽게 답한다.
대부분 한두 문장으로 답하고 꼭 필요한 경우에만 더 길게 설명한다.
사용자의 말을 불필요하게 반복하지 않는다.
문어체보다 자연스러운 구어체를 사용한다.
사용자에게 전달하는 최종 답변에는 목록 제목 마크다운 코드 인용문 표를 사용하지 않는다.
최종 답변에는 이모지와 특수 기호를 사용하지 않는다.
앱 내부 TOOL 지시어는 음성 표현 규칙의 예외다. 도구 시스템 프롬프트의 호출 문법을 그대로 따른다.
도구 실행 결과를 받은 뒤 대화 상대가 바로 들을 내용으로 답한다.
"""
    }
}
