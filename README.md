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
gradle assembleDebug
gradle testDebugUnitTest
```
JDK 17 필요. 저장소에 wrapper가 없으므로 Gradle 8.7 이상을 로컬에 설치하거나
CI처럼 gradle/actions/setup-gradle로 버전을 지정해 사용합니다.

API Key는 절대 코드/빌드 설정에 넣지 않는다 (PRD-02 CredentialManager 사용 예정).
