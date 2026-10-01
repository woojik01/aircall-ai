package com.woojik.aircallai.tools

/**
 * PRD-06: 모델 종류(OpenAI 호환 Cloud, Local)와 무관하게 동작하는 텍스트 기반 Tool 호출 규약.
 *
 * ```
 * TOOL: github.create_issue
 * owner: woojik01
 * repo: aircall-ai
 * title: 제목
 * body: 여러 줄
 * 본문 가능
 * ```
 */
object ToolCallProtocol {
    private val HEADER = Regex("^\\s*TOOL\\s*:\\s*([A-Za-z0-9_-]+)\\.([A-Za-z0-9_-]+)\\s*$")
    private val ARGUMENT = Regex("^([A-Za-z_]+)\\s*:\\s?(.*)$")

    fun parse(text: String): ToolRequest? {
        val lines = text.lines().filterNot { it.trim().startsWith("```") }
        val start = lines.indexOfFirst { HEADER.matches(it) }
        if (start < 0) return null
        val header = HEADER.find(lines[start])!!.groupValues
        val args = linkedMapOf<String, String>()
        var lastKey: String? = null
        for (line in lines.drop(start + 1)) {
            val match = ARGUMENT.find(line)
            if (match != null) {
                lastKey = match.groupValues[1].lowercase()
                args[lastKey] = match.groupValues[2].trim()
            } else if (lastKey != null) {
                args[lastKey] = (args.getValue(lastKey) + "\n" + line).trimEnd()
            }
        }
        return ToolRequest(header[1], header[2], args.filterValues { it.isNotBlank() })
    }

    fun systemPrompt(tools: List<Tool>): String = buildString {
        appendLine("너는 아래 도구를 실제로 사용할 수 있다. 사용자가 이 기능을 요청하면 할 수 없다고 말하지 말고 도구를 호출한다.")
        appendLine("도구를 호출할 때는 다른 말 없이 다음 형식만 출력한다. 첫 줄은 TOOL: 도구.작업, 다음 줄부터 인자이름: 값.")
        appendLine("TOOL: github.read_repository")
        appendLine("owner: woojik01")
        appendLine("repo: aircall-ai")
        appendLine("사용 가능한 도구:")
        tools.forEach { tool ->
            tool.actions.forEach { spec ->
                append("- ").append(tool.name).append('.').append(spec.action).append(": ").append(spec.description)
                if (spec.arguments.isNotEmpty()) append(" / 필수 인자: ").append(spec.arguments.joinToString(", "))
                if (spec.optionalArguments.isNotEmpty()) append(" / 선택 인자: ").append(spec.optionalArguments.joinToString(", "))
                appendLine()
            }
        }
        appendLine("필수 인자를 모르면 도구를 호출하지 말고 사용자에게 짧게 물어본다. 저장소 소유자를 모르면 github.get_user로 로그인 사용자를 먼저 확인할 수 있다.")
        append("도구 결과를 받으면 그 결과만 근거로 짧게 답한다. 실패하면 실패 이유를 그대로 전한다. 결과를 지어내지 않는다.")
    }

    fun resultMessage(request: ToolRequest, result: ToolResult): String =
        "[도구 결과] ${request.toolName}.${request.action} " +
            (if (result.success) "성공" else "실패") + ": " + result.message +
            "\n이 결과를 바탕으로 사용자에게 답하라. 같은 도구를 다시 호출하지 마라."
}
