package com.woojik.aircallai.conversation

object VoiceResponseSanitizer {
    fun sanitize(text: String): String {
        return text
            .replace(Regex("https?://\\S+"), " ")
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "네 말씀해 주세요" }
    }
}
