package com.woojik.aircallai

import com.woojik.aircallai.tools.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubToolTest {
    @Test fun unknownActionIsRejected() = runTest {
        val result = GitHubTool(FakeGitHubApiClient()).execute(
            ToolRequest("github", "delete_repository")
        )
        assertFalse(result.success)
    }

    @Test fun missingArgumentsAreRejected() = runTest {
        val result = GitHubTool(FakeGitHubApiClient()).execute(
            ToolRequest("github", "read_repository", mapOf("owner" to "woojik01"))
        )
        assertFalse(result.success)
    }

    private class FakeGitHubApiClient : GitHubApiClient(
        object : com.woojik.aircallai.core.storage.CredentialManager {
            override suspend fun save(service: String, credential: ByteArray) {}
            override suspend fun load(service: String): ByteArray? = null
            override suspend fun delete(service: String) {}
            override suspend fun clearAll() {}
        }
    )
}
