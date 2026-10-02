package com.woojik.aircallai

import com.woojik.aircallai.auth.AccountConnectionRepository
import com.woojik.aircallai.auth.ConnectionStatus
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
 * PRD-09 Phase 1: 계정 연결 저장소 단위 테스트.
 * 상태 복원, 서비스 간 독립성, callback 검증, 사용자 친화적 문구를 검증한다.
 */
class AccountConnectionRepositoryTest {

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

    private class FakeProvider(override val provider: String) : OAuthProvider {
        override suspend fun startConnect(): OAuthCallbackResult.Rejected? = null
        override suspend fun handleCallback(provider: String, code: String, state: String?): OAuthTokens? = null
        override suspend fun refresh(tokens: OAuthTokens): OAuthTokens? = null
    }

    private fun repo() = AccountConnectionRepository(
        OAuthCredentialStore(InMemoryCredentials()),
        listOf(FakeProvider("github"), FakeProvider("gmail")),
    )

    @Test
    fun initialStatusIsNotConnected() = runTest {
        val repository = repo()
        repository.refresh(1_000)
        assertEquals(ConnectionStatus.NOT_CONNECTED, repository.connections.value["github"]!!.status)
        assertEquals(ConnectionStatus.NOT_CONNECTED, repository.connections.value["gmail"]!!.status)
    }

    @Test
    fun connectMarksConnectedWithDisplayName() = runTest {
        val repository = repo()
        repository.connect("gmail", OAuthTokens("acc", null, expires = 9_999_999_999_999), "user@example.com", 1_000)
        val account = repository.connections.value["gmail"]!!
        assertEquals(ConnectionStatus.CONNECTED, account.status)
        assertEquals("user@example.com", account.displayName)
    }

    @Test
    fun expiredWithoutRefreshBecomesReauthRequired() = runTest {
        val repository = repo()
        repository.connect("gmail", OAuthTokens("acc", null, expires = 1_000), null, 500)
        repository.refresh(2_000)
        assertEquals(ConnectionStatus.REAUTH_REQUIRED, repository.connections.value["gmail"]!!.status)
    }

    @Test
    fun expiredWithRefreshBecomesExpired() = runTest {
        val repository = repo()
        repository.connect("gmail", OAuthTokens("acc", "rt", expires = 1_000), null, 500)
        repository.refresh(2_000)
        assertEquals(ConnectionStatus.EXPIRED, repository.connections.value["gmail"]!!.status)
    }

    @Test
    fun restoreAfterRestart() = runTest {
        val repository = repo()
        repository.connect("github", OAuthTokens("acc", null, expires = 9_999_999_999_999), "octocat", 1_000)
        // 앱 재시작 상황: 상태는 저장된 자격증명에서 다시 복원된다.
        repository.mark("github", ConnectionStatus.CONNECTING)
        repository.refresh(1_100)
        val account = repository.connections.value["github"]!!
        assertEquals(ConnectionStatus.CONNECTED, account.status)
        assertEquals("octocat", account.displayName)
    }

    @Test
    fun disconnectIsIndependentPerService() = runTest {
        val repository = repo()
        repository.connect("github", OAuthTokens("gh", null, expires = 9_999_999_999_999), "octocat", 1_000)
        repository.connect("gmail", OAuthTokens("gm", null, expires = 9_999_999_999_999), "user@example.com", 1_000)
        repository.disconnect("github")
        assertEquals(ConnectionStatus.NOT_CONNECTED, repository.connections.value["github"]!!.status)
        assertEquals(ConnectionStatus.CONNECTED, repository.connections.value["gmail"]!!.status)
        assertNull(repository.connections.value["github"]!!.displayName)
        // GitHub 해제가 Gmail 자격증명에 영향을 주지 않는다.
        assertTrue(repository.hasValidCredential("gmail", nowEpochMs = 2_000))
        assertFalse(repository.hasValidCredential("github", nowEpochMs = 2_000))
    }

    @Test
    fun callbackValidation() = runTest {
        val repository = repo()
        // provider 불일치 거부
        assertTrue(
            repository.validateCallback("github", "gmail", "code", "state", "state") is OAuthCallbackResult.Rejected,
        )
        // code 누락 거부
        assertTrue(
            repository.validateCallback("github", "github", null, "state", "state") is OAuthCallbackResult.Rejected,
        )
        // state 불일치 거부
        assertTrue(
            repository.validateCallback("github", "github", "code", "other", "state") is OAuthCallbackResult.Rejected,
        )
        // 정상 callback 수락
        val result = repository.validateCallback("github", "github", "code", "state", "state")
        assertTrue(result is OAuthCallbackResult.Success)
        assertEquals("code", (result as OAuthCallbackResult.Success).code)
    }

    @Test
    fun statusMessagesDoNotLeakEnumNames() = runTest {
        val repository = repo()
        val notConnected = repository.statusMessage(repository.connections.value["github"]!!)
        assertFalse(notConnected.contains("NOT_CONNECTED"))
        assertTrue(notConnected.contains("github"))
    }
}
