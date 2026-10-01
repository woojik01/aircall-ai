package com.woojik.aircallai.conversation

import com.woojik.aircallai.ai.provider.AIProvider
import com.woojik.aircallai.ai.provider.AIProviderException
import com.woojik.aircallai.ai.provider.AIResponse
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.audio.AudioError
import com.woojik.aircallai.tools.ToolCallProtocol
import com.woojik.aircallai.tools.ToolExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * PRD-06: toolExecutor가 있으면 사용 가능한 Tool을 시스템 프롬프트로 알리고,
 * AI 응답이 [ToolCallProtocol] 형식이면 Tool을 실행해 결과를 다시 AI에게 전달한다.
 * Tool 호출문과 결과는 화면/음성에 노출하지 않고 최종 답변만 transcript에 남긴다.
 */
class ConversationEngine(
    private var provider: AIProvider,
    private val toolExecutor: ToolExecutor? = null,
) {
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

    suspend fun submitUserMessage(text: String, voiceMode: Boolean = false) {
        if (text.isBlank()) return
        val active = provider
        val userMessage = ChatMessage(ChatMessage.Role.USER, text)
        _transcript.update { it + userMessage }
        _state.value = ConversationState.Processing(userMessage)

        try {
            val systemPrompts = buildList {
                if (voiceMode) add(ChatMessage(ChatMessage.Role.SYSTEM, VOICE_SYSTEM_PROMPT))
                toolExecutor?.let { add(ChatMessage(ChatMessage.Role.SYSTEM, ToolCallProtocol.systemPrompt(it.tools))) }
            }
            val response = respondWithTools(active, systemPrompts + _transcript.value)
            val responseText = if (voiceMode) {
                VoiceResponseSanitizer.sanitize(response.message.content)
            } else {
                response.message.content
            }
            val assistantMessage = response.message.copy(content = responseText)
            _transcript.update { it + assistantMessage }
            _state.value = ConversationState.Speaking(assistantMessage)
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

    private suspend fun respondWithTools(active: AIProvider, history: List<ChatMessage>): AIResponse {
        var context = history
        var response = active.respond(context)
        val executor = toolExecutor ?: return response
        repeat(MAX_TOOL_CALLS_PER_TURN) {
            val call = ToolCallProtocol.parse(response.message.content) ?: return response
            val result = executor.execute(call)
            context = context +
                ChatMessage(ChatMessage.Role.ASSISTANT, response.message.content) +
                ChatMessage(ChatMessage.Role.SYSTEM, ToolCallProtocol.resultMessage(call, result))
            response = active.respond(context)
        }
        return if (ToolCallProtocol.parse(response.message.content) != null) {
            response.copy(message = response.message.copy(content = "요청을 처리하지 못했어요. 다시 말씀해 주세요"))
        } else response
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
        _transcript.value = emptyList()
        _state.value = ConversationState.Idle
    }

    companion object {
        private const val MAX_TOOL_CALLS_PER_TURN = 3
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
