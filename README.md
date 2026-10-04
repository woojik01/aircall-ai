# AirCall AI (Android)

로컬 AI 모델 또는 Cloud API로 텍스트·음성 대화를 제공하는 Android 앱입니다.
[단계별 PRD](docs/prd)는 목표 사양이며, 아래는 현재 구현 상태입니다.

[설치·서명·릴리스 안내](docs/RELEASING.md) · [Google Play 제출 준비](docs/play/README.md) · [0.4.0 변경 기록](CHANGELOG.md)

0.4.0은 앱 내 응답 신고, 클라우드·음성 데이터 고지와 동의, 전체 기기 데이터 삭제,
최소 Google 권한, 공개 정책 생성과 AAB 검증을 추가합니다. 첫 배포는 무료·광고 없음으로 준비하며
개발자 계정·공개 연락처·신고 서비스·개인 서명키와 실제 Play 테스트는 별도 준비가 필요합니다.

메시지를 보내지 않은 방은 방을 떠날 때 자동 정리되며 현재 빈 방은 저장하지 않습니다.
AI 도구 작업은 승인 후 실제 결과가 AI에게 전달되고 대화가 이어집니다. 성공·실패 결과는
앱과 시스템 알림에 표시됩니다. Android 13 이상에서는 알림 허용이 필요하며, 거부해도 앱에서 결과를 확인할 수 있습니다.

## 현재 구현 상태

- Android 앱, 기기 저장소·Keystore 자격증명 저장, Local/Cloud Provider 구조
- 텍스트 대화와 Android STT/TTS를 이용한 음성 대화
- Foreground Service·알림·오버레이 컨트롤과 통화형 화면
- 로컬 모델 다운로드·파일 검증·로드 후 적용, LiteRT-LM CPU 추론 어댑터
- GitHub PAT 저장, 저장소 조회 및 Issue/PR 생성 API, WRITE 승인·승인 영속화 계층
- 대화에서 Tool을 호출하는 연결(TOOL 지시어 파싱·실행·결과 반영)과 실행 메타데이터 로깅
- PRD-06 Tool 어댑터 전체: GitHub, Notes(기기 로컬), Calendar(기기 캘린더), Gmail
- 캘린더 런타임 권한 요청 UI와 Gmail OAuth 토큰 등록 UI
- PRD-09 인증 추상화: OAuth 상태 모델·Provider 인터페이스·자격증명 저장소(갱신 포함)·연결 저장소
- GitHub Device Flow 및 Google Android AuthorizationClient 연결, 서비스별 연결/해제 UI
- PRD-09 소셜 로그인 UX: "GitHub로 로그인"/"Google로 로그인" 버튼이 기본.
  OAuth Client ID는 빌드 시점 기본값(BuildConfig)을 사용하며 고급 설정에서 재정의 가능.
  수동 PAT/토큰 입력은 고급 설정으로 유지
- 개인정보 안내와 Release R8 빌드, Android 16 대상 빌드, 정식 APK/AAB 릴리스 초안 구성 (앱 버전 0.3.0)

**실기 검증 필요:** 로컬 모델의 Android 추론 성능과 메모리 사용, 음성 인식·출력,
화면 회전·복귀·다른 앱 전환·화면 OFF·알림/오버레이·배터리 동작, Release APK 설치,
GitHub/Google OAuth 실제 연결·해제(각 서비스 OAuth 앱 등록 후 실기 테스트 필요).
캘린더 연동은 실기 검증 완료(2026-10-03).
PRD 전체 완료나 출시 준비 완료를 의미하지 않습니다.

## 로컬 모델 사용법

1. 설정 → 로컬 모델 관리 → 모델 다운로드 (E2B 약 2.6GB, E4B 약 3.7GB, Wi-Fi 권장).
2. 다운로드 크기와 SHA-256 검증 완료 후 **적용**을 누릅니다. 실제 모델 로드가 성공해야 선택이 저장됩니다.
3. AI Mode를 Local로 선택하고 텍스트 대화 또는 통화 화면으로 이동합니다.

Gemma 4는 **LiteRT-LM 0.17.1**과 `.litertlm` 파일을 사용합니다.
이전 버전의 웹용 `.task` 파일은 사용할 수 없으므로 모델을 새로 다운로드하고 적용해야 합니다.
파일은 앱 전용 저장소에 보관됩니다. 모델 관리 화면의 **이전 모델 파일 삭제**로
이전 웹용 파일의 저장 공간을 회수할 수 있습니다. API Key·토큰·설정은 유지됩니다.

64비트 ARM 또는 x86_64가 필요하며 앱은 총 RAM을 기준으로 E2B 4GiB, E4B 8GiB 이상을 검사합니다.
이 검사는 실행 성공이나 속도를 보장하지 않습니다. 사용 가능한 메모리가 부족하면 다른 앱을 닫고
다시 적용해 주세요. 로드 실패는 모델 관리 화면에 표시됩니다.

- 로컬 **AI 텍스트 추론**은 모델 다운로드 후 네트워크 없이 실행됩니다.
- **음성 인식·출력**은 Android의 음성 서비스를 사용합니다. 서비스·언어 설정에 따라 네트워크가
  필요하거나 음성·텍스트가 음성 서비스 제공자에게 전송될 수 있어, 오프라인 통화는 보장하지 않습니다.
- 모델 다운로드는 앱이 실행되는 동안 진행됩니다. 프로세스가 종료되면 다시 다운로드해야 하며 이어받기는 지원하지 않습니다.

모델 형식과 엔진 API: [Gemma 4 
E2B 배포 안내](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm),
[LiteRT-LM Kotlin API](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.10.2/docs/api/kotlin/getting_started.md).
카탈로그는 모델 revision·파일 크기·SHA-256을 고정합니다.

## Tool 연동 계층

- **GitHub 연동(소셜 로그인)**: 설정의 **"GitHub로 로그인"** 버튼(기기 인증).
  브라우저에서 표시된 코드를 입력·승인하면 연결된다. Client ID는 빌드 시점 기본값을 사용하며,
  없으면 고급 설정에서 1회 등록한다. 고급으로 PAT 직접 입력 유지
- `github` Tool: `read_repository`는 기본 허용, `create_issue`/`create_pull_request`는 승인 필요
- **Gmail 연동(소셜 로그인)**: 설정의 **"Google로 로그인"** 버튼.
  Android AuthorizationClient 승인 후 연결된다. 현재 Google 연결에는 refresh token이 없으므로 만료되면 다시 권한을 요청한다.
  Client ID는 빌드 시점 기본값 또는 고급 설정 등록(Android 유형 OAuth 클라이언트).
  고급으로 액세스 토큰 직접 입력 유지
- **Tool-AI 연결**: AI가 `TOOL: <도구>.<액션> key=value` 지시어를 응답하면
  실행 결과를 대화에 반영해 최종 답변한다 (최대 6회 도구 호출, 같은 변경 요청 반복 차단)
- `notes` Tool: `add_note`(승인 필요), `search_notes`/`list_notes`(기본 허용) — 기기 로컬 저장소 사용
- `calendar` Tool: `read_upcoming`(기본 허용), `create_event`(승인 필요) — Google 연결의 Calendar API 사용
- `gmail` Tool: `send_email`(승인 필요) — Gmail API(messages/send) 호출
- 승인은 **도구·작업 단위**로 저장된다. 승인 후에는 다른 인자로 같은 작업을 요청해도
  다시 묻지 않으며, 앱 재시작 후에도 유지된다. 설정 → Tool 작업 승인에서 해제할 수 있다.
- **계정 연결은 서비스별로 독립적**: 연결/해제도 서비스 단위이며 한 서비스 해제가 다른 서비스에 영향을 주지 않는다.
  OAuth 취소·실패가 앱 사용을 중단하지 않는다
- **실행 메타데이터 로깅**: 도구/액션/위험도/차단 여부/성공/지연(ms)을 기록한다
  (인자 값은 민감 정보 방지를 위해 미기록, 최근 100건 메모리 + 디버그 로그 요약)

## OAuth 앱 등록 (개발자 1회)

소셜 로그인 버튼은 OAuth Client ID가 앱에 포함되어 있어야 바로 동작한다.
Client ID는 공개값이므로 빌드에 포함해도 안전하다.

1. **GitHub**: github.com → Settings → Developer settings → OAuth Apps → New OAuth App.
   앱 이름·홈페이지 URL 입력(콜백 URL은 비워도 됨, 기기 흐름 사용). 발급된 Client ID를 아래에 등록.
2. **Google**: Google Cloud Console → 프로젝트 생성 → API 및 서비스 → 사용자 인증 정보 →
   OAuth 클라이언트 ID(**Android** 유형) 생성. 패키지 이름 `com.woojik.aircallai`,
   SHA-1 인증서 지문(debug.keystore의 것, 아래 참조) 입력. Gmail API 사용 설정 필요.
   현재는 Android AuthorizationClient를 사용하며 커스텀 콜백 스킴을 등록하지 않는다.
   정식 배포용은 `com.woojik.aircallai.release`와 정식 서명 인증서를 별도 등록한다 ([안내](docs/RELEASING.md)).
3. 빌드 머신의 `~/.gradle/gradle.properties`(또는 CI 시크릿)에 추가:

```properties
GITHUB_OAUTH_CLIENT_ID=Iv1.xxxx
GOOGLE_OAUTH_CLIENT_
ID=xxxx.apps.googleusercontent.com
```

등록하지 않고 빌드하면 버튼 대신 안내 문구가 표시되며, 앱의 고급 설정에서도 등록할 수 있다.

## 개인정보

설정 → 개인정보 · 데이터 흐름에서 확인할 수 있습니다.

- **Local:** AI 추론용 대화 텍스트를 외부 AI 서버로 보내지 않습니다. 음성 서비스의 네트워크 사용은 별도입니다.
- **Cloud:** 대화 텍스트와 인증용 API Key가 설정한 HTTPS API 주소로 전송됩니다. Key는 기기에 암호화 저장됩니다.
- **Tool:** 실행 시 요청과 인증 토큰이 해당 서비스로 전송됩니다. READ는 기본 허용, WRITE는 승인 후 실행됩니다.
  Notes는 기기에만 저장되고, Calendar는 기기 캘린더를 사용하며, Gmail은 발송 시 Google 서버로 전송됩니다.
- **계정 연결:** GitHub/Google 인증은 해당 서비스에서 수행되며 access/refresh token은 기기에만 암호화 저장되고
  AirCall AI 서버(존재하지 않음)로 전송되지 않습니다. 연결하지 않은 외부 서비스에는 접근하지 않습니다.

## 구조

```text
app/src/main/java/com/woojik/aircallai/
  ai/            provider, local(LiteRT-LM·다운로드·검증), cloud
  audio/         Android STT/TTS
  auth/          PRD-09 OAuth 상태 모델·GitHub 기기 인증·Google PKCE·자격증명 저장소·연결 저장소
  conversation/  ConversationEngine, VoiceSession, VoiceResponseSanitizer
  core/          로깅·Keystore·자격증명 저장
  session/       세션 상태·제어
  service/       ConversationService
  call/, overlay/ 통화 상태·오버레이
  tools/         Tool 실행·승인 계층, GitHub/Notes/Calendar/Gmail 어댑터,
                 ToolCallParser(TOOL 지시어 파싱), ToolBridgedAIProvider(AI-Tool 연결),
                 ToolExecutionLogger(실행 메타데이터)
  settings/, privacy/, ui/
```

## 빌드

JDK **21**, Gradle **8.13**, Android SDK Platform 34 및 Build Tools 35.0.0이 필요합니다.
AGP 8.13.2·Kotlin/Compose compiler 2.2.21을 사용합니다. Kotlin 2.2와 JVM 테스트용 JDK 21은 LiteRT-LM 의존성에 필요합니다.
저장소에는 Gradle wrapper가 없습니다. `ANDROID_HOME`을 SDK 경로로 설정합니다.

```bash
gradle testDebugUnitTest
gradle assembleDebug
gradle assembleRelease
```

Release는 R8 축소·난독화가 적용된 **서명되지 않은 APK**입니다. 배포용 서명은 별도로 필요합니다.
CI는 단위 테스트와 Debug APK 빌드를 실행합니다.
API Key/토큰은 코드·빌드 설정에 넣지 않고 CredentialManager로 저장합니다.
OAuth Client ID는 공개값이므로 예외적으로 빌드 속성으로 제공합니다(위 "OAuth 앱 등록" 참조).
