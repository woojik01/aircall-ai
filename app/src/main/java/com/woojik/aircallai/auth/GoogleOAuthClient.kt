package com.woojik.aircallai.auth

/**
 * Google 사용자 데이터 권한 승인 구현.
 *
 * Android에서는 브라우저 + custom URI callback 기반의 OAuth Authorization Code 흐름을
 * 사용하지 않고 Google Play services AuthorizationClient가 권한 승인을 처리한다.
 * AuthorizationClient가 반환한 단기 access token만 앱의 기존 암호화 CredentialStore에 저장한다.
 *
 * Google 공식 Android 문서:
 * - 사용자 데이터 권한: AuthorizationClient
 * - 인증: Credential Manager
 * - access token은 단기 토큰이며 만료 후 authorize()를 다시 호출한다.
 */
class GoogleOAuthClient : OAuthProvider {
    override val provider = "gmail"

    /**
     * Google AuthorizationClient가 발급한 access token은 약 1시간 유효하다.
     * Android 클라이언트에서는 refresh token을 기기에 저장하지 않고,
     * 만료 시 AuthorizationClient.authorize()를 다시 호출한다.
     */
    override suspend fun refresh(tokens: OAuthTokens): OAuthTokens? = null

    override suspend fun startConnect(): OAuthCallbackResult.Rejected? = null

    override suspend fun handleCallback(
        provider: String,
        code: String,
        state: String?,
    ): OAuthTokens? = null

    companion object {
        // gmail.modify: 읽기 + 메일 상태/라벨 변경 등에 필요한 Gmail 권한.
        // gmail.send: 메일 보내기 권한. 기존 https://mail.google.com/보다 범위를 줄인다.
        const val SCOPE_GMAIL_MODIFY = "https://www.googleapis.com/auth/gmail.modify"
        const val SCOPE_CALENDAR_EVENTS = "https://www.googleapis.com/auth/calendar.events"
        const val SCOPE_GMAIL_SEND = "https://www.googleapis.com/auth/gmail.send"
        // Google integration is Gmail sending only; Calendar permissions are not requested.
        val REQUIRED_SCOPES = listOf(SCOPE_GMAIL_SEND)

        // 기존 코드/외부 호출과의 호환을 위해 유지하되 Authorization URL에는 사용하지 않는다.
        @Deprecated("Android에서는 AuthorizationClient를 사용합니다.")
        const val SCOPE = SCOPE_GMAIL_MODIFY

        @Deprecated("custom URI callback OAuth는 더 이상 사용하지 않습니다.")
        const val CALLBACK_PATH = "/oauth2redirect"

        @Deprecated("custom URI callback OAuth는 더 이상 사용하지 않습니다.")
        fun redirectUriFor(clientId: String): String? = null

        @Deprecated("custom URI callback OAuth는 더 이상 사용하지 않습니다.")
        fun callbackSchemeFor(clientId: String): String? = null
    }
}
