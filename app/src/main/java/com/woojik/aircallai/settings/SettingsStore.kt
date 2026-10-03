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
class SettingsRepository(
    private val store: SettingsStore,
    /**
     * PRD-09 소셜 로그인 UX: 빌드 시점에 등록된 OAuth App Client ID 기본값(BuildConfig).
     * 공개값이므로 앱에 포함해도 안전하며, 사용자가 고급 설정에서 입력한 값이 우선한다.
     */
    private val defaultGithubOAuthClientId: String = "",
    private val defaultGoogleOAuthClientId: String = "",
) {

    fun aiProviderMode(): String = store.getString(KEY_AI_MODE) ?: MODE_LOCAL

    fun setAiProviderMode(mode: String) {
        require(mode == MODE_LOCAL || mode == MODE_CLOUD) { "unknown mode: " + mode }
        store.putString(KEY_AI_MODE, mode)
    }

    /**
     * PRD-04 Cloud Mode: OpenAI-compatible chat completions endpoint.
     * The endpoint is non-sensitive and is stored in general settings.
     * The actual calls are performed by the app itself.
     *
     * Fresh installations and older installations that never configured an endpoint
     * use Groq's current OpenAI-compatible endpoint by default, so Cloud mode cannot
     * fail with "endpoint not configured" merely because the endpoint was never saved.
     */
    fun cloudBaseUrl(): String {
        val stored = store.getString(KEY_CLOUD_BASE_URL)?.trim()
        return if (stored.isNullOrEmpty()) {
            DEFAULT_CLOUD_BASE_URL
        } else {
            stored
        }
    }

    fun setCloudBaseUrl(url: String) {
        store.putString(KEY_CLOUD_BASE_URL, url.trim())
    }

    /**
     * Groq의 구형 Llama 3.3 모델을 이미 저장한 설치본은 자동으로 현재 기본 모델로
     * 마이그레이션한다. 사용자가 다른 모델을 저장한 경우에는 그대로 유지한다.
     */
    fun cloudModel(): String {
        val stored = store.getString(KEY_CLOUD_MODEL)?.trim()
        return when {
            stored.isNullOrEmpty() -> DEFAULT_CLOUD_MODEL
            stored == DEPRECATED_LLAMA_33_MODEL -> {
                store.putString(KEY_CLOUD_MODEL, DEFAULT_CLOUD_MODEL)
                DEFAULT_CLOUD_MODEL
            }
            else -> stored
        }
    }

    fun setCloudModel(model: String) {
        store.putString(KEY_CLOUD_MODEL, model.trim())
    }

    /**
     * 로컬 모델 갤러리(로컬 기능 증분): 사용자가 선택·적용한 로컬 모델 id.
     * 모델 id는 카탈로그(LocalModelRegistry)에 속한 비민감 값이므로 일반 설정에 저장한다.
     * 빈 문자열/blank는 미선택(null)으로 정규화한다.
     */
    fun localModelId(): String? = store.getString(KEY_LOCAL_MODEL)?.trim()?.ifEmpty { null }

    fun setLocalModelId(id: String?) {
        val normalized = id?.trim()
        if (normalized.isNullOrEmpty()) {
            store.putString(KEY_LOCAL_MODEL, "")
        } else {
            store.putString(KEY_LOCAL_MODEL, normalized)
        }
    }

    /**
     * PRD-09 Phase 2: GitHub OAuth App Client ID. 공개 값(비밀이 아님)이므로 일반 설정에
     * 저장한다. 값이 없으면 빌드 시점 기본값(BuildConfig)을 사용한다(소셜 로그인 UX).
     */
    fun githubOAuthClientId(): String {
        val stored = store.getString(KEY_GITHUB_OAUTH_CLIENT_ID)?.trim().orEmpty()
        return stored.ifEmpty { defaultGithubOAuthClientId.trim() }
    }

    fun setGithubOAuthClientId(clientId: String) {
        store.putString(KEY_GITHUB_OAUTH_CLIENT_ID, clientId.trim())
    }

    /**
     * PRD-09 Phase 3: Google OAuth Client ID(웹 애플리케이션 유형). 공개 값이므로 일반 설정에
     * 저장한다. 값이 없으면 빌드 시점 기본값(BuildConfig)을 사용한다(소셜 로그인 UX).
     */
    fun googleOAuthClientId(): String {
        val stored = store.getString(KEY_GOOGLE_OAUTH_CLIENT_ID)?.trim().orEmpty()
        return stored.ifEmpty { defaultGoogleOAuthClientId.trim() }
    }

    fun setGoogleOAuthClientId(clientId: String) {
        store.putString(KEY_GOOGLE_OAUTH_CLIENT_ID, clientId.trim())
    }

    companion object {
        const val MODE_LOCAL = "local"
        const val MODE_CLOUD = "cloud"

        const val DEFAULT_CLOUD_BASE_URL = "https://api.groq.com/openai/v1/chat/completions"
        const val DEFAULT_CLOUD_MODEL = "openai/gpt-oss-120b"
        private const val DEPRECATED_LLAMA_33_MODEL = "llama-3.3-70b-versatile"

        private const val KEY_AI_MODE = "ai_provider_mode"
        private const val KEY_CLOUD_BASE_URL = "cloud_base_url"
        private const val KEY_CLOUD_MODEL = "cloud_model"
        private const val KEY_LOCAL_MODEL = "local_model_id"
        private const val KEY_GITHUB_OAUTH_CLIENT_ID = "github_oauth_client_id"
        private const val KEY_GOOGLE_OAUTH_CLIENT_ID = "google_oauth_client_id"
    }
}
