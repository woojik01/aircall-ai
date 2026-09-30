package com.woojik.aircallai

import com.woojik.aircallai.settings.InMemorySettingsStore
import com.woojik.aircallai.settings.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {

    @Test
    fun defaultAiModeIsLocal() {
        assertEquals(SettingsRepository.MODE_LOCAL, SettingsRepository(InMemorySettingsStore()).aiProviderMode())
    }

    @Test
    fun modeRoundTrips() {
        val repo = SettingsRepository(InMemorySettingsStore())
        repo.setAiProviderMode(SettingsRepository.MODE_CLOUD)
        assertEquals(SettingsRepository.MODE_CLOUD, repo.aiProviderMode())
    }

    @Test
    fun unknownModesRejected() {
        val repo = SettingsRepository(InMemorySettingsStore())
        var threw = false
        try { repo.setAiProviderMode("hybrid") } catch (e: IllegalArgumentException) { threw = true }
        assertTrue(threw)
        assertEquals(SettingsRepository.MODE_LOCAL, repo.aiProviderMode())
    }
}
