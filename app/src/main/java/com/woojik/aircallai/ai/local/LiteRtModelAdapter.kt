package com.woojik.aircallai.ai.local

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.woojik.aircallai.ai.provider.AIProviderException
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.ai.provider.ProviderErrorKind
import com.woojik.aircallai.core.logging.SecureLog
import com.woojik.aircallai.core.storage.AppStorage
import com.woojik.aircallai.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import java.io.File

/** Gemma 4 native Android backend. STT/TTS remain separate Android services. */
class LiteRtModelAdapter(
    context: Context,
    private val settings: SettingsRepository,
) : LocalModelAdapter {
    private val context = context.applicationContext
    private val runtime = LocalInferenceRuntime { file ->
        localErrors(ProviderErrorKind.LOAD_FAILED) {
            LiteRtBackend(file, this.context.cacheDir, useGpu = settings.localUseGpu())
        }
    }

    fun selectedModel(): LocalModelInfo? = LocalModelRegistry.byId(settings.localModelId())

    override suspend fun isModelAvailable(): Boolean =
        selectedModel()?.let { isInstalledModel(modelFile(it), it) } ?: false

    override suspend fun isDeviceSupported(): Boolean =
        supports(selectedModel())

    private fun supports(model: LocalModelInfo?): Boolean {
        if (Build.SUPPORTED_ABIS.none { it == "arm64-v8a" || it == "x86_64" }) return false
        if (model == null) return true
        val manager = context.getSystemService(ActivityManager::class.java) ?: return false
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return info.totalMem >= model.minRamMb * 1024L * 1024L
    }

    /** Apply only after loading succeeds; failure preserves the previous selection. */
    suspend fun apply(model: LocalModelInfo) {
        requireAvailable(model)
        localErrors(ProviderErrorKind.LOAD_FAILED) {
            runtime.withModel(modelFile(model)) { settings.setLocalModelId(model.id) }
        }
    }

    suspend fun unapply() = localErrors(ProviderErrorKind.LOAD_FAILED) {
        runtime.unload { settings.setLocalModelId(null) }
    }

    override suspend fun generate(history: List<ChatMessage>): String {
        val model = selectedModel() ?: throw AIProviderException(ProviderErrorKind.MODEL_NOT_INSTALLED)
        requireAvailable(model)
        return localErrors(ProviderErrorKind.INFERENCE_FAILED) {
            runtime.withModel(modelFile(model)) { backend ->
                backend.generate(history).ifBlank {
                    throw AIProviderException(ProviderErrorKind.INFERENCE_FAILED)
                }
            }
        }
    }

    private fun requireAvailable(model: LocalModelInfo) {
        if (!isInstalledModel(modelFile(model), model)) {
            throw AIProviderException(ProviderErrorKind.MODEL_NOT_INSTALLED)
        }
        if (!supports(model)) throw AIProviderException(ProviderErrorKind.UNSUPPORTED_DEVICE)
    }

    private fun modelFile(model: LocalModelInfo) = File(AppStorage.modelsDir(context), model.fileName)
}

private inline fun <T> localErrors(kind: ProviderErrorKind, block: () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: AIProviderException) {
    throw e
} catch (e: OutOfMemoryError) {
    throw AIProviderException(ProviderErrorKind.MEMORY)
} catch (e: LinkageError) {
    throw AIProviderException(ProviderErrorKind.UNSUPPORTED_DEVICE)
} catch (e: Exception) {
    SecureLog.d("LiteRtAdapter", "local inference failed: " + e.javaClass.simpleName)
    throw AIProviderException(kind)
}
