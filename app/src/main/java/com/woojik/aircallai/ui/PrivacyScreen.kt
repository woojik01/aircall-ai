package com.woojik.aircallai.ui

import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woojik.aircallai.BuildConfig

/** Privacy policy access and local data controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBeforeClearData: () -> Unit = {}) {
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsSection("문의 및 개인정보") {
                if (BuildConfig.AIRCALL_DEVELOPER_NAME.isNotBlank()) Text("개발자: ${BuildConfig.AIRCALL_DEVELOPER_NAME}")
                if (BuildConfig.AIRCALL_SUPPORT_EMAIL.isNotBlank()) Text("문의: ${BuildConfig.AIRCALL_SUPPORT_EMAIL}")
                if (BuildConfig.AIRCALL_PRIVACY_POLICY_URL.isNotBlank()) TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.AIRCALL_PRIVACY_POLICY_URL))) }
                        .onFailure { status = "웹페이지를 열 수 없습니다." }
                }) { Text("개인정보처리방침") }
                SupportContactButton()
            }
            SettingsSection("데이터 관리") {
                    OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text("이 기기의 앱 데이터 모두 삭제")
                    }
            }
            status?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false },
        title = { Text("앱 데이터를 모두 삭제할까요?") },
        text = { Text("이 기기의 채팅·메모·키·연결 정보·설정·모델을 모두 삭제합니다. 복구할 수 없으며 앱이 종료됩니다. 외부 서비스의 데이터는 삭제되지 않습니다.") },
        confirmButton = { TextButton(onClick = {
            onBeforeClearData()
            context.stopService(Intent(context, com.woojik.aircallai.service.ModelDownloadService::class.java))
            if (!context.getSystemService(ActivityManager::class.java).clearApplicationUserData()) {
                confirmClear = false; status = "데이터를 삭제하지 못했습니다. Android 앱 설정에서 저장공간을 삭제해 주세요."
            }
        }) { Text("모두 삭제") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("취소") } },
    )
}
