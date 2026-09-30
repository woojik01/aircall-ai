package com.woojik.aircallai.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.woojik.aircallai.core.logging.SecureLog
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Android TextToSpeech 기반 TTS. stop() 시 즉시 재생이 중단된다 (PRD-03: 재생 중 중지 가능).
 */
class AndroidSpeechSynthesizerEngine(
    context: Context,
) : SpeechSynthesizer {

    private val ready = AtomicBoolean(false)
    private var engine: TextToSpeech? = null

    init {
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.getDefault()
                ready.set(true)
            }
        }
    }

    override suspend fun speak(text: String) {
        val tts = engine
        if (tts == null || !ready.get()) {
            throw AudioError.Unknown(IllegalStateException("TTS not ready"))
        }
        suspendCancellableCoroutine { cont ->
            val id = "aircall-" + System.nanoTime()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id) cont.resumeWith(Unit)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == id) cont.resumeWithException(AudioError.Unknown(IllegalStateException("TTS error")))
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId == id) cont.resumeWithException(AudioError.Unknown(IllegalStateException("TTS error " + errorCode)))
                }
            })
            val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            if (result != TextToSpeech.SUCCESS) {
                cont.resumeWithException(AudioError.Unknown(IllegalStateException("speak() failed")))
            }
            cont.invokeOnCancellation { stop() }
        }
    }

    override fun stop() {
        engine?.stop()
        SecureLog.d(TAG, "TTS stopped (barge-in)")
    }

    fun shutdown() {
        engine?.shutdown()
        engine = null
    }

    companion object {
        private const val TAG = "AndroidTTS"
    }
}
