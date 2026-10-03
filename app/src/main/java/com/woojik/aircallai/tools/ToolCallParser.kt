package com.woojik.aircallai.tools

import org.json.JSONObject

/**
 * AI 응답에서 구조화된 Tool 호출을 파싱한다.
 *
 * 권장 형식:
 * TOOL_CALL: {"tool":"github","action":"read_repository","arguments":{"owner":"woojik01","repo":"aircall-ai"}}
 *
 * 마이그레이션 동안 기존 TOOL: <tool>.<action> key=value 형식도 fallback으로 지원한다.
 */
object ToolCallParser {

    data class ToolCall(val toolName: String, val action: String, val arguments: Map<String, String>)

    const val JSON_MARKER = "TOOL_CALL:"
    const val LEGACY_MARKER = "TOOL:"

    fun parseFirst(response: String): ToolCall? =
        parseStructured(response) ?: parseLegacy(response)

    private fun parseStructured(response: String): ToolCall? {
        val markerIndex = response.indexOf(JSON_MARKER)
        if (markerIndex < 0) return null
        val jsonStart = response.indexOf('{', markerIndex + JSON_MARKER.length)
        if (jsonStart < 0) return null
        val jsonText = extractJsonObject(response, jsonStart) ?: return null
        return runCatching {
            val root = JSONObject(jsonText)
            val tool = root.optString("tool").trim()
            val action = root.optString("action").trim()
            if (tool.isBlank() || action.isBlank()) return@runCatching null
            val argsObject = root.optJSONObject("arguments") ?: JSONObject()
            val args = linkedMapOf<String, String>()
            val keys = argsObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = argsObject.opt(key)
                args[key] = when (value) {
                    null, JSONObject.NULL -> ""
                    is String -> value
                    else -> value.toString()
                }
            }
            ToolCall(tool, action, args)
        }.getOrNull()
    }

    /** 문자열 내부의 중괄호/escape를 무시하면서 첫 JSON object의 끝을 찾는다. */
    private fun extractJsonObject(text: String, start: Int): String? {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val ch = text[i]
            if (inString) {
                if (escaped) escaped = false
                else if (ch == '\\') escaped = true
                else if (ch == '"') inString = false
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                    if (depth < 0) return null
                }
            }
        }
        return null
    }

    private fun parseLegacy(response: String): ToolCall? {
        val line = response.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith(LEGACY_MARKER) }
            ?: return null
        val body = line.removePrefix(LEGACY_MARKER).trim()
        if (body.isEmpty()) return null
        val tokens = tokenize(body)
        if (tokens.isEmpty()) return null
        val qualified = tokens[0]
        val dot = qualified.indexOf('.')
        if (dot <= 0 || dot == qualified.length - 1) return null
        val arguments = mutableMapOf<String, String>()
        var i = 1
        while (i < tokens.size) {
            val token = tokens[i]
            val eq = token.indexOf('=')
            if (eq > 0) {
                val key = token.substring(0, eq)
                val rest = token.substring(eq + 1)
                if (rest.startsWith("\"")) {
                    val joined = StringBuilder(rest)
                    while (!joined.toString().endsWith("\"") && i + 1 < tokens.size) {
                        i++
                        joined.append(' ').append(tokens[i])
                    }
                    arguments[key] = joined.toString().removeSurrounding("\"")
                } else arguments[key] = rest
            }
            i++
        }
        return ToolCall(qualified.substring(0, dot), qualified.substring(dot + 1), arguments)
    }

    private fun tokenize(body: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inQuote = false
        body.forEach { ch ->
            when {
                ch == '"' -> { inQuote = !inQuote; current.append(ch) }
                ch == ' ' && !inQuote -> {
                    if (current.isNotEmpty()) { tokens.add(current.toString()); current.setLength(0) }
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) tokens.add(current.toString())
        return tokens
    }
}
