package com.woojik.aircallai

import com.woojik.aircallai.settings.InMemorySettingsStore
import com.woojik.aircallai.settings.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PRD-09 소셜 로그인 UX: OAuth Client ID 기본값(BuildConfig)과 사용자 재정의 우선순위 검증.
 * Client ID는 공개값이므로 가짜 문자열만 사용한다.
 */
class SettingsRepositoryOAuthDefaultsTest {

    @Test
    fun usesBuildTimeDefaultsWhenNothingStored() {
        val repo = SettingsRepository(
            InMemorySettingsStore(),
            defaultGithubOAuthClientId = "Iv1.default",
            defaultGoogleOAuthClientId = "google.default.apps.googleusercontent.com",
        )
        assertEquals("Iv1.default", repo.githubOAuthClientId())
        assertEquals("google.default.apps.googleusercontent.com", repo.googleOAuthClientId())
    }

    @Test
    fun storedValueOverridesDefault() {
        val repo = SettingsRepository(
            InMemorySettingsStore(),
            defaultGithubOAuthClientId = "Iv1.default",
        )
        repo.setGithubOAuthClientId("Iv1.custom")
        assertEquals("Iv1.custom", repo.githubOAuthClientId())
    }

    @Test
    fun blankStoredValueFallsBackToDefault() {
        val repo = SettingsRepository(
            InMemorySettingsStore(),
            defaultGoogleOAuthClientId = "google.default.apps.googleusercontent.com",
        )
        repo.setGoogleOAuthClientId("   ")
        assertEquals("google.default.apps.googleusercontent.com", repo.googleOAuthClientId())
    }
}
