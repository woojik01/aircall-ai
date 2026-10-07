package com.woojik.aircallai.diagnostics

import android.Manifest
import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.util.AtomicFile
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.woojik.aircallai.BuildConfig
import java.io.File
import java.util.concurrent.Executors

/** This entire implementation is compiled into debug APKs only. Nothing is transmitted. */
object CrashDiagnostics {
    internal const val CHANNEL = "aircall_debug_crashes"
    internal const val NOTIFICATION_ID = 9101
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AirCall-crash-diagnostics").apply { isDaemon = true }
    }
    @Volatile private var app: Application? = null
    @Volatile private var stage = "application-create"
    @Volatile private var cachedReport: String? = null
    @Volatile private var postedReport: String? = null
    @Volatile private var queryStatus = "종료 기록 조회 대기"
    @Volatile private var deliveryStatus = "알림 전송 대기"
    @Volatile private var storageStatus = "기록 파일 확인 대기"

    @Synchronized fun install(application: Application) {
        if (app != null) return
        app = application
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, failure ->
            try { recordException(failure, if (thread == application.mainLooper.thread) "main" else "worker") }
            catch (_: Throwable) { /* Diagnostics must never prevent the original crash handler. */ }
            finally {
                if (previous != null) previous.uncaughtException(thread, failure)
                else { Process.killProcess(Process.myPid()); kotlin.system.exitProcess(10) }
            }
        }
        worker.execute {
            cachedReport = readReport(application)
            safeCollect(application)
            safePublish(application)
        }
    }

    fun markStage(value: String) {
        // Callers supply constant stage labels, never user data.
        stage = value.take(64)
        val application = app ?: return
        val capturedStage = stage
        if (Build.VERSION.SDK_INT >= 30) worker.execute {
            try { application.getSystemService(ActivityManager::class.java)
                .setProcessStateSummary(capturedStage.toByteArray(Charsets.UTF_8)) }
            catch (_: Throwable) { }
        }
    }

    fun recordStartupFailure(failure: Throwable) {
        worker.execute { try { recordException(failure, "startup-recovery") } catch (_: Throwable) { } }
    }

    fun onNotificationsAvailable(context: Context) {
        worker.execute { safePublish(context.applicationContext) }
    }

    /** Accessible without notification permission and without initializing the main screen. */
    fun loadSnapshot(context: Context, callback: (String) -> Unit) = review(context, false, false, callback)
    fun retryNotification(context: Context, callback: (String) -> Unit) = review(context, true, false, callback)
    fun testNotification(context: Context, callback: (String) -> Unit) = review(context, false, true, callback)

    private fun review(context: Context, forceNotify: Boolean, test: Boolean, callback: (String) -> Unit) {
        worker.execute {
            val application = context.applicationContext as Application
            cachedReport = readReport(application)
            // Manual inspection may recover a crash that happened before an APK update.
            safeCollect(application, reviewHistory = true)
            if (test) {
                try {
                    if (notificationsAllowed(application)) {
                        postNotification(application, "종료 기록이 아닌 알림 전달 확인용 테스트입니다.",
                            "AirCall AI 알림 테스트", 9102)
                        deliveryStatus = "테스트 알림 전송 완료"
                    }
                } catch (failure: Throwable) { deliveryStatus = "알림 전송 실패: ${failure.javaClass.simpleName}" }
            } else safePublish(application, forceNotify)
            callback(snapshot(application))
        }
    }

    private fun safeCollect(application: Application, reviewHistory: Boolean = false) {
        try { collectPreviousExit(application, reviewHistory) }
        catch (failure: Throwable) { queryStatus = "종료 기록 조회 실패: ${failure.javaClass.simpleName}" }
    }

    private fun safePublish(context: Context, force: Boolean = false) {
        try { publish(context, force) }
        catch (failure: Throwable) { deliveryStatus = "알림 전송 실패: ${failure.javaClass.simpleName}" }
    }

    private fun snapshot(context: Context): String = buildString {
        append("AirCall AI 종료 진단 (개발용)\n")
        append("빌드: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
        append("Android: ${Build.VERSION.SDK_INT}\n")
        append("알림 상태: ").append(try { notificationStatus(context) }
            catch (failure: Exception) { "상태 조회 실패: ${failure.javaClass.simpleName}" }).append('\n')
        append("조회 상태: ").append(queryStatus).append('\n')
        append("저장 상태: ").append(storageStatus).append('\n')
        append("전송 상태: ").append(deliveryStatus).append("\n\n")
        append(cachedReport ?: "저장된 종료 기록이 없습니다. 알림 테스트는 앱을 종료시키거나 종료 로그를 만들지 않습니다.")
    }

    private fun header(time: Long) = "time=$time\nAirCall AI ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
        "Android ${Build.VERSION.SDK_INT}; ABI ${Build.SUPPORTED_ABIS.joinToString()}\n"

    private fun recordException(failure: Throwable, thread: String) {
        val application = app ?: return
        val report = header(System.currentTimeMillis()) + "stage=$stage; thread=$thread\n" + DebugCrashReport.exception(failure)
        saveReport(application, report)
        publish(application)
    }

    private fun collectPreviousExit(application: Application, reviewHistory: Boolean = false) {
        if (Build.VERSION.SDK_INT < 30) {
            queryStatus = "이 Android 버전은 시스템 종료 기록 조회를 지원하지 않습니다. Java 예외 기록만 지원합니다."
            return
        }
        val prefs = application.getSharedPreferences("debug_crash_diagnostics", Context.MODE_PRIVATE)
        val earliest = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        val seen = if (reviewHistory) earliest else prefs.getLong("last_exit", earliest)
        val history = application.getSystemService(ActivityManager::class.java)
            .getHistoricalProcessExitReasons(null, 0, 32)
            .filter { it.processName == application.packageName }
        queryStatus = "시스템 종료 기록 ${history.size}건" +
            (history.maxByOrNull { it.timestamp }?.let { "; 최근 사유=${it.reason}, 시각=${it.timestamp}" } ?: "")
        val records = history.filter { it.timestamp > seen }
        val exit = records.filter { it.reason in setOf(ApplicationExitInfo.REASON_CRASH,
            ApplicationExitInfo.REASON_CRASH_NATIVE, ApplicationExitInfo.REASON_ANR,
            ApplicationExitInfo.REASON_LOW_MEMORY, ApplicationExitInfo.REASON_SIGNALED,
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE) }.maxByOrNull { it.timestamp }
        if (exit != null) {
            val reason = when (exit.reason) {
                ApplicationExitInfo.REASON_CRASH -> "JAVA_CRASH"
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE_CRASH"
                ApplicationExitInfo.REASON_ANR -> "ANR"
                ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
                ApplicationExitInfo.REASON_SIGNALED -> "SIGNAL"
                else -> "INITIALIZATION_FAILURE"
            }
            // Preserve the Java stack saved just before this same process died.
            val previousTime = cachedReport?.lineSequence()?.firstOrNull()?.substringAfter("time=", "")?.toLongOrNull()
            if (cachedReport?.contains("\nsystem-time=${exit.timestamp}\n") == true ||
                (previousTime != null && previousTime > exit.timestamp + 10_000)) return
            val javaReport = cachedReport?.takeIf { previousTime != null && kotlin.math.abs(exit.timestamp - previousTime) < 10_000 }
            val summary = exit.processStateSummary?.toString(Charsets.UTF_8)
                ?.takeIf { it.matches(Regex("[a-z-]{1,64}")) } ?: "unavailable"
            val report = (javaReport ?: header(exit.timestamp)) +
                "\nsystem-time=${exit.timestamp}\n" +
                "\nAndroid exit=$reason; status=${exit.status}; stage=$summary\n" +
                "PSS=${exit.pss} KB; RSS=${exit.rss} KB (last sampled)\n" +
                if (exit.reason == ApplicationExitInfo.REASON_ANR) anrFrames(exit) else ""
            saveReport(application, report)
        }
        records.maxOfOrNull { it.timestamp }?.let { prefs.edit().putLong("last_exit", it).commit() }
    }

    @androidx.annotation.RequiresApi(30)
    private fun anrFrames(exit: ApplicationExitInfo): String = try {
        exit.traceInputStream?.use { input ->
            val buffer = ByteArray(64 * 1024)
            var size = 0
            while (size < buffer.size) {
                val read = input.read(buffer, size, buffer.size - size)
                if (read < 0) break
                size += read
            }
            // Trace messages and native tombstones can contain user data. Keep Java frames only.
            buffer.copyOf(size).toString(Charsets.UTF_8).lineSequence()
                .map { it.trim() }.filter { it.matches(Regex("at [A-Za-z0-9_.$]+\\([A-Za-z0-9_.$: -]+\\)")) }
                .take(32).joinToString("\n", prefix = "ANR stack frames:\n", postfix = "\n")
        } ?: ""
    } catch (_: Exception) { "" }

    @Synchronized internal fun saveReport(context: Context, report: String) {
        val bounded = report.take(DebugCrashReport.MAX_CHARS)
        val file = AtomicFile(File(context.noBackupFilesDir, "debug-last-crash.txt"))
        val stream = file.startWrite()
        try { stream.write(bounded.toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (failure: Throwable) { file.failWrite(stream); throw failure }
        cachedReport = bounded
        storageStatus = "종료 기록 저장됨"
    }

    @Synchronized internal fun readReport(context: Context): String? = try {
        AtomicFile(File(context.noBackupFilesDir, "debug-last-crash.txt")).openRead().use {
            it.readBytes().toString(Charsets.UTF_8).take(DebugCrashReport.MAX_CHARS).also {
                storageStatus = "저장된 종료 기록 있음"
            }
        }
    } catch (_: java.io.FileNotFoundException) { storageStatus = "저장된 종료 기록 없음"; null
    } catch (failure: Exception) { storageStatus = "파일 읽기 실패: ${failure.javaClass.simpleName}"; null }

    internal fun notificationStatus(context: Context): String {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return "알림 권한 꺼짐"
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return "앱 알림 꺼짐"
        if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return "종료 로그 알림 채널 꺼짐"
        return "알림 사용 가능"
    }

    private fun notificationsAllowed(context: Context): Boolean {
        val status = notificationStatus(context)
        if (status != "알림 사용 가능") { deliveryStatus = status; return false }
        return true
    }

    private fun publish(context: Context, force: Boolean = false) {
        val report = cachedReport ?: run { deliveryStatus = "종료 기록 없음: 전송할 로그가 없습니다"; return }
        if (!notificationsAllowed(context)) return
        if (!force && postedReport == report) { deliveryStatus = "이미 알린 기록 (다시 표시 버튼으로 재전송 가능)"; return }
        val prefs = context.getSharedPreferences("debug_crash_diagnostics", Context.MODE_PRIVATE)
        val fingerprint = java.security.MessageDigest.getInstance("SHA-256")
            .digest(report.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        if (!force && prefs.getString("notified_report", null) == fingerprint) {
            postedReport = report; deliveryStatus = "이미 알린 기록 (다시 표시 버튼으로 재전송 가능)"; return
        }
        postNotification(context, report, "AirCall AI 개발용 종료 로그", NOTIFICATION_ID)
        postedReport = report
        deliveryStatus = "종료 로그 알림 전송 완료"
        prefs.edit().putString("notified_report", fingerprint).commit()
    }

    private fun postNotification(context: Context, report: String, title: String, id: Int) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "개발용 앱 종료 로그", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(context, DebugCrashLogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val public = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("개발용 앱 종료 기록").build()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error).setContentTitle(title)
            .setContentText("눌러서 로그 확인·복사")
            .setStyle(NotificationCompat.BigTextStyle().bigText(report.take(3_000)))
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public).build()
        manager.notify(id, notification)
    }
}
