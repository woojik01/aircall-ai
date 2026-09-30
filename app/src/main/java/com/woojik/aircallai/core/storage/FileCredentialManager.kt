package com.woojik.aircallai.core.storage

import com.woojik.aircallai.core.security.CryptoEngine
import java.io.File

/**
 * File-backed CredentialManager for app-private storage.
 *
 * - One encrypted blob per service: <dir>/<service>.bin
 * - Only ciphertext ever touches the filesystem (PRD-02).
 * - Service names are validated to prevent path traversal.
 */
class FileCredentialManager(
    private val dir: File,
    private val crypto: CryptoEngine,
) : CredentialManager {

    init {
        require(dir.isDirectory || dir.mkdirs()) { "credential dir unavailable" }
    }

    override suspend fun save(service: String, credential: ByteArray) {
        val target = fileFor(service)
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(crypto.encrypt(credential))
        if (!tmp.renameTo(target)) {
            tmp.delete()
            target.writeBytes(crypto.encrypt(credential))
        }
    }

    override suspend fun load(service: String): ByteArray? {
        val target = fileFor(service)
        if (!target.exists()) return null
        return crypto.decrypt(target.readBytes())
    }

    override suspend fun delete(service: String) {
        fileFor(service).delete()
    }

    override suspend fun clearAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun fileFor(service: String): File {
        require(service.isNotBlank()) { "service name must not be blank" }
        require(SERVICE_PATTERN.matches(service)) { "invalid service name" }
        return File(dir, "$service.bin")
    }

    companion object {
        /** Alphanumeric + underscore/dash/dot only: no separators, no traversal. */
        private val SERVICE_PATTERN = Regex("^[A-Za-z0-9._-]{1,64}$")
    }
}
