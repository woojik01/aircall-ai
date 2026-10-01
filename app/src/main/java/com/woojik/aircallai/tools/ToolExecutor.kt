package com.woojik.aircallai.tools

class ToolExecutor(tools: List<Tool>, private val permissions: ToolPermissionStore) {
    private val toolsByName = tools.associateBy { it.name }

    suspend fun execute(request: ToolRequest): ToolResult {
        val tool = toolsByName[request.toolName] ?: return ToolResult(false, "알 수 없는 도구입니다")
        if (!permissions.isAllowed(tool.name, request.action, tool.risk)) {
            return ToolResult(false, "사용자 승인이 필요한 작업입니다")
        }
        return runCatching { tool.execute(request) }
            .getOrElse { ToolResult(false, "도구 실행에 실패했습니다") }
    }
}
