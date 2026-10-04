package com.woojik.aircallai

import com.woojik.aircallai.settings.InMemorySettingsStore
import com.woojik.aircallai.tools.GitHubApiClient
import com.woojik.aircallai.tools.GitHubTool
import com.woojik.aircallai.tools.PersistedToolPermissionStore
import com.woojik.aircallai.tools.ToolApprovalCoordinator
import com.woojik.aircallai.tools.ToolExecutor
import com.woojik.aircallai.tools.ToolRequest
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import com.woojik.aircallai.tools.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD-06 승인 증분: 승인 코디네이터의 submit/approve/deny 흐름을 검증한다.
 * approve()는 승인을 영속 저장하고 실제 실행까지 이어져야 한다.
 * 여기서는 자격증명이 없어 실행 결과가 "인증 없음" 실패로 나오는 것으로 실행 도달을 확인한다.
 */
class ToolApprovalCoordinatorTest {

    @Test fun suspendedTurnReceivesActualResultAndDoubleTapWritesOnlyOnce() = runTest {
        var count = 0
        val tool = object : Tool {
            override val name = "notes"
            override val description = "test"
            override fun riskFor(action: String) = ToolRisk.WRITE
            override suspend fun execute(request: ToolRequest): ToolResult { count++; return ToolResult(true, "저장됨") }
        }
        val permissions = InMemoryToolPermissionStore()
        val coordinator = ToolApprovalCoordinator(permissions, ToolExecutor(listOf(tool), permissions), this)
        val result = async { coordinator.awaitResult(ToolRequest("notes", "add_note")) }
        runCurrent()
        assertFalse(result.isCompleted)
        coordinator.approve(); coordinator.approve()
        advanceUntilIdle()
        assertEquals(1, count)
        assertEquals("저장됨", result.await().message)
    }

    @Test fun cancellingWaitingTurnRemovesApprovalAndDoesNotExecute() = runTest {
        val coordinator = coordinator(this)
        val result = async { coordinator.awaitResult(ToolRequest("github", "create_issue")) }
        runCurrent()
        result.cancel()
        advanceUntilIdle()
        coordinator.approve()
        advanceUntilIdle()
        assertNull(coordinator.pending.value)
        assertNull(coordinator.lastResult.value)
    }

    private object NoCredentials : com.woojik.aircallai.core.storage.CredentialManager {
        override suspend fun save(service: String, credential: ByteArray) {}
        override suspend fun load(service: String): ByteArray? = null
        override suspend fun delete(service: String) {}
        override suspend fun clearAll() {}
    }

    @Test
    fun submitExposesPendingRequest() = runTest {
        val coordinator = coordinator(this)
        val request = ToolRequest("github", "create_issue", mapOf("title" to "x"))
        coordinator.submit(request)
        assertEquals(request, coordinator.pending.value)
    }

    @Test
    fun denyDropsRequestWithoutApproval() = runTest {
        val permissions = PersistedToolPermissionStore(InMemorySettingsStore())
        val coordinator = coordinator(this, permissions)
        coordinator.submit(ToolRequest("github", "create_issue"))
        coordinator.deny()
        assertNull(coordinator.pending.value)
        advanceUntilIdle()
        // 거부한 작업은 여전히 승인 없음 → 실행 계층에서 차단된다.
        assertFalse(permissions.isAllowed("github", "create_issue", com.woojik.aircallai.tools.ToolRisk.WRITE))
    }

    @Test
    fun approvePersistsAndExecutes() = runTest {
        val settings = InMemorySettingsStore()
        val permissions = PersistedToolPermissionStore(settings)
        val coordinator = coordinator(this, permissions)
        coordinator.submit(
            ToolRequest("github", "create_issue", mapOf("owner" to "o", "repo" to "r", "title" to "t")),
        )
        coordinator.approve()
        advanceUntilIdle()
        assertNull(coordinator.pending.value)
        // 승인이 영속화되었다: 같은 저장소를 읽는 새 인스턴스에서도 허용된다.
        assertTrue(
            PersistedToolPermissionStore(settings)
                .isAllowed("github", "create_issue", com.woojik.aircallai.tools.ToolRisk.WRITE),
        )
        // 실행까지 도달했다: 자격증명 없음으로 실패 (차단 메시지가 아님).
        val result = coordinator.lastResult.value
        assertNotNull(result)
        assertEquals("GitHub 인증이 설정되지 않았습니다", result!!.message)
    }

    private fun coordinator(
        scope: TestScope,
        permissions: PersistedToolPermissionStore = PersistedToolPermissionStore(InMemorySettingsStore()),
    ): ToolApprovalCoordinator {
        val executor = ToolExecutor(
            listOf(GitHubTool(GitHubApiClient(NoCredentials))),
            permissions,
        )
        return ToolApprovalCoordinator(permissions, executor, scope)
    }
}
