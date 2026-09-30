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

sealed interface SessionStatus {
    data object Inactive : SessionStatus
    data object Running : SessionStatus
    data object Paused : SessionStatus
    data object Ended : SessionStatus
}

interface SessionAudioHooks {
    fun stopSpeaking()
}

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

class SessionController(private val scope: CoroutineScope) {

    private val _status = MutableStateFlow(SessionStatus.Inactive)
    val status: StateFlow<SessionStatus> = _status.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val paused = MutableStateFlow(false)
    private var job: Job? = null

    val isRunning: Boolean
        get() = _status.value == SessionStatus.Running || _status.value == SessionStatus.Paused

    fun start(loop: suspend () -> Unit) {
        if (job?.isActive == true) return
        paused.value = (false)
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
        if (_status.value == SessionStatus.Paused) paused.value = (false)
    }

    fun setMuted(muted: Boolean) {
        _muted.value = muted
    }

    fun end() {
        job?.cancel()
        job = null
        _status.value = SessionStatus.Ended
    }

    companion object {
        private const val PAUSE_POLL_MS = 200L
    }
}
