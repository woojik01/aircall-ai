package com.woojik.aircallai.ai.local

import android.content.Context
import com.woojik.aircallai.core.storage.AppStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

/** Only a complete, verified file is published under the final model filename. */
class ModelDownloadManager(
    private val modelsDir: File,
    private val connect: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection },
) {
    constructor(context: Context) : this(AppStorage.modelsDir(context))

    private val _states = MutableStateFlow<Map<String, ModelDownloadState>>(emptyMap())
    val states: StateFlow<Map<String, ModelDownloadState>> = _states.asStateFlow()
    private val active = mutableSetOf<String>()
    private val connections = java.util.concurrent.ConcurrentHashMap<String, HttpURLConnection>()

    /** Call on IO after cancelling the job to interrupt a blocking socket read. */
    fun interrupt(id: String) { connections[id]?.disconnect() }

    fun targetFile(model: LocalModelInfo): File = File(modelsDir, model.fileName)
    fun isDownloaded(model: LocalModelInfo): Boolean = isInstalledModel(targetFile(model), model)

    suspend fun download(model: LocalModelInfo): Result<File> = withContext(Dispatchers.IO) {
        synchronized(active) {
            if (!active.add(model.id)) {
                return@withContext Result.failure(IOException("이미 다운로드 중입니다"))
            }
        }
        val target = targetFile(model)
        val temp = File(modelsDir, model.fileName + ".part")
        var connection: HttpURLConnection? = null
        try {
            modelsDir.mkdirs()
            // Verify existing files too, so an interrupted/legacy installation can be repaired.
            if (isDownloaded(model) && verifyModelFile(target, model)) {
                updateState(model.id, ModelDownloadState.Completed(model.fileName))
                return@withContext Result.success(target)
            }
            updateState(model.id, ModelDownloadState.Downloading(0, model.sizeBytes))
            connection = openFollowingRedirects(model.downloadUrl)
            connections[model.id] = connection
            currentCoroutineContext().ensureActive()
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException(httpErrorMessage(code))
            val length = connection.contentLengthLong
            if (length > 0 && length != model.sizeBytes) throw IOException("모델 파일 크기가 일치하지 않습니다")
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            var lastProgress = 0L
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        downloaded += count
                        if (downloaded > model.sizeBytes) throw IOException("모델 파일 크기가 일치하지 않습니다")
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        val now = System.nanoTime()
                        if (now - lastProgress > 500_000_000L || downloaded == model.sizeBytes) {
                            updateState(model.id, ModelDownloadState.Downloading(downloaded, model.sizeBytes))
                            lastProgress = now
                        }
                    }
                }
            }
            updateState(model.id, ModelDownloadState.Verifying)
            if (downloaded != model.sizeBytes || digest.digest().hex() != model.sha256) {
                throw IOException("모델 파일 검증에 실패했습니다. 다시 다운로드해 주세요")
            }
            currentCoroutineContext().ensureActive()
            // Same-directory rename is atomic; copying to the final path exposes incomplete data.
            java.nio.file.Files.move(temp.toPath(), target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            updateState(model.id, ModelDownloadState.Completed(model.fileName))
            Result.success(target)
        } catch (e: CancellationException) {
            updateState(model.id, ModelDownloadState.Idle)
            throw e
        } catch (e: Exception) {
            if (!currentCoroutineContext()[kotlinx.coroutines.Job]!!.isActive) {
                updateState(model.id, ModelDownloadState.Idle)
                currentCoroutineContext().ensureActive()
            }
            // Do not expose response bodies, signed CDN URLs, or server exception messages.
            val message = if (e is IOException && e.message?.startsWith("모델") == true) {
                e.message!!
            } else {
                "다운로드 실패. 네트워크와 저장 공간을 확인하고 다시 시도해 주세요."
            }
            updateState(model.id, ModelDownloadState.Failed(message))
            Result.failure(e)
        } finally {
            connections.remove(model.id)
            connection?.disconnect()
            temp.delete()
            synchronized(active) { active.remove(model.id) }
        }
    }

    private fun updateState(id: String, state: ModelDownloadState) {
        _states.update { it + (id to state) }
    }

    private fun openFollowingRedirects(startUrl: String): HttpURLConnection {
        var url = URI(startUrl)
        repeat(6) { redirect ->
            if (url.scheme != "https") throw IOException("모델 다운로드에는 HTTPS가 필요합니다")
            val connection = connect(url.toString())
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "AirCallAI/0.2 (Android)")
                val code = connection.responseCode
                if (code !in 300..399) return connection
                val location = connection.getHeaderField("Location")
                if (location.isNullOrBlank() || redirect == 5) throw IOException("모델 다운로드 리다이렉트 실패")
                url = url.resolve(location)
            } catch (e: Exception) {
                connection.disconnect()
                throw e
            }
            connection.disconnect()
        }
        throw IOException("모델 다운로드 리다이렉트 실패")
    }

    private fun httpErrorMessage(code: Int): String = when (code) {
        404 -> "모델 파일을 찾을 수 없습니다 (HTTP 404)"
        401, 403 -> "모델 다운로드 접근이 거부되었습니다 (HTTP $code)"
        429 -> "모델 다운로드 요청이 너무 많습니다. 잠시 후 다시 시도하세요 (HTTP 429)"
        else -> "모델 다운로드 서버 오류 (HTTP $code)"
    }

    /** Only the two retired catalog filenames (and their partial downloads) are eligible. */
    private fun legacyFiles(): List<File> = listOf("e2b", "e4b").flatMap { size ->
        val name = "gemma-4-$size-it-web.task"
        listOf(File(modelsDir, name), File(modelsDir, "$name.part"))
    }

    fun hasLegacyFiles(): Boolean = legacyFiles().any { it.isFile }

    fun deleteLegacyFiles(): Boolean = legacyFiles().map { !it.exists() || it.delete() }.all { it }

    fun delete(model: LocalModelInfo): Boolean = synchronized(active) {
        if (model.id in active) return@synchronized false
        val file = targetFile(model)
        val deleted = !file.exists() || file.delete()
        if (deleted) updateState(model.id, ModelDownloadState.Idle)
        deleted
    }
}

internal fun isInstalledModel(file: File, model: LocalModelInfo): Boolean =
    file.isFile && file.length() == model.sizeBytes

internal suspend fun verifyModelFile(file: File, model: LocalModelInfo): Boolean {
    if (!isInstalledModel(file, model)) return false
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().hex() == model.sha256
}

private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

