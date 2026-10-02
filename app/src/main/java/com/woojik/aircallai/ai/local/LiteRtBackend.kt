package com.woojik.aircallai.ai.local

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.woojik.aircallai.ai.provider.ChatMessage
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

internal class LiteRtBackend(file: File, cacheDir: File) : LocalInferenceBackend {
    private val engine = Engine(
        EngineConfig(
            modelPath = file.absolutePath,
            backend = Backend.CPU(),
            maxNumTokens = 2048,
            cacheDir = cacheDir.absolutePath,
        ),
    ).also { it.initialize() }

    override fun generate(history: List<ChatMessage>): String =
        engine.createConversation(localConversationConfig(history)).use { conversation ->
            conversation.sendMessage(history.last().content).contents.contents
                .filterIsInstance<Content.Text>().joinToString("") { it.text }
        }

    override fun close() = engine.close()
}
