package com.woojik.aircallai.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.woojik.aircallai.AppGraph
import com.woojik.aircallai.backup.*
import kotlinx.coroutines.*

@Composable
fun DataTransferScreen(graph: AppGraph, onBeforeTransfer: suspend () -> Unit, onImported: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var encryptedExport by remember { mutableStateOf(true) }
    var confirmPlain by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<BackupSnapshot?>(null) }
    var restorePreferences by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val secret = password.toCharArray()
            try {
                onBeforeTransfer()
                val snapshot = AppDataTransfer.capture(graph)
                withContext(Dispatchers.IO) {
                    val plain = snapshot.encode()
                    val bytes = if (encryptedExport) BackupCodec.encrypt(plain, secret) else plain
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes); it.flush() }
                        ?: error("output unavailable")
                }
                status = if (encryptedExport) "파일을 저장했습니다. 비밀번호는 파일과 따로 보관하세요."
                    else "JSON 파일을 저장했습니다. 대화·메모·기억할 정보가 암호화 없이 포함됩니다."
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { status = "저장하지 못했습니다. 저장 공간과 파일 위치를 확인해 주세요."
            } finally { secret.fill('\u0000'); password = ""; busy = false }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val secret = password.toCharArray()
            try {
                preview = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= BackupCodec.MAX_BYTES + 128)
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    } ?: error("input unavailable")
                    BackupSnapshot.decode(if (BackupCodec.isEncrypted(bytes)) BackupCodec.decrypt(bytes, secret) else bytes)
                }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { status = "파일을 열지 못했습니다. 비밀번호·파일 형식·파일 손상 여부를 확인해 주세요."
            } finally { secret.fill('\u0000'); password = ""; busy = false }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("대화·메모·기억할 정보와 일반 설정을 저장합니다. API 키·로그인 토큰·승인·모델 파일은 포함하지 않습니다.")
        OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), enabled = !busy,
            label = { Text("백업 비밀번호 (8자 이상)") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        Text("암호화 백업을 가져올 때는 먼저 해당 비밀번호를 입력하세요.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { encryptedExport = true; save.launch("aircall-backup.aircall") },
            enabled = !busy && password.length >= 8, modifier = Modifier.fillMaxWidth()) { Text("암호화 백업 저장") }
        OutlinedButton(onClick = { confirmPlain = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("JSON으로 내보내기") }
        OutlinedButton(onClick = { open.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("백업·JSON 가져오기") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        status?.let { Text(it) }
    }
    if (confirmPlain) AlertDialog(onDismissRequest = { confirmPlain = false }, title = { Text("일반 JSON으로 저장할까요?") },
        text = { Text("이 파일은 암호화되지 않으며 대화·메모·기억할 정보를 포함합니다. 파일을 가진 사람이 내용을 읽을 수 있습니다.") },
        confirmButton = { TextButton(onClick = { confirmPlain = false; encryptedExport = false; save.launch("aircall-data.json") }) { Text("JSON으로 저장") } },
        dismissButton = { TextButton(onClick = { confirmPlain = false }) { Text("취소") } })
    preview?.let { snapshot -> AlertDialog(onDismissRequest = { preview = null }, title = { Text("데이터 가져오기") },
        text = { Column {
            Text("채팅 ${snapshot.rooms.size}개 · 메모 ${snapshot.notes.size}개를 추가합니다. 기존 자료는 유지하고 동일한 자료는 건너뜁니다. 기억할 정보는 현재 저장된 내용이 없을 때만 복원합니다.")
            Row { Checkbox(checked = restorePreferences, onCheckedChange = { restorePreferences = it }); Text("일반 설정도 복원") }
        } },
        confirmButton = { TextButton(onClick = {
            preview = null; busy = true
            scope.launch {
                try {
                    onBeforeTransfer()
                    val count = AppDataTransfer.import(graph, snapshot, restorePreferences)
                    onImported()
                    status = "채팅 ${count}개와 메모를 가져왔습니다. 계정·API 키는 필요하면 다시 연결하세요."
                } catch (e: CancellationException) { throw e
                } catch (_: Exception) { status = "가져오기를 마치지 못했습니다. 일부 자료가 추가되었을 수 있습니다. 저장 공간을 확인한 뒤 같은 파일을 다시 가져오세요. 동일한 자료는 건너뜁니다."
                } finally { busy = false }
            }
        }) { Text("가져오기") } }, dismissButton = { TextButton(onClick = { preview = null }) { Text("취소") } }) }
}
