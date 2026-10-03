package com.woojik.aircallai.ai.local

import com.woojik.aircallai.ai.provider.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Native engines must never generate, switch models, or close concurrently. */
interface LocalInferenceBackend : AutoCloseable {
    fun generate(history: List<ChatMessage>): String
}

class LocalInferenceRuntime(private val create: (File) -> LocalInferenceBackend) {
    private val mutex = Mutex()
    private var backend: LocalInferenceBackend? = null
    private var loadedFile: Triple<String, Long, Long>? = null

    suspend fun <T> withModel(file: File, action: (LocalInferenceBackend) -> T): T =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val identity = Triple(file.absolutePath, file.length(), file.lastModified())
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

    suspend fun unload(afterClose: () -> Unit = {}) = withContext(Dispatchers.IO) {
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
