package com.woojik.aircallai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.woojik.aircallai.AirCallApp
import com.woojik.aircallai.audio.AndroidSpeechRecognizerEngine
import com.woojik.aircallai.audio.AndroidSpeechSynthesizerEngine
import com.woojik.aircallai.conversation.ConversationState
import com.woojik.aircallai.conversation.VoiceSession
import com.woojik.aircallai.core.logging.SecureLog
import com.woojik.aircallai.session.MutedSynthesizer
import com.woojik.aircallai.session.SessionAudioHooks
import com.woojik.aircallai.session.SessionRepository
import com.woojik.aircallai.session.SessionStatus
import com.woojik.aircallai.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * PRD-05 Foreground Service:
 * - 음성 캡처, 대화 세션 상태 유지, TTS 상태 관리, 세션 종료
 * - 지속 알림: 대화 상태 / 음소거 / 일시정지-재개 / 종료
 * - 화면이 꺼지거나 다른 앱을 사용해도 세션이 유지된다.
 * - UI는 Service 내부 로직에 직접 의존하지 않고 SessionRepository를 통해 통신한다.
 */
class ConversationService : Service() {

    private val repository: SessionRepository
        get() = (application as AirCallApp).graph.sessionRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var ttsEngine: AndroidSpeechSynthesizerEngine
    private lateinit var session: VoiceSession

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ttsEngine = AndroidSpeechSynthesizerEngine(application)
        val mutedSynthesizer = MutedSynthesizer(ttsEngine) { repository.muted.value }
        session = VoiceSession(
            recognizer = AndroidSpeechRecognizerEngine(application),
            synthesizer = mutedSynthesizer,
            engine = repository.engine,
        )
        repository.audioHooks = object : SessionAudioHooks {
            override fun stopSpeaking() = session.stopSpeaking()
        }
        // 상태 변화를 지속 알림에 반영한다.
        serviceScope.launch { repository.status.collect { updateNotification() } }
        serviceScope.launch { repository.engine.state.collect { updateNotification() } }
        serviceScope.launch { repository.muted.collect { updateNotification() } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!hasMicPermission()) {
                    // 마이크 FGS는 권한이 없으면 시작할 수 없다 (호출부에서 이미 확인하지만 방어).
                    SecureLog.w(TAG, "mic permission missing; not starting session")
                    stopSelf()
                    return START_STICKY
                }
                startInForeground()
                if (!repository.controller.isRunning) {
                    repository.controller.start { turn() }
                }
            }
            ACTION_PAUSE -> repository.controller.pause()
            ACTION_RESUME -> repository.controller.resume()
            ACTION_MUTE -> repository.controller.setMuted(true)
            ACTION_UNMUTE -> repository.controller.setMuted(false)
            ACTION_END -> endSession()
            else -> {
                // 시스템이 재시작한 경우(null intent): 죽은 세션을 되살리지 않고 정리한다.
                SecureLog.w(TAG, "service restarted by system; ending stale session")
                repository.controller.end()
                stopSelf()
            }
        }
        return START_STICKY
    }

    /** 한 턴 = STT -> AI -> TTS. 재생 완료 후 잠깐 쉬어 불필요한 CPU 사용을 줄인다 (PRD-05). */
    private suspend fun turn() {
        session.runOneTurn()
        delay(TURN_COOLDOWN_MS)
    }

    private fun endSession() {
        session.stopSpeaking()
        repository.controller.end()
        stopSelf()
    }

    private fun startInForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    // ----- 알림 (PRD-05: 상태/음소거/일시정지-재개/종료) -----

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "음성 대화 세션", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val status = repository.status.value
        val muted = repository.muted.value
        val text = conversationLabel(status) + (if (muted) " · 음소거" else "")
        val pauseResume = if (status == SessionStatus.Paused) {
            action("재개", ACTION_RESUME)
        } else {
            action("일시정지", ACTION_PAUSE)
        }
        val muteToggle = if (muted) action("음소거 해제", ACTION_UNMUTE) else action("음소거", ACTION_MUTE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AirCall AI 대화")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openAppIntent())
            .addAction(pauseResume)
            .addAction(muteToggle)
            .addAction(action("종료", ACTION_END))
            .build()
    }

    /** 마이크 사용 중임을 알림 텍스트로 표시한다 (PRD-05: 명확한 마이크 사용 표시). */
    private fun conversationLabel(status: SessionStatus): String {
        if (status == SessionStatus.Paused) return "일시정지됨"
        return when (repository.engine.state.value) {
            ConversationState.Idle -> "듣고 있습니다"
            ConversationState.Listening -> "듣고 있습니다"
            is ConversationState.Processing -> "생각 중..."
            is ConversationState.Speaking -> "말하는 중..."
            is ConversationState.Error -> "오류"
        }
    }

    private fun action(label: String, action: String): NotificationCompat.Action =
        NotificationCompat.Action.Builder(0, label, servicePendingIntent(action)).build()

    private fun servicePendingIntent(action: String): PendingIntent =
        PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, ConversationService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    override fun onDestroy() {
        super.onDestroy()
        repository.audioHooks = null
        repository.controller.end()
        ttsEngine.shutdown()
        serviceScope.cancel()
        SecureLog.d(TAG, "service destroyed, session ended")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.woojik.aircallai.session.START"
        const val ACTION_PAUSE = "com.woojik.aircallai.session.PAUSE"
        const val ACTION_RESUME = "com.woojik.aircallai.session.RESUME"
        const val ACTION_MUTE = "com.woojik.aircallai.session.MUTE"
        const val ACTION_UNMUTE = "com.woojik.aircallai.session.UNMUTE"
        const val ACTION_END = "com.woojik.aircallai.session.END"
        private const val CHANNEL_ID = "aircall_session"
        private const val NOTIFICATION_ID = 1
        private const val TURN_COOLDOWN_MS = 500L
        private const val TAG = "ConversationService"
    }
}
