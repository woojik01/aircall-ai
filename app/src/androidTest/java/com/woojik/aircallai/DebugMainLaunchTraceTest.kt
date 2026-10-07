package com.woojik.aircallai

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.woojik.aircallai.diagnostics.CrashDiagnostics
import com.woojik.aircallai.diagnostics.DebugCrashLogActivity
import com.woojik.aircallai.ui.MainActivity
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise the actual diagnostic launch button and retain a normal Activity finish separately from crashes. */
@RunWith(AndroidJUnit4::class)
class DebugMainLaunchTraceTest {
    @Test fun mainLifecycleSurvivesReturnToDiagnosticScreenWithoutReplacingCrash() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as Application
        val crash = File(context.noBackupFilesDir, "debug-last-crash.txt")
        val previous = crash.readText()
        val resumed = CountDownLatch(1)
        val destroyed = CountDownLatch(1)
        val mainActivity = AtomicReference<MainActivity>()
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity is MainActivity) { mainActivity.set(activity); resumed.countDown() }
            }
            override fun onActivityDestroyed(activity: Activity) {
                if (activity === mainActivity.get()) destroyed.countDown()
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        val diagnostic = instrumentation.startActivitySync(Intent(context, DebugCrashLogActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as DebugCrashLogActivity
        try {
            instrumentation.runOnMainSync {
                val views = java.util.ArrayDeque<View>()
                views.add(diagnostic.window.decorView)
                var clicked = false
                while (views.isNotEmpty()) {
                    val view = views.removeFirst()
                    if (view is Button && view.text.toString() == "메인 앱 열기 · 실행 기록 수집") {
                        assertTrue(view.performClick()); clicked = true; break
                    }
                    if (view is ViewGroup) repeat(view.childCount) { views.add(view.getChildAt(it)) }
                }
                assertTrue("The real diagnostic launch control must be present", clicked)
            }
            assertTrue("Main screen must resume", resumed.await(15, TimeUnit.SECONDS))
            instrumentation.runOnMainSync { mainActivity.get().finish() }
            assertTrue("Main screen must finish", destroyed.await(15, TimeUnit.SECONDS))
            val ready = CountDownLatch(1)
            val snapshot = AtomicReference<String>()
            CrashDiagnostics.loadSnapshot(context) { snapshot.set(it); ready.countDown() }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            val trace = File(context.noBackupFilesDir, "debug-main-launch.txt").readText()
            assertTrue(trace.contains("build=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"))
            assertTrue(trace.contains("main-launch"))
            assertTrue(trace.contains("stage=activity-create"))
            assertTrue(trace.contains("main-resumed"))
            assertTrue(trace.contains("main-destroyed; finishing=true; changing-config=false"))
            assertTrue(snapshot.get().contains(trace))
            assertEquals("A normal Activity finish must not replace an actual crash", previous, crash.readText())
        } finally {
            app.unregisterActivityLifecycleCallbacks(callbacks)
            instrumentation.runOnMainSync { diagnostic.finish() }
        }
    }
}
