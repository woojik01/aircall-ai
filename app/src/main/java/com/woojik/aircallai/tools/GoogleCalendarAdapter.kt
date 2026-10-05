package com.woojik.aircallai.tools

import com.woojik.aircallai.core.storage.CredentialManager
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class CalendarAccessException(message: String) : Exception(message)

/** Uses the same Google authorization as Gmail, with calendar.events scope. */
class GoogleCalendarAdapter(private val credentials: CredentialManager) : CalendarAdapter {
    override suspend fun upcomingEvents(limit: Int): List<String> {
        val now = URLEncoder.encode(Instant.now().toString(), "UTF-8")
        val result = request("GET", "?singleEvents=true&orderBy=startTime&maxResults=${limit.coerceIn(1, 20)}&timeMin=$now")
        val items = result.optJSONArray("items") ?: return emptyList()
        return List(items.length()) { index ->
            val event = items.getJSONObject(index)
            val start = event.optJSONObject("start")
            val date = start?.optString("dateTime").orEmpty().ifBlank { start?.optString("date").orEmpty() }
            val localTime = runCatching {
                Instant.parse(date).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            }.getOrDefault(date)
            "$localTime · ${event.optString("summary", "제목 없음") }"
        }
    }

    override suspend fun createEvent(title: String, startEpochMs: Long, durationMinutes: Int): Boolean {
        val body = eventPayload(title, startEpochMs, durationMinutes)
        return request("POST", "", body).optString("id").isNotBlank()
    }

    private suspend fun request(method: String, suffix: String, body: JSONObject? = null): JSONObject {
        val token = credentials.load(GmailApiClient.CREDENTIAL_SERVICE)?.toString(Charsets.UTF_8)
            ?: throw CalendarAccessException("Google 로그인이 필요합니다. 설정의 도구 및 계정에서 연결해 주세요.")
        return withContext(Dispatchers.IO) {
            val connection = URL("https://www.googleapis.com/calendar/v3/calendars/primary/events$suffix").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000; connection.readTimeout = 20_000
                connection.setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    connection.doOutput = true
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                when (val code = connection.responseCode) {
                    in 200..299 -> JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    401 -> throw CalendarAccessException("Google 로그인이 만료되었습니다. 계정을 다시 연결해 주세요.")
                    403 -> throw CalendarAccessException("Google Calendar 접근이 거부되었습니다. 캘린더 권한을 승인하고 Calendar API 활성화 상태를 확인해 주세요.")
                    else -> throw CalendarAccessException("Google Calendar 요청에 실패했습니다 (HTTP $code).")
                }
            } finally { connection.disconnect() }
        }
    }

    companion object {
        internal fun eventPayload(title: String, startEpochMs: Long, durationMinutes: Int): JSONObject = JSONObject()
            .put("summary", title)
            .put("start", JSONObject().put("dateTime", Instant.ofEpochMilli(startEpochMs).toString()))
            .put("end", JSONObject().put("dateTime", Instant.ofEpochMilli(startEpochMs + durationMinutes * 60_000L).toString()))
    }
}
