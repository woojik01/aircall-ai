package com.woojik.aircallai.tools

/** Strict single-call parser. Incomplete or ambiguous writes are never partially executed. */
object ToolCallParser {
    data class ToolCall(val toolName: String, val action: String, val arguments: Map<String, String>)
    const val MARKER = "TOOL:"
    private val identifier = Regex("[a-zA-Z_][a-zA-Z0-9_]*")

    fun hasDirective(response: String): Boolean = response.lineSequence().any { it.trim().startsWith(MARKER) }

    fun parseFirst(response: String): ToolCall? {
        val lines = response.lineSequence().map { it.trim() }.filter { it.startsWith(MARKER) }.toList()
        if (lines.size != 1 || lines.single().length > 65_536) return null
        val body = lines.single().removePrefix(MARKER).trim()
        val qualified = body.takeWhile { !it.isWhitespace() }
        val parts = qualified.split('.')
        if (parts.size != 2 || parts.any { !identifier.matches(it) }) return null
        var position = qualified.length
        val arguments = linkedMapOf<String, String>()
        while (position < body.length) {
            while (position < body.length && body[position].isWhitespace()) position++
            if (position == body.length) break
            val keyStart = position
            while (position < body.length && body[position] != '=' && !body[position].isWhitespace()) position++
            val key = body.substring(keyStart, position)
            if (!identifier.matches(key) || key in arguments || position == body.length || body[position] != '=') return null
            position++
            if (position == body.length) return null
            val value = StringBuilder()
            if (body[position] == '"') {
                position++
                var closed = false
                while (position < body.length) {
                    val ch = body[position++]
                    if (ch == '"') { closed = true; break }
                    if (ch == '\\' && position < body.length && (body[position] == '"' || body[position] == '\\')) {
                        value.append(body[position++])
                    } else value.append(ch)
                }
                if (!closed || (position < body.length && !body[position].isWhitespace())) return null
            } else {
                while (position < body.length && !body[position].isWhitespace()) {
                    if (body[position] == '"') return null
                    value.append(body[position++])
                }
                if (value.isEmpty()) return null
            }
            arguments[key] = value.toString()
        }
        return ToolCall(parts[0], parts[1], arguments)
    }
}
