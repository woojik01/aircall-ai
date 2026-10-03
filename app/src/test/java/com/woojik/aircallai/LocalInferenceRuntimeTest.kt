package com.woojik.aircallai

import com.woojik.aircallai.ai.local.LocalInferenceBackend
import com.woojik.aircallai.ai.local.LocalInferenceRuntime
import com.woojik.aircallai.ai.provider.ChatMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class LocalInferenceRuntimeTest {
    @Test fun gpuSettingChangeReloadsOnSameWorker() = runBlocking {
        val threads = mutableSetOf<Long>()
        var created = 0
        val runtime = LocalInferenceRuntime {
            threads.add(Thread.currentThread().id)
            created++
            Backend()
        }
        runtime.withModel(File("same"), "gpu") { threads.add(Thread.currentThread().id) }
        runtime.withModel(File("same"), "cpu") { threads.add(Thread.currentThread().id) }
        runtime.unload { threads.add(Thread.currentThread().id) }
        assertEquals(2, created)
        assertEquals(1, threads.size)
    }

    @Test fun failedInferenceRecreatesEngineOnNextTurn() = runBlocking {
        val engines = mutableListOf<Backend>()
        val runtime = LocalInferenceRuntime { Backend().also { engines.add(it) } }
        try {
            runtime.withModel(File("same")) { error("native inference failed") }
            fail("Expected failure")
        } catch (_: IllegalStateException) { }
        assertTrue(engines.single().closed)
        assertEquals("reply", runtime.withModel(File("same")) { it.generate(emptyList()) })
        assertEquals(2, engines.size)
        runtime.unload()
    }

    private class Backend : LocalInferenceBackend {
        var closed = false
        override fun generate(history: List<ChatMessage>): String {
            check(!closed)
            return "reply"
        }
        override fun close() { closed = true }
    }

    @Test fun failedSwitchNeverReusesClosedEngine() = runBlocking {
        val engines = mutableListOf<Backend>()
        val runtime = LocalInferenceRuntime {
            if (it.name == "bad") error("load failed")
            Backend().also { engine -> engines.add(engine) }
        }
        runtime.withModel(File("good")) { it.generate(emptyList()) }
        try {
            runtime.withModel(File("bad")) { fail("must not reach inference") }
            fail("must fail loading")
        } catch (_: IllegalStateException) { }
        assertTrue(engines.first().closed)
        assertEquals("reply", runtime.withModel(File("good")) { it.generate(emptyList()) })
        assertEquals(2, engines.size)
        runtime.unload()
        assertTrue(engines.last().closed)
    }

    @Test fun concurrentChatCallAndUnloadAreSerialized() = runBlocking {
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        val creations = AtomicInteger()
        val runtime = LocalInferenceRuntime { creations.incrementAndGet(); Backend() }
        (1..20).map {
            async {
                runtime.withModel(File("same")) { backend ->
                    val count = active.incrementAndGet()
                    maxActive.updateAndGet { old -> maxOf(old, count) }
                    try {
                        Thread.sleep(5)
                        backend.generate(emptyList())
                    } finally { active.decrementAndGet() }
                }
            }
        }.awaitAll()
        assertEquals(1, maxActive.get())
        assertEquals(1, creations.get())
        runtime.unload { assertEquals(0, active.get()) }
    }
}
