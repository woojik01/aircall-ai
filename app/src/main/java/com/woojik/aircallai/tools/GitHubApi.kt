package com.woojik.aircallai.tools

import com.woojik.aircallai.core.storage.CredentialManager
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** HTTP 전송 계층. 테스트에서 fake로 교체한다. 반환값은 HTTP 상태 코드이며 네트워크 실패 시 예외를 던진다. */
fun interface GitHubTransport {
    fun send(method: String, url: String, token: String, jsonBody: String?): Int
}

class UrlConnectionGitHubTransport : GitHubTransport {
    override fun send(method: String, url: String, token: String, jsonBody: String?): Int {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("Authorization", "Bearer $token")
            if (jsonBody != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            }
            return connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
    }
}

/**
 * PRD-06: CredentialManager에 보관된 GitHub 토큰으로 REST API를 호출하는 어댑터.
 * 토큰은 절대 ToolResult나 로그에 노출되지 않는다.
 */
open class GitHubApiClient(
    private val credentials: CredentialManager,
    private val transport: GitHubTransport = UrlConnectionGitHubTransport(),
) {
    suspend fun readRepository(owner: String, repo: String): ToolResult {
        if (!isValidRepo(owner, repo)) return ToolResult(false, INVALID_REPO)
        return request("GET", "/repos/$owner/$repo", null, "GitHub 저장소 조회가 완료되었습니다")
    }

    suspend fun createIssue(owner: String, repo: String, title: String, body: String?): ToolResult {
        if (!isValidRepo(owner, repo)) return ToolResult(false, INVALID_REPO)
        if (title.isBlank()) return ToolResult(false, "Issue 제목이 필요합니다")
        val json = jsonObject("title" to title.trim(), "body" to body)
        return request("POST", "/repos/$owner/$repo/issues", json, "GitHub Issue가 생성되었습니다")
    }

    suspend fun createPullRequest(
        owner: String,
        repo: String,
        title: String,
        head: String,
        base: String,
        body: String?,
    ): ToolResult {
        if (!isValidRepo(owner, repo)) return ToolResult(false, INVALID_REPO)
        if (title.isBlank()) return ToolResult(false, "PR 제목이 필요합니다")
        if (!VALID_BRANCH.matches(head) || !VALID_BRANCH.matches(base)) {
            return ToolResult(false, "잘못된 브랜치 이름입니다")
        }
        val json = jsonObject("title" to title.trim(), "head" to head, "base" to base, "body" to body)
        return request("POST", "/repos/$owner/$repo/pulls", json, "GitHub Pull Request가 생성되었습니다")
    }

    private suspend fun request(method: String, path: String, json: String?, successMessage: String): ToolResult {
        val token = credentials.load(CREDENTIAL_SERVICE)?.toString(Charsets.UTF_8)?.trim()
        if (token.isNullOrEmpty()) return ToolResult(false, "GitHub 인증이 설정되지 않았습니다")
        return withContext(Dispatchers.IO) {
            runCatching { transport.send(method, "$API_BASE$path", token, json) }
                .map { code -> resultFor(code, successMessage) }
                .getOrElse { ToolResult(false, "GitHub 연결에 실패했습니다") }
        }
    }

    private fun resultFor(code: Int, successMessage: String) = when (code) {
        in 200..299 -> ToolResult(true, successMessage)
        401, 403 -> ToolResult(false, "GitHub 인증에 실패했습니다")
        404 -> ToolResult(false, "GitHub 저장소를 찾을 수 없습니다")
        422 -> ToolResult(false, "GitHub 요청 내용이 올바르지 않습니다")
        else -> ToolResult(false, "GitHub 요청에 실패했습니다")
    }

    private fun isValidRepo(owner: String, repo: String) = VALID_NAME.matches(owner) && VALID_NAME.matches(repo)

    companion object {
        const val CREDENTIAL_SERVICE = "github"
        private const val API_BASE = "https://api.github.com"
        private const val INVALID_REPO = "잘못된 저장소 이름입니다"
        private val VALID_NAME = Regex("^[A-Za-z0-9_.-]{1,100}$")
        private val VALID_BRANCH = Regex("^[A-Za-z0-9_./:-]{1,255}$")

        internal fun jsonObject(vararg fields: Pair<String, String?>): String =
            fields.filter { it.second != null }
                .joinToString(",", "{", "}") { (k, v) -> "\"${escape(k)}\":\"${escape(v!!)}\"" }

        private fun escape(value: String): String = buildString {
            for (c in value) {
                when {
                    c == '"' -> append("\\\"")
                    c == '\\' -> append("\\\\")
                    c == '\n' -> append("\\n")
                    c == '\r' -> append("\\r")
                    c == '\t' -> append("\\t")
                    c < ' ' -> append(String.format("\\u%04x", c.code))
                    else -> append(c)
                }
            }
        }
    }
}
