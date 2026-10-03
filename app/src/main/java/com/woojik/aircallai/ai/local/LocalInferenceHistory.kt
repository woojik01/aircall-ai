package com.woojik.aircallai.ai.local

import com.woojik.aircallai.ai.provider.AIProviderException
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.ai.provider.ProviderErrorKind

/** Conservative UTF-8 budget; leaves space for the template and reply in a 4096-token cache.
 * This is a byte guard, not an exact tokenizer count. Never truncate instructions or current input.
 */
internal fun localInferenceHistory(history: List<ChatMessage>): List<ChatMessage> {
    require(history.isNotEmpty() && history.last().role == ChatMessage.Role.USER)
    val system = history.filter { it.role == ChatMessage.Role.SYSTEM }
    val current = history.last()
    fun cost(message: ChatMessage) = message.content.toByteArray(Charsets.UTF_8).size.toLong() + 64
    var used = system.sumOf { cost(it) } + cost(current) + 256
    if (used > 2800) throw AIProviderException(ProviderErrorKind.INPUT_TOO_LONG)
    val previous = history.dropLast(1).filter { it.role != ChatMessage.Role.SYSTEM }
    var start = previous.size
    // Keep complete recent user/assistant pairs; failed turns must not produce consecutive users.
    while (start >= 2) {
        val user = previous[start - 2]
        val assistant = previous[start - 1]
        if (user.role != ChatMessage.Role.USER || assistant.role != ChatMessage.Role.ASSISTANT) break
        val pairCost = cost(user) + cost(assistant)
        if (used + pairCost > 2800) break
        used += pairCost
        start -= 2
    }
    return system + previous.subList(start, previous.size) + current
}
