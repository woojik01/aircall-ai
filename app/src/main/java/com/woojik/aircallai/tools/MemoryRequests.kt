package com.woojik.aircallai.tools

/** Route only explicit, unambiguous requests; never infer facts or generate write arguments. */
object MemoryRequests {
    private val saveSuffix = Regex("(?s)^(.+?)\\s*(?:기억해\\s*줘|기억해주세요|기억해|기억해\\s*주세요|기억해\\s*둬)[.!。]*$")
    private val savePrefix = Regex("(?is)^(?:기억해\\s*줘|기억해\\s*주세요|기억해주세요|기억해|remember)\\s*[:：]\\s*(.+)$")
    private val secret = Regex("(?i)비밀번호|패스워드|api[ _-]*key|액세스.?토큰|access.?token|password|private.?key")
    private val history = Regex("(?:채팅|대화).{0,12}(?:기록|내역)|(?:이전|다른|지난|예전).{0,12}(?:채팅|대화)|chat history|previous chats", RegexOption.IGNORE_CASE)
    private val stableFact = Regex("^(?:(?:내|제)\\s*이름(?:은|는)\\s*.+|(?:나는|저는|난|전)\\s+.+\\s*(?:좋아해|좋아해요|좋아합니다|싫어해|싫어해요))[.!。]*$")
    private val questionOrAction = Regex("[?？]|뭐|무엇|누구|삭제|지워|잊어|수정|변경|보내")
    private val list = Regex("^(?:저장된\\s*)?기억(?:을|들|들이|들은|는)?\\s*(?:목록|보여\\s*줘|알려\\s*줘|조회|전부|모두).*$")

    fun initialCall(text: String): ToolCallParser.ToolCall? {
        val input = text.trim()
        val saved = (savePrefix.matchEntire(input) ?: saveSuffix.matchEntire(input))?.groupValues?.get(1)?.trim()
        if (saved != null && saved !in setOf("이걸", "그걸", "이것을", "그것을") && !secret.containsMatchIn(saved)) {
            return if (MemoryAssignment.parse(saved) != null)
                ToolCallParser.ToolCall("memory", "set", mapOf("assignment" to saved))
            else ToolCallParser.ToolCall("memory", "add", mapOf("text" to saved))
        }
        if (stableFact.matches(input) && !secret.containsMatchIn(input) && !questionOrAction.containsMatchIn(input))
            return ToolCallParser.ToolCall("memory", "add", mapOf("text" to input))
        if (Regex("^(?:내|제)\\s*이름.*(?:뭐|무엇|기억|알고|말해|알려)").containsMatchIn(input))
            return ToolCallParser.ToolCall("memory", "search", mapOf("query" to "이름", "limit" to "20"))
        if (history.containsMatchIn(input) && !Regex("삭제|지워|수정|변경").containsMatchIn(input)) {
            // Known topic words are only retrieval filters; no stored content is fabricated.
            val query = listOf("이름", "생일", "좋아하는", "학교", "게임").firstOrNull { input.contains(it) }
            return ToolCallParser.ToolCall("memory", "history", mapOf("limit" to "20") +
                if (query == null) emptyMap() else mapOf("query" to query))
        }
        if (list.matches(input)) return ToolCallParser.ToolCall("memory", "list", mapOf("limit" to "100"))
        return null
    }

    fun deniesAccess(text: String): Boolean = Regex(
        "(?:기억|채팅|대화|기록).{0,40}(?:볼 수 없|접근할 수 없|조회할 수 없|읽을 수 없|확인할 수 없|조회하지 못|접근하지 못)|모르겠|알 수 없|기억하지 못",
    ).containsMatchIn(text)
}
