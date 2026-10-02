package com.woojik.aircallai.tools

import com.woojik.aircallai.ai.provider.AIProvider
import com.woojik.aircallai.ai.provider.AIResponse
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.ai.provider.ProviderType

/**
 * PRD-06 Tool-AI 연결: 어떤 AIProvider든 감싸서 Tool 호출을 가능하게 하는 어댑터.
 *
 * 흐름:
 * 1. 시스템 프롬프트에 Tool 사용 규칙(지시어 문법, 사용 가능한 도구/액션 목록)을 주입한다.
 * 2. AI 응답에 "TOOL:" 지시어가 있으면 ToolCallParser로 파싱해 ToolExecutor로 실행한다.
 * 3. 실행 결과(성공/실패/차단)를 대화 이력에 TOOL_RESULT로 추가하고 다시 응답을 요청한다.
 *    (최대 MAX_TOOL_ROUNDS회. 무한 루프 방지)
 * 4. WRITE 작업이 승인 전이라 차단되면, 승인 코디네이터에 요청을 올려
 *    UI가 승인 다이얼로그를 띄우도록 하고, 사용자에게 승인 필요 안내 응답을 생성한다.
 */
class ToolBridgedAIProvider(
    private val base: AIProvider,
    private val executor: ToolExecutor,
    private val logger: ToolExecutionLogger,
    private val toolsDescription: String,
    private val tools: List<Tool> = emptyList(),
    private val approvalRequester: ((ToolRequest) -> Unit)? = null,
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

    private fun injectSystemPrompt(history: List<ChatMessage>): List<ChatMessage> {
        val prompt = ChatMessage(ChatMessage.Role.SYSTEM, toolSystemPrompt())
        return listOf(prompt) + history
    }

    private fun toolSystemPrompt(): String =
        "외부 기능(Tool)을 사용할 수 있다.\n" +
            "사용 가능한 도구:\n" + toolsDescription + "\n" +
            "Tool을 사용하려면 응답의 첫 줄에 아래 형식으로 지시어를 쓴다(한 번에 하나):\n" +
            "TOOL: <도구>.<액션> key=value key2=\"값에 공백\"\n" +
            "지시어를 쓰면 시스템이 실행하고 결과를 알려준다. 결과를 받은 뒤 자연스럽게 최종 답변한다.\n" +
            "사용자가 Tool 사용을 요청하지 않았으면 지시어 없이 평범하게 답한다."

    private fun toolResultPrompt(call: ToolCallParser.ToolCall, result: ToolResult): String =
        "TOOL_RESULT " + call.toolName + "." + call.action + " " +
            (if (result.success) "성공: " else "실패: ") + result.message +
            ". 이 결과를 반영해 사용자에게 자연스럽게 답한다."

    companion object {
        private const val MAX_TOOL_ROUNDS = 2
        const val BLOCKED_MESSAGE = "사용자 승인이 필요한 작업입니다"
    }
}
