package com.woojik.aircallai

import com.woojik.aircallai.ai.cloud.CloudAIProvider
import com.woojik.aircallai.ai.cloud.CloudApiAdapter
import com.woojik.aircallai.ai.cloud.HttpCloudApiAdapter
import com.woojik.aircallai.ai.provider.AIProviderException
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.ai.provider.ProviderErrorKind
import com.woojik.aircallai.core.security.CryptoEngine
import com.woojik.aircallai.core.storage.FileCredentialManager
import com.woojik.aircallai.settings.InMemorySettingsStore
import com.woojik.aircallai.settings.SettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * PRD-04: Cloud Mode에서 앱이 사용자가 등록한 API를 직접 호출하려면
 * endpoint(baseUrl/model)가 일반 설정에서 공급되어야 한다.
 * - endpoint 설정 저장/조회 (PRD-02 일반 설정 분류)
 * - CloudAIProvider가 설정된 endpoint를 어댑터에 전달
 * - endpoint 미설정 시 API 오류로 구분 (Key 오류와 혼동 없음)
 */
class CloudEndpointSettingsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class NoopCrypto : CryptoEngine {
        override fun encrypt(plain: ByteArray): ByteArray = plain
        override fun decrypt(blob: ByteArray): ByteArray? = blob
    }

    private class CapturingAdapter : CloudApiAdapter {
        var apiKey: String? = null
        var baseUrl: String? = null
        var model: String? = null
        override suspend fun chat(apiKey: String, baseUrl: String, model: String, history: List<ChatMessage>): String {
            this.apiKey = apiKey
            this.baseUrl = baseUrl
            this.model = model
            return "ok"
        }
    }

    @Test
    fun cloudEndpointSettingsPersist() {
        val settings = SettingsRepository(InMemorySettingsStore())
        assertEquals("", settings.cloudBaseUrl())
        assertEquals("", settings.cloudModel())

        settings.setCloudBaseUrl("  https://api.example.com/v1/chat/completions  ")
        settings.setCloudModel("  gpt-4o-mini  ")
        assertEquals("https://api.example.com/v1/chat/completions", settings.cloudBaseUrl())
        assertEquals("gpt-4o-mini", settings.cloudModel())

        // 새 저장소(프로세스 재시작과 동일)에서도 유지된다
        val reloaded = SettingsRepository(InMemorySettingsStore().apply {
            putString("cloud_base_url", settings.cloudBaseUrl())
            putString("cloud_model", settings.cloudModel())
        })
        assertEquals("https://api.example.com/v1/chat/completions", reloaded.cloudBaseUrl())
        assertEquals("gpt-4o-mini", reloaded.cloudModel())
    }

    @Test
    fun cloudProviderPassesConfiguredEndpointToAdapter() = runTest {
        val settings = SettingsRepository(InMemorySettingsStore())
        settings.setCloudBaseUrl("https://api.example.com/v1/chat/completions")
        settings.setCloudModel("gpt-4o-mini")
        val cm = FileCredentialManager(tmp.newFolder(), NoopCrypto())
        cm.save(CloudAIProvider.KEY_SERVICE, "sk-good".toByteArray())
        val adapter = CapturingAdapter()
        val provider = CloudAIProvider(cm, adapter) {
            CloudAIProvider.Endpoint(settings.cloudBaseUrl(), settings.cloudModel())
        }

        provider.respond(listOf(ChatMessage(ChatMessage.Role.USER, "hi")))

        assertEquals("sk-good", adapter.apiKey)
        assertEquals("https://api.example.com/v1/chat/completions", adapter.baseUrl)
        assertEquals("gpt-4o-mini", adapter.model)
    }

    @Test
    fun unconfiguredEndpointSurfacesAsApiErrorNotAuthFailure() = runTest {
        val cm = FileCredentialManager(tmp.newFolder(), NoopCrypto())
        cm.save(CloudAIProvider.KEY_SERVICE, "bad-key".toByteArray())
        val settings = SettingsRepository(InMemorySettingsStore()) // endpoint 미설치
        val provider = CloudAIProvider(cm, HttpCloudApiAdapter()) {
            CloudAIProvider.Endpoint(settings.cloudBaseUrl(), settings.cloudModel())
        }
        try {
            provider.respond(listOf(ChatMessage(ChatMessage.Role.USER, "hi")))
            fail("expected API_ERROR for unconfigured endpoint")
        } catch (e: AIProviderException) {
            assertEquals(ProviderErrorKind.API_ERROR, e.kind)
            assertTrue(e.kind != ProviderErrorKind.AUTH_FAILED)
        }
    }
}
