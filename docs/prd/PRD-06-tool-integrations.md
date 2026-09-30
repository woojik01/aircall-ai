# PRD-06 — Tool 및 외부 서비스 연동

## 목표

AI가 사용자의 허가를 받아 GitHub, Calendar, Gmail, 메모 등 외부 기능을 사용할 수 있는 Tool 시스템을 구축한다.

## 기본 구조

```
User Request
 → Conversation Engine
 → Tool Selection
 → Permission Check
 → CredentialManager
 → Tool Adapter
 → External API
 → Result
 → AI Response
```

## Tool 인터페이스

각 서비스는 독립 Adapter로 만든다.

예:
- GitHubTool
- CalendarTool
- GmailTool
- NotesTool

AI가 직접 Android API나 OAuth SDK를 호출하지 않도록 한다.

## 인증

OAuth가 필요한 서비스는 사용자가 직접 인증한다.

인증 결과는:
- 서버 저장 금지
- Git 저장 금지
- 로그 출력 금지
- CredentialManager에 로컬 암호화 저장

## 권한

위험도가 높은 작업은 사용자의 확인을 요구한다.

예:
- GitHub 읽기 → 기본 허용 가능
- GitHub 코드 수정/PR 생성 → 명시적 승인
- Gmail 읽기 → 명시적 연결 및 권한
- 메일 전송 → 실행 직전 확인
- 파일 삭제 → 반드시 확인

## 최소 권한

OAuth scope는 기능에 필요한 최소 범위만 요청한다.

## Tool 실행 기록

민감한 원문을 무기한 저장하지 않는다.

필요한 경우:
- 실행 시간
- Tool 이름
- 성공/실패
- 사용자 승인 여부

정도의 비민감 메타데이터만 로컬에 기록한다.

## 완료 조건

- Tool interface 동작
- CredentialManager 연동
- Mock GitHub Tool 테스트
- 승인/거부 흐름 테스트
- 인증 실패 처리
- 네트워크 실패 처리
- 민감 데이터 로그 누출 검사
- CI 통과
