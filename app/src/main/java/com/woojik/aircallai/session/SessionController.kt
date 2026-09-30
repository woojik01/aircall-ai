package com.woojik.aircallai.session

import com.woojik.aircallai.audio.SpeechSynthesizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** PRD-05 세션 상태. UI와 알림이 동일한 상태를 바라본다. */
sealed interface SessionStatus {
    data object Inactive : SessionStatus
    data object Running : SessionStatus
    data object Paused : SessionStatus
    data object Ended : SessionStatus
}

/**
 * PRD-05: Service의 오디오 제어 통로. UI는 Service 내부 로직에 직접 의존하지 않고
 * Repository를 통해서만 TTS 즉시 중지 등을 요청한다.
 */
interface SessionAudioHooks {
    fun stopSpeaking()
}

/**
 * PRD-05 음소거: 알림/화면의 음소거 상태를 TTS에 반영한다.
 * 음소거 중에도 STT/대화 상태는 유지되고 재생만 건너뛴다.
 */
class MutedSynthesizer(
    private val delegate: SpeechSynthesizer,
    private val isMuted: () -> Boolean,
) : SpeechSynthesizer {
    override suspend fun speak(text: String) {
        if (isMuted()) return
        delegate.speak(text)
    }
    override fun stop() = delegate.stop()
}

/**
 * PRD-05 백그라운드 대화 세션의 순수 로직 (JVM 테스트 대상).
 * Foreground Service는 얇은 Android 셸이고, 상태 전이는 여기서 일어난다.
 * 책임: 음성 턴 루프 실행, 일시정지/재개, 음소거, 세션 종료(사용자/시스템).
 */
class SessionController(private val scope: CoroutineScope) {

    // 상태 흐름: 명시적 타입 선언으로 SessionStatus 슈퍼타입을 고정한다.
    private val _status: MutableStateFlow<SessionStatus> = MutableStateFlow(SessionStatus.Inactive)
    val status: StateFlow<SessionStatus> = _status.asStateFlow()

    private val _muted: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val paused: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private var job: Job? = null

    val isRunning: Boolean
        get() = _status.value == SessionStatus.Running || _status.value == SessionStatus.Paused

    /** 세션을 시작한다. 진행 중인 턴은 취소하지 않고 다음 턴부터 일시정지한다. */
    fun start(loop: suspend () -> Unit) {
        if (job?.isActive == true) return
        paused.value = false
        _status.value = SessionStatus.Running
        job = scope.launch {
            while (isActive) {
                while (isActive && paused.value) {
                    _status.value = SessionStatus.Paused
                    delay(PAUSE_POLL_MS)
                }
                if (!isActive) break
                _status.value = SessionStatus.Running
                loop()
            }
        }
    }

    fun pause() {
        if (_status.value == SessionStatus.Running) paused.value = true
    }

    fun resume() {
        if (_status.value == SessionStatus.Paused) paused.value = false
    }

    fun setMuted(muted: Boolean) {
        _muted.value = muted
    }

    /** PRD-05 세션 종료: 사용자(알림 종료)와 시스템(서비스 종료) 모두 여기를 거친다. */
    fun end() {
        job?.cancel()
        job = null
        _status.value = SessionStatus.Ended
    }

    companion object {
        private const val PAUSE_POLL_MS = 200L
    }
}
