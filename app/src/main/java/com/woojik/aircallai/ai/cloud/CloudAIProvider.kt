package com.woojik.aircallai.ai.cloud

import com.woojik.aircallai.ai.provider.AIProvider
import com.woojik.aircallai.ai.provider.AIProviderException
import com.woojik.aircallai.ai.provider.AIResponse
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.ai.provider.ProviderErrorKind
import com.woojik.aircallai.ai.provider.ProviderType
import com.woojik.aircallai.core.storage.CredentialManager
import kotlinx.coroutines.CancellationException

/**
 * PRD-04 Cloud Mode:
 * - 사용자가 직접 등록한 API Key만 사용하며 PRD-02 CredentialManager에만 보관된다.
 * - 앱이 해당 API를 직접 호출한다.
 */
class CloudAIProvider(
    private val credentials: CredentialManager,
    private val apiAdapter: CloudApiAdapter,
    private val endpointProvider: () -> Endpoint,
) : AIProvider {

    /** 사용자가 설정한 클라우드 서비스 접속 정보. */
    data class Endpoint(val baseUrl: String, val model: String)

    override val type = ProviderType.CLOUD
    override val displayName = "Cloud AI"

    override suspend fun isReady(): Boolean = credentials.load(KEY_SERVICE) != null

    override suspend fun respond(history: List<ChatMessage>): AIResponse {
        val started = System.currentTimeMillis()
        val apiKey = credentials.load(KEY_SERVICE)?.decodeToString()
            ?: throw AIProviderException(ProviderErrorKind.NO_KEY)

        val endpoint = endpointProvider()
        val text = try {
            apiAdapter.chat(apiKey, endpoint.baseUrl, endpoint.model, history)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AIProviderException) {
            throw e
        } catch (t: Throwable) {
            throw AIProviderException(ProviderErrorKind.NETWORK)
        }
        return AIResponse(
            message = ChatMessage(ChatMessage.Role.ASSISTANT, text),
            providerType = ProviderType.CLOUD,
            latencyMs = System.currentTimeMillis() - started,
        )
    }

    companion object {
        /** CredentialManager에 저장되는 서비스 키 (PRD-02 명세의 service 구분). */
        const val KEY_SERVICE = "cloud_ai"
    }
}
