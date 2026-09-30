package com.woojik.aircallai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.conversation.ConversationState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(vm: MainViewModel) {
    val state by vm.state.collectAsState()
    val transcript by vm.transcript.collectAsState()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }

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
                    text = "오류: ${(state as ConversationState.Error).message}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                TextButton(onClick = { vm.engine.clearError() }) { Text("재시도") }
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
                        text = if (message.role == ChatMessage.Role.USER) "나: ${message.content}" else "AI: ${message.content}",
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("메시지 입력 (PRD-03에서 음성 연결)") },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        val text = input
                        input = ""
                        scope.launch { vm.engine.submitUserMessage(text) }
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
