package com.woojik.aircallai.settings

/**
 * Minimal key-value abstraction so settings persistence is testable on the JVM.
 * Only non-sensitive data (PRD-02 "일반 설정") goes through here.
 */
interface SettingsStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

/** In-memory implementation used by unit tests. */
class InMemorySettingsStore : SettingsStore {
    private val map = mutableMapOf<String, String>()
    override fun getString(key: String): String? = map[key]
    override fun putString(key: String, value: String) { map[key] = value }
}

/** Simple general-settings repository (PRD-02 data classification: non-sensitive). */
class SettingsRepository(private val store: SettingsStore) {

    fun aiProviderMode(): String = store.getString(KEY_AI_MODE) ?: MODE_LOCAL

    fun setAiProviderMode(mode: String) {
        require(mode == MODE_LOCAL || mode == MODE_CLOUD) { "unknown mode: " + mode }
        store.putString(KEY_AI_MODE, mode)
    }

    companion object {
        const val MODE_LOCAL = "local"
        const val MODE_CLOUD = "cloud"
        private const val KEY_AI_MODE = "ai_provider_mode"
    }
}
