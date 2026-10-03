package com.woojik.aircallai

import com.woojik.aircallai.auth.GoogleOAuthClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleOAuthClientTest {

    @Test
    fun providerIsGmail() {
        assertEquals("gmail", GoogleOAuthClient().provider)
    }

    @Test
    fun googleUsesNarrowGmailScopes() {
        assertEquals(
            "https://www.googleapis.com/auth/gmail.modify",
            GoogleOAuthClient.SCOPE_GMAIL_MODIFY,
        )
        assertEquals(
            "https://www.googleapis.com/auth/gmail.send",
            GoogleOAuthClient.SCOPE_GMAIL_SEND,
        )
    }

    @Test
    fun refreshRequiresReauthorizationOnAndroid() = kotlinx.coroutines.test.runTest {
        assertNull(
            GoogleOAuthClient().refresh(
                com.woojik.aircallai.auth.OAuthTokens(
                    accessToken = "access",
                    refreshToken = null,
                    expiresAtEpochMs = 1L,
                ),
            ),
        )
    }

    @Test
    fun customRedirectIsNoLongerGenerated() {
        assertNull(
            GoogleOAuthClient.redirectUriFor(
                "53717304535-4am22ke1q8ir76ih04s7ha3hegrl25lr.apps.googleusercontent.com",
            ),
        )
        assertNull(
            GoogleOAuthClient.callbackSchemeFor(
                "53717304535-4am22ke1q8ir76ih04s7ha3hegrl25lr.apps.googleusercontent.com",
            ),
        )
    }
}
