package com.woojik.aircallai.ai.local

import android.content.Context
import com.woojik.aircallai.core.storage.AppStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * 로컬 모델 다운로드 관리자.
 * 설정 갤러리에서 선택한 모델을 PRD-02 modelsDir에 내려받는다.
 *
 * - 진행률은 모델별(id 키) StateFlow로 노출 — 한 모델의 진행 상태가
 *   다른 모델 카드에 표시되지 않는다
 * - 같은 모델의 중복 다운로드는 뮤텍스로 차단한다
 * - 완전히 받은 뒤에만 .part 임시 파일을 최종 이름으로 바꾼다 (끊긴 파일 방지)
 * - HTTP 상태 코드별로 사람이 이해할 수 있는 오류 메시지를 제공한다 (404/401/403 구분)
 * - HuggingFace resolve URL은 Xet CDN으로 리다이렉트되므로 수동으로 따라간다
 */
class ModelDownloadManager(private val context: Context) {

    private val _states = MutableStateFlow<Map<String, ModelDownloadState>>(emptyMap())

    /** 모델 id → 다운로드 상태. 없는 키는 Idle로 간주한다. */
    val states: StateFlow<Map<String, ModelDownloadState>> = _states.asStateFlow()

    private val perModelMutex = Mutex()

    fun stateOf(model: LocalModelInfo): ModelDownloadState =
        _states.value[model.id] ?: ModelDownloadState.Idle

    fun isDownloading(model: LocalModelInfo): Boolean =
        stateOf(model) is ModelDownloadState.Downloading

    /** 모델 파일의 최종 위치 (다운로드 여부와 무관). */
    fun targetFile(model: LocalModelInfo): File =
        File(AppStorage.modelsDir(context), model.fileName)

    fun isDownloaded(model: LocalModelInfo): Boolean = targetFile(model).exists()

    suspend fun download(model: LocalModelInfo): Result<File> = withContext(Dispatchers.IO) {
        // 같은 모델의 중복 다운로드 차단. 다른 모델은 서로 독립적으로 진행된다.
        perModelMutex.withLock {
            if (isDownloading(model)) {
                return@withContext Result.failure(IOException("이미 다운로드 중입니다"))
            }
        }
        val target = targetFile(model)
        if (target.exists()) {
            updateState(model.id, ModelDownloadState.Completed(model.fileName))
            return@withContext Result.success(target)
        }
        val temp = File(target.parentFile, target.name + ".part")
        updateState(model.id, ModelDownloadState.Downloading(0, null))
        try {
            val connection = openFollowingRedirects(model.downloadUrl)
            val code = connection.responseCode
            if (code != HTTP_OK) {
                connection.disconnect()
                temp.delete()
                updateState(model.id, ModelDownloadState.Failed(httpErrorMessage(code)))
                return@withContext Result.failure(IOException("HTTP " + code))
            }
            val total = connection.contentLengthLong.takeIf { it > 0 }
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        updateState(
                            model.id,
                            ModelDownloadState.Downloading(downloaded, total),
                        )
                    }
                }
            }
            connection.disconnect()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            updateState(model.id, ModelDownloadState.Completed(model.fileName))
            Result.success(target)
        } catch (e: IOException) {
            temp.delete()
            updateState(
                model.id,
                ModelDownloadState.Failed("다운로드 실패: " + (e.message ?: e.javaClass.simpleName)),
            )
            Result.failure(e)
        } catch (e: Exception) {
            temp.delete()
            updateState(
                model.id,
                ModelDownloadState.Failed("다운로드 실패: " + (e.message ?: e.javaClass.simpleName)),
            )
            Result.failure(e)
        }
    }

    private fun updateState(id: String, state: ModelDownloadState) {
        _states.value = _states.value + (id to state)
    }

    /**
     * 리다이렉트(최대 5회)를 수동으로 따라간다. HuggingFace의 resolve URL은
     * 302로 Xet CDN(별도 호스트)을 가리키며, HttpURLConnection의 자동 리다이렉트는
     * 신뢰할 수 없어 직접 Location 헤더를 따른다.
     */
    private fun openFollowingRedirects(startUrl: String): HttpURLConnection {
        var url = startUrl
        var redirects = 0
        while (true) {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank() || redirects >= MAX_REDIRECTS) {
                    throw IOException("리다이렉트 실패 (HTTP $code)")
                }
                url = resolveAgainst(url, location)
                redirects++
                continue
            }
            return connection
        }
    }

    /** 상대 경로 Location을 기준 URL에 대해 절대 URL로 변환한다. */
    private fun resolveAgainst(baseUrl: String, location: String): String =
        if (location.startsWith("http")) {
            location
        } else {
            URI(baseUrl).resolve(location).toString()
        }

    /** HTTP 오류를 사용자가 원인을 알 수 있는 문구로 변환한다. */
    private fun httpErrorMessage(code: Int): String = when (code) {
        404 -> "다운로드 실패: 모델 파일을 찾을 수 없습니다 (HTTP 404)"
        401, 403 -> "다운로드 실패: 접근이 거부되었습니다 (라이선스 동의 필요, HTTP $code)"
        429 -> "다운로드 실패: 요청이 너무 많습니다. 잠시 후 다시 시도하세요 (HTTP 429)"
        in 500..599 -> "다운로드 실패: 서버 오류 (HTTP $code). 잠시 후 다시 시도하세요"
        else -> "다운로드 실패: HTTP $code"
    }

    fun delete(model: LocalModelInfo): Boolean = targetFile(model).delete()

    fun reset() {
        _states.value = emptyMap()
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val BUFFER_SIZE = 64 * 1024
        private const val HTTP_OK = 200
        private const val MAX_REDIRECTS = 5
        private const val USER_AGENT = "AirCallAI/0.1 (Android)"
    }
}
