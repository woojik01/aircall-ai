package com.woojik.aircallai

import com.woojik.aircallai.settings.InMemorySettingsStore
import com.woojik.aircallai.tools.PersistedToolPermissionStore
import com.woojik.aircallai.tools.ToolRisk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD-06 승인 증분: WRITE 승인 상태가 일반 설정에 영속화되는지 검증한다.
 * 승인 → 재시작(새 인스턴스, 같은 저장소) → 여전히 허용되어야 한다.
 */
class PersistedToolPermissionStoreTest {

    @Test
    fun readIsAlwaysAllowed() = runTest {
        val store = PersistedToolPermissionStore(InMemorySettingsStore())
        assertTrue(store.isAllowed("github", "read_repository", ToolRisk.READ))
    }

    @Test
    fun writeRequiresApprovalUntilAllowed() = runTest {
        val store = PersistedToolPermissionStore(InMemorySettingsStore())
        assertFalse(store.isAllowed("github", "create_issue", ToolRisk.WRITE))
        store.setAllowed("github", "create_issue", true)
        assertTrue(store.isAllowed("github", "create_issue", ToolRisk.WRITE))
    }

    @Test
    fun approvalSurvivesRestart() = runTest {
        val settings = InMemorySettingsStore()
        val first = PersistedToolPermissionStore(settings)
        first.setAllowed("github", "create_pull_request", true)
        // 앱 재시작: 같은 설정 저장소를 읽는 새 인스턴스
        val second = PersistedToolPermissionStore(settings)
        assertTrue(second.isAllowed("github", "create_pull_request", ToolRisk.WRITE))
        assertEquals(listOf("github:create_pull_request"), second.approvedActions())
    }

    @Test
    fun revokeRemovesApproval() = runTest {
        val settings = InMemorySettingsStore()
        val store = PersistedToolPermissionStore(settings)
        store.setAllowed("github", "create_issue", true)
        store.revoke("github:create_issue")
        assertFalse(store.isAllowed("github", "create_issue", ToolRisk.WRITE))
        assertTrue(store.approvedActions().isEmpty())
    }

    @Test
    fun denyApprovalRemovesIt() = runTest {
        val store = PersistedToolPermissionStore(InMemorySettingsStore())
        store.setAllowed("github", "create_issue", true)
        store.setAllowed("github", "create_issue", false)
        assertFalse(store.isAllowed("github", "create_issue", ToolRisk.WRITE))
    }
}
