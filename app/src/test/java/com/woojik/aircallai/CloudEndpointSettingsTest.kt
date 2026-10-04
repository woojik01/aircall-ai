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
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * PRD-04/05 Cloud Mode endpoint tests.
 */
class CloudEndpointSettingsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class NoopCrypto : CryptoEngine {
        override fun encrypt(plain: ByteArray): ByteArray = plain
        override fun decrypt(blob: ByteArray): ByteArray? = blob
    }

    private class CapturingAdapter : CloudApiAdapter {
        var calls = 0
        var apiKey: String? = null
        var baseUrl: String? = null
        var model: String? = null
        override suspend fun chat(apiKey: String, baseUrl: String, model: String, history: List<ChatMessage>): String {
            calls++
            this.apiKey = apiKey
            this.baseUrl = baseUrl
            this.model = model
            return "ok"
        }
    }

    @Test
    fun freshSettingsUseGroqDefaults() {
        val settings = SettingsRepository(InMemorySettingsStore())

        assertEquals(SettingsRepository.DEFAULT_CLOUD_BASE_URL, settings.cloudBaseUrl())
        assertEquals(SettingsRepository.DEFAULT_CLOUD_MODEL, settings.cloudModel())
    }

    @Test
    fun cloudEndpointSettingsPersist() {
        val settings = SettingsRepository(InMemorySettingsStore())
        assertEquals(SettingsRepository.DEFAULT_CLOUD_BASE_URL, settings.cloudBaseUrl())
        assertEquals(SettingsRepository.DEFAULT_CLOUD_MODEL, settings.cloudModel())

        settings.setCloudBaseUrl("  https://api.example.com/v1/chat/completions  ")
        settings.setCloudModel("  gpt-4o-mini  ")
        assertEquals("https://api.example.com/v1/chat/completions", settings.cloudBaseUrl())
        assertEquals("gpt-4o-mini", settings.cloudModel())

        val reloaded = SettingsRepository(InMemorySettingsStore().apply {
            putString("cloud_base_url", settings.cloudBaseUrl())
            putString("cloud_model", settings.cloudModel())
        })
        assertEquals("https://api.example.com/v1/chat/completions", reloaded.cloudBaseUrl())
        assertEquals("gpt-4o-mini", reloaded.cloudModel())
    }

    @Test
    fun deprecatedLlamaModelIsMigrated() {
        val store = InMemorySettingsStore().apply {
            putString("cloud_model", "llama-3.3-70b-versatile")
        }
        val settings = SettingsRepository(store)

        assertEquals(SettingsRepository.DEFAULT_CLOUD_MODEL, settings.cloudModel())
        assertEquals(SettingsRepository.DEFAULT_CLOUD_MODEL, store.getString("cloud_model"))
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
    fun explicitlyEmptyEndpointStillSurfacesAsApiError() = runTest {
        val cm = FileCredentialManager(tmp.newFolder(), NoopCrypto())
        cm.save(CloudAIProvider.KEY_SERVICE, "bad-key".toByteArray())
        val provider = CloudAIProvider(cm, HttpCloudApiAdapter()) {
            CloudAIProvider.Endpoint("", SettingsRepository.DEFAULT_CLOUD_MODEL)
        }
        try {
            provider.respond(listOf(ChatMessage(ChatMessage.Role.USER, "hi")))
            fail("expected API_ERROR for empty endpoint")
        } catch (e: AIProviderException) {
            assertEquals(ProviderErrorKind.API_ERROR, e.kind)
        }
    }

    @Test
    fun networkRequestsRequireConsentForTheirActualDestination() = runTest {
        val settings = SettingsRepository(InMemorySettingsStore())
        val cm = FileCredentialManager(tmp.newFolder(), NoopCrypto())
        cm.save(CloudAIProvider.KEY_SERVICE, "test-key".toByteArray())
        val adapter = CapturingAdapter()
        val provider = CloudAIProvider(cm, adapter,
            endpointAllowed = { settings.hasCloudDisclosure(it.baseUrl) }) {
            CloudAIProvider.Endpoint(settings.cloudBaseUrl(), settings.cloudModel())
        }
        val history = listOf(ChatMessage(ChatMessage.Role.USER, "private message"))
        for (destination in listOf(settings.cloudBaseUrl(), "https://different-ai.example.net/v1")) {
            settings.setCloudBaseUrl(destination)
            val before = adapter.calls
            try {
                provider.respond(history)
                fail("must not transmit before consent")
            } catch (e: AIProviderException) {
                assertEquals(ProviderErrorKind.CONSENT_REQUIRED, e.kind)
            }
            assertEquals(before, adapter.calls)
            settings.acceptCloudDisclosure()
            provider.respond(history)
            assertEquals(before + 1, adapter.calls)
            assertEquals(destination, adapter.baseUrl)
        }
    }
}
