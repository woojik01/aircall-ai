package com.woojik.aircallai.ui

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
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
import kotlinx.coroutines.delay
import com.woojik.aircallai.auth.ConnectionStatus

@Composable
fun SettingsCategories(onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingCategory("ai", "AI 및 모델", "로컬 · 클라우드, 모델 다운로드", onOpen)
        SettingCategory("accounts", "도구 및 계정", "GitHub · Google 로그인", onOpen)
        SettingCategory("appearance", "화면 및 알림", "라이트 · 다크 · 시스템, 알림 설정", onOpen)
        SettingCategory("permissions", "작업 승인", "실행 전 확인 및 이전 승인 관리", onOpen)
        SettingCategory("privacy", "개인정보", "개인정보처리방침 · 데이터 삭제", onOpen)
        SettingCategory("about", "앱 정보", "버전 · 문의", onOpen)
    }
}

@Composable
private fun SettingCategory(id: String, title: String, detail: String, onOpen: (String) -> Unit) {
    val symbol = when (id) {
        "ai" -> UiSymbol.Model; "accounts" -> UiSymbol.Account; "appearance" -> UiSymbol.Theme
        "permissions", "privacy" -> UiSymbol.Shield; else -> UiSymbol.Info
    }
    Surface(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = title) { onOpen(id) },
        shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { AirCallIcon(symbol) }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AirCallIcon(UiSymbol.Chevron)
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
    var status by remember(category) { mutableStateOf<String?>(null) }
    var deviceCode by remember { mutableStateOf<String?>(null) }
    var connecting by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(graph.settings.aiProviderMode()) }
    var key by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf(graph.settings.cloudBaseUrl()) }
    var model by remember { mutableStateOf(graph.settings.cloudModel()) }
    var approvals by remember { mutableStateOf(graph.toolPermissions.approvedActions()) }
    LaunchedEffect(status) {
        if (status != null) { delay(6_000); status = null }
    }
    LaunchedEffect(accounts["github"]?.status) {
        if (accounts["github"]?.status == ConnectionStatus.CONNECTED) deviceCode = null
    }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        when (category) {
            "ai" -> {
                SettingsSection("응답 생성 방식") {
                    listOf(SettingsRepository.MODE_LOCAL to "이 기기에서 · 로컬", SettingsRepository.MODE_CLOUD to "클라우드 API").forEach { (value, label) ->
                        Row(Modifier.fillMaxWidth().selectable(selected = mode == value, role = Role.RadioButton,
                            onClick = {
                                if (mode != value) {
                                    vm.cancelText()
                                    if (graph.sessionController.isRunning) {
                                        vm.endSession(); status = "AI 설정 변경으로 통화를 종료했습니다. 다시 시작해 주세요."
                                    }
                                    mode = value; graph.settings.setAiProviderMode(value)
                                    vm.engine.updateProvider(graph.providerRouter.current()); vm.refreshProviderReadiness()
                                }
                            }).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = mode == value, onClick = null)
                            Text(label, Modifier.weight(1f).padding(start = 8.dp))
                        }
                    }
                    OutlinedButton(onClick = onOpenLocalModels, modifier = Modifier.fillMaxWidth()) { Text("로컬 모델 관리") }
                }
                SettingsSection("클라우드 연결") {
                    OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("API 키") },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { scope.launch {
                            graph.credentials.save(CloudAIProvider.KEY_SERVICE, key.toByteArray()); key = ""
                            vm.refreshProviderReadiness(); status = "API 키를 저장했습니다."
                        } }, enabled = key.isNotBlank()) { Text("키 저장") }
                        TextButton(onClick = { scope.launch {
                            graph.credentials.delete(CloudAIProvider.KEY_SERVICE); vm.refreshProviderReadiness(); status = "키를 삭제했습니다."
                        } }) { Text("키 삭제") }
                    }
                    OutlinedTextField(endpoint, { endpoint = it }, Modifier.fillMaxWidth(), label = { Text("API 주소") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("모델명") }, singleLine = true)
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
                    }, modifier = Modifier.fillMaxWidth(), enabled = com.woojik.aircallai.privacy.HttpsEndpoint.isHttpsEndpoint(endpoint.trim()) && model.isNotBlank()) { Text("연결 정보 저장") }
                }
            }
            "accounts" -> {
                AccountCard("GitHub", accounts["github"]?.let { graph.accountRepository.statusMessage(it) } ?: "연결 안 됨") {
                    if (accounts["github"]?.status != ConnectionStatus.CONNECTED) {
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
                            } finally { connecting = false; deviceCode = null }
                        }
                    }, enabled = !connecting) { Text(if (connecting) "승인 대기 중" else "GitHub로 로그인") }
                    }
                    if (accounts["github"]?.status != null && accounts["github"]?.status != ConnectionStatus.NOT_CONNECTED) {
                    TextButton(onClick = { disconnect("github") }, enabled = !connecting) { Text("연결 해제") }
                    }
                }
                AccountCard("Google · Gmail 및 캘린더", accounts["gmail"]?.let { graph.accountRepository.statusMessage(it) } ?: "연결 안 됨") {
                    if (accounts["gmail"]?.status != ConnectionStatus.CONNECTED) {
                    Button(onClick = { status = connectGoogle() }, enabled = accounts["gmail"]?.status != ConnectionStatus.CONNECTING) {
                        Text(if (accounts["gmail"]?.status == ConnectionStatus.CONNECTING) "연결 중" else "Google로 로그인")
                    }
                    }
                    if (accounts["gmail"]?.status != null && accounts["gmail"]?.status != ConnectionStatus.NOT_CONNECTED) {
                    TextButton(onClick = { disconnect("gmail") }) { Text("연결 해제") }
                    }
                }
                AccountCard("기기 메모", "로그인 없이 사용") {}
            }
            "permissions" -> {
                Text("작업 실행 전마다 승인을 요청합니다.")
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
                SettingsSection("테마") {
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
                }
                SettingsSection("알림") {
                    OutlinedButton(onClick = openNotificationSettings, modifier = Modifier.fillMaxWidth()) { Text("시스템 알림 설정") }
                }
            }
        }
        status?.let { Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer) { Text(it, Modifier.fillMaxWidth().padding(16.dp)) } }
    }
}

@Composable
private fun AccountCard(title: String, status: String, content: @Composable ColumnScope.() -> Unit) {
    SettingsSection(title) {
        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}
