package com.woojik.aircallai.ai.local

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.woojik.aircallai.ai.provider.ChatMessage
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import java.io.File

/** The runtime applies the model's embedded chat template, preserving actual message roles. */
internal fun localConversationConfig(history: List<ChatMessage>): ConversationConfig {
    require(history.isNotEmpty() && history.last().role == ChatMessage.Role.USER)
    val voice = com.woojik.aircallai.ai.provider.ResponseStyle.isVoice(history)
    val instructions = listOf(if (voice) "You are AirCall AI, a helpful Korean voice assistant. Answer briefly."
        else com.woojik.aircallai.ai.provider.ResponseStyle.TEXT_PROMPT) +
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
        thinkingConfig = ThinkingConfig(enableThinking = false),
        samplerConfig = SamplerConfig(topK = 40, topP = 0.9, temperature = 0.7),
        maxOutputToken = if (voice) 256 else 1024,
    )
}

/** One native engine and one reusable text conversation; owned by the inference worker. */
internal class LiteRtBackend(
    file: File,
    cacheDir: File,
    useGpu: Boolean = false,
) : LocalInferenceBackend {
    private val engine = Engine(
        EngineConfig(
            modelPath = file.absolutePath,
            backend = if (useGpu) Backend.GPU() else Backend.CPU(
                threadCount = Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
            ),
            maxNumTokens = LocalModelRegistry.byFileName(file.name)?.contextTokens ?: 4096,
            cacheDir = File(cacheDir, "litert-0.17.1/" + if (useGpu) "gpu" else "cpu")
                .apply { mkdirs() }.absolutePath,
        ),
    ).also { it.initialize() }

    private val conversations = LocalConversationCache(
        maxCachedTokens = (LocalModelRegistry.byFileName(file.name)?.contextTokens ?: 4096) - 1024,
    ) { history ->
        val conversation = engine.createConversation(localConversationConfig(history))
        object : LocalConversationSession {
            override fun send(text: String): String =
                conversation.sendMessage(text, extraContext = mapOf("enable_thinking" to false))
                    .contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
            override fun tokenCount() = conversation.getTokenCount()
            override fun close() = conversation.close()
        }
    }

    override fun generate(history: List<ChatMessage>): String = conversations.generate(history)

    /** Model apply must test prefill and decode, not just successful engine allocation. */
    override fun validate() {
        try {
            conversations.generate(listOf(ChatMessage(ChatMessage.Role.USER, "안녕")))
        } finally {
            conversations.close()
        }
    }

    override fun close() {
        try { conversations.close() } finally { engine.close() }
    }
}
