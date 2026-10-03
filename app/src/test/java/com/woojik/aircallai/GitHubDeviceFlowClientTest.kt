package com.woojik.aircallai

import com.woojik.aircallai.auth.GitHubDeviceFlowClient
import com.woojik.aircallai.auth.OAuthHttpPost
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD-09 Phase 2: GitHub 기기 인증 흐름 단위 테스트 (fake HTTP).
 * 세션 파싱, 폴링 결과 분기(pending/success/failure), Client ID 미설정, 갱신 미지원을 검증한다.
 */
class GitHubDeviceFlowClientTest {

    private class FakeHttp(
        private val responder: (url: String, form: Map<String, String>) -> Pair<Int, String>,
    ) : OAuthHttpPost {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        override suspend fun postForm(url: String, form: Map<String, String>): Pair<Int, String> {
            calls.add(url to form)
            return responder(url, form)
        }
    }

    private val deviceCodeBody = "{\"device_code\":\"DEV123\",\"user_code\":\"ABCD-1234\"," +
        "\"verification_uri\":\"https://github.com/login/device\",\"expires_in\":900,\"interval\":5}"

    private fun client(http: OAuthHttpPost, clientId: String = "Iv1.cid") =
        GitHubDeviceFlowClient(http, { clientId }, nowMs = { 1000L })

    @Test
    fun startDeviceFlowRequiresClientId() = runTest {
        val http = FakeHttp { _, _ -> 200 to deviceCodeBody }
        assertNull(client(http, clientId = "").startDeviceFlow())
        // Client ID가 비어 있으면 네트워크를 호출하지 않는다.
        assertEquals(0, http.calls.size)
    }

    @Test
    fun startDeviceFlowParsesSession() = runTest {
        val http = FakeHttp { url, form ->
            assertEquals("https://github.com/login/device/code", url)
            assertEquals("Iv1.cid", form["client_id"])
            200 to deviceCodeBody
        }
        val session = client(http).startDeviceFlow()!!
        assertEquals("DEV123", session.deviceCode)
        assertEquals("ABCD-1234", session.userCode)
        assertEquals("https://github.com/login/device", session.verificationUri)
        assertEquals(5, session.intervalSeconds)
        // expires_in(900초)을 현재 시각(1000ms) 기준으로 계산한다.
        assertEquals(1000L + 900_000L, session.expiresAtMs)
    }

    @Test
    fun startDeviceFlowReturnsNullOnHttpFailure() = runTest {
        val http = FakeHttp { _, _ -> 500 to "{}" }
        assertNull(client(http).startDeviceFlow())
    }

    @Test
    fun pollTokenPendingWhileUserHasNotApproved() = runTest {
        val http = FakeHttp { _, _ ->
            200 to "{\"error\":\"authorization_pending\"}"
        }
        val session = client(http).startDeviceFlow()!!
        val result = client(http).pollToken(session)
        assertTrue(result is GitHubDeviceFlowClient.PollResult.Pending)
    }

    @Test
    fun pollTokenSuccessReturnsTokensWithExpiry() = runTest {
        val http = FakeHttp { url, _ ->
            if (url.endsWith("/device/code")) 200 to deviceCodeBody
            else 200 to "{\"access_token\":\"gho_token\",\"token_type\":\"bearer\",\"expires_in\":28800}"
        }
        val c = client(http)
        val session = c.startDeviceFlow()!!
        val result = c.pollToken(session)
        assertTrue(result is GitHubDeviceFlowClient.PollResult.Success)
        val tokens = (result as GitHubDeviceFlowClient.PollResult.Success).tokens
        assertEquals("gho_token", tokens.accessToken)
        // GitHub 기기 흐름은 refresh token이 없다.
        assertNull(tokens.refreshToken)
        assertEquals(1000L + 28_800_000L, tokens.expiresAtEpochMs)
    }

    @Test
    fun pollTokenExpiredCodeIsFailure() = runTest {
        val http = FakeHttp { _, _ -> 200 to "{\"error\":\"expired_token\"}" }
        val session = client(http).startDeviceFlow()!!
        val result = client(http).pollToken(session)
        assertTrue(result is GitHubDeviceFlowClient.PollResult.Failed)
    }

    @Test
    fun pollTokenDeniedIsFailure() = runTest {
        val http = FakeHttp { _, _ -> 200 to "{\"error\":\"access_denied\"}" }
        val session = client(http).startDeviceFlow()!!
        val result = client(http).pollToken(session)
        assertTrue(result is GitHubDeviceFlowClient.PollResult.Failed)
    }

    @Test
    fun refreshIsNotSupported() = runTest {
        val http = FakeHttp { _, _ -> 200 to deviceCodeBody }
        val c = client(http)
        assertNull(c.refresh(GitHubDeviceFlowClientTestTokens))
        assertNotNull(c.startDeviceFlow())
    }

    private companion object {
        val GitHubDeviceFlowClientTestTokens =
            com.woojik.aircallai.auth.OAuthTokens("gho_token", null, null)
    }
}
