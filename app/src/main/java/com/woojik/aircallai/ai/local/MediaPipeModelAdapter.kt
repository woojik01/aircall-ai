package com.woojik.aircallai.ai.local

import android.app.ActivityManager
import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.woojik.aircallai.ai.provider.AIProviderException
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.ai.provider.ProviderErrorKind
import com.woojik.aircallai.settings.SettingsRepository
import com.woojik.aircallai.core.logging.SecureLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * MediaPipe LLM Inference 어댑터 (로컬 백엔드 1단계).
 *
 * - LocalModelAdapter 인터페이스 뒤에 MediaPipe SDK를 격리한다 (PRD-04 구조 유지).
 * - 선택된 모델이 없거나 파일이 없으면 MODEL_NOT_INSTALLED로 동작 — 기존 Noop과 동일한 외부 동작.
 * - 추후 MLC(NPU) 어댑터를 추가할 때 이 구현을 교체/병행한다.
 */
class MediaPipeModelAdapter(
    private val context: Context,
    private val settings: SettingsRepository,
) : LocalModelAdapter {

    private var inference: LlmInference? = null
    private var loadedFileName: String? = null

    /** 사용자가 갤러리에서 선택한 모델 (미선택 시 null). */
    fun selectedModel(): LocalModelInfo? = LocalModelRegistry.byId(settings.localModelId())

    /** 모델 파일이 이미 다운로드되어 있는지 (갤러리 UI 표시용). */
    fun isModelDownloaded(model: LocalModelInfo): Boolean =
        File(modelsDir(), model.fileName).exists()

    override suspend fun isModelAvailable(): Boolean {
        val model = selectedModel() ?: return false
        return File(modelsDir(), model.fileName).exists()
    }

    override suspend fun isDeviceSupported(): Boolean {
        val model = selectedModel() ?: return false
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return true
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        // totalMem 기준 최소 RAM 확인 (부트 시점 총 메모리).
        return info.totalMem >= model.minRamMb * MEGA_BYTES
    }

    override suspend fun generate(history: List<ChatMessage>): String {
        val model = selectedModel()
            ?: throw AIProviderException(ProviderErrorKind.MODEL_NOT_INSTALLED)
        val path = File(modelsDir(), model.fileName)
        if (!path.exists()) {
            throw AIProviderException(ProviderErrorKind.MODEL_NOT_INSTALLED)
        }
        return withContext(Dispatchers.IO) {
            try {
                val engine = obtainInference(path.absolutePath)
                val prompt = buildLocalPrompt(history)
                engine.generate(prompt).ifEmpty {
                    throw AIProviderException(ProviderErrorKind.LOAD_FAILED)
                }
            } catch (e: AIProviderException) {
                throw e
            } catch (e: OutOfMemoryError) {
                throw AIProviderException(ProviderErrorKind.MEMORY)
            } catch (e: Exception) {
                SecureLog.d(TAG, "mediapipe generate failed: " + e.javaClass.simpleName)
                throw AIProviderException(ProviderErrorKind.LOAD_FAILED)
            }
        }
    }

    /** 모델 파일 경로가 바뀌면 엔진을 다시 로드한다. 로드는 무겁기 때문에 캐시한다. */
    private fun obtainInference(modelPath: String): LlmInference {
        val current = inference
        if (current != null && loadedFileName == modelPath) return current
        runCatching { current?.close() }
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(MAX_TOKENS)
            .build()
        val created = LlmInference.createFromOptions(context, options)
        inference = created
        loadedFileName = modelPath
        return created
    }

    private fun modelsDir(): File =
        File(context.filesDir, "models").apply { mkdirs() }

    fun shutdown() {
        runCatching { inference?.close() }
        inference = null
        loadedFileName = null
    }

    companion object {
        private const val TAG = "MediaPipeAdapter"
        private const val MEGA_BYTES = 1L * 1024 * 1024
        private const val MAX_TOKENS = 1024
    }
}
