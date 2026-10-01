package com.woojik.aircallai.tools

import com.woojik.aircallai.settings.SettingsStore

interface ToolPermissionStore {
    suspend fun isAllowed(toolName: String, action: String, risk: ToolRisk): Boolean
    suspend fun setAllowed(toolName: String, action: String, allowed: Boolean)
}

class InMemoryToolPermissionStore : ToolPermissionStore {
    private val allowed = mutableSetOf<String>()
    override suspend fun isAllowed(toolName: String, action: String, risk: ToolRisk) =
        risk == ToolRisk.READ || "$toolName:$action" in allowed
    override suspend fun setAllowed(toolName: String, action: String, allowed: Boolean) {
        val key = "$toolName:$action"
        if (allowed) this.allowed.add(key) else this.allowed.remove(key)
    }
}

/**
 * PRD-06: 사용자 승인 여부를 일반 설정에 영구 저장한다(승인 플래그는 민감정보가 아니다).
 * READ는 항상 허용, WRITE/DESTRUCTIVE는 사용자가 설정에서 켠 작업만 허용한다.
 */
class SettingsToolPermissionStore(private val store: SettingsStore) : ToolPermissionStore {
    override suspend fun isAllowed(toolName: String, action: String, risk: ToolRisk) =
        risk == ToolRisk.READ || isApproved(toolName, action)

    override suspend fun setAllowed(toolName: String, action: String, allowed: Boolean) {
        store.putString(key(toolName, action), allowed.toString())
    }

    fun isApproved(toolName: String, action: String): Boolean =
        store.getString(key(toolName, action)) == "true"

    private fun key(toolName: String, action: String) = "tool_allow:$toolName:$action"
}
