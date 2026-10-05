package com.woojik.aircallai.privacy

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Contains only fields the user reviews. No transcript, OAuth token or device identifier. */
data class ContentReport(
    val id: String = UUID.randomUUID().toString(),
    val category: String = "content",
    val reason: String,
    val response: String = "",
    val appVersion: String,
    val androidApi: Int,
) {
    internal fun validate() {
        require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false))
        require(category in setOf("content", "privacy", "bug"))
        require(reason.isNotBlank() && reason.length <= 2000 && response.length <= 4000)
        require(appVersion.isNotBlank() && appVersion.length <= 80 && androidApi in 26..100)
    }

    fun payload(): String {
        validate()
        return JSONObject().put("id", id).put("category", category).put("reason", reason)
            .put("response", response).put("appVersion", appVersion).put("androidApi", androidApi).toString()
    }
}

data class ReportReceipt(val id: String)
fun interface ReportTransport { suspend fun post(endpoint: String, payload: String): String }

/** Success requires a receiver acknowledgement for this exact ID, after durable storage. */
class ContentReportClient(
    private val endpoint: String,
    private val transport: ReportTransport = HttpsReportTransport(),
    private val reportMethod: String = "https",
    val supportEmail: String = "",
) {
    val usesEmail: Boolean get() = reportMethod == "email"
    val isConfigured: Boolean get() = when (reportMethod) {
        "email" -> isSupportEmail(supportEmail)
        "https" -> isHttpsEndpoint(endpoint)
        else -> false
    }

    /** Preparing an editable draft is not evidence of delivery or receipt. */
    fun emailDraft(report: ContentReport): ContentReportEmailDraft {
        check(usesEmail && isConfigured) { "신고 문의 이메일이 아직 준비되지 않았습니다." }
        return ContentReportEmailDraft.create(supportEmail, report)
    }

    suspend fun submit(report: ContentReport): ReportReceipt {
        check(reportMethod == "https" && isConfigured) { "HTTPS 신고 접수 주소가 아직 준비되지 않았습니다. 전송되지 않았습니다." }
        val result = JSONObject(transport.post(endpoint, report.payload()))
        check(result.optBoolean("ok") && result.optString("id") == report.id) {
            "신고 접수를 확인하지 못했습니다. 같은 신고 번호로 다시 시도할 수 있습니다."
        }
        return ReportReceipt(report.id)
    }

    companion object {
        // Public support addresses only; reject headers, extra recipients and URI parameters.
        fun isSupportEmail(value: String): Boolean = value.length <= 254 &&
            value.matches(Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9]+(?:[.\\-][A-Za-z0-9]+)*\\.[A-Za-z]{2,63}"))

        fun isHttpsEndpoint(value: String): Boolean = runCatching {
            val uri = URI(value)
            uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
        }.getOrDefault(false)
    }
}

class HttpsReportTransport : ReportTransport {
    override suspend fun post(endpoint: String, payload: String): String = withContext(Dispatchers.IO) {
        try {
            var url = URL(endpoint)
            var method = "POST"
            // Apps Script ContentService returns a one-time HTTPS URL for the response body.
            repeat(3) {
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 15_000
                    connection.instanceFollowRedirects = false
                    connection.requestMethod = method
                    connection.setRequestProperty("Accept", "application/json")
                    if (method == "POST") {
                        val bytes = payload.toByteArray(Charsets.UTF_8)
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        connection.setFixedLengthStreamingMode(bytes.size)
                        connection.outputStream.use { it.write(bytes) }
                    }
                    val code = connection.responseCode
                    if (code in setOf(301, 302, 303)) {
                        val next = URL(url, connection.getHeaderField("Location") ?: error("Missing redirect"))
                        check(url.host == "script.google.com" && next.host == "script.googleusercontent.com" &&
                            next.protocol == "https") { "Unsupported reporting redirect" }
                        // Never forward the report payload or any credentials to a redirect.
                        url = next
                        method = "GET"
                    } else {
                        check(code in 200..299) { "신고 서버에 연결하지 못했습니다. 전송 결과를 확인할 수 없습니다." }
                        return@withContext connection.inputStream.use { stream ->
                            val result = ByteArrayOutputStream()
                            val buffer = ByteArray(1024)
                            while (true) {
                                val count = stream.read(buffer)
                                if (count < 0) break
                                check(result.size() + count <= 8192) { "Unexpected report response" }
                                result.write(buffer, 0, count)
                            }
                            String(result.toByteArray(), Charsets.UTF_8)
                        }
                    }
                } finally { connection.disconnect() }
            }
            error("Too many reporting redirects")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            // Do not expose response bodies, personal report text or network diagnostics.
            throw IllegalStateException("신고 접수를 확인하지 못했습니다. 인터넷 연결을 확인하고 다시 시도해 주세요.")
        }
    }
}
