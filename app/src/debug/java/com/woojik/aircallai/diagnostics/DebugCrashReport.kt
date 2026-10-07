package com.woojik.aircallai.diagnostics

/** Only structural error information. Never include exception messages, chat text or tokens. */
object DebugCrashReport {
    const val MAX_CHARS = 12_000

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
