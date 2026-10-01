package com.woojik.aircallai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.woojik.aircallai.settings.SettingsRepository
import kotlinx.coroutines.launch

/**
 * PRD-04: 설정에서 AI Mode(Local/Cloud)를 고르면 다음 대화부터 적용된다.
 * API Key는 여기서 입력받아 PRD-02 CredentialManager(Keystore 암호화)에만 저장된다.
 * Cloud API 주소/모델명은 일반 설정에 저장되며 실제 호출은 앱이 직접 수행한다.
 * PRD-06: GitHub PAT는 CredentialManager에만 저장하고, WRITE Tool 작업은 작업별 승인 스위치로 허용한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SettingsRepository,
    onModeChanged: () -> Unit = {},
    onSaveApiKey: suspend (String) -> Unit,
    onDeleteApiKey: suspend () -> Unit,
    onSaveGitHubToken: suspend (String) -> Unit = {},
    onDeleteGitHubToken: suspend () -> Unit = {},
    toolApprovals: List<String> = emptyList(),
    isToolApproved: (String) -> Boolean = { false },
    onToolApprovalChanged: suspend (String, Boolean) -> Unit = { _, _ -> },
) {
    var githubTokenInput by remember { mutableStateOf("") }
    val approvals = remember { mutableStateMapOf<String, Boolean>().apply { toolApprovals.forEach { put(it, isToolApproved(it)) } } }
    var mode by remember { mutableStateOf(settings.aiProviderMode()) }
    var apiKeyInput by remember { mutableStateOf("") }
    var baseUrlInput by remember { mutableStateOf(settings.cloudBaseUrl()) }
    var modelInput by remember { mutableStateOf(settings.cloudModel()) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("설정") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("AI Mode", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = mode == SettingsRepository.MODE_LOCAL,
                    onClick = {
                        settings.setAiProviderMode(SettingsRepository.MODE_LOCAL)
                        mode = SettingsRepository.MODE_LOCAL
                        // PRD-05 hotfix: 모드 변경 즉시 라우터가 다시 적용되도록 알린다.
                        onModeChanged()
                    },
                )
                Text("Local")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = mode == SettingsRepository.MODE_CLOUD,
                    onClick = {
                        settings.setAiProviderMode(SettingsRepository.MODE_CLOUD)
                        mode = SettingsRepository.MODE_CLOUD
                        onModeChanged()
                    },
                )
                Text("Cloud")
            }
            Text(
                "모드 변경은 다음 대화부터 적용됩니다.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text("Cloud API Key", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("sk-...") },
                singleLine = true,
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            onSaveApiKey(apiKeyInput)
                            apiKeyInput = ""
                            status = "Key가 안전하게 저장되었습니다 (기기 암호화)."
                        }
                    },
                    enabled = apiKeyInput.isNotBlank(),
                ) { Text("저장") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            onDeleteApiKey()
                            status = "Key가 삭제되었습니다."
                        }
                    },
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("삭제") }
            }
            Text(
                "Key는 기기의 Android Keystore로 암호화되어 저장되며 서버로 전송되지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text("Cloud API 연결", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = baseUrlInput,
                onValueChange = { baseUrlInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                label = { Text("API 주소") },
                placeholder = { Text("https://api.groq.com/openai/v1/chat/completions") },
                singleLine = true,
            )
            OutlinedTextField(
                value = modelInput,
                onValueChange = { modelInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                label = { Text("모델명") },
                placeholder = { Text(SettingsRepository.DEFAULT_CLOUD_MODEL) },
                singleLine = true,
            )
            Button(
                onClick = {
                    settings.setCloudBaseUrl(baseUrlInput)
                    settings.setCloudModel(modelInput)
                    status = "Cloud API 연결 정보가 저장되었습니다."
                },
                modifier = Modifier.padding(top = 8.dp),
                enabled = baseUrlInput.isNotBlank() && modelInput.isNotBlank(),
            ) { Text("연결 저장") }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text("GitHub 연결", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = githubTokenInput,
                onValueChange = { githubTokenInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                label = { Text("Personal Access Token") },
                placeholder = { Text("github_pat_...") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            onSaveGitHubToken(githubTokenInput)
                            githubTokenInput = ""
                            status = "GitHub 토큰이 안전하게 저장되었습니다 (기기 암호화)."
                        }
                    },
                    enabled = githubTokenInput.isNotBlank(),
                ) { Text("토큰 저장") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            onDeleteGitHubToken()
                            status = "GitHub 토큰이 삭제되었습니다."
                        }
                    },
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("토큰 삭제") }
            }

            if (toolApprovals.isNotEmpty()) {
                Text(
                    "도구 작업 승인",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    "저장소 조회는 항상 허용됩니다. 아래 작업은 켠 경우에만 AI가 실행할 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                )
                toolApprovals.forEach { action ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        Text(actionLabel(action), modifier = Modifier.weight(1f))
                        Switch(
                            checked = approvals[action] == true,
                            onCheckedChange = { checked ->
                                approvals[action] = checked
                                scope.launch { onToolApprovalChanged(action, checked) }
                            },
                        )
                    }
                }
            }

            status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

private fun actionLabel(action: String) = when (action) {
    "create_issue" -> "GitHub Issue 생성 허용"
    "create_pull_request" -> "GitHub Pull Request 생성 허용"
    else -> action
}
