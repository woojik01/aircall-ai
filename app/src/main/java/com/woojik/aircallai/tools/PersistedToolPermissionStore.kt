package com.woojik.aircallai.tools

import com.woojik.aircallai.settings.SettingsStore

/**
 * PRD-06: WRITE 작업 승인 상태를 일반 설정(SettingsStore)에 영속화한다.
 * 키 형식은 "tool:action" 목록이며 민감 정보가 아니므로 PRD-02 분류상 일반 설정에 둔다.
 * 앱 재시작 후에도 승인이 유지되고, 사용자가 설정에서 개별 해제할 수 있다.
 */
class PersistedToolPermissionStore(
    private val store: SettingsStore,
) : ToolPermissionStore {

    override suspend fun isAllowed(toolName: String, action: String, risk: ToolRisk): Boolean =
        risk == ToolRisk.READ || key(toolName, action) in allowed()

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
