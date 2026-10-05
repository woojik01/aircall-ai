# 원스토어 우선 출시 설계 및 등록 안내

확인일: 2026-10-05. 이 문서는 구현된 제출 경로와 사람이 완료해야 하는 등록 작업을 구분합니다.
코드와 CI 성공만으로 원스토어 심사 승인·출시 완료가 되지는 않습니다.

## 첫 출시 결정

| 항목 | 결정 |
| --- | --- |
| 국가 / 언어 | 대한민국 / 한국어 |
| 판매 형태 | 무료 앱, 광고·인앱결제·구독 없음 |
| 제출 파일 | 개발자 개인 키로 서명한 단일 APK |
| 상품 PID | `0001009976` — ONEconsole의 등록 패키지는 정식 패키지와 일치해야 함 |
| 공개 운영자 / 문의 | `woojik01` / `woojik1220@gmail.com` |
| 첫 버전 | `0.4.1` / `versionCode=30001` |
| 정식 패키지 | `com.woojik.aircallai.release` |
| 개발용 패키지 | `com.woojik.aircallai` — 원스토어 제출 금지 |
| Android | minSdk 26 / targetSdk 36 |
| AI 이용 | 앱 가입 없이 이용, 로컬 모델 다운로드·적용 또는 사용자 Cloud API 설정 필요 |
| 외부 계정 | GitHub·Google 연결 선택, Google 연결에는 Google Play 서비스 필요 |
| 결제 라이브러리 | 첫 버전에는 IAP·유료 앱 라이선스 SDK를 추가하지 않음 |
| 데이터 | 기기 전용 저장, 자격증명·대화 암호화. 선택한 Cloud·도구·음성 서비스·신고 전송은 안내 |
| 신고 접수 | 이메일 앱에서 확인 후 직접 전송, 별도 HTTPS 신고 서버 없음 |
| 신고 보관 | 접수 후 30일, 운영자가 받은편지함·휴지통·별도 사본에서 수동 삭제 |
| 배포 방식 | GitHub에서 서명 APK·제출 문서·초안 생성 → 실기 확인 → ONEconsole 검증 요청 |

APK를 선택한 이유는 현재 빌드·직접 설치 검증 경로를 그대로 이용하고 개발자가 정식 서명키를 유지하기 위해서입니다.
원스토어는 APK와 AAB를 모두 지원하지만 AAB로 전환한 상품을 APK로 되돌릴 수 없다고 안내합니다.
향후 다른 마켓에서 같은 패키지로 업데이트하려면 **앱 서명 인증서**가 같아야 합니다.
Play의 업로드 키와 앱 서명키를 혼동하지 마세요. 스토어 공통 `versionCode`를 단조 증가시킵니다.

## 준비 순서 — 브라우저와 GitHub 화면

1. [ONEconsole](https://dev.onestore.net/dev/)에서 계정을 만들고 필요한 약관·인증을 완료합니다.
   공식 가입 안내는 만 14세 이상이라고 명시합니다. 가입 가능 연령과 계약·판매자 정보는 별개이므로
   미성년자의 동의·판매자 등록에서 콘솔이 요구하는 항목을 보호자와 확인하세요. 운영자 명의를 임의로 바꾸지 않습니다.
2. 대한민국 대상 Android 상품을 등록하고 **PID(Product ID)**를 확인합니다. Android **AID**와 다른 값입니다.
   `AIRCALL_ONESTORE_PRODUCT_ID`에 콘솔이 발급한 10자리 PID를 입력합니다.
3. 개인 정식 서명키를 Android Studio의 **Generate Signed Bundle/APK**에서 만들거나 기존 정식 키를 사용합니다.
   키 파일·암호를 안전하게 백업하고 공개 Git에 올리지 않습니다. [공통 서명 안내](../RELEASING.md)를 참고합니다.
4. 공개 운영자명, 실제 지원 이메일, 정책 주소, 신고 방식을 정합니다.
   `config/play.properties`는 이름과 달리 **모든 마켓이 공유하는 공개 연락처·개인정보 구성**입니다.
   기존 Google Play 설정을 재입력할 필요는 없습니다.
5. Actions → **Prepare ONE store Documents**를 실행합니다. `aircall-onestore-documents`의
   `privacy-policy.html`을 설정한 공개 HTTPS 주소에 게시합니다. PID는 이 단계에서는 없어도 됩니다.
6. 첫 버전은 `AIRCALL_REPORT_METHOD=email`, 빈 `AIRCALL_REPORT_ENDPOINT`, 보관 기간 30일을 사용합니다.
   신고 화면은 내용을 확인할 이메일 초안을 열며, 사용자가 이메일 앱에서 전송해야 접수됩니다.
   전송 완료나 접수 번호를 앱이 자동 확인하지 않습니다. 발신 이메일은 운영자에게 보입니다.
   운영자는 [개인정보 운영 절차](../PRIVACY_OPERATIONS.md)에 따라 접수 후 최대 30일 이내 신고와 휴지통을 영구 삭제합니다.
   하루 한 번 점검한다면 29일 경과 기록부터 삭제해 보관 기한을 넘기지 않습니다.
   향후 [HTTPS 신고 접수 서비스](../../support/report-receiver/README.md)를 실제 배포할 때만
   `AIRCALL_REPORT_METHOD=https`와 공개 주소를 설정하고 정책·앱·서비스의 보관 기간을 일치시킵니다.
7. Google Cloud에 `com.woojik.aircallai.release`와 정식 인증서 SHA-1로 Android OAuth를 등록합니다.
   Gmail·Calendar API와 동의 화면을 준비하고 일반 사용자용 공개·심사 상태를 확인합니다.
   개발용 인증서 등록은 그대로 둡니다. Google 로그인은 앱 자체 사용의 필수 조건이 아닙니다.
8. 변경을 별도 브랜치와 PR로 검토하고 해당 커밋의 **Release Validation** 및 PR의 **Android CI** 성공을 확인합니다.
   **Release Validation**은 개인 키 없이 `version.properties`의 고정 버전으로 테스트·lint·R8 unsigned APK를 검증합니다.
   `aircall-unsigned-release-validation`은 설치·원스토어 제출용이 아닙니다.
   Actions → **ONE store Release**에서 검토한 브랜치를 직접 선택해 실행합니다.
   서명 워크플로는 그 커밋의 성공한 **Release Validation**을 확인하며 자동 push·PR 실행으로 서명하지 않습니다.
   `version_code`는 모든 이전 정식 릴리스·초안과 마켓 등록 버전보다 큰 값입니다. 첫 권장값은 30001입니다.
9. `aircall-onestore-submission`에서 APK와 제출 문서를 받습니다. 실제 ARM64 기기에서 새 설치와
   같은 키의 더 높은 버전 업데이트를 시험하고 [검증표](REVIEW_AND_TEST.md)를 기록합니다.
10. ONEconsole에 **aircall-onestore-버전-버전코드.apk**를 업로드합니다. 상품 설명, 지원 정보,
    개인정보 주소, 실제 스크린샷(공식 안내: 2~8장), 지원 단말과 검증 참고 정보를 입력합니다.
    콘솔에서 검증을 요청하고 승인 후 직접 배포합니다. 워크플로는 스토어 업로드·공개를 자동 수행하지 않습니다.

## GitHub Actions 설정

저장소 → Settings → Secrets and variables → Actions에서 입력합니다.

| 구분 | 이름 | 설명 |
| --- | --- | --- |
| Secret | `AIRCALL_KEYSTORE_BASE64` | 개인 keystore의 Base64 |
| Secret | `AIRCALL_KEYSTORE_PASSWORD` | keystore 암호 |
| Secret | `AIRCALL_KEY_ALIAS` | 개인 키 alias |
| Secret | `AIRCALL_KEY_PASSWORD` | 개인 키 암호 |
| Variable | `AIRCALL_RELEASE_CERT_SHA256` | 개인 앱 서명 인증서 SHA-256 |
| Variable | `GOOGLE_OAUTH_CLIENT_ID_RELEASE` | 정식 패키지·인증서에 등록한 Android OAuth Client ID |
| Variable 또는 공개 설정 | `AIRCALL_DEVELOPER_NAME` | 실제 앱 운영자·공개 개발자명 |
| Variable 또는 공개 설정 | `AIRCALL_SUPPORT_EMAIL` | 실제 문의·개인정보 연락 이메일 |
| Variable 또는 공개 설정 | `AIRCALL_PRIVACY_POLICY_URL` | 게시 완료한 공개 HTTPS 정책 페이지 |
| Variable 또는 공개 설정 | `AIRCALL_REPORT_METHOD` | 첫 버전 `email`, 실제 수신 서비스 사용 시 `https` |
| Variable 또는 공개 설정 | `AIRCALL_REPORT_ENDPOINT` | 이메일 방식은 빈 값, HTTPS 방식은 실제 공개 `/exec` URL |
| Variable 또는 공개 설정 | `AIRCALL_REPORT_RETENTION_DAYS` | 첫 버전 30일, 실제 운영·정책·앱과 일치 |
| Variable 또는 `config/onestore.properties` | `AIRCALL_ONESTORE_PRODUCT_ID` | 원스토어 상품 PID, 10자리 숫자 |

서명키·암호를 채팅에 보내지 마세요. 공개 정보에도 실제로 공개할 값을 입력합니다.
모든 필수값이 준비되기 전 정식 APK를 만들면 출시 가능한 결과물처럼 오해할 수 있어 릴리스 검사가 중단됩니다.
키·서비스 설정과 별개로 브랜치의 unsigned release 코드 검증과 개발용 APK 기능 테스트를 진행할 수 있습니다.

## 자동 검증 및 산출물

- 공통 개인정보 설정의 빈 값·가짜 주소·HTTP·잘못된 보관 기간을 차단합니다.
- 공개 정책 페이지의 실제 응답과 운영자 이메일을 확인합니다.
- 이메일 방식은 공개 정책의 이메일 신고 설명·보관 기간을 검사합니다. 메일 발송·수신·수동 삭제는 운영자가 확인합니다.
- HTTPS 방식은 신고 수신기에 실제 쓰기·읽기·삭제 테스트를 하고 정리 성공을 확인합니다.
- PID, 개인 서명키, 인증서 fingerprint와 Google 정식 OAuth 설정 존재를 검사합니다.
- 단위 테스트·lint·R8 release APK 빌드를 실행합니다.
- APK의 패키지·버전·서명·최소/대상 SDK·ARM64 런타임·ZIP 및 ELF 16 KB 정렬을 검사합니다.
- 모든 GitHub 정식 릴리스와 초안의 메타데이터에 대조하여 키 변경과 버전 역행을 차단합니다.
  GitHub 밖에서 배포한 APK의 버전·인증서는 운영자가 별도로 비교해야 합니다.
- unsigned 검증 메타데이터에는 `signatureVerified=false`가 기록되며 제출·릴리스 이력 검사에서 거부됩니다.
- PR의 **Android CI**는 기존 개발용 APK를 업데이트해 설정·자격증명·대화·메모·모델 파일 보존을 검사합니다.
  정식 패키지는 별도 앱이므로 개발용 데이터가 자동 이전되지 않으며, 개인 키의 정식 APK 업데이트는 실기로 확인합니다.
- 서명 APK, `release-metadata.json`, `SHA256SUMS.txt`, 실제 정보로 채운 정책 HTML,
  제출 요약, 설명 초안, 데이터·권한표, 실기 검증표를 하나의 아티팩트에 묶습니다.
- GitHub Release는 **초안**으로만 만들고 실행 마지막에 runner의 개인 키 파일을 제거합니다.

`AIRCALL_DISTRIBUTION_CHANNEL=onestore` 빌드는 설정의 앱 정보에 원스토어용 앱으로 표시됩니다.
발급받은 PID로 `https://onesto.re/{PID}` 링크를 만들며 원스토어 앱이 없으면 공식 웹 페이지로 연결됩니다.
백그라운드 자동 다운로드·자동 설치·강제 업데이트는 추가하지 않습니다.

## 공식 출처

- [2026 Target SDK 최소 기준 일정](https://dev.onestore.net/devpoc/support/news/noticeView.omp?noticeId=33671): 2026-09-01부터 33 이상. 앱은 36 사용.
- [상품등록과 관리](https://onestore-dev.gitbook.io/dev/docs/apps): APK/AAB 지원, PID와 AID 구분.
- [가입 안내](https://onestore-dev.gitbook.io/dev/docs/member/sign-up): 만 14세 이상 가입 안내.
- [상품 FAQ](https://onestore-dev.gitbook.io/dev/help/faq/apps): 무료·유료 등록, 스크린샷, 권한, 검증 요청.
- [개발도구 FAQ](https://onestore-dev.gitbook.io/dev/help/faq/tools): 개발용 debug 서명 대신 개인 인증서 사용.
- [APK/AAB·키 관리 FAQ](https://onestore-dev.gitbook.io/dev/help/faq/apps/one-store-android-app-bundle): 마켓 간 동일 서명, AAB 전환 제한.
- [앱 연결 규격](https://onestore-dev.gitbook.io/dev/tools/app-links): PID와 `https://onesto.re/{PID}`.
- [상품 검증 가이드라인](https://onestore-dev.gitbook.io/dev/docs/review/one-store-review-guideline): 콘텐츠 심사 원칙.
- [Android TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech): Android 11 이상 TTS 서비스 queries 선언.
