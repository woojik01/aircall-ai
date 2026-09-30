package com.woojik.aircallai.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer as AndroidSpeechRecognizerApi
import com.woojik.aircallai.core.logging.SecureLog

/**
 * Android SpeechRecognizer 기반 STT. 기기 내 온라인 엔진을 사용하므로
 * 네트워크 오류와 인식 오류를 구분해 AudioError로 전달한다 (PRD-03).
 */
class AndroidSpeechRecognizerEngine(
    private val context: Context,
) : SpeechRecognizer {

    override suspend fun recognizeOnce(): String? {
        if (!AndroidSpeechRecognizerApi.isRecognitionAvailable(context)) {
            throw AudioError.SpeechRecognition(IllegalStateException("recognition unavailable"))
        }
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val recognizer = AndroidSpeechRecognizerApi.createSpeechRecognizer(context)
            var result: String? = null
            var done = false
            fun finishOnce(value: String?, error: Throwable?) {
                if (done) return
                done = true
                recognizer.destroy()
                when {
                    error != null -> cont.resumeWithException(error)
                    else -> cont.resumeWith(value)
                }
            }
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val text = results
                        .getStringArrayList(AndroidSpeechRecognizerApi.RESULTS_RECOGNITION)
                        ?.firstOrNull { it.isNotBlank() }
                    result = text
                    finishOnce(text, null)
                }
                override fun onError(error: Int) = finishOnce(
                    null,
                    when (error) {
                        AndroidSpeechRecognizerApi.ERROR_NETWORK,
                        AndroidSpeechRecognizerApi.ERROR_NETWORK_TIMEOUT -> AudioError.Network()
                        AndroidSpeechRecognizerApi.ERROR_INSUFFICIENT_PERMISSIONS -> AudioError.Permission()
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
                Intent(AndroidSpeechRecognizerApi.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(AndroidSpeechRecognizerApi.EXTRA_LANGUAGE_MODEL,
                        AndroidSpeechRecognizerApi.LANGUAGE_MODEL_FREE_FORM)
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
