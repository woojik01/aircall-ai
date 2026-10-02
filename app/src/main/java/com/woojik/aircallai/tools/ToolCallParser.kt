package com.woojik.aircallai.tools

/**
 * PRD-06 Tool-AI 연결: AI 응답 텍스트에서 Tool 호출 지시어를 파싱한다.
 * 형식 (한 줄):
 *   TOOL: github.read_repository owner=woojik01 repo=aircall-ai
 * 값에 공백이 필요하면 큰따옴표로 감싼다:
 *   TOOL: github.create_issue owner=o repo=r title="버그: 음성 인식 안 됨"
 * 한 응답에 여러 지시어가 있으면 첫 번째만 실행한다(순차 실행은 후속 증분).
 */
object ToolCallParser {

    data class ToolCall(val toolName: String, val action: String, val arguments: Map<String, String>)

    const val MARKER = "TOOL:"

    /** 응답에서 첫 Tool 지시어를 파싱한다. 없으면 null. */
    fun parseFirst(response: String): ToolCall? {
        val line = response.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith(MARKER) }
            ?: return null
        val body = line.removePrefix(MARKER).trim()
        if (body.isEmpty()) return null

        val tokens = tokenize(body)
        if (tokens.isEmpty()) return null
        val qualified = tokens[0]
        val dot = qualified.indexOf('.')
        if (dot <= 0 || dot == qualified.length - 1) return null
        val toolName = qualified.substring(0, dot)
        val action = qualified.substring(dot + 1)
        val arguments = mutableMapOf<String, String>()
        var i = 1
        while (i < tokens.size) {
            val token = tokens[i]
            val eq = token.indexOf('=')
            if (eq > 0) {
                val key = token.substring(0, eq)
                // 값이 따옴표로 시작하면 닫는 따옴표까지 하나의 값으로 묶는다.
                val rest = token.substring(eq + 1)
                if (rest.startsWith("\"")) {
                    val joined = StringBuilder(rest)
                    while (!joined.toString().endsWith("\"") && i + 1 < tokens.size) {
                        i++
                        joined.append(' ').append(tokens[i])
                    }
                    arguments[key] = joined.toString().removeSurrounding("\"")
                } else {
                    arguments[key] = rest
                }
            }
            i++
        }
     
   return ToolCall(toolName, action, arguments)
    }

    private fun tokenize(body: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inQuote = false
        body.forEach { ch ->
            when {
                ch == '"' -> {
                    inQuote = !inQuote
                    current.append(ch)
                }
                ch == ' ' && !inQuote -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.setLength(0)
                    }
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) tokens.add(current.toString())
        return tokens
    }
}
