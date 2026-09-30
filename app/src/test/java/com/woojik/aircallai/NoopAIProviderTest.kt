package com.woojik.aircallai

import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.ai.provider.NoopAIProvider
import com.woojik.aircallai.ai.provider.ProviderType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoopAIProviderTest {

    @Test
    fun `noop provider is always ready and echoes type`() = runTest {
        val local = NoopAIProvider(ProviderType.LOCAL)
        val cloud = NoopAIProvider(ProviderType.CLOUD)
        assertTrue(local.isReady())
        assertTrue(cloud.isReady())
        assertEquals(ProviderType.LOCAL, local.type)
        assertEquals(ProviderType.CLOUD, cloud.type)
    }

    @Test
    fun `respond returns assistant message`() = runTest {
        val response = NoopAIProvider().respond(listOf(ChatMessage(ChatMessage.Role.USER, "test")))
        assertEquals(ChatMessage.Role.ASSISTANT, response.message.role)
        assertTrue(response.message.content.contains("test"))
    }
}
