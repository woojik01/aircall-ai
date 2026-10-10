package com.woojik.aircallai

import com.woojik.aircallai.ai.provider.*
import com.woojik.aircallai.backup.*
import com.woojik.aircallai.chat.*
import com.woojik.aircallai.core.security.CryptoEngine
import com.woojik.aircallai.conversation.ConversationEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupTransferTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun portableEncryptionRejectsWrongPasswordAndTampering() {
        val plain = "비공개 대화".toByteArray()
        val password = "test password".toCharArray()
        val bytes = BackupCodec.encrypt(plain, password)
        assertFalse(bytes.toString(Charsets.UTF_8).contains("비공개"))
        assertArrayEquals(plain, BackupCodec.decrypt(bytes, password))
        for (attempt in listOf(bytes, bytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })) {
            try { BackupCodec.decrypt(attempt, if (attempt === bytes) "wrong".toCharArray() else password); fail() }
            catch (_: javax.crypto.AEADBadTagException) { }
        }
    }
    @Test fun snapshotDoesNotImportCredentialsOrSystemInstructions() {
        val snapshot = BackupSnapshot(listOf(ChatRoom(title = "내 대화", messages = listOf(ChatMessage(ChatMessage.Role.USER, "안녕")))),
            listOf("123 메모"), "민수", mapOf("apiKey" to "secret", "theme" to "dark"))
        val decoded = BackupSnapshot.decode(snapshot.encode())
        assertEquals(snapshot.rooms, decoded.rooms)
        assertEquals(mapOf("theme" to "dark"), decoded.preferences)
        val malicious = snapshot.encode().toString(Charsets.UTF_8).replace("\"USER\"", "\"SYSTEM\"")
        try { BackupSnapshot.decode(malicious.toByteArray()); fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun importPreservesExistingRoomsAndIsIdempotent() = runTest {
        val crypto = object : CryptoEngine {
            override fun encrypt(plain: ByteArray) = plain
            override fun decrypt(blob: ByteArray) = blob
        }
        val store = ChatRoomStore(folder.root.resolve("chats.enc"), crypto)
        val repo = ChatRoomRepository(store, backgroundScope)
        repo.initialize()
        val existing = repo.newRoom()
        repo.updateMessages(listOf(ChatMessage(ChatMessage.Role.USER, "현재 대화")))
        val imported = ChatRoom(title = "백업", messages = listOf(ChatMessage(ChatMessage.Role.USER, "백업 대화")))
        assertEquals(1, repo.importRooms(listOf(imported)))
        assertEquals(0, repo.importRooms(listOf(imported)))
        assertTrue(repo.rooms.value.any { it.id == existing.id && it.messages.single().content == "현재 대화" })
        assertEquals(2, store.read().size)
    }
    @Test fun retryUsesFailedInputOnceAndNeverRetriesAfterWriteAttempt() = runTest {
        var fail = true
        val provider = object : AIProvider {
            override val type = ProviderType.CLOUD
            override val displayName = "test"
            override var retrySafe = true
            override suspend fun isReady() = true
            override suspend fun respond(history: List<ChatMessage>): AIResponse {
                if (fail) throw AIProviderException(ProviderErrorKind.NETWORK)
                return AIResponse(ChatMessage(ChatMessage.Role.ASSISTANT, "ok"), type, 0)
            }
        }
        val engine = ConversationEngine(provider)
        assertFalse(engine.submitUserMessage("질문"))
        fail = false
        assertTrue(engine.submitUserMessage("질문", reuseFailedInput = true))
        assertEquals(1, engine.transcript.value.count { it.role == ChatMessage.Role.USER })
        provider.retrySafe = false; fail = true
        assertFalse(engine.submitUserMessage("메일"))
        assertFalse(engine.retryAllowed.value)
        assertFalse(engine.submitUserMessage("메일", reuseFailedInput = true))
    }
}
