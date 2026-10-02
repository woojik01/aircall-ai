package com.woojik.aircallai.tools

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PRD-06 Notes Adapter: 기기에 로컬로 저장되는 간단한 메모 저장소.
 * 네트워크를 사용하지 않는다. JVM 단위 테스트에서 그대로 사용할 수 있도록
 * 순수 java.io 기반으로 구현한다.
 */
interface NotesStore {
    suspend fun append(text: String)
    suspend fun search(query: String): List<String>
    suspend fun recent(limit: Int): List<String>
}

/** 파일 기반 구현. 한 줄이 하나의 메모(줄바꿈은 공백으로 정규화). */
class FileNotesStore(private val file: File) : NotesStore {

    override suspend fun append(text: String) = withContext(Dispatchers.IO) {
        val normalized = text.replace(NEWLINE, " ").trim()
        if (normalized.isEmpty()) return@withContext
        file.parentFile?.mkdirs()
        file.appendText(System.currentTimeMillis().toString() + " " + normalized + System.lineSeparator())
    }

    override suspend fun search(query: String): List<String> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        readLines().filter { it.contains(q, ignoreCase = true) }
    }

    override suspend fun recent(limit: Int): List<String> = withContext(Dispatchers.IO) {
        readLines().takeLast(limit.coerceIn(1, MAX_LIST))
    }

    private fun readLines(): List<String> =
        if (file.exists()) file.readLines().filter { it.isNotBlank() } else emptyList()

    companion object {
        private val NEWLINE = Regex("[\\r\\n]+")
        private const val MAX_LIST = 50
    }
}

/**
 * PRD-06 Notes Tool.
 * - add_note: WRITE (승인 필요)
 * - search_notes / list_notes: READ (기본 허용)
 */
class NotesTool(private val store: NotesStore) : Tool {
    override val name = "notes"
    override val description = "기기에 로컬로 저장되는 메모를 추가하거나 찾는 도구"

    override fun riskFor(action: String) = when (action) {
        "search_notes", "list_notes" -> ToolRisk.READ
        else -> ToolRisk.WRITE
    }

    override suspend fun execute(request: ToolRequest): ToolResult {
        return when (request.action) {
            "add_note" -> {
                val text = request.arguments["text"]?.trim().orEmpty()
                if (text.isEmpty()) return ToolResult(false, "메모 내용(text)이 필요합니다")
                store.append(text)
                ToolResult(true, "메모를 저장했습니다")
            }
            "search_notes" -> {
                val query = request.arguments["query"]?.trim().orEmpty()
                if (query.isEmpty()) return ToolResult(false, "검색어(query)가 필요합니다")
                val found = store.search(query)
                if (found.isEmpty()) ToolResult(true, "일치하는 메모가 없습니다") else ToolResult(true, found.joinToString(" | "))
            }
            "list_notes" -> {
                val limit = request.arguments["limit"]?.toIntOrNull() ?: 10
                val notes = store.recent(limit)
                if (notes.isEmpty()) ToolResult(true, "저장된 메모가 없습니다") else ToolResult(true, notes.joinToString(" | "))
            }
            else -> ToolResult(false, "지원하지 않는 Notes 작업입니다")
        }
    }
}
