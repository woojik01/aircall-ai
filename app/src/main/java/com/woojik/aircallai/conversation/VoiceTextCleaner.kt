package com.woojik.aircallai.conversation

/**
 * PRD-05: 음성으로 재생할 텍스트를 정리한다.
 * 문장 부호(., ! ? : ; - ' ")는 TTS 억양(의문/감탄/호흡)에 필수라서 그대로 유지하고
 * 이모지, 이모티콘, 마크다운 기호, URL만 제거한다.
 */
object VoiceResponseSanitizer {
    fun sanitize(text: String): String {
        val cleaned = text
            .replace(Regex("https?://\\S+"), " ")
            .replace(Regex("[;:][\\-o^]?[)(DPpOoSs3*]"), " ")
            .replace(Regex("[*_~#>\\[\\]{}|+^@$/<>=]"), " ")
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s.,!?…:;'()\\"-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val hasContent = cleaned.any { it.isLetterOrDigit() }
        return if (hasContent) cleaned else "네 말씀해 주세요"
    }
}
