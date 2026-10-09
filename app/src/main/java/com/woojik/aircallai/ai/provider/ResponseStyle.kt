package com.woojik.aircallai.ai.provider

/** One explicit mode travels through both providers and the tool bridge. */
object ResponseStyle {
    const val VOICE_MARKER = "AIRCALL_VOICE_MODE"
    const val TEXT_PROMPT = "You are AirCall AI, a helpful assistant. Reply in Korean unless asked otherwise. " +
        "For text chat, explain with enough detail to answer the request. Use Markdown lists, tables and fenced code when helpful."
    fun isVoice(history: List<ChatMessage>) = history.any {
        it.role == ChatMessage.Role.SYSTEM && it.content.contains(VOICE_MARKER)
    }
}
