package com.woojik.aircallai

import com.woojik.aircallai.ai.provider.*
import com.woojik.aircallai.chat.*
import com.woojik.aircallai.conversation.ConversationEngine
import com.woojik.aircallai.core.security.CryptoEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChatRoomRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val crypto = object : CryptoEngine {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 73).toByte() }.toByteArray()
        override fun decrypt(blob: ByteArray) = encrypt(blob)
    }
    private fun store() = ChatRoomStore(java.io.File(folder.root, "chats.enc"), crypto)

    @Test fun roundTripPreservesIndependentRoomsAndRenamedTitles() {
        val store = store()
        val a = ChatRoom(title = "프로젝트", messages = listOf(ChatMessage(ChatMessage.Role.USER, "내 대화")), renamed = true)
        val b = ChatRoom(title = "두 번째")
        store.write(listOf(a, b))
        assertEquals(listOf(a, b), store.read())
        assertFalse(java.io.File(folder.root, "chats.enc").readText().contains("내 대화"))
        store.write(listOf(b))
        assertEquals(listOf(b), store.read())
    }

    @Test fun switchingAndRenamingDoNotMixRoomHistories() = runTest {
        val repo = ChatRoomRepository(store(), backgroundScope)
        repo.initialize()
        val first = repo.newRoom()
        repo.updateMessages(listOf(ChatMessage(ChatMessage.Role.USER, "첫 질문")))
        repo.rename(first.id, "직접 정한 이름")
        val second = repo.newRoom()
        repo.updateMessages(listOf(ChatMessage(ChatMessage.Role.USER, "다른 질문")))
        assertEquals("첫 질문", repo.select(first.id)!!.messages.single().content)
        repo.updateMessages(listOf(ChatMessage(ChatMessage.Role.USER, "질문 변경")))
        assertEquals("직접 정한 이름", repo.rooms.value.first { it.id == first.id }.title)
        assertEquals("다른 질문", repo.rooms.value.first { it.id == second.id }.messages.single().content)
        repo.delete(first.id)
        assertNull(repo.activeId.value)
        assertEquals(listOf(second.id), repo.rooms.value.map { it.id })
    }

    @Test fun responseFromPreviousRoomCannotAppendToRestoredRoom() = runTest {
        val answer = CompletableDeferred<Unit>()
        val provider = object : AIProvider {
            override val type = ProviderType.LOCAL
            override val displayName = "test"
            override suspend fun isReady() = true
            override suspend fun respond(history: List<ChatMessage>): AIResponse {
                answer.await()
                return AIResponse(ChatMessage(ChatMessage.Role.ASSISTANT, "이전 방 답변"), type, 0)
            }
        }
        val engine = ConversationEngine(provider)
        val job = launch { engine.submitUserMessage("이전 질문") }
        runCurrent()
        val other = listOf(ChatMessage(ChatMessage.Role.USER, "새 방 질문"))
        engine.restore(other)
        answer.complete(Unit); job.join()
        assertEquals(other, engine.transcript.value)
    }

    @Test fun newRoomAndSwitchingRemoveUnusedDraftsEvenWhenRenamed() = runTest {
        val repo = ChatRoomRepository(store(), backgroundScope)
        repo.initialize()
        val first = repo.newRoom()
        repo.rename(first.id, "비어 있는 이름 변경 방")
        val used = repo.newRoom()
        assertFalse(repo.rooms.value.any { it.id == first.id })
        repo.updateMessages(listOf(ChatMessage(ChatMessage.Role.USER, "안녕")))
        val draft = repo.newRoom()
        repo.select(used.id)
        assertEquals(listOf(used.id), repo.rooms.value.map { it.id })
        assertFalse(repo.rooms.value.any { it.id == draft.id })
    }

    @Test fun restartCleansLegacyEmptyRoomsAndKeepsChatsWithoutAssistantReply() = runTest {
        val store = store()
        val used = ChatRoom(messages = listOf(ChatMessage(ChatMessage.Role.USER, "실패한 질문도 보존")))
        store.write(listOf(ChatRoom(), ChatRoom(messages = listOf(ChatMessage(ChatMessage.Role.SYSTEM, "시스템"))), used))
        val repo = ChatRoomRepository(store, backgroundScope)
        repo.initialize()
        assertEquals(listOf(used), repo.rooms.value)
    }
}
