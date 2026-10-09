package com.woojik.aircallai.tools

enum class ToolRisk { READ, WRITE, DESTRUCTIVE }

data class ToolRequest(val toolName: String, val action: String, val arguments: Map<String, String> = emptyMap())
data class ToolResult(val success: Boolean, val message: String, val url: String? = null)

interface Tool {
    val name: String
    val description: String
    fun riskFor(action: String): ToolRisk
    suspend fun execute(request: ToolRequest): ToolResult
}
