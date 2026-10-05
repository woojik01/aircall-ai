package com.woojik.aircallai

import com.woojik.aircallai.privacy.ContentReport
import com.woojik.aircallai.privacy.ContentReportClient
import com.woojik.aircallai.privacy.ReportTransport
import java.net.URI
import java.net.URLDecoder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ContentReportEmailDraftTest {
    @Test fun emailDraftContainsOnlyReviewedFieldsAndDoesNotSend() = runTest {
        var submitted = false
        val client = ContentReportClient("", ReportTransport { _, _ -> submitted = true; "{}" },
            reportMethod = "email", supportEmail = "support@example.com")
        val report = ContentReport(reason = "신고 + 확인 & 답변?\n둘째 줄", appVersion = "1.0.0", androidApi = 36)
        assertTrue(client.isConfigured)
        val draft = client.emailDraft(report)
        assertEquals("support@example.com", draft.recipient)
        assertTrue(draft.body.contains(report.id))
        assertTrue(draft.body.contains(report.reason))
        assertTrue(draft.body.contains("앱 버전: 1.0.0"))
        assertTrue(draft.body.contains("Android API: 36"))
        assertFalse(draft.body.contains("[사용자가 포함하기로 선택한 AI 응답]"))
        assertFalse(submitted)
        try { client.submit(report); fail("Draft mode must never claim a server receipt") }
        catch (_: IllegalStateException) { }
        assertFalse(submitted)

        val uri = draft.mailtoUri()
        assertEquals("mailto", URI(uri).scheme)
        assertEquals("support@example.com", URLDecoder.decode(uri.substringAfter(':').substringBefore('?'), "UTF-8"))
        val parameters = uri.substringAfter('?').split('&').associate {
            val (key, value) = it.split('=', limit = 2)
            key to URLDecoder.decode(value, "UTF-8")
        }
        assertEquals(setOf("subject", "body"), parameters.keys)
        assertEquals(draft.subject, parameters["subject"])
        assertEquals(draft.body, parameters["body"])
    }

    @Test fun selectedResponseIsOnlyIncludedWhenProvidedAndCanBeEdited() {
        val client = ContentReportClient("", reportMethod = "email", supportEmail = "support@example.com")
        val report = ContentReport(reason = "문제 신고", response = "사용자가 수정한 응답", appVersion = "1.0.0", androidApi = 36)
        assertTrue(client.emailDraft(report).body.contains("[사용자가 포함하기로 선택한 AI 응답]\n사용자가 수정한 응답"))
        try { client.emailDraft(report.copy(response = "a".repeat(4001))); fail("Do not bypass excerpt limit") }
        catch (_: IllegalArgumentException) { }
        try { client.emailDraft(report.copy(reason = "")); fail("Empty report must not launch a draft") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun emailRecipientCannotInjectAnotherRecipientOrMailHeader() {
        for (email in listOf("", "support@example.com?bcc=other@example.com", "support@example.com\r\nBcc:other@example.com",
            "support@example.com,other@example.com", "mailto:support@example.com")) {
            val client = ContentReportClient("", reportMethod = "email", supportEmail = email)
            assertFalse(client.isConfigured)
            try { client.emailDraft(ContentReport(reason = "신고", appVersion = "1.0.0", androidApi = 36)); fail() }
            catch (_: IllegalStateException) { }
        }
    }

    @Test fun unknownReportMethodCannotSubmitThroughTheHttpsEndpoint() = runTest {
        var submitted = false
        val client = ContentReportClient("https://reports.example.com", ReportTransport { _, _ -> submitted = true; "{}" },
            reportMethod = "invalid", supportEmail = "support@example.com")
        assertFalse(client.isConfigured)
        try { client.submit(ContentReport(reason = "신고", appVersion = "1.0.0", androidApi = 36)); fail() }
        catch (_: IllegalStateException) { }
        assertFalse(submitted)
    }
}
