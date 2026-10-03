package com.woojik.aircallai.auth

/**
 * PRD-09 Phase 2: GitHub OAuth 기기(Device) 인증 흐름.
 * 시스템 브라우저에서 사용자가 user_code를 입력해 승인하면 앱이 토큰 엔드포인트를 폴링한다.
 * GitHub 기기 흐름은 refresh token을 제공하지 않으므로 만료 시 재연결이 필요하다.
 * OAuth App Client ID는 민감 값이 아니므로 일반 설정에서 읽는다.
 */
class GitHubDeviceFlowClient(
    private val http: OAuthHttpPost,
    private val clientIdProvider: () -> String,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : OAuthProvider {
    override val provider = "github"

    /** 기기 인증 세션. 사용자에게 user_code와 verification_uri를 보여준다. */
    data class DeviceSession(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val intervalSeconds: Int,
        val expiresAtMs: Long,
    )

    sealed interface PollResult {
        /** 승인 완료. 토큰 획득. */
        data class Success(val tokens: OAuthTokens) : PollResult

        /** 사용자 승인 대기 중. interval 후 다시 폴링한다. */
        object Pending : PollResult

        /** 만료·거부·오류. 재시도 필요. */
        data class Failed(val reason: String) : PollResult
    }

    /** 기기 코드 발급을 요청한다. Client ID 미설정·네트워크 실패 시 null. */
    suspend fun startDeviceFlow(): DeviceSession? {
        val clientId = clientIdProvider().trim()
        if (clientId.isEmpty()) return null
        val (status, body) = http.postForm(
            DEVICE_CODE_URL,
            mapOf("client_id" to clientId, "scope" to SCOPE),
        )
        if (status !in 200..299) return null
        val deviceCode = extractString(body, "device_code") ?: return null
        val userCode = extractString(body, "user_code") ?: return null
        val verificationUri = extractString(body, "verification_uri") ?: return null
        return DeviceSession(
            deviceCode = deviceCode,
            userCode = userCode,
            verificationUri = verificationUri,
            intervalSeconds = extractNumber(body, "interval")?.toInt() ?: DEFAULT_INTERVAL_SECONDS,
            expiresAtMs = nowMs() + (extractNumber(body, "expires_in") ?: DEFAULT_EXPIRES_IN_SECONDS) * 1000,
        )
    }

    /** 토큰 엔드포인트를 1회 폴링한다. */
    suspend fun pollToken(session: DeviceSession): PollResult {
        val clientId = clientIdProvider().trim()
        val (status, body) = http.postForm(
            TOKEN_URL,
            mapOf(
                "client_id" to clientId,
                "device_code" to session.deviceCode,
                "grant_type" to GRANT_TYPE,
            ),
        )
        val error = extractString(body, "error")
        return when {
            status in 200..299 && error == null -> {
                val access = extractString(body, "access_token")
                    ?: return PollResult.Failed("GitHub 응답에 access_token이 없습니다")
                val expiresIn = extractNumber(body, "expires_in")
                PollResult.Success(
                    OAuthTokens(access, null, expiresIn?.let { nowMs() + it * 1000 }),
                )
            }
            error == "authorization_pending" || error == "slow_down" -> PollResult.Pending
            error == "expired_token" -> PollResult.Failed("기기 인증 코드가 만료되었습니다. 다시 연결해 주세요")
            error == "access_denied" -> PollResult.Failed("GitHub 연결이 거부되었습니다")
            else -> PollResult.Failed("GitHub 인증에 실패했습니다")
        }
    }

    /** GitHub 기기 흐름은 refresh token이 없어 갱신을 지원하지 않는다(재연결 필요). */
    override suspend fun refresh(tokens: OAuthTokens): OAuthTokens? = null

    /** 미사용: 기기 흐름은 callback을 사용하지 않는다. */
    override suspend fun startConnect(): OAuthCallbackResult.Rejected? = null
    override suspend fun handleCallback(provider: String, code: String, state: String?): OAuthTokens? = null

    companion object {
        const val SCOPE = "repo issues"
        private const val DEVICE_CODE_URL = "https://github.com/login/device/code"
        private const val TOKEN_URL = "https://github.com/login/oauth/access_token"
        private const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
        private const val DEFAULT_INTERVAL_SECONDS = 5
        private const val DEFAULT_EXPIRES_IN_SECONDS = 900L

        /** JSON 문자열 필드 추출. 의존성 없이 최소 파싱만 한다. */
        fun extractString(body: String, key: String): String? =
            Regex("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)

        /** JSON 숫자 필드 추출. */
        fun extractNumber(body: String, key: String): Long? =
            Regex("\"" + key + "\"\\s*:\\s*(-?\\d+)").find(body)?.groupValues?.get(1)?.toLongOrNull()
    }
}
