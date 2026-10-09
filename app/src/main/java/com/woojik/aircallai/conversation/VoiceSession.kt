package com.woojik.aircallai.conversation

import com.woojik.aircallai.audio.AudioError
import com.woojik.aircallai.audio.SpeechRecognizerInterface
import com.woojik.aircallai.audio.SpeechSynthesizer
import com.woojik.aircallai.core.logging.SecureLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel

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

    private val turnMutex = Mutex()
    private val _metrics = MutableStateFlow<List<TurnMetrics>>(emptyList())
    val metrics: StateFlow<List<TurnMetrics>> = _metrics.asStateFlow()
    @Volatile private var speechStopped = false

    suspend fun runOneTurn() {
        if (!turnMutex.tryLock()) return
        try {
            runTurn()
        } catch (e: CancellationException) {
            engine.stopListening()
            engine.markIdle()
            throw e
        } finally {
            turnMutex.unlock()
        }
    }

    private suspend fun runTurn() {
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
        speechStopped = false
        var ttsStart = 0L
        var aiResponseEnd = 0L
        var audioFailure: AudioError? = null
        val succeeded = coroutineScope {
            val sentences = Channel<String>(Channel.UNLIMITED)
            val buffer = SpokenSentenceBuffer()
            val speaker = launch {
                try {
                    for (sentence in sentences) if (!speechStopped) {
                        if (ttsStart == 0L) ttsStart = System.currentTimeMillis()
                        synthesizer.speak(sentence)
                    }
                } catch (e: AudioError) { audioFailure = e; speechStopped = true }
            }
            val success = try {
                engine.submitUserMessage(text, voiceMode = true) { partial ->
                    buffer.update(partial).forEach { sentences.trySend(it) }
                }.also {
                    aiResponseEnd = System.currentTimeMillis()
                    if (it) buffer.finish()?.let { sentence -> sentences.trySend(sentence) }
                }
            } finally { sentences.close() }
            speaker.join()
            success
        }
        if (!succeeded) return
        audioFailure?.let { engine.reportAudioError(it) }
        val ttsEnd = System.currentTimeMillis()
        engine.markIdle()

        _metrics.value = (_metrics.value + TurnMetrics(
            recognitionEnd,
            aiResponseEnd,
            if (ttsStart == 0L) aiResponseEnd else ttsStart,
            ttsEnd,
        )).takeLast(100)
        SecureLog.d(TAG, "turn complete, aiMs=" + (aiResponseEnd - recognitionEnd) + ", ttsMs=" + (ttsEnd - ttsStart))
    }

    fun stopSpeaking() {
        speechStopped = true
        synthesizer.stop()
        engine.markIdle()
    }

    companion object {
        private const val TAG = "VoiceSession"
    }
}

/** Cumulative streaming updates produce each complete sentence exactly once. */
internal class SpokenSentenceBuffer {
    private var text = ""
    private var emitted = 0
    fun update(next: String): List<String> {
        if (!next.startsWith(text)) { text = next; emitted = next.length; return emptyList() }
        text = next
        val result = mutableListOf<String>()
        var index = emitted
        while (index < text.length) {
            if (text[index] in ".!?。\n" && !(text[index] == '.' &&
                    text.getOrNull(index - 1)?.isDigit() == true && text.getOrNull(index + 1)?.isDigit() == true)) {
                text.substring(emitted, index + 1).trim().takeIf { it.isNotEmpty() }?.let { result += it }
                emitted = index + 1
            }
            index++
        }
        return result
    }
    fun finish(): String? = text.substring(emitted).trim().takeIf { it.isNotEmpty() }.also { emitted = text.length }
}
