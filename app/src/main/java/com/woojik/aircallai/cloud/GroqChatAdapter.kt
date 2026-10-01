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
 * - 사용자가 등록한 baseUrl/apiKey/model만 사용 (코드에 Key/기본 URL 하드코딩 없음)
 * - 응답/Key는 절대 로그에 남기지 않는다
 * - 별도 AirCall AI 서버를 거치지 않는다
 * - PRD-05: 음성으로 재생되는 대화 특성상 자연스러운 구어체 페르소나를
 *   시스템 프롬프트로 함께 보낸다 (이모지/특수문자는 TTS에서 소리내지 못하므로 금지).
 */
class HttpCloudApiAdapter : CloudApiAdapter {

    override suspend fun chat(apiKey: String, baseUrl: String, model: String, history: List<ChatMessage>): String =
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
                conn.outputStream.use { it.write(buildBody(model, history).toByteArray(Charsets.UTF_8)) }
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

    private fun buildBody(model: String, history: List<ChatMessage>): String {
        val messages = JSONArray()
        messages.put(
            JSONObject()
                .put("role", "system")
                .put("content", SYSTEM_PERSONA)
        )
        history.forEach { m ->
            messages.put(
                JSONObject()
                    .put("role", if (m.role == ChatMessage.Role.USER) "user" else "assistant")
                    .put("content", m.content)
            )
        }
        return JSONObject().put("model", model).put("messages", messages).toString()
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

        /** 음성 우선 앱: 실제 사람과 통화하듯 자연스러운 톤으로 말한다. */
        private const val SYSTEM_PERSONA =
            "너는 전화처럼 음성으로 대화하는 친근한 AI 친구다. " +
                "다음 규칙을 지켜라. " +
                "1) 반드시 자연스러운 한국어 구어체(해요체)로, 실제 사람이 말하듯 답한다. " +
                "2) 짧고 리듬 있게. 한 번에 한두 문장으로 말하고 필요하면 되묻는다. " +            
                "3) 감탄사나 억양이 살아나는 표현(아, 음, 그러니까, 오 그렇군요, 정말요?)을 자연스럽게 쓴다. " +
                "4) 이모지, 이모티콘, 특수문자 장식, 마크다운은 절대 쓰지 않는다. " +
                "5) 문서체나 나열식 설명 대신, 친구와 수다 떨듯 대화한다. " +
                "6) 사용자가 짧게 물으면 짧게 답하고, 깊은 이야기가 오가면 공감 먼저 한다."
    }
}
