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
- **예정**: PRD-08 안정화/릴리스
- 기기 테스트(화면 회전/복귀, 다른 앱 전환/화면 OFF/알림·오버레이 제어/배터리, 로컬 모델 실추론)는 아직 미확인

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
  ui/            MainActivity, Main/Call/Conversation/Settings/LocalModel Screen
```

## 로컬 모델 사용법
1. 설정 → Local 모델 관리 → 모델 선택 → 다운로드 (Wi-Fi 권장, 2~3GB)
2. 다운로드 완료 후 "적용" → AI Mode를 Local로 선택
3. 통화 화면에서 통화 시작 (인터넷 없이 기기에서만 추론)

## 빌드
```bash
gradle assembleDebug
gradle testDebugUnitTest
```
JDK 17 필요. 저장소에 wrapper가 없으므로 Gradle 8.7 이상을 로컬에 설치하거나
CI처럼 gradle/actions/setup-gradle로 버전을 지정해 사용합니다.

API Key는 절대 코드/빌드 설정에 넣지 않는다 (PRD-02 CredentialManager 사용 예정).
