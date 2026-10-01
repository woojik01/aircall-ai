package com.woojik.aircallai

import com.woojik.aircallai.tools.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolExecutorTest {
    @Test fun readToolRunsWithoutExplicitApproval() = runTest {
        val result = ToolExecutor(listOf(MockGitHubTool()), InMemoryToolPermissionStore())
            .execute(ToolRequest("github", "read_repository"))
        assertTrue(result.success)
    }

    @Test fun writeToolRequiresApproval() = runTest {
        val result = ToolExecutor(listOf(MockGitHubTool()), InMemoryToolPermissionStore())
            .execute(ToolRequest("github", "create_pull_request"))
        assertFalse(result.success)
    }

    @Test fun approvedWriteToolRuns() = runTest {
        val permissions = InMemoryToolPermissionStore()
        permissions.setAllowed("github", "create_issue", true)
        val result = ToolExecutor(listOf(MockGitHubTool()), permissions)
            .execute(ToolRequest("github", "create_issue"))
        assertTrue(result.success)
    }

    @Test fun unknownToolFailsSafely() = runTest {
        val result = ToolExecutor(listOf(MockGitHubTool()), InMemoryToolPermissionStore())
            .execute(ToolRequest("calendar", "read"))
        assertFalse(result.success)
    }
}
