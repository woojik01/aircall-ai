package com.woojik.aircallai.diagnostics

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Native view independent of AppGraph and Compose, so it can open when the main screen crashes. */
class DebugCrashLogActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        val log = TextView(this).apply { text = "종료 로그를 읽는 중…"; setTextIsSelectable(true) }
        val copy = Button(this).apply {
            text = "로그 복사"; isEnabled = false
            setOnClickListener {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("AirCall AI 종료 로그", log.text))
            }
        }
        layout.addView(copy)
        layout.addView(log)
        setContentView(ScrollView(this).apply { addView(layout) })
        Thread {
            val report = CrashDiagnostics.readReport(applicationContext) ?: "저장된 종료 기록이 없습니다."
            Handler(Looper.getMainLooper()).post {
                if (!isDestroyed) { log.text = report; copy.isEnabled = true }
            }
        }.start()
    }
}
