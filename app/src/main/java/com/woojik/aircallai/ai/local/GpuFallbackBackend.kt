package com.woojik.aircallai.ai.local

import com.woojik.aircallai.ai.provider.ChatMessage
import kotlinx.coroutines.CancellationException

/** Retry once on CPU after GPU initialization OR inference failure, keeping CPU thereafter. */
internal class GpuFallbackBackend(
    useGpu: Boolean,
    private val create: (Boolean) -> LocalInferenceBackend,
    private val onBackend: (String) -> Unit = {},
) : LocalInferenceBackend {
    private var gpu = useGpu
    private var backend: LocalInferenceBackend = try {
        create(gpu)
    } catch (failure: Exception) {
        if (!gpu || failure is CancellationException) throw failure
        gpu = false
        create(false)
    }

    init { onBackend(if (gpu) "GPU" else "CPU") }

    override fun generate(history: List<ChatMessage>): String = run { it.generate(history) }
    override fun validate() = run { it.validate() }

    private fun <T> run(action: (LocalInferenceBackend) -> T): T = try {
        action(backend)
    } catch (failure: Exception) {
        if (!gpu || failure is CancellationException) throw failure
        // Free GPU memory before allocating a CPU engine. Do not hide memory/linkage errors.
        backend.close()
        gpu = false
        backend = create(false)
        onBackend("CPU (GPU 오류 후 전환)")
        action(backend)
    }

    override fun close() = backend.close()
}
