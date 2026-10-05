package com.woojik.aircallai.tools

import com.woojik.aircallai.settings.SettingsStore

/**
 * 이전 버전의 지속 승인 목록을 표시·해제하기 위한 호환 저장소.
 * 저장된 승인은 실행 권한을 부여하지 않는다. 변경 작업은 요청마다 승인한다.
 */
class PersistedToolPermissionStore(
    private val store: SettingsStore,
) : ToolPermissionStore {

    override suspend fun isAllowed(toolName: String, action: String, risk: ToolRisk): Boolean =
        risk == ToolRisk.READ

    override suspend fun setAllowed(toolName: String, action: String, allowed: Boolean) {
        val current = allowed().toMutableSet()
        val k = key(toolName, action)
        if (allowed) current.add(k) else current.remove(k)
        store.putString(KEY_ALLOWED, current.joinToString(","))
    }

    /** 설정 화면 표시용: 현재 승인된 "tool:action" 목록 (정렬). */
    fun approvedActions(): List<String> = allowed().sorted()

    /** 설정에서 개별 승인 해제. */
    suspend fun revoke(allowedKey: String) {
        val current = allowed().toMutableSet()
        current.remove(allowedKey)
        store.putString(KEY_ALLOWED, current.joinToString(","))
    }

    private fun allowed(): Set<String> =
        (store.getString(KEY_ALLOWED) ?: "")
            .split(",")
            .filter { it.isNotBlank() }
            .toSet()

    private fun key(toolName: String, action: String) = toolName + ":" + action

    companion object {
        private const val KEY_ALLOWED = "tool_allowed_actions"
    }
}
