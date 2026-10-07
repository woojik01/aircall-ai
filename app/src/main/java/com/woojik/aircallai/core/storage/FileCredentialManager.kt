package com.woojik.aircallai.core.storage

import com.woojik.aircallai.core.security.CryptoEngine
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val lock = Mutex()

    override suspend fun save(service: String, credential: ByteArray) = withContext(Dispatchers.IO) { lock.withLock {
        val target = fileFor(service)
        check(dir.isDirectory || dir.mkdirs()) { "인증 정보를 저장할 공간을 사용할 수 없습니다." }
        val tmp = File(target.parentFile, target.name + ".tmp")
        // Finish encryption and sync before replacing the last saved credential.
        // Never fall back to truncating the live file if an atomic move fails.
        try {
            check(!target.exists() || crypto.decrypt(target.readBytes()) != null) {
                "기존 인증 정보를 읽지 못해 덮어쓰기를 중단했습니다."
            }
            val encrypted = crypto.encrypt(credential)
            FileOutputStream(tmp).use { it.write(encrypted); it.fd.sync() }
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally { tmp.delete() }
        Unit
    } }

    override suspend fun load(service: String): ByteArray? = withContext(Dispatchers.IO) { lock.withLock {
        val target = fileFor(service)
        if (!target.exists()) return@withLock null
        crypto.decrypt(target.readBytes())
    } }

    override suspend fun delete(service: String) = withContext(Dispatchers.IO) { lock.withLock {
        fileFor(service).delete()
        Unit
    } }

    override suspend fun clearAll() = withContext(Dispatchers.IO) { lock.withLock {
        dir.listFiles()?.forEach { it.delete() }
        Unit
    } }

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
