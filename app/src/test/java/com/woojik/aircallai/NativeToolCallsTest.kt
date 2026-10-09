package com.woojik.aircallai

import com.woojik.aircallai.ai.cloud.*
import com.woojik.aircallai.tools.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class NativeToolCallsTest {
    @Test fun parsesNativeMultilineArgumentsAndRejectsUnknownCalls() {
        val envelope = NativeToolCalls.envelope("call_1", "gmail__send_email", """{"to":"a@example.com","subject":"제목","body":"첫 줄\n둘째 줄"}""")
        val parsed = ToolCallParser.parseFirst(envelope)!!
        assertEquals("첫 줄\n둘째 줄", parsed.arguments["body"])
        assertTrue(ToolCallParser.hasDirective(envelope))
        assertNull(ToolCallParser.parseFirst("TOOL_JSON:{\"id\":\"x\",\"tool\":\"unknown\",\"action\":\"delete\",\"arguments\":{}}"))
        assertNull(ToolCallParser.parseFirst(envelope + "\nTOOL: notes.add_note text=other"))
    }

    @Test fun fragmentedNativeStreamNeverExposesToolSyntaxAsSpeech() = runTest {
        val data = "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"function\":{\"name\":\"notes__add_note\",\"arguments\":\"{\\\"text\\\":\"}}]}}]}\n\n" +
            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"\\\"메모\\\"}\"}}]}}]}\n\n" + "data: [DONE]\n\n"
        val updates = mutableListOf<String>()
        val result = readChatStream(data.reader().buffered(), allowNative = true) { updates += it }
        assertTrue(updates.isEmpty())
        assertEquals("메모", ToolCallParser.parseFirst(result)!!.arguments["text"])
    }

    @Test fun editedApprovalExecutesOnceAndCannotChangeAction() = runTest {
        var executed: ToolRequest? = null
        var count = 0
        val tool = object : Tool {
            override val name = "gmail"
            override val description = "test"
            override fun riskFor(action: String) = ToolRisk.WRITE
            override suspend fun execute(request: ToolRequest): ToolResult { count++; executed = request; return ToolResult(true, "ok") }
        }
        val permissions = InMemoryToolPermissionStore()
        val coordinator = ToolApprovalCoordinator(permissions, ToolExecutor(listOf(tool), permissions), this)
        val original = ToolRequest("gmail", "send_email", mapOf("to" to "old@example.com", "body" to "old"))
        val result = async { coordinator.awaitResult(original) }
        runCurrent()
        coordinator.approve(original.copy(action = "other"), expected = original)
        assertEquals(original, coordinator.pending.value)
        coordinator.approve(original.copy(arguments = original.arguments + ("to" to "new@example.com")), expected = original)
        coordinator.approve()
        assertTrue(result.await().success)
        assertEquals(1, count)
        assertEquals("new@example.com", executed!!.arguments["to"])
    }
}
