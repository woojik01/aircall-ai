package com.woojik.aircallai

import com.woojik.aircallai.session.SessionController
import com.woojik.aircallai.session.SessionStatus
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ImmediateSessionPauseTest {
    @Test fun pauseCancelsActiveTurnAndResumeWaitsForCleanup() = runTest {
        var started = 0
        var cleaned = 0
        val controller = SessionController(backgroundScope)
        controller.start {
            started++
            try { awaitCancellation() } finally { cleaned++ }
        }
        runCurrent()
        controller.pause()
        assertEquals(SessionStatus.Paused, controller.status.value)
        controller.resume()
        runCurrent()
        assertEquals(2, started)
        assertEquals(1, cleaned)
        controller.end()
        runCurrent()
        assertEquals(2, cleaned)
        assertEquals(SessionStatus.Ended, controller.status.value)
    }
}
