package com.woojik.aircallai.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun ConversationScreen(vm: MainViewModel, roomId: String?, onOpenCall: () -> Unit) {
    val state by vm.state.collectAsState()
    val transcript by vm.transcript.collectAsState()
    val sessionStatus by vm.sessionStatus.collectAsState()
    val listState = rememberLazyListState()
    var input by rememberSaveable(roomId) { mutableStateOf("") }
    val active = sessionStatus == SessionStatus.Running || sessionStatus == SessionStatus.Paused
    LaunchedEffect(roomId, transcript.size) {
        if (transcript.isNotEmpty()) listState.animateScrollToItem(transcript.lastIndex)
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        if (transcript.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(painterResource(R.drawable.aircall_ai_icon), "AirCall AI",
                    Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)))
                Spacer(Modifier.height(24.dp))
                Text("어떤 이야기를 나눌까요?", style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text("글로 남기거나, 편하게 말해 보세요.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                itemsIndexed(transcript) { _, message ->
                    val user = message.role == ChatMessage.Role.USER
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
                        Surface(
                            color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            shape = RoundedCornerShape(22.dp), modifier = Modifier.widthIn(max = 340.dp),
                        ) {
                            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                                if (!user) Text("AirCall", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 6.dp))
                                androidx.compose.foundation.text.selection.SelectionContainer { Text(message.content) }
                            }
                        }
                    }
                }
            }
        }
        if (state is ConversationState.Error) {
            Text((state as ConversationState.Error).message, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp))
            TextButton(onClick = { vm.engine.clearError() }) { Text("닫기") }
        }
        if (state is ConversationState.Processing) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("생각하고 있어요", Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { vm.cancelText() }) { Text("중지") }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onOpenCall) { Text(if (active) "진행 중인 통화" else "음성 대화") }
            Spacer(Modifier.weight(1f))
            Text(vm.engine.activeProvider.displayName, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f), placeholder = { Text("메시지를 입력하세요") },
                shape = RoundedCornerShape(24.dp), maxLines = 5)
            Button(onClick = { val text = input; input = ""; vm.sendText(text) },
                enabled = roomId != null && input.isNotBlank() && state !is ConversationState.Processing && !active,
                contentPadding = PaddingValues(horizontal = 18.dp), modifier = Modifier.height(56.dp)) { Text("전송") }
        }
    }
}
