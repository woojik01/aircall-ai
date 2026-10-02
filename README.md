# AirCall AI (Android)

로컬 우선 Android AI 음성 비서. 요구사항은 [docs/prd](docs/prd) 참조.

## 현재 단계
- **완료 (main)**: PRD-01 Android 기반, PRD-02 로컬 저장소/자격증명, PRD-03 음성 대화 MVP,
  PRD-04 하이브리드 AI Provider(Local/Cloud·Groq), PRD-05 백그라운드 음성 대화(Foreground Service·지속 알림),
  PRD-07 통화형 UI(통화 화면·상태 표시·오버레이 컨트롤),
  로컬 모델 갤러리(다운로드/적용) + MediaPipe LLM Inference 어댑터,
  PRD-08 1차(개인정보 데이터 흐름 표시 + Release R8 빌드 구성, versionCode 2 / 0.2.0)
- **진행 중**: PRD-06 Tool 연동 — Tool 실행/권한 계층, GitHub 조회(READ)와 Issue/PR 생성(WRITE) API,
  GitHub 토큰(PAT) 연결 UI, **WRITE 작업 승인 다이얼로그 + 승인 상태 영속화** 완료.
  Calendar/Gmail/Notes Adapter, Tool-AI 응답 연결, 실행 메타데이터 로깅은 다음 증분
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
  tools/         Tool, ToolExecutor, ToolPermissionStore, GitHubTool, GitHubApiClient(READ/WRITE),
                 PersistedToolPermissionStore(승인 영속화), ToolApprovalCoordinator(승인 흐름)
  settings/      SettingsRepository (로컬 모델 선택 포함)
  privacy/       PrivacyNotices — 모드별/Tool별 데이터 흐름 설명 단일 소스 (PRD-08)
  ui/            MainActivity, Main/Call/Conversation/Settings/LocalModel/Privacy Screen, ToolApprovalDialog
```

## 로컬 모델 사용법
1. 설정 → Local 모델 관리 → 모델 선택 → 다운로드 (Wi-Fi 권장, 2~3GB)
2. 다운로드 완료 후 "적용" → AI Mode를 Local로 선택
3. 통화 화면에서 통화 시작 (인터넷 없이 기기에서만 추론)

## Tool 연동 (PRD-06)
- 설정 → GitHub 연동에서 토큰(PAT) 등록 (기기 Keystore 암호화, 로그 미노출)
- `github` Tool: `read_repository`(READ, 기본 허용), `create_issue`/`create_pull_request`(WRITE, 승인 필요)
- **승인 흐름**: WRITE 작업 요청 시 승인 다이얼로그(도구/액션/인자 표시) → 승인하면 이후 재요청 없이 실행
- **승인 영속화**: 승인 상태는 일반 설정에 저장되어 앱 재시작 후에도 유지.
  설정 → Tool 작업 승인에서 목록 확인/개별 해제 가능
- 승인되지 않은 WRITE 작업은 ToolExecutor가 실행 전에 차단한다

## 개인정보 (PRD-08)
설정 → 개인정보 · 데이터 흐름에서 모드별 데이터 이동을 확인할 수 있습니다:
- **Local 모드**: 대화 내용이 기기를 벗어나지 않습니다 (네트워크 불필요)
- **Cloud 모드**: 대화 텍스트만 설정한 API 주소로 HTTPS 전송. API Key는 기기 Keystore 암호화 저장
- **Tool 연동**: 사용 시 접근 서비스에만 해당 요청 전송, 현재는 읽기(READ)만 기본 허용

## 빌드
```bash
gradle assembleDebug
gradle testDebugUnitTest
```
JDK 17 필요. 저장소에 wrapper가 없으므로 Gradle 8.7 이상을 로컬에 설치하거나
CI처럼 gradle/actions/setup-gradle로 버전을 지정해 사용합니다.
Release 빌드는 `gradle assembleRelease` — R8 축소/난독화가 적용됩니다.

API Key/토큰은 절대 코드/빌드 설정에 넣지 않는다 (PRD-02 CredentialManager 사용).
