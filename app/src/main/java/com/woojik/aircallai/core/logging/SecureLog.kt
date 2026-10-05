package com.woojik.aircallai.core.logging

import android.util.Log

/**
 * Logging facade that never prints credentials or raw user content.
 * Debug logs are only emitted when the build is debuggable.
 */
object SecureLog {
    @Volatile
    var debuggable: Boolean = false

    fun d(tag: String, message: String) {
        if (debuggable) Log.d(tag, mask(message))
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (debuggable) Log.e(tag, mask(message) + (throwable?.let { " [${it.javaClass.simpleName}]" } ?: ""))
    }

    /** Masks anything that looks like a key or token before it reaches logcat. */
    fun mask(message: String): String =
        message
            .replace(Regex("(?i)Bearer\\s+[^\\s\",]+"), "Bearer ***")
            .replace(Regex("(?i)((?:[a-z_]*(?:key|token|secret|password|credential))[\"]?\\s*[=:]\\s*)(?:\"[^\"]*\"|[^\\s&,}]+)"), "$1***")
}
