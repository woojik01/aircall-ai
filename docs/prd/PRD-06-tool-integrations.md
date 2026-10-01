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
- Mock GitHub Tool
- 알 수 없는 Tool의 안전한 실패
- 예외 발생 시 민감정보를 포함하지 않는 일반 오류 반환
- 단위 테스트
- GitHub API Adapter(`GitHubApiClient`): CredentialManager의 `github` 자격증명 사용, 저장소 조회(READ),
  저장소 이름 검증, 네트워크 timeout, IO 스레드 실행, 인증 실패(401/403)/미존재(404)/네트워크 실패 구분,
  토큰을 로그·ToolResult에 노출하지 않음
- GitHub Issue 생성(`create_issue`)·Pull Request 생성(`create_pull_request`) API: WRITE 위험도, 사용자 승인 필요,
  입력 검증(제목·브랜치 이름), 422 요청 오류 구분, JSON 본문 이스케이프, `GitHubTransport`로 HTTP 계층 분리(테스트용 fake)
- `delete*` 작업은 DESTRUCTIVE로 분류
- `SettingsToolPermissionStore`: 작업별 승인 여부를 일반 설정에 영구 저장(앱 재시작 후에도 유지)
- 설정 화면: GitHub Personal Access Token 저장/삭제(CredentialManager, 화면에 마스킹), 작업별 승인 스위치
- AppGraph에서 `ToolExecutor` + `GitHubTool` 구성
- 대화 연결(`ToolCallProtocol` + `ConversationEngine`): 사용 가능한 Tool 목록을 시스템 프롬프트로 알리고,
  AI가 `TOOL: github.<작업>` + `인자: 값` 형식으로 답하면 ToolExecutor로 실행한 뒤 결과를 다시 AI에게 전달한다.
  Tool 호출문과 결과는 화면/음성에 노출하지 않고 최종 답변만 남긴다. 한 턴에 최대 3회 호출.
  텍스트 규약이라 OpenAI 호환 Cloud 모델과 Local 모델 모두에서 동작한다.
- 저장소 조회/로그인 사용자(`get_user`)/Issue·PR 생성 결과를 AI가 말할 수 있는 요약(설명, 스타, 이슈 수, 생성된 URL 등)으로 반환

## 권한 정책

GitHub 예시:
- 저장소 조회: READ
- Issue 생성: WRITE
- Pull Request 생성: WRITE
- 삭제 작업: DESTRUCTIVE

실제 사용자 승인은 UI에서 연결할 수 있도록 ToolPermissionStore를 별도 계층으로 분리한다.

## CredentialManager

기존 PRD-02의 CredentialManager를 그대로 사용한다. 실제 서비스 Adapter가 추가될 때 자격증명을 Tool 내부에서 평문 저장하지 않고 CredentialManager를 통해 읽도록 한다.

## 다음 증분

- OAuth 연결 (현재는 Personal Access Token)
- Calendar/Gmail/Notes Adapter
- 모델 네이티브 function calling 지원 (현재는 텍스트 규약)
- 대화 중 실행 직전 1회성 승인 UI (현재는 설정의 작업별 상시 승인)
- 실행 메타데이터 로깅

## 완료 조건

- Tool interface 동작
- CredentialManager 연동 구조 확보
- Mock GitHub Tool 테스트
- 승인/거부 흐름 테스트
- 인증 실패 처리 구조
- 네트워크 실패 처리 구조
- 민감 데이터 로그 누출 방지
- CI 통과
