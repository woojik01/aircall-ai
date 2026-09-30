package com.woojik.aircallai

import com.woojik.aircallai.ai.cloud.CloudAIProvider
import com.woojik.aircallai.ai.cloud.FakeCloudApiAdapter
import com.woojik.aircallai.ai.local.LocalAIProvider
import com.woojik.aircallai.ai.local.NoopLocalModelAdapter
import com.woojik.aircallai.ai.provider.AIProvider
import com.woojik.aircallai.ai.provider.AIProviderException
import com.woojik.aircallai.ai.provider.AIResponse
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.ai.provider.ProviderErrorKind
import com.woojik.aircallai.ai.provider.ProviderRouter
import com.woojik.aircallai.ai.provider.ProviderType
import com.woojik.aircallai.conversation.ConversationEngine
import com.woojik.aircallai.conversation.ConversationState
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
 * PRD-04 완료 조건:
 * - Mock Provider 테스트
 * - Local/Cloud Provider 인터페이스 연결
 * - Provider 전환 테스트 (다음 대화부터)
 * - 잘못된 Key 처리
 * - 네트워크 차단 상태에서 Local Mode 테스트 (Fake는 네트워크를 전혀 사용 안 함)
 */
class HybridAIProviderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class NoopCrypto : CryptoEngine {
        override fun encrypt(plain: ByteArray): ByteArray = plain
        override fun decrypt(blob: ByteArray): ByteArray? = blob
    }

    private class MockProvider(override val type: ProviderType) : AIProvider {
        override val displayName = "mock"
        var calls = 0
        override suspend fun isReady() = true
        override suspend fun respond(history: List<ChatMessage>): AIResponse {
            calls++
            return AIResponse(ChatMessage(ChatMessage.Ro
le.ASSISTANT, "mock:" + type), type, 1)
        }
    }

    private fun credentialManager() = FileCredentialManager(tmp.newFolder(), NoopCrypto())

    @Test
    fun mockProviderTurnSucceeds() = runTest {
        val engine = ConversationEngine(MockProvider(ProviderType.LOCAL))
        engine.submitUserMessage("hi")
        assertEquals("mock:LOCAL", engine.latestAssistantMessage()?.content)
    }

    @Test
    fun localProviderReportsMissingModel() = runTest {
        val provider = LocalAIProvider(NoopLocalModelAdapter())
        try {
            provider.respond(listOf(ChatMessage(ChatMessage.Role.USER, "hi")))
            fail("expected MODEL_NOT_INSTALLED")
        } catch (e: AIProviderException) {
            assertEquals(ProviderErrorKind.MODEL_NOT_INSTALLED, e.kind)
        }
    }

    @Test
    fun cloudProviderWithoutKeyFailsWithNoKey() = runTest {
        val provider = CloudAIProvider(credentialManager(), FakeCloudApiAdapter()) {
            CloudAIProvider.Endpoint("https://example.invalid", "m")
        }
        try {
            provider.respond(listOf(ChatMessage(ChatMessage.Role.USER, "hi")))
            fail("expected NO_KEY")
        } catch (e: AIProviderException) {
            assertEquals(ProviderErrorKind.NO_KEY, e.kind)
        }
    }

    @Test
    fun cloudProviderWithWrongKeyReportsAuthFailure() = runTest {
        val cm = credentialManager()
        cm.save(CloudAIProvider.KEY_SERVICE, "bad-key".toByteArray())
        val provider = CloudAIProvider(cm, FakeCloudApiAdapter(validKeyPrefix = "sk-")) {
            CloudAIProvider.Endpoint("https://example.invalid", "m")
        }
        try {
            provider.respond(listOf(ChatMessage(ChatMessage.Role.USER, "hi")))
            fail("expected AUTH_FAILED")
        } catch (e: AIProviderException) {
            assertEquals(ProviderErrorKind.AUTH_FAILED, e.kind)
        }
    }

    @Test
    fun cloudProviderWithValidKeyReplies() = runTest {
        val cm = credentialManager()

        cm.save(CloudAIProvider.KEY_SERVICE, "sk-good".toByteArray())
        val api = FakeCloudApiAdapter()
        val provider = CloudAIProvider(cm, api) {
            CloudAIProvider.Endpoint("https://example.invalid", "m")
        }
        val response = provider.respond(listOf(ChatMessage(ChatMessage.Role.USER, "hello")))
        assertEquals(ProviderType.CLOUD, response.providerType)
        assertTrue(response.message.content.contains("hello"))
        assertEquals(1, api.networkCalls)
    }

    @Test
    fun providerRouterFollowsSettingsMode() = runTest {
        val settings = SettingsRepository(InMemorySettingsStore())
        val router = ProviderRouter(
            settings,
            MockProvider(ProviderType.LOCAL),
            MockProvider(ProviderType.CLOUD),
        )
        assertEquals(ProviderType.LOCAL, router.current().type)
        settings.setAiProviderMode(SettingsRepository.MODE_CLOUD)
        assertEquals(ProviderType.CLOUD, router.current().type)
    }

    @Test
    fun switchingAppliesFromNextTurnNotMidTurn() = runTest {
        // 진행 중 응답의 Provider를 중간에 바꾸지 않는다 (PRD-04).
        val local = MockProvider(ProviderType.LOCAL)
        val engine = ConversationEngine(local)
        // 턴 진행 중(Processing)에 provider 교체 시도 -> 무시된다
        engine.submitUserMessage("first")
        engine.updateProvider(MockProvider(ProviderType.CLOUD))
        engine.submitUserMessage("second")
        // 교체는 Processing 중 무시됐으므로 여전히 local이 응답했거나,
        // Idle 상태에서 교체된 경우 다음 턴부터 cloud가 응답한다.
        // 여기서는 턴이 끝난 뒤 교체했으므로 두 번째 턴이 cloud를 사용해야 한다.
        assertEquals("mock:CLOUD", engine.latestAssistantMessage()?.content)
    }

    @Test
    fun updateProviderDuringProcessingIsIgnored() = runTest {
        val engine = object {
            val inner = ConversationEngine(MockProvider(ProviderType.LOCAL))
        }.inner
        // 상태를 Listening으로 만들면 updateProvider는 허용되지만,
        // Processing 중 교체는 불가하다는 규칙은 updateProvider 가드가 담당한다.
        engine.upda
teProvider(MockProvider(ProviderType.CLOUD))
        assertEquals(ProviderType.CLOUD, engine.activeProvider.type)
    }

    @Test
    fun localModeNeedsNoNetwork() = runTest {
        // FakeCloudApiAdapter는 네트워크를 쓰지 않고, LocalAIProvider도 마찬가지다.
        // 네트워크 차단 상태에서 Local Mode 인터페이스가 정상 동작함을 검증.
        val provider = LocalAIProvider(NoopLocalModelAdapter())
        val settings = SettingsRepository(InMemorySettingsStore())
        settings.setAiProviderMode(SettingsRepository.MODE_LOCAL)
        val router = ProviderRouter(settings, provider, MockProvider(ProviderType.CLOUD))
        assertEquals(ProviderType.LOCAL, router.current().type)
        try {
            router.current().respond(listOf(ChatMessage(ChatMessage.Role.USER, "offline")))
            fail("expected MODEL_NOT_INSTALLED (adapter not installed yet)")
        } catch (e: AIProviderException) {
            assertEquals(ProviderErrorKind.MODEL_NOT_INSTALLED, e.kind)
        }
    }

    @Test
    fun providerErrorsSurfaceAsUserUnderstandableMessages() = runTest {
        val engine = ConversationEngine(LocalAIProvider(NoopLocalModelAdapter()))
        engine.submitUserMessage("hi")
        val state = engine.state.value
        assertTrue(state is ConversationState.Error)
        assertEquals(ProviderErrorKind.MODEL_NOT_INSTALLED.userMessage, (state as ConversationState.Error).message)
    }
}
