package com.woojik.aircallai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.woojik.aircallai.ai.provider.NoopAIProvider
import com.woojik.aircallai.conversation.ConversationEngine
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel-owned engine so conversation state survives rotation (PRD-01 req. 6).
 */
class MainViewModel(
    val engine: ConversationEngine,
) : ViewModel() {
    val state: StateFlow<com.woojik.aircallai.conversation.ConversationState> = engine.state
    val transcript: StateFlow<List<com.woojik.aircallai.ai.provider.ChatMessage>> = engine.transcript

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MainViewModel(ConversationEngine(NoopAIProvider())) as T
        }
    }
}
