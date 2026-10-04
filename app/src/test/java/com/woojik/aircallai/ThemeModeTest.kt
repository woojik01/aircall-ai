package com.woojik.aircallai

import com.woojik.aircallai.settings.InMemorySettingsStore
import com.woojik.aircallai.settings.SettingsRepository
import com.woojik.aircallai.ui.usesDarkTheme
import org.junit.Assert.*
import org.junit.Test

class ThemeModeTest {
    @Test fun modePersistsAcrossRepositoryRecreation() {
        val store = InMemorySettingsStore()
        val repository = SettingsRepository(store)
        assertEquals(SettingsRepository.THEME_SYSTEM, repository.themeMode())
        for (mode in listOf("light", "dark", "system")) {
            repository.setThemeMode(mode)
            assertEquals(mode, SettingsRepository(store).themeMode())
        }
        store.putString(SettingsRepository.KEY_THEME_MODE, "invalid")
        assertEquals("system", SettingsRepository(store).themeMode())
    }

    @Test fun explicitModesOverrideSystemAndSystemModeTracksIt() {
        for (systemDark in listOf(false, true)) {
            assertFalse(usesDarkTheme("light", systemDark))
            assertTrue(usesDarkTheme("dark", systemDark))
            assertEquals(systemDark, usesDarkTheme("system", systemDark))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownMode() {
        SettingsRepository(InMemorySettingsStore()).setThemeMode("invalid")
    }
}
