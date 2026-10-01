package com.woojik.aircallai

import com.woojik.aircallai.core.storage.CredentialManager
import com.woojik.aircallai.tools.GitHubApiClient
import com.woojik.aircallai.tools.GitHubTool
import com.woojik.aircallai.tools.ToolRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * PRD-06: GitHubTool 인자/액션 검증.
 * 두 케이스 모두 API 호출 전에 검증 단계에서 실패해야 하므로
 * 실제 GitHubApiClient에 자격증명 없는 CredentialManager만 넣어 테스트한다.
 * (Kotlin 기본 final 클래스는 상속할 수 없어 fake 서브클래스를 만들지 않는다.)
 */
class GitHubApiToolTest {
    private object NoCredentials : CredentialManager {
        override suspend fun save(service: String, credential: ByteArray) {}
        override suspend fun load(service: String): ByteArray? = null
        override suspend fun delete(service: String) {}
        override suspend fun clearAll() {}
    }

    @Test
    fun unknownActionIsRejected() = runTest {
        val tool = GitHubTool(GitHubApiClient(NoCredentials))
        val result = tool.execute(
            ToolRequest("github", "delete_repository"),
        )
        assertFalse(result.success)
    }

    @Test
    fun missingArgumentsAreRejected() = runTest {
        val tool = GitHubTool(GitHubApiClient(NoCredentials))
        val result = tool.execute(
            ToolRequest("github", "read_repository", mapOf("owner" to "woojik01")),
        )
        assertFalse(result.success)
    }
}
