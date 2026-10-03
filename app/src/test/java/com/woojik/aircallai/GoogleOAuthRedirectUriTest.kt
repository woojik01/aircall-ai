package com.woojik.aircallai

import com.woojik.aircallai.auth.GoogleOAuthClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Android AuthorizationClient Google authorization contract. */
class GoogleOAuthRedirectUriTest {

    @Test
    fun browserRedirectCallbacksAreDisabled() {
        assertNull(GoogleOAuthClient.redirectUriFor("123456789012-abc123defg.apps.googleusercontent.com"))
        assertNull(GoogleOAuthClient.callbackSchemeFor("123456789012-abc123defg.apps.googleusercontent.com"))
    }

    @Test
    fun requestsOnlyTheRequiredGmailScopes() {
        assertEquals("https://www.googleapis.com/auth/gmail.modify", GoogleOAuthClient.SCOPE_GMAIL_MODIFY)
        assertEquals("https://www.googleapis.com/auth/gmail.send", GoogleOAuthClient.SCOPE_GMAIL_SEND)
    }
}
