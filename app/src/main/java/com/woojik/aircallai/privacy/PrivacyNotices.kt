package com.woojik.aircallai.privacy

/** User-facing descriptions of the implemented data flows. */
data class PrivacyNotice(val title: String, val detail: String)

object PrivacyNotices {
    val localMode = PrivacyNotice(
        title = "Local 모드",
        detail = "AI 응답은 다운로드한 모델이 기기에서 생성하며, 이 추론에는 대화 텍스트를 외부 AI 서버로 보내지 않습니다. " +
            "모델 다운로드에는 인터넷이 필요합니다. 통화의 음성 인식과 출력은 Android 음성 서비스를 사용합니다. " +
            "서비스와 언어 설정에 따라 음성·텍스트가 해당 제공자에게 전송되거나 네트워크 연결이 필요할 수 있습니다.",
    )

    val cloudMode = PrivacyNotice(
        title = "Cloud 모드",
        detail = "대화 텍스트가 설정한 Cloud API 주소(기본: Groq)로 HTTPS로 전송되어 응답이 생성됩니다. " +
            "API Key는 기기에 암호화 저장되며, 요청을 인증하기 위해 해당 API 서버로 전송됩니다. " +
            "음성 인식과 출력은 Android 음성 서비스를 사용하며, 서비스 설정에 따라 음성·텍스트가 제공자에게 전송될 수 있습니다.",
    )

    val tools = PrivacyNotice(
        title = "Tool 연동",
        detail = "GitHub 토큰 저장과 Tool 실행·승인 계층이 구현되어 있습니다. 대화에서 Tool을 호출하는 기능은 아직 연결되지 않았습니다. " +
            "실행 계층은 저장소 조회(READ)를 기본 허용하고 Issue/PR 생성(WRITE)은 승인 후 허용합니다. " +
            "승인은 도구·작업 단위로 저장되어 해제 전까지 유지됩니다. 실행 시 요청과 인증 토큰은 해당 서비스에 전송됩니다.",
    )
}
