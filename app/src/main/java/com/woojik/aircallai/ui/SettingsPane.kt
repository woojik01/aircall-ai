package com.woojik.aircallai.ui

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.woojik.aircallai.AppGraph
import com.woojik.aircallai.ai.cloud.CloudAIProvider
import com.woojik.aircallai.settings.SettingsRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsCategories(onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingCategory("ai", "AI 및 모델", "로컬 · 클라우드, 모델 다운로드", onOpen)
        SettingCategory("accounts", "도구 및 계정", "GitHub · Google 로그인", onOpen)
        SettingCategory("appearance", "화면 및 알림", "라이트 · 다크 · 시스템, 알림 설정", onOpen)
        SettingCategory("permissions", "작업 승인", "실행 전 확인 및 이전 승인 관리", onOpen)
        SettingCategory("privacy", "개인정보", "기기 저장과 데이터 전송 안내", onOpen)
    }
}

@Composable
private fun SettingCategory(id: String, title: String, detail: String, onOpen: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onOpen(id) }) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsDetail(
    category: String, graph: AppGraph, vm: MainViewModel,
    onOpenLocalModels: () -> Unit,
    connectGitHub: suspend ((String) -> Unit) -> String,
    connectGoogle: () -> String, disconnect: (String) -> Unit,
    openNotificationSettings: () -> Unit,
    themeMode: String,
    onThemeChanged: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val accounts by graph.accountRepository.connections.collectAsState()
    var status by remember { mutableStateOf<String?>(null) }
    var deviceCode by remember { mutableStateOf<String?>(null) }
    var connecting by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(graph.settings.aiProviderMode()) }
    var key by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf(graph.settings.cloudBaseUrl()) }
    var model by remember { mutableStateOf(graph.settings.cloudModel()) }
    var approvals by remember { mutableStateOf(graph.toolPermissions.approvedActions()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        when (category) {
            "ai" -> {
                Text("응답 생성 방식", style = MaterialTheme.typography.titleMedium)
                listOf(SettingsRepository.MODE_LOCAL to "이 기기에서 · 로컬", SettingsRepository.MODE_CLOUD to "클라우드 API").forEach { (value, label) ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = mode == value, onClick = {
                            if (mode != value) {
                                vm.cancelText()
                                if (graph.sessionController.isRunning) {
                                    vm.endSession(); status = "AI 설정 변경으로 통화를 종료했습니다. 다시 시작해 주세요."
                                }
                                mode = value; graph.settings.setAiProviderMode(value)
                                vm.engine.updateProvider(graph.providerRouter.current()); vm.refreshProviderReadiness()
                            }
                        })
                        Text(label, Modifier.weight(1f))
                    }
                }
                Button(onClick = onOpenLocalModels, modifier = Modifier.fillMaxWidth()) { Text("로컬 모델 관리") }
                Text("클라우드 연결", style = MaterialTheme.typography.titleMedium)
                Text("AI 모델 제공자의 API 키는 도구 계정 로그인과 별도로 설정합니다.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("API 키") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch {
                        graph.credentials.save(CloudAIProvider.KEY_SERVICE, key.toByteArray()); key = ""
                        vm.refreshProviderReadiness(); status = "API 키를 기기에 암호화해 저장했습니다."
                    } }, enabled = key.isNotBlank()) { Text("키 저장") }
                    TextButton(onClick = { scope.launch {
                        graph.credentials.delete(CloudAIProvider.KEY_SERVICE); vm.refreshProviderReadiness(); status = "키를 삭제했습니다."
                    } }) { Text("키 삭제") }
                }
                OutlinedTextField(endpoint, { endpoint = it }, Modifier.fillMaxWidth(), label = { Text("API 주소") }, minLines = 2, maxLines = 4)
                OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("모델명") }, maxLines = 3)
                Button(onClick = {
                    val changed = endpoint.trim() != graph.settings.cloudBaseUrl() || model.trim() != graph.settings.cloudModel()
                    val endedCall = changed && graph.sessionController.isRunning
                    if (changed) {
                        vm.cancelText()
                        if (endedCall) vm.endSession()
                    }
                    graph.settings.setCloudBaseUrl(endpoint); graph.settings.setCloudModel(model)
                    vm.refreshProviderReadiness()
                    status = if (endedCall) "연결 정보를 저장하고 통화를 종료했습니다. 다시 시작해 주세요." else "연결 정보를 저장했습니다."
                }, enabled = com.woojik.aircallai.privacy.ContentReportClient.isHttpsEndpoint(endpoint.trim()) && model.isNotBlank()) { Text("연결 정보 저장") }
            }
            "accounts" -> {
                Text("서비스에 로그인하여 도구를 연결하세요. 로그인 없이도 AI 대화와 기기 메모를 사용할 수 있습니다.")
                AccountCard("GitHub", accounts["github"]?.let { graph.accountRepository.statusMessage(it) } ?: "연결 안 됨") {
                    deviceCode?.let { code ->
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text("인증 코드: $code", style = MaterialTheme.typography.titleLarge)
                        }
                        Text("브라우저에서 코드를 입력한 뒤 앱으로 돌아오세요.")
                    }
                    Button(onClick = {
                        connecting = true
                        scope.launch {
                            try { status = connectGitHub { deviceCode = it } }
                            catch (e: kotlinx.coroutines.CancellationException) {
                                graph.accountRepository.refresh(System.currentTimeMillis())
                                throw e
                            } finally { connecting = false }
                        }
                    }, enabled = !connecting) { Text(if (connecting) "승인 대기 중" else "GitHub로 로그인") }
                    TextButton(onClick = { disconnect("github") }, enabled = !connecting) { Text("연결 해제") }
                }
                AccountCard("Google · Gmail 및 캘린더", accounts["gmail"]?.let { graph.accountRepository.statusMessage(it) } ?: "연결 안 됨") {
                    Text("메일 발송과 Google 기본 캘린더의 일정 조회·등록에 사용합니다.")
                    Button(onClick = { status = connectGoogle() }) { Text("Google로 로그인") }
                    TextButton(onClick = { disconnect("gmail") }) { Text("연결 해제") }
                }
                AccountCard("기기 메모", "로그인 없이 사용") { Text("메모는 이 기기에 저장됩니다.") }
            }
            "permissions" -> {
                Text("메일 발송, 일정 등록, 메모 저장 등 변경 작업은 매번 내용을 확인한 뒤 실행합니다. 이전 버전의 저장된 승인은 자동 실행에 사용되지 않습니다.")
                if (approvals.isEmpty()) Text("이전 버전에 저장된 승인이 없습니다.")
                approvals.forEach { action ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(action.split(':', limit = 2).let {
                                com.woojik.aircallai.tools.ToolLabels.action(it[0], it.getOrElse(1) { "" })
                            })
                            TextButton(onClick = { scope.launch {
                                graph.toolPermissions.revoke(action); approvals = graph.toolPermissions.approvedActions()
                            } }) { Text("이전 승인 삭제") }
                        }
                    }
                }
            }
            "appearance" -> {
                Text("은은한 오로라", style = MaterialTheme.typography.titleLarge)
                Text("기기에 맞는 화면 테마를 선택하세요.")
                Text("테마", style = MaterialTheme.typography.titleMedium)
                listOf(
                    SettingsRepository.THEME_LIGHT to "라이트",
                    SettingsRepository.THEME_DARK to "다크",
                    SettingsRepository.THEME_SYSTEM to "시스템 설정",
                ).forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().selectable(
                        selected = themeMode == value,
                        role = androidx.compose.ui.semantics.Role.RadioButton,
                        onClick = {
                            graph.settings.setThemeMode(value)
                            onThemeChanged(value)
                        },
                    ).padding(vertical = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = themeMode == value, onClick = null)
                        Text(label, modifier = Modifier.padding(start = 12.dp),
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Text("시스템 설정을 선택하면 기기의 다크 모드 변경을 자동으로 반영합니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                Text("알림", style = MaterialTheme.typography.titleMedium)
                Text("AI 도구 작업을 실제로 실행하면 성공·실패 결과 알림을 보냅니다. 알림을 누르면 해당 채팅방을 엽니다. 통화 알림에서는 일시정지·재개, 음소거, 종료를, 모델 다운로드 알림에서는 진행률 확인과 취소를 사용할 수 있습니다.")
                Text("알림을 허용하지 않아도 작업 결과는 앱에서 확인할 수 있습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = openNotificationSettings) { Text("시스템 알림 설정 열기") }
                HorizontalDivider()
                Text("앱 정보", style = MaterialTheme.typography.titleMedium)
                Text("버전 ${com.woojik.aircallai.BuildConfig.VERSION_NAME} (${com.woojik.aircallai.BuildConfig.VERSION_CODE})")
                Text(if (com.woojik.aircallai.BuildConfig.DEBUG) "개발용 앱" else "정식 배포용 앱")
            }
        }
        status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun AccountCard(title: String, status: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
