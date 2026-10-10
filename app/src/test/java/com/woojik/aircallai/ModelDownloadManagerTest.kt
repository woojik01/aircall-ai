package com.woojik.aircallai

import com.woojik.aircallai.ai.local.LocalModelInfo
import com.woojik.aircallai.ai.local.ModelDownloadManager
import com.woojik.aircallai.ai.local.ModelDownloadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ModelDownloadManagerTest {
    @get:Rule val temp = TemporaryFolder()
    private val bytes = "synthetic native model".toByteArray()
    private val model get() = LocalModelInfo(
        "test", "Test", "test.litertlm", "https://models.example/test.litertlm",
        bytes.size.toLong(), 4096, "fixture",
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
    )
    private open class Connection(private val body: ByteArray) : HttpURLConnection(URL("https://models.example")) {
        var disconnected = false
        override fun getResponseCode() = 200
        override fun getContentLengthLong() = -1L // CDN may omit Content-Length.
        override fun getInputStream() = ByteArrayInputStream(body)
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun connect() = Unit
    }

    @Test fun legacyCleanupPreservesNativeModelAndOtherFiles() {
        val manager = ModelDownloadManager(temp.root)
        temp.root.resolve("gemma-4-e2b-it-web.task").writeText("old")
        temp.root.resolve("gemma-4-e4b-it-web.task.part").writeText("partial")
        val unrelated = temp.root.resolve("unrelated.txt").apply { writeText("keep") }
        manager.targetFile(model).writeBytes(bytes)
        assertTrue(manager.hasLegacyFiles())
        assertTrue(manager.deleteLegacyFiles())
        assertFalse(manager.hasLegacyFiles())
        assertTrue(manager.isDownloaded(model))
        assertEquals("keep", unrelated.readText())
    }

    @Test fun verifiedDownloadCanBeDeletedAndStateRefreshes() = runBlocking {
        val connection = Connection(bytes)
        val manager = ModelDownloadManager(temp.root) { connection }
        assertTrue(manager.download(model).isSuccess)
        assertArrayEquals(bytes, manager.targetFile(model).readBytes())
        assertTrue(connection.disconnected)
        assertTrue(manager.states.value[model.id] is ModelDownloadState.Completed)
        assertTrue(manager.delete(model))
        assertFalse(manager.isDownloaded(model))
        assertEquals(ModelDownloadState.Idle, manager.states.value[model.id])
    }

    @Test fun truncatedAndSameSizeCorruptFilesAreNeverInstalled() = runBlocking {
        for (body in listOf(bytes.copyOf(bytes.size - 1), ByteArray(bytes.size))) {
            val connection = Connection(body)
            val manager = ModelDownloadManager(temp.root) { connection }
            assertTrue(manager.download(model).isFailure)
            assertFalse(manager.targetFile(model).exists())
            assertFalse(temp.root.resolve(model.fileName + ".part").exists())
            assertTrue(connection.disconnected)
            assertTrue(manager.states.value[model.id] is ModelDownloadState.Failed)
        }
    }

    @Test fun corruptExistingFileIsReplaced() = runBlocking {
        val manager = ModelDownloadManager(temp.root) { Connection(bytes) }
        manager.targetFile(model).writeBytes(ByteArray(bytes.size))
        assertTrue(manager.download(model).isSuccess)
        assertArrayEquals(bytes, manager.targetFile(model).readBytes())
    }

    @Test fun failedReplacementPreservesExistingModelBytes() = runBlocking {
        val manager = ModelDownloadManager(temp.root) { Connection(byteArrayOf(1)) }
        val existing = "previous model version".toByteArray()
        manager.targetFile(model).writeBytes(existing)
        assertTrue(manager.download(model).isFailure)
        assertArrayEquals(existing, manager.targetFile(model).readBytes())
    }

    @Test fun concurrentDuplicateDoesNotOpenOrOverwriteFile() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val manager = ModelDownloadManager(temp.root) {
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            Connection(bytes)
        }
        val first = async(kotlinx.coroutines.Dispatchers.IO) { manager.download(model) }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        try {
            assertTrue(manager.download(model).isFailure)
            assertFalse(manager.delete(model))
        } finally { release.countDown() }
        assertTrue(first.await().isSuccess)
    }

    @Test fun cancellationCleansUpAndAllowsRetry() = runBlocking {
        val manager = ModelDownloadManager(temp.root) { throw CancellationException("cancelled") }
        try {
            manager.download(model)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(ModelDownloadState.Idle, manager.states.value[model.id])
        assertFalse(manager.targetFile(model).exists())
        assertFalse(temp.root.resolve(model.fileName + ".part").exists())
        assertTrue(manager.delete(model))
    }

    @Test fun insecureRedirectIsRejectedAndDisconnected() = runBlocking {
        val connection = object : Connection(bytes) {
            override fun getResponseCode() = 302
            override fun getHeaderField(name: String?) = "http://models.example/unsafe"
        }
        val manager = ModelDownloadManager(temp.root) { connection }
        assertTrue(manager.download(model).isFailure)
        assertTrue(connection.disconnected)
        assertFalse(manager.isDownloaded(model))
    }

    @Test fun resumesPartialAcrossManagerInstancesAndVerifiesWholeFile() = runBlocking {
        temp.root.resolve(model.fileName + ".part").writeBytes(bytes.take(5).toByteArray())
        val connection = object : Connection(bytes.drop(5).toByteArray()) {
            override fun getResponseCode() = 206
            override fun getHeaderField(name: String?): String? =
                if (name == "Content-Range") "bytes 5-${bytes.size - 1}/${bytes.size}" else null
        }
        val manager = ModelDownloadManager(temp.root) { connection }
        assertTrue(manager.download(model).isSuccess)
        assertEquals("bytes=5-", connection.getRequestProperty("Range"))
        assertArrayEquals(bytes, manager.targetFile(model).readBytes())
    }

    @Test fun ignoredRangeRestartsWithoutAppending() = runBlocking {
        temp.root.resolve(model.fileName + ".part").writeBytes(bytes.take(5).toByteArray())
        val manager = ModelDownloadManager(temp.root) { Connection(bytes) }
        assertTrue(manager.download(model).isSuccess)
        assertArrayEquals(bytes, manager.targetFile(model).readBytes())
    }

    @Test fun wrongRangeNeverPublishesModel() = runBlocking {
        temp.root.resolve(model.fileName + ".part").writeBytes(bytes.take(5).toByteArray())
        val manager = ModelDownloadManager(temp.root) { object : Connection(bytes.drop(5).toByteArray()) {
            override fun getResponseCode() = 206
            override fun getHeaderField(name: String?) = "bytes 4-${bytes.size - 1}/${bytes.size}"
        } }
        assertTrue(manager.download(model).isFailure)
        assertFalse(manager.isDownloaded(model))
    }
}
