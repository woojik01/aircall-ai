package com.woojik.aircallai

import com.woojik.aircallai.ai.provider.providerReadinessOrFalse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ProviderReadinessTest {
    @Test fun unreadableCredentialDisablesAiInsteadOfCrashingStartup() = runTest {
        assertFalse(providerReadinessOrFalse { throw java.io.IOException("unreadable file") })
        assertFalse(providerReadinessOrFalse { throw UnsatisfiedLinkError("native library unavailable") })
        assertTrue(providerReadinessOrFalse { true })
    }

    @Test fun closingActivityStillCancelsReadinessWork() = runTest {
        val cancellation = CancellationException("activity closed")
        try {
            providerReadinessOrFalse { throw cancellation }
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }
}
