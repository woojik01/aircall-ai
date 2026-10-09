package com.woojik.aircallai.tools

import com.woojik.aircallai.core.storage.CredentialManager
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PRD-06: CredentialManager에 보관된 GitHub 토큰으로 REST API를 호출하는 어댑터.
 * 토큰은 절대 ToolResult나 로그에 노출되지 않는다.
 * 테스트에서 fake 구현으로 교체할 수 있도록 open 클래스다.
 */
open class GitHubApiClient(
    private val credentials: CredentialManager,
) {
    suspend fun readRepository(owner: String, repo: String): ToolResult {
        if (!VALID_NAME.matches(owner) || !VALID_NAME.matches(repo)) {
            return ToolResult(false, "잘못된 저장소 이름입니다")
        }
        return request("GET", "/repos/" + owner + "/" + repo)
    }

    /** AI가 저장소 파일 내용을 실제로 읽을 수 있도록 Contents API 응답을 반환한다. */
    suspend fun readFile(owner: String, repo: String, filePath: String): ToolResult {
        if (!VALID_NAME.matches(owner) || !VALID_NAME.matches(repo)) {
            return ToolResult(false, "잘못된 저장소 이름입니다")
        }
        val normalizedPath = filePath.trim().trim('/')
        val segments = normalizedPath.split('/')
        if (normalizedPath.isBlank() || segments.any { it.isBlank() || it == "." || it == ".." }) {
            return ToolResult(false, "파일 경로가 올바르지 않습니다")
        }
        val encodedPath = segments.joinToString("/") { encodePathSegment(it) }
        return request("GET", "/repos/" + owner + "/" + repo + "/contents/" + encodedPath, decodeContents = true)
    }

    /** PRD-06 WRITE: Issue 생성. 승인 계층(ToolExecutor)이 허용한 경우에만 도달한다. */
    suspend fun createIssue(owner: String, repo: String, title: String, body: String?): ToolResult {
        if (!VALID_NAME.matches(owner) || !VALID_NAME.matches(repo)) {
            return ToolResult(false, "잘못된 저장소 이름입니다")
        }
        if (title.isBlank()) {
            return ToolResult(false, "Issue 제목이 필요합니다")
        }
        val json = StringBuilder("{\"title\":").append(json(title))
        if (body != null) {
            json.append(",\"body\":").append(json(body))
        }
        json.append("}")
        return request("POST", "/repos/" + owner + "/" + repo + "/issues", json.toString())
    }

    /** PRD-06 WRITE: Pull Request 생성. head/base 브랜치 검증 후 POST한다. */
    suspend fun createPullRequest(
        owner: String,
        repo: String,
        title: String,
        head: String,
        base: String,
        body: String?,
    ): ToolResult {
        if (!VALID_NAME.matches(owner) || !VALID_NAME.matches(repo)) {
            return ToolResult(false, "잘못된 저장소 이름입니다")
        }
        if (title.isBlank()) {
            return ToolResult(false, "PR 제목이 필요합니다")
        }
        if (head.isBlank() || base.isBlank()) {
            return ToolResult(false, "head/base 브랜치가 필요합니다")
        }
        val json = StringBuilder("{\"title\":").append(json(title))
        json.append(",\"head\":").append(json(head))
        json.append(",\"base\":").append(json(base))
        if (body != null) {
            json.append(",\"body\":").append(json(body))
        }
        json.append("}")
        return request("POST", "/repos/" + owner + "/" + repo + "/pulls", json.toString())
    }

    private suspend fun request(method: String, path: String, body: String? = null, decodeContents: Boolean = false): ToolResult {
        val token = credentials.load(CREDENTIAL_SERVICE)?.toString(Charsets.UTF_8)
            ?: return ToolResult(false, "GitHub 인증이 설정되지 않았습니다")
        return withContext(Dispatchers.IO) { send(method, path, token, body, decodeContents) }
    }

    private fun send(method: String, path: String, token: String, body: String?, decodeContents: Boolean): ToolResult {
        return runCatching {
            val connection = URL("https://api.github.com" + path).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("Authorization", "Bearer " + token)
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val responseBody = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            when (code) {
                in 200..299 -> {
                    val resultBody = if (decodeContents) decodeContentsResponse(responseBody) else responseBody
                    val resultUrl = runCatching { org.json.JSONObject(responseBody).optString("html_url") }.getOrNull()
                        ?.takeIf { safeGitHubResultUrl(it) }
                    ToolResult(true, resultBody.take(MAX_RESPONSE_CHARS).ifBlank { "GitHub 요청이 완료되었습니다." }, resultUrl)
                }
                401 -> ToolResult(false, "GitHub 토큰이 유효하지 않습니다. 계정을 다시 연결해 주세요.")
                403 -> ToolResult(false, githubError("GitHub 접근이 거부되었습니다. 저장소 권한 또는 API 제한을 확인해 주세요.", responseBody))
                404 -> ToolResult(false, "GitHub 저장소 또는 파일을 찾을 수 없습니다.")
                422 -> ToolResult(false, githubError("GitHub 요청이 거부되었습니다.", responseBody))
                else -> ToolResult(false, githubError("GitHub 요청에 실패했습니다 (HTTP $code).", responseBody))
            }
        }.getOrElse { ToolResult(false, "GitHub 연결에 실패했습니다") }
    }

    companion object {
        const val CREDENTIAL_SERVICE = "github"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private val VALID_NAME = Regex("^[A-Za-z0-9_.-]{1,100}$")
        private const val MAX_RESPONSE_CHARS = 12_000

        private fun encodePathSegment(segment: String): String =
            java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

        private fun decodeContentsResponse(body: String): String = runCatching {
            val json = org.json.JSONObject(body)
            if (json.optString("type") == "file") {
                val encoded = json.optString("content").replace("\n", "").trim()
                if (encoded.isBlank()) {
                    "파일이 너무 커서 GitHub Contents API 응답에 내용이 없습니다."
                } else {
                    String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT), Charsets.UTF_8)
                }
            } else {
                body
            }
        }.getOrDefault(body)

        private fun githubError(fallback: String, body: String): String {
            val message = runCatching { org.json.JSONObject(body).optString("message") }.getOrNull()
            return if (message.isNullOrBlank()) fallback else "$fallback: $message"
        }

        /** JSON 문자열 리터럴로 이스케이프. 사용자 입력을 안전하게 직렬화한다. */
        internal fun json(value: String): String {
            val escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
            return "\"" + escaped + "\""
        }
    }
}
