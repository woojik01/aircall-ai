# PRD-06 — Tool 및 외부 서비스 연동

## 목표

AI가 사용자의 허가를 받아 외부 기능을 호출할 수 있는 안전한 Tool 실행 계층을 제공한다.

## 현재 구현

```
ToolRequest
  → ToolExecutor
  → Tool.riskFor(action)
  → Permission Check
  → Tool.execute()
  → ToolResult
```

구현 항목:
- Tool 공통 인터페이스
- 작업별 위험도 READ, WRITE, DESTRUCTIVE
- READ 작업은 기본 허용
- WRITE와 DESTRUCTIVE 작업은 명시적 승인 필요
- 승인되지 않은 작업은 실행하지 않음
- 알 수 없는 Tool의 안전한 실패
- 예외 발생 시 민감정보를 포함하지 않는 일반 오류 반환
- 단위 테스트
- GitHub API Adapter(`GitHubApiClient`): CredentialManager의 `github` 자격증명 사용, 저장소 조회(READ),
  Issue/PR 생성(WRITE), 저장소 이름 검증, 네트워크 timeout, IO 스레드 실행,
  인증 실패(401/403)/미존재(404)/네트워크 실패 구분, 토큰을 로그·ToolResult에 노출하지 않음
- Notes Adapter(`NotesTool`/`FileNotesStore`): 기기 로컬 파일에 메모를 저장한다(네트워크 없음).
  `add_note`(WRITE), `search_notes`/`list_notes`(READ)
- Calendar Adapter(`CalendarTool`/`AndroidCalendarAdapter`): 기기 캘린더(ContentResolver)를 읽고 쓴다.
  `read_upcoming`(READ), `create_event`(WRITE). 시각은 `CalendarTimeParser`가 "YYYY-MM-DD HH:MM" 형식으로 검증한다
- Gmail Adapter(`GmailTool`/`GmailApiClient`): CredentialManager의 `gmail` 자격증명으로
  Gmail REST API(messages/send)를 호출한다. `send_email`(WRITE).
  수신자 주소 검증, 헤더 주입(CRLF) 차단, 토큰 미노출
- Tool-AI 연결(`ToolCallParser`/`ToolBridgedAIProvider`): AI 응답의 `TOOL:` 지시어를 파싱·실행하고
  결과를 TOOL_RESULT로 이력에 반영해 최종 답변을 생성한다(최대 2 라운드).
  WRITE 미승인 차단 시 승인 코디네이터에 요청을 전달하고 승인 안내 응답을 반환한다
- 실행 메타데이터 로깅(`ToolExecutionLogger`): 도구/액션/위험도/차단/성공/지연/시각 기록.
  인자 값은 미기록(민감 정보 방지), 최근 100건 StateFlow + 디버그 로그 요약(SecureLog 마스킹)

## 권한 정책

GitHub 예시:
- 저장소 조회: READ
- Issue 생성: WRITE
- Pull Request 생성: WRITE
- 삭제 작업: DESTRUCTIVE

Notes: `add_note` WRITE, 검색/목록 READ.
Calendar: `read_upcoming` READ, `create_event` WRITE.
Gmail: `send_email` WRITE.

실제 사용자 승인은 UI에서 연결할 수 있도록 ToolPermissionStore를 별도 계층으로 분리한다.
WRITE 승인 상태는 일반 설정에 영속화된다(PersistedToolPermissionStore).

## CredentialManager

기존 PRD-02의 CredentialManager를 그대로 사용한다. 서비스 Adapter는 자격증명을 Tool 내부에서
평문 저장하지 않고 CredentialManager를 통해 읽는다(GitHub=`github`, Gmail=`gmail`).

## 후속 증분 (완료)

- GitHub Issue/PR 생성 API (WRITE, 승인 필요) — 완료
- OAuth 또는 Personal Access Token 연결 UI — 완료(PAT)
- Calendar/Gmail/Notes Adapter — 완료
- Tool 선택과 AI 응답 연결 — 완료
- 사용자 승인 UI — 완료
- 실행 메타데이터 로깅 — 완료

Gmail OAuth 토큰 발급·갱신 흐름과 캘린더 계정 선택 UI는 실기 검증 후 후속 증분에서 다룬다.

## 완료 조건

- Tool interface 동작
- CredentialManager 연동 구조 확보
- Mock GitHub Tool 테스트
- 승인/거부 흐름 테스트
- 인증 실패 처리 구조
- 네트워크 실패 처리 구조
- 민감 데이터 로그 누출 방지
- CI 통과
