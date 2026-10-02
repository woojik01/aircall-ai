package com.woojik.aircallai.tools

import com.woojik.aircallai.core.logging.SecureLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PRD-06 실행 메타데이터: Tool 실행 이력을 기록한다.
 * - 인자 값은 기록하지 않는다(민감 정보 포함 가능).
 * - 기록 항목: 도구/액션/위험도, 차단 여부(승인 전), 성공 여부, 지연(ms), 시각.
 * - 최근 MAX_ENTRIES건만 메모리에 유지하고 StateFlow로 UI가 관찰할 수 있다.
 * - 디버그 빌드에서만 요약 로그를 남긴다(SecureLog).
 */
class ToolExecutionLogger {

    data class Entry(
        val toolName: String,
        val action: String,
        val risk: ToolRisk,
        val blocked: Boolean,
        val success: Boolean,
        val latencyMs: Long,
        val timestampMs: Long,
    )

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun record(entry: Entry) {
        _entries.value = (_entries.value + entry).takeLast(MAX_ENTRIES)
        SecureLog.d(
            TAG,
            "tool=" + entry.toolName + " action=" + entry.action +
                " risk=" + entry.risk + " blocked=" + entry.blocked +
                " success=" + entry.success + " latencyMs=" + entry.latencyMs,
        )
    }

    companion object {
        private const val TAG = "ToolExecution"
        private const val MAX_ENTRIES = 100
    }
}
