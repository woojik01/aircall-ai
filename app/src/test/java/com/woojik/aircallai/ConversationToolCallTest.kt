package com.woojik.aircallai

import com.woojik.aircallai.ai.provider.AIProvider
import com.woojik.aircallai.ai.provider.AIResponse
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.ai.provider.ProviderType
import com.woojik.aircallai.conversation.ConversationEngine
import com.woojik.aircallai.tools.InMemoryToolPermissionStore
import com.woojik.aircallai.tools.Tool
import com.woojik.aircallai.tools.ToolActionSpec
import com.woojik.aircallai.tools.ToolCallProtocol
import com.woojik.aircallai.tools.ToolExecutor
import com.woojik.aircallai.tools.ToolRequest
import com.woojik.aircallai.tools.ToolResult
import com.woojik.aircallai.tools.ToolRisk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationToolCallTest {
    private class ScriptedProvider(private val replies: List<String>) : AIProvider {
        val requests = mutableListOf<List<ChatMessage>>()
        override val type = ProviderType.CLOUD
        override val displayName = "scripted"
        override suspend fun isReady() = true
        override suspend fun respond(history: List<ChatMessage>): AIResponse {
            requests += history
            val reply = replies[minOf(requests.size - 1, replies.lastIndex)]
            return AIResponse(ChatMessage(ChatMessage.Role.ASSISTANT, reply), type, 0)
        }
    }

    private class RecordingTool(private val risk: ToolRisk = ToolRisk.READ) : Tool {
        val executed = mutableListOf<ToolRequest>()
        override val name = "github"
        override val description = "test"
        override val actions = listOf(ToolActionSpec("read_repository", "저장소 조회", listOf("owner", "repo")))
        override fun riskFor(action: String) = risk
        override suspend fun execute(request: ToolRequest): ToolResult {
            executed += request
            return ToolResult(true, "저장소 woojik01/aircall-ai, 스타 3개")
        }
    }

    private val call = "TOOL: github.read_repository\nowner: woojik01\nrepo: aircall-ai"

    @Test fun parsesMultilineArguments() {
        val request = ToolCallProtocol.parse("```\nTOOL: github.create_issue\nowner: o\nrepo: r\ntitle: 제목\nbody: 첫 줄\n둘째 줄\n```")!!
        assertEquals("github", request.toolName)
        assertEquals("create_issue", request.action)
        assertEquals("첫 줄\n둘째 줄", request.arguments["body"])
        assertEquals("제목", request.arguments["title"])
    }

    @Test fun plainTextIsNotAToolCall() {
        assertNull(ToolCallProtocol.parse("오늘 날씨 좋네요: 맑음"))
    }

    @Test fun toolCallIsExecutedAndOnlyFinalAnswerIsShown() = runTest {
        val tool = RecordingTool()
        val provider = ScriptedProvider(listOf(call, "스타가 3개예요"))
        val engine = ConversationEngine(provider, ToolExecutor(listOf(tool), InMemoryToolPermissionStore()))

        engine.submitUserMessage("내 저장소 스타 몇 개야?", voiceMode = true)

        assertEquals(mapOf("owner" to "woojik01", "repo" to "aircall-ai"), tool.executed.single().arguments)
        val transcript = engine.transcript.value
        assertEquals(listOf("내 저장소 스타 몇 개야?", "스타가 3개예요"), transcript.map { it.content })
        assertTrue(provider.requests.first().any { it.role == ChatMessage.Role.SYSTEM && it.content.contains("github.read_repository") })
        assertTrue(provider.requests[1].last().content.contains("저장소 woojik01/aircall-ai, 스타 3개"))
    }

    @Test fun unapprovedWriteIsNotExecutedAndAiIsTold() = runTest {
        val tool = RecordingTool(ToolRisk.WRITE)
        val provider = ScriptedProvider(listOf(call, "설정에서 허용해 주세요"))
        val engine = ConversationEngine(provider, ToolExecutor(listOf(tool), InMemoryToolPermissionStore()))

        engine.submitUserMessage("이슈 만들어줘")

        assertTrue(tool.executed.isEmpty())
        assertTrue(provider.requests[1].last().content.contains("사용자 승인이 필요한 작업입니다"))
    }

    @Test fun endlessToolCallsAreCapped() = runTest {
        val tool = RecordingTool()
        val provider = ScriptedProvider(listOf(call))
        val engine = ConversationEngine(provider, ToolExecutor(listOf(tool), InMemoryToolPermissionStore()))

        engine.submitUserMessage("조회")

        assertEquals(3, tool.executed.size)
        assertEquals("요청을 처리하지 못했어요. 다시 말씀해 주세요", engine.latestAssistantMessage()!!.content)
    }

    @Test fun withoutExecutorToolTextIsUntouched() = runTest {
        val provider = ScriptedProvider(listOf("안녕"))
        val engine = ConversationEngine(provider)
        engine.submitUserMessage("hi")
        assertEquals(1, provider.requests.size)
        assertTrue(provider.requests.first().none { it.role == ChatMessage.Role.SYSTEM })
    }
}
