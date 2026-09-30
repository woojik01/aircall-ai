package com.woojik.aircallai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.conversation.ConversationEngine
import com.woojik.aircallai.conversation.ConversationState
import com.woojik.aircallai.session.SessionRepository
import com.woojik.aircallai.session.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * PRD-05: ViewModel은 세션 상태를 SessionRepository(상태 통로)를 통해서만 본다.
 * Service 내부 로직에 직접 의존하지 않는다.
 * 대화 상태는 Service가 소유하므로 화면 회전/다른 앱 전환에도 유지된다.
 */
class MainViewModel(
    app: Application,
    private val repository: SessionRepository,
    private val isMicPermissionGranted: () -> Boolean,
    private val requestMicPermission: () -> Unit,
    private val startSession: () -> Unit,
    private val stopSession: () -> Unit,
) : AndroidViewModel(app) {

    val state: StateFlow<ConversationState> = repository.engine.state
    val transcript: StateFlow<List<ChatMessage>> = repository.engine.transcript
    val sessionStatus: StateFlow<SessionStatus> = repository.status
    val sessionMuted: StateFlow<Boolean> = repository.muted

    /** 기존 화면 재사용 (clearError 등). 실제 소유자는 SessionRepository다. */
    val engine: ConversationEngine get() = repository.engine

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** PRD-05: 음성 대화 시작 = Foreground Service 세션 시작. 권한은 최소 범위 요청. */
    fun onMicTap() {
        if (!isMicPermissionGranted()) {
            requestMicPermission()
            return
        }
        startSession()
    }

    fun pauseSession() = repository.controller.pause()

    fun resumeSession() = repository.controller.resume()

    fun toggleMute() = repository.controller.setMuted(!repository.muted.value)

    fun endSession() {
        repository.stopSpeaking()
        stopSession()
    }

    /** PRD-03 스펙 유지: TTS 재생 즉시 중지. */
    fun onStopSpeaking() = repository.stopSpeaking()

    fun sendText(text: String) {
        if (text.isBlank()) return
        scope.launch {
            repository.engine.submitUserMessage(text)
            repository.engine.markIdle()
        }
    }

    override fun onCleared() {
        super.onCleared()
        scope.cancel()
    }
}
