package com.woojik.aircallai

import com.woojik.aircallai.tools.GoogleCalendarAdapter
import org.junit.Assert.*
import org.junit.Test

class GoogleCalendarPayloadTest {
    @Test fun eventHasExactUtcStartAndDurationAndEscapesTitle() {
        val payload = GoogleCalendarAdapter.eventPayload("회의 \"확인\"", 0L, 60)
        assertEquals("1970-01-01T00:00:00Z", payload.getJSONObject("start").getString("dateTime"))
        assertEquals("1970-01-01T01:00:00Z", payload.getJSONObject("end").getString("dateTime"))
        assertEquals("회의 \"확인\"", org.json.JSONObject(payload.toString()).getString("summary"))
    }
}
