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

- GitHub Issue/PR 생성 API (WRITE, 승인 필요)
- OAuth 또는 Personal Access Token 연결 UI
- Calendar/Gmail/Notes Adapter
- Tool 선택과 AI 응답 연결
- 사용자 승인 UI
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
