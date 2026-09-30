package com.woojik.aircallai.core.storage

/**
 * PRD-02 CredentialManager contract (verbatim from the PRD).
 * All implementations must keep values encrypted at rest and out of logs.
 */
interface CredentialManager {
    suspend fun save(service: String, credential: ByteArray)
    suspend fun load(service: String): ByteArray?
    suspend fun delete(service: String)
    suspend fun clearAll()
}
