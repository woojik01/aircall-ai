package com.woojik.aircallai.ai.provider

/**
 * PRD-04: Local 실패(모델 미설치/메모리 부족/미지원 기기/로딩 실패)와
 * Cloud 실패(Key 없음/인증 실패/quota 초과/네트워크 실패/API 오류)를 구분한다.
 */
enum class ProviderErrorKind(val userMessage: String) {
    // Local
    MODEL_NOT_INSTALLED("설정 → 로컬 모델 관리에서 모델을 다운로드하고 적용해 주세요."),
    MEMORY("메모리가 부족해 로컬 AI를 실행할 수 없습니다."),
    UNSUPPORTED_DEVICE("이 기기는 로컬 AI를 지원하지 않습니다."),
    LOAD_FAILED("로컬 AI 모델을 로드하지 못했습니다. 다른 앱을 닫고 다시 적용하거나 모델을 다시 다운로드해 주세요."),

    INPUT_TOO_LONG("로컬 모델의 입력 한도를 넘었습니다. 내용을 나눠서 보내 주세요."),
    INFERENCE_FAILED("로컬 AI 추론에 실패했습니다. 로컬 모델 화면에서 실행 장치와 오류 코드를 확인해 주세요."),

    // Cloud
    NO_KEY("등록된 API Key가 없습니다. 설정에서 Key를 등록해 주세요."),
    AUTH_FAILED("API Key가 올바르지 않습니다."),
    QUOTA_EXCEEDED("API 사용량 한도를 초과했습니다."),
    NETWORK("네트워크 연결을 확인해 주세요."),
    API_ERROR("AI 서비스 오류가 발생했습니다."),
}

class AIProviderException(
    val kind: ProviderErrorKind,
    detail: String? = null,
) : Exception(kind.userMessage + (detail?.let { " (" + it + ")" } ?: ""))
