package com.woojik.aircallai.tools

/** Shared wording for approval dialogs, settings and execution notifications. */
object ToolLabels {
    fun action(tool: String, action: String): String = when ("$tool.$action") {
        "notes.add_note" -> "메모 저장"
        "notes.search_notes" -> "메모 검색"
        "notes.list_notes" -> "메모 조회"
        "github.read_repository" -> "GitHub 저장소 조회"
        "github.read_file" -> "GitHub 파일 조회"
        "github.create_issue" -> "GitHub 이슈 생성"
        "github.create_pull_request" -> "GitHub PR 생성"
        "calendar.read_upcoming" -> "일정 조회"
        "calendar.create_event" -> "일정 등록"
        "gmail.send_email" -> "이메일 발송"
        else -> "도구 작업"
    }

    fun argument(key: String): String = when (key) {
        "to" -> "받는 사람"
        "subject" -> "메일 제목"
        "body" -> "내용"
        "title" -> "제목"
        "owner" -> "저장소 소유자"
        "repo" -> "저장소 이름"
        "path", "file_path" -> "파일 경로"
        "head" -> "변경 사항이 있는 브랜치"
        "base" -> "변경 사항을 받을 브랜치"
        "start" -> "시작 날짜와 시간"
        "start_epoch_ms" -> "시작 시간 (유닉스 시간, 밀리초)"
        "duration_minutes" -> "진행 시간 (분)"
        "text", "content" -> "내용"
        "query" -> "검색어"
        "limit" -> "최대 조회 개수"
        else -> key
    }
}
