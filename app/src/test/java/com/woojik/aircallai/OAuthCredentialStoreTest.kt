package com.woojik.aircallai

import com.woojik.aircallai.auth.OAuthCallbackResult
import com.woojik.aircallai.auth.OAuthCredentialStore
import com.woojik.aircallai.auth.OAuthProvider
import com.woojik.aircallai.auth.OAuthTokens
import com.woojik.aircallai.core.storage.CredentialManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD-09 Phase 1: OAuth 자격증명 저장 단위 테스트.
 * 토큰 암호화 저장은 CredentialManager 계층을 fake로 대체하고,
 * 직렬화 복원, 연결 해제 독립성, 만료/갱신 흐름, 토큰 미노출을 검증한다.
 */
class OAuthCredentialStoreTest {

    private class InMemoryCredentials : CredentialManager {
        val map = mutableMapOf<String, ByteArray>()
        override suspend fun save(service: String, credential: ByteArray) {
            map[service] = credential
        }
        override suspend fun load(service: String): ByteArray? = map[service]
        override suspend fun delete(service: String) {
            map.remove(service)
        }
        override suspend fun clearAll() {
            map.clear()
        }
    }

    private class FakeProvider(
        override val provider: String,
        private val refreshed: OAuthTokens? = null,
    ) : OAuthProvider {
        var refreshCalled = false
        override suspend fun startConnect(): OAuthCallbackResult.Rejected? = null
        override suspend fun handleCallback(provider: String, code: String, state: String?): OAuthTokens? = null
        override suspend fun refresh(tokens: OAuthTokens): OAuthTokens? {
            refreshCalled = true
            return refreshed
        }
    }

    private fun tokens(access: String = "acc", refresh: String? = "ref", expires: Long? = null) =
        OAuthTokens(access, refresh, expires)

    @Test
    fun saveAndLoadRoundTrips() = runTest {
        val credentials = InMemoryCredentials()
        val store = OAuthCredentialStore(credentials)
        store.save("gmail", tokens(access = "ya29.a0", refresh = "1//rt"), "user@example.com")
        val loaded = store.load("gmail")!!
        assertEquals("ya29.a0", loaded.accessToken)
        assertEquals("1//rt", loaded.refreshToken)
        assertNull(loaded.expiresAtEpochMs)
        assertEquals("user@example.com", store.loadDisplayName("gmail"))
    }

    @Test
    fun storedBytesDoNotContainPlainToken() = runTest {
        val credentials = InMemoryCredentials()
        val store = OAuthCredentialStore(credentials)
        store.save("gmail", tokens(access = "secret-access-token"), null)
        val raw = credentials.map[OAuthCredentialStore.serviceKey("gmail")]!!
        // 평문 토큰이 저장 바이트에 그대로 남지 않는다(Base64 인코딩됨).
        assertFalse(String(raw, Charsets.UTF_8).contains("secret-access-token"))
    }

    @Test
    fun disconnectRemovesOnlyTargetProvider() = runTest {
        val credentials = InMemoryCredentials()
        val store = OAuthCredentialStore(credentials)
        store.save("github", tokens(), "octocat")
        store.save("gmail", tokens(), "user@example.com")
        store.disconnect("github")
        assertNull(store.load("github"))
        assertNull(store.loadDisplayName("github"))
        // 다른 서비스 자격증명은 유지된다.
        assertEquals("user@example.com", store.loadDisplayName("gmail"))
    }

    @Test
    fun validAccessTokenReturnsUnexpiredTokenWithoutRefresh() = runTest {
        val store = OAuthCredentialStore(InMemoryCredentials())
        store.save("gmail", tokens(access = "fresh", refresh = "ref", expires = 9_999_999_999_999), null)
        val provider = FakeProvider("gmail")
        assertEquals("fresh", store.validAccessToken("gmail", provider, now = 1_000))
        assertFalse(provider.refreshCalled)
    }

    @Test
    fun expiredTokenIsRefreshedAndPersisted() = runTest {
        val store = OAuthCredentialStore(InMemoryCredentials())
        store.save("gmail", tokens(access = "old", refresh = "ref", expires = 1_000), "user@example.com")
        val provider = FakeProvider("gmail", refreshed = tokens(access = "new", refresh = "ref2", expires = 5_000))
        assertEquals("new", store.validAccessToken("gmail", provider, now = 2_000))
        assertTrue(provider.refreshCalled)
        // 갱신된 토큰이 저장소에 반영되고 계정 표시명은 유지된다.
        assertEquals("new", store.load("gmail")!!.accessToken)
        assertEquals("user@example.com", store.loadDisplayName("gmail"))
    }

    @Test
    fun expiredWithoutRefreshTokenReturnsNull() = runTest {
        val store = OAuthCredentialStore(InMemoryCredentials())
        store.save("gmail", tokens(access = "old", refresh = null, expires = 1_000), null)
        val provider = FakeProvider("gmail")
        assertNull(store.validAccessToken("gmail", provider, now = 2_000))
    }

    @Test
    fun refreshFailureReturnsNull() = runTest {
        val store = OAuthCredentialStore(InMemoryCredentials())
        store.save("gmail", tokens(access = "old", refresh = "ref", expires = 1_000), null)
        val provider = FakeProvider("gmail", refreshed = null)
        assertNull(store.validAccessToken("gmail", provider, now = 2_000))
    }

    @Test
    fun missingCredentialReturnsNull() = runTest {
        val store = OAuthCredentialStore(InMemoryCredentials())
        assertNull(store.validAccessToken("github", FakeProvider("github"), now = 1_000))
    }
}
