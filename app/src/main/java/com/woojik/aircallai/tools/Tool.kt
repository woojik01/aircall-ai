package com.woojik.aircallai.tools

enum class ToolRisk { READ, WRITE, DESTRUCTIVE }

data class ToolRequest(val toolName: String, val action: String, val arguments: Map<String, String> = emptyMap())
data class ToolResult(val success: Boolean, val message: String)

interface Tool {
    val name: String
    val description: String
    fun riskFor(action: String): ToolRisk
    suspend fun execute(request: ToolRequest): ToolResult

    /**
     * 진행 내레이션에 쓰이는 사용자 친화적 이름(예: "Gmail 메일 전송").
     * 도구가 오버라이드하지 않으면 "tool.action" 형식으로 표시된다.
     * 새 도구는 이 값만 제공하면 진행 안내에 자동으로 편입된다.
     */
    fun label(action: String): String = name + "." + action
}
