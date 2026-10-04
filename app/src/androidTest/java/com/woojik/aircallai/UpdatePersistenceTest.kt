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
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val graph = (context.applicationContext as AirCallApp).graph
        repeat(100) {
            if (!graph.chatRooms.ready.value) Thread.sleep(50)
        }
        assertTrue(graph.chatRooms.ready.value)
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
}
