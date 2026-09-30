package com.woojik.aircallai.ai.provider

import com.woojik.aircallai.settings.SettingsRepository

/**
 * PRD-04 Provider 전환:
 * 설정에서 Local/Cloud를 고르면 "다음 대화부터" 해당 Provider를 사용한다.
 * 현재 진행 중인 응답의 Provider를 중간에 바꾸지 않는다
 * (ConversationEngine이 턴 시작 시점의 provider를 사용).
 */
class ProviderRouter(
    private val settings: SettingsRepository,
    private val localProvider: AIProvider,
    private val cloudProvider: AIProvider,
) {
    fun current(): AIProvider =
        if (settings.aiProviderMode() == SettingsRepository.MODE_CLOUD) cloudProvider else localProvider
}
