package com.woojik.aircallai

import com.woojik.aircallai.tools.FileNotesStore
import com.woojik.aircallai.tools.InMemoryToolPermissionStore
import com.woojik.aircallai.tools.NotesTool
import com.woojik.aircallai.tools.ToolExecutor
import com.woojik.aircallai.tools.ToolRequest
import com.woojik.aircallai.tools.ToolRisk
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD-06 Notes Adapter: 메모 추가/검색/목록과 승인 계층 차단을 검증한다.
 * FileNotesStore는 순수 java.io 기반이라 JVM 테스트에서 그대로 사용한다.
 */
class NotesToolTest {

    private fun store(): FileNotesStore = FileNotesStore(File.createTempFile("notes", ".txt"))

    private fun tool(notes: FileNotesStore = store()) = NotesTool(notes)

    @Test
    fun riskClassification() {
        val t = tool()
        assertEquals(ToolRisk.READ, t.riskFor("search_notes"))
        assertEquals(ToolRisk.READ, t.riskFor("list_notes"))
        assertEquals(ToolRisk.WRITE, t.riskFor("add_note"))
        assertEquals(ToolRisk.WRITE, t.riskFor("unknown"))
    }

    @Test
    fun addAndSearchNotes() = runTest {
        val notes = store()
        val t = tool(notes)
        assertTrue(t.execute(ToolRequest("notes", "add_note", mapOf("text" to "우유 사기"))).success)
        assertTrue(t.execute(ToolRequest("notes", "add_note", mapOf("text" to "PRD-06 어댑터 리뷰"))).success)
        val found = t.execute(ToolRequest("notes", "search_notes", mapOf("query" to "우유")))
        assertTrue(found.success)
        assertTrue(found.message.contains("우유 사기"))
        assertFalse(found.message.contains("PRD-06"))
    }

    @Test
    fun listNotesAndEmptyStates() = runTest {
        val t = tool()
        val empty = t.execute(ToolRequest("notes", "list_notes"))
        assertTrue(empty.success)
        assertEquals("저장된 메모가 없습니다", empty.message)
        assertTrue(t.execute(ToolRequest("notes", "add_note", mapOf("text" to "hello"))).success)
        assertTrue(t.execute(ToolRequest("notes", "list_notes")).message.contains("hello"))
    }

    @Test
    fun addNoteRequiresText() = runTest {
        val result = tool().execute(ToolRequest("notes", "add_note"))
        assertFalse(result.success)
    }

    @Test
    fun unapprovedAddNoteIsBlockedByExecutor() = runTest {
        val executor = ToolExecutor(listOf(tool()), InMemoryToolPermissionStore())
        val blocked = executor.execute(ToolRequest("notes", "add_note", mapOf("text" to "x")))
        assertFalse(blocked.success)
    }

    @Test
    fun approvedAddNotePassesApprovalLayer() = runTest {
        val notes = store()
        val permissions = InMemoryToolPermissionStore()
        permissions.setAllowed("notes", "add_note", true)
        val executor = ToolExecutor(listOf(NotesTool(notes)), permissions)
        assertTrue(executor.execute(ToolRequest("notes", "add_note", mapOf("text" to "승인된 메모"))).success)
    }
}
