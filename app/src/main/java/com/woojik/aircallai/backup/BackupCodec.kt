package com.woojik.aircallai.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Password-based authenticated encryption survives reinstall/device changes; never exports credentials. */
object BackupCodec {
    private val magic = "AIRCALLBACKUP1\n".toByteArray(Charsets.US_ASCII)
    const val MAX_BYTES = 8 * 1024 * 1024
    fun isEncrypted(bytes: ByteArray) = bytes.size >= magic.size && bytes.take(magic.size).toByteArray().contentEquals(magic)
    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        require(plain.size <= MAX_BYTES && password.size >= 8)
        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val nonce = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
        cipher.updateAAD(magic)
        return magic + salt + nonce + cipher.doFinal(plain)
    }
    fun decrypt(bytes: ByteArray, password: CharArray): ByteArray {
        require(isEncrypted(bytes) && bytes.size in (magic.size + 44)..(MAX_BYTES + 128))
        val saltStart = magic.size
        val salt = bytes.copyOfRange(saltStart, saltStart + 16)
        val nonce = bytes.copyOfRange(saltStart + 16, saltStart + 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
        cipher.updateAAD(magic)
        return cipher.doFinal(bytes.copyOfRange(saltStart + 28, bytes.size))
    }
    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, 210_000, 256)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            try { SecretKeySpec(bytes, "AES") } finally { bytes.fill(0) }
        } finally { spec.clearPassword() }
    }
}
