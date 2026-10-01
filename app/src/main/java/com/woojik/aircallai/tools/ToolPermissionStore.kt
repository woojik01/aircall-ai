package com.woojik.aircallai.tools

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
