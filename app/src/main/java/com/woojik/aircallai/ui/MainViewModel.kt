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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

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
    /** PRD-07: 현재 Provider가 통화를 시작할 수 있는지 (Cloud 키 등록 여부 / 로컬 모델 준비 여부). */
    private val isProviderReady: suspend () -> Boolean = { true },
) : AndroidViewModel(app) {

    val state: StateFlow<ConversationState> = repository.engine.state
    val transcript: StateFlow<List<ChatMessage>> = repository.engine.transcript
    val sessionStatus: StateFlow<SessionStatus> = repository.status
    val sessionMuted: StateFlow<Boolean> = repository.muted

    /** 기존 화면 재사용 (clearError 등). 실제 소유자는 SessionRepository다. */
    val engine: ConversationEngine get() = repository.engine

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** PRD-07: Provider 준비 여부 — 준비되지 않으면 통화 화면이 설정 필요 상태를 표시한다. */
    private val _providerReady = MutableStateFlow(true)
    val providerReady: StateFlow<Boolean> = _providerReady.asStateFlow()

    init {
        refreshProviderReadiness()
        scope.launch {
            state.collectLatest { error ->
                if (error is ConversationState.Error) {
                    delay(6_000)
                    if (state.value === error) engine.clearError()
                }
            }
        }
    }

    /** 설정에서 모드/API 키가 바뀌면 다시 계산한다. */
    fun refreshProviderReadiness() {
        scope.launch { _providerReady.value = isProviderReady() }
    }

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

    fun toggleMute() {
        val muted = !repository.muted.value
        repository.controller.setMuted(muted)
        if (muted) repository.stopSpeaking()
    }

    fun endSession() {
        repository.stopSpeaking()
        stopSession()
    }

    /** PRD-03 스펙 유지: TTS 재생 즉시 중지. */
    fun onStopSpeaking() = repository.stopSpeaking()

    private var textJob: Job? = null

    fun sendText(text: String) {
        if (text.isBlank()) return
        textJob = scope.launch {
            if (repository.engine.submitUserMessage(text)) repository.engine.markIdle()
        }
    }

    fun cancelText() { textJob?.cancel(); engine.clearError() }

    suspend fun prepareForRoomChange() {
        textJob?.cancelAndJoin()
        if (repository.controller.isRunning) {
            repository.stopSpeaking()
            repository.controller.end()
            stopSession()
        }
    }

    fun dispose() { scope.cancel() }

    override fun onCleared() {
        super.onCleared()
        scope.cancel()
    }
}

