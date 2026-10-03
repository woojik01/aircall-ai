package com.woojik.aircallai.tools

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 도구 실행 진행 이벤트. UI와 음성(TTS)이 실시간으로 진행 상황을 안내할 수 있게 한다.
 *
 * 설계 원칙:
 * - 이벤트는 도구 이름/액션 라벨만 담고 도구 구현과 완전히 분리된다.
 * - 새 도구가 추가돼도 이벤트 버스에 연결만 하면 진행 안내가 자동으로 동작한다.
 * - 인자 값(수신자 주소, 파일 내용 등)은 이벤트에 담지 않는다(개인정보 보호, PRD-08).
 */
sealed interface ToolActivityEvent {
    /** 사용자 친화적 진행 라벨(예: "GitHub 저장소 조회", "Gmail 메일 전송"). */
    val label: String

    /** AI가 도구 호출 직전에 스스로 만든 진행 안내 문장. 가장 자연스러운 내레이션이다. */
    data class Narration(override val label: String) : ToolActivityEvent

    /** 도구 실행 시작. */
    data class Started(val request: ToolRequest, override val label: String) : ToolActivityEvent

    /** 도구 실행 성공. */
    data class Succeeded(val request: ToolRequest, override val label: String) : ToolActivityEvent

    /** 일시적 실패로 자동 재시도 중. */
    data class Retrying(val request: ToolRequest, override val label: String, val attempt: Int) : ToolActivityEvent

    /** 도구 실행 실패(재시도 포함 후). */
    data class Failed(val request: ToolRequest, override val label: String) : ToolActivityEvent

    /** 사용자 승인이 필요한 WRITE 작업이 차단됨. */
    data class ApprovalRequired(val request: ToolRequest, override val label: String) : ToolActivityEvent
}

/**
 * 진행 이벤트 버스. 도구 개수와 조합(github만, gmail만, github+gmail, 향후 도구)과
 * 무관하게 모든 실행 흐름을 하나의 스트림으로 방송한다.
 * replay=0: 구독자는 앞으로 발생할 이벤트만 받는다(과거 재생 없음).
 */
class ToolActivityBus {
    private val _events = MutableSharedFlow<ToolActivityEvent>(replay = 0, extraBufferCapacity = 32)
    val events: SharedFlow<ToolActivityEvent> = _events.asSharedFlow()

    fun emit(event: ToolActivityEvent) {
        _events.tryEmit(event)
    }
}

/** 이벤트를 사용자에게 표시/날독할 문장으로 변환한다. */
object ToolActivityPhraser {
    fun text(event: ToolActivityEvent): String = when (event) {
        is ToolActivityEvent.Narration -> event.label
        is ToolActivityEvent.Started -> event.label + " 실행 중..."
        is ToolActivityEvent.Succeeded -> event.label + " 완료했습니다."
        is ToolActivityEvent.Retrying -> event.label + " 다시 시도합니다."
        is ToolActivityEvent.Failed -> event.label + " 실패했습니다."
        is ToolActivityEvent.ApprovalRequired -> event.label + " 승인이 필요합니다."
    }
}
