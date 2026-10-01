package com.woojik.aircallai.privacy

/**
 * PRD-08 개인정보: 사용자가 데이터 흐름을 명확히 이해할 수 있도록
 * 모드별 데이터 이동을 하나의 소스로 설명한다. UI와 설정 화면이 같은 문구를 쓴다.
 */
data class PrivacyNotice(
    val title: String,
    val detail: String,
)

object PrivacyNotices {

    /** Local Mode: 어떤 데이터도 외부로 나가지 않는다. */
    val localMode = PrivacyNotice(
        title = "Local 모드",
        detail = "대화 내용은 이 기기를 벗어나지 않습니다. 음성 인식은 기기의 STT, " +
            "AI 응답은 이 기기에 다운로드한 모델이 생성하고, 음성 출력도 기기 TTS로 재생됩니다. " +
            "네트워크 연결 없이 동작합니다.",
    )

    /** Cloud Mode: 어떤 데이터가 어디로 전송되는지. */
    val cloudMode = PrivacyNotice(
        title = "Cloud 모드",
        detail = "대화 텍스트가 설정한 Cloud API 주소(기본: Groq)로 HTTPS로 전송되어 응답이 생성됩니다. " +
            "음성 인식/출력은 기기에서 처리되며, 전송되는 것은 텍스트 대화 내용뿐입니다. " +
            "API Key는 기기의 Android Keystore로 암호화되어 저장되며 서버로 보내지 않습니다.",
    )

    /** Tool: 어떤 서비스에 접근하는지 (PRD-06 GitHub 조회 등). */
    val tools = PrivacyNotice(
        title = "Tool 연동",
        detail = "Tool 사용 시 접근하는 서비스(GitHub 등)에만 해당 요청이 전송됩니다. " +
            "현재는 저장소 조회(READ)만 허용되며, 쓰기 작업은 권한 계층에서 차단됩니다. " +
            "각 서비스의 자격증명은 기기에 암호화 보관됩니다.",
    )
}
