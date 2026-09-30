package com.woojik.aircallai.core.security

/**
 * PRD-02: crypto abstraction so that JVM unit tests can substitute a fake/in-memory engine
 * while production uses the Android Keystore-backed implementation.
 */
interface CryptoEngine {
    /** Encrypts plaintext bytes. Returns ciphertext blob (IV included). */
    fun encrypt(plain: ByteArray): ByteArray

    /** Decrypts a blob produced by [encrypt]. Returns null if it cannot be decrypted. */
    fun decrypt(blob: ByteArray): ByteArray?
}
