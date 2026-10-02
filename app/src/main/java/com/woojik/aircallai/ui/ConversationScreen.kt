package com.woojik.aircallai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        topBar = { TopAppBar(title = { Text("대화") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text = stateLabel(state),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            if (state is ConversationState.Error) {
                Text(
                    text = "오류: " + (state as ConversationState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                TextButton(onClick = { vm.engine.clearError() }) { Text("오류 닫기") }
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(transcript) { message ->
                    Text(
                        text = if (message.role == ChatMessage.Role.USER) "나: " + message.content else "AI: " + message.content,
                    )
                }
            }

            // PRD-05: 세션 제어 (알림의 일시정지/음소거/종료와 동일한 동작).
            if (sessionActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (sessionStatus == SessionStatus.Paused) {
                        Button(onClick = { vm.resumeSession() }) { Text("재개") }
                    } else {
                        OutlinedButton(onClick = { vm.pauseSession() }) { Text("일시정지") }
                    }
                    OutlinedButton(onClick = { vm.toggleMute() }) {
                        Text(if (muted) "음소거 해제" else "음소거")
                    }
                    OutlinedButton(onClick = { vm.endSession() }) { Text("세션 종료") }
                }
                Text(
                    text = "백그라운드 음성 세션 실행 중입니다. 기기 절전 설정에 따라 중단될 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // PRD-05: 말하기 = 백그라운드 음성 세션 시작 (권한은 필요한 시점에 요청)
                Button(
                    onClick = { vm.onMicTap() },
                    enabled = !sessionActive &&
                        (state is ConversationState.Idle || state is ConversationState.Error),
                ) { Text(if (sessionStatus == SessionStatus.Ended) "🎤 다
시 시작" else "🎤 말하기") }
                if (state is ConversationState.Speaking) {
                    // PRD-03: TTS 재생 중 중지 가능
                    Button(
                        onClick = { vm.onStopSpeaking() },
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Text("■ 중지") }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("메시지 입력") },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        val text = input
                        input = ""
                        vm.sendText(text)
                    },
                    enabled = input.isNotBlank() && state !is ConversationState.Processing,
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("전송") }
            }
        }
    }
}

private fun stateLabel(state: ConversationState): String = when (state) {
    ConversationState.Idle -> "대기 중"
    ConversationState.Listening -> "듣고 있습니다"
    is ConversationState.Processing -> "생각 중..."
    is ConversationState.Speaking -> "말하는 중..."
    is ConversationState.Error -> "오류"
}
