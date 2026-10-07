package com.woojik.aircallai.tools

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.woojik.aircallai.ui.MainActivity

class ToolResultNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "AI 작업 결과", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun notify(event: ToolExecutionEvent) {
        if (event.status == ToolExecutionStatus.RUNNING) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (!manager.areNotificationsEnabled()) return
        val intent = Intent(context, MainActivity::class.java)
            .setAction("aircall.task.${event.id}")
            .putExtra(MainActivity.EXTRA_SCREEN, "chat")
            .putExtra(MainActivity.EXTRA_ROOM_ID, event.roomId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val public = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("AirCall AI 작업 결과").build()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if (event.status == ToolExecutionStatus.SUCCEEDED) "AI 작업 실행 완료" else "AI 작업 결과 확인")
            .setContentText(event.summary).setStyle(NotificationCompat.BigTextStyle().bigText(event.summary))
            .setContentIntent(pending).setAutoCancel(true)
            .setTimeoutAfter(if (event.status == ToolExecutionStatus.SUCCEEDED) 60_000L else 10_000L)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public).build()
        // Permission may be revoked after the check. This must not change the tool's result.
        try { manager.notify(event.id, 3000, notification) } catch (_: SecurityException) { }
    }

    companion object { const val CHANNEL = "aircall_tool_results" }
}
