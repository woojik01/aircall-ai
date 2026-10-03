package com.woojik.aircallai.tools

import com.woojik.aircallai.core.storage.CredentialManager
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PRD-06 Gmail Adapter: CredentialManager에 보관된 OAuth 토큰으로
 * Gmail REST API(messages/send)를 호출해 이메일을 발송한다.
 * 토큰은 절대 ToolResult나 로그에 노출되지 않는다.
 * 테스트에서 fake 구현으로 교체할 수 있도록 open 클래스다.
 */
open class GmailApiClient(
    private val credentials: CredentialManager,
) {
    /** WRITE: 이메일 발송. 승인 계층(ToolExecutor)이 허용한 경우에만 도달한다. */
    suspend fun sendEmail(to: String, subject: String, body: String): ToolResult {
        if (!VALID_EMAIL.matches(to)) {
            return ToolResult(false, "받는 사람 주소가 올바르지 않습니다")
        }
        if (subject.isBlank()) {
            return ToolResult(false, "메일 제목이 필요합니다")
        }
        if (body.isBlank()) {
            return ToolResult(false, "메일 내용이 필요합니다")
        }
        val token = credentials.load(CREDENTIAL_SERVICE)?.toString(Charsets.UTF_8)
            ?: return ToolResult(false, "Gmail 인증이 설정되지 않았습니다")
        return withContext(Dispatchers.IO) { send(rawMessage(to, subject, body), token) }
    }

    /** RFC 822 원문을 base64url로 인코딩한다. 헤더 주입(CRLF)을 차단한다. */
    internal fun rawMessage(to: String, subject: String, body: String): String {
        val message = StringBuilder()
            .append("To: ").append(to).append("\r\n")
            .append("Subject: ").append(encodeSubject(subject)).append("\r\n")
            .append("Content-Type: text/plain; charset=\"UTF-8\"\r\n")
            .append("\r\n")
            .append(body)
            .toString()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(message.toByteArray(Charsets.UTF_8))
    }

    /** RFC 2047 encoded-word로 UTF-8 제목을 표현해 Gmail/메일 클라이언트의 문자셋 오해를 막는다. */
    private fun encodeSubject(value: String): String {
        val bytes = sanitizeHeader(value).toByteArray(Charsets.UTF_8)
        val words = mutableListOf<String>()
        var start = 0
        while (start < bytes.size) {
            var end = minOf(start + MAX_SUBJECT_CHUNK_BYTES, bytes.size)
            while (end < bytes.size && (bytes[end].toInt() and 0xC0) == 0x80) end -= 1
            if (end == start) end = minOf(start + MAX_SUBJECT_CHUNK_BYTES, bytes.size)
            val encoded = Base64.getEncoder().encodeToString(bytes.copyOfRange(start, end))
            words += "=?UTF-8?B?$encoded?="
            start = end
        }
        return words.joinToString(" ")
    }

    /** CRLF 쌍은 공백 하나로, 남은 단독 CR/LF도 공백으로 바꿔 헤더 주입을 차단한다. */
    private fun sanitizeHeader(value: String): String =
        value.replace(CRLF_PAIR, " ").replace(CRLF, " ")

    private fun send(raw: String, token: String): ToolResult {
        return runCatching {
            val payload = "{\"raw\":\"" + raw + "\"}"
            val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Authorization", "Bearer " + token)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val responseBody = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            when (code) {
                in 200..299 -> {
                    val messageId = runCatching {
                        org.json.JSONObject(responseBody).optString("id")
                    }.getOrDefault("")
                    ToolResult(
                        true,
                        if (messageId.isBlank()) {
                            "Gmail API가 발송 요청을 접수했습니다."
                        } else {
                            "Gmail API가 발송 요청을 접수했습니다 (message ID: $messageId)."
                        },
                    )
                }
                401 -> ToolResult(false, "Gmail 토큰이 만료되었거나 유효하지 않습니다. Google 계정을 다시 연결해 주세요.")
                403 -> ToolResult(false, gmailError(responseBody, "Gmail 발송 권한이 없습니다. gmail.send 권한을 다시 승인해 주세요."))
                else -> ToolResult(false, gmailError(responseBody, "Gmail 요청이 실패했습니다 (HTTP $code)."))
            }
        }.getOrElse { error ->
            ToolResult(
                false,
                "Gmail 네트워크 요청에 실패했습니다: " + error.javaClass.simpleName +
                    (error.message?.let { " ($it)" } ?: ""),
            )
        }
    }

    companion object {
        const val CREDENTIAL_SERVICE = "gmail"
        private const val ENDPOINT = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val MAX_SUBJECT_CHUNK_BYTES = 45

        private fun gmailError(body: String, fallback: String): String {
            val message = runCatching {
                org.json.JSONObject(body).getJSONObject("error").optString("message")
            }.getOrNull()
            return if (message.isNullOrBlank()) fallback else "$fallback: $message"
        }

        private val VALID_EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        private val CRLF_PAIR = Regex("\\r\\n")
        private val CRLF = Regex("[\\r\\n]")
    }
}

/**
 * PRD-06 Gmail Tool.
 * - send_email: WRITE (승인 필요).
 */
class GmailTool(
    private val api: GmailApiClient,
) : Tool {
    override val name = "gmail"
    override val description = "Gmail로 이메일을 보내는 도구"

    override fun riskFor(action: String) = when (action) {
        "send_email" -> ToolRisk.WRITE
        else -> ToolRisk.WRITE
    }

    override suspend fun execute(request: ToolRequest): ToolResult {
        return when (request.action) {
            "send_email" -> {
                val to = request.arguments["to"] ?: return ToolResult(false, "받는 사람(to)이 필요합니다")
                val subject = request.arguments["subject"] ?: return ToolResult(false, "메일 제목(subject)이 필요합니다")
                val body = request.arguments["body"] ?: return ToolResult(false, "메일 내용(body)이 필요합니다")
                api.sendEmail(to, subject, body)
            }
            else -> ToolResult(false, "지원하지 않는 Gmail 작업입니다")
        }
    }
}
