package com.woojik.aircallai

import com.woojik.aircallai.auth.GoogleOAuthClient
import com.woojik.aircallai.privacy.*
import com.woojik.aircallai.settings.InMemorySettingsStore
import com.woojik.aircallai.settings.SettingsRepository
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

    @Test fun gmailAuthorizationDoesNotRequestMailboxAccess() {
        assertEquals(listOf(GoogleOAuthClient.SCOPE_GMAIL_SEND), GoogleOAuthClient.REQUIRED_SCOPES)
        assertFalse(GoogleOAuthClient.REQUIRED_SCOPES.contains(GoogleOAuthClient.SCOPE_GMAIL_MODIFY))
    }
}
