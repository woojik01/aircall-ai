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
 * Android TextToSpeech 기반 TTS (PRD-05 개선: 사람에 가까운 자연스러운 음성).
 * - 한국어 음성을 우선 사용한다 (기기에 없으면 기본 언어로 대체).
 * - 약간 느린 속도와 따뜻한 톤으로 설정해 억양이 살아나게 한다.
 * - 문장 부호(. , ! ?)를 그대로 전달해 TTS가 의문/감탄 억양을 만들게 한다.
 * - stop()은 현재 speak()을 정상 종료시켜 다음 음성 턴이 계속될 수 있게 한다.
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
                engine?.let { configure(it) }
                ready.set(true)
            }
        }
    }

    /** 자연스러운 음성을 위한 엔진 설정. 억양은 문장 부호를 읽어 TTS가 스스로 만든다. */
    private fun configure(tts: TextToSpeech) {
        val koResult = tts.setLanguage(Locale.KOREAN)
        if (koResult == TextToSpeech.LANG_MISSING_DATA || koResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.language = Locale.getDefault()
        }
        tts.setSpeechRate(NATURAL_SPEECH_RATE)
        tts.setPitch(NATURAL_PITCH)
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
                                IllegalStateException("TTS error " + errorCode),
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
        private const val NATURAL_SPEECH_RATE = 0.95f
        private const val NATURAL_PITCH = 1.05f
    }
}
