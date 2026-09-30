package com.woojik.aircallai.ai.local

import com.woojik.aircallai.ai.provider.AIProvider
import com.woojik.aircallai.ai.provider.AIProviderException
import com.woojik.aircallai.ai.provider.AIResponse
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.ai.provider.ProviderErrorKind
import com.woojik.aircallai.ai.provider.ProviderType

/**
 * PRD-04 Local Mode: 인터넷 없이 대화 가능을 목표로 한다.
 * 실제 모델 SDK는 LocalModelAdapter 뒤에 격리된다.
 */
class LocalAIProvider(
    private val adapter: LocalModelAdapter,
) : AIProvider {

    override val type = ProviderType.LOCAL
    override val displayName = "Local AI"

    override suspend fun isReady(): Boolean = adapter.isModelAvailable() && adapter.isDeviceSupported()

    override suspend fun respond(history: List<ChatMessage>): AIResponse {
        val started = System.currentTimeMillis()
        if (!adapter.isDeviceSupported()) {
            throw AIProviderException(ProviderErrorKind.UNSUPPORTED_DEVICE)
        }
        if (!adapter.isModelAvailable()) {
            throw AIProviderException(ProviderErrorKind.MODEL_NOT_INSTALLED)
        }
        val text = try {
            adapter.generate(history)
        } catch (e: AIProviderException) {
            throw e
        } catch (e: OutOfMemoryError) {
            throw AIProviderException(ProviderErrorKind.MEMORY)
        } catch (t: Throwable) {
            throw AIProviderException(ProviderErrorKind.LOAD_FAILED)
        }
        return AIResponse(
            message = ChatMessage(ChatMessage.Role.ASSISTANT, text),
            providerType = ProviderType.LOCAL,
            latencyMs = System.currentTimeMillis() - started,
        )
    }
}
