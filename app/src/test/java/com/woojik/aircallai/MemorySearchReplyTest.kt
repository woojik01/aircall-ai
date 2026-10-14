package com.woojik.aircallai

import com.woojik.aircallai.ai.provider.*
import com.woojik.aircallai.conversation.ConversationEngine
import com.woojik.aircallai.tools.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class MemorySearchReplyTest {
    private val catalog = "memory.search query=VALUE [limit=VALUE] — 검색"

    private class ReadTool : Tool {
        override val name = "memory"
        override val description = "test memory"
        override fun riskFor(action: String) = ToolRisk.READ
        var reads = 0
        override suspend fun execute(request: ToolRequest): ToolResult {
            reads++
            return ToolResult(true, "이름=테스트별명")
        }
    }

    private fun bridge(tool: ReadTool, type: ProviderType,
        generate: suspend (List<ChatMessage>) -> String): ToolBridgedAIProvider {
        val backend = object : AIProvider {
            override val type = type
            override val displayName = "test"
            override suspend fun isReady() = true
            override suspend fun respond(history: List<ChatMessage>) = AIResponse(
                ChatMessage(ChatMessage.Role.ASSISTANT, generate(history)), type, 0)
        }
        return ToolBridgedAIProvider(backend,
            ToolExecutor(listOf(tool), InMemoryToolPermissionStore()),
            ToolExecutionLogger(), catalog, tools = listOf(tool))
    }

    @Test fun successfulSearchAlwaysReachesTranscriptAfterEmptyOrFailedGeneration() = runTest {
        for (type in listOf(ProviderType.LOCAL, ProviderType.CLOUD)) {
            for (fails in listOf(false, true)) {
                val tool = ReadTool()
                val engine = ConversationEngine(bridge(tool, type) {
                    if (fails) throw AIProviderException(ProviderErrorKind.INFERENCE_FAILED)
                    "   "
                })
                assertTrue(engine.submitUserMessage("내 이름이 뭐야?"))
                assertTrue(engine.latestAssistantMessage()!!.content.contains("이름=테스트별명"))
                assertEquals(1, tool.reads)
            }
        }
    }

    @Test fun repeatingSameSuccessfulSearchReturnsFactsWithoutExecutingAgain() = runTest {
        val tool = ReadTool()
        val provider = bridge(tool, ProviderType.LOCAL) {
            "TOOL: memory.search query=이름 limit=20"
        }
        val response = provider.respond(listOf(ChatMessage(ChatMessage.Role.USER, "내 이름이 뭐야?")))
        assertTrue(response.message.content.contains("이름=테스트별명"))
        assertFalse(response.message.content.contains("한도"))
        assertEquals(1, tool.reads)
    }

    @Test fun timeoutAfterSearchReturnsReadResult() = runTest {
        val tool = ReadTool()
        val provider = bridge(tool, ProviderType.CLOUD) {
            delay(120_000)
            "unreachable"
        }
        assertTrue(provider.respond(listOf(ChatMessage(ChatMessage.Role.USER, "내 이름이 뭐야?")))
            .message.content.contains("이름=테스트별명"))
        assertEquals(60_000L, testScheduler.currentTime)
    }

    @Test fun successfulModelReplyIsPreservedAndReceivesFacts() = runTest {
        for (type in listOf(ProviderType.LOCAL, ProviderType.CLOUD)) {
            val tool = ReadTool()
            val provider = bridge(tool, type) { history ->
                assertTrue(history.last().content.contains("이름=테스트별명"))
                "저장된 이름은 테스트별명이에요."
            }
            assertEquals("저장된 이름은 테스트별명이에요.", provider.respond(
                listOf(ChatMessage(ChatMessage.Role.USER, "내 이름이 뭐야?"))).message.content)
        }
    }

    @Test fun cancellationIsNeverTurnedIntoAnAssistantReply() = runTest {
        val provider = bridge(ReadTool(), ProviderType.CLOUD) { throw CancellationException("stop") }
        try {
            provider.respond(listOf(ChatMessage(ChatMessage.Role.USER, "내 이름이 뭐야?")))
            fail("Expected cancellation")
        } catch (expected: CancellationException) {
            assertEquals("stop", expected.message)
        }
    }

    @Test fun generationErrorBeforeAnyReadIsStillAnError() = runTest {
        val tool = ReadTool()
        val provider = bridge(tool, ProviderType.CLOUD) { throw AIProviderException(ProviderErrorKind.NETWORK) }
        try {
            provider.respond(listOf(ChatMessage(ChatMessage.Role.USER, "안녕")))
            fail("Expected provider error")
        } catch (expected: AIProviderException) {
            assertEquals(ProviderErrorKind.NETWORK, expected.kind)
            assertEquals(0, tool.reads)
        }
    }
}
