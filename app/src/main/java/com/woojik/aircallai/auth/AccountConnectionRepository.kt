package com.woojik.aircallai.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * PRD-09 계정 연결 저장소. GitHub/Google 연결 상태를 서비스별로 독립 관리한다.
 * UI는 이 상태를 화면에 표시하고, Tool은 미연결/만료 상태에서 안내 응답을 만든다.
 * 상태 갱신은 토큰 값을 포함하지 않으므로 StateFlow 노출이 안전하다.
 */
class AccountConnectionRepository(
    private val store: OAuthCredentialStore,
    providers: List<OAuthProvider>,
) {
    private val providerMap = providers.associateBy { it.provider }
    private val states = MutableStateFlow(
        providers.associate { it.provider to ConnectionAccount(it.provider, null, ConnectionStatus.NOT_CONNECTED) },
    )
    val connections: StateFlow<Map<String, ConnectionAccount>> = states

    /** 저장된 자격증명 기준으로 상태를 복원한다(앱 재시작 후). */
    suspend fun refresh(nowEpochMs: Long) {
        val updated = states.value.toMutableMap()
        for (provider in providerMap.keys) {
            val tokens = store.load(provider)
            val savedName = store.loadDisplayName(provider)
            updated[provider] = ConnectionAccount(
                provider = provider,
                displayName = savedName,
                status = when {
                    tokens == null -> ConnectionStatus.NOT_CONNECTED
                    tokens.isExpired(nowEpochMs) && tokens.refreshToken == null -> ConnectionStatus.REAUTH_REQUIRED
                    tokens.isExpired(nowEpochMs) -> ConnectionStatus.EXPIRED
                    else -> ConnectionStatus.CONNECTED
                },
            )
        }
        states.value = updated
    }

    suspend fun connect(provider: String, tokens: OAuthTokens, displayName: String?, nowEpochMs: Long) {
        store.save(provider, tokens, displayName)
        update(provider, ConnectionStatus.CONNECTED, displayName)
        refresh(nowEpochMs)
    }

    /** PRD-09 연결 해제: 해당 서비스만 초기화한다. 계정 식별자도 함께 지운다. */
    suspend fun disconnect(provider: String) {
        store.disconnect(provider)
        update(provider, ConnectionStatus.NOT_CONNECTED, null)
    }

    suspend fun mark(provider: String, status: ConnectionStatus, displayName: String? = null) {
        val current = states.value[provider] ?: return
        update(
            provider,
            status,
            if (displayName != null) displayName else current.displayName,
        )
    }

    private fun update(provider: String, status: ConnectionStatus, displayName: String?) {
        val current = states.value[provider] ?: return
        val updated = states.value.toMutableMap()
        updated[provider] = ConnectionAccount(
            provider = provider,
            displayName = displayName,
            status = status,
        )
        states.value = updated
    }

    /** Tool 계층용: 연결되어 유효한 access token이 있는지. */
    suspend fun hasValidCredential(provider: String, nowEpochMs: Long): Boolean {
        val refresher = providerMap[provider] ?: return false
        return store.validAccessToken(provider, refresher, nowEpochMs) != null
    }

    /** 인증 만료 시 안내 문구. 내부 enum 이름을 그대로 노출하지 않는다. */
    fun statusMessage(account: ConnectionAccount): String = when (account.status) {
        ConnectionStatus.NOT_CONNECTED ->
            account.provider + " 계정이 연결되어 있지 않습니다. 연결하면 이 기능을 사용할 수 있습니다."
        ConnectionStatus.CONNECTING -> account.provider + " 연결을 진행 중입니다."
        ConnectionStatus.CONNECTED -> account.provider + " 계정이 연결되어 있습니다."
        ConnectionStatus.EXPIRED -> account.provider + " 로그인이 만료되었습니다. 다시 연결해 주세요."
        ConnectionStatus.REAUTH_REQUIRED -> account.provider + " 재인증이 필요합니다. 다시 연결해 주세요."
        ConnectionStatus.ERROR -> account.provider + " 연결에 문제가 발생했습니다. 다시 시도해 주세요."
    }

    /**
     * PRD-09 callback 검증. provider 불일치·code 누락·state 불일치를 거부한다.
     * state는 startConnect에서 발급한 것과 일치해야 한다(구현이 expectedState를 전달).
     */
    fun validateCallback(
        expectedProvider: String,
        actualProvider: String,
        code: String?,
        state: String?,
        expectedState: String?,
    ): OAuthCallbackResult {
        if (actualProvider != expectedProvider) {
            return OAuthCallbackResult.Rejected("provider가 일치하지 않습니다")
        }
        if (code.isNullOrBlank()) {
            return OAuthCallbackResult.Rejected("인증 코드가 없습니다")
        }
        if (expectedState != null && state != expectedState) {
            return OAuthCallbackResult.Rejected("state가 일치하지 않습니다")
        }
        return OAuthCallbackResult.Success(actualProvider, code)
    }
}
