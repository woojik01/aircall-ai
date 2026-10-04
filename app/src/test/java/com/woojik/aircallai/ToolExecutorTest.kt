package com.woojik.aircallai

import com.woojik.aircallai.tools.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolExecutorTest {
    @Test fun onlyActualExecutionsProduceResultEventsAndObserverFailureDoesNotRetry() = runTest {
        val events = mutableListOf<ToolExecutionEvent>()
        val permissions = InMemoryToolPermissionStore()
        val executor = ToolExecutor(listOf(MockGitHubTool()), permissions, onEvent = { events += it }, roomId = { "room" })
        executor.execute(ToolRequest("github", "create_issue"))
        assertTrue(events.isEmpty())
        executor.execute(ToolRequest("github", "read_repository"))
        assertEquals(listOf(ToolExecutionStatus.RUNNING, ToolExecutionStatus.SUCCEEDED), events.map { it.status })
        assertTrue(events.all { it.roomId == "room" })
        assertEquals(events[0].id, events[1].id)
        assertTrue(ToolExecutor(listOf(MockGitHubTool()), permissions, onEvent = { error("notifications disabled") })
            .execute(ToolRequest("github", "read_repository")).success)
    }
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
