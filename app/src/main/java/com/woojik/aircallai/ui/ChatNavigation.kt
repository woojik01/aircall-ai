package com.woojik.aircallai.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import com.woojik.aircallai.AppGraph
import com.woojik.aircallai.R
import com.woojik.aircallai.chat.ChatRoom
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirCallUi(
    vm: MainViewModel, graph: AppGraph,
    openTarget: String, openVersion: Int,
    connectGitHub: suspend ((String) -> Unit) -> String,
    connectGoogle: () -> String, disconnectAccount: (String) -> Unit,
    startDownload: (String) -> Unit,
    requestTaskNotifications: () -> Unit = {},
) {
    var themeMode by remember { mutableStateOf(graph.settings.themeMode()) }
    var ageAcknowledged by remember { mutableStateOf(graph.settings.hasMinimumAgeAcknowledgement()) }
    if (!ageAcknowledged) {
        val context = LocalContext.current
        AirCallTheme(themeMode = themeMode) {
            AuroraBackground {
                AlertDialog(onDismissRequest = {}, title = { Text("이용 연령 안내") },
                    text = { Text("AirCall AI는 만 14세 이상을 대상으로 합니다. 생년월일이나 신분증을 수집하지 않으며, " +
                        "이 확인은 기기에만 저장합니다. AI 답변은 틀릴 수 있습니다. 만 14세 이상인 경우 계속해 주세요.") },
                    confirmButton = { TextButton(onClick = {
                        graph.settings.acceptMinimumAgeAcknowledgement(); ageAcknowledged = true
                    }) { Text("만 14세 이상입니다") } },
                    dismissButton = { TextButton(onClick = { (context as? android.app.Activity)?.finish() }) { Text("나가기") } },
                )
            }
        }
        return
    }
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val rooms by graph.chatRooms.rooms.collectAsState()
    val activeId by graph.chatRooms.activeId.collectAsState()
    val ready by graph.chatRooms.ready.collectAsState()
    val storageError by graph.chatRooms.error.collectAsState()
    val taskEvents by graph.toolTracker.events.collectAsState()
    LaunchedEffect(Unit) { requestTaskNotifications() }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "chat"
    var editing by remember { mutableStateOf<ChatRoom?>(null) }
    var deleting by remember { mutableStateOf<ChatRoom?>(null) }
    var rename by remember { mutableStateOf("") }
    var pendingText by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingVoice by rememberSaveable { mutableStateOf(false) }
    var pendingRoomId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(activeId) {
        if (pendingRoomId != activeId) {
            pendingText = null; pendingVoice = false; pendingRoomId = null
        }
    }
    val usesCloud = graph.settings.aiProviderMode() == com.woojik.aircallai.settings.SettingsRepository.MODE_CLOUD
    fun sendText(text: String): Boolean {
        if (graph.settings.aiProviderMode() == com.woojik.aircallai.settings.SettingsRepository.MODE_CLOUD &&
            !graph.settings.hasCloudDisclosure()) {
            pendingText = text; pendingRoomId = activeId; return false
        }
        vm.sendText(text); return true
    }
    fun startVoice() {
        if (!graph.settings.hasSpeechDisclosure() ||
            (graph.settings.aiProviderMode() == com.woojik.aircallai.settings.SettingsRepository.MODE_CLOUD && !graph.settings.hasCloudDisclosure())) {
            pendingVoice = true; pendingRoomId = activeId
        } else vm.onMicTap()
    }
    fun openChat() { nav.navigate("chat") { popUpTo("chat") { inclusive = true }; launchSingleTop = true } }
    fun changeRoom(id: String? = null) {
        scope.launch {
            vm.prepareForRoomChange()
            graph.toolApproval.deny()
            val room = if (id == null) graph.chatRooms.newRoom() else graph.chatRooms.select(id)
            room?.let { vm.engine.restore(it.messages) }
            drawer.close(); openChat()
        }
    }
    LaunchedEffect(openVersion, ready) {
        if (!ready || openTarget.isBlank()) return@LaunchedEffect
        if (openTarget == "models") nav.navigate("models") { launchSingleTop = true }
        else if (openTarget == "call") nav.navigate("call") { launchSingleTop = true }
        else openChat()
    }
    BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }
    AirCallTheme(themeMode = themeMode) {
        AuroraBackground {
            ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = ready, drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.fillMaxWidth(0.88f).widthIn(max = 360.dp),
                    windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
                ) {
                    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Image(painterResource(R.drawable.aircall_ai_icon), "AirCall AI 아이콘",
                            Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)))
                        Column(Modifier.weight(1f)) { Text("AirCall AI", style = MaterialTheme.typography.titleLarge)
                            Text("나만의 대화 공간", style = MaterialTheme.typography.bodyMedium) }
                    }
                    Button(onClick = { changeRoom() }, enabled = ready,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { Text("새 채팅") }
                    Text("채팅방", Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.labelLarge)
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp)) {
                        items(rooms, key = { it.id }) { room ->
                            var menu by remember { mutableStateOf(false) }
                            Surface(color = if (room.id == activeId) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                contentColor = if (room.id == activeId) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text(room.title, Modifier.weight(1f).clickable { changeRoom(room.id) }.padding(16.dp),
                                        style = MaterialTheme.typography.bodyLarge)
                                    Box {
                                        TextButton(onClick = { menu = true }, contentPadding = PaddingValues(8.dp)) { Text("관리") }
                                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                            DropdownMenuItem(text = { Text("이름 변경") }, onClick = {
                                                menu = false; editing = room; rename = room.title
                                            })
                                            DropdownMenuItem(text = { Text("삭제") }, onClick = { menu = false; deleting = room })
                                        }
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    NavigationDrawerItem(label = { Text("설정") }, selected = route.startsWith("settings"),
                        modifier = Modifier.padding(12.dp), onClick = { scope.launch {
                            drawer.close(); nav.navigate("settings") { launchSingleTop = true }
                        } })
                }
            }) {
                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
                    topBar = {
                    // A custom top bar must handle its own top inset; Scaffold only
                    // forwards the measured bar height to the screen content.
                    Surface(
                        color = Color.Transparent,
                        modifier = Modifier.windowInsetsPadding(
                            WindowInsets.systemBars.union(WindowInsets.displayCutout)
                                .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                        ),
                    ) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            TextButton(onClick = {
                                if (route == "chat") scope.launch { drawer.open() }
                                else if (!nav.popBackStack()) openChat()
                            }) { Text(if (route == "chat") "메뉴" else "뒤로") }
                            Text(when (route) {
                                "chat" -> rooms.firstOrNull { it.id == activeId }?.title ?: "새 채팅"
                                "call" -> "음성 대화"
                                "models" -> "로컬 모델"
                                "privacy" -> "개인정보"
                                "about" -> "앱 정보 및 사용 안내"
                                "settings/{category}" -> when (entry?.arguments?.getString("category")) {
                                    "ai" -> "AI 및 모델"; "accounts" -> "도구 및 계정"
                                    "permissions" -> "작업 승인"; else -> "화면 및 알림"
                                }
                                else -> "설정"
                            }, modifier = Modifier.weight(1f).padding(end = 12.dp),
                                style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }) { padding ->
                    // Consume Scaffold padding so nested screens do not apply system
                    // bar insets twice; their IME padding still handles the keyboard.
                    Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                        storageError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                        taskEvents.lastOrNull { it.roomId == activeId }?.let { task ->
                            Text(task.summary, color = if (task.status == com.woojik.aircallai.tools.ToolExecutionStatus.FAILED)
                                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        }
                        if (!ready) {
                            if (storageError == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                            else TextButton(onClick = { scope.launch {
                                graph.chatRooms.initialize()
                                if (graph.chatRooms.ready.value && graph.chatRooms.activeId.value == null) {
                                    vm.engine.restore(graph.chatRooms.newRoom().messages)
                                }
                            } }) { Text("기록 다시 읽기") }
                        } else NavHost(navController = nav, startDestination = "chat", modifier = Modifier.weight(1f)) {
                            composable("chat") { ConversationScreen(vm, activeId, onOpenCall = { nav.navigate("call") },
                                onSendText = { sendText(it) },
                                onOpenAiSettings = { nav.navigate("settings/ai") }) }
                            composable("call") { CallScreen(vm, onExit = { nav.popBackStack() },
                                onStartSession = { startVoice() }) }
                            composable("settings") { SettingsCategories { category ->
                                nav.navigate(when (category) {
                                    "privacy", "about" -> category
                                    else -> "settings/$category"
                                })
                            } }
                            composable("about") { AppInfoScreen(
                                onOpenAiSettings = { nav.navigate("settings/ai") },
                                onOpenPrivacy = { nav.navigate("privacy") },
                            ) }
                            composable("settings/{category}") { target ->
                                SettingsDetail(target.arguments?.getString("category").orEmpty(), graph, vm,
                                    themeMode = themeMode, onThemeChanged = { themeMode = it },
                                    onOpenLocalModels = { nav.navigate("models") }, connectGitHub = connectGitHub,
                                    connectGoogle = connectGoogle, disconnect = disconnectAccount,
                                    openNotificationSettings = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) })
                            }
                            composable("models") {
                                LocalModelScreen(graph.settings, graph.localModelAdapter, graph.modelDownloadManager,
                                    graph.appScope, onModelChanged = { vm.refreshProviderReadiness() },
                                    onDownload = { startDownload(it.id) }, onCancelDownload = {
                                        context.startService(Intent(context, com.woojik.aircallai.service.ModelDownloadService::class.java)
                                            .setAction(com.woojik.aircallai.service.ModelDownloadService.ACTION_CANCEL)
                                            .putExtra(com.woojik.aircallai.service.ModelDownloadService.EXTRA_MODEL, it.id))
                                    })
                            }
                            composable("privacy") { PrivacyScreen(onBeforeClearData = {
                                vm.endSession(); graph.toolApproval.deny()
                            }) }
                        }
                    }
                }
            }
            editing?.let { room ->
                AlertDialog(onDismissRequest = { editing = null }, title = { Text("채팅방 이름 변경") },
                    text = { OutlinedTextField(rename, { rename = it.take(80) }, label = { Text("채팅방 이름") }, singleLine = true) },
                    confirmButton = { TextButton(onClick = { graph.chatRooms.rename(room.id, rename); editing = null },
                        enabled = rename.isNotBlank()) { Text("저장") } },
                    dismissButton = { TextButton(onClick = { editing = null }) { Text("취소") } })
            }
            deleting?.let { room ->
                AlertDialog(onDismissRequest = { deleting = null }, title = { Text("채팅방을 삭제할까요?") },
                    text = { Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        Text("${room.title}의 대화 기록이 기기에서 삭제됩니다.")
                    } },
                    confirmButton = { TextButton(onClick = { scope.launch {
                        if (room.id == activeId) {
                            vm.prepareForRoomChange(); graph.toolApproval.deny()
                            graph.chatRooms.delete(room.id)
                            vm.engine.restore(graph.chatRooms.newRoom().messages)
                            openChat()
                        } else graph.chatRooms.delete(room.id)
                        deleting = null
                    } }) { Text("삭제") } },
                    dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } })
            }
            val pending by graph.toolApproval.pending.collectAsState()
            pending?.let { ToolApprovalDialog(it, onApprove = { graph.toolApproval.approve() }, onDeny = { graph.toolApproval.deny() }) }
            if (pendingText != null || pendingVoice) {
                val voice = pendingVoice
                AlertDialog(onDismissRequest = { pendingText = null; pendingVoice = false; pendingRoomId = null },
                    title = { Text(if (voice) "음성 대화의 데이터 사용" else "클라우드로 대화 전송") },
                    text = { Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (voice) Text("통화를 시작하면 마이크를 사용합니다. Android 음성 인식·출력 서비스의 설정에 따라 " +
                            "음성과 텍스트가 해당 서비스 제공자에게 전송될 수 있습니다. 앱이 화면 밖에 있거나 화면이 꺼져도 " +
                            "통화가 진행되는 동안 마이크를 사용할 수 있으며, 알림에서 일시정지하거나 종료할 수 있습니다.")
                        if (usesCloud) Text("대화 기록과 응답 생성에 필요한 도구 결과가 설정한 AI 서버로 전송됩니다. " +
                            "API 키는 이 서버의 요청 인증에 사용됩니다. 전송 주소: ${graph.settings.cloudBaseUrl()}")
                        else if (voice) Text("로컬 모델의 AI 응답 생성은 기기에서 처리됩니다. 음성 서비스와 도구 연동은 별도로 네트워크를 사용할 수 있습니다.")
                        Text("자세한 내용은 설정의 개인정보 화면에서 확인할 수 있습니다.")
                    } },
                    confirmButton = { TextButton(onClick = {
                        // A notification/deep link can change rooms while this dialog is open.
                        val resume = pendingRoomId != null && pendingRoomId == graph.chatRooms.activeId.value
                        val text = pendingText
                        pendingText = null; pendingVoice = false; pendingRoomId = null
                        if (!resume) return@TextButton
                        if (usesCloud) graph.settings.acceptCloudDisclosure()
                        if (voice) graph.settings.acceptSpeechDisclosure()
                        if (voice) vm.onMicTap() else text?.let { vm.sendText(it) }
                    }) { Text("동의하고 계속") } },
                    dismissButton = { TextButton(onClick = { pendingText = null; pendingVoice = false; pendingRoomId = null }) { Text("취소") } },
                )
            }
        }
    }
}
