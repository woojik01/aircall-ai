package com.woojik.aircallai

import com.woojik.aircallai.core.storage.CredentialManager
import com.woojik.aircallai.tools.GmailApiClient
import com.woojik.aircallai.tools.GmailTool
import com.woojik.aircallai.tools.InMemoryToolPermissionStore
import com.woojik.aircallai.tools.ToolExecutor
import com.woojik.aircallai.tools.ToolRequest
import com.woojik.aircallai.tools.ToolResult
import com.woojik.aircallai.tools.ToolRisk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD-06 Gmail Adapter: 발송 인자 검증, 주소/헤더 검증, 승인 계층 차단을 검증한다.
 * 검증 실패 케이스는 네트워크 호출 전에 끝나므로 자격증명 없는 Client로 테스트한다.
 */
class GmailToolTest {

    private object NoCredentials : CredentialManager {
        override suspend fun save(service: String, credential: ByteArray) {}
        override suspend fun load(service: String): ByteArray? = null
        override suspend fun delete(service: String) {}
        override suspend fun clearAll() {}
    }

    private fun tool() = GmailTool(GmailApiClient(NoCredentials))

    @Test
    fun sendEmailIsClassifiedAsWriteRisk() {
        assertEquals(ToolRisk.WRITE, tool().riskFor("send_email"))
    }

    @Test
    fun sendEmailRequiresAllArguments() = runTest {
        val t = tool()
        assertFalse(t.execute(ToolRequest("gmail", "send_email", mapOf("subject" to "s", "body" to "b"))).success)
        assertFalse(t.execute(ToolRequest("gmail", "send_email", mapOf("to" to "a@b.com", "body" to "b"))).success)
        assertFalse(t.execute(ToolRequest("gmail", "send_email", mapOf("to" to "a@b.com", "subject" to "s"))).success)
    }

    @Test
    fun sendEmailRejectsInvalidAddress() = runTest {
        val result: ToolResult = tool().execute(
            ToolRequest("gmail", "send_email", mapOf("to" to "not-an-email", "subject" to "s", "body" to "b")),
        )
        assertFalse(result.success)
        assertEquals("받는 사람 주소가 올바르지 않습니다", result.message)
    }

    @Test
    fun headerInjectionIsSanitized() {
        val client = GmailApiClient(NoCredentials)
        val raw = client.rawMessage("a@b.com", "제목\r\nBcc: evil@c.com", "내용")
        // base64url 디코딩해 Subject 헤더에 CRLF가 남아 있지 않은지 확인한다.
        val decoded = String(java.util.Base64.getUrlDecoder().decode(raw), Charsets.UTF_8)
        assertFalse(decoded.contains("\r\nBcc"))
        val expectedSubject = java.util.Base64.getEncoder()
            .encodeToString("제목 Bcc: evil@c.com".toByteArray(Charsets.UTF_8))
        assertTrue(decoded.contains("Subject: =?UTF-8?B?$expectedSubject?="))
    }

    @Test
    fun unapprovedSendEmailIsBlockedByExecutor() = runTest {
        val executor = ToolExecutor(listOf(tool()), InMemoryToolPermissionStore())
        val blocked = executor.execute(
            ToolRequest("gmail", "send_email", mapOf("to" to "a@b.com", "subject" to "s", "body" to "b")),
        )
        assertFalse(blocked.success)
        assertEquals("사용자 승인이 필요한 작업입니다", blocked.message)
    }

    @Test
    fun approvedSendEmailPassesApprovalLayer() = runTest {
        // 승인 통과 후에는 자격증명 없음 오류로 실패해야 한다 (차단 메시지와 다름).
        val permissions = InMemoryToolPermissionStore()
        permissions.setAllowed("gmail", "send_email", true)
        val executor = ToolExecutor(listOf(tool()), permissions)
        val result: ToolResult = executor.execute(
            ToolRequest("gmail", "send_email", mapOf("to" to "a@b.com", "subject" to "s", "body" to "b")),
        )
        assertFalse(result.success)
        assertEquals("Gmail 인증이 설정되지 않았습니다", result.message)
    }
}
