package com.woojik.aircallai.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.woojik.aircallai.core.logging.SecureLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Android SpeechRecognizer 기반 STT.
 *
 * SpeechRecognizer의 생성/리스닝 제어는 메인 스레드에서 수행한다.
 * 오류는 continuation을 취소시키지 않고 정상적인 예외 결과로 전달해
 * SessionController 전체가 취소되는 것을 방지한다.
 */
class AndroidSpeechRecognizerEngine(
    private val context: Context,
) : SpeechRecognizerInterface {

    override suspend fun recognizeOnce(): String? = withContext(Dispatchers.Main.immediate) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            throw AudioError.SpeechRecognition(IllegalStateException("recognition unavailable"))
        }

        suspendCancellableCoroutine { cont ->
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            var done = false

            fun finishOnce(value: String?, error: Throwable?) {
                if (done) return
                done = true
                recognizer.destroy()
                if (error != null) {
                    cont.resumeWith(Result.failure(error))
                } else {
                    cont.resume(value, null)
                }
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val text = results
                        .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull { it.isNotBlank() }
                    finishOnce(text, null)
                }

                override fun onError(error: Int) = finishOnce(
                    null,
                    when (error) {
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> AudioError.Network()
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> AudioError.Permission()
                        else -> AudioError.SpeechRecognition(
                            IllegalStateException("STT error $error"),
                        )
                    },
                )

                override fun onBeginningOfSpeech() {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onRmsChanged(rmsdB: Float) {}
            })

            recognizer.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                },
            )
            SecureLog.d(TAG, "STT listening started")

            cont.invokeOnCancellation {
                recognizer.destroy()
            }
        }
    }

    companion object {
        private const val TAG = "AndroidSTT"
    }
}
