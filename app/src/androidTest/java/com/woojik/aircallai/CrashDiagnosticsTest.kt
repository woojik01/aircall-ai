package com.woojik.aircallai

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.woojik.aircallai.diagnostics.CrashDiagnostics
import com.woojik.aircallai.diagnostics.DebugCrashLogActivity
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Invoked separately: the crash phase must really terminate the app, then verify on restart. */
@RunWith(AndroidJUnit4::class)
class CrashDiagnosticsTest {
    @Test fun crashReportSurvivesRestartAndNotificationOpensIndependentLogScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.noBackupFilesDir, "debug-last-crash.txt")
        if (InstrumentationRegistry.getArguments().getString("phase") == "crash") {
            file.delete()
            context.getSystemService(NotificationManager::class.java).cancel(9101)
            CrashDiagnostics.markStage("diagnostic-test")
            // No crash trigger is included in either production or the installable debug app.
            Thread { throw IllegalStateException("secret-test-token-must-not-appear") }.start()
            CountDownLatch(1).await(15, TimeUnit.SECONDS)
            fail("The default Android crash handler must terminate the process")
        }
        val deadline = android.os.SystemClock.uptimeMillis() + 10_000
        val manager = context.getSystemService(NotificationManager::class.java)
        CrashDiagnostics.onNotificationsAvailable(context)
        while (android.os.SystemClock.uptimeMillis() < deadline &&
            (!file.isFile || manager.activeNotifications.none { it.id == 9101 })) Thread.sleep(100)
        assertTrue(file.isFile)
        val report = file.readText()
        assertTrue(report.contains("java.lang.IllegalStateException"))
        assertTrue(report.contains("CrashDiagnosticsTest"))
        assertTrue(report.contains("diagnostic-test"))
        assertFalse(report.contains("secret-test-token"))
        val notification = manager.activeNotifications.single { it.id == 9101 }
        assertEquals("aircall_debug_crashes", notification.notification.channelId)
        assertEquals(android.app.Notification.VISIBILITY_PRIVATE, notification.notification.visibility)
        val opened = CountDownLatch(1)
        val app = context.applicationContext as Application
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: android.app.Activity) {
                if (activity is DebugCrashLogActivity) opened.countDown()
            }
            override fun onActivityCreated(activity: android.app.Activity, state: android.os.Bundle?) {}
            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, state: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        try {
            notification.notification.contentIntent.send()
            assertTrue("Notification must open the independent log screen", opened.await(10, TimeUnit.SECONDS))
        } finally { app.unregisterActivityLifecycleCallbacks(callbacks) }
    }
}
