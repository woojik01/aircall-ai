package com.woojik.aircallai.audio

/**
 * PRD-01 오디오 파이프라인: AudioInput -> SpeechRecognizer -> ConversationEngine
 * 인터페이스라 JVM 테스트와 Android 구현(AndroidSpeechRecognizerEngine)을 분리한다.
 * (android.speech.SpeechRecognizer 클래스명과 충돌하지 않도록 이름을 다르게 지었다.)
 */
interface SpeechRecognizerInterface {
    /** 인식된 문장을 반환한다. 사용자가 아무 말도 하지 않으면 null. */
    suspend fun recognizeOnce(): String?
}
