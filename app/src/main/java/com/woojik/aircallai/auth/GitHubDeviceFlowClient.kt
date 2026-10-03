package com.woojik.aircallai.auth

class GitHubDeviceFlowClient(
    private val http: OAuthHttpPost,
    private val clientIdProvider: () -> String,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : OAuthProvider {
    override val provider = "github"

    data class DeviceSession(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val intervalSeconds: Int,
        val expiresAtMs: Long,
    )

    sealed interface StartResult {
        data class Success(val session: DeviceSession) : StartResult
        data class Failed(val reason: String) : StartResult
    }

    sealed interface PollResult {
        data class Success(val tokens: OAuthTokens) : PollResult
        object Pending : PollResult
        data class Failed(val reason: String) : PollResult
    }

    suspend fun startDeviceFlow(): DeviceSession? =
        (startDeviceFlowDetailed() as? StartResult.Success)?.session

    suspend fun startDeviceFlowDetailed(): StartResult {
        val clientId = clientIdProvider().trim()
        if (clientId.isEmpty()) {
            return StartResult.Failed("GitHub OAuth App Client ID가 설정되지 않았습니다.")
        }

        val (status, body) = http.postForm(
            DEVICE_CODE_URL,
            mapOf("client_id" to clientId, "scope" to SCOPE),
        )
        if (status !in 200..299) {
            val error = extractString(body, "error")
            val description = extractString(body, "error_description")
            return StartResult.Failed(
                when (error) {
                    "device_flow_disabled" ->
                        "GitHub OAuth App에서 Device Flow가 비활성화되어 있습니다. GitHub Developer settings에서 Enable Device Flow를 켜 주세요."
                    "incorrect_client_credentials" ->
                        "GitHub OAuth App Client ID가 올바르지 않습니다."
                    else -> buildString {
                        append("GitHub 기기 인증 요청 실패 (HTTP ").append(status).append(")")
                        if (!error.isNullOrBlank()) append(": ").append(error)
                        if (!description.isNullOrBlank()) append(" - ").append(description)
                    },
                },
            )
        }

        val deviceCode = extractString(body, "device_code")
            ?: return StartResult.Failed("GitHub 응답에 device_code가 없습니다.")
        val userCode = extractString(body, "user_code")
            ?: return StartResult.Failed("GitHub 응답에 user_code가 없습니다.")
        val verificationUri = extractString(body, "verification_uri")
            ?: return StartResult.Failed("GitHub 응답에 verification_uri가 없습니다.")

        return StartResult.Success(
            DeviceSession(
                deviceCode,
                userCode,
                verificationUri,
                extractNumber(body, "interval")?.toInt() ?: DEFAULT_INTERVAL_SECONDS,
                nowMs() + (extractNumber(body, "expires_in") ?: DEFAULT_EXPIRES_IN_SECONDS) * 1000,
            ),
        )
    }

    suspend fun pollToken(session: DeviceSession): PollResult {
        val clientId = clientIdProvider().trim()
        if (clientId.isEmpty()) return PollResult.Failed("GitHub OAuth App Client ID가 설정되지 않았습니다.")

        val (status, body) = http.postForm(
            TOKEN_URL,
            mapOf(
                "client_id" to clientId,
                "device_code" to session.deviceCode,
                "grant_type" to GRANT_TYPE,
            ),
        )
        val error = extractString(body, "error")
        val description = extractString(body, "error_description")

        return when {
            status in 200..299 && error == null -> {
                val access = extractString(body, "access_token")
                    ?: return PollResult.Failed("GitHub 응답에 access_token이 없습니다.")
                PollResult.Success(
                    OAuthTokens(
                        access,
                        extractString(body, "refresh_token"),
                        extractNumber(body, "expires_in")?.let { nowMs() + it * 1000 },
                    ),
                )
            }
            error == "authorization_pending" || error == "slow_down" -> PollResult.Pending
            error == "expired_token" || error == "token_expired" ->
                PollResult.Failed("GitHub 기기 인증 코드가 만료되었습니다. 다시 연결해 주세요.")
            error == "access_denied" ->
                PollResult.Failed("GitHub 연결이 거부되었습니다.")
            error == "device_flow_disabled" ->
                PollResult.Failed("GitHub OAuth App에서 Device Flow가 비활성화되어 있습니다. GitHub Developer settings에서 Enable Device Flow를 켜 주세요.")
            error == "incorrect_client_credentials" ->
                PollResult.Failed("GitHub OAuth App Client ID가 올바르지 않습니다.")
            else -> {
                val suffix = if (!description.isNullOrBlank()) " - $description" else ""
                PollResult.Failed(
                    "GitHub 인증에 실패했습니다" +
                        if (error.isNullOrBlank()) " (HTTP $status)" else " ($error)" +
                        suffix,
                )
            }
        }
    }

    override suspend fun refresh(tokens: OAuthTokens): OAuthTokens? = null
    override suspend fun startConnect(): OAuthCallbackResult.Rejected? = null
    override suspend fun handleCallback(provider: String, code: String, state: String?): OAuthTokens? = null

    companion object {
        const val SCOPE = "repo issues"
        private const val DEVICE_CODE_URL = "https://github.com/login/device/code"
        private const val TOKEN_URL = "https://github.com/login/oauth/access_token"
        private const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
        private const val DEFAULT_INTERVAL_SECONDS = 5
        private const val DEFAULT_EXPIRES_IN_SECONDS = 900L

        fun extractString(body: String, key: String): String? =
            Regex("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)

        fun extractNumber(body: String, key: String): Long? =
            Regex("\"" + key + "\"\\s*:\\s*(-?\\d+)").find(body)?.groupValues?.get(1)?.toLongOrNull()
    }
}
