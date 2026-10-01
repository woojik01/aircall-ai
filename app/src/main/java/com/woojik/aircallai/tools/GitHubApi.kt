package com.woojik.aircallai.tools

import com.woojik.aircallai.core.storage.CredentialManager
import java.net.HttpURLConnection
import java.net.URL

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
        return request("GET", "/repos/$owner/$repo")
    }

    private suspend fun request(method: String, path: String): ToolResult {
        val token = credentials.load(CREDENTIAL_SERVICE)?.toString(Charsets.UTF_8)
            ?: return ToolResult(false, "GitHub 인증이 설정되지 않았습니다")
        return runCatching {
            val connection = URL("https://api.github.com$path").openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("Authorization", "Bearer $token")
            val code = connection.responseCode
            connection.disconnect()
            if (code in 200..299) ToolResult(true, "GitHub 요청이 완료되었습니다")
            else ToolResult(false, "GitHub 요청에 실패했습니다")
        }.getOrElse { ToolResult(false, "GitHub 연결에 실패했습니다") }
    }

    companion object {
        const val CREDENTIAL_SERVICE = "github"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private val VALID_NAME = Regex("^[A-Za-z0-9_.-]{1,100}$")
    }
}
