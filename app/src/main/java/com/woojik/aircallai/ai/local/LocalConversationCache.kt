package com.woojik.aircallai.ai.local

import com.woojik.aircallai.ai.provider.ChatMessage

internal interface LocalConversationSession : AutoCloseable {
    fun send(text: String): String
    fun tokenCount(): Int
}

/** Reuse native KV state only when the caller's history exactly matches previous successful turns. */
internal class LocalConversationCache(private val maxCachedTokens: Int = 3000,
    private val create: (List<ChatMessage>) -> LocalConversationSession) : AutoCloseable {
    private var session: LocalConversationSession? = null
    private var completedHistory: List<ChatMessage>? = null

    fun generate(history: List<ChatMessage>): String {
        if (session == null || history.dropLast(1) != completedHistory || session!!.tokenCount() > maxCachedTokens) {
            close()
            session = create(history)
        }
        val text = session!!.send(history.last().content)
        check(text.isNotBlank()) { "LOCAL_EMPTY_RESPONSE" }
        completedHistory = history + ChatMessage(ChatMessage.Role.ASSISTANT, text)
        return text
    }

    override fun close() {
        val old = session
        session = null
        completedHistory = null
        old?.close()
    }
}
