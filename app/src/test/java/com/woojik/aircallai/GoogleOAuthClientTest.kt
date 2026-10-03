package com.woojik.aircallai

import com.woojik.aircallai.auth.GoogleOAuthClient
import com.woojik.aircallai.auth.OAuthHttpPost
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD-09 Phase 3: Google OAuth 흐름 단위 테스트 (fake HTTP).
 * PKCE 파라미터, code 교환, refresh 갱신, Client ID 미설정을 검증한다.
 */
class GoogleOAuthClientTest {

    private class FakeHttp(
        private val responder: (url: String, form: Map<String, String>) -> Pair<Int, String>,
    ) : OAuthHttpPost {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        override suspend fun postForm(url: String, form: Map<String, String>): Pair<Int, String> {
            calls.add(url to form)
            return responder(url, form)
        }
    }

    private fun client(http: OAuthHttpPost, clientId: String = "google-client.apps.googleusercontent.com") =
        GoogleOAuthClient(http, { clientId }, nowMs = { 2000L })

    @Test
    fun buildAuthRequestRequiresClientId() {
        val http = FakeHttp { _, _ -> 200 to "{}" }
        assertNull(client(http, clientId = "").buildAuthRequest("com.woojik.aircallai://oauth2redirect"))
    }

    @Test
    fun authRequestContainsPkceAndState() {
        val http = FakeHttp { _, _ -> 200 to "{}" }
        val request = client(http).buildAuthRequest("com.woojik.aircallai://oauth2redirect")!!
        // PKCE S256: code_challenge는 verifier의 SHA-256(base64url)이어야 한다.
        assertTrue(request.authUrl.contains("code_challenge_method=S256"))
        val challenge = Regex("code_challenge=([^&]+)").find(request.authUrl)!!.groupValues[1]
        assertEquals(GoogleOAuthClient.s256(request.codeVerifier), challenge)
        // state가 URL에 포함되고 검증 가능하다.
        assertTrue(request.authUrl.contains("state="))
        assertNotNull(request.state)
        // refresh token을 받기 위한 파라미터.
        assertTrue(request.authUrl.contains("access_type=offline"))
        assertTrue(request.authUrl.contains("scope="))
    }

    @Test
    fun exchangeCodeReturnsTokensWithRefresh() = runTest {
        val http = FakeHttp { url, form ->
            assertEquals("https://oauth2.googleapis.com/token", url)
            assertEquals("authorization_code", form["grant_type"])
            assertEquals("the-code", form["code"])
            assertEquals("the-verifier", form["code_verifier"])
            200 to "{\"access_token\":\"ya29.acc\",\"refresh_token\":\"1//rt\",\"expires_in\":3600,\"token_type\":\"Bearer\"}"
        }
        val tokens = client(http).exchangeCode("the-code", "the-verifier", "com.woojik.aircallai://oauth2redirect")!!
        assertEquals("ya29.acc", tokens.accessToken)
        assertEquals("1//rt", tokens.refreshToken)
        assertEquals(2000L + 3_600_000L, tokens.expiresAtEpochMs)
    }

    @Test
    fun exchangeCodeFailureReturnsNull() = runTest {
        val http = FakeHttp { _, _ -> 400 to "{\"error\":\"invalid_grant\"}" }
        assertNull(client(http).exchangeCode("bad", "v", "com.woojik.aircallai://oauth2redirect"))
    }

    @Test
    fun refreshReturnsNewAccessTokenKeepingRefreshToken() = runTest {
        val http = FakeHttp { _, form ->
            assertEquals("refresh_token", form["grant_type"])
            assertEquals("1//rt", form["refresh_token"])
            200 to "{\"access_token\":\"ya29.new\",\"expires_in\":3600,\"token_type\":\"Bearer\"}"
        }
        val tokens = client(http).refresh(
            com.woojik.aircallai.auth.OAuthTokens("ya29.old", "1//rt", 1000L),
        )!!
        assertEquals("ya29.new", tokens.accessToken)
        assertEquals("1//rt", tokens.refreshToken)
        assertEquals(2000L + 3_600_000L, tokens.expiresAtEpochMs)
    }

    @Test
    fun refreshWithoutRefreshTokenReturnsNull() = runTest {
        val http = FakeHttp { _, _ -> 200 to "{}" }
        assertNull(client(http).refresh(com.woojik.aircallai.auth.OAuthTokens("ya29.old", null, null)))
    }

    @Test
    fun refreshFailureReturnsNull() = runTest {
        val http = FakeHttp { _, _ -> 401 to "{\"error\":\"invalid_grant\"}" }
        assertNull(client(http).refresh(com.woojik.aircallai.auth.OAuthTokens("ya29.old", "1//rt", null)))
    }
}
