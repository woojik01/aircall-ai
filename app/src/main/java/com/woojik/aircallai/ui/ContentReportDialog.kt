package com.woojik.aircallai.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.woojik.aircallai.BuildConfig
import com.woojik.aircallai.privacy.ContentReport
import com.woojik.aircallai.privacy.ContentReportClient
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ContentReportDialog(client: ContentReportClient, selectedResponse: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reportId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var reason by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(if (selectedResponse == null) "bug" else "content") }
    var includeResponse by rememberSaveable { mutableStateOf(false) }
    var excerpt by rememberSaveable { mutableStateOf(selectedResponse.orEmpty().take(4000)) }
    var sending by remember { mutableStateOf(false) }
    var receipt by rememberSaveable { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var emailDraftOpened by rememberSaveable { mutableStateOf(false) }
    var emailUnavailable by remember { mutableStateOf(false) }
    var attemptedPayload by rememberSaveable { mutableStateOf<String?>(null) }

    fun currentReport(): ContentReport {
        val fingerprint = "$category\u0000$reason\u0000${if (includeResponse) excerpt else ""}"
        // Retry identical submissions with the same ID; edited reports get a new ID.
        if (attemptedPayload != null && attemptedPayload != fingerprint) reportId = UUID.randomUUID().toString()
        attemptedPayload = fingerprint
        return ContentReport(id = reportId, category = category, reason = reason,
            response = if (includeResponse) excerpt else "", appVersion = BuildConfig.VERSION_NAME,
            androidApi = Build.VERSION.SDK_INT)
    }
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(if (selectedResponse == null) "문의 및 신고" else "AI 응답 신고") },
        text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (receipt != null) {
                Text("개발자에게 신고가 접수되었습니다.")
                androidx.compose.foundation.text.selection.SelectionContainer { Text("접수 번호: $receipt") }
            } else {
                if (client.usesEmail) {
                    Text("받는 사람: ${client.supportEmail}")
                    Text("동의하면 이메일 앱에서 신고 초안을 엽니다. 내용을 확인하고 이메일 앱의 보내기를 눌러야 개발자에게 전송됩니다. " +
                        "초안을 여는 것만으로 전송되거나 접수되지 않습니다. 발송 시 발신 이메일 주소와 메일 정보도 개발자와 메일 서비스에 전달됩니다.")
                } else {
                    Text("동의하면 작성한 내용을 개발자의 HTTPS 신고 접수 서비스로 전송합니다.")
                }
                Text("신고 번호, 직접 작성한 내용, 앱 버전(${BuildConfig.VERSION_NAME}), Android API(${Build.VERSION.SDK_INT})를 포함합니다. " +
                    "대화 전체와 API 키·로그인 토큰은 자동 포함하지 않습니다. 개인정보는 지우고 보내 주세요.")
                if (!client.isConfigured) Text(if (client.usesEmail) "신고 문의 이메일이 아직 준비되지 않았습니다." else "신고 접수 주소가 아직 준비되지 않았습니다.",
                    color = MaterialTheme.colorScheme.error)
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
                    if (includeResponse) Text("최대 4,000자까지 포함하며 긴 응답은 앞부분만 표시됩니다.")
                }
                Text(if (client.usesEmail) "개발자는 접수한 신고 메일을 최대 ${BuildConfig.AIRCALL_REPORT_RETENTION_DAYS}일 보관하며 기한 안에 수동으로 영구 삭제합니다. " +
                    "앱이 메일을 자동 삭제하지 않습니다. 삭제 요청에는 신고 번호를 적어 주세요. 발신함·임시보관함은 사용하는 메일 서비스에서 직접 관리해 주세요."
                    else "신고 보관 기간: ${BuildConfig.AIRCALL_REPORT_RETENTION_DAYS}일. 삭제 요청에는 접수 번호를 적어 주세요.",
                    style = MaterialTheme.typography.bodySmall)
                if (emailDraftOpened) {
                    Text("이메일 초안을 열었습니다. 이 앱에서는 발송·접수 여부를 확인할 수 없습니다. 이메일 앱에서 발송 상태를 확인해 주세요.",
                        style = MaterialTheme.typography.bodySmall)
                    androidx.compose.foundation.text.selection.SelectionContainer { Text("신고 번호: $reportId") }
                }
                status?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (client.usesEmail && emailUnavailable) TextButton(enabled = reason.isNotBlank(), onClick = {
                    runCatching {
                        val draft = client.emailDraft(currentReport())
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("AirCall AI 신고 초안", draft.copyText()))
                    }.onSuccess { status = "신고 초안을 복사했습니다. 이메일에 붙여 넣고 직접 보내 주세요." }
                        .onFailure { status = "신고 초안을 복사하지 못했습니다. 화면의 내용을 직접 작성해 보내 주세요." }
                }) { Text("신고 초안 복사") }
                if (sending) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        } },
        confirmButton = {
            if (receipt != null) TextButton(onClick = onDismiss) { Text("닫기") }
            else TextButton(enabled = client.isConfigured && !sending && reason.isNotBlank(), onClick = {
                if (client.usesEmail) {
                    status = null
                    runCatching {
                        val draft = client.emailDraft(currentReport())
                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse(draft.mailtoUri())))
                    }.onSuccess {
                        emailDraftOpened = true; emailUnavailable = false
                    }.onFailure {
                        emailUnavailable = true
                        status = "이메일 앱을 열 수 없습니다. 신고 초안을 복사해 문의 이메일로 직접 보내 주세요."
                    }
                } else {
                    scope.launch {
                        sending = true; status = null
                        try {
                            receipt = client.submit(currentReport()).id
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { status = "접수를 확인하지 못했습니다. 같은 내용으로 다시 시도할 수 있습니다." }
                        finally { sending = false }
                    }
                }
            }) { Text(if (sending) "전송 중" else if (client.usesEmail) "동의하고 이메일 초안 열기" else "동의하고 전송") }
        },
        dismissButton = { if (receipt == null) TextButton(onClick = onDismiss, enabled = !sending) { Text(if (emailDraftOpened) "닫기" else "취소") } },
    )
}
