package com.woojik.aircallai.ai.local

/** Android용 LiteRT-LM 모델 카탈로그. 웹용 .task 파일과 호환되지 않는다. */
data class LocalModelInfo(
    val id: String,
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    /** 고정한 저장소 revision의 정확한 파일 크기. */
    val sizeBytes: Long,
    /** 실행에 필요한 최소 기기 RAM (MB). */
    val minRamMb: Int,
    val description: String,
    val sha256: String,
    val contextTokens: Int = 4096,
    val inputByteBudget: Int = 2800,
)

object LocalModelRegistry {

    /** LiteRT Community의 Android CPU/GPU 모델. revision과 SHA-256으로 다운로드를 검증한다. */
    // New IDs prevent a saved web-model selection from bypassing native load/apply validation.
    val models: List<LocalModelInfo> = listOf(
        LocalModelInfo(
            id = "gemma-4-e2b-cpu",
            displayName = "Gemma 4 E2B",
            fileName = "gemma-4-E2B-it.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1/gemma-4-E2B-it.litertlm",
            sizeBytes = 2_588_147_712L,
            minRamMb = 4096,
            description = "GPU 가속 및 CPU 전환을 지원하는 텍스트 모델. 속도와 실행 가능 여부는 기기 메모리에 따라 다릅니다.",
            sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        ),
        LocalModelInfo(
            id = "gemma-4-e4b-cpu",
            displayName = "Gemma 4 E4B",
            fileName = "gemma-4-E4B-it.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/2eee7ac325f20eb8c9ac1d0e972f7c84663062da/gemma-4-E4B-it.litertlm",
            sizeBytes = 3_659_530_240L,
            minRamMb = 8192,
            description = "더 큰 GPU 가속 및 CPU 전환을 지원하는 텍스트 모델. E2B보다 많은 저장 공간과 메모리가 필요합니다.",
            sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
            contextTokens = 8192,
            inputByteBudget = 6000,
        ),
    )

    fun byId(id: String?): LocalModelInfo? = models.firstOrNull { it.id == id }

    fun byFileName(fileName: String): LocalModelInfo? = models.firstOrNull { it.fileName == fileName }
}

/** 다운로드 진행 표시용 포맷 (예: 2.3 GB). */
fun formatSizeBytes(bytes: Long): String {
    val gb = bytes / 1_000_000_000.0
    return if (gb >= 1.0) String.format("%.1f GB", gb)

    else String.format("%.0f MB", bytes / 1_000_000.0)
}

/** 다운로드 진행률(0..100). 총 크기를 모르면 0을 반환한다 (불확정 값 표시 금지). */
fun downloadPercent(downloadedBytes: Long, totalBytes: Long?): Int {
    if (totalBytes == null || totalBytes <= 0L || downloadedBytes < 0L) return 0
    val percent = (downloadedBytes * 100 / totalBytes).toInt()
    return percent.coerceIn(0, 100)
}

/** 순수 다운로드 상태 머신 (UI/매니저 공유, JVM 테스트 대상). */
sealed interface ModelDownloadState {
    data object Idle : ModelDownloadState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long?) : ModelDownloadState
    data object Verifying : ModelDownloadState
    data class Failed(val message: String) : ModelDownloadState
    data class Completed(val fileName: String) : ModelDownloadState
}
