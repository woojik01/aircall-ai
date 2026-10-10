# AirCall AI

Android에서 로컬 모델 또는 클라우드 API로 텍스트·음성 대화를 제공하는 앱입니다.
Google·GitHub에 로그인하지 않아도 대화와 기기 메모를 사용할 수 있습니다.

## 무료 APK 다운로드

**[AirCall AI 0.4.1 (30007) 공개 시험판](https://github.com/woojik01/aircall-ai/releases/tag/v0.4.1-build.30007)** ·
**[APK 바로 다운로드](https://github.com/woojik01/aircall-ai/releases/download/v0.4.1-build.30007/aircall-0.4.1-30007.apk)**

Android 8.0 이상에서 사용할 수 있습니다. APK 다운로드와 앱 배포는 무료이며 스토어 계정이 필요하지 않습니다.
로컬 모델은 다운로드 후 기기에서 실행합니다. 사용자가 연결하는 클라우드 API에는 제공자의 별도 요금이 있을 수 있습니다.
실제 기기의 음성·모델·로그인 동작은 별도 확인이 필요한 **공개 시험판**입니다.

1. `aircall-0.4.1-30007.apk`를 다운로드합니다. AAB와 소스 ZIP은 직접 설치하는 파일이 아닙니다.
2. Android 설치 안내에 따라 사용한 브라우저 또는 파일 관리자의 앱 설치를 허용하고 APK를 엽니다.
3. 설정 → AI 및 모델에서 기기에 맞는 로컬 모델을 내려받아 적용합니다.
4. 업데이트는 [Releases](https://github.com/woojik01/aircall-ai/releases)에서 같은 서명의 더 높은 versionCode APK를 받습니다.
   현재 앱 내 업데이트 안내는 원스토어를 가리킬 수 있습니다.

**서명 주의:** GitHub APK의 개인 서명과 원스토어가 적용한 출시 서명이 다르므로 서로 덮어쓰는 업데이트가 안 될 수 있습니다.
설치 충돌이 발생하면 기존 앱을 바로 삭제하지 마세요. 삭제하면 대화·메모·설정이 사라질 수 있습니다.
개발용 앱(`com.woojik.aircallai`)과 공개 시험판(`com.woojik.aircallai.release`) 사이의 자동 데이터 이전도 지원하지 않습니다.

2026-10-09 공개 APK를 실제 다운로드하여 크기와 SHA-256을 확인했습니다.
50,274,501바이트이며 아래 값은 릴리스의 `SHA256SUMS.txt` 및 GitHub 자산 지문과 일치합니다.

```text
4e4acca2036cb998abe78979c44f4eb29d02748335886f7e466ead8c5908108c  aircall-0.4.1-30007.apk
```

GitHub 직접 배포는 Google 개발자 인증 완료를 의미하지 않습니다. 향후 정책이 적용되는 지역·시점에는
미인증 앱 설치와 업데이트에 사용자의 고급 설치 절차가 필요할 수 있습니다.
[Google 공식 안내](https://developer.android.com/developer-verification/guides/faq)를 확인하세요.

[원스토어 제출](docs/onestore/README.md) · [설치·업데이트·서명](docs/RELEASING.md) · [Google Play 제출 준비](docs/play/README.md) ·
[변경 기록](CHANGELOG.md) · [보안 안내](SECURITY.md) · [개발 명세](docs/prd/README.md)

0.4.1은 원스토어용 단일 서명 APK·제출 문서 생성 경로와 앱 정보·사용 안내를 추가합니다.
Actions의 **ONE store Release**는 개인 서명키·실제 공개 정책·발급 PID를 검사하고
제출 파일과 GitHub 초안을 만듭니다. 계정 등록, 실제 스크린샷과 기기 테스트, 원스토어 심사는 별도로 완료해야 합니다.

## 현재 기능

아래는 최신 소스 기준입니다. [사용 경험 개선](docs/EXPERIENCE_IMPROVEMENTS.md)은 개발 브랜치의 변경이며,
위의 기존 `0.4.1 (30007)` 공개 APK에는 포함되어 있지 않습니다.

- 채팅방 생성·이름 변경·삭제, 대화 이력의 암호화된 기기 저장
- 로컬 모델 다운로드·파일 검증·적용, GPU 사용 실패 시 CPU 전환
- 사용자가 설정한 HTTPS 주소의 OpenAI 호환 클라우드 API 연결
- Android 음성 인식·음성 출력, 통화 알림과 선택적 오버레이
- GitHub 저장소·파일 조회, 이슈·PR 생성
- Google 권한 연결로 Gmail 발송; 캘린더 기능과 캘린더 권한 요청은 비활성화
- 로그인 없이 기기에 메모 저장·검색
- 작업 결과 알림, 서비스 문의 이메일, 개인정보 안내와 전체 기기 데이터 삭제
- 텍스트·음성 답변 방식 분리, 기본 Markdown 표시, 클라우드 실시간 응답과 문장 단위 음성 재생
- 첫 AI 설정 안내·저장된 연결 테스트, 기기 메모리·저장 공간에 따른 모델 추천과 다운로드 이어받기
- 사용자가 관리하는 암호화된 기억할 정보, 오래된 대화의 제한된 발췌
- 지원하는 클라우드 API의 구조화된 도구 호출, 편집 가능한 작업 승인과 실행 결과 카드
- 비밀번호 기반 암호화 백업·JSON 내보내기·자료 가져오기, 안전한 재시도와 설치 경로별 업데이트 안내

앱 버전은 [version.properties](version.properties)를 기준으로 합니다.
개발 명세는 목표 사양이며 구현 완료·실기 검증·출시 승인 여부를 뜻하지 않습니다.

## 개발용 APK와 서명 빌드

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
다운로드는 백그라운드 서비스에서 진행하며 중단된 부분 파일을 다음 실행에서 이어받습니다.
서버가 이어받기를 지원하지 않으면 처음부터 내려받고, 최종 크기·SHA-256 검증에 성공한 파일만 적용할 수 있습니다.

**클라우드:** 설정 → AI 및 모델에서 제공자의 API 키, HTTPS API 주소, 모델명을 저장하세요.
키는 기기에 암호화 저장되며 선택한 API 서버로 인증을 위해 전송됩니다.
API 주소에는 사용자명·비밀번호·URL 프래그먼트를 넣을 수 없습니다. API의 리디렉션은 따르지 않습니다.
저장된 연결 테스트는 대화 기록 없이 짧은 테스트 문장만 전송하며 별도 확인을 받습니다.
실시간 응답은 지원하지 않는 API에서 끌 수 있고, 구조화된 도구 호출은 지원하는 모델에서 선택적으로 켤 수 있습니다.

로컬 텍스트 추론은 모델 다운로드 후 네트워크 없이 실행됩니다.
음성은 Android 서비스를 사용하므로 서비스·언어 설정에 따라 인터넷 연결과 외부 전송이 필요할 수 있습니다.

## 계정 연결과 작업 승인

설정 → 도구 및 계정에서 **GitHub로 로그인** 또는 **Google로 로그인**을 선택하세요.
GitHub는 기기 인증을, Google은 Android AuthorizationClient를 사용합니다.
Google 연결은 Gmail 발송 권한만 요청합니다. 캘린더 권한은 요청하지 않으며 일정 도구도 실행하지 않습니다.
만료 시 다시 연결해야 할 수 있습니다.

조회 작업은 기본 허용합니다. 메일 발송, 메모 저장, 이슈·PR 생성 등 변경 작업은
**매번 표시된 내용을 확인하고 승인해야** 실행합니다. 승인은 다음 요청에 재사용하지 않습니다.
이전 버전에 저장된 지속 승인은 자동 실행에 사용하지 않으며 설정 → 작업 승인에서 삭제할 수 있습니다.
작업 결과 알림에는 도구 종류와 실행 상태만 표시합니다.

외부 도구 결과는 AI 답변 생성에 사용됩니다. 클라우드 모드에서는 조회한 파일·메모 등
결과에 포함된 정보도 선택한 AI 제공자에게 전달될 수 있습니다.

## 기억과 백업

설정 → 기억할 정보에서 최대 150자의 이름·선호 등을 직접 저장·수정·삭제합니다.
이 정보는 기기에 암호화 저장되며 클라우드 모드에서는 선택한 AI 서버로 전송됩니다.

설정 → 백업 및 가져오기에서 대화·메모·기억할 정보·일반 설정을 파일로 저장합니다.
암호화 백업의 비밀번호는 8자 이상이며, 잊으면 복원할 수 없습니다. JSON은 암호화되지 않습니다.
API 키·OAuth 토큰·승인·모델 파일은 내보내지 않습니다.
가져오기는 기존 자료를 유지하고 동일한 자료를 건너뜁니다. 일반 설정 복원은 선택 사항이며,
기억할 정보는 현재 저장된 내용이 없을 때만 복원합니다.

## 개발자 OAuth 설정

OAuth Client ID는 공개 식별자이며 비밀키가 아닙니다. 클라이언트 시크릿은 앱에 넣지 않습니다.

- GitHub OAuth 앱에서 기기 인증 흐름을 활성화하고 `GITHUB_OAUTH_CLIENT_ID`를 설정합니다.
- Google Cloud에서 Gmail API와 Android OAuth 클라이언트를 준비합니다. 캘린더 API는 현재 기능에 필요하지 않습니다.
  개발용 패키지는 `com.woojik.aircallai`, 정식 패키지는 `com.woojik.aircallai.release`입니다.
  각 패키지와 실제 서명 인증서에 맞게 등록합니다. Google Play 앱 서명은 업로드 키와 다를 수 있습니다.
- 일반 빌드는 `GOOGLE_OAUTH_CLIENT_ID`, 정식 릴리스 워크플로는
  `GOOGLE_OAUTH_CLIENT_ID_RELEASE` 저장소 변수를 사용합니다.

현재 빌드 속성과 공개 Client ID 기본값은 [Gradle 설정](app/build.gradle.kts)을 참조하세요.
정식 배포용 계정·인증서·공개 연락처·정책 URL은 별도로 필요합니다.

## 개발과 검사

JDK 21, Gradle 8.13, Android SDK Platform 36, Build Tools 35.0.0을 사용합니다.
저장소에는 Gradle wrapper가 없습니다. AGP·Kotlin·Compose·LiteRT-LM 의존성은 Gradle 파일에 고정되어 있습니다.

```bash
python -m unittest discover -s scripts -p 'test_*.py' -v
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
| `.github/workflows/` | 개발 CI, 정식 릴리스 초안, Play 문서 생성 |

과거 PRD와 변경 기록은 당시 설계를 보존합니다. 현재 동작은 코드와 이 README를 기준으로 확인하세요.
