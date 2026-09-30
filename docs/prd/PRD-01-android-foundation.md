# PRD-01 — Android 기반 및 앱 아키텍처

## 목표

AirCall AI의 최소 실행 가능한 Android 앱과 장기 확장이 가능한 모듈 구조를 구축한다.

## 범위

- Android 프로젝트 생성
- Kotlin 기반 구현
- 최소 지원 Android 버전은 구현 시점의 최신 안정 Android 요구사항과 실제 필요한 API를 기준으로 결정한다.
- 단일 앱 모듈을 기본으로 하되 기능별 패키지를 명확히 분리한다.
- 기본 화면, 설정 화면, 대화 화면의 뼈대를 만든다.
- 앱 시작/종료 및 상태 복구 구조를 만든다.

## 권장 패키지 구조

```
app/
  core/
    security/
    storage/
    network/
    logging/
  conversation/
  audio/
  ai/
    provider/
    local/
    cloud/
  background/
  tools/
  ui/
  settings/
```

## 핵심 인터페이스

AI 모델 구현이 UI에 직접 의존하지 않도록 다음 개념을 분리한다.

```text
ConversationEngine
    ↓
AIProvider
    ├── LocalAIProvider
    └── CloudAIProvider
```

오디오 역시:

```text
AudioInput → SpeechRecognizer → ConversationEngine → SpeechSynthesizer → AudioOutput
```

## 요구사항

1. 앱은 인터넷 없이도 기본 UI를 실행할 수 있어야 한다.
2. 네트워크가 없다고 앱 자체가 시작되지 않으면 안 된다.
3. API Key를 코드에 넣지 않는다.
4. 전역 Singleton 남용을 피한다.
5. Coroutine/Flow 등 비동기 처리를 사용해 UI 스레드를 차단하지 않는다.
6. 화면 회전/프로세스 재생성에 대비해 대화 상태를 별도 상태 계층에서 관리한다.

## 완료 조건

- Debug APK 빌드 성공
- 앱 설치 및 실행
- 메인/대화/설정 화면 이동
- 기본 단위 테스트 통과
- CI 통과
