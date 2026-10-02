package com.woojik.aircallai.call

import com.woojik.aircallai.conversation.ConversationState
import com.woojik.aircallai.session.SessionStatus

/**
 * PRD-07 통화 화면의 상태 표현.
 * 사용자가 현재 AI가 무엇을 하고 있는지 즉시 알 수 있어야 한다.
 *
 * 접근성: 상태는 색상만으로 구분하지 않고 글리프(glyph) + 텍스트(label)로 함께 구분한다.
 */
enum class CallStatus(val glyph: String, val label: String) {
    IDLE("●", "대기 중"),
    LISTENING("🎧", "듣고 있습니다"),
    THINKING("💭", "생각 중"),
    SPEAKING("💬", "말하는 중"),
    PAUSED("⏸", "일시정지됨"),
    MUTED("🔇", "음소거"),
    OFFLINE("📴", "AI 설정 필요"),
    ERROR("⚠", "오류"),
}

/** PRD-07 통화 화면 하단 컨트롤(음소거/일시정지/종료)의 활성 여부. */
data class CallControls(
    val canMute: Boolean,
    val canPause: Boolean,
    val canEnd: Boolean,
)

/**
 * 세션 상태 + 대화 상태 + 음소거 + Provider 준비 여부로부터 통화 화면 상태를 도출한다.
 * 순수 로직이므로 JVM 단위 테스트로 상태 전환의 정확성을 검증한다 (PRD-07 완료 조건).
 *
 * 우선순위: 오류 > 일시정지 > 음소거 > 말하는 중 > 생각 중 > 듣고 있습니다.
 * 세션이 없을 때 Provider가 준비되지 않았으면 설정 필요로 표시한다.
 */
fun callStatusOf(
    session: SessionStatus,
    conversation: ConversationState,
    muted: Boolean,
    providerReady: Boolean,
): CallStatus = when {
    conversation is ConversationState.Error -> CallStatus.ERROR
    session == SessionStatus.Paused -> CallStatus.PAUSED
    session == SessionStatus.Running -> when {
        muted -> CallStatus.MUTED
        conversation is ConversationState.Speaking -> CallStatus.SPEAKING
        conversation is ConversationState.Processing -> CallStatus.THINKING
        else -> CallStatus.LISTENING
    }
    else -> if (providerReady) CallStatus.IDLE else CallStatus.OFFLINE
}

/**
 * 통화 컨트롤은 세션 활성 여부로만 결정한다.
 * (오류 상태에서도 통화 종료로 벗어날 수 있어야 한다.)
 */
fun callControlsOf(status: CallStatus, sessionActive: Boolean): CallControls {
    val active = sessionActive && status != CallStatus.OFFLINE && status != CallStatus.IDLE
    return CallControls(canMute = active, canPause = active, canEnd = active)
}

/** 음소거 버튼 라벨 (알림/오버레이/통화 화면이 동일한 문구를 쓴다). */
fun muteLabel(muted: Boolean): String = if (muted) "음
소거 해제" else "음소거"

/** 일시정지/재개 버튼 라벨. */
fun pauseLabel(paused: Boolean): String = if (paused) "재개" else "일시정지"
