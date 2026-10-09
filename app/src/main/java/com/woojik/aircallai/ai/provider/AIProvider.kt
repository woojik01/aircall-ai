package com.woojik.aircallai.ai.provider

/**
 * Abstract chat turn used across the app. UI-agnostic on purpose.
 */
data class ChatMessage(
    val role: Role,
    val content: String,
) {
    enum class Role { USER, ASSISTANT, SYSTEM }
}

data class AIResponse(
    val message: ChatMessage,
    val providerType: ProviderType,
    val latencyMs: Long,
)

enum class ProviderType { LOCAL, CLOUD }

/**
 * PRD-01 core abstraction: ConversationEngine depends only on this interface.
 * LocalAIProvider (PRD-04) and CloudAIProvider (PRD-04) implement it later.
 */
interface AIProvider {
    val type: ProviderType
    val displayName: String

    /** Prerequisite check (model file/device support or credential); inference may still fail. */
    suspend fun isReady(): Boolean

    suspend fun respond(history: List<ChatMessage>): AIResponse
    /** Cumulative text; adapters without streaming still produce one final update. */
    suspend fun respondStreaming(history: List<ChatMessage>, onText: suspend (String) -> Unit): AIResponse {
        return respond(history).also { onText(it.message.content) }
    }
}
