# PRD-09 — 간편 계정 로그인 및 외부 서비스 OAuth 연동

## 1. 목표

AirCall AI에서 사용자가 GitHub Personal Access Token(PAT), Gmail OAuth access token 등을 직접 발급받아 복사·붙여넣지 않아도 외부 서비스를 간단하게 연결할 수 있도록 한다.

핵심 원칙:
- 앱은 로그인 없이도 사용할 수 있어야 한다.
- 로그인은 앱 사용의 필수 조건이 아니다.
- GitHub와 Google 계정 연결은 독립적으로 수행한다.
- 계정 로그인과 외부 서비스 권한 부여를 구분한다.
- OAuth 자격증명은 PRD-02의 로컬 전용 보안 저장 정책을 그대로 따른다.
- AirCall AI 자체 서버에 비밀번호, access token, refresh token, 대화 기록을 저장하지 않는다.
- 필요한 서비스와 권한만 선택적으로 연결한다.
- 기존 PAT/Gmail token 수동 입력 기능은 OAuth가 안정적으로 검증될 때까지 호환한다.
- OAuth 실패·취소·만료가 발생해도 앱의 기본 기능은 계속 사용할 수 있어야 한다.

## 2. 현재 상태

2026-10-03 기준 main에는 PRD-06의 핵심 Tool 계층이 반영되어 있다.

- Android 앱 및 로컬 저장소/Keystore 자격증명 저장
- Local/Cloud AI Provider
- 텍스트·음성 대화 및 백그라운드 통화 구조
- GitHub PAT 저장, repository 조회, Issue/PR 생성
- Gmail Tool 및 Gmail OAuth access token 수동 등록 UI
- Calendar 권한 요청 구조
- Notes/Calendar/Gmail/GitHub Tool Adapter
- Tool 위험도 READ/WRITE/DESTRUCTIVE
- WRITE 승인 및 승인 상태 영속화
- Tool-AI 연결 및 실행 메타데이터 로깅

현재 제한:
- GitHub는 PAT를 직접 등록해야 한다.
- Gmail은 token을 직접 등록해야 한다.
- Gmail access token 자동 발급/갱신은 아직 구현되지 않았다.
- 외부 계정 OAuth 연결 UX가 없다.

현재 main의 최신 커밋은 PRD-06 Calendar/Gmail/Notes Adapter 완료 커밋이다. 현재 열린 PR #21은 Calendar 권한 요청과 Gmail token 등록 UI 개선이며 OAuth 자동화 자체는 아니다.

## 3. 로그인 없는 사용

앱 최초 실행에서 로그인 화면을 강제하지 않는다.

권장 시작 화면:

AirCall AI

AI와 바로 대화하세요.

[바로 사용하기]

계정을 연결하면 더 많은 기능을 사용할 수 있습니다.

[GitHub 연결]
[Google 연결]

바로 사용하기를 선택하면 계정 없이 핵심 기능을 사용할 수 있다.

로그인 없이 사용할 수 있는 예:
- 로컬 AI
- 이미 설정된 Cloud AI Provider
- 텍스트 대화
- 음성 대화
- 로컬 Notes
- Android Calendar 권한 기반 기능
- 계정이 필요하지 않은 기타 기능

연결하지 않은 외부 Tool을 호출하면 로그인 화면으로 강제 이동하지 않고 해당 서비스 연결을 안내한다.

예:

GitHub 계정이 연결되어 있지 않습니다. GitHub를 연결하면 저장소를 확인할 수 있습니다.

[GitHub 연결] [나중에]

## 4. 계정 모델

AirCall AI 자체 계정과 외부 서비스 계정을 구분한다.

1차 범위에서는 AirCall AI 자체 회원가입/로그인을 만들지 않는다.

- 이메일/비밀번호 회원가입 없음
- AirCall 서버 계정 없음
- 필수 로그인 없음
- 중앙 사용자 계정 서버 없음

외부 서비스 계정은 다음과 같이 독립적으로 연결한다.

- GitHub
- Google/Gmail

향후 다른 서비스로 확장할 수 있도록 Provider별 연결 상태를 독립적으로 관리한다.

## 5. GitHub 연결

설정 → 계정 및 도구 연동 → GitHub에서 [GitHub로 연결]을 제공한다.

사용자가 버튼을 누르면 시스템 브라우저 또는 Android 권장 인증 흐름으로 GitHub 인증 페이지를 연다.

인증 성공 후 앱으로 돌아오며 GitHub 사용자명과 연결 상태를 표시한다. token 자체는 절대 표시하지 않는다.

기본 Tool 범위:
- repository 조회
- Issue 생성
- Pull Request 생성

처음부터 불필요한 전체 계정 권한을 요청하지 않는다.

GitHub 연동은 가능하면 GitHub App 기반 권한 모델을 우선 검토한다. 저장소 단위 접근과 최소 권한을 지원해야 한다.

기존 PAT 방식은 OAuth/GitHub App 방식이 안정적으로 검증될 때까지 호환한다.

## 6. Google/Gmail 연결

설정 → 계정 및 도구 연동 → Google에서 [Google로 연결]을 제공한다.

Google 인증 화면에서 사용자가 계정을 선택하고 필요한 권한을 승인한다.

성공 후 연결 상태와 가능하면 계정 식별용 이메일/표시명을 표시한다. access token과 refresh token은 UI에 노출하지 않는다.

Gmail Tool에 실제 필요한 OAuth scope만 요청한다.

현재 목표:
- Gmail 읽기 기능에 필요한 읽기 권한
- Gmail 검색 기능에 필요한 권한
- Gmail 발송 기능에 필요한 발송 권한

기능이 없는 권한은 요청하지 않는다.

Google/Gmail OAuth와 Android 기기 Calendar 권한은 별개다. Google 로그인만으로 Android Calendar 권한을 허용된 것으로 처리하지 않는다.

## 7. OAuth 구조

권장 구조:

UI
  → OAuth Coordinator
  → GitHub OAuth 또는 Google OAuth
  → callback
  → CredentialManager

Tool은 OAuth 구현 세부사항을 직접 처리하지 않는다.

GitHubTool → CredentialManager → github credential
GmailTool → CredentialManager → gmail credential

OAuth 계층과 Tool 계층을 분리한다.

가능한 경우 Android의 공식 권장 인증 흐름과 시스템 브라우저를 사용한다. 앱 내부에서 GitHub/Google 비밀번호를 직접 입력받지 않는다.

OAuth callback에는 provider가 지원하는 state/PKCE 등 적절한 보안 메커니즘을 적용하고 callback의 state, code, provider를 검증한다.

## 8. CredentialManager 연동

PRD-02의 CredentialManager를 그대로 사용한다.

서비스별 credential에는 provider, access token, 필요 시 refresh token, 계정 식별자, 만료 시각 등의 정보가 포함될 수 있다.

access token과 refresh token은 Android Keystore 기반 암호화 저장을 사용한다.

다음은 금지한다:
- 평문 token 파일
- 평문 SharedPreferences
- Git/소스 코드/BuildConfig 내 token
- Logcat 내 token
- AI prompt 또는 ToolResult 내 token
- Tool 실행 메타데이터 내 token
- AirCall AI 자체 서버로 token 전송

## 9. Token 갱신

OAuth provider가 refresh token을 제공하는 경우 자동 갱신을 지원한다.

access token 유효 → API 호출

access token 만료 → refresh token 존재 여부 확인
- 존재: token refresh → CredentialManager 갱신 → API 호출
- 없음 또는 refresh 실패: 해당 서비스만 재인증 상태로 전환

재인증 때문에 앱 전체 사용을 중단하지 않는다.

연결 상태는 다음을 지원한다.
- NOT_CONNECTED
- CONNECTING
- CONNECTED
- EXPIRED
- REAUTH_REQUIRED
- ERROR

UI에는 내부 enum 이름 대신 이해하기 쉬운 문구를 표시한다.

## 10. 연결 해제

서비스별 [연결 해제]를 제공한다.

연결 해제 시 해당 서비스의 access token, refresh token, 계정 credential 및 연결 metadata를 삭제하고 해당 Tool 인증 상태를 초기화한다.

다른 서비스의 credential은 삭제하지 않는다.

예: GitHub 연결 해제는 Google 연결에 영향을 주지 않는다.

## 11. OAuth 취소/실패

인증 취소가 앱 종료나 전체 기능 중단으로 이어지지 않는다.

예:

GitHub 연결이 취소되었습니다.
GitHub 없이도 AirCall AI를 계속 사용할 수 있습니다.

[다시 연결] [계속 사용]

네트워크 오류, 권한 거부, callback 오류도 사용자에게 원인을 이해할 수 있는 수준으로 표시하고 다시 시도할 수 있어야 한다.

## 12. 기존 수동 Token 방식

OAuth 구현 직후 기존 수동 token 입력 기능을 삭제하지 않는다.

일반 사용자 화면에서는 OAuth 연결을 기본으로 제공하고, 필요하다면 고급 설정에서 수동 PAT/token 입력을 제공한다.

예:

GitHub
[GitHub로 연결]
  고급 설정 → Personal Access Token 직접 입력

Google
[Google로 연결]
  고급 설정 → OAuth Token 직접 입력

OAuth가 안정적으로 검증된 이후 수동 방식의 최종 유지 여부는 별도 결정한다.

## 13. AI Tool 동작

AI는 계정 연결 상태에 따라 Tool을 사용할 수 있어야 한다.

GitHub 미연결 상태에서 GitHub 작업을 요청하면:

GitHub 계정이 연결되어 있지 않습니다.
GitHub를 연결하면 이 기능을 사용할 수 있습니다.

[GitHub 연결]

연결 상태에서는 기존 GitHubTool이 CredentialManager의 credential을 사용한다.

Gmail도 동일한 흐름을 사용한다.

인증 만료 시 단순 API 오류 대신 재연결 방법을 안내한다.

## 14. 보안 요구사항

절대 금지:
- GitHub/Google 비밀번호를 앱이 직접 수집
- access token Logcat 출력
- refresh token 평문 저장
- token을 AI prompt에 포함
- token을 ToolResult에 포함
- token을 실행 메타데이터에 기록
- token을 Git 또는 빌드 설정에 삽입
- token을 자체 서버에 업로드

OAuth callback은 예상하지 않은 provider/state/code를 거부해야 한다.

## 15. 개인정보 UI

설정 → 개인정보 · 데이터 흐름에서 다음을 명확하게 설명한다.

로그인하지 않은 경우:
- AirCall AI 계정이 필요하지 않음
- 로컬 기능 사용 가능
- 연결하지 않은 외부 서비스에는 접근하지 않음

GitHub 연결:
- GitHub 계정 인증은 GitHub에서 수행
- 승인된 범위로 GitHub API 호출
- credential은 기기에 암호화 저장
- AirCall AI 자체 서버에는 credential을 저장하지 않음

Google 연결:
- Google 계정 인증은 Google에서 수행
- 승인된 Google/Gmail 범위로 API 호출
- credential은 기기에 암호화 저장
- AirCall AI 자체 서버에는 credential을 저장하지 않음

## 16. 테스트 요구사항

OAuth:
- GitHub 성공/취소/실패
- Google 성공/취소/실패
- callback state 불일치
- callback code 누락
- provider 불일치
- 네트워크 오류

Credential:
- access token 암호화 저장
- refresh token 암호화 저장
- 앱 재시작 후 복구
- 연결 해제 후 삭제
- 서비스 간 credential 독립성
- token 로그 노출 방지

Token refresh:
- 정상 refresh
- refresh 실패
- refresh token 없음
- 만료 처리
- 재인증 요구 처리

로그인 없는 사용:
- 계정 없이 앱 실행
- 계정 없이 로컬 AI 사용
- 계정 없이 음성 대화
- 미연결 GitHub Tool 호출
- 미연결 Gmail Tool 호출
- OAuth 취소 후 앱 계속 사용

Tool:
- 연결된 GitHub repository 조회
- Issue 생성 승인 흐름
- Gmail 발송 승인 흐름
- 인증 만료 시 재연결 안내
- 연결 해제 후 Tool 접근 차단

## 17. 구현 순서

Phase 1 — 인증 추상화
- OAuthProvider 인터페이스
- OAuthState/Callback 모델
- AccountConnection 상태 모델
- CredentialManager 확장
- 단위 테스트

Phase 2 — GitHub
- GitHub OAuth 또는 GitHub App 연결
- callback
- credential 저장
- 계정 상태 UI
- 연결 해제
- GitHub Tool 연동
- 실제 Android 기기 테스트

Phase 3 — Google
- Google OAuth
- Gmail scope
- callback
- credential 저장 및 갱신
- 계정 상태 UI
- 연결 해제
- Gmail Tool 연동
- 실제 Android 기기 테스트

Phase 4 — 로그인 없는 UX
- 첫 실행 로그인 강제 제거
- 바로 사용하기
- 미연결 Tool 안내
- 재연결 UI
- 실패/취소 UX

Phase 5 — 보안/회귀 검증
- token leak 검사
- callback 보안 검사
- credential 암호화 검사
- 기존 PAT fallback 검사
- Tool 승인 회귀 테스트
- Android 빌드
- CI
- 실제 기기 테스트

## 18. 완료 기준

1. 로그인 없이 앱을 실행할 수 있다.
2. 로그인 없이 핵심 로컬 기능을 사용할 수 있다.
3. GitHub OAuth 연결이 가능하다.
4. Google OAuth 연결이 가능하다.
5. OAuth token이 PRD-02 방식으로 암호화되어 로컬에 저장된다.
6. GitHub/Gmail Tool이 OAuth credential을 사용할 수 있다.
7. OAuth token 만료 및 재인증 흐름이 동작한다.
8. 사용자가 서비스별로 연결/해제를 할 수 있다.
9. 한 서비스의 연결 해제가 다른 서비스에 영향을 주지 않는다.
10. OAuth 취소/실패가 앱 종료나 전체 기능 중단으로 이어지지 않는다.
11. 기존 수동 token 방식과의 호환성을 유지한다.
12. token이 로그, ToolResult, prompt, 실행 메타데이터에 노출되지 않는다.
13. OAuth callback 보안 검증이 통과한다.
14. 단위 테스트와 가능한 통합 테스트가 통과한다.
15. Debug APK 빌드가 성공한다.
16. CI가 통과한다.
17. 실제 Android 기기에서 GitHub/Google 연결 및 해제를 검증한다.
18. 개인정보 안내가 실제 동작과 일치한다.

## 19. 구현 시 주의사항

이 PRD는 AirCall AI 자체 사용자 계정을 만드는 프로젝트가 아니다.

목표:

로그인 없이 AirCall AI 사용
+
필요할 때만 GitHub / Google 계정 연결
+
연결된 서비스만 AI Tool에서 사용

OAuth 연결을 이유로 중앙 서버를 새로 만들지 않는다.
향후 서버 기반 동기화가 추가되더라도 OAuth credential을 서버에 저장하는 구조로 변경하지 않는다.
모든 외부 서비스 credential은 PRD-02의 로컬 전용 보안 저장 원칙을 유지한다.