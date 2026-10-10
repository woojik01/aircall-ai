package com.woojik.aircallai.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.woojik.aircallai.tools.*

@Composable
fun ToolTaskCard(event: ToolExecutionEvent?, pending: ToolRequest?) {
    val context = LocalContext.current
    if (event == null && pending == null) return
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(if (pending != null) ToolLabels.action(pending.toolName, pending.action) + " · 내용 확인 및 승인 대기"
                else event!!.summary, style = MaterialTheme.typography.bodyMedium)
            if (pending == null) event?.resultUrl?.takeIf { safeGitHubResultUrl(it) }?.let { url ->
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        .onFailure { android.widget.Toast.makeText(context, "링크를 열 수 없습니다.", android.widget.Toast.LENGTH_SHORT).show() }
                }) { Text("GitHub에서 결과 열기") }
            }
        }
    }
}
