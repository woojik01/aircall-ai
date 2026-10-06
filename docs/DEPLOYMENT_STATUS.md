# AirCall AI 배포 준비 점검

점검일: 2026-10-07 (대한민국 시간). 이 기록은 저장소·GitHub Actions·공개 웹 응답으로 확인한 범위를 명시합니다.

## 확인 및 보완

| 항목 | 상태와 근거 |
| --- | --- |
| 최신 앱 코드 | 점검 시작 기준 `17e15c1`, Android CI [37544057066](https://github.com/woojik01/aircall-ai/actions/runs/37544057066) 성공 |
| 개인 서명 빌드 | [37457280737](https://github.com/woojik01/aircall-ai/actions/runs/37457280737) 성공. APK/AAB·초안 생성·개인 키 검사 완료 |
| 정식 패키지 | `com.woojik.aircallai.release`, 최소 API 26 / 대상 API 36 |
| 공개 개발자명 | 사용자 제공 `woojik01`, 공통 공개 설정에 반영 |
| 원스토어 PID | 사용자 제공 `0001009976`, 공개 설정 반영. ONEconsole 상품과의 최종 일치는 콘솔에서 확인 |
| 서비스 문의 | `woojik1220@gmail.com`, 응답 신고·HTTPS 신고 연결은 삭제 상태 유지 |
| 정책 URL | `https://woojik01.github.io/aircall-ai/privacy-policy.html`, 기존 URL HTTP 200 확인. 오래된 신고 안내를 현재 앱 동작으로 수정 |
| 정책 원본 | 웹과 제출 문서가 `docs/play/privacy-policy.template.html`을 공유. 릴리스 시 게시 내용 일치 검사 |
| 제출 자료 | 정책 웹페이지 전체·원스토어 설명·권한표·실기 검증표 생성. Store Preparation에서 자동 생성·공개 정책 검사 |
| 서명 이력 | 확인된 최신 초안 `v0.4.1-build.30004`. 다음 빌드는 실행 시 모든 초안·스토어 버전보다 큰 값 사용 |
| 정식 OAuth 변수 | 개인 서명 빌드 로그에서 `GOOGLE_OAUTH_CLIENT_ID_RELEASE` 존재 확인. Google 콘솔의 인증서 등록·공개/심사 상태는 이 로그로 검증되지 않음 |

## 원스토어 제출 전에 남는 작업

1. 최신 main Android CI 및 Store Preparation 성공을 확인합니다.
2. ONE store Release에서 새 version_code를 입력해 최신 코드의 정식 서명 APK와 제출 묶음을 생성합니다. 점검 당시 이력만 기준으로는 30005 이상이며, 그 뒤 생성한 빌드나 스토어 버전이 있으면 더 큰 값을 사용합니다. 오래된 30004 APK를 최신 코드로 간주하지 않습니다.
3. 실제 ARM64 기기에서 설치·업데이트 후 데이터 유지·로컬/클라우드·음성·도구·권한 거부를 검증하고 `docs/onestore/REVIEW_AND_TEST.md`에 기록합니다. 기록은 아직 미검증입니다.
4. 정식 Google 인증서 등록, Gmail·Calendar API 활성화, 동의 화면의 테스트 사용자 제한·민감 범위 심사 상태를 콘솔과 실제 로그인으로 확인합니다. 변수 존재만으로 완료 처리하지 않습니다.
5. 실제 앱 스크린샷, 상품 설명, 정책 URL, 대상 국가, 콘텐츠 등급, 검증 접근 방법을 ONEconsole에 입력하고 정식 APK로 검증 요청합니다. 이 점검은 콘솔에 상품을 제출하거나 앱을 공개하지 않습니다.
6. 공개 정책의 문의 메일 보관 약속(접수일부터 최대 30일 안에 메일함·휴지통 영구 삭제)은 운영자의 수동 업무입니다. 앱이나 서버가 대신 자동 삭제하지 않습니다. 운영자는 이 절차를 실제로 수행해야 합니다.

## Google Play

현재 공개 출시 요건 미충족: 앱 내부에서 개발자에게 AI 콘텐츠를 신고하는 기능이 없습니다. 외부 메일 앱을 여는 서비스 문의는 앱을 떠나지 않는 신고 요건을 충족하지 않습니다. 사용자 요청에 따라 응답 신고는 복원하지 않았습니다. 외부 모델을 사용하더라도 AI 챗봇이 핵심 기능인 앱은 정책 적용 대상입니다.

Play 제출 이전에 해당 기능과 운영 절차를 준비해야 합니다. Play Console 계정·앱 서명·데이터 보안·FGS·대상 연령·콘텐츠 등급·실제 테스트는 별도로 확인해야 합니다. 현재 Android Release는 테스트용 비공개 초안 생성 경로이며 공개 준비 완료를 의미하지 않습니다.

## Galaxy Store

현재 전용 제출 워크플로와 Seller Portal 등록·심사 기록은 확인되지 않았습니다. 원스토어용 산출물의 상품 링크와 채널 표시를 Galaxy Store용이라고 제출하지 않습니다. 공통 정책·서명·데이터 검증은 재사용할 수 있으며, 원스토어 우선 출시 후 전용 채널과 콘솔 등록을 준비합니다.

## 근거

- [Google Play AI 생성 콘텐츠 정책](https://support.google.com/googleplay/android-developer/answer/13985936) — 2026-10-07 확인
- [Google Play 정책 적용 범위](https://support.google.com/googleplay/android-developer/answer/14094294)
- [원스토어 등록 안내와 공식 자료](onestore/README.md)
- [공통 패키지·서명 안내](RELEASING.md)
