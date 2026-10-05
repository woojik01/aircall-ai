# 개인정보 공개 페이지와 운영 절차

작성 기준: 2026-10-05, Asia/Seoul. 현재 원스토어 준비 구성은 **이메일 신고**이며
HTTPS 자동 접수 서버·Apps Script 신고 저장소를 운영한다는 주장은 하지 않는다.

## 공개할 파일

`docs/index.html`, `docs/privacy-policy.html`, `docs/support.html`, `docs/site.css`,
`docs/.nojekyll`의 파일 내용만 `gh-pages` 브랜치 루트에 배치한다.
페이지와 CSS는 상대 경로를 사용하므로 GitHub 프로젝트 사이트 `/aircall-ai/`에서도 동작한다.
`docs/` 전체나 비공개 운영 자료를 사이트에 복사하지 않는다.

공개 개발자명은 `woojik01`, 문의 이메일은 `woojik1220@gmail.com`이다.
정책 버전은 `2026-10-05`이며 공개 배포 후 실제 HTTPS 페이지에 접속해 내용과 링크를 확인해야 한다.
예상 주소는 `https://woojik01.github.io/aircall-ai/privacy-policy.html`이다.
예상 주소를 이미 공개되었다는 증거로 사용하지 않는다.

## 신고·문의 이메일 30일 보관

이 구성의 `AIRCALL_REPORT_RETENTION_DAYS=30`은 **운영자의 보관 정책**이다.
이메일 모드에서 앱이나 이 저장소의 Apps Script가 Gmail 편지함을 자동 정리하지 않는다.
정책을 게시하는 운영자는 다음 절차를 실제로 수행해야 한다.

1. 신고와 문의를 비공개로 확인한다. 신고 번호·접수일·삭제 기한만 관리하고 불필요한 개인정보를 별도 파일로 복사하지 않는다.
2. 매일 수신함·보관함·관련 발신 답변을 확인한다. 접수일부터 30일을 넘기지 않도록 삭제 기한 이전에 해당 기록과 관련 개인정보를 영구 삭제한다. 같은 건의 회신이 생겨도 원 신고의 접수일을 기준으로 한다.
3. 휴지통·스팸함에도 원문이 남지 않게 영구 삭제를 완료한다. 일일 점검만 하는 경우 29일 경과 기록부터 삭제하면 하루의 점검 간격 때문에 30일을 넘기는 일을 피할 수 있다.
4. 조기 열람·정정·삭제·처리정지 요청을 받은 경우 신고 번호·발송 시각 등 최소 정보로 본인 또는 정당한 대리인의 권한을 확인하고 처리한다. 공개 Issue나 공개 Sheet로 원문을 이동하지 않는다.
5. 앱의 초안 열기를 접수 성공으로 취급하지 않는다. 실제 수신 메일을 기준으로 접수를 관리한다. 발신자의 보낸편지함, 서비스 제공자의 시스템 로그·백업은 운영자가 직접 지울 수 없다는 안내를 유지한다.

신고 원문이나 이메일 주소를 AI 학습 자료로 사용하지 않는다. 지속적인 운영 기록이 필요하면
개인정보를 제거한 통계만 별도로 검토하되 새 데이터 처리 목적을 임의로 추가하지 않는다.
HTTPS 접수 서비스로 변경하면 정책·앱 안내·검증 스크립트·보관 자동화·실제 배포를 함께 변경한다.

## 코드와 정책의 근거

- `AppGraph.kt`: 앱 전용 파일 `chats/rooms.enc`, `tools/notes.txt`, 암호화 자격증명 저장소를 구성. 개발자 AI 중계 서버 없이 앱의 Cloud 어댑터가 직접 요청.
- `chat/ChatRoomRepository.kt`: 방 이름과 메시지를 암호화한 원자적 파일에 저장하고 방 삭제 시 저장 목록에서 제거.
- `core/storage/FileCredentialManager.kt`, `auth/OAuthCredentialStore.kt`: 키·토큰·저장되는 계정 표시 정보를 Android Keystore CryptoEngine으로 암호화. 서비스 연결 해제 시 해당 키 삭제.
- `tools/NotesTool.kt`: 메모 내용과 생성 시각은 일반 앱 전용 텍스트 파일. 개별 메모 삭제 기능 없음. 조회 결과가 AI 입력에 들어갈 수 있음.
- `ai/cloud/CloudAIProvider.kt`, `tools/ToolBridgedAIProvider.kt`: 동의한 서비스 주소에 대화 이력과 도구 결과를 포함한 요청. 메모·일정·저장소 조회 결과도 클라우드 전송 가능.
- `auth/GoogleOAuthClient.kt`: Gmail 발송·Calendar 이벤트 범위. Google 새로고침 토큰 저장 없이 단기 액세스 토큰 사용. Gmail 읽기 범위는 현재 요청 목록에서 제외.
- `tools/GmailTool.kt`, `tools/GoogleCalendarAdapter.kt`, `tools/GitHubApi.kt`: 선택한 외부 기능의 요청과 인증정보를 해당 서비스에 전송.
- `audio/AndroidSpeechRecognizerEngine.kt`, `audio/AndroidTtsEngine.kt`: Android 인식·TTS 서비스. 앱이 원본 음성 파일을 자체 저장하는 코드는 없으며 인식 텍스트는 대화에 포함.
- `settings/SettingsStore.kt`: 주소별 Cloud 전송 안내 확인, 음성 안내 확인, 만 14세 이상 안내 확인을 일반 설정에 저장. 생년월일 수집 없음.
- `tools/ToolExecutionLog.kt`, `tools/ToolExecutionEvent.kt`, `core/logging/SecureLog.kt`: 100건 실행 메타데이터와 50건 상태는 메모리; 디버그에서만 제한된 로그. 작업 알림은 메타데이터만 표시.
- `AndroidManifest.xml`: `allowBackup=false`, `usesCleartextTraffic=false`; 마이크·알림·선택적 오버레이·사용자가 시작한 서비스 권한. 기기 캘린더·전화·SMS·주소록·위치·모든 파일 권한 없음.
- `ui/PrivacyScreen.kt`: 채팅·메모·키·토큰·설정·모델을 Android 앱 데이터 삭제 기능으로 제거. 외부 서비스 자료는 별도 삭제.
- `privacy/ContentReportClient.kt`, `ui/ContentReportDialog.kt`: 최종 이메일 모드 변경 시 실제 초안 필드·선택 응답·사용자 전송 동작과 공개 정책을 일치시킨다.

## 확인한 공식 자료

- 개인정보 보호법 제30조: https://www.law.go.kr/lsLinkCommonInfo.do?lsJoLnkSeq=1034292763
- 개인정보보호위원회 2026 처리방침 작성지침 안내: https://www.privacy.go.kr/front/bbs/bbsView.do?bbsNo=BBSMSTR_000000000049&bbscttNo=20885
- GitHub Pages는 방문자 IP를 보안 목적으로 기록: https://docs.github.com/en/pages/getting-started-with-github-pages/what-is-github-pages#data-collection
- Google 정책: https://policies.google.com/privacy?hl=ko
- GitHub 정책: https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement
- Groq 정책: https://groq.com/privacy-policy
- Hugging Face 정책: https://huggingface.co/privacy

외부 제공자의 국가·세부 보관 기간은 사용자 선택·계정 설정에 따라 달라질 수 있어 임의의
국가·숫자나 이용자의 키·토큰을 개발자가 수집하지 않는다는 근거 없는 일괄 표현을 넣지 않았다.
대화·인증정보가 기기에만 보관된다는 표현은 **저장 위치**이며 Cloud·인증·도구 요청에서
외부로 전송하지 않는다는 뜻으로 사용하지 않는다.
