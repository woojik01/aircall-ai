package com.woojik.aircallai.ai.local

/** Classify native errors without displaying prompt text, paths, keys or the raw exception. */
internal fun localFailureCode(error: Throwable): String {
    val messages = generateSequence(error) { it.cause }.take(6).map { it.message.orEmpty() }
        .joinToString(" ").lowercase()
    return when {
        "local_empty_response" in messages -> "LOCAL_EMPTY_RESPONSE"
        "allocate" in messages || "out of memory" in messages -> "LOCAL_TENSOR_ALLOCATION"
        "opencl" in messages || "gpu" in messages || "delegate" in messages -> "LOCAL_GPU_DRIVER"
        "template" in messages || "jinja" in messages -> "LOCAL_CHAT_TEMPLATE"
        "max" in messages && "token" in messages -> "LOCAL_CONTEXT_LIMIT"
        else -> "LOCAL_NATIVE_" + error.javaClass.simpleName.replace(Regex("[^A-Za-z0-9_]"), "").take(60)
    }
}
