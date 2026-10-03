package com.woojik.aircallai.tools

import com.woojik.aircallai.ai.provider.AIProvider
import com.woojik.aircallai.ai.provider.AIResponse
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.ai.provider.ProviderType

/**
 * PRD-06 Tool-AI 연결 + AI 워크플로우 진행 내레이션 개선.
 *
 * 흐름:
 * 1. 시스템 프롬프트에 Tool 사용 규칙(지시어 문법, 도구 목록, 진행 안내 규칙)을 주입한다.
 * 2. AI가 도구를 쓰기 전 지시어 앞에 쓴 자연어 문장을 '진행 안내'로 간주해
 *    ToolActivityBus로 실시간 방송한다. 예: "README.md 내용 확인 중..."
 * 3. TOOL 지시어를 파싱해 ToolExecutor로 실행하고, 실행 상태(시작/성공/재시도/실패/승인 필요)도 버스로 방송한다.
 * 4. 실행 결과를 TOOL_RESULT로 대화 이력에 추가하고 다시 응답을 요청한다.
 *    MAX_TOOL_ROUNDS까지 여러 도구를 연속으로 사용할 수 있다(예: GitHub 조회 후 Gmail 발송).
 * 5. WRITE 작업이 승인 전이라 차단되면 승인 코디네이터에 요청을 올리고 안내 응답을 생성한다.
 *
 * 유연성: 어떤 도구를 몇 번 어떤 순서로 쓸지는 AI가 대화 문맥에서 스스로 정한다.
 * 시스템은 이벤트 스트림으로 '무엇이 어떻게 진행되는지'만 실시간 알려준다.
 * 새 도구가 추가되면 도구 목록에 등록되는 것만으로 이 흐름에 자동 편입된다.
 */
class ToolBridgedAIProvider(
    private val base: AIProvider,
    private val executor: ToolExecutor,
    private val logger: ToolExecutionLogger,
    private val toolsDescription: String,
    private val tools: List<Tool> = emptyList(),
    private val approvalRequester: ((ToolRequest) -> Unit)? = null,
    private val activityBus: ToolActivityBus? = null,
) : AIProvider {

    override val type: ProviderType = base.type
    override val displayName: String = base.displayName

    override suspend fun isReady(): Boolean = base.isReady()

    override suspend fun respond(history: List<ChatMessage>): AIResponse {
        val requestHistory = injectSystemPrompt(history)
        val started = System.currentTimeMillis()

        var round = 0
        var current = requestHistory
        while (round < MAX_TOOL_ROUNDS) {
            val response = base.respond(current)
            val call = ToolCallParser.parseFirst(response.message.content) ?: return response
            round++

            // 진행 안내: 지시어 앞의 자연어를 실시간으로 방송한다.
            narrationBefore(response.message.content)?.let {
                activityBus?.emit(ToolActivityEvent.Narration(it))
            }

            val request = ToolRequest(call.toolName, call.action, call.arguments)
            val risk = tools.firstOrNull { it.name == call.toolName }?.riskFor(call.action) ?: ToolRisk.WRITE
            val toolStart = System.currentTimeMillis()
            val result = executor.execute(request)
            val blocked = result.message == BLOCKED_MESSAGE
            logger.record(
                ToolExecutionLogger.Entry(
                    toolName = call.toolName,
                    action = call.action,
                    risk = risk,
                    blocked = blocked,
                    success = result.success,
                    latencyMs = System.currentTimeMillis() - toolStart,
                    timestampMs = System.currentTimeMillis(),
                ),
            )

            if (blocked) {
                // 승인이 필요한 WRITE 작업: UI에 승인 다이얼로그를 띄우도록 요청을 올린다.
                approvalRequester?.invoke(request)
                val userMessage = ChatMessage(
                    ChatMessage.Role.ASSISTANT,
                    "이 작업은 승인이 필요합니다. 화면의 승인 다이얼로그에서 허용해 주세요.",
                )
                return AIResponse(
                    message = userMessage,
                    providerType = base.type,
                    latencyMs = System.currentTimeMillis() - started,
                )
            }

            current = current + response.message +
                ChatMessage(ChatMessage.Role.USER, toolResultPrompt(call, result))
        }
        // 라운드 제한 초과: 마지막 응답을 그대로 돌려준다.
        return base.respond(current)
    }

    /** TOOL 지시어 앞의 자연어 문장을 진행 안내로 추출한다. 없으면 null. */
    private fun narrationBefore(content: String): String? {
        val lines = content.lines()
        val markerIndex = lines.indexOfFirst { it.trim().startsWith(ToolCallParser.MARKER) }
        if (markerIndex <= 0) return null
        return lines.subList(0, markerIndex)
            .joinToString(" ")
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    private fun injectSystemPrompt(history: List<ChatMessage>): List<ChatMessage> {
        val prompt = ChatMessage(ChatMessage.Role.SYSTEM, toolSystemPrompt())
        return listOf(prompt) + history
    }

    private fun toolSystemPrompt(): String =
        "외부 기능(Tool)을 사용할 수 있다.\n" +
            "사용 가능한 도구:\n" + toolsDescription + "\n" +
            "Tool을 사용할 때는 아래 규칙을 지킨다:\n" +
            "1. 지시어 앞에 사용자에게 지금 무엇을 하는지 한 문장으로 먼저 설명한다. " +
            "예: README.md 내용 확인 중... / gmail 전송 중...\n" +
            "2. 다음 줄에 지시어를 쓴다(한 번에 하나):\n" +
            "TOOL: <도구>.<액션> key=value key2=\"값에 공백\"\n" +
            "3. 지시어를 쓰면 시스템이 실행하고 결과를 알려준다. 결과를 받은 뒤 자연스럽게 다음 행동이나 최종 답변을 한다.\n" +
            "4. 사용자의 요청에 여러 도구가 필요하면 도구를 하나씩 순서대로 사용한다. " +
            "예: GitHub에서 파일을 읽은 뒤 Gmail으로 전송한다.\n" +
            "5. 도구 실행이 실패하면 원인을 파악해 같은 도구를 인자를 고쳐 다시 시도할 수 있다. " +
            "재시도 전에 사용자에게 상황을 한 문장으로 설명한다.\n" +
            "사용자가 Tool 사용을 요청하지 않았으면 지시어 없이 평범하게 답한다."

    private fun toolResultPrompt(call: ToolCallParser.ToolCall, result: ToolResult): String =
        "TOOL_RESULT " + call.toolName + "." + call.action + " " +
            (if (result.success) "성공: " else "실패: ") + result.message +
            ". 이 결과를 반영해 사용자에게 자연스럽게 답한다. " +
            "실패한 경우 원인을 파악해 인자를 고쳐 다시 시도하거나, 다른 도구로 방법을 바꾸거나, " +
            "불가능한 이유를 사용자에게 설명한다."

    companion object {
        private const val MAX_TOOL_ROUNDS = 6
        const val BLOCKED_MESSAGE = "사용자 승인이 필요한 작업입니다"
    }
}
