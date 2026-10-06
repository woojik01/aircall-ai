package com.woojik.aircallai.ui

import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woojik.aircallai.privacy.PrivacyNotices
import com.woojik.aircallai.BuildConfig

/**
 * PRD-08 개인정보 화면: 모드별/Tool별 데이터 이동을 사용자가 명확히
 * 이해할 수 있게 표시한다. 문구는 PrivacyNotices 단일 소스를 사용한다.
 */
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
            listOf(PrivacyNotices.localMode, PrivacyNotices.cloudMode, PrivacyNotices.tools, PrivacyNotices.support).forEach { notice ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(notice.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            notice.detail,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            Text(
                "채팅방 기록은 기기에 암호화해 저장됩니다. 사이드 메뉴에서 방을 삭제하면 해당 기록이 삭제됩니다.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (BuildConfig.AIRCALL_DEVELOPER_NAME.isNotBlank()) Text("개발자: ${BuildConfig.AIRCALL_DEVELOPER_NAME}")
            if (BuildConfig.AIRCALL_SUPPORT_EMAIL.isNotBlank()) Text("문의: ${BuildConfig.AIRCALL_SUPPORT_EMAIL}")
            if (BuildConfig.AIRCALL_PRIVACY_POLICY_URL.isNotBlank()) TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.AIRCALL_PRIVACY_POLICY_URL))) }
                    .onFailure { status = "웹페이지를 열 수 없습니다." }
            }) { Text("공개 개인정보처리방침 보기") }
            SupportContactButton()
            Text("AirCall AI 자체 계정을 만들지 않습니다. 외부 계정 연결 해제는 설정의 도구 및 계정에서 할 수 있습니다. " +
                "연결 해제는 기기의 토큰을 지우며, 서비스의 계정이나 이미 보낸 메일·일정을 삭제하지 않습니다. " +
                "Google·GitHub의 계정 설정에서 앱의 접근 권한도 취소할 수 있습니다.")
            TextButton(onClick = { confirmClear = true }) { Text("이 기기의 앱 데이터 모두 삭제") }
            status?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false },
        title = { Text("앱 데이터를 모두 삭제할까요?") },
        text = { Text("채팅, 메모, API 키, 로그인 토큰, 설정, 다운로드한 모델을 이 기기에서 삭제하고 앱을 초기화합니다. " +
            "삭제 후 앱이 종료됩니다. 외부 서비스로 이미 보낸 데이터는 해당 서비스에 별도로 삭제를 요청해야 합니다.") },
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
