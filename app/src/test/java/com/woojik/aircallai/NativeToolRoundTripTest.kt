package com.woojik.aircallai

import com.woojik.aircallai.ai.cloud.*
import com.woojik.aircallai.ai.provider.*
import java.io.*
import java.net.*
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeToolRoundTripTest {
    @Test fun nativeCallAndToolResultUseProviderRoles() = runTest {
        val requests = mutableListOf<ByteArrayOutputStream>()
        val responses = ArrayDeque(listOf(
            """{"choices":[{"message":{"content":null,"tool_calls":[{"id":"call_1","type":"function","function":{"name":"notes__add_note","arguments":"{\"text\":\"메모\"}"}}]}}]}""",
            """{"choices":[{"message":{"content":"저장했습니다."}}]}"""))
        val adapter = HttpCloudApiAdapter(nativeToolsEnabled = { true }, connect = {
            val output = ByteArrayOutputStream().also { requests += it }
            val response = responses.removeFirst()
            object : HttpURLConnection(URL("https://example.invalid")) {
                override fun connect() {}
                override fun disconnect() {}
                override fun usingProxy() = false
                override fun getResponseCode() = 200
                override fun getOutputStream() = output
                override fun getInputStream() = ByteArrayInputStream(response.toByteArray(Charsets.UTF_8))
            }
        })
        val original = ChatMessage(ChatMessage.Role.USER, "메모를 저장해줘")
        val call = adapter.chat("key", "https://example.invalid", "model", listOf(original))
        adapter.chat("key", "https://example.invalid", "model", listOf(original,
            ChatMessage(ChatMessage.Role.ASSISTANT, call), ChatMessage(ChatMessage.Role.USER, "TOOL_RESULT notes.add_note 성공: 저장했습니다")))
        val body = JSONObject(requests.last().toString("UTF-8"))
        assertFalse(body.getBoolean("parallel_tool_calls"))
        assertEquals(9, body.getJSONArray("tools").length())
        val messages = body.getJSONArray("messages")
        assertEquals("assistant", messages.getJSONObject(2).getString("role"))
        assertEquals("call_1", messages.getJSONObject(2).getJSONArray("tool_calls").getJSONObject(0).getString("id"))
        assertEquals("tool", messages.getJSONObject(3).getString("role"))
        assertEquals("call_1", messages.getJSONObject(3).getString("tool_call_id"))
    }
}
