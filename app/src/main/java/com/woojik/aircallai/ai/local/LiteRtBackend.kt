package com.woojik.aircallai.ai.local

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.core.logging.SecureLog
import java.io.File

/** The runtime applies the model's embedded chat template, preserving actual message roles. */
internal fun localConversationConfig(history: List<ChatMessage>): ConversationConfig {
    require(history.isNotEmpty() && history.last().role == ChatMessage.Role.USER)
    val instructions = listOf("You are AirCall AI, a helpful Korean voice assistant. Answer briefly.") +
        history.filter { it.role == ChatMessage.Role.SYSTEM }.map { it.content }
    return ConversationConfig(
        systemInstruction = Contents.of(instructions.joinToString("\n")),
        initialMessages = history.dropLast(1).mapNotNull {
            when (it.role) {
                ChatMessage.Role.USER -> Message.user(it.content)
                ChatMessage.Role.ASSISTANT -> Message.model(it.content)
                ChatMessage.Role.SYSTEM -> null
            }
        },
        // Short spoken replies should not include a thinking channel.
        extraContext = mapOf("enable_thinking" to false),
    )
}

/**
 * LiteRT-LM 추론 백엔드.
 *
 * GPU 가속: useGpu가 true면 GPU 백엔드로 초기화를 시도하고, 기기/드라이버가 GPU를
 * 지원하지 않아 초기화에 실패하면 자동으로 CPU로 폴백한다(사용자에게 별도 안내 불필요).
 */
internal class LiteRtBackend(
    file: File,
    cacheDir: File,
    useGpu: Boolean = false,
) : LocalInferenceBackend {

    private val engine = createEngine(file, cacheDir, useGpu)

    private fun engineConfig(file: File, cacheDir: File, backend: Backend): EngineConfig =
        EngineConfig(
            modelPath = file.absolutePath,
            backend = backend,
            maxNumTokens = 2048,
            cacheDir = cacheDir.absolutePath,
        )

    private fun createEngine(file: File, cacheDir: File, useGpu: Boolean): Engine {
        SecureLog.d(
            "LiteRtBackend",
            "engine init start fileBytes=" + file.length() + " useGpu=" + useGpu,
        )
        if (!useGpu) return try {
            Engine(engineConfig(file, cacheDir, Backend.CPU())).also {
                it.initialize()
                SecureLog.d("LiteRtBackend", "engine init success backend=CPU")
            }
        } catch (t: Throwable) {
            SecureLog.e(
                "LiteRtBackend",
                "engine init failed backend=CPU exception=" + t.javaClass.name +
                    " message=" + (t.message ?: "<none>"),
                t,
            )
            throw t
        }
        return try {
            Engine(engineConfig(file, cacheDir, Backend.GPU())).also {
                it.initialize()
                SecureLog.d("LiteRtBackend", "engine init success backend=GPU")
            }
        } catch (t: Throwable) {
            // GPU 미지원 기기/드라이버 오류: CPU로 자동 폴백한다.
            SecureLog.e(
                "LiteRtBackend",
                "GPU init failed; falling back to CPU exception=" + t.javaClass.name +
                    " message=" + (t.message ?: "<none>"),
                t,
            )
            try {
                Engine(engineConfig(file, cacheDir, Backend.CPU())).also {
                    it.initialize()
                    SecureLog.d("LiteRtBackend", "engine init success backend=CPU fallback=true")
                }
            } catch (cpuError: Throwable) {
                SecureLog.e(
                    "LiteRtBackend",
                    "CPU fallback init failed exception=" + cpuError.javaClass.name +
                        " message=" + (cpuError.message ?: "<none>"),
                    cpuError,
                )
                throw cpuError
            }
        }
    }

    override fun generate(history: List<ChatMessage>): String {
        SecureLog.d("LiteRtBackend", "createConversation start historyMessages=" + history.size)
        return try {
            engine.createConversation(localConversationConfig(history)).use { conversation ->
                SecureLog.d("LiteRtBackend", "createConversation success; sendMessage start")
                val response = conversation.sendMessage(history.last().content)
                val text = response.contents.contents
                    .filterIsInstance<Content.Text>().joinToString("") { it.text }
                SecureLog.d("LiteRtBackend", "sendMessage success responseChars=" + text.length)
                text
            }
        } catch (t: Throwable) {
            SecureLog.e(
                "LiteRtBackend",
                "generation failed exception=" + t.javaClass.name +
                    " message=" + (t.message ?: "<none>") +
                    " historyMessages=" + history.size,
                t,
            )
            throw t
        }
    }

    override fun close() = engine.close()
}
