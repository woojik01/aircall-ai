package com.woojik.aircallai.tools

/** One catalog supplies discovery, validation and provider-native JSON schemas. */
object AppToolCatalog {
    const val DESCRIPTION =
        "github.read_repository owner=<소유자> repo=<저장소> — GitHub 저장소 정보를 조회한다\n" +
        "github.read_file owner=<소유자> repo=<저장소> path=<파일 경로> — 저장소 파일 내용을 읽는다\n" +
        "github.create_issue owner=<소유자> repo=<저장소> title=<제목> [body=<내용>] — Issue를 만든다 (승인 필요)\n" +
        "github.create_pull_request owner=<소유자> repo=<저장소> title=<제목> head=<브랜치> base=<브랜치> [body=<내용>] — PR을 만든다 (승인 필요)\n" +
        "notes.add_note text=<내용> — 메모를 기기에 저장한다 (승인 필요)\n" +
        "notes.search_notes query=<검색어> — 메모를 검색한다\n" +
        "notes.list_notes [limit=<개수>] — 최근 메모를 나열한다\n" +
        "gmail.send_email to=<주소> subject=<제목> body=<내용> — 이메일을 보낸다 (승인 필요)"
}
