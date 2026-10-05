# AirCall AI

Android에서 로컬 모델 또는 클라우드 API로 텍스트·음성 대화를 제공하는 앱입니다.
Google·GitHub에 로그인하지 않아도 대화와 기기 메모를 사용할 수 있습니다.

[원스토어 우선 출시](docs/onestore/README.md) · [설치·업데이트·서명](docs/RELEASING.md) · [Google Play 제출 준비](docs/play/README.md) ·
[변경 기록](CHANGELOG.md) · [보안 안내](SECURITY.md) · [개발 명세](docs/prd/README.md)

0.4.1은 원스토어용 단일 서명 APK·제출 문서 생성 경로와 앱 정보·사용 안내를 추가합니다.
Actions의 **ONE store Release**는 개인 서명키·실제 공개 정책·신고 수신기·발급 PID를 검사하고
제출 파일과 GitHub 초안을 만듭니다. 계정 등록, 실제 스크린샷과 기기 테스트, 원스토어 심사는 별도로 완료해야 합니다.

## 현재 기능

- 채팅방 생성·이름 변경·삭제, 대화 이력의 암호화된 기기 저장
- 로컬 모델 다운로드·파일 검증·적용, GPU 사용 실패 시 CPU 전환
- 사용자가 설정한 HTTPS 주소의 OpenAI 호환 클라우드 API 연결
- Android 음성 인식·음성 출력, 통화 알림과 선택적 오버레이
- GitHub 저장소·파일 조회, 이슈·PR 생성
- Google 로그인으로 Gmail 발송 및 기본 캘린더 일정 조회·등록
- 로그인 없이 기기에 메모 저장·검색
- 작업 결과 알림, 응답 신고, 개인정보 안내와 전체 기기 데이터 삭제
- 앱 정보·첫 AI 설정 안내, 원스토어 상품 페이지를 통한 업데이트 확인

앱 버전은 [version.properties](version.properties)를 기준으로 합니다.
개발 명세는 목표 사양이며 구현 완료·실기 검증·출시 승인 여부를 뜻하지 않습니다.

## 설치와 업데이트

GitHub Actions의 **aircall-debug-apk**에서 ZIP을 풀고 **aircall-dev.apk**를 설치하세요.
**aircall-upgrade-test-apk**는 업데이트 검사용이며 일반 설치 파일이 아닙니다.
개발용 앱과 정식 앱은 패키지와 서명이 다릅니다. 두 앱 사이의 데이터 이전은 지원하지 않습니다.
개발용 서명키는 안정적인 개발 APK 업데이트를 위해 공개되어 있으며 정식 배포에 사용할 수 없습니다.

정식 배포용 서명, 릴리스 초안과 Play AAB 준비는 [릴리스 안내](docs/RELEASING.md)를 참조하세요.

스마트폰에서 만든 개인 키의 `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`를 **Actions Secrets**에 등록하면
**Android Signed Build** → **Run workflow**에서 서명된 APK/AAB를 만들 수 있습니다.
성공한 실행의 **aircall-signed-build**에서 APK를 받으세요. 스토어 제출 준비는 별도로 완료해야 합니다.

## AI 설정

**로컬:** 설정 → AI 및 모델 → 로컬 모델 관리에서 다운로드 후 적용하세요.
모델 로드가 성공해야 선택이 저장됩니다. 이전 웹용 `.task` 파일은 사용할 수 없습니다.
모델 파일·크기·해시·최소 메모리는 [모델 카탈로그](app/src/main/java/com/woojik/aircallai/ai/local/LocalModels.kt)에 있습니다.
카탈로그 요구사항을 충족해도 모든 기기에서 실행 성공이나 속도를 보장하지는 않습니다.
다운로드는 백그라운드 서비스에서 진행되지만 프로세스 종료 후 이어받기는 지원하지 않습니다.

**클라우드:** 설정 → AI 및 모델에서 제공자의 API 키, HTTPS API 주소, 모델명을 저장하세요.
키는 기기에 암호화 저장되며 선택한 API 서버로 인증을 위해 전송됩니다.
API 주소에는 사용자명·비밀번호·URL 프래그먼트를 넣을 수 없습니다. API의 리디렉션은 따르지 않습니다.

로컬 텍스트 추론은 모델 다운로드 후 네트워크 없이 실행됩니다.
음성은 Android 서비스를 사용하므로 서비스·언어 설정에 따라 인터넷 연결과 외부 전송이 필요할 수 있습니다.

## 계정 연결과 작업 승인

설정 → 도구 및 계정에서 **GitHub로 로그인** 또는 **Google로 로그인**을 선택하세요.
GitHub는 기기 인증을, Google은 Android AuthorizationClient를 사용합니다.
Google 연결은 Gmail 발송과 캘린더 일정 권한을 요청합니다. 만료 시 다시 연결해야 할 수 있습니다.

조회 작업은 기본 허용합니다. 메일 발송, 메모 저장, 일정 등록, 이슈·PR 생성 등 변경 작업은
**매번 표시된 내용을 확인하고 승인해야** 실행합니다. 승인은 다음 요청에 재사용하지 않습니다.
이전 버전에 저장된 지속 승인은 자동 실행에 사용하지 않으며 설정 → 작업 승인에서 삭제할 수 있습니다.
작업 결과 알림에는 도구 종류와 실행 상태만 표시합니다.

외부 도구 결과는 AI 답변 생성에 사용됩니다. 클라우드 모드에서는 조회한 파일·일정·메모 등
결과에 포함된 정보도 선택한 AI 제공자에게 전달될 수 있습니다.

## 개발자 OAuth 설정

OAuth Client ID는 공개 식별자이며 비밀키가 아닙니다. 클라이언트 시크릿은 앱에 넣지 않습니다.

- GitHub OAuth 앱에서 기기 인증 흐름을 활성화하고 `GITHUB_OAUTH_CLIENT_ID`를 설정합니다.
- Google Cloud에서 Gmail·Calendar API와 Android OAuth 클라이언트를 준비합니다.
  개발용 패키지는 `com.woojik.aircallai`, 정식 패키지는 `com.woojik.aircallai.release`입니다.
  각 패키지와 실제 서명 인증서에 맞게 등록합니다. Google Play 앱 서명은 업로드 키와 다를 수 있습니다.
- 일반 빌드는 `GOOGLE_OAUTH_CLIENT_ID`, 정식 릴리스 워크플로는
  `GOOGLE_OAUTH_CLIENT_ID_RELEASE` 저장소 변수를 사용합니다.

현재 빌드 속성과 공개 Client ID 기본값은 [Gradle 설정](app/build.gradle.kts)을 참조하세요.
정식 배포용 계정·인증서·공개 연락처·정책 URL·신고 접수 서비스 설정은 별도로 필요합니다.

## 개발과 검사

JDK 21, Gradle 8.13, Android SDK Platform 36, Build Tools 35.0.0을 사용합니다.
저장소에는 Gradle wrapper가 없습니다. AGP·Kotlin·Compose·LiteRT-LM 의존성은 Gradle 파일에 고정되어 있습니다.

```bash
python -m unittest discover -s scripts -p 'test_*.py' -v
node --test support/report-receiver/receiver.test.cjs
gradle --no-daemon testDebugUnitTest testReleaseUnitTest lintRelease
gradle --no-daemon assembleDebug
```

CI는 단위 테스트·릴리스 lint·APK/AAB 검증과 이전 개발 APK 위에 설치하는 업데이트 검사를 실행합니다.
실제 기기의 로그인, 모델 성능·메모리, 음성, 알림·오버레이, 화면 꺼짐·백그라운드 동작은 별도 확인이 필요합니다.

## 구조

| 경로 | 역할 |
| --- | --- |
| `app/` | Android 앱과 JVM·기기 테스트 |
| `docs/prd/` | 단계별 개발 명세와 과거 설계 |
| `docs/play/` | Google Play 제출 문서·정책 템플릿 |
| `scripts/` | 배포 설정·서명·APK/AAB·릴리스 이력 검사 |
| `support/report-receiver/` | 선택적 신고 접수 서비스 |
| `.github/workflows/` | 개발 CI, 정식 릴리스 초안, Play 문서 생성 |

과거 PRD와 변경 기록은 당시 설계를 보존합니다. 현재 동작은 코드와 이 README를 기준으로 확인하세요.
