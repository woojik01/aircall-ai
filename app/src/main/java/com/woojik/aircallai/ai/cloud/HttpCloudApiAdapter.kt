package com.woojik.aircallai.ai.cloud

import com.woojik.aircallai.ai.provider.AIProviderException
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.ai.provider.ProviderErrorKind
import com.woojik.aircallai.core.logging.SecureLog
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * PRD-04 Cloud Mode: OpenAI 호환 chat completions 엔드포인트를 직접 호출한다.
 * - 사용자가 등록한 baseUrl/apiKey만 사용 (코드에 기본 Key/URL 하드코딩 없음)
 * - 응답/Key는 절대 로그에 남기지 않는다
 * - 별도 AirCall AI 서버를 거치지 않는다
 */
class HttpCloudApiAdapter : CloudApiAdapter {

    override suspend fun chat(apiKey: String, baseUrl: String, history: List<ChatMessage>): String =
        withContext(Dispatchers.IO) {
            if (baseUrl.isBlank()) {
                throw AIProviderException(ProviderErrorKind.API_ERROR, "endpoint not configured")
            }
            val connection = try {
                val conn = URL(baseUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer " + apiKey)
                conn.outputStream.use { it.write(buildBody(history).toByteArray(Charsets.UTF_8)) }
                conn
            } catch (e: IOException) {
                throw AIProviderException(ProviderErrorKind.NETWORK)
            }

            val code = try {
                connection.responseCode
            } catch (e: IOException) {
                throw AIProviderException(ProviderErrorKind.NETWORK)
            }
            when {
                code in 200..299 -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    parseAssistantText(body)
                }
                code == 401 || code == 403 -> throw AIProviderException(ProviderErrorKind.AUTH_FAILED)
                code == 429 -> throw AIProviderException(ProviderErrorKind.QUOTA_EXCEEDED)
                else -> throw AIProviderException(ProviderErrorKind.API_ERROR, "HTTP " + code)
            }
        }

    private fun buildBody(history: List<ChatMessage>): String {
        val messages = JSONArray()
        history.forEach { m ->
            messages.put(JSONObject().put("role", if (m.role == ChatMessage.Role.USER) "user" else "assistant").put("content", m.content))
        }
        return JSONObject().put("model", "").put("messages", messages).toString()
    }

    private fun parseAssistantText(body: String): String {
        return try {
            val text = JSONObject(body)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
            SecureLog.d(TAG, "cloud response parsed")
            text
        } catch (t: Throwable) {
            throw AIProviderException(ProviderErrorKind.API_ERROR, "unparseable response")
        }
    }

    companion object {
        private const val TAG = "HttpCloudApi"
    }
}
