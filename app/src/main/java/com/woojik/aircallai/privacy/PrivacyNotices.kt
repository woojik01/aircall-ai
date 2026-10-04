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
        detail = "대화 텍스트와 응답 생성에 필요한 도구 결과가 설정한 Cloud API 주소(기본: Groq)로 HTTPS로 전송되어 응답이 생성됩니다. " +
            "API Key는 기기에 암호화 저장되며, 요청을 인증하기 위해 해당 API 서버로 전송됩니다. " +
            "음성 인식과 출력은 Android 음성 서비스를 사용하며, 서비스 설정에 따라 음성·텍스트가 제공자에게 전송될 수 있습니다.",
    )

    val tools = PrivacyNotice(
        title = "Tool 연동",
        detail = "GitHub·Google 로그인으로 연결된 Tool 실행·승인 계층을 사용합니다. " +
            "저장소 조회·메모 검색·일정 조회(READ)는 기본 허용하고 Issue/PR 생성, 메모 추가, 일정 등록, 메일 발송(WRITE)은 승인 후 허용합니다. " +
            "Notes 메모는 네트워크 없이 기기에만 저장됩니다. Calendar 연동은 Google 기본 캘린더를 읽고 쓰며 일정 정보와 인증 토큰을 Google 서버로 전송합니다. " +
            "Gmail 발송 시 메일 제목·내용과 인증 토큰이 Google 서버로 전송됩니다. " +
            "승인은 도구·작업 단위로 저장되어 해제 전까지 유지되며, 실행 시 요청과 인증 토큰은 해당 서비스에 전송됩니다.",
    )

    val reports = PrivacyNotice(
        title = "AI 응답 신고 및 문의",
        detail = "응답의 신고 버튼 또는 이 화면에서 앱을 떠나지 않고 신고할 수 있습니다. " +
            "직접 작성한 내용, 앱 버전, Android 버전이 신고 접수 서비스로 전송됩니다. " +
            "선택한 응답은 함께 보내기를 선택한 경우에만 포함하며 전송 전에 수정할 수 있습니다. " +
            "전체 대화, API 키, 로그인 토큰, 지속적인 기기 식별자는 신고에 자동 포함하지 않습니다. " +
            "접수 번호로 신고 삭제를 요청할 수 있으며 보관 기간은 공개 개인정보처리방침을 확인해 주세요.",
    )
}
