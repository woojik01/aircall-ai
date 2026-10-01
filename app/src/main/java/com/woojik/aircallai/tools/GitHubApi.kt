package com.woojik.aircallai.tools

import com.woojik.aircallai.core.storage.CredentialManager
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class GitHubHttpResponse(val code: Int, val body: String = "")

/** HTTP 전송 계층. 테스트에서 fake로 교체한다. 네트워크 실패 시 예외를 던진다. */
fun interface GitHubTransport {
    fun send(method: String, url: String, token: String, jsonBody: String?): GitHubHttpResponse
}

class UrlConnectionGitHubTransport : GitHubTransport {
    override fun send(method: String, url: String, token: String, jsonBody: String?): GitHubHttpResponse {
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
            val code = connection.responseCode
            val body = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else ""
            return GitHubHttpResponse(code, body)
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
 * 성공 시 AI가 사용자에게 전달할 수 있는 요약을 반환한다. 토큰은 ToolResult나 로그에 노출하지 않는다.
 */
open class GitHubApiClient(
    private val credentials: CredentialManager,
    private val transport: GitHubTransport = UrlConnectionGitHubTransport(),
) {
    suspend fun currentUser(): ToolResult =
        request("GET", "/user", null) { json -> "GitHub 로그인 사용자: ${json.text("login")}" }

    suspend fun readRepository(owner: String, repo: String): ToolResult {
        if (!isValidRepo(owner, repo)) return ToolResult(false, INVALID_REPO)
        return request("GET", "/repos/$owner/$repo", null) { json ->
            buildString {
                append("저장소 ").append(json.text("full_name"))
                append(if (json.optBoolean("private")) " (비공개)" else " (공개)")
                json.text("description").takeIf { it.isNotEmpty() }?.let { append(", 설명: ").append(it) }
                json.text("language").takeIf { it.isNotEmpty() }?.let { append(", 언어: ").append(it) }
                append(", 기본 브랜치: ").append(json.text("default_branch"))
                append(", 스타 ").append(json.optInt("stargazers_count")).append("개")
                append(", 열린 이슈 ").append(json.optInt("open_issues_count")).append("개")
                json.text("pushed_at").takeIf { it.isNotEmpty() }?.let { append(", 마지막 푸시: ").append(it) }
            }
        }
    }

    suspend fun createIssue(owner: String, repo: String, title: String, body: String?): ToolResult {
        if (!isValidRepo(owner, repo)) return ToolResult(false, INVALID_REPO)
        if (title.isBlank()) return ToolResult(false, "Issue 제목이 필요합니다")
        val json = jsonObject("title" to title.trim(), "body" to body)
        return request("POST", "/repos/$owner/$repo/issues", json) { res ->
            "Issue #${res.optInt("number")}가 생성되었습니다: ${res.text("html_url")}"
        }
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
        return request("POST", "/repos/$owner/$repo/pulls", json) { res ->
            "Pull Request #${res.optInt("number")}가 생성되었습니다: ${res.text("html_url")}"
        }
    }

    private suspend fun request(
        method: String,
        path: String,
        json: String?,
        summarize: (JSONObject) -> String,
    ): ToolResult {
        val token = credentials.load(CREDENTIAL_SERVICE)?.toString(Charsets.UTF_8)?.trim()
        if (token.isNullOrEmpty()) {
            return ToolResult(false, "GitHub 인증이 설정되지 않았습니다. 설정에서 GitHub 토큰을 저장해야 합니다")
        }
        return withContext(Dispatchers.IO) {
            runCatching { transport.send(method, "$API_BASE$path", token, json) }
                .map { response -> resultFor(response, summarize) }
                .getOrElse { ToolResult(false, "GitHub 연결에 실패했습니다") }
        }
    }

    private fun resultFor(response: GitHubHttpResponse, summarize: (JSONObject) -> String) = when (response.code) {
        in 200..299 -> ToolResult(
            true,
            runCatching { summarize(JSONObject(response.body)) }.getOrDefault("GitHub 요청이 완료되었습니다"),
        )
        401 -> ToolResult(false, "GitHub 인증에 실패했습니다. 토큰을 확인해야 합니다")
        403 -> ToolResult(false, "GitHub 권한이 없습니다. 토큰 권한을 확인해야 합니다")
        404 -> ToolResult(false, "GitHub 저장소를 찾을 수 없습니다")
        422 -> ToolResult(false, "GitHub 요청 내용이 올바르지 않습니다")
        else -> ToolResult(false, "GitHub 요청에 실패했습니다")
    }

    private fun isValidRepo(owner: String, repo: String) = VALID_NAME.matches(owner) && VALID_NAME.matches(repo)

    private fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key)

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
