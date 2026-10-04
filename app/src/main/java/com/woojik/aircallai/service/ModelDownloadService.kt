package com.woojik.aircallai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.woojik.aircallai.AirCallApp
import com.woojik.aircallai.ai.local.LocalModelInfo
import com.woojik.aircallai.ai.local.LocalModelRegistry
import com.woojik.aircallai.ai.local.ModelDownloadState
import com.woojik.aircallai.ai.local.downloadPercent
import com.woojik.aircallai.ui.MainActivity
import kotlinx.coroutines.*

/** User-started downloads outlive the Activity and have explicit, model-specific cancellation. */
class ModelDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = mutableMapOf<String, Job>()
    private val manager get() = (application as AirCallApp).graph.modelDownloadManager
    private val notifications get() = getSystemService(NotificationManager::class.java)
    private var foreground = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "모델 다운로드", NotificationManager.IMPORTANCE_LOW))
        scope.launch {
            manager.states.collect { states ->
                if (foreground) jobs.keys.forEach { id ->
                    LocalModelRegistry.byId(id)?.let { model ->
                        notifications.notify(notificationId(id), notification(model, states[id]))
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val model = LocalModelRegistry.byId(intent?.getStringExtra(EXTRA_MODEL))
        when {
            intent?.action == ACTION_CANCEL && model != null -> {
                jobs[model.id]?.cancel()
                scope.launch(Dispatchers.IO) { manager.interrupt(model.id) }
                if (jobs.isEmpty()) stopSelf()
            }
            intent?.action == ACTION_START && model != null -> start(model)
            jobs.isEmpty() -> stopSelf()
        }
        // No blind redelivery after process death; a new user request verifies/restarts the file.
        return START_NOT_STICKY
    }

    private fun start(model: LocalModelInfo) {
        if (jobs.containsKey(model.id)) return
        ServiceCompat.startForeground(this, notificationId(model.id), notification(model, ModelDownloadState.Downloading(0, model.sizeBytes)),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
        foreground = true
        if (wakeLock?.isHeld != true) {
            wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AirCall:ModelDownload").apply {
                acquire(6 * 60 * 60 * 1000L)
            }
        }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var cancelled = false
            try { manager.download(model) }
            catch (e: CancellationException) { cancelled = true; throw e }
            finally {
                jobs.remove(model.id)
                if (jobs.isEmpty()) {
                    foreground = false
                    ServiceCompat.stopForeground(this@ModelDownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    releaseWakeLock()
                    stopSelf()
                } else {
                    val next = LocalModelRegistry.byId(jobs.keys.first())!!
                    ServiceCompat.startForeground(this@ModelDownloadService, notificationId(next.id), notification(next, manager.states.value[next.id]),
                        if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
                }
                if (cancelled) notifications.cancel(notificationId(model.id))
                else notifications.notify(notificationId(model.id), notification(model, manager.states.value[model.id]))
            }
        }
        jobs[model.id] = job
        job.start()
    }

    private fun notification(model: LocalModelInfo, state: ModelDownloadState?): Notification {
        val active = state is ModelDownloadState.Downloading || state is ModelDownloadState.Verifying || state == null
        val percent = (state as? ModelDownloadState.Downloading)?.let { downloadPercent(it.downloadedBytes, it.totalBytes) } ?: 0
        val label = when (state) {
            is ModelDownloadState.Completed -> "다운로드 완료 · 눌러서 모델 적용"
            is ModelDownloadState.Failed -> state.message
            ModelDownloadState.Verifying -> "파일 무결성 확인 중"
            ModelDownloadState.Idle -> "다운로드 취소됨"
            else -> "다운로드 중 $percent%"
        }
        val open = PendingIntent.getActivity(this, 200,
            Intent(this, MainActivity::class.java).setAction("aircall.OPEN_MODELS")
                .putExtra(MainActivity.EXTRA_SCREEN, "models")
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(model.displayName)
            .setContentText(label).setStyle(NotificationCompat.BigTextStyle().bigText(label))
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(active).setAutoCancel(!active)
        if (active) {
            builder.setProgress(100, percent, state is ModelDownloadState.Verifying)
            val cancel = PendingIntent.getService(this, notificationId(model.id),
                Intent(this, ModelDownloadService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_MODEL, model.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(0, "취소", cancel)
        }
        return builder.build()
    }

    private fun releaseWakeLock() { wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null }
    override fun onDestroy() {
        foreground = false
        jobs.keys.toList().forEach { id -> notifications.cancel(notificationId(id)) }
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        const val ACTION_START = "aircall.model.START"
        const val ACTION_CANCEL = "aircall.model.CANCEL"
        const val EXTRA_MODEL = "model_id"
        private const val CHANNEL = "aircall_downloads"
        private fun notificationId(id: String) = 100 + LocalModelRegistry.models.indexOfFirst { it.id == id }
    }
}
