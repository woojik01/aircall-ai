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
                ModalDrawerSheet(modifier = Modifier.fillMaxWidth(0.88f).widthIn(max = 360.dp)) {
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
                Scaffold(containerColor = Color.Transparent, topBar = {
                    Surface(color = Color.Transparent) {
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
                    Column(Modifier.fillMaxSize().padding(padding)) {
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
                            composable("chat") { ConversationScreen(vm, activeId, onOpenCall = { nav.navigate("call") }) }
                            composable("call") { CallScreen(vm, onExit = { nav.popBackStack() }) }
                            composable("settings") { SettingsCategories { category ->
                                nav.navigate(if (category == "privacy") "privacy" else "settings/$category")
                            } }
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
                            composable("privacy") { PrivacyScreen() }
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
        }
    }
}
