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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CoroutineStart
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
class HttpCloudApiAdapter(
    private val nativeToolsEnabled: () -> Boolean = { false },
    private val connect: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection },
) : CloudApiAdapter {

    override suspend fun chatStreaming(apiKey: String, baseUrl: String, model: String,
        history: List<ChatMessage>, onText: suspend (String) -> Unit): String = withContext(Dispatchers.IO) {
        if (!com.woojik.aircallai.privacy.HttpsEndpoint.isHttpsEndpoint(baseUrl))
            throw AIProviderException(ProviderErrorKind.API_ERROR)
        var connection: HttpURLConnection? = null
        val cancellationWatcher = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { connection?.disconnect() }
        }
        try {
            val conn = connect(baseUrl).also { connection = it }
            conn.requestMethod = "POST"
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "text/event-stream")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            val body = JSONObject(buildBody(model, history)).put("stream", true).toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            when (conn.responseCode) {
                401, 403 -> throw AIProviderException(ProviderErrorKind.AUTH_FAILED)
                429 -> throw AIProviderException(ProviderErrorKind.QUOTA_EXCEEDED)
                in 200..299 -> Unit
                else -> throw AIProviderException(ProviderErrorKind.API_ERROR, "스트리밍을 지원하지 않으면 설정에서 실시간 응답을 끄세요.")
            }
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                if (conn.contentType?.contains("application/json") == true) {
                    return@withContext parseAssistantText(reader.readText()).also { onText(it) }
                }
                readChatStream(reader, allowNative = nativeToolsEnabled(), onText = onText)
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: AIProviderException) { throw e
        } catch (_: IOException) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            throw AIProviderException(ProviderErrorKind.NETWORK)
        } catch (_: org.json.JSONException) { throw AIProviderException(ProviderErrorKind.API_ERROR)
        } catch (_: IllegalArgumentException) { throw AIProviderException(ProviderErrorKind.API_ERROR)
        } finally { cancellationWatcher.cancel(); connection?.disconnect() }
    }

    override suspend fun chat(apiKey: String, baseUrl: String, model: String, history: List<ChatMessage>): String =
        withContext(Dispatchers.IO) {
            if (!com.woojik.aircallai.privacy.HttpsEndpoint.isHttpsEndpoint(baseUrl)) {
                throw AIProviderException(ProviderErrorKind.API_ERROR, "올바른 HTTPS API 주소를 설정해 주세요.")
            }
            var connection: HttpURLConnection? = null
            val cancellationWatcher = launch(start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { connection?.disconnect() }
            }
            try {
                val conn = connect(baseUrl)
                connection = conn
                conn.requestMethod = "POST"
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer " + apiKey)
                conn.outputStream.use { it.write(buildBody(model, history).toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                when {
                    code in 200..299 -> {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        parseAssistantText(body)
                    }
                    code == 401 || code == 403 -> throw AIProviderException(ProviderErrorKind.AUTH_FAILED)
                    code == 429 -> throw AIProviderException(ProviderErrorKind.QUOTA_EXCEEDED)
                    else -> throw AIProviderException(ProviderErrorKind.API_ERROR, "HTTP " + code)
                }
            } catch (e: IOException) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                throw AIProviderException(ProviderErrorKind.NETWORK)
            } finally {
                cancellationWatcher.cancel()
                connection?.disconnect()
            }
        }

    private fun buildBody(model: String, history: List<ChatMessage>): String {
        val messages = JSONArray()
        messages.put(
            JSONObject()
                .put("role", "system")
                .put("content", (if (com.woojik.aircallai.ai.provider.ResponseStyle.isVoice(history)) SYSTEM_PERSONA
                    else com.woojik.aircallai.ai.provider.ResponseStyle.TEXT_PROMPT) +
                    if (nativeToolsEnabled()) " Use registered function calls instead of TOOL text. Discover tools with tools__list first." else "")
        )
        val bounded = if (history.lastOrNull()?.role == ChatMessage.Role.USER)
            com.woojik.aircallai.ai.local.localInferenceHistory(history, 48_000, summarize = true) else history
        var pendingToolId: String? = null
        bounded.forEach { m ->
            val envelope = if (m.role == ChatMessage.Role.ASSISTANT && nativeToolsEnabled()) NativeToolCalls.decode(m.content) else null
            if (envelope != null) {
                messages.put(NativeToolCalls.message(envelope))
                pendingToolId = envelope.getString("id")
            } else if (pendingToolId != null && m.role == ChatMessage.Role.USER) {
                messages.put(JSONObject().put("role", "tool").put("tool_call_id", pendingToolId).put("content", m.content))
                pendingToolId = null
            } else {
            messages.put(
                JSONObject()
                    .put("role", when (m.role) {
                        ChatMessage.Role.USER -> "user"
                        ChatMessage.Role.ASSISTANT -> "assistant"
                        ChatMessage.Role.SYSTEM -> "system"
                    })
                    .put("content", m.content)
            )
            }
        }
        return JSONObject().put("model", model).put("messages", messages).also {
            if (nativeToolsEnabled()) it.put("tools", NativeToolCalls.schema()).put("parallel_tool_calls", false)
        }.toString()
    }

    private fun parseAssistantText(body: String): String {
        return try {
            val message = JSONObject(body)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
            val calls = message.optJSONArray("tool_calls")
            val text = if (calls != null && calls.length() > 0) {
                require(nativeToolsEnabled() && calls.length() == 1)
                val call = calls.getJSONObject(0)
                val function = call.getJSONObject("function")
                NativeToolCalls.envelope(call.getString("id"), function.getString("name"), function.getString("arguments"))
            } else message.getString("content")
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

/** SSE events can contain multiple data lines and empty usage-only choices. */
internal suspend fun readChatStream(reader: java.io.BufferedReader, allowNative: Boolean = false,
    onText: suspend (String) -> Unit): String {
    val text = StringBuilder()
    val event = StringBuilder()
    var done = false
    var callId = ""
    var functionName = ""
    val arguments = StringBuilder()
    suspend fun consume() {
        val data = event.toString().trim()
        event.setLength(0)
        if (data.isEmpty()) return
        if (data == "[DONE]") { done = true; return }
        val json = JSONObject(data)
        if (json.has("error")) throw AIProviderException(ProviderErrorKind.API_ERROR)
        val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: return
        val payload = choice.optJSONObject("delta")
        payload?.optJSONArray("tool_calls")?.let { calls ->
            require(calls.length() <= 1)
            if (calls.length() == 1) {
                val call = calls.getJSONObject(0)
                require(call.optInt("index", 0) == 0)
                callId += call.optString("id", "")
                call.optJSONObject("function")?.let {
                    functionName += it.optString("name", "")
                    arguments.append(it.optString("arguments", ""))
                    require(arguments.length <= 65_536)
                }
            }
        }
        val delta = payload?.optString("content").orEmpty()
        if (delta.isNotEmpty() && delta != "null") {
            text.append(delta)
            if (text.length > 1_000_000) throw AIProviderException(ProviderErrorKind.API_ERROR)
            onText(text.toString())
        }
    }
    while (!done) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        val line = reader.readLine() ?: break
        if (line.isEmpty()) consume()
        else if (line.startsWith("data:")) {
            if (event.isNotEmpty()) event.append('\n')
            event.append(line.removePrefix("data:").trimStart())
            if (event.length > 1_000_000) throw AIProviderException(ProviderErrorKind.API_ERROR)
        }
    }
    if (event.isNotEmpty()) consume()
    if (!done) throw AIProviderException(ProviderErrorKind.NETWORK, "응답이 중단되었습니다")
    if (functionName.isNotEmpty()) {
        require(allowNative)
        return NativeToolCalls.envelope(callId, functionName, arguments.toString())
    }
    if (text.isEmpty()) throw AIProviderException(ProviderErrorKind.API_ERROR)
    return text.toString()
}
