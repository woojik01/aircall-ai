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

    private suspend fun request(method: String, path: String, body: String? = null): ToolResult {
        val token = credentials.load(CREDENTIAL_SERVICE)?.toString(Charsets.UTF_8)
            ?: return ToolResult(false, "GitHub 인증이 설정되지 않았습니다")
        return withContext(Dispatchers.IO) { send(method, path, token, body) }
    }

    private fun send(method: String, path: String, token: String, body: String?): ToolResult {
        return runCatching {
            val connection = URL("https://api.github.com" + path).openConnection() as HttpURLConnection
            connection.requestMethod = method
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
            connection.disconnect()
            when (code) {
                in 200..299 -> ToolResult(true, "GitHub 요청이 완료되었습니다")
                401, 403 -> ToolResult(false, "GitHub 인증에 실패했습니다")
                404 -> ToolResult(false, "GitHub 저장소를 찾을 수 없습니다")
                422 -> ToolResult(false, "GitHub 요청이 거부되었습니다 (인자를 확인하세요)")
                else -> ToolResult(false, "GitHub 요청에 실패했습니다")
            }
        }.getOrElse { ToolResult(false, "GitHub 연결에 실패했습니다") }
    }

    companion object {
        const val CREDENTIAL_SERVICE = "github"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private val VALID_NAME = Regex("^[A-Za-z0-9_.-]{1,100}$")

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
