package com.woojik.aircallai.conversation

import com.woojik.aircallai.ai.provider.AIProvider
import com.woojik.aircallai.ai.provider.AIProviderException
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.audio.AudioError
import com.woojik.aircallai.tools.ToolActivityBus
import com.woojik.aircallai.tools.ToolActivityPhraser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConversationEngine(
    private var provider: AIProvider,
    private val activityBus: ToolActivityBus? = null,
) {
    private val _state = MutableStateFlow<ConversationState>(ConversationState.Idle)
    val state: StateFlow<ConversationState> = _state.asStateFlow()
    private val _transcript = MutableStateFlow<List<ChatMessage>>(emptyList())
    val transcript: StateFlow<List<ChatMessage>> = _transcript.asStateFlow()

    /**
     * 진행 내레이션: 도구 실행 중 실시간으로 바뀌는 한 줄 안내 문장.
     * UI는 Processing 상태에서 이 값을 진행 표시로 노출하고, 음성 모드에서는 낮은 볼륨으로 날독할 수 있다.
     * null이면 표시할 진행 문장이 없는 상태다.
     */
    private val _activity = MutableStateFlow<String?>(null)
    val activity: StateFlow<String?> = _activity.asStateFlow()

    val activeProvider: AIProvider get() = provider

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        if (activityBus != null) {
            activityScope.launch {
                activityBus.events.collect { event ->
                    if (_state.value is ConversationState.Processing) {
                        _activity.value = ToolActivityPhraser.text(event)
                    }
                }
            }
        }
    }

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

    suspend fun submitUserMessage(text: String, voiceMode: Boolean = false) {
        if (text.isBlank()) return
        val active = provider
        val userMessage = ChatMessage(ChatMessage.Role.USER, text)
        _transcript.update { it + userMessage }
        _activity.value = null
        _state.value = ConversationState.Processing(userMessage)

        try {
            val requestHistory = if (voiceMode) {
                listOf(ChatMessage(ChatMessage.Role.SYSTEM, VOICE_SYSTEM_PROMPT)) + _transcript.value
            } else {
                _transcript.value
            }
            val response = active.respond(requestHistory)
            val responseText = if (voiceMode) {
                VoiceResponseSanitizer.sanitize(response.message.content)
            } else {
                response.message.content
            }
            val assistantMessage = response.message.copy(content = responseText)
            _transcript.update { it + assistantMessage }
            _state.value = ConversationState.Speaking(assistantMessage)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AIProviderException) {
            _state.value = ConversationState.Error(
                ConversationState.ErrorKind.AI_PROVIDER,
                e.message ?: e.kind.userMessage,
            )
        } catch (t: Throwable) {
            _state.value = ConversationState.Error(
                ConversationState.ErrorKind.AI_PROVIDER,
                t.message ?: "AI 응답 생성 실패",
            )
        }
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
        _activity.value = null
        _state.update { if (it is ConversationState.Speaking) ConversationState.Idle else it }
    }

    fun latestAssistantMessage(): ChatMessage? =
        _transcript.value.lastOrNull { it.role == ChatMessage.Role.ASSISTANT }

    fun reset() {
        _transcript.value = emptyList()
        _activity.value = null
        _state.value = ConversationState.Idle
    }

    companion object {
        private const val VOICE_SYSTEM_PROMPT = """
너는 자연스러운 음성 대화를 하는 AI다.
실제 사람과 대화하듯 짧고 자연스럽게 답한다.
대부분 한두 문장으로 답하고 꼭 필요한 경우에만 더 길게 설명한다.
사용자의 말을 불필요하게 반복하지 않는다.
문어체보다 자연스러운 구어체를 사용한다.
목록 제목 마크다운 코드 인용문 표를 사용하지 않는다.
이모지와 특수 기호를 사용하지 않는다.
대화 상대가 바로 들을 내용만 답한다.
"""
    }
}
