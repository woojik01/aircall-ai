package com.woojik.aircallai

import com.woojik.aircallai.diagnostics.DebugCrashReport
import org.junit.Assert.*
import org.junit.Test

class DebugCrashReportTest {
    @Test fun existingLowMemoryReportIsExplainedAsSystemTermination() {
        val report = "time=123\nAndroid exit=LOW_MEMORY; status=0; stage=screen-ready\n" +
            "PSS=0 KB; RSS=165248 KB (last sampled)\n"
        val explanation = requireNotNull(DebugCrashReport.exitInterpretation(report))
        assertTrue(explanation.contains("Android가 프로세스를 종료"))
        assertTrue(explanation.contains("마지막 측정값"))
        assertTrue(explanation.contains("0은 측정값이 없을 수"))
        assertFalse(explanation.startsWith("종료 분류: 앱 충돌"))
    }

    @Test fun crashAnrAndUnclassifiedReportsKeepDistinctInterpretations() {
        assertEquals("종료 분류: 앱 충돌 (JAVA_CRASH)",
            DebugCrashReport.exitInterpretation("Android exit=JAVA_CRASH; status=0"))
        assertEquals("종료 분류: 앱 충돌 (NATIVE_CRASH)",
            DebugCrashReport.exitInterpretation("Android exit=NATIVE_CRASH; status=0"))
        assertEquals("종료 분류: 앱 응답 없음 (ANR)",
            DebugCrashReport.exitInterpretation("Android exit=ANR; status=0"))
        assertNull(DebugCrashReport.exitInterpretation(null))
        assertNull(DebugCrashReport.exitInterpretation("java.lang.IllegalStateException\n"))
        assertNull(DebugCrashReport.exitInterpretation("Android exit=UNKNOWN; status=0"))
    }

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
