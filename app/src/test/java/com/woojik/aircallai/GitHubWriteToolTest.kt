package com.woojik.aircallai

import com.woojik.aircallai.core.storage.CredentialManager
import com.woojik.aircallai.settings.InMemorySettingsStore
import com.woojik.aircallai.tools.GitHubApiClient
import com.woojik.aircallai.tools.GitHubHttpResponse
import com.woojik.aircallai.tools.GitHubTool
import com.woojik.aircallai.tools.GitHubTransport
import com.woojik.aircallai.tools.SettingsToolPermissionStore
import com.woojik.aircallai.tools.ToolExecutor
import com.woojik.aircallai.tools.ToolRequest
import com.woojik.aircallai.tools.ToolRisk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubWriteToolTest {
    private class FakeCredentials(private val token: String?) : CredentialManager {
        override suspend fun save(service: String, credential: ByteArray) {}
        override suspend fun load(service: String): ByteArray? = token?.toByteArray()
        override suspend fun delete(service: String) {}
        override suspend fun clearAll() {}
    }

    private class RecordingTransport(
        private val code: Int = 201,
        private val fail: Boolean = false,
        private val body: String = "{\"number\":7,\"html_url\":\"https://github.com/woojik01/aircall-ai/issues/7\"}",
    ) : GitHubTransport {
        val calls = mutableListOf<List<String?>>()
        override fun send(method: String, url: String, token: String, jsonBody: String?): GitHubHttpResponse {
            calls += listOf(method, url, token, jsonBody)
            if (fail) throw IOException("boom $token")
            return GitHubHttpResponse(code, body)
        }
    }

    private val token = "ghp_secret_token"
    private val issueArgs = mapOf("owner" to "woojik01", "repo" to "aircall-ai", "title" to "버그 \"재현\"", "body" to "line1\nline2")

    private fun tool(transport: GitHubTransport, token: String? = this.token) =
        GitHubTool(GitHubApiClient(FakeCredentials(token), transport))

    @Test fun createIssuePostsEscapedJson() = runTest {
        val transport = RecordingTransport()
        val result = tool(transport).execute(ToolRequest("github", "create_issue", issueArgs))
        assertTrue(result.success)
        assertEquals("Issue #7가 생성되었습니다: https://github.com/woojik01/aircall-ai/issues/7", result.message)
        val (method, url, sentToken, body) = transport.calls.single()
        assertEquals("POST", method)
        assertEquals("https://api.github.com/repos/woojik01/aircall-ai/issues", url)
        assertEquals(token, sentToken)
        assertEquals("{\"title\":\"버그 \\\"재현\\\"\",\"body\":\"line1\\nline2\"}", body)
    }

    @Test fun createPullRequestPostsToPullsEndpoint() = runTest {
        val transport = RecordingTransport()
        val args = mapOf("owner" to "woojik01", "repo" to "aircall-ai", "title" to "PR", "head" to "feat/x", "base" to "main")
        val result = tool(transport).execute(ToolRequest("github", "create_pull_request", args))
        assertTrue(result.success)
        assertEquals("https://api.github.com/repos/woojik01/aircall-ai/pulls", transport.calls.single()[1])
        assertEquals("{\"title\":\"PR\",\"head\":\"feat/x\",\"base\":\"main\"}", transport.calls.single()[3])
    }

    @Test fun invalidBranchIsRejectedBeforeNetwork() = runTest {
        val transport = RecordingTransport()
        val args = mapOf("owner" to "o", "repo" to "r", "title" to "t", "head" to "bad branch", "base" to "main")
        assertFalse(tool(transport).execute(ToolRequest("github", "create_pull_request", args)).success)
        assertTrue(transport.calls.isEmpty())
    }

    @Test fun missingTokenFailsWithoutNetwork() = runTest {
        val transport = RecordingTransport()
        val result = tool(transport, token = null).execute(ToolRequest("github", "create_issue", issueArgs))
        assertFalse(result.success)
        assertTrue(transport.calls.isEmpty())
    }

    @Test fun statusCodesAreMappedWithoutLeakingToken() = runTest {
        val expected = mapOf(
            401 to "GitHub 인증에 실패했습니다. 토큰을 확인해야 합니다",
            403 to "GitHub 권한이 없습니다. 토큰 권한을 확인해야 합니다",
            404 to "GitHub 저장소를 찾을 수 없습니다",
            422 to "GitHub 요청 내용이 올바르지 않습니다",
            500 to "GitHub 요청에 실패했습니다",
        )
        expected.forEach { (code, message) ->
            val result = tool(RecordingTransport(code)).execute(ToolRequest("github", "create_issue", issueArgs))
            assertFalse(result.success)
            assertEquals(message, result.message)
        }
        val network = tool(RecordingTransport(fail = true)).execute(ToolRequest("github", "create_issue", issueArgs))
        assertEquals("GitHub 연결에 실패했습니다", network.message)
        assertFalse(network.message.contains(token))
    }

    @Test fun readRepositoryReturnsSummaryForAi() = runTest {
        val json = "{\"full_name\":\"woojik01/aircall-ai\",\"private\":false,\"description\":null," +
            "\"language\":\"Kotlin\",\"default_branch\":\"main\",\"stargazers_count\":3,\"open_issues_count\":1}"
        val result = tool(RecordingTransport(200, body = json))
            .execute(ToolRequest("github", "read_repository", mapOf("owner" to "woojik01", "repo" to "aircall-ai")))
        assertTrue(result.success)
        assertEquals(
            "저장소 woojik01/aircall-ai (공개), 언어: Kotlin, 기본 브랜치: main, 스타 3개, 열린 이슈 1개",
            result.message,
        )
    }

    @Test fun getUserNeedsNoArguments() = runTest {
        val transport = RecordingTransport(200, body = "{\"login\":\"woojik01\"}")
        val result = tool(transport).execute(ToolRequest("github", "get_user"))
        assertEquals("GitHub 로그인 사용자: woojik01", result.message)
        assertEquals("https://api.github.com/user", transport.calls.single()[1])
    }

    @Test fun riskLevels() {
        val t = tool(RecordingTransport())
        assertEquals(ToolRisk.READ, t.riskFor("read_repository"))
        assertEquals(ToolRisk.WRITE, t.riskFor("create_issue"))
        assertEquals(ToolRisk.WRITE, t.riskFor("create_pull_request"))
        assertEquals(ToolRisk.DESTRUCTIVE, t.riskFor("delete_repository"))
    }

    @Test fun writeRequiresPersistedApproval() = runTest {
        val store = InMemorySettingsStore()
        val transport = RecordingTransport()
        val executor = ToolExecutor(listOf(tool(transport)), SettingsToolPermissionStore(store))
        assertFalse(executor.execute(ToolRequest("github", "create_issue", issueArgs)).success)
        assertTrue(transport.calls.isEmpty())

        SettingsToolPermissionStore(store).setAllowed("github", "create_issue", true)
        val reloaded = ToolExecutor(listOf(tool(transport)), SettingsToolPermissionStore(store))
        assertTrue(reloaded.execute(ToolRequest("github", "create_issue", issueArgs)).success)
        assertFalse(reloaded.execute(ToolRequest("github", "create_pull_request", issueArgs)).success)

        SettingsToolPermissionStore(store).setAllowed("github", "create_issue", false)
        assertFalse(reloaded.execute(ToolRequest("github", "create_issue", issueArgs)).success)
    }
}
