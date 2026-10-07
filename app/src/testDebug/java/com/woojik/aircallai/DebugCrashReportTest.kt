package com.woojik.aircallai

import com.woojik.aircallai.diagnostics.DebugCrashReport
import org.junit.Assert.*
import org.junit.Test

class DebugCrashReportTest {
    @Test fun capturesCauseFramesWithoutExceptionMessagesOrTokens() {
        val cause = IllegalArgumentException("secret-access-token")
        val failure = IllegalStateException("private-chat-text", cause)
        val report = DebugCrashReport.exception(failure)
        assertTrue(report.contains("java.lang.IllegalStateException"))
        assertTrue(report.contains("java.lang.IllegalArgumentException"))
        assertTrue(report.contains("DebugCrashReportTest.kt"))
        assertFalse(report.contains("secret-access-token"))
        assertFalse(report.contains("private-chat-text"))
    }

    @Test fun cyclicCauseChainAndHugeStackAreBounded() {
        val a = RuntimeException("a")
        val b = RuntimeException("b")
        a.initCause(b); b.initCause(a)
        a.stackTrace = Array(1000) { StackTraceElement("SomeClass", "method", "Source.kt", it) }
        val report = DebugCrashReport.exception(a)
        assertTrue(report.length <= DebugCrashReport.MAX_CHARS)
        assertEquals(24, Regex("SomeClass.method").findAll(report).count())
    }
}
