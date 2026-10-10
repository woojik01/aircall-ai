package com.woojik.aircallai

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.backup.*
import com.woojik.aircallai.chat.*
import com.woojik.aircallai.core.security.AndroidKeystoreCrypto
import com.woojik.aircallai.tools.FileNotesStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PortableBackupTest {
    @Test fun backupRestoresAcrossIndependentDeviceKeys() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        val directory = java.io.File(context.cacheDir, "portable-backup-$id").apply { mkdirs() }
        val firstAlias = "aircall-backup-source-$id"
        val secondAlias = "aircall-backup-destination-$id"
        try {
            val original = ChatRoomStore(java.io.File(directory, "source.enc"), AndroidKeystoreCrypto(firstAlias))
            val room = ChatRoom(title = "백업 검사", messages = listOf(ChatMessage(ChatMessage.Role.USER, "비공개 대화")))
            original.write(listOf(room))
            val notes = FileNotesStore(java.io.File(directory, "notes.txt"))
            notes.append("기억할 메모")
            val data = BackupSnapshot(original.read(), notes.all(), "설명을 자세히 해 주세요").encode()
            val password = "portable test password".toCharArray()
            val archive = BackupCodec.encrypt(data, password)
            val restored = BackupSnapshot.decode(BackupCodec.decrypt(archive, password))
            val destination = ChatRoomStore(java.io.File(directory, "destination.enc"), AndroidKeystoreCrypto(secondAlias))
            destination.write(restored.rooms)
            assertEquals(listOf(room), destination.read())
            assertTrue(restored.notes.single().contains("기억할 메모"))
            assertEquals("설명을 자세히 해 주세요", restored.memory)
            assertFalse(archive.toString(Charsets.UTF_8).contains("비공개 대화"))
            password.fill('\u0000')
        } finally {
            // Only the uniquely created test directory and key aliases are removed.
            directory.deleteRecursively()
            java.security.KeyStore.getInstance("AndroidKeyStore").apply {
                load(null); deleteEntry(firstAlias); deleteEntry(secondAlias)
            }
        }
    }
}
