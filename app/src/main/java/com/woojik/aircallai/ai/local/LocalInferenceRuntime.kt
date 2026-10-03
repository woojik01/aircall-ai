package com.woojik.aircallai.ai.local

import com.woojik.aircallai.ai.provider.ChatMessage
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/** Native engines must never generate, switch models, or close concurrently. */
interface LocalInferenceBackend : AutoCloseable {
    fun generate(history: List<ChatMessage>): String
    fun validate() {}
}

class LocalInferenceRuntime(private val create: (File) -> LocalInferenceBackend) {
    // Keep initialization, inference and cleanup on one worker for native GPU thread affinity.
    private val dispatcher = Executors.newSingleThreadExecutor { task ->
        Thread(task, "AirCall-local-inference").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val mutex = Mutex()
    private var backend: LocalInferenceBackend? = null
    private var loadedFile: List<Any>? = null

    suspend fun <T> withModel(file: File, configurationKey: String = "", action: (LocalInferenceBackend) -> T): T =
        withContext(dispatcher) {
            mutex.withLock {
                val identity = listOf(file.absolutePath, file.length(), file.lastModified(), configurationKey)
                if (backend == null || loadedFile != identity) {
                    closeCurrent()
                    // Publish only a successfully initialized engine. A failed switch leaves no cache.
                    backend = create(file)
                    loadedFile = identity
                }
                try {
                    action(checkNotNull(backend))
                } catch (failure: Throwable) {
                    // A failed native conversation may leave the engine unusable.
                    // Preserve the original failure even when cleanup also fails.
                    try { closeCurrent() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
                    throw failure
                }
            }
        }

    suspend fun unload(afterClose: () -> Unit = {}) = withContext(dispatcher) {
        mutex.withLock {
            closeCurrent()
            afterClose()
        }
    }

    private fun closeCurrent() {
        val previous = backend
        backend = null
        loadedFile = null
        previous?.close()
    }
}
