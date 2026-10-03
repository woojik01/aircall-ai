package com.woojik.aircallai.tools

import com.woojik.aircallai.ai.provider.AIProvider
import com.woojik.aircallai.ai.provider.AIResponse
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.ai.provider.ProviderType

/**
 * PRD-06 Tool-AI 연결 + 결과 전달/검증 개선.
 *
 * 워크플로우 (AI -> 도구 -> AI -> 도구 ..., 한 턴에 최대 MAX_TOOL_ROUNDS회):
 * 1. 시스템 프롬프트에 Tool 사용 규칙(지시어 문법, 도구 목록, 결과 참조 문법)을 주입한다.
 * 2. AI 응답에 TOOL 지시어가 있으면 파싱해 ToolExecutor로 실행한다.
 *    이때 인자 값의 {{TOOL_RESULT}} 플레이스홀더를 이전 성공 결과의 실제 내용으로 치환한다.
 *    (README처럼 여러 줄·큰 내용은 지시어 한 줄에 못 넣으므로 이 참조 문법으로 전달한다.)
 * 3. 실행 결과(성공/실패/차단)를 TOOL_RESULT로 대화 이력에 추가하고 다시 AI에게 응답을 요청한다.
 *    - 실패해도 루프를 끝내지 않고 결과를 AI에게 돌려준다(AI가 인자를 고쳐 재시도할 수 있다).
 *    - 승인이 필요한 WRITE 차단은 승인 다이얼로그 요청 후 턴을 끝낸다.
 * 4. 최종 답변(지시어 없는 응답)을 내기 전에 검증한다:
 *    이번 턴에 실패한 도구 호출이 있는데 AI가 완료처럼 답하면, 실패 사실을 명시한
 *    검증 프롬프트로 한 번 더 응답을 요청해 정정한다(VERIFY_PASSES회 제한).
 * 5. 라운드를 초과하면 마지막 응답을 그대로 돌려준다.
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
        var verifyPass = 0
        var current = requestHistory
        // 이번 턴의 성공한 결과(플레이스홀더 치환에 쓰인다)와 실패한 호출 목록(검증에 쓰인다).
        var lastSuccessResult: String? = null
        val failedCalls = mutableListOf<String>()

        while (round < MAX_TOOL_ROUNDS) {
            val response = base.respond(current)
            val call = ToolCallParser.parseFirst(response.message.content)

            if (call == null) {
                // 지시어 없는 최종 답변: 검증 후 반환한다.
                val needsVerify = failedCalls.isNotEmpty() && verifyPass < VERIFY_PASSES
                if (!needsVerify) return response
                verifyPass++
                current = current + response.message + ChatMessage(
                    ChatMessage.Role.USER,
                    verificationPrompt(failedCalls.toList()),
                )
                continue
            }

            round++

            // {{TOOL_RESULT}} 플레이스홀더를 이전 성공 결과의 실제 내용으로 치환한다.
            val request = ToolRequest(call.toolName, call.action, substitute(call.arguments, lastSuccessResult))
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

            if (result.success) {
                lastSuccessResult = result.message
            } else {
                failedCalls += (call.toolName + "." + call.action + " — " + result.message)
            }

            // 성공/실패 모두 AI에게 돌려준다. AI가 실패 원인을 보고 재시도하거나 안내할 수 있다.
            current = current + response.message +
                ChatMessage(ChatMessage.Role.USER, toolResultPrompt(call, result, lastSuccessResult))
        }
        // 라운드 제한 초과: 마지막 응답을 그대로 돌려준다.
        return base.respond(current)
    }

    /**
     * 인자 값의 {{TOOL_RESULT}}(와 {{TOOL_RESULT:n}} — n번째 이전 성공 결과는 미지원, 단일 최근 결과만)을
     * 직전 성공 결과의 실제 내용으로 바꾼다. 치환 결과가 없으면(이전 성공 결과 없음)
     * 플레이스홀더를 그대로 두지 말고 명확한 오류 문구로 대체해 도구가 잘못된 값을 쓰지 않게 한다.
     */
    private fun substitute(
        arguments: Map<String, String>,
        lastSuccessResult: String?,
    ): Map<String, String> {
        if (arguments.values.none { it.contains(RESULT_PLACEHOLDER) }) return arguments
        return arguments.mapValues { (_, value) ->
            if (!value.contains(RESULT_PLACEHOLDER)) value
            else if (lastSuccessResult != null) value.replace(RESULT_PLACEHOLDER, lastSuccessResult)
            else value.replace(RESULT_PLACEHOLDER, "(참조할 이전 Tool 결과가 없습니다)")
        }
    }

    private fun injectSystemPrompt(history: List<ChatMessage>): List<ChatMessage> {
        val prompt = ChatMessage(ChatMessage.Role.SYSTEM, toolSystemPrompt())
        return listOf(prompt) + history
    }

    private fun toolSystemPrompt(): String =
        "외부 기능(Tool)을 사용할 수 있다.\n" +
            "사용 가능한 도구:\n" + toolsDescription + "\n" +
            "Tool 사용 규칙:\n" +
            "1. 지시어는 한 번에 하나씩, 순서대로 사용한다. 여러 도구가 필요하면(예: GitHub 파일 읽기 후 Gmail 발송) " +
            "첫 도구 결과를 받은 뒤 다음 도구를 호출한다.\n" +
            "2. 지시어 앞에 지금 무엇을 하는지 한 문장으로 설명한다.\n" +
            "3. 이전 Tool 실행 결과의 내용을 그대로 인자로 넘길 때는 {{TOOL_RESULT}}를 쓴다. " +
            "예: TOOL: gmail.send_email to=a@b.com subject=\"안녕\" body={{TOOL_RESULT}}\n" +
            "   파일 내용처럼 길거나 여러 줄인 값을 직접 쓰지 말고 반드시 {{TOOL_RESULT}}로 참조한다.\n" +
            "4. 지시어 형식: TOOL: <도구>.<액션> key=value key2=\"값에 공백\"\n" +
            "5. 결과를 받은 뒤 자연스럽게 다음 행동이나 최종 답변을 한다. " +
            "완료했다고 말하기 전에 해당 Tool의 성공 결과가 반드시 있어야 한다.\n" +
            "6. Tool이 실패하면 실패 원인을 보고 인자를 고쳐 다시 시도하거나, 실패했다고 정직하게 알린다. " +
            "실패한 작업을 완료했다고 말하지 않는다.\n" +
            "사용자가 Tool 사용을 요청하지 않았으면 지시어 없이 평범하게 답한다."

    private fun toolResultPrompt(call: ToolCallParser.ToolCall, result: ToolResult, lastSuccessResult: String?): String {
        val summary = if (result.message.length > RESULT_SUMMARY_LIMIT) {
            result.message.take(RESULT_SUMMARY_LIMIT) + "...(이후 생략, 전체 내용은 {{TOOL_RESULT}}로 참조할 수 있습니다)"
        } else {
            result.message
        }
        return "TOOL_RESULT " + call.toolName + "." + call.action + " " +
            (if (result.success) "성공: " else "실패: ") + summary +
            ". 이 결과를 반영해 다음 행동이나 최종 답변을 한다. " +
            "결과 내용을 다른 도구에 넘길 때는 body={{TOOL_RESULT}} 형식으로 참조한다."
    }

    /** 검증 프롬프트: 실패한 호출 목록을 명시하고 정직한 답변을 요구한다. */
    private fun verificationPrompt(failedCalls: List<String>): String =
        "검증: 이번 요청에서 다음 Tool 호출이 실패했고 아직 성공하지 못했다:\n" +
            failedCalls.joinToString("\n") { "- " + it } + "\n" +
            "실패한 작업을 완료했다고 절대 말하지 말고, 인자를 고쳐 다시 시도하거나(TOOL 지시어 사용) " +
            "또는 실패 원인과 함께 사용자에게 상황을 정확히 설명한다."

    companion object {
        private const val MAX_TOOL_ROUNDS = 6
        private const val VERIFY_PASSES = 1
        private const val RESULT_PLACEHOLDER = "{{TOOL_RESULT}}"
        private const val RESULT_SUMMARY_LIMIT = 4000
        const val BLOCKED_MESSAGE = "사용자 승인이 필요한 작업입니다"
    }
}
