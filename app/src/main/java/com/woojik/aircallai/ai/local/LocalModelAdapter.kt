package com.woojik.aircallai.ai.local

import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.ai.provider.ProviderErrorKind

/**
 * PRD-04: 모델별 구현은 Adapter로 격리한다.
 * ConversationEngine/LocalAIProvider는 구체 모델 SDK를 직접 호출하지 않는다.
 */
interface LocalModelAdapter {
    /** 모델 파일과 실행 엔진이 기기에 준비되어 있는지 (modelsDir 기준). */
    suspend fun isModelAvailable(): Boolean

    /** 기기가 로컬 추론을 지원하는지 (ABI/메모리 등). */
    suspend fun isDeviceSupported(): Boolean

    /** 모델을 로드하고 응답을 생성한다. 실패 시 AIProviderException을 던진다. */
    suspend fun generate(history: List<ChatMessage>): String
}

/**
 * PRD-04 단계의 기본 어댑터: 실제 로컬 모델 연동 전까지
 * 모델 미설치 상태로 동작한다 (설치 후 실제 어댑터로 교체).
 */
class NoopLocalModelAdapter : LocalModelAdapter {
    override suspend fun isModelAvailable(): Boolean = false
    override suspend fun isDeviceSupported(): Boolean = true
    override suspend fun generate(history: List<ChatMessage>): String =
        throw com.woojik.aircallai.ai.provider.AIProviderException(ProviderErrorKind.MODEL_NOT_INSTALLED)
}
