package com.woojik.aircallai

import com.woojik.aircallai.auth.GoogleOAuthClient
import com.woojik.aircallai.privacy.*
import com.woojik.aircallai.settings.InMemorySettingsStore
import com.woojik.aircallai.settings.SettingsRepository
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlayPrivacyTest {
    @Test fun cloudConsentMustBeExplicitAndEndpointSpecific() {
        val store = InMemorySettingsStore()
        val settings = SettingsRepository(store)
        assertFalse(settings.hasCloudDisclosure())
        assertFalse(settings.hasSpeechDisclosure())
        assertFalse(settings.hasMinimumAgeAcknowledgement())
        settings.acceptMinimumAgeAcknowledgement()
        assertTrue(SettingsRepository(store).hasMinimumAgeAcknowledgement())
        settings.acceptCloudDisclosure(); settings.acceptSpeechDisclosure()
        assertTrue(SettingsRepository(store).hasCloudDisclosure())
        assertTrue(SettingsRepository(store).hasSpeechDisclosure())
        settings.setCloudBaseUrl("https://new-ai.example.net/v1")
        assertFalse(settings.hasCloudDisclosure())
    }

    @Test fun reportDoesNotIncludeResponseUnlessExplicitlyAdded() {
        val report = ContentReport(reason = "부적절한 답변", appVersion = "0.4.0", androidApi = 36)
        val payload = JSONObject(report.payload())
        assertEquals("", payload.getString("response"))
        assertEquals(setOf("id", "category", "reason", "response", "appVersion", "androidApi"), payload.keys().asSequence().toSet())
    }

    @Test fun reportSuccessRequiresTheSameReceiptId() = runTest {
        val report = ContentReport(reason = "신고", appVersion = "0.4.0", androidApi = 36)
        for (response in listOf("{\"ok\":false}", "{\"ok\":true,\"id\":\"wrong\"}", "<html>Login</html>")) {
            val client = ContentReportClient("https://reports.example.net/exec", ReportTransport { _, _ -> response })
            try { client.submit(report); fail("Must not claim success") } catch (_: Exception) { }
        }
        val client = ContentReportClient("https://reports.example.net/exec", ReportTransport { _, _ ->
            JSONObject().put("ok", true).put("id", report.id).toString()
        })
        assertEquals(report.id, client.submit(report).id)
    }

    @Test fun unconfiguredAndNonHttpsReportsNeverReachTransport() = runTest {
        for (endpoint in listOf("", "http://reports.example.net", "https://user:secret@reports.example.net")) {
            var sent = false
            val client = ContentReportClient(endpoint, ReportTransport { _, _ -> sent = true; "{}" })
            try { client.submit(ContentReport(reason = "신고", appVersion = "0.4.0", androidApi = 36)); fail() }
            catch (_: IllegalStateException) { }
            assertFalse(sent)
        }
    }

    @Test fun gmailAuthorizationDoesNotRequestMailboxAccess() {
        assertEquals(listOf(GoogleOAuthClient.SCOPE_GMAIL_SEND, GoogleOAuthClient.SCOPE_CALENDAR_EVENTS), GoogleOAuthClient.REQUIRED_SCOPES)
        assertFalse(GoogleOAuthClient.REQUIRED_SCOPES.contains(GoogleOAuthClient.SCOPE_GMAIL_MODIFY))
    }
}
