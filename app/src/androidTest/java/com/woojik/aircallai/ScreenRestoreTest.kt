package com.woojik.aircallai

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.woojik.aircallai.ui.MainActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Restore real Compose/NavController saved state after a non-chat screen was rendered. */
@RunWith(AndroidJUnit4::class)
class ScreenRestoreTest {
    @Test fun recreatedActivityRestoresModelsScreenAndKeepsSavedConversation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as AirCallApp
        val current = AtomicReference<MainActivity>()
        val resumed = AtomicReference(CountDownLatch(1))
        val restored = AtomicReference<Bundle>()
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) {
                if (activity is MainActivity && state != null) restored.set(state)
            }
            override fun onActivityResumed(activity: Activity) {
                if (activity is MainActivity) { current.set(activity); resumed.get().countDown() }
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivityDestroyed(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        try {
            context.startActivity(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
                .putExtra(MainActivity.EXTRA_SCREEN, "models").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            assertTrue(resumed.get().await(15, TimeUnit.SECONDS))
            assertModelsVisible()
            val expectedRoom = app.graph.chatRooms.rooms.value.first { it.id == "upgrade-room" }
            repeat(2) {
                val previous = current.get()
                resumed.set(CountDownLatch(1))
                restored.set(null)
                instrumentation.runOnMainSync { previous.recreate() }
                assertTrue("Recreated activity must resume", resumed.get().await(15, TimeUnit.SECONDS))
                assertNotSame(previous, current.get())
                assertNotNull("The system must supply the actual saved-state Bundle", restored.get())
                assertModelsVisible()
                assertFalse(current.get().isFinishing)
                assertFalse(current.get().isDestroyed)
                assertTrue("Stored conversation must remain available", app.graph.chatRooms.rooms.value.contains(expectedRoom))
            }
        } finally {
            app.unregisterActivityLifecycleCallbacks(callbacks)
            current.get()?.let { activity -> instrumentation.runOnMainSync { activity.finish() } }
        }
    }

    private fun assertModelsVisible() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = android.os.SystemClock.uptimeMillis() + 15_000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            val root = automation.rootInActiveWindow
            // Compose renders virtual accessibility descendants. Traverse the real tree;
            // findAccessibilityNodeInfosByText is not a reliable query for those providers.
            val nodes = java.util.ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
            root?.let { nodes.add(it) }
            val texts = mutableSetOf<String>()
            var checked = 0
            while (nodes.isNotEmpty() && checked++ < 500) {
                val node = nodes.removeFirst()
                if (node.packageName?.toString() == InstrumentationRegistry.getInstrumentation().targetContext.packageName)
                    node.text?.toString()?.let { texts.add(it) }
                repeat(node.childCount) { index -> node.getChild(index)?.let { nodes.add(it) } }
            }
            if ("GPU 가속" in texts && "다운로드 후 적용해 주세요." in texts) return
            Thread.sleep(100)
        }
        val diagnosticReady = CountDownLatch(1)
        val diagnostic = AtomicReference<String>()
        com.woojik.aircallai.diagnostics.CrashDiagnostics.loadSnapshot(
            InstrumentationRegistry.getInstrumentation().targetContext) {
            diagnostic.set(it); diagnosticReady.countDown()
        }
        if (diagnosticReady.await(10, TimeUnit.SECONDS)) println(diagnostic.get())
        fail("The restored models screen must render its actual controls")
    }
}
