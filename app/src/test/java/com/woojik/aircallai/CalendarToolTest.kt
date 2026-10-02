package com.woojik.aircallai

import com.woojik.aircallai.tools.CalendarAdapter
import com.woojik.aircallai.tools.CalendarTimeParser
import com.woojik.aircallai.tools.CalendarTool
import com.woojik.aircallai.tools.InMemoryToolPermissionStore
import com.woojik.aircallai.tools.ToolExecutor
import com.woojik.aircallai.tools.ToolRequest
import com.woojik.aircallai.tools.ToolResult
import com.woojik.aircallai.tools.ToolRisk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD-06 Calendar Adapter: 시각 파싱, 일정 조회/등록, 승인 계층 차단을 검증한다.
 * CalendarAdapter는 fake로 대체한다(Android ContentResolver는 JVM에서 사용 불가).
 */
class CalendarToolTest {

    private class FakeCalendarAdapter : CalendarAdapter {
        val created = mutableListOf<Pair<String, Long>>()
        override suspend fun upcomingEvents(limit: Int): List<String> = listOf("회의 (2026-10-05 10:00)")
        override suspend fun createEvent(title: String, startEpochMs: Long, durationMinutes: Int): Boolean {
            created.add(title to startEpochMs)
            return true
        }
    }

    private fun tool(adapter: CalendarAdapter = FakeCalendarAdapter()) = CalendarTool(adapter)

    @Test
    fun riskClassification() {
        val t = tool()
        assertEquals(ToolRisk.READ, t.riskFor("read_upcoming"))
        assertEquals(ToolRisk.WRITE, t.riskFor("create_event"))
        assertEquals(ToolRisk.WRITE, t.riskFor("unknown"))
    }

    @Test
    fun timeParserAcceptsValidAndRejectsInvalid() {
        assertNotNull(CalendarTimeParser.parseDateTime("2026-10-05 10:30"))
        assertNull(CalendarTimeParser.parseDateTime("2026-10-05"))
        assertNull(CalendarTimeParser.parseDateTime("10-05-2026 10:30"))
        assertNull(CalendarTimeParser.parseDateTime("2026-13-05 10:30"))
    }

    @Test
    fun readUpcomingSummarizesEvents() = runTest {
        val result: ToolResult = tool().execute(ToolRequest("calendar", "read_upcoming"))
        assertTrue(result.success)
        assertTrue(result.message.contains("회의"))
    }

    @Test
    fun createEventRequiresTitleAndValidStart() = runTest {
        val t = tool()
        assertFalse(t.execute(ToolRequest("calendar", "create_event", mapOf("start" to "2026-10-05 10:00"))).success)
        assertFalse(t.execute(ToolRequest("calendar", "create_event", mapOf("title" to "회의", "start" to "2026-10-05"))).success)
    }

    @Test
    fun createEventPassesParsedStartToAdapter() = runTest {
        val adapter = FakeCalendarAdapter()
        assertTrue(
            tool(adapter).execute(
                ToolRequest("calendar", "create_event", mapOf("title" to "회의", "start" to "2026-10-05 10:00")),
            ).success,
        )
        assertEquals(1, adapter.created.size)
        assertEquals("회의", adapter.created[0].first)
    }

    @Test
    fun unapprovedCreateEventIsBlockedByExecutor() = runTest {
        val executor = ToolExecutor(listOf(tool()), InMemoryToolPermissionStore())
        val blocked = executor.execute(
            ToolRequest("calendar", "create_event", mapOf("title" to "회의", "start" to "2026-10-05 10:00")),
        )
        assertFalse(blocked.success)
    }
}
