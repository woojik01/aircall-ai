package com.woojik.aircallai.tools

class ToolExecutor(
    tools: List<Tool>,
    private val permissions: ToolPermissionStore,
    private val onEvent: (ToolExecutionEvent) -> Unit = {},
    private val roomId: () -> String? = { null },
) {
    private val toolsByName = tools.associateBy { it.name }

    suspend fun execute(request: ToolRequest): ToolResult {
        val tool = toolsByName[request.toolName] ?: return ToolResult(false, "알 수 없는 도구입니다")
        val risk = tool.riskFor(request.action)
        if (!permissions.isAllowed(tool.name, request.action, risk)) {
            return ToolResult(false, "사용자 승인이 필요한 작업입니다")
        }
        val event = ToolExecutionEvent(toolName = tool.name, action = request.action, roomId = roomId())
        emit(event)
        val result = try { tool.execute(request) }
        catch (e: kotlinx.coroutines.CancellationException) {
            emit(event.copy(status = ToolExecutionStatus.CANCELLED))
            throw e
        }
        catch (_: Exception) { ToolResult(false, "도구 실행에 실패했습니다") }
        emit(event.copy(status = if (result.success) ToolExecutionStatus.SUCCEEDED else ToolExecutionStatus.FAILED))
        return result
    }

    // A disabled notification or failed UI observer must never retry a completed write.
    private fun emit(event: ToolExecutionEvent) { runCatching { onEvent(event) } }
}
