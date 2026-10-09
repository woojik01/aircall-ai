package com.woojik.aircallai.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.woojik.aircallai.R
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.conversation.ConversationState
import com.woojik.aircallai.session.SessionStatus

@Composable
fun ConversationScreen(vm: MainViewModel, roomId: String?, onOpenCall: () -> Unit,
    onSendText: (String) -> Boolean = { vm.sendText(it); true },
    onOpenAiSettings: () -> Unit = {}) {
    val state by vm.state.collectAsState()
    val transcript by vm.transcript.collectAsState()
    val sessionStatus by vm.sessionStatus.collectAsState()
    val providerReady by vm.providerReady.collectAsState()
    val partial by vm.engine.partialResponse.collectAsState()
    val retryAllowed by vm.engine.retryAllowed.collectAsState()
    val listState = rememberLazyListState()
    var input by rememberSaveable(roomId) { mutableStateOf("") }
    var awaitingConsent by rememberSaveable(roomId) { mutableStateOf(false) }
    val active = sessionStatus == SessionStatus.Running || sessionStatus == SessionStatus.Paused
    LaunchedEffect(roomId, transcript.size) {
        if (awaitingConsent && transcript.lastOrNull { it.role == ChatMessage.Role.USER }?.content == input.trim()) {
            input = ""; awaitingConsent = false
        }
        if (transcript.isNotEmpty()) listState.animateScrollToItem(transcript.lastIndex)
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        if (transcript.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(painterResource(R.drawable.aircall_ai_icon), "AirCall AI",
                    Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)))
                Spacer(Modifier.height(24.dp))
                Text("어떤 이야기를 나눌까요?", style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(if (providerReady) "글로 남기거나, 편하게 말해 보세요." else
                    "먼저 AI를 설정해 주세요.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                if (!providerReady) TextButton(onClick = onOpenAiSettings) { Text("AI 설정 열기") }
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                itemsIndexed(transcript) { _, message ->
                    val user = message.role == ChatMessage.Role.USER
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
                        Surface(
                            color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            contentColor = if (user) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            shape = RoundedCornerShape(if (user) 22.dp else 16.dp),
                            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(if (user) 0.88f else 1f),
                        ) {
                            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                                Text(if (user) "나" else "AirCall", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 6.dp))
                                if (user) androidx.compose.foundation.text.selection.SelectionContainer {
                                    Text(message.content, style = MaterialTheme.typography.bodyLarge)
                                } else ChatMarkdown(message.content)
                            }
                        }
                    }
                }
            }
        }
        if (state is ConversationState.Error) {
            Surface(color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text((state as ConversationState.Error).message, style = MaterialTheme.typography.bodyMedium)
                    if (retryAllowed && !active) TextButton(onClick = { vm.retryText() }) { Text("다시 시도") }
                    TextButton(onClick = onOpenAiSettings) { Text("AI 설정 확인") }
                    if (!retryAllowed && (state as ConversationState.Error).kind == ConversationState.ErrorKind.AI_PROVIDER)
                        Text("변경 작업을 시도했다면 외부 서비스에서 결과를 확인한 뒤 새 요청을 보내세요.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { vm.engine.clearError() }) { Text("닫기") }
                }
            }
        }
        if (state is ConversationState.Processing) {
            if (partial.isNotBlank()) Surface(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(partial, Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()).padding(12.dp))
            }
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("생각하고 있어요", Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { vm.cancelText() }) { Text("중지") }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface, tonalElevation = 2.dp,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(vm.engine.activeProvider.displayName, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onOpenCall) {
                        AirCallIcon(UiSymbol.Phone)
                        Spacer(Modifier.width(6.dp))
                        Text(if (active) "통화 중" else "음성 대화", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = input, onValueChange = { input = it },
                        modifier = Modifier.weight(1f), placeholder = { Text("메시지 입력") },
                        shape = RoundedCornerShape(24.dp), maxLines = 5,
                        textStyle = MaterialTheme.typography.bodyLarge)
                    FilledIconButton(onClick = {
                        if (onSendText(input)) { input = ""; awaitingConsent = false } else awaitingConsent = true
                    }, enabled = roomId != null && input.isNotBlank() && state !is ConversationState.Processing && !active,
                        modifier = Modifier.size(56.dp), shape = RoundedCornerShape(20.dp)) {
                        AirCallIcon(UiSymbol.Send, "메시지 전송")
                    }
                }
            }
        }
    }
}
