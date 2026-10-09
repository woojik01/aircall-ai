package com.woojik.aircallai.ai.local

/** RAM is a preflight hint, not a promise that a native model will run. */
fun recommendedLocalModel(totalRamBytes: Long, freeBytes: Long): LocalModelInfo? =
    LocalModelRegistry.models.firstOrNull {
        totalRamBytes >= it.minRamMb * 1024L * 1024L && freeBytes >= it.sizeBytes + 64 * 1024 * 1024L
    }
