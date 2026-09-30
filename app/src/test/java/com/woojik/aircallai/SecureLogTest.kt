package com.woojik.aircallai

import com.woojik.aircallai.core.logging.SecureLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureLogTest {

    @Test
    fun maskHidesKeyAndTokenValues() {
        val masked = SecureLog.mask("api_key=abc123 token=xyz secret word")
        assertEquals("api_key=*** token=*** secret word", masked)
    }

    @Test
    fun maskLeavesPlainMessagesIntact() {
        assertEquals("conversation started", SecureLog.mask("conversation started"))
    }

    @Test
    fun maskRedactsCredentialInAnyCase() {
        val masked = SecureLog.mask("Credential=hunter2 CREDENTIAL=x")
        assertFalse(masked.contains("hunter2"))
        assertFalse(masked.contains("=x"))
    }
}
