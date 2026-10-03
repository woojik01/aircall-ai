package com.woojik.aircallai

import com.woojik.aircallai.auth.GoogleOAuthClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * PRD-09: Android 유형 Google OAuth 클라이언트의 리버스 스킴 콜백 URI 계산 검증.
 * Client ID는 공개값이므로 가짜 문자열만 사용한다.
 */
class GoogleOAuthRedirectUriTest {

    @Test
    fun redirectUriIsReverseClientIdScheme() {
        val uri = GoogleOAuthClient.redirectUriFor("123456789012-abc123defg.apps.googleusercontent.com")
        assertEquals("com.googleusercontent.apps.123456789012-abc123defg:/oauth2redirect", uri)
    }

    @Test
    fun callbackSchemeMatchesManifestScheme() {
        val scheme = GoogleOAuthClient.callbackSchemeFor("123456789012-abc123defg.apps.googleusercontent.com")
        assertEquals("com.googleusercontent.apps.123456789012-abc123defg", scheme)
    }

    @Test
    fun invalidClientIdReturnsNull() {
        assertNull(GoogleOAuthClient.redirectUriFor(""))
        assertNull(GoogleOAuthClient.redirectUriFor("not-a-client-id"))
    }
}
