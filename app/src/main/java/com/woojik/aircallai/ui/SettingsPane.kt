package com.woojik.aircallai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woojik.aircallai.settings.SettingsRepository
import kotlinx.coroutines.launch

/**
 * PRD-04: 설정에서 AI Mode(Local/Cloud)를 고르면 다음 대화부터 적용된다.
 * API Key는 여기서 입력받아 PRD-02 CredentialManager(Keystore 암호화)에만 저장된다.
 * Cloud API 주소/모델명은 일반 설정에 저장되며 실제 호출은 앱이 직접 수행한다.
 * 로컬 기능 증분: Local 모델 관리(갤러리) 화면으로 이동한다.
 * PRD-08: 개인정보(데이터 흐름) 화면으로 이동한다.
 * PRD-06: GitHub 토큰(PAT) 저장/삭제, 승인된 WRITE 작업 목록 표시/해제.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SettingsRepository,
    onModeChanged: () -> Unit = {},
    onSaveApiKey: suspend (String) -> Unit,
    onDeleteApiKey: suspend () -> Unit,
    onOpenLocalModels: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onSaveGitHubToken: suspend (String) -> Unit = {},
    onDeleteGitHubToken: suspend () -> Unit = {},
    toolApprovals: List<String> = emptyList(),
    onRevokeToolApproval: suspend (String) -> Unit = {},
) {
    var mode by remember { mutableStateOf(settings.aiProviderMode()) }
    var apiKeyInput by remember { mutableStateOf("") }
    var gitHubTokenInput by remember { mutableStateOf("") }
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
                .verticalScroll(rememberScrollState())
                .padding(padding)
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

            Text("Local 모델", style = MaterialTheme.typography.titleMedium)
            Text(
                "AI 응답은 기기에서 생성합니다. 음성 인식·출력은 음성 서비스에 따라 네트워크가 필요할 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            OutlinedButton(
                onClick = onOpenLocalModels,
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("로컬 모델 관리 (다운로드/적용)") }

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

            // PRD-06: GitHub 토큰(PAT) 연결. 저장소 조회(READ)와 승인된 생성 작업(WRITE)에 사용된다.
            Text("GitHub 연동", style = MaterialTheme.typography.titleMedium)
            Text(
                "GitHub 토큰(PAT) 저장과 승인 계층이 준비되어 있습니다. 대화에서 Tool을 호출하는 기능은 아직 연결되지 않았습니다. 조회는 기본 허용, " +
                    "Issue/PR 생성은 사용 시 승인이 필요합니다.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            OutlinedTextField(
                value = gitHubTokenInput,
                onValueChange = { gitHubTokenInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                label = { Text("GitHub 토큰 (PAT)") },
                placeholder = { Text("ghp_... / github_pat_...") },
                singleLine = true,
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            onSaveGitHubToken(gitHubTokenInput)
                            gitHubTokenInput = ""
                            status = "GitHub 토큰이 안전하게 저장되었습니다 (기기 암호화)."
                        }
                    },
                    enabled = gitHubTokenInput.isNotBlank(),
                ) { Text("저장") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            onDeleteGitHubToken()
                            status = "GitHub 토큰이 삭제되었습니다."
                        }
                    },
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("삭제") }
            }
            Text(
                "토큰은 기기의 Android Keystore로 암호화되어 저장되며 로그에 노출되지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // PRD-06 승인 증분: 승인된 WRITE 작업은 재시작 후에도 유지되며 여기서 해제할 수 있다.
            Text("Tool 작업 승인", style = MaterialTheme.typography.titleMedium)
            if (toolApprovals.isEmpty()) {
                Text(
                    "승인된 WRITE 작업이 없습니다. Tool이 WRITE 작업을 요청하면 승인 다이얼로그가 표시됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                var approvals by remember(toolApprovals) { mutableStateOf(toolApprovals) }
                Text(
                    "다음 WRITE 작업이 승인되어 있습니다. 해제하면 다시 승인이 필요합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                approvals.forEach { key ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(key, modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    onRevokeToolApproval(key)
                                    approvals = approvals - key
                                    status = "승인이 해제되었습니다: " + key
                                }
                            },
                        ) { Text("해제") }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // PRD-08: 개인정보(데이터 흐름) 안내 화면으로 이동한다.
            Text("개인정보", style = MaterialTheme.typography.titleMedium)
            Text(
                "모드별로 어떤 데이터가 어디로 전송되는지 확인할 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            OutlinedButton(
                onClick = onOpenPrivacy,
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("개인정보 · 데이터 흐름") }

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
