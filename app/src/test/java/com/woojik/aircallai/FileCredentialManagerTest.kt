package com.woojik.aircallai

import com.woojik.aircallai.core.security.CryptoEngine
import com.woojik.aircallai.core.storage.FileCredentialManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * JVM test of FileCredentialManager with a real AES/GCM engine (in-memory key,
 * standing in for the Android Keystore which is unavailable on the JVM).
 */
class FileCredentialManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class AesCryptoEngine : CryptoEngine {
        private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

        override fun encrypt(plain: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            return cipher.iv + cipher.doFinal(plain)
        }

        override fun decrypt(blob: ByteArray): ByteArray? {
            if (blob.size < 12) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob, 0, 12))
            return try { cipher.doFinal(blob, 12, blob.size - 12) } catch (t: Throwable) { null }
        }
    }

    private fun manager() = FileCredentialManager(tmp.newFolder("credentials"), AesCryptoEngine())

    @Test
    fun fakeApiKeyRoundTrips() = runTest {
        val cm = manager()
        val apiKey = "sk-fake-1234567890".toByteArray()
        cm.save("cloud_ai", apiKey)
        assertArrayEquals(apiKey, cm.load("cloud_ai"))
    }

    @Test
    fun storedFileContainsNoPlaintext() = runTest {
        val dir = tmp.newFolder("plain-check")
        val cm = FileCredentialManager(dir, AesCryptoEngine())
        val apiKey = "sk-PLAINTEXT-VISIBLE-987".toByteArray()
        cm.save("cloud_ai", apiKey)
        val rawText = String(dir.resolve("cloud_ai.bin").readBytes(), Charsets.ISO_8859_1)
        assertFalse("plaintext leaked into the stored file", rawText.contains("PLAINTEXT-VISIBLE"))
    }

    @Test
    fun credentialSurvivesNewManagerInstance() = runTest {
        val dir = tmp.newFolder("restart")
        val engine = AesCryptoEngine()
        val apiKey = "restart-key".toByteArray()
        FileCredentialManager(dir, engine).save("svc", apiKey)
        val reloaded = FileCredentialManager(dir, engine).load("svc")
        assertArrayEquals(apiKey, reloaded)
    }

    @Test
    fun deleteIsPermanent() = runTest {
        val dir = tmp.newFolder("delete")
        val cm = FileCredentialManager(dir, AesCryptoEngine())
        cm.save("svc", "v".toByteArray())
        cm.delete("svc")
        assertNull(cm.load("svc"))
        assertTrue(!dir.resolve("svc.bin").exists())
    }

    @Test
    fun clearAllWipesEverything() = runTest {
        val dir = tmp.newFolder("clear")
        val cm = FileCredentialManager(dir, AesCryptoEngine())
        cm.save("a", "1".toByteArray())
        cm.save("b", "2".toByteArray())
        cm.clearAll()
        assertNull(cm.load("a"))
        assertNull(cm.load("b"))
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test
    fun loadUnknownServiceReturnsNull() = runTest {
        assertNull(manager().load("nope"))
    }

    @Test
    fun invalidServiceNamesRejected() = runTest {
        val cm = manager()
        for (bad in listOf("", "../escape", "a/b", "svc name")) {
            var threw = false
            try { cm.save(bad, byteArrayOf(1)) } catch (e: IllegalArgumentException) { threw = true }
            assertTrue("expected rejection for: " + bad, threw)
        }
    }

    @Test
    fun servicesKeepIndependentCredentials() = runTest {
        val cm = manager()
        cm.save("github", "gh".toByteArray())
        cm.save("gmail", "gm".toByteArray())
        assertArrayEquals("gh".toByteArray(), cm.load("github"))
        assertArrayEquals("gm".toByteArray(), cm.load("gmail"))
    }

    @Test fun failedEncryptionPreservesPreviousCredential() = runTest {
        val dir = tmp.newFolder("failed-write")
        val real = AesCryptoEngine()
        FileCredentialManager(dir, real).save("cloud_ai", "saved-key".toByteArray())
        val before = dir.resolve("cloud_ai.bin").readBytes()
        val failing = object : CryptoEngine {
            override fun encrypt(plain: ByteArray): ByteArray = error("write interrupted")
            override fun decrypt(blob: ByteArray) = real.decrypt(blob)
        }
        try { FileCredentialManager(dir, failing).save("cloud_ai", "new-key".toByteArray()) }
        catch (_: IllegalStateException) { }
        assertArrayEquals(before, dir.resolve("cloud_ai.bin").readBytes())
        assertArrayEquals("saved-key".toByteArray(), FileCredentialManager(dir, real).load("cloud_ai"))
    }

    @Test fun unreadableCredentialIsNotReplacedByANewKey() = runTest {
        val dir = tmp.newFolder("wrong-key")
        FileCredentialManager(dir, AesCryptoEngine()).save("cloud_ai", "saved-key".toByteArray())
        val before = dir.resolve("cloud_ai.bin").readBytes()
        var rejected = false
        try { FileCredentialManager(dir, AesCryptoEngine()).save("cloud_ai", "replacement".toByteArray()) }
        catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected)
        assertArrayEquals(before, dir.resolve("cloud_ai.bin").readBytes())
    }
    @Test fun unavailableStorageDoesNotThrowDuringApplicationGraphConstruction() = runTest {
        val blocked = tmp.newFile("blocked-credentials")
        blocked.writeText("preserved")
        val manager = FileCredentialManager(blocked, AesCryptoEngine())
        assertNull(manager.load("cloud_ai"))
        try {
            manager.save("cloud_ai", "key".toByteArray())
            fail("Saving must report unavailable storage")
        } catch (_: IllegalStateException) { }
        assertEquals("preserved", blocked.readText())
    }

}
