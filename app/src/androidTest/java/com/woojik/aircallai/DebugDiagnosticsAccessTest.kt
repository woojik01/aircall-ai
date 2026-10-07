package com.woojik.aircallai

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.woojik.aircallai.diagnostics.CrashDiagnostics
import com.woojik.aircallai.diagnostics.DebugCrashLogActivity
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Verify the user's missing-notification path, then explicitly retry after permission is granted. */
@RunWith(AndroidJUnit4::class)
class DebugDiagnosticsAccessTest {
    @Test fun diagnosticScreenWorksWithoutNotificationsAndSavedLogCanBeReposted() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.noBackupFilesDir, "debug-last-crash.txt")
        val previous = file.readText()
        assertTrue(previous.contains("java.lang.IllegalStateException"))
        val blocked = InstrumentationRegistry.getArguments().getString("phase") == "blocked"
        assertEquals(if (blocked) PackageManager.PERMISSION_DENIED else PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        fun snapshot(action: (android.content.Context, (String) -> Unit) -> Unit): String {
            val ready = CountDownLatch(1)
            val result = AtomicReference<String>()
            action(context) { result.set(it); ready.countDown() }
            assertTrue("Diagnostic callback must finish", ready.await(10, TimeUnit.SECONDS))
            return result.get()
        }
        val status = snapshot { target, callback -> CrashDiagnostics.retryNotification(target, callback) }
        val manager = context.getSystemService(NotificationManager::class.java)
        if (blocked) {
            assertTrue(status.contains("알림 권한 꺼짐"))
            assertTrue(status.contains("java.lang.IllegalStateException"))
            assertTrue(manager.activeNotifications.none { it.id == 9101 })
        } else {
            assertTrue(status.contains("종료 로그 알림 전송 완료"))
            assertTrue(manager.activeNotifications.any { it.id == 9101 })
        }
        val probe = snapshot { target, callback -> CrashDiagnostics.testNotification(target, callback) }
        assertTrue(probe.contains(if (blocked) "알림 권한 꺼짐" else "테스트 알림 전송 완료"))
        if (!blocked) assertTrue(manager.activeNotifications.any { it.id == 9102 })
        assertEquals("Notification testing must preserve the actual crash log", previous, file.readText())
        val launchers = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0)
        assertTrue("A direct diagnostic launcher must be available", launchers.any {
            it.activityInfo.name == DebugCrashLogActivity::class.java.name && it.activityInfo.exported
        })
        val app = context.applicationContext as Application
        val resumed = CountDownLatch(1)
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: android.app.Activity) { if (activity is DebugCrashLogActivity) resumed.countDown() }
            override fun onActivityCreated(activity: android.app.Activity, state: android.os.Bundle?) {}
            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, state: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        try {
            context.startActivity(Intent(context, DebugCrashLogActivity::class.java)
                .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            assertTrue("Direct diagnostic screen must open", resumed.await(10, TimeUnit.SECONDS))
            val deadline = android.os.SystemClock.uptimeMillis() + 10_000
            var displayed = false
            while (android.os.SystemClock.uptimeMillis() < deadline) {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                if (root?.findAccessibilityNodeInfosByText("java.lang.IllegalStateException")?.isNotEmpty() == true &&
                    (!blocked || root.findAccessibilityNodeInfosByText("알림 권한 꺼짐").isNotEmpty())) {
                    displayed = true; break
                }
                Thread.sleep(100)
            }
            assertTrue("The saved log must be visible even when notifications are denied", displayed)
        } finally { app.unregisterActivityLifecycleCallbacks(callbacks) }
    }
}
