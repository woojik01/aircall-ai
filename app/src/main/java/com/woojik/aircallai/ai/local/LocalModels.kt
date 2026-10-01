package com.woojik.aircallai.ai.local

import com.woojik.aircallai.ai.provider.ChatMessage

/**
 * 로컬 모델 갤러리 카탈로그 (Edge AI Gallery 방식).
 * 사용자가 모델을 선택해 다운로드하고 적용한다. 모델 파일이 GB 단위이므로
 * 앱에 내장하지 않고 설정 시점에 내려받아 PRD-02 modelsDir에 보관한다.
 *
 * MediaPipe LLM Inference 호환 .task 모델만 등록한다 (1단계 백엔드).
 */
data class LocalModelInfo(
    val id: String,
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    /** 표시용 근사 크기 (바이트). 실제 다운로드는 Content-Length 기준. */
    val sizeBytes: Long,
    /** 실행에 필요한 최소 기기 RAM (MB). */
    val minRamMb: Int,
    val description: String,
)

object LocalModelRegistry {

    /** MediaPipe 사전 변환(.task) 모델 — LiteRT Community HuggingFace. */
    val models: List<LocalModelInfo> = listOf(
        LocalModelInfo(
            id = "gemma-3n-e2b",
            displayName = "Gemma 3n E2B",
            fileName = "gemma-3n-e2b.task",
            downloadUrl = "https://huggingface.co/litert-community/Gemma3n-E2B-it/resolve/main/Gemma3n-E2B-it.task",
            sizeBytes = 2_300_000_000L,
            minRamMb = 4096,
            description = "가장 가벼운 기본 모델. 중급 기기에 적합.",
        ),
        LocalModelInfo(
            id = "gemma-3n-e4b",
            displayName = "Gemma 3n E4B",
            fileName = "gemma-3n-e4b.task",
            downloadUrl = "https://huggingface.co/litert-community/Gemma3n-E4B-it/resolve/main/Gemma3n-E4B-it.task",
            sizeBytes = 4_200_000_000L,
            minRamMb = 8192,
            description = "더 높은 품질. 플래그십(8GB RAM 이상) 권장.",
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

/**
 * 대화 기록을 로컬 모델 프롬프트로 변환한다.
 * 로컬 소형 모델은 시스템 롤을 지원하지 않으므로 하나의 텍스트 프롬프트로 직렬화한다.
 */
fun buildLocalPrompt(history: List<ChatMessage>): String = buildString {
    append("You are AirCall AI, a helpful Korean voice assistant. Answer briefly.\n\n")
    history.forEach { message ->
        when (message.role) {
            ChatMessage.Role.SYSTEM -> append("지시: ").append(message.content).append('\n')
            ChatMessage.Role.USER -> append("사용자: ").append(message.content).append('\n')
            ChatMessage.Role.ASSISTANT -> append("AI: ").append(message.content).append('\n')
        }
    }
    append("AI: ")
}
