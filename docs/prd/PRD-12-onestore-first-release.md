# PRD-12: 원스토어 우선 배포

목표: 대한민국에 무료·광고 없음 AirCall AI를 개인 서명 APK로 제출할 수 있게 한다.
실제 공개 출시 완료는 계정·정책·서명키·원스토어 검증과 기기 테스트 완료 후 판단한다.

## 구현

1. `AIRCALL_DISTRIBUTION_CHANNEL` 빌드 설정으로 direct/play/onestore 구분, 정식 패키지는 공통 유지.
2. 별도 원스토어 PID 설정, 유효한 발급 PID로 공식 상품 링크 생성.
3. 앱 정보 및 사용 안내 화면, AI 첫 설정 이동, 개인정보·신고 경로 유지.
4. Google Play 서비스 없는 기기에서 Google 연결 조건 안내, TTS 서비스 visibility 선언.
5. ONE store Release: 공개 정책/신고 서버/PID/개인 서명 사전 검사, unit/lint/R8,
   서명 APK 검증·이력 대조, 제출 패키지·비공개 초안 생성, 키 제거.
6. Prepare ONE store Documents: 서명 없이도 실제 공개 정보로 정책·스토어·심사 자료 생성.
7. CI에서 원스토어 채널 release 경로와 개발용 APK 업데이트 유지 검증.

## 완료 기준

- 코드: unit·lint·APK 빌드·서명·네이티브 정렬·기존 APK 업데이트 검사가 모두 성공.
- 운영: ONEconsole 계정·발급 PID·개인 키·공개 연락처·정책·신고 서버와 Google 공개 권한 상태 준비.
- 실기: `docs/onestore/REVIEW_AND_TEST.md` 기록.
- 제출: 본인 PID·패키지·실제 기능·권한에 맞는 상품 등록, 검증 요청과 승인 확인.

IAP·유료 앱 라이선스·유료 API 판매·서버 계정·자동 스토어 업로드는 첫 출시 범위에 포함하지 않는다.
상세 설계 및 확인한 공식 출처는 [원스토어 출시 안내](../onestore/README.md)에 기록한다.
