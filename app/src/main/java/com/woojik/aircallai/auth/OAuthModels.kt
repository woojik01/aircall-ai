package com.woojik.aircallai.auth

/**
 * PRD-09 계정 연결 상태. UI에는 enum 이름 대신 이해하기 쉬운 문구를 표시한다.
 * 한 서비스의 상태 변화가 다른 서비스에 영향을 주지 않는다.
 */
enum class ConnectionStatus {
    NOT_CONNECTED,
    CONNECTING,
    CONNECTED,
    EXPIRED,
    REAUTH_REQUIRED,
    ERROR,
}

/** PRD-09 서비스별 연결 정보. 토큰 값은 포함하지 않고 상태만 노출한다. */
data class ConnectionAccount(
    val provider: String,
    val displayName: String?,
    val status: ConnectionStatus,
    val errorMessage: String? = null,
)

/**
 * PRD-09 OAuth 자격증명 모델.
 * access/refresh token은 CredentialManager(Keystore 암호화)에만 저장되며
 * 로그, ToolResult, AI prompt, 실행 메타데이터에 절대 노출되지 않는다.
 */
data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochMs: Long?,
) {
    /** 만료 시각이 지났는지. 만료 시각을 모르면 만료되지 않은 것으로 본다. */
    fun isExpired(nowEpochMs: Long): Boolean =
        expiresAtEpochMs != null && nowEpochMs >= expiresAtEpochMs
}

/** PRD-09 callback 검증 결과. state 불일치·code 누락·provider 불일치는 여기서 거부한다. */
sealed interface OAuthCallbackResult {
    data class Success(val provider: String, val code: String) : OAuthCallbackResult
    data class Rejected(val reason: String) : OAuthCallbackResult
}
