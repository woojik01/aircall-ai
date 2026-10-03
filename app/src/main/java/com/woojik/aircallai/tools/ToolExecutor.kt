package com.woojik.aircallai.tools

/**
 * Tool 실행 계층.
 *
 * 진행 내레이션 개선:
 * - 실행 전후와 재시도 시 ToolActivityBus로 이벤트를 방송해 UI/음성이 실시간 안내를 할 수 있다.
 * - READ 작업은 일시적 오류(네트워크 등) 가능성이 있으므로 1회 자동 재시도한다.
 *   WRITE 작업은 재실행 부작용(중복 발송 등) 때문에 자동 재시도하지 않는다.
 * - WRITE 재시도 판단은 AI가 TOOL_RESULT를 보고 스스로 하도록 위임한다.
 */
class ToolExecutor(
    tools: List<Tool>,
    private val permissions: ToolPermissionStore,
    private val activityBus: ToolActivityBus? = null,
) {
    private val toolsByName = tools.associateBy { it.name }

    suspend fun execute(request: ToolRequest): ToolResult = executeWithRetry(request)

    suspend fun executeWithRetry(request: ToolRequest, maxAttempts: Int = 2): ToolResult {
        val tool = toolsByName[request.toolName] ?: return ToolResult(false, "알 수 없는 도구입니다")
        val risk = tool.riskFor(request.action)
        val label = tool.label(request.action)

        if (!permissions.isAllowed(tool.name, request.action, risk)) {
            activityBus?.emit(ToolActivityEvent.ApprovalRequired(request, label))
            return ToolResult(false, "사용자 승인이 필요한 작업입니다")
        }

        activityBus?.emit(ToolActivityEvent.Started(request, label))
        var result = runCatching { tool.execute(request) }
            .getOrElse { ToolResult(false, "도구 실행에 실패했습니다") }
        var attempt = 1

        // READ 작업만 자동 재시도한다(WRITE 중복 실행 방지).
        while (!result.success && risk == ToolRisk.READ && attempt < maxAttempts) {
            attempt++
            activityBus?.emit(ToolActivityEvent.Retrying(request, label, attempt))
            result = runCatching { tool.execute(request) }
                .getOrElse { ToolResult(false, "도구 실행에 실패했습니다") }
        }

        if (result.success) {
            activityBus?.emit(ToolActivityEvent.Succeeded(request, label))
        } else {
            activityBus?.emit(ToolActivityEvent.Failed(request, label))
        }
        return result
    }
}
