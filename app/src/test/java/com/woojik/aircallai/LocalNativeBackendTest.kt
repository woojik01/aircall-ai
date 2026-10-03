package com.woojik.aircallai

import com.woojik.aircallai.ai.local.*
import com.woojik.aircallai.ai.provider.ChatMessage
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class LocalNativeBackendTest {
    private val greeting = listOf(ChatMessage(ChatMessage.Role.USER, "안녕"))
    private class Fake(private val fail: Boolean = false) : LocalInferenceBackend {
        var closed = false
        override fun generate(history: List<ChatMessage>): String {
            check(!closed)
            if (fail) error("GPU decode failed")
            return "안녕하세요"
        }
        override fun validate() { generate(emptyList()) }
        override fun close() { closed = true }
    }

    @Test fun shortGreetingGpuDecodeFailureRetriesOnceOnCpu() {
        val devices = mutableListOf<Boolean>()
        val gpu = Fake(true)
        val backend = GpuFallbackBackend(true, { useGpu ->
            devices += useGpu
            if (useGpu) gpu else Fake().also { assertTrue(gpu.closed) }
        })
        assertEquals("안녕하세요", backend.generate(greeting))
        assertEquals("안녕하세요", backend.generate(greeting))
        assertEquals(listOf(true, false), devices)
        backend.close()
    }

    @Test fun gpuInitializationFailureFallsBackToCpu() {
        val backend = GpuFallbackBackend(true, { gpu -> if (gpu) error("init") else Fake() })
        assertEquals("안녕하세요", backend.generate(greeting))
        backend.close()
    }

    @Test fun cancellationNeverStartsCpuRetry() {
        val devices = mutableListOf<Boolean>()
        try {
            GpuFallbackBackend(true, { gpu -> devices += gpu; throw CancellationException() })
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertEquals(listOf(true), devices)
    }

    @Test fun conversationReusesKvCacheAndResetsOnHistoryChange() {
        var created = 0
        var closed = 0
        val cache = LocalConversationCache {
            created++
            object : LocalConversationSession {
                override fun send(text: String) = "답변"
                override fun tokenCount() = 100
                override fun close() { closed++ }
            }
        }
        cache.generate(greeting)
        val next = greeting + ChatMessage(ChatMessage.Role.ASSISTANT, "답변") +
            ChatMessage(ChatMessage.Role.USER, "다음")
        cache.generate(next)
        assertEquals(1, created)
        cache.generate(greeting)
        assertEquals(2, created)
        assertEquals(1, closed)
        cache.close()
    }

    @Test fun diagnosticsDoNotExposeNativePromptOrPath() {
        val code = localFailureCode(IllegalStateException("Failed to allocate tensors /private/user 안녕 key=secret"))
        assertEquals("LOCAL_TENSOR_ALLOCATION", code)
    }
}
