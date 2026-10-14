package com.woojik.aircallai.tools

import com.woojik.aircallai.core.security.CryptoEngine
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** App-wide memories. Reads and atomic encrypted mutations share one lock. */
class FileMemoryStore(private val file: File, private val crypto: CryptoEngine) {
    private val mutex = Mutex()

    suspend fun set(name: String, value: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val entries = read()
            require(entries.has(name) || entries.length() < 500) { "기억 개수 제한에 도달했습니다" }
            entries.put(name, value)
            write(entries)
        }
    }

    suspend fun get(name: String): String? = withContext(Dispatchers.IO) {
        mutex.withLock { read().let { if (it.has(name)) it.getString(name) else null } }
    }

    suspend fun list(query: String, limit: Int): List<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val entries = read()
            entries.keys().asSequence().toList().sorted()
                .filter { it.contains(query, ignoreCase = true) || entries.getString(it).contains(query, ignoreCase = true) }
                .take(limit.coerceIn(1, 50)).map { "$it=${entries.getString(it)}" }
        }
    }

    suspend fun delete(name: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val entries = read()
            if (!entries.has(name)) return@withLock false
            entries.remove(name)
            write(entries)
            true
        }
    }

    private fun read(): JSONObject {
        if (!file.exists()) return JSONObject()
        require(file.length() <= MAX_BYTES) { "기억 파일 크기 제한 초과" }
        val bytes = file.inputStream().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_BYTES) { "기억 파일 크기 제한 초과" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        require(bytes.size <= MAX_BYTES) { "기억 파일 크기 제한 초과" }
        val plain = crypto.decrypt(bytes) ?: error("기억 복호화 실패: 기존 파일은 보존됩니다")
        return JSONObject(plain.toString(Charsets.UTF_8))
    }

    private fun write(entries: JSONObject) {
        val encrypted = crypto.encrypt(entries.toString().toByteArray(Charsets.UTF_8))
        require(encrypted.size <= MAX_BYTES) { "기억 파일 크기 제한 초과" }
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        try {
            java.io.FileOutputStream(temp).use { it.write(encrypted); it.fd.sync() }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally { temp.delete() }
    }

    companion object { private const val MAX_BYTES = 4 * 1024 * 1024 }
}

/** Only an explicit memory tool call mutates storage; ordinary prose/code is never parsed. */
class MemoryTool(private val store: FileMemoryStore) : Tool {
    override val name = "memory"
    override val description = "변수이름=변수값 형태의 기억을 기기에 암호화 저장·갱신·조회·삭제"

    override fun riskFor(action: String) = when (action) {
        "get", "list" -> ToolRisk.READ
        "delete" -> ToolRisk.DESTRUCTIVE
        else -> ToolRisk.WRITE
    }

    override suspend fun execute(request: ToolRequest): ToolResult {
        return when (request.action) {
            "set" -> {
                val assignment = request.arguments["assignment"].orEmpty()
                val separator = assignment.indexOf('=')
                if (separator < 1) return ToolResult(false, "assignment는 변수이름=변수값 형식이어야 합니다")
                val key = assignment.substring(0, separator).trim()
                val value = assignment.substring(separator + 1).trim()
                if (!validName(key)) return ToolResult(false, "변수 이름은 줄바꿈과 = 없이 1~80자여야 합니다")
                if (value.isBlank() || value.length > 4000) return ToolResult(false, "변수 값은 1~4000자여야 합니다")
                store.set(key, value)
                ToolResult(true, "기억을 저장했습니다: $key=$value")
            }
            "get", "delete" -> {
                val key = request.arguments["name"]?.trim().orEmpty()
                if (!validName(key)) return ToolResult(false, "올바른 변수 이름(name)이 필요합니다")
                if (request.action == "get") {
                    val value = store.get(key)
                    ToolResult(true, if (value == null) "저장된 기억이 없습니다: $key" else "$key=$value")
                } else {
                    val deleted = store.delete(key)
                    ToolResult(true, if (deleted) "기억을 삭제했습니다: $key" else "저장된 기억이 없습니다: $key")
                }
            }
            "list" -> {
                val entries = store.list(request.arguments["query"]?.trim().orEmpty(), request.arguments["limit"]?.toIntOrNull() ?: 20)
                ToolResult(true, if (entries.isEmpty()) "일치하는 기억이 없습니다" else entries.joinToString("\n"))
            }
            else -> ToolResult(false, "지원하지 않는 기억 작업입니다")
        }
    }

    private fun validName(name: String) = name.isNotBlank() && name.length <= 80 &&
        name.none { it == '=' || it.isISOControl() }
}
