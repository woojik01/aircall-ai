# 설치 및 릴리스

## GitHub 공개 시험판 다운로드

현재 공개 버전은 **[0.4.1 / 30007](https://github.com/woojik01/aircall-ai/releases/tag/v0.4.1-build.30007)**입니다.
일반 사용자는 [개인 키로 서명된 APK](https://github.com/woojik01/aircall-ai/releases/download/v0.4.1-build.30007/aircall-0.4.1-30007.apk)를 받으세요.
AAB와 개발용 APK는 일반 사용자의 직접 설치 경로가 아닙니다.

2026-10-09 익명 공개 다운로드를 확인했습니다. 파일 크기는 50,274,501바이트이며
SHA-256은 릴리스 체크섬 파일과 GitHub 자산 지문 모두에 일치합니다.

```text
4e4acca2036cb998abe78979c44f4eb29d02748335886f7e466ead8c5908108c  aircall-0.4.1-30007.apk
```

이 검사는 다운로드 파일의 일치 여부와 APK ZIP의 manifest/dex 존재를 확인한 것입니다.
휴대폰 설치·음성·모델·로그인의 실기 검증이나 Google 개발자 인증 완료를 의미하지 않습니다.

GitHub APK는 개인 출시 키로 서명하며, 원스토어가 재서명한 APK와는 같은 패키지여도 업데이트 호환이 안 될 수 있습니다.
설치 충돌을 해결하려고 기존 앱을 바로 삭제하면 기기 데이터가 사라질 수 있습니다.
직접 배포 업데이트는 같은 키와 더 높은 versionCode를 사용하세요. 다음 빌드는 실행 시 전체 이력을 확인하고 **30007보다 큰 값**을 사용합니다.
현재 앱 내 업데이트 안내는 원스토어를 가리킬 수 있으므로 사용자는 GitHub Releases에서도 새 APK를 확인해야 합니다.

공개 시험판은 다운로드·배포 무료입니다. 유료 클라우드 API 사용은 별도이므로 비용 없이 사용하려면 기기의 로컬 모델을 선택합니다.
현재 Google 기능은 Gmail 발송만 지원하며 캘린더 도구와 권한 요청은 비활성화되어 있습니다.

## 스토어 제출과 개발자 빌드

배포 준비 상태는 [현재 점검 기록](DEPLOYMENT_STATUS.md)을 확인하세요. 원스토어 제출은 [원스토어 제출 안내](onestore/README.md)를 확인하세요. Google Play 제출은 [Play 제출 준비](play/README.md)를 확인하세요. 정식 릴리스는 공개 개인정보처리방침·운영자 연락처가 실제 준비되어야 진행됩니다.

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

R8 적용 정식 코드와 AAB의 서명 경로도 CI의 임시 테스트 키로 검증합니다. 그 APK/AAB와 테스트 키는 업로드하지 않습니다. 개인 키 빌드는 Android Signed Build, 스토어 제출 파일은 ONE store Release 또는 Android Release에서 만듭니다. CI 성공은 모든 기기에서의 실행 성공이나 스토어 심사 통과를 의미하지 않습니다.

## 스마트폰에서 개인 서명 APK/AAB 만들기

PC 없이 Termux에서 만든 키를 사용할 수 있습니다. 서명키는 스마트폰에서 생성하고 Base64 변환도 기기 안에서 실행합니다. 온라인 변환 사이트나 채팅에 키·비밀번호·Base64를 보내지 않습니다.

1. Termux에서 `pkg update` 후 `pkg install openjdk-17`을 실행합니다. 설치 가능한 JDK 패키지는 사용하는 Termux 저장소에 따라 확인하세요. CI의 JDK 버전과 키 생성용 JDK 버전은 같을 필요가 없습니다.
2. 아래 명령으로 키를 만듭니다. `-storetype JKS`를 명시하므로 확장자뿐 아니라 실제 형식도 JKS입니다. 이미 배포한 앱의 키가 있다면 새로 만들지 말고 그 키를 사용합니다.

```bash
keytool -genkeypair -v -storetype JKS -keystore release.jks \
  -alias aircall-ai -keyalg RSA -keysize 2048 -validity 10000
```

3. 비밀번호와 인증서 정보를 입력합니다. 키 비밀번호 질문에서 Enter를 눌렀다면 keystore 비밀번호와 같은 값입니다.
4. `base64 -w 0 release.jks > release.jks.base64.txt`로 변환합니다. `-w`가 지원되지 않으면 `base64 release.jks | tr -d '\n' > release.jks.base64.txt`를 사용합니다.
5. `cat release.jks.base64.txt` 출력 전체를 GitHub Secret에 붙여 넣습니다. 줄바꿈이 포함된 Base64도 워크플로우가 처리합니다. `release.jks` 원본과 비밀번호는 별도로 안전하게 백업합니다. `.base64.txt`도 원본 키와 같은 수준으로 보호하고 저장소에 올리지 않습니다.

[저장소 Secrets 설정](https://github.com/woojik01/aircall-ai/settings/secrets/actions) → **New repository secret**에서 아래 4개를 등록합니다. Variables가 아닙니다.

| Secret 이름 | 값 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `release.jks`의 Base64 전체 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 비밀번호 |
| `ANDROID_KEY_ALIAS` | 위 명령 사용 시 `aircall-ai` |
| `ANDROID_KEY_PASSWORD` | 키 비밀번호; Enter로 동일하게 설정했다면 keystore 비밀번호 |

6. [Android Signed Build](https://github.com/woojik01/aircall-ai/actions/workflows/android-signed-build.yml) → **Run workflow** → **main** → 이전 모든 개인 키 빌드·정식 릴리스·초안보다 큰 `version_code`를 입력합니다. 첫 빌드는 `30001` 이상을 권장합니다. 다음 빌드는 이전보다 큰 값을 사용합니다.
7. 성공한 실행의 **aircall-signed-build**를 다운로드합니다. ZIP을 풀고 `aircall-버전-versionCode.apk`를 설치합니다. AAB는 직접 설치 파일이 아닙니다. 패키지는 `com.woojik.aircallai.release`입니다.

이 워크플로우는 4개 Secrets만으로 개인 키 테스트 APK/AAB를 만들고 비공개 릴리스 초안에 버전·서명 이력을 남깁니다. 스토어 심사·공개 정책 URL·정식 Google OAuth 등록은 별도이며, 원스토어 제출용 문서는 ONE store Release를 사용합니다. **Android CI**에서 받는 `aircall-dev.apk`는 계속 개발용 키를 사용합니다.

키 비밀번호·alias·Base64가 잘못되면 빌드 전에 중단합니다. JKS와 PKCS12 형식 모두 지원하고 공개 개발 키는 거부합니다. 인증서 SHA-1·SHA-256은 실행 **Summary**에 표시하며 SHA-256은 자동으로 APK/AAB 검증에 사용합니다. 선택적으로 `AIRCALL_RELEASE_CERT_SHA256` Variable을 등록하면 해당 지문과 일치하는 키만 허용합니다. 최초 성공 이후에는 릴리스 초안/게시 이력과 서명키·versionCode를 대조합니다. 같은 버전의 초안은 덮어쓰지 않습니다.

초안은 다음 정식 릴리스가 참조하는 버전·서명 기록입니다. 삭제하면 이력 검사가 해당 기록을 확인할 수 없으므로 키 원본과 함께 유지하세요. 다음 ONE store Release/Android Release에는 테스트 빌드보다 큰 `version_code`를 넣습니다.

정식 Google 로그인을 테스트하려면 `GOOGLE_OAUTH_CLIENT_ID_RELEASE` Variable을 준비하고 Summary의 패키지·SHA-1로 Android OAuth 클라이언트를 등록합니다. 이 값은 개인 서명 테스트 빌드에서는 선택 사항이고 스토어 제출 워크플로우에서는 필수입니다.

## 개인 키와 Google 등록 (개발자 최초 1회)

위 Termux 명령 또는 Android Studio의 Generate Signed Bundle/APK로 개인 keystore를 만듭니다. 파일과 암호를 별도로 안전하게 보관하고 소스에 커밋하지 않습니다. 기존 키를 잃어버리거나 다른 키로 바꾸면 APK 직접 배포 사용자가 업데이트할 수 없습니다.

GitHub 저장소 Settings → Secrets and variables → Actions:

| 구분 | 이름 | 값 |
| --- | --- | --- |
| Secret | `ANDROID_KEYSTORE_BASE64` | 개인 keystore 파일의 Base64 |
| Secret | `ANDROID_KEYSTORE_PASSWORD` | keystore 암호 |
| Secret | `ANDROID_KEY_ALIAS` | 개인 키 alias |
| Secret | `ANDROID_KEY_PASSWORD` | 개인 키 암호 |
| Variable (선택) | `AIRCALL_RELEASE_CERT_SHA256` | 허용할 개인 서명 인증서 SHA-256 지문; 생략 시 키에서 자동 계산 |
| Variable | `GOOGLE_OAUTH_CLIENT_ID_RELEASE` | 정식 패키지/서명에 등록한 Android OAuth Client ID |

Google Cloud에 패키지 `com.woojik.aircallai.release`와 개인 인증서 SHA-1로 **새 Android OAuth 클라이언트**를 등록합니다. 기존 개발용 등록은 유지합니다. Play App Signing을 사용하는 경우 사용자가 설치하는 앱의 **앱 서명 인증서**를 Google에 등록해야 합니다. 업로드 키와 앱 서명키는 서로 다를 수 있습니다.

기존 `AIRCALL_KEYSTORE_BASE64`, `AIRCALL_KEYSTORE_PASSWORD`, `AIRCALL_KEY_ALIAS`, `AIRCALL_KEY_PASSWORD` Secrets도 계속 지원합니다. `ANDROID_*` 4개가 모두 없을 때만 기존 4개를 사용합니다. 두 이름을 섞어서 일부씩 등록하면 키가 뒤섞이지 않도록 중단합니다. 모든 개인 키 워크플로우가 같은 규칙을 적용합니다.

개인 키 정보가 부족하거나 fingerprint가 개발용 키와 같으면 릴리스는 중단합니다. 자동 임시 키 생성이나 개발용 서명으로 대체하지 않습니다.

## 릴리스 초안 만들기

1. `version.properties`의 `versionName`을 원하는 버전으로 업데이트하고 main CI를 통과시킵니다.
2. Actions → **Android Release** → Run workflow → **main**을 선택합니다.
3. `version_code`에 모든 이전 정식 릴리스와 초안보다 큰 정수를 입력합니다. 첫 릴리스는 30000 이상입니다.
4. 테스트·APK/AAB 빌드·서명·패키징 검증 후 `aircall-signed-release` 아티팩트와 **비공개 초안 릴리스**가 생성됩니다.
5. APK, AAB, `SHA256SUMS.txt`, `release-metadata.json`, 변경 기록을 확인하고 실기 테스트 후 초안을 게시합니다.

초안 생성은 자동 공개 또는 Play Store 업로드가 아닙니다. 동일 버전 재실행은 기존 초안을 덮어쓰지 않습니다. 서명·패키지·versionCode의 변경은 이전 메타데이터와 대조하며 업데이트 불가능한 산출물은 배포 전에 차단합니다.

APK 직접 배포와 Play AAB 배포의 인증서가 다르면 같은 패키지여도 서로 업데이트할 수 없습니다. 여러 배포 경로를 사용할 때 동일 앱 서명 정책을 먼저 정해야 합니다.

## 실기 확인

- 기존 개발용 앱을 삭제하지 않고 최신 APK로 업데이트해 대화·설정이 유지되는지 확인
- 정식 APK 새 설치 → 같은 키의 더 높은 versionCode APK 업데이트
- 빈 방 생성/이름 변경/방 이동/앱 재시작 시 빈 방 정리 및 기존 대화 보존
- 메모 저장 승인 → 한 번만 저장 → 실제 결과 답변과 알림 → 알림의 방 이동
- 작업 거부·실패·알림 권한 거부 시 거짓 완료나 crash 없음
- Gmail 발송·GitHub 생성 작업의 실제 서비스 결과 확인; 캘린더 도구는 비활성화 상태 확인
- 화면 회전·키보드·백그라운드/통화·다운로드·Android 16 권한과 레이아웃 확인
- ARM64 실제 기기의 로컬 모델 CPU/GPU 추론 및 16 KB 기기에서의 실행 확인

텍스트 작업은 영구 예약 큐가 아닙니다. 앱 프로세스 강제 종료 후 외부 변경 작업을 자동 재실행하지 않습니다. 중단된 메일·GitHub 변경은 해당 서비스에서 결과를 확인한 후 다시 요청합니다.

## 공식 자료 (2026-10-05 확인)

- [Android 앱 서명](https://developer.android.com/studio/publish/app-signing)
- [GitHub Actions Secrets 및 Base64 바이너리 저장](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)
- [Android 앱 버전](https://developer.android.com/studio/publish/versioning)
- [16 KB 페이지 크기](https://developer.android.com/guide/practices/page-sizes)
- [알림 런타임 권한](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- [Google Play 대상 API 요구사항](https://support.google.com/googleplay/android-developer/answer/11926878)
