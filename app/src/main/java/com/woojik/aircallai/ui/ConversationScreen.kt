package com.woojik.aircallai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.conversation.ConversationState
import com.woojik.aircallai.session.SessionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(vm: MainViewModel) {
    val state by vm.state.collectAsState()
    val transcript by vm.transcript.collectAsState()
    val sessionStatus by vm.sessionStatus.collectAsState()
    val muted by vm.sessionMuted.collectAsState()
    var input by remember { mutableStateOf("") }
    val sessionActive = sessionStatus == SessionStatus.Running || sessionStatus == SessionStatus.Paused

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("대화", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            stateLabel(state),
                            style = MaterialTheme.typography.labelMedium,
                            color = stateColor(state),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { vm.onMicTap() },
                            enabled = !sessionActive &&
                                (state is ConversationState.Idle || state is ConversationState.Error),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Text(if (sessionStatus == SessionStatus.Ended) "다시 말하기" else "말하기")
                        }
                        if (state is ConversationState.Speaking) {
                            OutlinedButton(onClick = { vm.onStopSpeaking() }) { Text("중지") }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("메시지를 입력하세요") },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            textStyle = MaterialTheme.typography.bodyLarge,
                        )
                        Button(
                            onClick = {
                                val text = input.trim()
                                input = ""
                                vm.sendText(text)
                            },
                            enabled = input.isNotBlank() && state !is ConversationState.Processing,
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                        ) { Text("전송", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state is ConversationState.Error) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = (state as ConversationState.Error).message,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { vm.engine.clearError() }) { Text("닫기") }
                    }
                }
            }

            if (sessionActive) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "음성 세션 실행 중",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(
                            modifier = Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (sessionStatus == SessionStatus.Paused) {
                                Button(onClick = { vm.resumeSession() }) { Text("재개") }
                            } else {
                                OutlinedButton(onClick = { vm.pauseSession() }) { Text("일시정지") }
                            }
                            OutlinedButton(onClick = { vm.toggleMute() }) {
                                Text(if (muted) "음소거 해제" else "음소거")
                            }
                            TextButton(onClick = { vm.endSession() }) { Text("종료") }
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(transcript) { message ->
                    ChatBubble(message)
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == ChatMessage.Role.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (isUser) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 5.dp,
                bottomEnd = if (isUser) 5.dp else 18.dp,
            ),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = if (isUser) "나" else "AirCall AI",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                )
                Text(
                    text = message.content,
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun stateColor(state: ConversationState) = when (state) {
    is ConversationState.Error -> MaterialTheme.colorScheme.error
    ConversationState.Listening, is ConversationState.Processing, is ConversationState.Speaking ->
        MaterialTheme.colorScheme.primary
    ConversationState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun stateLabel(state: ConversationState): String = when (state) {
    ConversationState.Idle -> "대기 중"
    ConversationState.Listening -> "듣고 있습니다"
    is ConversationState.Processing -> "생각 중"
    is ConversationState.Speaking -> "말하는 중"
    is ConversationState.Error -> "오류"
}
