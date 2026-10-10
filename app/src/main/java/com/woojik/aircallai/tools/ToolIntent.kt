package com.woojik.aircallai.tools

/**
 * 도구 사용 의도 탐지 (워크플로우 정확성 개선).
 *
 * 약한 로컬 모델이 "메일 보냈습니다"처럼 Tool을 실행하지 않고 완료를 주장하는 문제를
 * 시스템 차원에서 잡기 위한 휴리스틱. 두 가지를 판단한다:
 * 1. 사용자 메시지에 도구 영역 키워드가 있는가 → 이번 턴에 실행돼야 할 도구 후보.
 * 2. 응답이 완료를 주장하는가 → 실행 근거(성공한 TOOL_RESULT) 없이는 정정 대상.
 *
 * 키워드 매칭은 보수적으로(도메인 단어 중심) 하여 잡담을 도구 요청으로 오판하지 않게 한다.
 */
object ToolIntent {

    /** 도구 이름 → 요청 감지 키워드. */
    private val TOOL_KEYWORDS: List<Pair<String, Regex>> = listOf(
        "gmail" to Regex("메일|이메일|지메일|gmail|발송|전송해"),
        "github" to Regex("깃허브|github|레포|리포|저장소|이슈|issue|풀리퀘|pull.?request|\\bpr\\b|readme"),
        "notes" to Regex("메모|기록해"),
        "calendar" to Regex("캘린더|일정|약속|미팅"),
    )

    /** 완료 주장 표현. "보냈", "보냈어요", "보냈습니다" 등을 잡는다. */
    private val COMPLETION_CLAIM: Regex =
        Regex("(보냈|전송했|발송했|만들었|생성했|작성했|등록했|저장했|완료했|열었|읽었|검색했|찾았)")

    /** 사용자 메시지에서 이번 턴에 필요할 가능성이 있는 도구 이름 집합. */
    fun toolsRequestedIn(userMessage: String): Set<String> {
        val lower = userMessage.lowercase()
        if (Regex("사용법|개념|뜻|차이|원리|무엇|뭐야|방법").containsMatchIn(lower) &&
            !Regex("해\\s*줘|해\\s*주세요|보내\\s*줘|만들어\\s*줘|저장해|조회해").containsMatchIn(lower)) return emptySet()
        return TOOL_KEYWORDS.filter { it.second.containsMatchIn(lower) }.map { it.first }.toSet()
    }

    /** 응답이 작업 완료를 주장하는지. */
    fun claimsCompletion(response: String): Boolean = COMPLETION_CLAIM.containsMatchIn(response)
}
