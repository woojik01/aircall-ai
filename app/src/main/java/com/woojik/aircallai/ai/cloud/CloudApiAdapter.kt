package com.woojik.aircallai.ai.cloud

import com.woojik.aircallai.ai.provider.ChatMessage

/**
 * PRD-04: Cloud Mode에서 앱이 선택한 서비스 API를 직접 호출한다.
 * 별도 AirCall AI 서버를 거치지 않는다 (PRD 원칙 3).
 */
interface CloudApiAdapter {
    /**
     * @param apiKey 사용자가 등록한 Key (CredentialManager에서만 나온다).
     * @param baseUrl 사용자가 선택한 API 엔드포인트 (OpenAI 호환 chat completions 형식).
     * @return 어시스턴트 응답 텍스트
     * @throws com.woojik.aircallai.ai.provider.AIProviderException AUTH_FAILED / QUOTA_EXCEEDED / NETWORK / API_ERROR
     */
    suspend fun chat(apiKey: String, baseUrl: String, history: List<ChatMessage>): String
}

/**
 * 테스트/설정 전 검증용 가짜 어댑터.
 */
class FakeCloudApiAdapter(
    private val validKeyPrefix: String = "sk-",
    private val reply: (String) -> String = { "cloud reply: " + it },
) : CloudApiAdapter {
    var networkCalls = 0
    override suspend fun chat(apiKey: String, baseUrl: String, history: List<ChatMessage>): String {
        networkCalls++
        if (!apiKey.startsWith(validKeyPrefix)) {
            throw com.woojik.aircallai.ai.provider.AIProviderException(
                com.woojik.aircallai.ai.provider.ProviderErrorKind.AUTH_FAILED)
        }
        return reply(history.lastOrNull { it.role == ChatMessage.Role.USER }?.content ?: "")
    }
}
