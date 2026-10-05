package com.woojik.aircallai.privacy

import java.net.URLEncoder

/** The system email app handles sending. No Gmail API permission or automatic delivery. */
data class ContentReportEmailDraft private constructor(
    val recipient: String,
    val subject: String,
    val body: String,
) {
    fun mailtoUri(): String = "mailto:${encode(recipient)}?subject=${encode(subject)}&body=${encode(body)}"
    fun copyText(): String = "받는 사람: $recipient\n제목: $subject\n\n$body"

    companion object {
        fun create(recipient: String, report: ContentReport): ContentReportEmailDraft {
            require(ContentReportClient.isSupportEmail(recipient))
            report.validate()
            val category = when (report.category) {
                "content" -> "부적절한 AI 응답"
                "privacy" -> "개인정보 문의·신고 삭제 요청"
                else -> "앱 문제"
            }
            val body = buildString {
                appendLine("신고 번호: ${report.id}")
                appendLine("유형: $category")
                appendLine("앱 버전: ${report.appVersion}")
                appendLine("Android API: ${report.androidApi}")
                appendLine()
                appendLine("[직접 작성한 신고 내용]")
                append(report.reason)
                if (report.response.isNotEmpty()) {
                    append("\n\n[사용자가 포함하기로 선택한 AI 응답]\n")
                    append(report.response)
                }
            }
            return ContentReportEmailDraft(recipient, "AirCall AI 신고 [${report.id}]", body)
        }

        private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}
