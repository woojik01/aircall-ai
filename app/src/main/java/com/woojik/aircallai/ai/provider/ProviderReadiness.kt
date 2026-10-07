package com.woojik.aircallai.ai.provider

import kotlinx.coroutines.CancellationException

/** Optional AI readiness must never prevent opening chat and settings. */
internal suspend fun providerReadinessOrFalse(check: suspend () -> Boolean): Boolean = try {
    check()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    false
} catch (_: LinkageError) {
    false
}
