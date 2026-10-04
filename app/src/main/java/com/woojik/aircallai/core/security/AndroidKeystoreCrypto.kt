package com.woojik.aircallai.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256/GCM encryption with the key held inside AndroidKeyStore.
 * The key never leaves the Keystore and is never written to a file (PRD-02).
 */
class AndroidKeystoreCrypto(
    private val alias: String = DEFAULT_ALIAS,
) : CryptoEngine {

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain)
        return iv + encrypted
    }

    override fun decrypt(blob: ByteArray): ByteArray? {
        if (blob.size < IV_SIZE) return null
        val iv = blob.copyOfRange(0, IV_SIZE)
        val encrypted = blob.copyOfRange(IV_SIZE, blob.size)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            // Reading must not replace a missing key with an unrelated new key.
            val existing = existingKey() ?: return null
            cipher.init(Cipher.DECRYPT_MODE, existing, GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(encrypted)
        } catch (t: Throwable) {
            // Never log the value or the throwable message: may contain key material hints.
            null
        }
    }

    @Synchronized
    private fun key(): SecretKey {
        existingKey()?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun existingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        return (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    companion object {
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_BITS = 128
        const val DEFAULT_ALIAS = "aircall_master_key"
    }
}
