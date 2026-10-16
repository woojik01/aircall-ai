package com.woojik.aircallai

import com.woojik.aircallai.ai.local.LocalInferenceBackend
import com.woojik.aircallai.ai.local.LocalInferenceRuntime
import com.woojik.aircallai.ai.provider.ChatMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineStart
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LocalInferenceRuntimeTest {
    @Test fun backgroundCleanupDoesNotLoadAnUnusedModel() = runBlocking {
        val runtime = LocalInferenceRuntime { error("Cleanup must not load an engine") }
        assertFalse(runtime.releaseCachedModel())
    }

    @Test fun backgroundCleanupReleasesCacheAndNextRequestReloadsOnSameWorker() = runBlocking {
        val engines = mutableListOf<Backend>()
        val threads = mutableSetOf<Long>()
        val runtime = LocalInferenceRuntime {
            threads.add(Thread.currentThread().id)
            Backend().also { engines.add(it) }
        }
        runtime.withModel(File("same")) { it.generate(emptyList()) }
        assertFalse(runtime.releaseCachedModel { false }) // A background call still owns the model.
        assertFalse(engines.single().closed)
        assertTrue(runtime.releaseCachedModel {
            threads.add(Thread.currentThread().id)
            true
        })
        assertTrue(engines.single().closed)
        assertFalse(runtime.releaseCachedModel())
        assertEquals("reply", runtime.withModel(File("same")) { it.generate(emptyList()) })
        assertEquals(2, engines.size)
        assertEquals(1, threads.size)
        runtime.unload()
    }

    @Test fun cleanupWaitsForInferenceAndRechecksCallOwnership() = runBlocking {
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val engine = Backend()
        val runtime = LocalInferenceRuntime { engine }
        val callStarted = AtomicBoolean(false)
        val reply = async {
            runtime.withModel(File("same")) {
                entered.countDown()
                check(finish.await(5, TimeUnit.SECONDS))
                assertFalse(engine.closed)
                it.generate(emptyList())
            }
        }
        try {
            kotlinx.coroutines.yield()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val cleanup = async(start = CoroutineStart.UNDISPATCHED) {
                runtime.releaseCachedModel { !callStarted.get() }
            }
            assertFalse(cleanup.isCompleted)
            callStarted.set(true)
            finish.countDown()
            assertEquals("reply", reply.await())
            assertFalse(cleanup.await())
            assertFalse(engine.closed)
            callStarted.set(false)
            assertTrue(runtime.releaseCachedModel { !callStarted.get() })
            assertTrue(engine.closed)
        } finally {
            finish.countDown()
            runtime.unload()
        }
    }

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
