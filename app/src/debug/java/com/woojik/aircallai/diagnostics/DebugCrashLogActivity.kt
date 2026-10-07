package com.woojik.aircallai.diagnostics

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** A debug-only launcher screen independent of AppGraph, Compose and notification delivery. */
class DebugCrashLogActivity : Activity() {
    private lateinit var log: TextView
    private val main = Handler(Looper.getMainLooper())
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        log = TextView(this).apply { text = "종료 진단을 확인하는 중…"; setTextIsSelectable(true) }
        fun button(label: String, action: () -> Unit) {
            layout.addView(Button(this).apply { text = label; setOnClickListener { action() } })
        }
        button("메인 앱 열기 · 실행 기록 수집") {
            startActivity(Intent(this, com.woojik.aircallai.ui.MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
        }
        button("새로고침") { refresh() }
        button("알림 권한 허용") {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            else openSettings()
        }
        button("알림 설정") { openSettings() }
        button("알림 테스트") { CrashDiagnostics.testNotification(applicationContext, ::display) }
        button("저장된 로그 알림 다시 표시") { CrashDiagnostics.retryNotification(applicationContext, ::display) }
        button("로그 복사") {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("AirCall AI 종료 진단", log.text))
        }
        layout.addView(log)
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    override fun onResume() { super.onResume(); refresh() }
    private fun refresh() { CrashDiagnostics.loadSnapshot(applicationContext, ::display) }
    private fun display(value: String) { main.post { if (!isDestroyed) log.text = value } }
    private fun openSettings() {
        try { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) }
        catch (_: android.content.ActivityNotFoundException) { log.text = "시스템 설정에서 AirCall AI 개발용 앱의 알림을 허용해 주세요." }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }
}
