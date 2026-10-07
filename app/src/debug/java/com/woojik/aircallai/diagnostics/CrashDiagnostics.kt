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
            try {
                cachedReport = readReport(application)
                collectPreviousExit(application)
                publish(application)
            } catch (_: Throwable) { }
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
        worker.execute { try { publish(context.applicationContext) } catch (_: Throwable) { } }
    }

    private fun header(time: Long) = "time=$time\nAirCall AI ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
        "Android ${Build.VERSION.SDK_INT}; ABI ${Build.SUPPORTED_ABIS.joinToString()}\n"

    private fun recordException(failure: Throwable, thread: String) {
        val application = app ?: return
        val report = header(System.currentTimeMillis()) + "stage=$stage; thread=$thread\n" + DebugCrashReport.exception(failure)
        saveReport(application, report)
        publish(application)
    }

    private fun collectPreviousExit(application: Application) {
        if (Build.VERSION.SDK_INT < 30) return
        val prefs = application.getSharedPreferences("debug_crash_diagnostics", Context.MODE_PRIVATE)
        @Suppress("DEPRECATION")
        val installedAt = application.packageManager.getPackageInfo(application.packageName, 0).lastUpdateTime
        val seen = prefs.getLong("last_exit", installedAt)
        val records = application.getSystemService(ActivityManager::class.java)
            .getHistoricalProcessExitReasons(null, 0, 8)
            .filter { it.processName == application.packageName && it.timestamp > seen }
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
            val javaReport = cachedReport?.takeIf { previousTime != null && kotlin.math.abs(exit.timestamp - previousTime) < 10_000 }
            val summary = exit.processStateSummary?.toString(Charsets.UTF_8)
                ?.takeIf { it.matches(Regex("[a-z-]{1,64}")) } ?: "unavailable"
            val report = (javaReport ?: header(exit.timestamp)) +
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
    }

    @Synchronized internal fun readReport(context: Context): String? = try {
        AtomicFile(File(context.noBackupFilesDir, "debug-last-crash.txt")).openRead().use {
            it.readBytes().toString(Charsets.UTF_8).take(DebugCrashReport.MAX_CHARS)
        }
    } catch (_: Exception) { null }

    private fun publish(context: Context) {
        val report = cachedReport ?: return
        if (postedReport == report) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        val prefs = context.getSharedPreferences("debug_crash_diagnostics", Context.MODE_PRIVATE)
        val fingerprint = java.security.MessageDigest.getInstance("SHA-256")
            .digest(report.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        if (prefs.getString("notified_report", null) == fingerprint) { postedReport = report; return }
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "개발용 앱 종료 로그", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(context, DebugCrashLogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(context, NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val public = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("개발용 앱 종료 기록").build()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error).setContentTitle("AirCall AI 개발용 종료 로그")
            .setContentText("눌러서 로그 확인·복사")
            .setStyle(NotificationCompat.BigTextStyle().bigText(report.take(3_000)))
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public).build()
        manager.notify(NOTIFICATION_ID, notification)
        postedReport = report
        prefs.edit().putString("notified_report", fingerprint).commit()
    }
}
