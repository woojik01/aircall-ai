package com.woojik.aircallai.tools

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ToolExecutionStatus { RUNNING, SUCCEEDED, FAILED, CANCELLED }

/** Metadata only: arguments, tokens and returned private data never enter system notifications. */
data class ToolExecutionEvent(
    val id: String = UUID.randomUUID().toString(),
    val toolName: String,
    val action: String,
    val roomId: String? = null,
    val status: ToolExecutionStatus = ToolExecutionStatus.RUNNING,
    val resultUrl: String? = null,
) {
    val label: String get() = ToolLabels.action(toolName, action)
    val summary: String get() = when (status) {
        ToolExecutionStatus.RUNNING -> "$label 실행 중"
        ToolExecutionStatus.SUCCEEDED -> "$label 실행 완료"
        ToolExecutionStatus.FAILED -> "$label 실행 실패 · 대화에서 결과를 확인해 주세요."
        ToolExecutionStatus.CANCELLED -> "$label 실행 중단 · 변경 작업은 서비스에서 결과를 확인해 주세요."
    }
}

class ToolExecutionTracker {
    private val _events = MutableStateFlow<List<ToolExecutionEvent>>(emptyList())
    val events = _events.asStateFlow()
    fun record(event: ToolExecutionEvent) {
        _events.update { previous -> (previous.filterNot { it.id == event.id } + event).takeLast(50) }
    }
}
