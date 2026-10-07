package com.woojik.aircallai

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.auth.OAuthCredentialStore
import com.woojik.aircallai.auth.OAuthTokens
import com.woojik.aircallai.chat.ChatRoom
import com.woojik.aircallai.chat.ChatRoomStore
import com.woojik.aircallai.core.security.AndroidKeystoreCrypto
import com.woojik.aircallai.core.storage.AppStorage
import com.woojik.aircallai.core.storage.FileCredentialManager
import com.woojik.aircallai.settings.SettingsRepository
import com.woojik.aircallai.settings.SharedPrefsStore
import com.woojik.aircallai.ui.MainActivity
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** One test APK seeds the OLD installed app, then verifies after adb install -r. */
@RunWith(AndroidJUnit4::class)
class UpdatePersistenceTest {
    @Test fun dataSurvivesApkReplacement() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val settings = SettingsRepository(SharedPrefsStore(context))
        val crypto = AndroidKeystoreCrypto()
        val credentials = FileCredentialManager(AppStorage.credentialsDir(context), crypto)
        val chats = ChatRoomStore(File(context.filesDir, "chats/rooms.enc"), crypto)
        val model = File(AppStorage.modelsDir(context), "upgrade-fixture.litertlm")
        val note = File(context.filesDir, "tools/notes.txt")
        val expected = ChatRoom(id = "upgrade-room", title = "업데이트 보존", renamed = true,
            messages = listOf(ChatMessage(ChatMessage.Role.USER, "저장된 대화")))
        if (InstrumentationRegistry.getArguments().getString("phase") == "seed") {
            settings.acceptMinimumAgeAcknowledgement()
            settings.setThemeMode(SettingsRepository.THEME_DARK)
            settings.setAiProviderMode(SettingsRepository.MODE_CLOUD)
            settings.setCloudModel("upgrade-fixture-model")
            settings.setLocalModelId("upgrade-fixture")
            credentials.save("cloud_ai", "test-only-api-key".toByteArray())
            OAuthCredentialStore(credentials).save("github", OAuthTokens("test-only-token", null, null), "fixture-account")
            chats.write(listOf(expected))
            model.writeText("test-only-model-bytes")
            note.parentFile!!.mkdirs()
            note.writeText("test-only-note")
            assertTrue(context.getSharedPreferences("upgrade_test", 0).edit()
                .putInt("oldVersion", context.packageManager.getPackageInfo(context.packageName, 0).versionCode).commit())
            return@runBlocking
        }
        val oldVersion = context.getSharedPreferences("upgrade_test", 0).getInt("oldVersion", -1)
        assertTrue("Must be a higher-version in-place install", oldVersion > 0 &&
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode > oldVersion)
        // Exercise the real application startup as well as reading files directly.
        val activity = launchMainActivity()
        val graph = (context.applicationContext as AirCallApp).graph
        repeat(100) {
            if (!graph.chatRooms.ready.value) Thread.sleep(50)
        }
        assertTrue(graph.chatRooms.ready.value)
        assertChatVisible()
        assertTrue(graph.chatRooms.rooms.value.contains(expected))
        assertEquals(SettingsRepository.THEME_DARK, settings.themeMode())
        assertEquals(SettingsRepository.MODE_CLOUD, settings.aiProviderMode())
        assertEquals("upgrade-fixture-model", settings.cloudModel())
        assertEquals("upgrade-fixture", settings.localModelId())
        assertArrayEquals("test-only-api-key".toByteArray(), credentials.load("cloud_ai"))
        assertEquals("test-only-token", OAuthCredentialStore(credentials).load("github")?.accessToken)
        assertEquals("fixture-account", OAuthCredentialStore(credentials).loadDisplayName("github"))
        assertEquals("test-only-model-bytes", model.readText())
        assertEquals("test-only-note", note.readText())
        instrumentation.runOnMainSync { activity.finish() }
    }
    @Test fun legacySettingsAndUnreadableAccountDoNotCrashRepeatedLaunch() {
        if (InstrumentationRegistry.getArguments().getString("phase") == "seed") return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("aircall_settings", 0)
        val previousTheme = prefs.all["theme_mode"]
        val previousGpu = prefs.all["local_use_gpu"]
        val unreadable = File(AppStorage.credentialsDir(context), "oauth.gmail.account.bin")
        assertFalse("Fixture must not replace account data", unreadable.exists())
        assertTrue(unreadable.mkdir())
        assertTrue(prefs.edit().putInt("theme_mode", 17).putBoolean("local_use_gpu", false).commit())
        try {
            val settings = SettingsRepository(SharedPrefsStore(context))
            assertEquals(SettingsRepository.THEME_SYSTEM, settings.themeMode())
            assertFalse(settings.localUseGpu())
            repeat(2) {
                val activity = launchMainActivity()
                val graph = (context.applicationContext as AirCallApp).graph
                repeat(100) {
                    if (!graph.chatRooms.ready.value ||
                        graph.accountRepository.connections.value["gmail"]?.status != com.woojik.aircallai.auth.ConnectionStatus.ERROR)
                        Thread.sleep(50)
                }
                assertChatVisible()
                assertTrue("Real chat navigation must start", graph.chatRooms.ready.value)
                assertEquals(com.woojik.aircallai.auth.ConnectionStatus.ERROR,
                    graph.accountRepository.connections.value["gmail"]?.status)
                assertEquals(com.woojik.aircallai.auth.ConnectionStatus.CONNECTED,
                    graph.accountRepository.connections.value["github"]?.status)
                assertFalse(activity.isFinishing)
                assertFalse(activity.isDestroyed)
                instrumentation.runOnMainSync { activity.finish() }
                repeat(100) { if (!activity.isDestroyed) Thread.sleep(50) }
                assertTrue(activity.isDestroyed)
            }
        } finally {
            unreadable.delete()
            val editor = prefs.edit()
            fun restore(key: String, value: Any?) {
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    null -> editor.remove(key)
                    else -> error("Unexpected fixture setting type")
                }
            }
            restore("theme_mode", previousTheme)
            restore("local_use_gpu", previousGpu)
            assertTrue(editor.commit())
        }
    }

    // Measure actual resumed lifecycle and a rendered chat control rather than Looper idleness.
    private fun launchMainActivity(): MainActivity {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as android.app.Application
        val resumed = java.util.concurrent.CountDownLatch(1)
        val activity = java.util.concurrent.atomic.AtomicReference<MainActivity>()
        val callbacks = object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(value: android.app.Activity) {
                if (value is MainActivity) { activity.set(value); resumed.countDown() }
            }
            override fun onActivityCreated(value: android.app.Activity, state: android.os.Bundle?) {}
            override fun onActivityStarted(value: android.app.Activity) {}
            override fun onActivityPaused(value: android.app.Activity) {}
            override fun onActivityStopped(value: android.app.Activity) {}
            override fun onActivitySaveInstanceState(value: android.app.Activity, state: android.os.Bundle) {}
            override fun onActivityDestroyed(value: android.app.Activity) {}
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        try {
            context.startActivity(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            assertTrue("MainActivity must resume within 15 seconds", resumed.await(15, java.util.concurrent.TimeUnit.SECONDS))
            return checkNotNull(activity.get())
        } finally { app.unregisterActivityLifecycleCallbacks(callbacks) }
    }

    private fun assertChatVisible() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = android.os.SystemClock.uptimeMillis() + 15_000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            val nodes = java.util.ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
            automation.rootInActiveWindow?.let { nodes.add(it) }
            var checked = 0
            while (nodes.isNotEmpty() && checked++ < 500) {
                val node = nodes.removeFirst()
                if (node.packageName?.toString() == InstrumentationRegistry.getInstrumentation().targetContext.packageName &&
                    node.text?.toString() in setOf("메시지 입력", "어떤 이야기를 나눌까요?", "새 채팅")) return
                repeat(node.childCount) { index -> node.getChild(index)?.let { nodes.add(it) } }
            }
            Thread.sleep(100)
        }
        fail("The actual chat screen must be visible after startup")
    }

}
