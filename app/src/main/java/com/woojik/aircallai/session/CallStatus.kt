package com.woojik.aircallai.session

import com.woojik.aircallai.conversation.ConversationState

/**
 * PRD-07 통화 화면/알림이 공통으로 쓰는 사용자 관점 상태.
 * 색상만으로 구분하지 않도록 항상 텍스트 라벨과 함께 표시한다.
 */
enum class CallStatus(val label: String) {
    READY("통화 대기"),
    LISTENING("듣는 중"),
    THINKING("생각 중"),
    SPEAKING("말하는 중"),
    PAUSED("일시정지됨"),
    OFFLINE("오프라인"),
    ERROR("오류"),
    ENDED("통화 종료");

    companion object {
        fun from(conversation: ConversationState, session: SessionStatus): CallStatus {
            if (conversation is ConversationState.Error) {
                return if (conversation.kind == ConversationState.ErrorKind.NETWORK) OFFLINE else ERROR
            }
            if (session == SessionStatus.Paused) return PAUSED
            val active = session == SessionStatus.Running
            return when (conversation) {
                is ConversationState.Processing -> THINKING
                is ConversationState.Speaking -> SPEAKING
                ConversationState.Listening -> LISTENING
                ConversationState.Idle -> when {
                    active -> LISTENING
                    session == SessionStatus.Ended -> ENDED
                    else -> READY
                }
                is ConversationState.Error -> ERROR
            }
        }
    }
}

/** 통화 시간 표시 (mm:ss, 1시간 이상은 h:mm:ss). */
fun formatCallDuration(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
