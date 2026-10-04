# Google Play 제출 준비 — AirCall AI 0.4.0

첫 배포 방향: **무료, 광고 없음**. 현재 개발자 계정·개발자 공개 이름·문의 이메일·개인정보처리방침 URL이 미정입니다. 코드는 제출 준비를 지원하지만 지금 상태를 심사 통과 또는 공개 출시 완료로 표시하지 않습니다.

## 적용된 앱·빌드 구성

| 항목 | 구현 |
| --- | --- |
| 정식 앱 ID | `com.woojik.aircallai.release` — 첫 Play 등록 후 유지 |
| Android | 최소 API 26, 대상·컴파일 API 36 |
| AAB | 개인 업로드 키로 서명, bundletool 구조 검사, 업로드 인증서 확인 |
| 네이티브 런타임 | ARM64 및 16 KB ELF/ZIP 정렬 검사, AAB에서 생성한 APK도 검사 |
| AI 신고 | 채팅·음성의 각 응답, 개인정보의 일반 문의; 로그인 없이 앱 내 HTTPS 전송 |
| 데이터 고지 | 첫 클라우드 전송·음성 사용 동의, AI 서버 주소가 바뀌면 재동의 |
| 데이터 삭제 | 방·키·계정·모델의 기존 개별 삭제, 전체 앱 데이터 삭제 추가 |
| Google 권한 | `gmail.send`, `calendar.events`만 요청; `gmail.modify` 제외 |
| 배포 차단 | 공개 정보 누락, 실제 정책/신고 URL 미준비, 서명/버전/정렬 검사 실패 시 정식 산출물 업로드 중단 |

개발용 앱과 정식 앱은 ID와 저장 영역이 다릅니다. 기존 개발용 데이터를 Play 앱으로 자동 복사하지 않습니다. 직접 설치 APK와 Play 설치 앱은 **앱 서명키**가 같아야 서로 업데이트할 수 있습니다. **업로드 키**로 서명한 APK가 Play에서 배포하는 앱과 같은 서명이라고 가정하지 않습니다.

## 준비 순서 (브라우저 중심)

1. **계정 소유자 결정**: Play Console 가입은 만 18세 이상이며 일회성 등록비는 US$25입니다. 미성년 개발자는 보호자 등 자격을 갖춘 실제 운영자가 본인 명의·결제로 계정을 등록하고 신원·기기를 확인해야 합니다. 실제 소유자가 약관·운영·공개 정보를 관리합니다.
2. **공개 정보 결정**: 개발자 표시 이름, 실제 받을 수 있는 문의 이메일, 개인정보처리방침을 공개할 HTTPS 주소를 정합니다. 이름·주소를 임의로 추정하거나 예시 이메일로 제출하지 않습니다.
3. **신고 접수 배포**: [접수 서비스 안내](../../support/report-receiver/README.md)에 따라 비공개 시트와 Apps Script 웹 앱을 준비합니다. 신고 URL은 `/exec` 배포 주소를 사용합니다.
4. **공개 설정 입력**: GitHub → Settings → Secrets and variables → Actions → Variables에서 아래 표를 입력합니다. 또는 `config/play.properties`를 GitHub 웹 편집기로 수정합니다. 변수의 비어 있지 않은 값이 파일보다 우선합니다.
5. **정책 문서 만들기**: Actions → **Prepare Play Documents** → Run workflow → main. `aircall-play-documents`의 `privacy-policy.html`을 받습니다. 정한 공개 주소에 HTML로 게시합니다. 로그인·지역 제한 없이 열려야 하며 PDF나 공동 편집 화면은 제출용 정책이 아닙니다. 이 작업은 파일 생성이며 자동으로 웹에 게시하지 않습니다.
6. **정식 키 준비**: [기존 서명 안내](../RELEASING.md)에 따라 Android Studio에서 개인 업로드 keystore를 만들고 GitHub Secrets에 저장합니다. 파일·비밀번호를 이슈·PR·대화에 공개하지 않습니다.
7. **Play 앱 생성**: Play Console에 AirCall AI를 만들고 Play App Signing을 설정합니다. 첫 AAB의 앱 ID가 `com.woojik.aircallai.release`인지 확인합니다. 앱 서명 인증서 SHA-1으로 정식 Google Android OAuth 등록을 준비합니다. 개발용 인증서 등록은 유지합니다.
8. **Google OAuth 준비**: 메일 발송·캘린더 기능에 필요한 API·동의 화면·공개 앱 이름·정책 URL·민감 범위 심사를 준비합니다. `gmail.send`도 민감 범위이므로 범위 축소만으로 공개 앱의 검증이 끝난 것은 아닙니다. Play 앱 서명 인증서와 직접 설치 인증서가 다르면 각 인증서를 등록합니다.
9. **릴리스 생성**: 성공한 main CI 이후 **Android Release** 실행. 더 큰 `version_code`를 입력합니다. 공개 정책/신고 서비스에 접속해 실제 설정을 검사하고, 서명된 APK/AAB와 검증 메타데이터를 생성합니다. 정식 워크플로는 Play에 자동 업로드하지 않습니다.
10. **내부 테스트**: `aircall-signed-release`의 `.aab`를 Play Console 내부 테스트에 올리고 **Play에서 설치한 앱**으로 [검증 목록](REVIEW_AND_TEST.md)을 수행합니다. 직접 설치 APK만 테스트한 것으로 Play 서명·분할 APK 검증을 대체하지 않습니다.
11. **스토어 양식**: [소개 문구](STORE_LISTING.md), [데이터 보안 작성 근거](DATA_SAFETY.md), 개인정보 URL, 실제 스크린샷, IARC 등급, 대상 연령, 앱 액세스 안내, FGS 선언을 입력합니다. 실제 사용 기능·서비스 정책과 맞아야 합니다.
12. **비공개 테스트 → 프로덕션 신청**: 2023-11-13 이후 만든 개인 계정은 최소 12명이 연속 14일 참여한 비공개 테스트 후 프로덕션 액세스를 신청해야 합니다. 실제 테스트 피드백과 수정 내용을 기록합니다. 모든 조건 충족 후 공개 출시를 별도로 진행합니다.

## 공개 변수

| 이름 | 값 |
| --- | --- |
| `AIRCALL_DEVELOPER_NAME` | 실제 운영자의 공개 표시 이름 |
| `AIRCALL_SUPPORT_EMAIL` | 문의와 개인정보 요청을 받을 실제 이메일 |
| `AIRCALL_PRIVACY_POLICY_URL` | 공개 HTML 정책 주소 |
| `AIRCALL_REPORT_ENDPOINT` | 익명 신고 접수 HTTPS 주소 |
| `AIRCALL_REPORT_RETENTION_DAYS` | 기본 30, 서버 정리 설정과 같게 입력 |
| `GOOGLE_OAUTH_CLIENT_ID_RELEASE` | 정식 패키지·인증서의 Android OAuth 클라이언트 ID |
| `AIRCALL_RELEASE_CERT_SHA256` | 업로드/직접 설치용 개인 키의 SHA-256, Play 앱 서명 인증서와 구분 |

서명 Secrets는 `AIRCALL_KEYSTORE_BASE64`, `AIRCALL_KEYSTORE_PASSWORD`, `AIRCALL_KEY_ALIAS`, `AIRCALL_KEY_PASSWORD`입니다. 공개 이름·정책 URL을 채워도 키가 없으면 정식 빌드를 만들지 않습니다. CI의 임시 테스트 키로 만든 APK/AAB는 배포하지 않습니다.

## 제출 전에 남는 작업

계정 생성/확인, 실제 정책 HTML 게시, 익명 신고 서비스 배포와 실전 접수, 업로드 키 보관, Play App Signing·OAuth 등록/검증, 실기·16 KB 기기 검사, 실제 스크린샷, IARC·대상 연령·데이터 보안·FGS 선언, 신규 개인 계정의 테스트입니다. 현재 정규식과 안전 프롬프트는 일부 유해 요청을 막는 기본 방어이며 모든 우회 표현이나 모델의 유해 출력을 판별하지 않습니다. 실제 모델별 안전 테스트와 접수된 신고 검토가 필요하며 심사 통과를 보장하지 않습니다.

## 공식 근거 (2026-10-04 확인)

- [가입·만 18세·등록비](https://support.google.com/googleplay/android-developer/answer/6112435)
- [대상 API 36](https://support.google.com/googleplay/android-developer/answer/11926878)
- [신규 개인 계정 테스트](https://support.google.com/googleplay/android-developer/answer/14151465)
- [AI 생성 콘텐츠·앱 내 신고](https://support.google.com/googleplay/android-developer/answer/13985936)
- [사용자 데이터·공개 개인정보처리방침](https://support.google.com/googleplay/android-developer/answer/10144311)
- [데이터 보안 양식](https://support.google.com/googleplay/android-developer/answer/10787469)
- [FGS 선언](https://support.google.com/googleplay/android-developer/answer/13392821)
- [16 KB 지원·AAB 정렬](https://developer.android.com/guide/practices/page-sizes)
- [Gmail 범위·민감 권한 검증](https://developers.google.com/workspace/gmail/api/auth/scopes)
