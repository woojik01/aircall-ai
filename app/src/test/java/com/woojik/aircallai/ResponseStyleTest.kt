package com.woojik.aircallai

import com.woojik.aircallai.ai.cloud.HttpCloudApiAdapter
import com.woojik.aircallai.ai.provider.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ResponseStyleTest {
    @Test fun textAndVoiceSendDifferentInstructions() = runTest {
        suspend fun prompt(voice: Boolean): String {
            val request = ByteArrayOutputStream()
            val connection = object : HttpURLConnection(URL("https://example.invalid")) {
                override fun connect() {}
                override fun disconnect() {}
                override fun usingProxy() = false
                override fun getResponseCode() = 200
                override fun getOutputStream() = request
                override fun getInputStream() = ByteArrayInputStream("""{"choices":[{"message":{"content":"ok"}}]}""".toByteArray())
            }
            val history = if (voice) listOf(ChatMessage(ChatMessage.Role.SYSTEM, ResponseStyle.VOICE_MARKER)) else emptyList()
            HttpCloudApiAdapter { connection }.chat("key", "https://example.invalid", "model", history)
            return JSONObject(request.toString("UTF-8")).getJSONArray("messages").getJSONObject(0).getString("content")
        }
        assertTrue(prompt(false).contains("Markdown"))
        assertTrue(prompt(true).contains("한두 문장"))
        assertFalse(ResponseStyle.isVoice(listOf(ChatMessage(ChatMessage.Role.USER, ResponseStyle.VOICE_MARKER))))
    }
}
