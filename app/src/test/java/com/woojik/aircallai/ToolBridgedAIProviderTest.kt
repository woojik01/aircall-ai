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
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import com.woojik.aircallai.tools.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD-06 Tool-AI 연결: bridged provider의 지시어 실행/결과 반영/차단 안내를 검증한다.
 * FakeAIProvider는 지시어 응답 → 결과 수신 → 최종 응답 순서를 흉내 낸다.
 */
class ToolBridgedAIProviderTest {

    @Test fun repeatedCompletionClaimsWithoutExecutionAreReplacedByHonestStatus() = runTest {
        val logger = ToolExecutionLogger()
        val (provider, _) = bridge(listOf("메모를 저장했습니다."), logger)
        val response = provider.respond(listOf(ChatMessage(ChatMessage.Role.USER, "메모 저장해줘")))
        assertTrue(logger.entries.value.isEmpty())
        assertTrue(response.message.content.contains("실제로 실행하지 못했습니다"))
    }

    @Test fun approvalContinuesTurnWithActualResultBeforeAiAnswers() = runTest {
        val permissions = InMemoryToolPermissionStore()
        val tool = MockGitHubTool()
        val executor = ToolExecutor(listOf(tool), permissions)
        val approval = ToolApprovalCoordinator(permissions, executor, this)
        val fake = FakeAIProvider(listOf("TOOL: github.create_issue title=test", "이슈를 만들었습니다."))
        val provider = ToolBridgedAIProvider(fake, executor, ToolExecutionLogger(), "desc", listOf(tool),
            approvalHandler = { approval.awaitResult(it) })
        val response = async { provider.respond(listOf(ChatMessage(ChatMessage.Role.USER, "이슈 생성"))) }
        runCurrent()
        assertEquals(1, fake.callCount)
        approval.approve()
        response.await()
        assertTrue(fake.receivedHistories[1].any { it.content.contains("TOOL_RESULT github.create_issue 성공") })
    }

    @Test fun malformedWriteIsNeverExecutedOrReturnedAsCompletedDirective() = runTest {
        val logger = ToolExecutionLogger()
        val (provider, _) = bridge(listOf("TOOL: github.create_issue title=\"끝나지 않음"), logger)
        val response = provider.respond(listOf(ChatMessage(ChatMessage.Role.USER, "이슈 생성")))
        assertTrue(logger.entries.value.isEmpty())
        assertTrue(response.message.content.contains("완료된 작업은 없습니다"))
    }

    /** 스크립트: respond() 호출마다 미리 정의된 응답을 차례로 반환한다. */
    private class FakeAIProvider(
        private val script: List<String>,
        override val type: ProviderType = ProviderType.CLOUD,
    ) : AIProvider {
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
            toolsDescription = "desc",
            tools = listOf(MockGitHubTool()),
        )
        return Pair(provider, fake)
    }

    @Test
    fun plainResponsePassesThrough() = runTest {
        val (provider, fake) = bridge(listOf("그냥 대답입니다."))
        val response = provider.respond(listOf(ChatMessage(ChatMessage.Role.USER, "안녕")))
        assertEquals("그냥 대답입니다.", response.message.content)
        // 시스템에는 개요를, 별도 메시지에는 상세 명세를 전달한다.
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
        assertEquals("read_repository", entry.action)
        assertEquals(false, entry.blocked)
        assertTrue(entry.success)
    }

    @Test
    fun localCapabilityQuestionReceivesToolInstructionsWithoutKeywords() = runTest {
        val fake = FakeAIProvider(listOf("기능을 설명합니다."), ProviderType.LOCAL)
        val provider = ToolBridgedAIProvider(
            base = fake,
            executor = ToolExecutor(listOf(MockGitHubTool()), PersistedToolPermissionStore(InMemorySettingsStore())),
            logger = ToolExecutionLogger(),
            toolsDescription = "github.read_repository owner=<소유자> repo=<저장소> — 저장소 조회",
        )
        provider.respond(listOf(ChatMessage(ChatMessage.Role.USER, "무슨 기능을 할 수 있어?")))
        val prompt = fake.receivedHistories.single().first()
        assertEquals(ChatMessage.Role.SYSTEM, prompt.role)
        assertTrue(prompt.content.contains("github"))
        assertTrue(prompt.content.contains("TOOL: tools.list"))
        assertTrue(fake.receivedHistories.single().last().content.contains("github.read_repository"))
        assertTrue(prompt.content.contains("never deny tool access without trying"))
    }

    @Test
    fun localVoiceRequestKeepsCallSyntaxAndExecutesTool() = runTest {
        val fake = FakeAIProvider(
            listOf("TOOL: github.read_repository owner=woojik01 repo=aircall-ai", "조회했습니다."),
            ProviderType.LOCAL,
        )
        val provider = ToolBridgedAIProvider(
            base = fake,
            executor = ToolExecutor(listOf(MockGitHubTool()), PersistedToolPermissionStore(InMemorySettingsStore())),
            logger = ToolExecutionLogger(),
            toolsDescription = "github.read_repository owner=<소유자> repo=<저장소> — 저장소 조회",
        )
        provider.respond(listOf(
            ChatMessage(ChatMessage.Role.SYSTEM, "최종 답변에는 특수 기호를 사용하지 않는다."),
            ChatMessage(ChatMessage.Role.USER, "깃허브 저장소 보여줘"),
        ))
        val prompt = fake.receivedHistories.first().first().content
        assertTrue(prompt.contains("TOOL: <tool>.<action>"))
        assertTrue(fake.receivedHistories.first().last().content.contains("owner=VALUE"))
        assertTrue(fake.receivedHistories.first().any { it.role == ChatMessage.Role.SYSTEM && it.content.contains("최종 답변에는 특수 기호") })
        assertTrue(prompt.contains("Tool syntax is exempt from voice rules"))
        assertTrue(fake.receivedHistories[1].any { it.content.contains("TOOL_RESULT github.read_repository 성공") })
    }
    @Test fun catalogRefreshDoesNotExecuteExternalTool() = runTest {
        val logger = ToolExecutionLogger()
        val (provider, fake) = bridge(listOf("TOOL: tools.list", "기능을 설명합니다."), logger)
        provider.respond(listOf(ChatMessage(ChatMessage.Role.USER, "도구 목록 보여줘")))
        assertTrue(logger.entries.value.isEmpty())
        assertTrue(fake.receivedHistories[1].last().content.contains("TOOL_RESULT tools.list 성공"))
    }

    @Test fun invalidActionAndMissingArgumentsAreRepairedBeforeExecution() = runTest {
        val fake = FakeAIProvider(listOf(
            "TOOL: github.nonexistent owner=a repo=b",
            "TOOL: github.read_repository owner=a",
            "TOOL: github.read_repository owner=a repo=b",
            "조회했습니다.",
        ))
        val logger = ToolExecutionLogger()
        val provider = ToolBridgedAIProvider(fake,
            ToolExecutor(listOf(MockGitHubTool()), PersistedToolPermissionStore(InMemorySettingsStore())),
            logger, "github.read_repository owner=<소유자> repo=<저장소> — 조회")
        provider.respond(listOf(ChatMessage(ChatMessage.Role.USER, "깃허브 저장소 조회")))
        assertEquals(1, logger.entries.value.size)
        assertTrue(fake.receivedHistories[1].last().content.contains("목록에 없는 액션"))
        assertTrue(fake.receivedHistories[2].last().content.contains("필수 인자 누락: repo"))
    }

    @Test fun malformedCallCanBeCorrectedWithoutPartialExecution() = runTest {
        val logger = ToolExecutionLogger()
        val (provider, fake) = bridge(listOf(
            "TOOL: github.read_repository owner=\"a",
            "TOOL: github.read_repository owner=a repo=b",
            "조회했습니다.",
        ), logger)
        val result = provider.respond(listOf(ChatMessage(ChatMessage.Role.USER, "저장소 조회")))
        assertEquals("조회했습니다.", result.message.content)
        assertEquals(1, logger.entries.value.size)
        assertTrue(fake.receivedHistories[1].last().content.contains("호출 문법"))
    }

    @Test fun missingResultPlaceholderCannotBeWrittenAsErrorText() = runTest {
        val logger = ToolExecutionLogger()
        val (provider, _) = bridge(listOf("TOOL: github.create_issue title={{TOOL_RESULT}}"), logger)
        provider.respond(listOf(ChatMessage(ChatMessage.Role.USER, "이슈 생성")))
        assertTrue(logger.entries.value.isEmpty())
    }

    @Test fun systemOverviewPreservesInstructionsAndKeepsDetailedCatalogSeparate() = runTest {
        val fake = FakeAIProvider(listOf("안녕하세요."))
        val provider = ToolBridgedAIProvider(fake,
            ToolExecutor(listOf(MockGitHubTool()), InMemoryToolPermissionStore()),
            ToolExecutionLogger(), "github.read_file owner=<소유자> repo=<저장소> path=<파일 경로> — 파일 읽기")
        val instruction = ChatMessage(ChatMessage.Role.SYSTEM, "기존 음성 지시")
        provider.respond(listOf(instruction, ChatMessage(ChatMessage.Role.USER, "안녕")))
        val input = fake.receivedHistories.single()
        assertTrue(input.first().content.contains("github"))
        assertTrue(input.first().content.contains("TOOL: tools.list"))
        assertTrue(input.first().content.contains("TOOL_RESULT"))
        assertTrue(!input.first().content.contains("path=<파일 경로>"))
        assertTrue(input.contains(instruction))
        assertTrue(input.any { it.role == ChatMessage.Role.USER && it.content.contains("path=<파일 경로>") })
    }

    @Test fun localReducerKeepsCatalogOriginalRequestAndToolResultInEveryRound() = runTest {
        val fake = FakeAIProvider(listOf("TOOL: tools.list",
            "TOOL: github.read_repository owner=a repo=b", "조회했습니다."), ProviderType.LOCAL)
        val provider = ToolBridgedAIProvider(fake,
            ToolExecutor(listOf(MockGitHubTool()), InMemoryToolPermissionStore()),
            ToolExecutionLogger(), "github.read_repository owner=<소유자> repo=<저장소> — 저장소 조회")
        val original = "깃허브 저장소 조회"
        provider.respond(listOf(ChatMessage(ChatMessage.Role.SYSTEM, "최종 답변은 음성에 맞춘다."),
            ChatMessage(ChatMessage.Role.USER, original)))
        assertEquals(3, fake.receivedHistories.size)
        fake.receivedHistories.forEach { history ->
            val bounded = com.woojik.aircallai.ai.local.localInferenceHistory(history)
            assertTrue(bounded.first().role == ChatMessage.Role.SYSTEM)
            assertTrue(bounded.last().content.contains(original))
            assertTrue(bounded.last().content.contains("github.read_repository"))
        }
        val finalInput = com.woojik.aircallai.ai.local.localInferenceHistory(fake.receivedHistories.last()).last()
        assertTrue(finalInput.content.contains("TOOL_RESULT github.read_repository 성공"))
    }

}

