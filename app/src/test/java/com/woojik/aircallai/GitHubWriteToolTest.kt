package com.woojik.aircallai

import com.woojik.aircallai.core.storage.CredentialManager
import com.woojik.aircallai.tools.GitHubApiClient
import com.woojik.aircallai.tools.GitHubTool
import com.woojik.aircallai.tools.InMemoryToolPermissionStore
import com.woojik.aircallai.tools.ToolExecutor
import com.woojik.aircallai.tools.ToolRequest
import com.woojik.aircallai.tools.ToolRisk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD-06 WRITE 증분: GitHub Issue/PR 생성 액션의 위험도 분류,
 * 인자 검증, 승인 계층 차단, JSON 이스케이프를 검증한다.
 * 검증 실패 케이스는 네트워크 호출 전에 끝나므로 자격증명 없는 Client로 테스트한다.
 */
class GitHubWriteToolTest {
    private object NoCredentials : CredentialManager {
        override suspend fun save(service: String, credential: ByteArray) {}
        override suspend fun load(service: String): ByteArray? = null
        override suspend fun delete(service: String) {}
        override suspend fun clearAll() {}
    }

    private fun tool() = GitHubTool(GitHubApiClient(NoCredentials))

    @Test
    fun writeActionsAreClassifiedAsWriteRisk() {
        val tool = tool()
        assertEquals(ToolRisk.WRITE, tool.riskFor("create_issue"))
        assertEquals(ToolRisk.WRITE, tool.riskFor("create_pull_request"))
        assertEquals(ToolRisk.READ, tool.riskFor("read_repository"))
    }

    @Test
    fun createIssueRequiresTitle() = runTest {
        val result = tool().execute(
            ToolRequest("github", "create_issue", mapOf("owner" to "woojik01", "repo" to "aircall-ai")),
        )
        assertFalse(result.success)
    }

    @Test
    fun createPullRequestRequiresBranches() = runTest {
        val result = tool().execute(
            ToolRequest(
                "github",
                "create_pull_request",
                mapOf("owner" to "woojik01", "repo" to "aircall-ai", "title" to "fix"),
            ),
        )
        assertFalse(result.success)
    }

    @Test
    fun unapprovedCreateIssueIsBlockedByExecutor() = runTest {
        val executor = ToolExecutor(listOf(tool()), InMemoryToolPermissionStore())
        val result = executor.execute(
            ToolRequest(
                "github",
                "create_issue",
                mapOf("owner" to "woojik01", "repo" to "aircall-ai", "title" to "x"),
            ),
        )
        assertFalse(result.success)
    }

    @Test
    fun approvedCreateIssuePassesApprovalLayer() = runTest {
        // 승인 통과 후에는 자격증명 없음 오류로 실패해야 한다 (차단 메시지와 다름).
        val permissions = InMemoryToolPermissionStore()
        permissions.setAllowed("github", "create_issue", true)
        val executor = ToolExecutor(listOf(tool()), permissions)
        val result = executor.execute(
            ToolRequest(
                "github",
                "create_issue",
                mapOf("owner" to "woojik01", "repo" to "aircall-ai", "title" to "x"),
            ),
        )
        assertFalse(result.success)
        assertEquals("GitHub 인증이 설정되지 않았습니다", result.message)
    }

    @Test
    fun jsonStringEscapesSpecialCharacters() {
        assertEquals(
            "\"a\\b\"c\\nd\"",
            GitHubApiClient.json("a\\b\"c\\nd"),
        )
    }
}
