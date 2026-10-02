package com.woojik.aircallai.conversation

import com.woojik.aircallai.audio.AudioError
import com.woojik.aircallai.audio.SpeechRecognizerInterface
import com.woojik.aircallai.audio.SpeechSynthesizer
import com.woojik.aircallai.core.logging.SecureLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VoiceSession(
    private val recognizer: SpeechRecognizerInterface,
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
        if (text.isNullOrBlank()) return

        val recognitionEnd = System.currentTimeMillis()
        engine.submitUserMessage(text, voiceMode = true)
        val response = (engine.state.value as? ConversationState.Speaking)?.assistantMessage ?: return
        val aiResponseEnd = System.currentTimeMillis()

        val ttsStart = System.currentTimeMillis()
        engine.markSpeaking()
        try {
            synthesizer.speak(response.content)
        } catch (e: AudioError) {
            engine.reportAudioError(e)
        }
        val ttsEnd = System.currentTimeMillis()
        engine.markIdle()

        _metrics.value = _metrics.value + TurnMetrics(
            recognitionEnd,
            aiResponseEnd,
            ttsStart,
            ttsEnd,
        )
        SecureLog.d(TAG, "turn complete, aiMs=" + (aiResponseEnd - recognitionEnd) + ", ttsMs=" + (ttsEnd - ttsStart))
    }

    fun stopSpeaking() {
        synthesizer.stop()
        engine.markIdle()
    }

    companion object {
        private const val TAG = "VoiceSession"
    }
}
