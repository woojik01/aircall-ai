package com.woojik.aircallai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.woojik.aircallai.conversation.ConversationEngine
import com.woojik.aircallai.conversation.VoiceSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel이 ConversationEngine/VoiceSession을 소유해 화면 회전에도 대화 상태 유지 (PRD-01 req.6).
 * PRD-03: 음성 턴 실행과 TTS 중지를 UI에 제공한다.
 */
class MainViewModel(
    app: Application,
    val engine: ConversationEngine,
    val voiceSession: VoiceSession,
    private val isMicPermissionGranted: () -> Boolean,
    private val requestMicPermission: () -> Unit,
) : AndroidViewModel(app) {

    val state: StateFlow<com.woojik.aircallai.conversation.ConversationState> = engine.state
    val transcript: StateFlow<List<com.woojik.aircallai.ai.provider.ChatMessage>> = engine.transcript

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** PRD-03: 권한은 필요한 시점에 최소 범위로 요청. */
    fun onMicTap() {
        if (!isMicPermissionGranted()) {
            requestMicPermission()
            return
        }
        scope.launch { voiceSession.runOneTurn() }
    }

    /** PRD-03: TTS 재생 중 중지 가능. */
    fun onStopSpeaking() {
        voiceSession.stopSpeaking()
    }

    fun sendText(text: String) {
        if (text.isBlank()) return
        scope.launch {
            engine.submitUserMessage(text)
            engine.markIdle()
        }
    }

    override fun onCleared() {
        super.onCleared()
        scope.cancel()
    }
}
