package com.woojik.aircallai.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.woojik.aircallai.core.logging.SecureLog
import kotlinx.coroutines.resume
import kotlinx.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Android SpeechRecognizer 기반 STT. 기기 내 온라인 엔진을 사용하므로
 * 네트워크 오류와 인식 오류를 구분해 AudioError로 전달한다 (PRD-03).
 */
class AndroidSpeechRecognizerEngine(
    private val context: Context,
) : SpeechRecognizerInterface {

    override suspend fun recognizeOnce(): String? {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            throw AudioError.SpeechRecognition(IllegalStateException("recognition unavailable"))
        }
        return suspendCancellableCoroutine { cont ->
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            var done = false
            fun finishOnce(value: String?, error: Throwable?) {
                if (done) return
                done = true
                recognizer.destroy()
                when {
                    error != null -> cont.resumeWithException(error)
                    else -> cont.resume(value)
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
                        else -> AudioError.SpeechRecognition(IllegalStateException("STT error " + error))
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
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                }
            )
            SecureLog.d(TAG, "STT listening started")
            cont.invokeOnCancellation { recognizer.destroy() }
        }
    }

    companion object {
        private const val TAG = "AndroidSTT"
    }
}
