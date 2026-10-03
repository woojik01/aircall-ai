# AI 워크플로우 진행 내레이션 설계

## 문제

기존 ToolBridgedAIProvider는 도구 호출을 실행하지만 그 과정이 사용자에게 보이지 않는다.
"README를 읽어서 메일로 보내줘" 같은 요청에서 첫 응답 이후 아무 안내 없이 조용히 끝나거나,
진행/실패/재시도가 대화 밖에서 일어나 사용자가 무슨 일인지 알 수 없다.

## 목표

- 도구 실행의 각 단계(시작/성공/재시도/실패/승인 필요)를 실시간 문장으로 사용자에게 안내한다.
- 도구 조합(github만, gmail만, github+gmail, 향후 추가 도구)과 무관하게 하나의 메커니즘으로 동작한다.
- 어떤 도구를 언제 쓸지는 AI가 대화 문맥에서 유동적으로 결정한다. 시스템은 고정 스크립트를 두지 않는다.
- 안내 문장에 개인정보(인자 값)를 담지 않는다(PRD-08 원칙 유지).

## 구조

- `ToolActivityEvent`: Narration / Started / Succeeded / Retrying / Failed / ApprovalRequired 봉인 인터페이스.
- `ToolActivityBus`: 진행 이벤트를 방송하는 SharedFlow 버스. 도구 수·조합과 무관하게 단일 스트림.
- `Tool.label(action)`: 도구별 사용자 친화적 라벨. 오버라이드 없으면 `tool.action` 폴백.
  새 도구는 이 값만 제공하면 진행 안내에 자동 편입된다.
- `ToolActivityPhraser`: 이벤트를 사용자용 문장("GitHub 저장소 조회 실행 중...")으로 변환.

## 흐름 (예: README를 읽어 Gmail으로 전송)

1. AI: "레포부터 보겠습니다." + `TOOL: github.read_repository ...`
   - 지시어 앞 문장은 Narration 이벤트로 즉시 방송된다.
2. Executor: Started("GitHub 저장소 조회 실행 중...") → 실행 → Succeeded.
3. AI: "README.md 내용을 확인했습니다. 이제 메일을 보내겠습니다." + `TOOL: gmail.send_email ...`
4. Executor: Started → (실패 시) Retrying → Failed. TOOL_RESULT 실패가 AI에게 전달되고
   AI가 "발송이 안 되었습니다. 다시 시도합니다." 같은 안내와 함께 재호출할 수 있다.
5. 최종 성공 안내가 transcript에 남는다.

## 재시도 정책

- READ 작업: 일시적 오류 가능성을 고려해 ToolExecutor가 1회 자동 재시도(Retrying 이벤트 발행).
- WRITE 작업: 중복 실행 부작용(중복 발송 등) 때문에 자동 재시도하지 않는다.
  실패 원인을 담은 TOOL_RESULT를 AI에게 주고, AI가 재시도 여부와 방법을 판단해 유동적으로 대응한다.

## 다중 도구 지원

- MAX_TOOL_ROUNDS를 2에서 6으로 상향해 한 턴에 여러 도구를 연속 사용할 수 있다.
- 시스템 프롬프트에 "여러 도구가 필요하면 하나씩 순서대로 사용한다" 규칙을 추가했다.

## UI/음성 연결 (후속 증분)

- `ConversationEngine.activity: StateFlow<String?>`가 Processing 중 실시간 진행 문장을 노출한다.
- 텍스트 UI: ConversationScreen에서 Processing 상태일 때 대화 버블 아래 진행 줄로 표시.
- 통화형 UI: CallStatus/CallOverlay에 진행 문장 표시.
- 음성 모드: 진행 문장을 짧게 날독하거나 상태 톤만 표시(UX 검증 후 결정).

## 새 도구 추가 가이드

1. `Tool` 인터페이스 구현 시 `label(action)`에 한국어 라벨을 제공한다.
2. AppGraph의 도구 목록과 toolCatalog에 등록한다.
3. 이후에는 진행 안내/재시도/승인 안내가 자동으로 동작한다.
