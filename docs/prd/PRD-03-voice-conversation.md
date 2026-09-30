# PRD-03 — 음성 대화 MVP

## 목표

사용자가 버튼을 누르고 말하면 AI가 음성으로 응답하는 최소 음성 대화 루프를 구현한다.

## 흐름

```
Microphone
 → Audio Capture
 → Speech-to-Text
 → Conversation Engine
 → AI Provider
 → Text-to-Speech
 → Speaker
```

## 요구사항

- 마이크 권한 요청
- 음성 입력 시작/종료
- 음성 인식 결과 표시
- AI 응답 표시
- TTS 재생
- 재생 중 중지 가능
- 오류 상태 표시
- 네트워크 오류와 음성 인식 오류를 구분

## 대화 상태

```
IDLE
LISTENING
PROCESSING
SPEAKING
ERROR
```

상태 전환을 명시적으로 관리한다.

## Barge-in 준비

MVP부터 오디오 파이프라인을 분리하여 AI 음성 출력 중 사용자의 음성을 감지할 수 있는 구조를 만든다.

최종 동작:

```
AI speaking
    ↓
user starts speaking
    ↓
TTS immediately stops
    ↓
new utterance processed
```

## 지연시간

각 단계의 latency를 측정할 수 있도록 timestamp를 남긴다. 개인정보나 음성 원문은 디버그 로그에 자동 저장하지 않는다.

## 완료 조건

- 실제 Android 기기에서 음성 입력 성공
- AI 응답을 음성으로 재생
- TTS 중단 가능
- 오류 복구 가능
- 기본 대화 10회 이상 연속 수행 테스트
- CI 통과
