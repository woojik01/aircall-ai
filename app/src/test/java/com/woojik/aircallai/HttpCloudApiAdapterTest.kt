package com.woojik.aircallai

import com.woojik.aircallai.ai.cloud.HttpCloudApiAdapter
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.ai.provider.AIProviderException
import com.woojik.aircallai.ai.provider.ProviderErrorKind
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HttpCloudApiAdapterTest {
    private class Connection(val code: Int = 200, val writeFails: Boolean = false) :
        HttpURLConnection(URL("https://example.invalid")) {
        val request = ByteArrayOutputStream()
        var disconnected = false
        override fun connect() {}
        override fun usingProxy() = false
        override fun disconnect() { disconnected = true }
        override fun getResponseCode() = code
        override fun getOutputStream(): java.io.OutputStream {
            if (writeFails) throw IOException("write failed")
            return request
        }
        override fun getInputStream() = ByteArrayInputStream(
            """{"choices":[{"message":{"content":"ok"}}]}""".toByteArray()
        )
    }

    @Test
    fun preservesAllRolesAndClosesSuccessfulConnection() = runTest {
        val conn = Connection()
        val adapter = HttpCloudApiAdapter { conn }
        assertEquals("ok", adapter.chat("key", "https://example.invalid", "model", listOf(
            ChatMessage(ChatMessage.Role.SYSTEM, "tool rules"),
            ChatMessage(ChatMessage.Role.USER, "hi"),
            ChatMessage(ChatMessage.Role.ASSISTANT, "hello"),
            ChatMessage(ChatMessage.Role.USER, "next"),
        )))
        val messages = JSONObject(conn.request.toString("UTF-8")).getJSONArray("messages")
        assertEquals("system", messages.getJSONObject(1).getString("role"))
        assertEquals("tool rules", messages.getJSONObject(1).getString("content"))
        assertEquals("user", messages.getJSONObject(2).getString("role"))
        assertEquals("assistant", messages.getJSONObject(3).getString("role"))
        assertTrue(conn.disconnected)
    }

    @Test
    fun closesConnectionOnHttpFailure() = runTest {
        val conn = Connection(code = 401)
        try {
            HttpCloudApiAdapter { conn }.chat("key", "https://example.invalid", "m", emptyList())
            fail("expected authentication error")
        } catch (e: AIProviderException) { assertEquals(ProviderErrorKind.AUTH_FAILED, e.kind) }
        assertTrue(conn.disconnected)
    }

    @Test
    fun closesConnectionOnRequestWriteFailure() = runTest {
        val conn = Connection(writeFails = true)
        try {
            HttpCloudApiAdapter { conn }.chat("key", "https://example.invalid", "m", emptyList())
            fail("expected network error")
        } catch (e: AIProviderException) { assertEquals(ProviderErrorKind.NETWORK, e.kind) }
        assertTrue(conn.disconnected)
    }
}
