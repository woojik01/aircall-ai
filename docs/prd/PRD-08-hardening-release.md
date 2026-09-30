# PRD-08 — 성능·보안 강화 및 출시 준비

## 목표

기능 구현을 마친 AirCall AI를 실제 Android 배포 가능한 수준으로 검증한다.

## 성능

측정 항목:
- 앱 시작 시간
- AI 모델 로딩 시간
- STT latency
- 첫 AI 응답 latency
- TTS 시작 latency
- Barge-in 반응 시간
- CPU 사용량
- RAM 사용량
- 배터리 소모
- 발열

Local AI는 특히 메모리와 발열을 측정한다.

## 안정성

검증:
- 화면 OFF/ON
- 앱 백그라운드/포그라운드
- 다른 앱 실행
- 네트워크 ON/OFF
- API 오류
- 프로세스 종료
- 서비스 재시작
- 권한 거부
- 저장공간 부족
- 모델 파일 손상

## 보안 감사

반드시 확인:
- Git history에 secret 없음
- APK/소스에 API Key 없음
- Logcat에 token 없음
- 평문 credential 파일 없음
- OAuth scope 최소화
- 불필요한 서버 통신 없음
- HTTPS 사용
- 인증서/네트워크 보안 설정 검토
- 백업 정책에서 민감 데이터 노출 여부 검토

## 개인정보

앱은 사용자가 명확히 이해할 수 있도록:
- Local Mode에서는 어떤 데이터가 외부로 나가지 않는지
- Cloud Mode에서 어떤 데이터가 선택한 서비스로 전송되는지
- Tool 사용 시 어떤 서비스에 접근하는지

를 표시한다.

## 배포

- Release build
- 서명 설정
- ProGuard/R8 검토
- 권한 검토
- 앱 아이콘/이름
- 개인정보 처리 관련 문서
- Play Store 요구사항 확인

## 완료 조건

- Release APK/AAB 빌드
- 실제 Android 기기 장시간 테스트
- 보안 검사 통과
- 성능 측정 결과 기록
- 치명적 crash 없음
- 모든 이전 PRD 완료
- CI 전체 통과
