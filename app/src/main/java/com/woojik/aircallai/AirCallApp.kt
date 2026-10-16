package com.woojik.aircallai

import android.app.Application
import android.content.ComponentCallbacks2
import com.woojik.aircallai.diagnostics.CrashDiagnostics
import com.woojik.aircallai.core.logging.SecureLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Application entry point.
 * PRD-05: UI(Activity)와 Foreground Service가 공유하는 AppGraph의 구성 루트.
 * 산발적 전역 Singleton이 아니라 App 수명주기를 따르는 단일 구성 루트다.
 */
class AirCallApp : Application() {
    private val graphDelegate = lazy { AppGraph(this) }
    val graph: AppGraph by graphDelegate

    override fun onCreate() {
        super.onCreate()
        // The release source set supplies an empty implementation.
        CrashDiagnostics.install(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Android 14+ still delivers these two callbacks. Do not depend on legacy levels.
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) releaseCachedModel()
    }

    private fun releaseCachedModel() {
        // Opening the independent diagnostics screen must not initialize the app graph.
        if (!graphDelegate.isInitialized()) return
        val initialized = graphDelegate.value
        if (initialized.sessionController.isRunning) return
        initialized.appScope.launch {
            try {
                initialized.localModelAdapter.releaseCachedModel {
                    !initialized.sessionController.isRunning
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                SecureLog.d("LocalMemory", "model cleanup failed: " + failure.javaClass.simpleName)
            }
        }
    }
}
