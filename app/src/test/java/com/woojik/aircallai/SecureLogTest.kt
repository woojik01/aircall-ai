package com.woojik.aircallai

import com.woojik.aircallai.core.logging.SecureLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureLogTest {

    @Test
    fun `mask hides key and token values`() {
        val masked = SecureLog.mask("api_key=abc123 token=xyz secret word")
        assertEquals("api_key=*** token=*** secret word", masked)
    }

    @Test
    fun `mask leaves plain messages intact`() {
        assertEquals("conversation started", SecureLog.mask("conversation started"))
    }
}
