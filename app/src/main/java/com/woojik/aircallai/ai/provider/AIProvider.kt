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

    /** True when the provider is ready to be used (model loaded / credential present). */
    suspend fun isReady(): Boolean

    suspend fun respond(history: List<ChatMessage>): AIResponse
}
