package com.woojik.aircallai.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.woojik.aircallai.core.logging.SecureLog
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Android TextToSpeech 기반 TTS.
 * stop()은 현재 speak()을 정상 종료시켜 다음 음성 턴이 계속될 수 있게 한다.
 */
class AndroidSpeechSynthesizerEngine(
    context: Context,
) : SpeechSynthesizer {

    private val ready = AtomicBoolean(false)
    private val activeContinuation =
        AtomicReference<kotlinx.coroutines.CancellableContinuation<Unit>?>(null)
    private var engine: TextToSpeech? = null

    init {
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.getDefault()
                ready.set(true)
            }
        }
    }

    override suspend fun speak(text: String) = withContext(Dispatchers.Main.immediate) {
        val tts = engine
        if (tts == null || !ready.get()) {
            throw AudioError.Unknown(IllegalStateException("TTS not ready"))
        }

        suspendCancellableCoroutine { cont ->
            val id = "aircall-" + System.nanoTime()
            activeContinuation.set(cont)

            fun finishSuccess() {
                if (activeContinuation.compareAndSet(cont, null)) {
                    cont.resume(Unit, null)
                }
            }

            fun finishError(error: Throwable) {
                if (activeContinuation.compareAndSet(cont, null)) {
                    cont.resumeWith(Result.failure(error))
                }
            }

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id) finishSuccess()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == id) {
                        finishError(AudioError.Unknown(IllegalStateException("TTS error")))
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId == id) {
                        finishError(
                            AudioError.Unknown(
                                IllegalStateException("TTS error $errorCode"),
                            ),
                        )
                    }
                }
            })

            val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            if (result != TextToSpeech.SUCCESS) {
                finishError(AudioError.Unknown(IllegalStateException("speak() failed")))
            }

            cont.invokeOnCancellation {
                activeContinuation.compareAndSet(cont, null)
                tts.stop()
            }
        }
    }

    override fun stop() {
        engine?.stop()
        activeContinuation.getAndSet(null)?.resume(Unit, null)
        SecureLog.d(TAG, "TTS stopped (barge-in)")
    }

    fun shutdown() {
        engine?.shutdown()
        engine = null
        activeContinuation.getAndSet(null)?.resume(Unit, null)
    }

    companion object {
        private const val TAG = "AndroidTTS"
    }
}
