package com.woojik.aircallai.ai.local

import android.content.Context
import com.woojik.aircallai.core.storage.AppStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 로컬 모델 다운로드 관리자.
 * 설정 갤러리에서 선택한 모델을 PRD-02 modelsDir에 내려받는다.
 *
 * - 진행률은 StateFlow로 노출 (UI가 폴링하지 않는다)
 * - 완전히 받은 뒤에만 .part 임시 파일을 최종 이름으로 바꾼다 (끊긴 파일 방지)
 * - 실패 시 임시 파일을 정리하고 Failed 상태로 전이한다
 */
class ModelDownloadManager(private val context: Context) {

    private val _state = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    val state: StateFlow<ModelDownloadState> = _state.asStateFlow()

    /** 모델 파일의 최종 위치 (다운로드 여부와 무관). */
    fun targetFile(model: LocalModelInfo): File =
        File(AppStorage.modelsDir(context), model.fileName)

    fun isDownloaded(model: LocalModelInfo): Boolean = targetFile(model).exists()

    suspend fun download(model: LocalModelInfo): Result<File> = withContext(Dispatchers.IO) {
        val target = targetFile(model)
        if (target.exists()) {
            _state.value = ModelDownloadState.Completed(model.fileName)
            return@withContext Result.success(target)
        }
        val temp = File(target.parentFile, target.name + ".part")
        try {
            val connection = URL(model.downloadUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.connect()
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
                        _state.value = ModelDownloadState.Downloading(downloaded, total)
                    }
                }
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            _state.value = ModelDownloadState.Completed(model.fileName)
            Result.success(target)
        } catch (e: IOException) {
            temp.delete()
            _state.value = ModelDownloadState.Failed("다운로드 실패: 네트워크를 확인하세요")
            Result.failure(e)
        } catch (e: Exception) {
            temp.delete()
            _state.value = ModelDownloadState.Failed("다운로드 실패: " + (e.message ?: "알 수 없는 오류"))
            Result.failure(e)
        }
    }

    fun delete(model: LocalModelInfo): Boolean = targetFile(model).delete()

    fun reset() {
        _state.value = ModelDownloadState.Idle
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val BUFFER_SIZE = 64 * 1024
    }
}
