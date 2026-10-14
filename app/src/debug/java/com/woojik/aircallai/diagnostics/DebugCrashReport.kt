package com.woojik.aircallai.diagnostics

/** Only structural error information. Never include exception messages, chat text or tokens. */
object DebugCrashReport {
    const val MAX_CHARS = 12_000

    /** Also interpret reports saved by older builds, without rewriting their original evidence. */
    fun exitInterpretation(report: String?): String? {
        val reason = report?.lineSequence()?.firstOrNull { it.startsWith("Android exit=") }
            ?.substringAfter("Android exit=")?.substringBefore(';') ?: return null
        return when (reason) {
            "LOW_MEMORY" -> "종료 분류: 시스템 메모리 부족으로 Android가 프로세스를 종료했습니다. " +
                "이 기록만으로 앱의 메모리 누수나 Java/네이티브 충돌을 판단할 수 없습니다. " +
                "PSS/RSS는 마지막 측정값이며, 0은 측정값이 없을 수 있다는 뜻입니다."
            "JAVA_CRASH", "NATIVE_CRASH" -> "종료 분류: 앱 충돌 ($reason)"
            "ANR" -> "종료 분류: 앱 응답 없음 (ANR)"
            "SIGNAL" -> "종료 분류: 신호에 의한 종료. 신호 기록만으로 충돌 원인을 단정할 수 없습니다."
            "INITIALIZATION_FAILURE" -> "종료 분류: 앱 초기화 실패"
            else -> null
        }
    }

    fun exception(failure: Throwable): String = buildString {
        var cause: Throwable? = failure
        val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
        repeat(6) {
            val current = cause ?: return@repeat
            if (!visited.add(current)) return@repeat
            append(current.javaClass.name).append('\n')
            current.stackTrace.take(24).forEach { frame ->
                append("  at ").append(frame.className).append('.').append(frame.methodName)
                    .append('(').append(frame.fileName?.substringAfterLast('/')?.substringAfterLast('\\') ?: "Unknown")
                    .append(':').append(frame.lineNumber).append(")\n")
            }
            cause = current.cause
        }
    }.take(MAX_CHARS)
}
