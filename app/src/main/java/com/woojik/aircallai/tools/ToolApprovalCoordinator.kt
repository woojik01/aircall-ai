package com.woojik.aircallai.tools

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * PRD-06: WRITE Tool 작업의 사용자 승인 흐름을 조율한다.
 * submit()으로 대기 요청을 노출하면 UI가 다이얼로그를 띄우고,
 * approve()는 승인을 영속 저장한 뒤 실행, deny()는 버린다.
 * 승인/거부 결과와 마지막 실행 결과는 StateFlow로 노출해 UI가 관찰한다.
 */
class ToolApprovalCoordinator(
    private val permissions: ToolPermissionStore,
    private val executor: ToolExecutor,
    private val scope: CoroutineScope,
) {
    private val _pending = MutableStateFlow<ToolRequest?>(null)
    val pending: StateFlow<ToolRequest?> = _pending

    private val _lastResult = MutableStateFlow<ToolResult?>(null)
    val lastResult: StateFlow<ToolResult?> = _lastResult

    /** 승인이 필요한 요청을 UI에 올린다. 이미 대기 중이면 덮어쓴다. */
    fun submit(request: ToolRequest) {
        _pending.value = request
    }

    /** 사용자 승인: 권한을 영속 저장하고 작업을 실행한다. */
    fun approve() {
        val request = _pending.value ?: return
        scope.launch {
            permissions.setAllowed(request.toolName, request.action, true)
            _lastResult.value = executor.execute(request)
            _pending.value = null
        }
    }

    /** 사용자 거부: 승인 없이 요청을 버린다. */
    fun deny() {
        _pending.value = null
    }

    /** 테스트/초기화용: 마지막 실행 결과를 지운다. */
    fun clearLastResult() {
        _lastResult.value = null
    }
}
