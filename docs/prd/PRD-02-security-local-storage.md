# PRD-02 — 로컬 데이터 및 보안 자격증명

## 목표

모든 API Key와 외부 서비스 연동 정보를 서버 없이 사용자 기기에 저장하는 보안 저장 계층을 구축한다.

## 절대 규칙

다음 데이터는 서버에 저장하지 않는다.

- AI API Key
- OAuth access token
- OAuth refresh token
- GitHub 인증정보
- Google/Gmail/Calendar 인증정보
- 사용자의 대화 기록
- 개인 메모 및 민감한 설정

## 저장 위치

Android 앱 전용 내부 저장 영역을 사용한다.

예:

```
app-private/
  config/
  credentials/
  conversations/
  cache/
  models/
```

다른 앱이 일반적으로 직접 접근할 수 없는 앱 전용 저장소를 사용한다.

## 암호화

민감한 데이터는 평문 JSON/TXT로 저장하지 않는다.

- Android Keystore를 이용해 암호화 키를 관리한다.
- 암호화된 credential blob만 파일/DB에 저장한다.
- 암호화 키 자체를 파일에 저장하지 않는다.
- 로그/Crash report에 credential이 포함되지 않도록 한다.

## Credential Manager

앱 내부에 단일 CredentialManager를 둔다.

책임:
- 저장
- 조회
- 갱신
- 삭제
- 서비스별 credential 구분
- 암호화/복호화
- 로그 마스킹

예상 API:

```kotlin
interface CredentialManager {
    suspend fun save(service: String, credential: ByteArray)
    suspend fun load(service: String): ByteArray?
    suspend fun delete(service: String)
    suspend fun clearAll()
}
```

## 데이터 분류

### 민감 데이터
암호화 필수:
- API Key
- OAuth Token
- refresh token

### 일반 설정
로컬 저장:
- 선택한 AI Provider
- 음성 설정
- UI 설정
- 사용자 선호 설정

### 대화 데이터
로컬 DB에 저장하되 삭제 기능을 제공한다.

## 금지

- Git에 secret 커밋
- `.env`를 APK에 포함
- BuildConfig에 실제 Key 삽입
- 소스 코드 문자열 상수로 Key 삽입
- Logcat에 토큰 출력
- 자체 서버에 Key 업로드

## 완료 조건

- 가짜 API Key를 저장/조회/삭제하는 테스트 통과
- 저장 파일에서 평문 credential이 노출되지 않음
- 앱 재실행 후 credential 복구
- credential 삭제 후 복구 불가
- 로그에 credential이 나타나지 않음
- CI secret scan 통과
