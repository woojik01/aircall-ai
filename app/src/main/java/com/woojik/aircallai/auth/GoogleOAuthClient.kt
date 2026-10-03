package com.woojik.aircallai.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * PRD-09 Phase 3: Google OAuth Authorization Code + PKCE 흐름.
 * 시스템 브라우저로 Google 인증 화면을 열고, 앱으로 돌아온 code를 토큰으로 교환한다.
 * access_type=offline로 refresh token을 받아 만료 시 자동 갱신한다.
 * Google OAuth Client ID는 공개 값이므로 일반 설정에서 읽는다.
 * Android 유형 OAuth 클라이언트를 사용하므로 콜백은 리버스 클라이언트 ID 스킴
 * (com.googleusercontent.apps.<번호>-<해시>:/oauth2redirect)로 돌아온다.
 */
class GoogleOAuthClient(
    private val http: OAuthHttpPost,
    private val clientIdProvider: () -> String,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
) : OAuthProvider {
    override val provider = "gmail"

    /** 인증 요청. state/code_verifier는 콜백 검증과 토큰 교환에 필요하다. */
    data class AuthRequest(
        val authUrl: String,
        val state: String,
        val codeVerifier: String,
    )

    /** 인증 URL을 만든다. Client ID 미설정 시 null. */
    fun buildAuthRequest(redirectUri: String): AuthRequest? {
        val clientId = clientIdProvider().trim()
        if (clientId.isEmpty()) return null
        val state = randomToken()
        val verifier = randomToken()
        val challenge = s256(verifier)
        val url = StringBuilder(AUTH_ENDPOINT)
            .append("?response_type=code")
            .append("&client_id=").append(enc(clientId))
            .append("&redirect_uri=").append(enc(redirectUri))
            .append("&scope=").append(enc(SCOPE))
            .append("&state=").append(enc(state))
            .append("&code_challenge=").append(enc(challenge))
            .append("&code_challenge_method=S256")
            .append("&access_type=offline")
            .append("&prompt=consent")
            .toString()
        return AuthRequest(url, state, verifier)
    }

    /** 미사용: 인증 시작은 buildAuthRequest + 시스템 브라우저로 수행한다. */
    override suspend fun startConnect(): OAuthCallbackResult.Rejected? = null

    /** 미사용: 콜백 code 교환은 exchangeCode에서 수행한다(state/PKCE 검증 포함). */
    override suspend fun handleCallback(provider: String, code: String, state: String?): OAuthTokens? = null

    /** 승인된 code를 access/refresh token으로 교환한다. 실패 시 null. */
    suspend fun exchangeCode(code: String, codeVerifier: String, redirectUri: String): OAuthTokens? {
        val clientId = clientIdProvider().trim()
        val (status, body) = http.postForm(
            TOKEN_ENDPOINT,
            mapOf(
                "client_id" to clientId,
                "grant_type" to "authorization_code",
                "code" to code,
                "code_verifier" to codeVerifier,
                "redirect_uri" to redirectUri,
            ),
        )
        if (status !in 200..299) return null
        return tokensFrom(body)
    }

    /** PRD-09 §9: refresh token으로 access token을 갱신한다. 갱신 불가 시 null. */
    override suspend fun refresh(tokens: OAuthTokens): OAuthTokens? {
        val refreshToken = tokens.refreshToken ?: return null
        val clientId = clientIdProvider().trim()
        val (status, body) = http.postForm(
            TOKEN_ENDPOINT,
            mapOf(
                "client_id" to clientId,
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
            ),
        )
        if (status !in 200..299) return null
        val access = extractString(body, "access_token") ?: return null
        val expiresIn = extractNumber(body, "expires_in")
        return OAuthTokens(
            accessToken = access,
            refreshToken = extractString(body, "refresh_token") ?: refreshToken,
            expiresAtEpochMs = expiresIn?.let { nowMs() + it * 1000 },
        )
    }

    private fun tokensFrom(body: String): OAuthTokens? {
        val access = extractString(body, "access_token") ?: return null
        val expiresIn = extractNumber(body, "expires_in")
        return OAuthTokens(
            accessToken = access,
            refreshToken = extractString(body, "refresh_token"),
            expiresAtEpochMs = expiresIn?.let { nowMs() + it * 1000 },
        )
    }

    private fun randomToken(bytes: Int = 32): String {
        val buf = ByteArray(bytes)
        random.nextBytes(buf)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf)
    }

    companion object {
        const val SCOPE = "https://mail.google.com/"
        private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        const val CALLBACK_PATH = "/oauth2redirect"

        /**
         * Android 유형 OAuth 클라이언트의 콜백 URI.
         * Client ID(<번호>-<해시>.apps.googleusercontent.com)를 뒤집은
         * 리버스 스킴(com.googleusercontent.apps.<번호>-<해시>)으로 계산한다.
         * 미등록 Client ID에는 null을 돌려준다.
         */
        fun redirectUriFor(clientId: String): String? {
            val id = clientId.trim()
            if (!id.endsWith(".apps.googleusercontent.com")) return null
            val suffix = id.substringBefore(".apps.googleusercontent.com")
                .split(".").reversed().joinToString(".")
            if (suffix.isEmpty()) return null
            return "com.googleusercontent.apps." + suffix + ":" + CALLBACK_PATH
        }

        /** 콜백 URI에서 스킴 부분(리버스 클라이언트 ID). */
        fun callbackSchemeFor(clientId: String): String? =
            redirectUriFor(clientId)?.removeSuffix(":" + CALLBACK_PATH)

        fun enc(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

        fun s256(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(verifier.toByteArray(Charsets.US_ASCII))
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        }

        fun extractString(body: String, key: String): String? =
            Regex("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)

        fun extractNumber(body: String, key: String): Long? =
            Regex("\"" + key + "\"\\s*:\\s*(-?\\d+)").find(body)?.groupValues?.get(1)?.toLongOrNull()
    }
}
