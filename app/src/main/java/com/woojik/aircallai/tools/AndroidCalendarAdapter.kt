package com.woojik.aircallai.tools

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PRD-06 Calendar Adapter: 기기 캘린더(ContentResolver) 기반 구현.
 * - READ: 다가오는 일정을 제목·시간만 요약해 반환한다(설명·참석자 등 상세는 다루지 않는다).
 * - WRITE: 기본(primary) 캘린더에 일정을 추가한다.
 */
class AndroidCalendarAdapter(
    private val context: Context,
) : CalendarAdapter {

    override suspend fun upcomingEvents(limit: Int): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(now.toString())
                .appendPath((now + SEARCH_RANGE_MS).toString())
                .build()
            val projection = arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
            )
            val events = mutableListOf<String>()
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                CalendarContract.Instances.BEGIN + " ASC",
            )?.use { cursor ->
                val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                while (cursor.moveToNext() && events.size < limit) {
                    val title = cursor.getString(titleIdx) ?: "(제목 없음)"
                    val begin = cursor.getLong(beginIdx)
                    events.add(title + " (" + formatDateTime(begin) + ")")
                }
            }
            events
        }.getOrElse { emptyList() }
    }

    override suspend fun createEvent(title: String, startEpochMs: Long, durationMinutes: Int): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val calendarId = primaryCalendarId() ?: return@runCatching false
                val values = ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    put(CalendarContract.Events.TITLE, title)
                    put(CalendarContract.Events.DTSTART, startEpochMs)
                    put(CalendarContract.Events.DTEND, startEpochMs + durationMinutes * 60_000L)
                    put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                }
                context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) != null
            }.getOrDefault(false)
        }

    /** 기본(primary) 캘린더가 없으면 첫 번째 캘린더를 사용한다. */
    private fun primaryCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
        return context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
            val primaryIdx = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
            var firstId: Long? = null
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                if (firstId == null) firstId = id
                if (primaryIdx >= 0 && cursor.getInt(primaryIdx) == 1) return id
            }
            firstId
        }
    }

    private fun formatDateTime(epochMs: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return format.format(Date(epochMs))
    }

    companion object {
        private const val SEARCH_RANGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
