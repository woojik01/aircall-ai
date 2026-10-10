package com.woojik.aircallai.tools

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * PRD-06: WRITE Tool 작업의 사용자 승인 흐름을 조율한다.
 * submit()으로 대기 요청을 노출하면 UI가 다이얼로그를 띄우고,
 * approve()는 표시한 요청만 한 번 실행하고, deny()는 버린다.
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
    private val approvalLock = Mutex()
    private var waiting: CompletableDeferred<ToolResult>? = null

    /** Keep the AI turn suspended until the reviewed request actually completes. */
    suspend fun awaitResult(request: ToolRequest): ToolResult = approvalLock.withLock {
        val result = CompletableDeferred<ToolResult>(kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job])
        waiting = result
        _pending.value = request.copy(arguments = request.arguments.toMap())
        try { result.await() }
        finally {
            if (waiting === result) {
                waiting = null
                _pending.value = null
            }
        }
    }

    /** 승인이 필요한 요청을 UI에 올린다. 이미 대기 중인 요청을 덮어쓰지 않는다. */
    fun submit(request: ToolRequest) {
        if (_pending.value != null || waiting != null) return
        _pending.value = request.copy(arguments = request.arguments.toMap())
    }

    /** 사용자 승인: 표시된 요청만 실행하며 다음 작업에는 다시 승인이 필요하다. */
    fun approve(reviewed: ToolRequest? = null, expected: ToolRequest? = null) {
        val pending = _pending.value ?: return
        if (expected != null && expected != pending) return
        if (reviewed != null && (reviewed.toolName != pending.toolName || reviewed.action != pending.action ||
                reviewed.arguments.keys != pending.arguments.keys)) return
        val request = reviewed?.copy(arguments = reviewed.arguments.toMap()) ?: pending
        val completion = waiting
        // Consume synchronously: a second tap cannot send a second email or create a second issue.
        _pending.value = null
        scope.launch {
            try {
                if (completion != null && !completion.isActive) return@launch
                val result = executor.executeApproved(request)
                _lastResult.value = result
                completion?.complete(if (request != pending) result.copy(message = result.message +
                    "\n승인창에서 최종 확인한 내용:\n" + request.arguments.entries.joinToString("\n") { "${it.key}: ${it.value}" }) else result)
            } catch (e: CancellationException) {
                completion?.cancel(e)
                throw e
            } catch (_: Exception) {
                val result = ToolResult(false, "승인된 작업을 실행하지 못했습니다")
                _lastResult.value = result
                completion?.complete(result)
            }
        }
    }

    /** 사용자 거부: 승인 없이 요청을 버린다. */
    fun deny() {
        if (_pending.value == null) return
        waiting?.complete(ToolResult(false, "사용자가 작업 실행을 거부했습니다"))
        _pending.value = null
    }

    /** 테스트/초기화용: 마지막 실행 결과를 지운다. */
    fun clearLastResult() {
        _lastResult.value = null
    }
}
