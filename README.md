# AirCall AI (Android)

로컬 우선 Android AI 음성 비서. 요구사항은 [docs/prd](docs/prd) 참조.

## 현재 단계
- **완료 (main)**: PRD-01 Android 기반, PRD-02 로컬 저장소/자격증명, PRD-03 음성 대화 MVP,
  PRD-04 하이브리드 AI Provider(Local/Cloud·Groq), PRD-05 백그라운드 음성 대화(Foreground Service·지속 알림),
  PRD-07 통화형 UI(통화 화면·상태 표시·오버레이 컨트롤)
- **진행 중**: PRD-06 Tool 연동 — Tool 실행/권한 계층, GitHub 저장소 조회 Adapter 완료.
  인증 UI, Issue/PR 생성, Calendar/Gmail/Notes, 승인 UI는 다음 증분
- **로컬 기능 증분**: 로컬 모델 갤러리(다운로드/적용) + MediaPipe LLM Inference 어댑터.
  모델 카탈로그: Gemma 4 E2B/E4B (.task, litert-community 공개 다운로드). MLC(NPU) 어댑터는 후속 증분
- **PRD-08 (진행 중)**: 개인정보(데이터 흐름) 표시 화면 추가 — Local/Cloud/Tool 각각 어떤 데이터가
  어디로 이동하는지 설정 → 개인정보에서 확인. Release 빌드 R8 축소/난독화 적용(MediaPipe JNI keep 규칙 포함),
  versionCode 2 / 0.2.0
- 기기 테스트(화면 회전/복귀, 다른 앱 전환/화면 OFF/알림·오버레이 제어/배터리, 로컬 모델 실추론,
  Release 빌드 실기 설치)는 아직 미확인

## 구조
```
app/src/main/java/com/woojik/aircallai/
  core/          logging, security(Keystore), storage(CredentialManager)
  ai/            provider(AIProvider, ProviderRouter), local(모델 갤러리·MediaPipe 어댑터), cloud
  cloud/         GroqChatAdapter.kt (HttpCloudApiAdapter)
  audio/         STT/TTS 엔진 (AndroidSpeechRecognizerEngine, AndroidTtsEngine)
  call/          CallStatus/CallControls — 통화 화면 상태 도출(순수 로직)
  conversation/  ChatTurnEngine(ConversationEngine), VoiceSession, VoiceTextCleaner(VoiceResponseSanitizer)
  session/       SessionController, SessionRepository
  service/       ConversationService (Foreground Service)
  overlay/       CallOverlayController — PRD-07 플로팅 컨트롤(권한 거부 시 알림으로 대체)
  tools/         Tool, ToolExecutor, ToolPermissionStore, GitHubTool, GitHubApiClient
  settings/      SettingsRepository (로컬 모델 선택 포함)
  privacy/       PrivacyNotices — 모드별/Tool별 데이터 흐름 설명 단일 소스 (PRD-08)
  ui/            MainActivity, Main/Call/Conversation/Settings/LocalModel/Privacy Screen
```

## 로컬 모델 사용법
1. 설정 → Local 모델 관리 → 모델 선택 → 다운로드 (Wi-Fi 권장, 2~3GB)
2. 다운로드 완료 후 "적용" → AI Mode를 Local로 선택
3. 통화 화면에서 통화 시작 (인터넷 없이 기기에서만 추론)

## 개인정보 (PRD-08)
설정 → 개인정보 · 데이터 흐름에서 모드별 데이터 이동을 확인할 수 있습니다:
- **Local 모드**: 대화 내용이 기기를 벗어나지 않습니다 (네트워크 불필요)
- **Cloud 모드**: 대화 텍스트만 설정한 API 주소로 HTTPS 전송. API Key는 기기 Keystore 암호화 저장
- **Tool 연동**: 사용 시 접근 서비스에만 해당 요청 전송, 현재는 읽기(READ)만 허용

## 빌드
```bash
gradle assembleDebug
gradle testDebugUnitTest
```
JDK 17 필요. 저장소에 wrapper가 없으므로 Gradle 8.7 이상을 로컬에 설치하거나
CI처럼 gradle/actions/setup-gradle로 버전을 지정해 사용합니다.
Release 빌드는 `gradle assembleRelease` — R8 축소/난독화가 적용됩니다.

API Key는 절대 코드/빌드 설정에 넣지 않는다 (PRD-02 CredentialManager 사용 예정).
