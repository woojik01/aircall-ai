package com.woojik.aircallai.ui

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woojik.aircallai.BuildConfig
import com.woojik.aircallai.privacy.ContentReport
import com.woojik.aircallai.privacy.ContentReportClient
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ContentReportDialog(client: ContentReportClient, selectedResponse: String?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var reportId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var reason by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(if (selectedResponse == null) "bug" else "content") }
    var includeResponse by rememberSaveable { mutableStateOf(false) }
    var excerpt by rememberSaveable { mutableStateOf(selectedResponse.orEmpty().take(4000)) }
    var sending by remember { mutableStateOf(false) }
    var receipt by rememberSaveable { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var attemptedPayload by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(if (selectedResponse == null) "문의 및 신고" else "AI 응답 신고") },
        text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (receipt != null) {
                Text("개발자에게 신고가 접수되었습니다.")
                androidx.compose.foundation.text.selection.SelectionContainer { Text("접수 번호: $receipt") }
            } else {
                Text("작성한 내용, 앱 버전, Android 버전만 개발자의 신고 접수 서비스로 전송합니다. " +
                    "대화 전체와 API 키·로그인 토큰은 포함하지 않습니다. 개인정보는 지우고 보내 주세요.")
                if (!client.isConfigured) Text("신고 접수 주소가 아직 준비되지 않았습니다.", color = MaterialTheme.colorScheme.error)
                listOf("content" to "부적절한 AI 응답", "privacy" to "개인정보 문의·신고 삭제 요청", "bug" to "앱 문제").forEach { (value, label) ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(category == value, { category = value }, enabled = !sending)
                        Text(label, Modifier.weight(1f))
                    }
                }
                OutlinedTextField(reason, { reason = it.take(2000) }, Modifier.fillMaxWidth(),
                    label = { Text("신고 내용") }, minLines = 3, enabled = !sending)
                if (selectedResponse != null) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(includeResponse, { includeResponse = it }, enabled = !sending)
                        Text("선택한 응답을 함께 보내기", Modifier.weight(1f))
                    }
                    if (includeResponse) OutlinedTextField(excerpt, { excerpt = it.take(4000) },
                        Modifier.fillMaxWidth(), label = { Text("보낼 응답 내용 · 수정 가능") },
                        minLines = 3, maxLines = 8, enabled = !sending)
                    if (includeResponse && selectedResponse.length > 4000) Text("응답의 앞 4,000자만 포함됩니다.")
                }
                Text("신고 보관 기간: ${BuildConfig.AIRCALL_REPORT_RETENTION_DAYS}일. 삭제 요청에는 접수 번호를 적어 주세요.",
                    style = MaterialTheme.typography.bodySmall)
                status?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (sending) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        } },
        confirmButton = {
            if (receipt != null) TextButton(onClick = onDismiss) { Text("닫기") }
            else TextButton(enabled = client.isConfigured && !sending && reason.isNotBlank(), onClick = {
                scope.launch {
                    sending = true; status = null
                    try {
                        val fingerprint = "$category\u0000$reason\u0000${if (includeResponse) excerpt else ""}"
                        // Retry identical submissions with the same ID; edited reports get a new ID.
                        if (attemptedPayload != null && attemptedPayload != fingerprint) reportId = UUID.randomUUID().toString()
                        attemptedPayload = fingerprint
                        receipt = client.submit(ContentReport(id = reportId, category = category, reason = reason,
                            response = if (includeResponse) excerpt else "", appVersion = BuildConfig.VERSION_NAME,
                            androidApi = Build.VERSION.SDK_INT)).id
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { status = "접수를 확인하지 못했습니다. 같은 내용으로 다시 시도할 수 있습니다." }
                    finally { sending = false }
                }
            }) { Text(if (sending) "전송 중" else "동의하고 전송") }
        },
        dismissButton = { if (receipt == null) TextButton(onClick = onDismiss, enabled = !sending) { Text("취소") } },
    )
}
