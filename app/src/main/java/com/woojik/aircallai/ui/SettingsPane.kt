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
 * PRD-06: GitHub/Gmail 토큰 저장/삭제, 캘린더 권한 요청, 승인된 WRITE 작업 목록 표시/해제.
 * PRD-09 소셜 로그인 UX: "GitHub로 로그인"/"Google로 로그인" 버튼이 기본이며
 * Client ID는 빌드 시점 기본값을 사용한다. Client ID/수동 토큰 입력은 고급으로 유지한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SettingsRepository,
    onModeChanged: () -> Unit = {},
    onSaveApiKey: suspend (String) -> Unit,
    onDeleteApiKey: suspend () -> Unit = {},
    onOpenLocalModels: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onSaveGitHubToken: suspend (String) -> Unit = {},
    onDeleteGitHubToken: suspend () -> Unit = {},
    calendarPermissionGranted: Boolean = true,
    onRequestCalendarPermission: () -> Unit = {},
    onSaveGmailToken: suspend (String) -> Unit = {},
    onDeleteGmailToken: suspend () -> Unit = {},
    githubConnectionStatus: String? = null,
    gmailConnectionStatus: String? = null,
    onConnectGitHub: suspend ((String) -> Unit) -> String = { "" },
    onConnectGoogle: () -> String = { "" },
    onDisconnectGitHub: () -> Unit = {},
    onDisconnectGmail: () -> Unit = {},
    toolApprovals: List<String> = emptyList(),
    onRevokeToolApproval: suspend (String) -> Unit = {},
) {
    var mode by remember { mutableStateOf(settings.aiProviderMode()) }
    var apiKeyInput by remember { mutableStateOf(
"") }
    var gitHubTokenInput by remember { mutableStateOf("") }
    var gmailTokenInput by remember { mutableStateOf("") }
    var gitHubClientIdInput by remember { mutableStateOf(settings.githubOAuthClientId()) }
    var googleClientIdInput by remember { mutableStateOf(settings.googleOAuthClientId()) }
    var baseUrlInput by remember { mutableStateOf(settings.cloudBaseUrl()) }
    var modelInput by remember { mutableStateOf(settings.cloudModel()) }
    var status by remember { mutableStateOf<String?>(null) }
    var githubDeviceCode by remember { mutableStateOf<String?>(null) }
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

            // PRD-09 소셜 로그인 UX: GitHub 로그인 버튼(기본). Client ID는 빌드 시점 기본값 사용.
            Text("GitHub 계정 연결", style = MaterialTheme.typography.titleMedium)
            githubConnectionStatus?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
        
        )
            }
            githubDeviceCode?.let { code ->
                Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("GitHub 인증 코드", style = MaterialTheme.typography.labelLarge)
                        Text(code, style = MaterialTheme.typography.headlineSmall)
                        Text("브라우저에서 이 코드를 입력하고 승인을 마치면 앱이 자동으로 연결됩니다.")
                    }
                }
            }
            Text(
                "GitHub로 로그인하면 GitHub 인증 화면에서 승인 후 연결됩니다. 저장소 조회는 기본 허용, " +
                    "Issue/PR 생성은 사용 시 승인이 필요합니다. 토큰은 기기에 암호화 저장됩니다.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (settings.githubOAuthClientId().isNotBlank()) {
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                githubDeviceCode = null
                                status = onConnectGitHub { code -> githubDeviceCode = code }
                            }
                        },
                    ) { Text("GitHub로 로그인") }
                    OutlinedButton(
                        onClick = onDisconnectGitHub,
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Text("연결 해제") }
                }
            } else {
                Text(
                    "로그인이 아직 준비되지 않았습니다. 아래 고급 설정에서 Client ID를 한 번만 등록하면 " +
                        "버튼만 눌러 로그인할 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Text(
                "고급: OAuth Client ID 등록 / Personal Access Token 직접 입력",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp),
            )
            OutlinedTextField(
                value = gitHubClientIdInput,
                onValueChange = { gitHubClientIdInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                label = { Text("GitHub OAuth App Client ID") },
                placeholder = { Text("Iv1.... / Ov23....") },
                singleLine = true,
            )
            Row(modifier = Modifier.padding(top = 4.dp)) {
                Button(
             
       onClick = {
                        settings.setGithubOAuthClientId(gitHubClientIdInput)
                        status = "GitHub Client ID가 저장되었습니다."
                    },
                    enabled = gitHubClientIdInput.isNotBlank(),
                ) { Text("Client ID 저장") }
            }
            OutlinedTextField(
                value = gitHubTokenInput,
                onValueChange = { gitHubTokenInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
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
                ) { Text("PAT 저장") }
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // PRD-09 소셜 로그인 UX: Google 로그인 버튼(기본). Client ID는 빌드 시점 기본값 사용.
            Text("Google 계정 연결 (Gmail)", style = MaterialTheme.typography.titleMedium)
            gmailConnectionStatus?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
   
         }
            Text(
                "Google로 로그인하면 Google 인증 화면에서 계정을 승인합니다. 승인 후 앱으로 돌아오면 " +
                    "연결되고 토큰은 만료 시 자동 갱신됩니다(refresh token). 메일 발송은 사용 시 승인이 필요하며 " +
                    "토큰은 기기에 암호화 저장됩니다.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (settings.googleOAuthClientId().isNotBlank()) {
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    Button(
                        onClick = { status = onConnectGoogle() },
                    ) { Text("Google로 로그인") }
                    OutlinedButton(
                        onClick = onDisconnectGmail,
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Text("연결 해제") }
                }
            } else {
                Text(
                    "로그인이 아직 준비되지 않았습니다. 아래 고급 설정에서 Client ID를 한 번만 등록하면 " +
                        "버튼만 눌러 로그인할 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Text(
                "고급: OAuth Client ID 등록 / 액세스 토큰 직접 입력 (만료 시 재등록 필요)",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp),
            )
            OutlinedTextField(
                value = googleClientIdInput,
                onValueChange = { googleClientIdInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                label = { Text("Google OAuth Client ID (Android)") },
                placeholder = { Text("....apps.googleusercontent.com") },
                singleLine = true,
            )
            Row(modifier = Modifier.padding(top = 4.dp)) {
                Button(
                    onClick = {
                        settings.setGoogleOAuthClientId(googleClientIdInput)
                        status = "Google Client ID가 저장되었습니다."
                    },
                    enabled = googleClientIdInput.isNotBlank(),
                ) { Text("Client ID 저장") }
            }
            OutlinedTextField(
                value = gmailTokenInput,
                onValueChange = { gmailTokenInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                placeholder = { Text("ya29....") },
                singleLine = true,
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            onSaveGmailToken(gmailTokenInput)
                            gmailTokenInput = ""
                            status = "Gmail 토큰이 안전하게 저장되었습니다 (기기 암호화)."
                        }
                    },
                    enabled = gmailTokenInput.isNotBlank(),
                ) { Text("토큰 저장") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            onDeleteGmailToken()
                            status = "Gmail 토큰이 삭제되었습니다."
                        }
                    },
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("삭제") }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // PRD-06: 기기 캘린더 연동. 일정 조회/등록에는 캘린더 권한이 필요하다.
            Text("Calendar 연동", style = MaterialTheme.typography.titleMedium)
            Text(
                "기기 캘린더 일정 조회(기본 허용)와 일정 등록(승인 필요)에 캘린더 권한이 필요합니다.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (calendarPermissionGranted) {
                Text(
                    "캘린더 권한이 허용되었습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Button(
                    onClick = onRequestCalendarPermission,
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("캘린더 권한 허용") }
                Text(
                    "권한을 거부한 경우 시스템 설정 → 앱 → AirCall AI → 권한에서 캘린더를 허용해 주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

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
