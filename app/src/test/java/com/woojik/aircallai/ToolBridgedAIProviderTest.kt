package com.woojik.aircallai

import com.woojik.aircallai.ai.provider.AIProvider
import com.woojik.aircallai.ai.provider.AIResponse
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.ai.provider.ProviderType
import com.woojik.aircallai.settings.InMemorySettingsStore
import com.woojik.aircallai.tools.MockGitHubTool
import com.woojik.aircallai.tools.PersistedToolPermissionStore
import com.woojik.aircallai.tools.ToolBridgedAIProvider
import com.woojik.aircallai.tools.ToolExecutionLogger
import com.woojik.aircallai.tools.ToolExecutor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD-06 Tool-AI 연결: bridged provider의 지시어 실행/결과 반영/차단 안내를 검증한다.
 * FakeAIProvider는 지시어 응답 → 결과 수신 → 최종 응답 순서를 흉내 낸다.
 */
class ToolBridgedAIProviderTest {

    /** 스크립트: respond() 호출마다 미리 정의된 응답을 차례로 반환한다. */
    private class FakeAIProvider(private val script: List<String>) : AIProvider {
        override val type = ProviderType.CLOUD
        override val displayName = "fake"
        var callCount = 0
        val receivedHistories = mutableListOf<List<ChatMessage>>()
        override suspend fun isReady() = true
        override suspend fun respond(history: List<ChatMessage>): AIResponse {
            receivedHistories.add(history)
            val content = script[minOf(callCount, script.size - 1)]
            callCount++
            return AIResponse(ChatMessage(ChatMessage.Role.ASSISTANT, content), type, 0)
        }
    }

    private fun bridge(
        script: List<String>,
        logger: ToolExecutionLogger = ToolExecutionLogger(),
    ): Pair<ToolBridgedAIProvider, FakeAIProvider> {
        val fake = FakeAIProvider(script)
        val provider = ToolBridgedAIProvider(
            base = fake,
            executor = ToolExecutor(listOf(MockGitHubTool()), PersistedToolPermissionStore(InMemorySettingsStore())),
            logger = logger,
            toolsDescription
 = "desc",
            tools = listOf(MockGitHubTool()),
        )
        return Pair(provider, fake)
    }

    @Test
    fun plainResponsePassesThrough() = runTest {
        val (provider, fake) = bridge(listOf("그냥 대답입니다."))
        val response = provider.respond(listOf(ChatMessage(ChatMessage.Role.USER, "안녕")))
        assertEquals("그냥 대답입니다.", response.message.content)
        // 시스템 프롬프트가 주입되었는지 확인: 첫 이력의 첫 메시지는 SYSTEM 역할이어야 한다.
        assertEquals(ChatMessage.Role.SYSTEM, fake.receivedHistories[0][0].role)
    }

    @Test
    fun toolDirectiveIsExecutedAndResultFedBack() = runTest {
        val (provider, fake) = bridge(
            listOf(
                "TOOL: github.read_repository owner=woojik01 repo=aircall-ai",
                "저장소 조회 결과를 전달했습니다.",
            ),
        )
        val response = provider.respond(listOf(ChatMessage(ChatMessage.Role.USER, "내 저장소 보여줘")))
        assertEquals("저장소 조회 결과를 전달했습니다.", response.message.content)
        // 두 번째 respond 호출의 이력에 TOOL_RESULT가 포함되어야 한다.
        assertEquals(2, fake.receivedHistories.size)
        assertTrue(fake.receivedHistories[1].any { it.content.contains("TOOL_RESULT github.read_repository") })
    }

    @Test
    fun blockedWriteShowsApprovalMessage() = runTest {
        val (provider, _) = bridge(
            listOf("TOOL: github.create_issue owner=o repo=r title=t"),
        )
        val response = provider.respond(listOf(ChatMessage(ChatMessage.Role.USER, "이슈 만들어줘")))
        assertTrue(response.message.content.contains("승인"))
    }

    @Test
    fun executionIsLogged() = runTest {
        val logger = ToolExecutionLogger()
        val (provider, _) = bridge(
            listOf("TOOL: github.read_repository owner=a repo=b", "완료"),
            logger,
        )
        provider.respond(listOf(ChatMessage(ChatMessage.Role.USER, "조회")))
        assertEquals(1, logger.entries.value.size)
        val entry = logger.entries.value[0]
        assertEquals("github", entry.toolName)
        asse
rtEquals("read_repository", entry.action)
        assertEquals(false, entry.blocked)
        assertTrue(entry.success)
    }
}
