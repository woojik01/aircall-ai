# AirCall AI (Android)

로컬 우선 Android AI 음성 비서. 요구사항은 [docs/prd](docs/prd) 참조.

## 현재 단계
- **PRD-01 완료 대기 (리뷰 중)** — Android 기반 구조, 메인/대화/설정 화면, ConversationEngine + AIProvider 추상화, 단위 테스트, GitHub Actions CI.

## 구조
```
app/src/main/java/com/woojik/aircallai/
  core/       logging, (security/storage/network: PRD-02 이후)
  ai/provider AIProvider, NoopAIProvider (Local/Cloud는 PRD-04)
  conversation ConversationEngine, ConversationState (IDLE/LISTENING/PROCESSING/SPEAKING/ERROR)
  ui/         MainActivity, Main/Conversation/Settings Screen
```

## 빌드
```bash
gradle wrapper        # 최초 1회 (JDK 17)
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
API Key는 절대 코드/빌드 설정에 넣지 않는다 (PRD-02 CredentialManager 사용 예정).
