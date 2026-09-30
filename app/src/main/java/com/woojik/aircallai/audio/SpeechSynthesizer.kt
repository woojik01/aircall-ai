package com.woojik.aircallai.audio

/**
 * PRD-01 오디오 파이프라인: ... -> SpeechSynthesizer -> AudioOutput
 * TTS 재생 중 중지(barge-in) 가능해야 한다 (PRD-03).
 */
interface SpeechSynthesizer {
    /** 텍스트를 읽기 시작하고 재생이 끝나거나 중지될 때까지 대기한다. */
    suspend fun speak(text: String)

    /** 진행 중인 재생을 즉시 중지한다 (barge-in). */
    fun stop()
}
