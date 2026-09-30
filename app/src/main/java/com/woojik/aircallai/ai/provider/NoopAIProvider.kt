package com.woojik.aircallai.ai.provider

/**
 * Placeholder provider used until PRD-04 wires the real local/cloud providers.
 * Always ready, replies with a fixed message, no network access.
 */
class NoopAIProvider(
    override val type: ProviderType = ProviderType.LOCAL,
) : AIProvider {
    override val displayName: String = "Echo (placeholder)"

    override suspend fun isReady(): Boolean = true

    override suspend fun respond(history: List<ChatMessage>): AIResponse {
        val lastUser = history.lastOrNull { it.role == ChatMessage.Role.USER }
        val reply = "PRD-01 골격 응답: \"${lastUser?.content.orEmpty()}\" (아직 실제 AI Provider는 연결되지 않았습니다)"
        return AIResponse(
            message = ChatMessage(ChatMessage.Role.ASSISTANT, reply),
            providerType = type,
            latencyMs = 0,
        )
    }
}
