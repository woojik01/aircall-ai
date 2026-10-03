package com.woojik.aircallai.auth

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PRD-09 OAuth HTTP 전송 계약. 단위 테스트에서 fake로 교체한다.
 * 응답은 (HTTP 상태 코드, 본문 문자열)로 반환한다. 토큰은 호출자만 다룬다.
 */
interface OAuthHttpPost {
    /** form-urlencoded POST를 실행한다. */
    suspend fun postForm(url: String, form: Map<String, String>): Pair<Int, String>
}

/** HttpURLConnection 기반 구현. 네트워크 실패 시 (-1, "")를 반환한다. */
class HttpOAuthPost(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
) : OAuthHttpPost {
    override suspend fun postForm(url: String, form: Map<String, String>): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = connectTimeoutMs
                connection.readTimeout = readTimeoutMs
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.setRequestProperty("Accept", "application/json")
                val encoded = form.entries.joinToString("&") {
                    encode(it.key) + "=" + encode(it.value)
                }
                connection.outputStream.use { it.write(encoded.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                code to body
            }.getOrElse { -1 to "" }
        }

    companion object {
        fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    }
}
