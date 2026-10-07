package com.woojik.aircallai.diagnostics

import android.app.Application
import android.content.Context

/** No handlers, files, notification channels or diagnostic screens in production. */
@Suppress("UNUSED_PARAMETER")
object CrashDiagnostics {
    fun install(application: Application) {}
    fun markStage(stage: String) {}
    fun recordStartupFailure(failure: Throwable) {}
    fun onNotificationsAvailable(context: Context) {}
}
