package com.woojik.aircallai.audio

/**
 * PRD-03: 네트워크 오류와 음성 인식 오류를 구분한다.
 */
sealed class AudioError(
    open val userMessage: String,
    cause: Throwable? = null,
) : Exception(userMessage, cause) {

    /** 온라인 STT 엔진 등 네트워크 의존 오류 (AI provider 오류와는 별개). */
    class Network(cause: Throwable? = null) : AudioError("네트워크 연결을 확인해 주세요.", cause)

    /** 마이크/인식 실패, 권한 거부 등 기기 쪽 오류. */
    class SpeechRecognition(cause: Throwable? = null) : AudioError("음성 인식에 실패했습니다. 다시 시도해 주세요.", cause)

    /** 마이크 권한이 없는 경우. */
    class Permission : AudioError("마이크 권한이 필요합니다. 설정에서 허용해 주세요.")

    class Unknown(cause: Throwable? = null) : AudioError("오디오 오류가 발생했습니다.", cause)
}
