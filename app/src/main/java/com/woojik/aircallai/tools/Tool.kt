package com.woojik.aircallai.tools

enum class ToolRisk { READ, WRITE, DESTRUCTIVE }

data class ToolRequest(val toolName: String, val action: String, val arguments: Map<String, String> = emptyMap())
data class ToolResult(val success: Boolean, val message: String)

/** AI에게 알려줄 작업 설명. `arguments`는 필수 인자, `optionalArguments`는 선택 인자. */
data class ToolActionSpec(
    val action: String,
    val description: String,
    val arguments: List<String> = emptyList(),
    val optionalArguments: List<String> = emptyList(),
)

interface Tool {
    val name: String
    val description: String
    val actions: List<ToolActionSpec> get() = emptyList()
    fun riskFor(action: String): ToolRisk
    suspend fun execute(request: ToolRequest): ToolResult
}
