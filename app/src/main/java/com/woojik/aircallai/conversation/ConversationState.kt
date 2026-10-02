package com.woojik.aircallai.conversation

import com.woojik.aircallai.ai.provider.ChatMessage

/**
 * Conversation state lives outside any Activity/Composable so it survives
 * configuration changes. Process death clears this in-memory state.
 */
sealed interface ConversationState {
    data object Idle : ConversationState
    data object Listening : ConversationState
    data class Processing(val pendingUserMessage: ChatMessage?) : ConversationState
    data class Speaking(val assistantMessage: ChatMessage) : ConversationState
    data class Error(val kind: ErrorKind, val message: String) : ConversationState

    enum class ErrorKind { NETWORK, SPEECH_RECOGNITION, AI_PROVIDER, PERMISSION, UNKNOWN }
}
