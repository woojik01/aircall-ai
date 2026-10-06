package com.woojik.aircallai.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.woojik.aircallai.BuildConfig

@Composable
fun SupportContactButton() {
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    TextButton(onClick = {
        failed = runCatching {
            context.startActivity(Intent(Intent.ACTION_SENDTO,
                Uri.parse("mailto:${BuildConfig.AIRCALL_SUPPORT_EMAIL}")))
        }.isFailure
    }) { Text("서비스 문의 이메일 보내기") }
    if (failed) Text("메일 앱을 열 수 없습니다. ${BuildConfig.AIRCALL_SUPPORT_EMAIL}으로 직접 문의해 주세요.",
        color = MaterialTheme.colorScheme.error)
}
