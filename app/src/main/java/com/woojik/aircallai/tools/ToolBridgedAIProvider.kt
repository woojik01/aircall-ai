package com.woojik.aircallai.tools

import com.woojik.aircallai.ai.provider.AIProvider
import com.woojik.aircallai.ai.provider.AIResponse
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.ai.provider.ProviderType

/**
 * PRD-06 Tool-AI 연결 + 결과 전달/검증 + 도구 사용 의도 검증 개선.
 *
 * 워크플로우 (AI -> 도구 -> AI -> 도구 ..., 한 턴에 최대 MAX_TOOL_ROUNDS회):
 * 1. 시스템 프롬프트(ToolSystemPrompt)에 역할, 도구 목록, 지시어 문법,
 *    결과 참조({{TOOL_RESULT}}) 규칙, 결과 처리/정직성 규칙을 주입한다.
 * 2. AI 응답에 TOOL 지시어가 있으면 파싱해 ToolExecutor로 실행한다.
 *    이때 인자 값의 {{TOOL_RESULT}} 플레이스홀더를 직전 성공 결과의 실제 내용으로 치환한다.
 * 3. 실행 결과(성공/실패/차단)를 TOOL_RESULT로 대화 이력에 추가하고 다시 AI에게 응답을 요청한다.
 *    - 실패해도 루프를 끝내지 않고 결과를 AI에게 돌려준다(AI가 인자를 고쳐 재시도할 수 있다).
 *    - 승인이 필요한 WRITE 차단은 승인 다이얼로그 요청 후 턴을 끝낸다.
 * 4. 최종 답변(지시어 없는 응답)을 내기 전에 검증한다(정정 라운드 최대 VERIFY_PASSES회):
 *    a. 실패한 호출이 남아 있으면 실패 목록을 명시해 정정을 요청한다.
 *    b. 사용자 요청에 필요한 도구를 한 번도 성공적으로 실행하지 않았는데
 *       완료를 주장하거나 도구 없이 답하면, "Tool을 실제로 실행하거나 이유를 설명하라"는
 *       정정 프롬프트로 재응답을 요청한다. → 도구 미사용 거짓 완료를 시스템이 차단한다.
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

        // 이번 턴 사용자 요청에서 필요한 도구 후보(휴리스틱)와 추적 상태.
        val originalUserMessage = history.lastOrNull { it.role == ChatMessage.Role.USER }?.content.orEmpty()
        val requestedTools = ToolIntent.toolsRequestedIn(originalUserMessage)

        var round = 0
        var verifyPass = 0
        var current = requestHistory
        var lastSuccessResult: String? = null
        val failedCalls = mutableListOf<String>()
        val succeededTools = mutableSetOf<String>()

        while (round < MAX_TOOL_ROUNDS) {
            val response = base.respond(current)
            val call = ToolCallParser.parseFirst(response.message.content)

            if (call == null) {
                // 지시어 없는 최종 답변: 검증 후 반환한다.
                val unmetTools = requestedTools.filter { it !in succeededTools }
                val claimsWithoutEvidence = ToolIntent.claimsCompletion(response.message.content) &&
                    succeededTools.isEmpty() && requestedTools.isNotEmpty()
                val needsVerify = verifyPass < VERIFY_PASSES &&
                    (failedCalls.isNotEmpty() || unmetTools.isNotEmpty() || claimsWithoutEvidence)
                if (!needsVerify) return response
                verifyPass++
                current = current + response.message + ChatMessage(
                    ChatMessage.Role.USER,
                    verificationPrompt(failedCalls.toList(), unmetTools, claimsWithoutEvidence),
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
                succeededTools += call.toolName
            } else {
                failedCalls += (call.toolName + "." + call.action + " — " + result.message)
            }

            // 성공/실패 모두 AI에게 돌려준다. AI가 실패 원인을 보고 재시도하거나 안내할 수 있다.
            current = current + response.message +
                ChatMessage(ChatMessage.Role.USER, toolResultPrompt(call, result))
        }
        // 라운드 제한 초과: 마지막 응답을 그대로 돌려준다.
        return base.respond(current)
    }

    /**
     * 인자 값의 {{TOOL_RESULT}}를 직전 성공 결과의 실제 내용으로 바꾼다.
     * 치환 결과가 없으면(이전 성공 결과 없음) 플레이스홀더를 그대로 두지 말고
     * 명확한 오류 문구로 대체해 도구가 잘못된 값을 쓰지 않게 한다.
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

    /**
     * 정밀 시스템 프롬프트.
     * 구성: 역할 → 도구 목록 → 지시어 문법 → 결과 참조 → 결과 처리 → 정직성 → 일반 대화.
     * 각 규칙은 실행 시스템(ToolCallParser/치환/검증)의 실제 동작과 정확히 일치한다.
     */
    private fun toolSystemPrompt(): String {
        val sb = StringBuilder()
        sb.append("[역할]\n")
        sb.append("너는 사용자의 요청을 자연스러운 한국어로 처리하는 AI 어시스턴트다.\n")
        sb.append("필요할 때 아래 정의된 외부 기능(Tool)을 사용해 실제 작업을 수행한다.\n\n")

        sb.append("[사용 가능한 도구]\n")
        sb.append(toolsDescription)
        sb.append("\n\n")

        sb.append("[도구 사용 판단]\n")
        sb.append("사용자가 실제 작업(메일 발송, 저장소 읽기, Issue/PR 생성, 메모 저장, 일정 등록)을 요청하면 반드시 Tool을 사용한다.\n")
        sb.append("도구 없이 수행할 수 있는 실제 작업은 없다. 실행 없이 결과를 상상해 답하지 않는다.\n")
        sb.append("일반 질문·대화·지식 설명은 도구 없이 답한다.\n\n")

        sb.append("[Tool 지시어 문법]\n")
        sb.append("- 지시어는 한 줄 형식이며, 한 응답에 정확히 하나만 쓴다:\n")
        sb.append("  TOOL: <도구>.<액션> key=value key2=\"값에 공백\"\n")
        sb.append("- 지시어는 응답의 마지막에 쓰고, 그 앞에 지금 무엇을 하는지 한 문장으로 설명한다.\n")
        sb.append("- 인자 값에 줄바꿈을 넣지 않는다. 표기된 필수 인자를 빠짐없이 쓴다.\n")
        sb.append("- 사용자가 여러 작업을 요청했으면 한 지시어씩 순서대로 처리한다.\n\n")

        sb.append("[이전 Tool 결과 참조]\n")
        sb.append("- 직전에 성공한 Tool 결과의 내용을 인자로 넘길 때는 값 대신 {{TOOL_RESULT}}를 쓴다.\n")
        sb.append("  예: TOOL: gmail.send_email to=user@example.com subject=\"안내\" body={{TOOL_RESULT}}\n")
        sb.append("- {{TOOL_RESULT}}는 가장 최근에 성공한 Tool 결과 하나만 가리킨다.\n")
        sb.append("- 파일 내용처럼 길거나 여러 줄인 값을 인자에 직접 쓰지 않고 반드시 {{TOOL_RESULT}}로 참조한다.\n")
        sb.append("- 참조할 성공 결과가 없으면 {{TOOL_RESULT}}를 쓰지 않는다.\n\n")

        sb.append("[Tool 실행 결과 처리]\n")
        sb.append("- 지시어를 쓰면 시스템이 실행하고 TOOL_RESULT 메시지로 결과를 알려준다.\n")
        sb.append("- 성공: 결과를 반영해 다음 작업(추가 지시어)이나 최종 답변을 한다.\n")
        sb.append("- 실패: 실패 원인을 확인하고 인자를 고쳐 다시 시도한다. 같은 실패가 반복되면 재시도를 멈추고 실패 원인과 해결 방법(예: 계정 연결 필요)을 사용자에게 설명한다.\n")
        sb.append("- 승인 필요: 시스템이 승인 다이얼로그를 띄운다. 사용자에게 승인을 요청하는 문장으로 답하고 턴을 끝낸다.\n\n")

        sb.append("[정직성 규칙]\n")
        sb.append("- 작업을 완료했다고 말하려면 그 작업의 TOOL_RESULT가 성공이어야 한다.\n")
        sb.append("- Tool을 실행하지 않았거나 실패한 작업을 완료했다고 절대 말하지 않는다.\n")
        sb.append("- 요청을 수행하지 못했으면 무엇이 안 됐는지와 이유를 정확히 말한다.\n\n")

        sb.append("[일반 대화]\n")
        sb.append("- 사용자가 Tool 사용을 요청하지 않았으면 지시어 없이 평범하게 답한다.\n")
        sb.append("- 도구가 필요 없는 대화에서는 지시어를 만들어내지 않는다.")
        return sb.toString()
    }

    private fun toolResultPrompt(call: ToolCallParser.ToolCall, result: ToolResult): String {
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

    /**
     * 검증 프롬프트: 실패한 호출, 실행되지 않은 요청 도구, 근거 없는 완료 주장을
     * 명시하고 정직한 행동(Tool 실행 또는 이유 설명)을 요구한다.
     */
    private fun verificationPrompt(
        failedCalls: List<String>,
        unmetTools: List<String>,
        claimsWithoutEvidence: Boolean,
    ): String {
        val parts = mutableListOf<String>()
        if (failedCalls.isNotEmpty()) {
            parts += "이번 요청에서 다음 Tool 호출이 실패했고 아직 성공하지 못했다:\n" +
                failedCalls.joinToString("\n") { "- " + it }
        }
        if (unmetTools.isNotEmpty()) {
            parts += "사용자 요청에는 다음 Tool 사용이 필요하지만 이번 턴에 한 번도 성공적으로 실행되지 않았다: " +
                unmetTools.joinToString(", ") +
                ".\n작업을 수행하려면 반드시 TOOL 지시어로 실제로 실행한다."
        }
        if (claimsWithoutEvidence) {
            parts += "방금 응답은 작업 완료를 주장하지만 성공한 TOOL_RESULT가 하나도 없다. " +
                "Tool을 실행하지 않고 완료했다고 말하는 것은 거짓이다."
        }
        parts += "실패한 작업이나 수행하지 못한 작업을 완료했다고 절대 말하지 말고, " +
            "TOOL 지시어로 다시 시도하거나(TOOL: <도구>.<액션> ...), " +
            "수행할 수 없는 이유를 사용자에게 정확히 설명한다."
        return "검증:\n" + parts.joinToString("\n")
    }

    companion object {
        private const val MAX_TOOL_ROUNDS = 6
        private const val VERIFY_PASSES = 2
        private const val RESULT_PLACEHOLDER = "{{TOOL_RESULT}}"
        private const val RESULT_SUMMARY_LIMIT = 4000
        const val BLOCKED_MESSAGE = "사용자 승인이 필요한 작업입니다"
    }
}
