# 설치 및 릴리스

첫 출시는 [원스토어 제출 안내](onestore/README.md)를 확인하세요. Google Play 제출은 [Play 제출 준비](play/README.md)를 확인하세요. 정식 릴리스는 공개 개인정보처리방침·실제로 운영하는 신고 방법·운영자 연락처가 준비되어야 진행됩니다. 첫 버전은 이메일 신고와 30일 수동 보관·삭제를 사용합니다.

## 패키지와 서명

| 용도 | applicationId | 서명 | 데이터 |
| --- | --- | --- | --- |
| 기존 개발용 APK | `com.woojik.aircallai` | 기존 `app/debug.keystore` | 기존 설치에 업데이트 가능 |
| 정식 배포 APK/AAB | `com.woojik.aircallai.release` | 개발자가 보관하는 개인 키 | 별도 앱 저장 영역 |

개발용 앱을 이미 설치한 사용자의 업데이트와 Google OAuth를 유지하기 위해 개발용 ID와 서명을 변경하지 않습니다. 공개된 개발 키는 정식 배포에 사용하지 않습니다. 정식 패키지는 개발용 앱과 함께 설치할 수 있으며 첫 배포 이후 패키지와 서명키를 유지해야 합니다. 두 패키지 간 대화·계정·모델을 자동 복사하지 않습니다. 정식 앱은 새로 설정해야 합니다.

## 개발용 APK 받기

GitHub → Actions → 성공한 최신 **Android CI** → **aircall-debug-apk**를 받습니다. ZIP을 풀고 `aircall-dev.apk`를 설치합니다. ZIP이나 AAB 자체는 APK 설치 파일이 아닙니다.

CI는 버전 코드 `30000 + Android CI 실행 번호`를 사용합니다. 이전 설치보다 낮은 버전을 설치하면 Android가 거부할 수 있습니다. 최신 main 빌드를 사용하세요. 고정 개발용 키가 없어지면 CI는 중단하며 새 키를 자동 생성하지 않습니다.

APK 업로드 전 확인:

- ZIP 체크섬 및 manifest/dex 존재, 중복 엔트리 없음
- `aapt`로 패키지·버전·최소 Android 8/API 26·대상 API 36·실행 Activity 검사
- `apksigner`로 실제 서명 및 기존 개발용 인증서 일치 검사
- `zipalign -P 16` 및 ARM64/x86_64 네이티브 ELF의 16 KB LOAD 정렬 검사
- 다운로드 파일의 SHA-256 및 빌드 메타데이터 동봉

R8 적용 정식 코드와 AAB의 서명 경로도 CI의 임시 테스트 키로 검증합니다. 그 APK/AAB와 테스트 키는 업로드하지 않습니다. 실제 배포는 별도 Android Release에서 개인 키로만 수행합니다. CI 성공은 모든 기기에서의 실행 성공이나 Google Play 심사 통과를 의미하지 않습니다.

## 서명 없는 정식 코드 검증

**Release Validation**은 PR·브랜치 push·수동 실행으로 동작하며 개인 키나 Actions secrets를 사용하지 않습니다.
`version.properties`의 `0.4.1` / `30001`로 단위 테스트·release lint·R8 빌드를 수행하고,
unsigned APK의 정식 패키지·SDK·ARM64 런타임·16 KB 정렬을 검사합니다. 개발용 OAuth Client ID도 주입하지 않습니다.

`aircall-unsigned-release-validation`에는 `aircall-release-UNSIGNED-VALIDATION.apk`와
`signatureVerified=false`, `artifactKind=unsigned-validation` 메타데이터가 포함됩니다.
이 파일은 설치나 스토어 제출에 사용할 수 없습니다. 제출 문서·릴리스 이력 검사는 unsigned 결과를 거부합니다.
같은 커밋의 성공한 검증이 있어야 수동 정식 서명 워크플로가 진행됩니다.

## 개인 키와 Google 등록 (개발자 최초 1회)

Android Studio의 Generate Signed Bundle/APK로 개인 keystore를 만듭니다. 파일과 암호를 별도로 안전하게 보관하고 소스에 커밋하지 않습니다. 기존 키를 잃어버리거나 다른 키로 바꾸면 APK 직접 배포 사용자가 업데이트할 수 없습니다.

GitHub 저장소 Settings → Secrets and variables → Actions:

| 구분 | 이름 | 값 |
| --- | --- | --- |
| Secret | `AIRCALL_KEYSTORE_BASE64` | 개인 keystore 파일의 Base64 |
| Secret | `AIRCALL_KEYSTORE_PASSWORD` | keystore 암호 |
| Secret | `AIRCALL_KEY_ALIAS` | 개인 키 alias |
| Secret | `AIRCALL_KEY_PASSWORD` | 개인 키 암호 |
| Variable | `AIRCALL_RELEASE_CERT_SHA256` | 개인 서명 인증서 SHA-256 지문 |
| Variable | `GOOGLE_OAUTH_CLIENT_ID_RELEASE` | 정식 패키지/서명에 등록한 Android OAuth Client ID |

Google Cloud에 패키지 `com.woojik.aircallai.release`와 개인 인증서 SHA-1로 **새 Android OAuth 클라이언트**를 등록합니다. 기존 개발용 등록은 유지합니다. Play App Signing을 사용하는 경우 사용자가 설치하는 앱의 **앱 서명 인증서**를 Google에 등록해야 합니다. 업로드 키와 앱 서명키는 서로 다를 수 있습니다.

개인 키 정보가 부족하거나 fingerprint가 개발용 키와 같으면 릴리스는 중단합니다. 자동 임시 키 생성이나 개발용 서명으로 대체하지 않습니다.

## 릴리스 초안 만들기

1. `version.properties`의 버전과 공개 구성을 별도 브랜치에서 검토하고 **Release Validation**을 통과시킵니다.
   PR의 **Android CI**도 확인합니다. 첫 배포는 `0.4.1` / `30001`입니다.
2. Actions → **ONE store Release**(원스토어 APK) 또는 **Android Release**(APK/AAB) → Run workflow에서 검토한 브랜치를 선택합니다.
   수동 실행만 개인 키에 접근하며 선택한 소스 커밋의 성공한 **Release Validation**을 먼저 확인합니다.
3. `version_code`에 모든 이전 정식 릴리스와 초안보다 큰 정수를 입력합니다. 첫 릴리스는 30000 이상입니다.
4. 테스트·APK/AAB 빌드·서명·패키징 검증 후 `aircall-signed-release` 아티팩트와 **비공개 초안 릴리스**가 생성됩니다.
5. APK, AAB, `SHA256SUMS.txt`, `release-metadata.json`, 변경 기록을 확인하고 실기 테스트 후 초안을 게시합니다.

초안 생성은 자동 공개 또는 Play Store 업로드가 아닙니다. 동일 버전 재실행은 기존 초안을 덮어쓰지 않습니다. 서명·패키지·versionCode의 변경은 이전 메타데이터와 대조하며 업데이트 불가능한 산출물은 배포 전에 차단합니다.
검증 뒤 코드를 바꾸면 새 커밋을 다시 검증해야 합니다. 브랜치에서 서명 초안을 만들기 위해 main에 미검토 코드를 병합할 필요는 없습니다.

APK 직접 배포와 Play AAB 배포의 인증서가 다르면 같은 패키지여도 서로 업데이트할 수 없습니다. 여러 배포 경로를 사용할 때 동일 앱 서명 정책을 먼저 정해야 합니다.

## 실기 확인

- 기존 개발용 앱을 삭제하지 않고 최신 APK로 업데이트해 대화·설정이 유지되는지 확인
- 정식 APK 새 설치 → 같은 키의 더 높은 versionCode APK 업데이트
- 빈 방 생성/이름 변경/방 이동/앱 재시작 시 빈 방 정리 및 기존 대화 보존
- 메모 저장 승인 → 한 번만 저장 → 실제 결과 답변과 알림 → 알림의 방 이동
- 작업 거부·실패·알림 권한 거부 시 거짓 완료나 crash 없음
- Gmail 발송·일정 생성·GitHub 생성 작업의 실제 서비스 결과 확인
- 화면 회전·키보드·백그라운드/통화·다운로드·Android 16 권한과 레이아웃 확인
- ARM64 실제 기기의 로컬 모델 CPU/GPU 추론 및 16 KB 기기에서의 실행 확인

**Android CI**의 자동 업데이트 검사는 기존 개발용 패키지와 고정 개발용 인증서로 실행됩니다.
기존 main APK에 설정·암호화된 API/OAuth 자격증명·대화·메모·모델 파일을 만든 후 `adb install -r`로 바꾸고 앱 시작과 데이터를 확인합니다.
처음 만드는 개인 정식 키·별도 정식 패키지에 대한 업데이트 보존 검사는 같은 키의 더 높은 버전 APK로 별도 실기 확인이 필요합니다.

텍스트 작업은 영구 예약 큐가 아닙니다. 앱 프로세스 강제 종료 후 외부 변경 작업을 자동 재실행하지 않습니다. 중단된 메일·일정·GitHub 변경은 해당 서비스에서 결과를 확인한 후 다시 요청합니다.

## 공식 자료 (2026-10-04 확인)

- [Android 앱 서명](https://developer.android.com/studio/publish/app-signing)
- [Android 앱 버전](https://developer.android.com/studio/publish/versioning)
- [16 KB 페이지 크기](https://developer.android.com/guide/practices/page-sizes)
- [알림 런타임 권한](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- [Google Play 대상 API 요구사항](https://support.google.com/googleplay/android-developer/answer/11926878)
