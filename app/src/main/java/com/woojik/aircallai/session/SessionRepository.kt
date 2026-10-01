package com.woojik.aircallai.session

import com.woojik.aircallai.conversation.ConversationEngine
import kotlinx.coroutines.flow.StateFlow

/**
 * PRD-05: Foreground Service와 UI 사이의 명확한 상태 통로 (Binder 대신 Repository).
 * Service와 Activity가 서로의 내부 로직에 직접 의존하지 않는다.
 *
 * SessionStatus, SessionController, SessionAudioHooks는 SessionCtl.kt에서
 * 단일 정의를 제공한다. 이 파일에서는 Repository만 정의한다.
 */
class SessionRepository(
    val engine: ConversationEngine,
    val controller: SessionController,
) {
    val status: StateFlow<SessionStatus> = controller.status
    val muted: StateFlow<Boolean> = controller.muted

    /** Service가 설치하는 오디오 제어 통로. Service 종료 시 해제된다. */
    var audioHooks: SessionAudioHooks? = null

    /** PRD-03 스펙 유지: TTS 재생 즉시 중지(barge-in). */
    fun stopSpeaking() {
        audioHooks?.stopSpeaking()
    }
}
