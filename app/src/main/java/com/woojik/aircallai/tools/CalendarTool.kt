package com.woojik.aircallai.tools

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * PRD-06 Calendar Adapter 계약.
 * 기기 캘린더(ContentResolver) 구현은 AndroidCalendarAdapter. 단위 테스트는 fake로 대체한다.
 */
interface CalendarAdapter {
    suspend fun upcomingEvents(limit: Int): List<String>
    suspend fun createEvent(title: String, startEpochMs: Long, durationMinutes: Int): Boolean
}

/** "yyyy-MM-dd HH:mm" 문자열을 epoch millis로 변환한다. 실패 시 null. */
object CalendarTimeParser {
    private val FORMAT = Regex("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}$")

    fun parseDateTime(value: String): Long? {
        if (!FORMAT.matches(value)) return null
        return runCatching {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            format.isLenient = false
            format.parse(value)?.time
        }.getOrNull()
    }
}

/**
 * PRD-06 Calendar Tool.
 * - read_upcoming: READ (기본 허용). 제목·시간만 요약해 반환한다.
 * - create_event: WRITE (승인 필요).
 */
class CalendarTool(
    private val adapter: CalendarAdapter,
) : Tool {
    override val name = "calendar"
    override val description = "Google 캘린더의 일정을 조회하거나 새 일정을 만드는 도구"

    override fun riskFor(action: String) = when (action) {
        "read_upcoming" -> ToolRisk.READ
        else -> ToolRisk.WRITE
    }

    override suspend fun execute(request: ToolRequest): ToolResult = try {
        executeRequest(request)
    } catch (e: CalendarAccessException) {
        ToolResult(false, e.message ?: "Google 캘린더 연결을 확인해 주세요")
    }

    private suspend fun executeRequest(request: ToolRequest): ToolResult {
        return when (request.action) {
            "read_upcoming" -> {
                val limit = request.arguments["limit"]?.toIntOrNull() ?: 5
                val events = adapter.upcomingEvents(limit.coerceIn(1, 20))
                if (events.isEmpty()) ToolResult(true, "예정된 일정이 없습니다") else ToolResult(true, events.joinToString(" | "))
            }
            "create_event" -> {
                val title = request.arguments["title"]?.trim().orEmpty()
                if (title.isEmpty()) return ToolResult(false, "일정 제목(title)이 필요합니다")
                val startMs = CalendarTimeParser.parseDateTime(request.arguments["start"]?.trim().orEmpty())
                    ?: return ToolResult(false, "시작 시각(start=\"YYYY-MM-DD HH:MM\")이 필요합니다")
                val duration = (request.arguments["duration_minutes"]?.toIntOrNull() ?: 60).coerceIn(5, 24 * 60)
                if (adapter.createEvent(title, startMs, duration)) {
                    ToolResult(true, "일정을 등록했습니다")
                } else {
                    ToolResult(false, "일정 등록에 실패했습니다")
                }
            }
            else -> ToolResult(false, "지원하지 않는 Calendar 작업입니다")
        }
    }
}

