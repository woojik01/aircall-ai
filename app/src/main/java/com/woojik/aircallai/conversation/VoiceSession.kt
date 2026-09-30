package com.woojik.aircallai.conversation

import com.woojik.aircallai.audio.AudioError
import com.woojik.aircallai.audio.SpeechRecognizer
import com.woojik.aircallai.audio.SpeechSynthesizer
import com.woojik.aircallai.core.logging.SecureLog
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PRD-03 음성 대화 루프:
 * Microphone -> Audio Capture -> STT -> ConversationEngine -> AI -> TTS -> Speaker
 *
 * 각 단계 latency를 timestamp로 기록한다 (PRD-03 지연시간 측정).
 * 개인정보/음성 원문은 로그에 남기지 않는다.
 */
class VoiceSession(
    private val recognizer: SpeechRecognizer,
    private val synthesizer: SpeechSynthesizer,
    private val engine: ConversationEngine,
) {
    data class TurnMetrics(
        val recognitionEndMs: Long,
        val aiResponseEndMs: Long,
        val ttsStartMs: Long,
        val ttsEndMs: Long,
    )

    private val _metrics = MutableStateFlow<List<TurnMetrics>>(emptyList())
    val metrics: StateFlow<List<TurnMetrics>> = _metrics.asStateFlow()

    /** STT 1회 -> AI 응답 -> TTS 재생까지의 한 턴을 수행한다. */
    suspend fun runOneTurn() {
        engine.startListening()
        val text = try {
            recognizer.recognizeOnce()
        } catch (e: AudioError) {
            engine.reportAudioError(e)
            null
        } finally {
            engine.stopListening()
        }
        if (text == null) return

        val recognitionEnd = System.currentTimeMillis()
        engine.submitUserMessage(text)
        val response = engine.latestAssistantMessage() ?: return

        val ttsStart = System.currentTimeMillis()
        engine.markSpeaking()
        try {
            synthesizer.speak(response.content)
        } catch (e: AudioError) {
            engine.reportAudioError(e)
        }
        val ttsEnd = System.currentTimeMillis()
        engine.markIdle()
        _metrics.value = _metrics.value + TurnMetrics(recognitionEnd, recognitionEnd, ttsStart, ttsEnd)
        SecureLog.d(TAG, "turn complete, ttsMs=" + (ttsEnd - ttsStart))
    }

    /** PRD-03: TTS 재생을 즉시 중단한다 (재생 중 중지 가능). */
    fun stopSpeaking() {
        synthesizer.stop()
        engine.markIdle()
    }

    companion object {
        private const val TAG = "VoiceSession"
    }
}
