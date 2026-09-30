# PRD-04 — 하이브리드 AI Provider

## 목표

사용자가 로컬 AI와 클라우드 AI 중 하나를 선택할 수 있도록 AI 계층을 구현한다.

## Provider 구조

```
AIProvider
├── LocalAIProvider
└── CloudAIProvider
```

ConversationEngine은 구체적인 모델 SDK를 직접 호출하지 않는다.

## Local Mode

목표:
- 가능한 경우 인터넷 없이 대화
- 모델 파일과 실행 엔진을 기기에 저장
- 네트워크 연결을 요구하지 않음
- 대화 데이터가 외부로 전송되지 않도록 구성

모델별 구현은 Adapter로 격리한다.

## Cloud Mode

- 사용자가 직접 API Key를 등록
- Key는 PRD-02의 CredentialManager에만 저장
- 앱이 해당 API를 직접 호출
- 별도 AirCall AI 서버를 거치지 않는다.

## Provider 전환

설정에서:

```
AI Mode
○ Local
○ Cloud
```

사용자가 변경하면 다음 대화부터 해당 Provider를 사용한다.

현재 진행 중인 응답의 Provider를 중간에 바꾸지는 않는다.

## 오류 처리

Local 실패:
- 모델 미설치
- 메모리 부족
- 지원되지 않는 기기
- 모델 로딩 실패

Cloud 실패:
- Key 없음
- 인증 실패
- quota 초과
- 네트워크 실패
- API 오류

각 오류를 구분한다.

## 완료 조건

- Mock Provider 테스트
- Local Provider 인터페이스 연결
- Cloud Provider 인터페이스 연결
- Provider 전환 테스트
- 잘못된 Key 처리
- 네트워크 차단 상태에서 Local Mode 테스트
- CI 통과
