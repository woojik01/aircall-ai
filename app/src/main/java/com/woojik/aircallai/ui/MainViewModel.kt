package com.woojik.aircallai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.woojik.aircallai.ai.provider.NoopAIProvider
import com.woojik.aircallai.audio.AndroidSpeechRecognizerEngine
import com.woojik.aircallai.audio.AndroidSpeechSynthesizerEngine
import com.woojik.aircallai.conversation.ConversationEngine
import com.woojik.aircallai.conversation.VoiceSession
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel이 ConversationEngine/VoiceSession을 소유해 화면 회전에도 대화 상태 유지 (PRD-01 req.6).
 */
class MainViewModel(
    app: Application,
    val engine: ConversationEngine,
    val voiceSession: VoiceSession,
    val isMicPermissionGranted: () -> Boolean,
    private val requestMicPermission: () -> Unit,
) : AndroidViewModel(app) {

    val state: StateFlow<com.woojik.aircallai.conversation.ConversationState> = engine.state
    val transcript: StateFlow<List<com.woojik.aircallai.ai.provider.ChatMessage>> = engine.transcript

    fun onMicTap() {
        if (!isMicPermissionGranted()) {
            requestMicPermission()
            return
        }
        viewModelScopeJob()
    }

    private fun viewModelScopeJob() {
        ioScope.launch { voiceSession.runOneTurn() }
    }

    fun onStopSpeaking() {
        voiceSession.stopSpeaking()
    }

    fun sendText(text: String) {
        if (text.isBlank()) return
        ioScope.launch {
            engine.submitUserMessage(text)
            engine.markIdle()
        }
    }

    override fun onCleared() {
        super.onCleared()
        ioScope.cancel()
        tts.shutdown()
    }

    private val ioScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob()
    )
    private val tts: AndroidSpeechSynthesizerEngine

    init {
        tts = AndroidSpeechSynthesizerEngine(app)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                throw IllegalStateException("use MainViewModel.factory(app, ...)")
            }
        }
    }
}
