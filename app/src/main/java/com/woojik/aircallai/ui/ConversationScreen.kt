package com.woojik.aircallai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.conversation.ConversationState
import com.woojik.aircallai.session.CallStatus
import com.woojik.aircallai.session.SessionStatus
import com.woojik.aircallai.session.formatCallDuration
import kotlinx.coroutines.delay

/**
 * PRD-07 통화형 메인 화면.
 * - 상태는 색 + 텍스트 라벨로 표시하고(색상만으로 구분하지 않음), 변경 시 TalkBack에 알린다.
 * - 음소거/일시정지/종료는 큰 버튼과 명확한 텍스트로 제공한다.
 * - 대화 상태는 Service/Repository가 소유하므로 화면 회전·복귀 후에도 유지된다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(vm: MainViewModel) {
    val state by vm.state.collectAsState()
    val transcript by vm.transcript.collectAsState()
    val sessionStatus by vm.sessionStatus.collectAsState()
    val muted by vm.sessionMuted.collectAsState()
    val callStatus = CallStatus.from(state, sessionStatus)
    val sessionActive = sessionStatus == SessionStatus.Running || sessionStatus == SessionStatus.Paused

    var showTranscript by rememberSaveable { mutableStateOf(false) }
    var showKeyboard by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    var startedAt by rememberSaveable { mutableStateOf(0L) }
    var elapsed by remember { mutableStateOf(0L) }

    LaunchedEffect(sessionActive) {
        if (!sessionActive) return@LaunchedEffect
        if (startedAt == 0L) startedAt = System.currentTimeMillis()
        while (true) {
            elapsed = (System.currentTimeMillis() - startedAt) / 1000
            delay(1000)
        }
    }
    LaunchedEffect(sessionStatus) {
        if (sessionStatus == SessionStatus.Ended || sessionStatus == SessionStatus.Inactive) startedAt = 0L
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(vm.aiName(), modifier = Modifier.semantics { heading() })
                        Text(
                            if (sessionActive) "통화 중 · " + formatCallDuration(elapsed) else "AirCall AI",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            StatusIndicator(callStatus, muted)

            if (state is ConversationState.Error) {
                Text(
                    text = (state as ConversationState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
                TextButton(onClick = { vm.engine.clearError() }) { Text("다시 시도") }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            ) {
                if (showTranscript) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(transcript) { TranscriptLine(it, maxLines = Int.MAX_VALUE) }
                    }
                } else {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        transcript.lastOrNull { it.role == ChatMessage.Role.USER }?.let { TranscriptLine(it, 2) }
                        transcript.lastOrNull { it.role == ChatMessage.Role.ASSISTANT }?.let { TranscriptLine(it, 4) }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showTranscript = !showTranscript }) {
                    Text(if (showTranscript) "기록 닫기" else "대화 기록")
                }
                TextButton(onClick = { showKeyboard = !showKeyboard }) {
                    Text(if (showKeyboard) "키보드 닫기" else "텍스트 입력")
                }
            }

            if (showKeyboard) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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

            if (state is ConversationState.Speaking) {
                OutlinedButton(
                    onClick = { vm.onStopSpeaking() },
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .heightIn(min = 48.dp),
                ) { Text("말 끊기") }
            }

            CallControls(
                sessionActive = sessionActive,
                paused = sessionStatus == SessionStatus.Paused,
                muted = muted,
                canStart = state is ConversationState.Idle || state is ConversationState.Error,
                restart = sessionStatus == SessionStatus.Ended,
                onStart = { vm.onMicTap() },
                onPauseToggle = { if (sessionStatus == SessionStatus.Paused) vm.resumeSession() else vm.pauseSession() },
                onMuteToggle = { vm.toggleMute() },
                onEnd = { vm.endSession() },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusIndicator(status: CallStatus, muted: Boolean) {
    val color = statusColor(status)
    val description = status.label + if (muted) ", 음소거됨" else ""
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics {
            contentDescription = "현재 상태: $description"
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.15f),
            border = androidx.compose.foundation.BorderStroke(4.dp, color),
            modifier = Modifier.size(160.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    status.label,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (muted) {
            AssistChip(
                onClick = {},
                label = { Text("음소거됨 · AI 음성이 재생되지 않습니다") },
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun TranscriptLine(message: ChatMessage, maxLines: Int) {
    val speaker = if (message.role == ChatMessage.Role.USER) "나" else "AI"
    Text(
        text = "$speaker: ${message.content}",
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = if (message.role == ChatMessage.Role.USER) {
            MaterialTheme.typography.bodyMedium
        } else {
            MaterialTheme.typography.bodyLarge
        },
    )
}

@Composable
private fun CallControls(
    sessionActive: Boolean,
    paused: Boolean,
    muted: Boolean,
    canStart: Boolean,
    restart: Boolean,
    onStart: () -> Unit,
    onPauseToggle: () -> Unit,
    onMuteToggle: () -> Unit,
    onEnd: () -> Unit,
) {
    val big = Modifier.heightIn(min = 64.dp)
    if (!sessionActive) {
        Button(
            onClick = onStart,
            enabled = canStart,
            modifier = big
                .fillMaxWidth()
                .padding(top = 8.dp)
                .semantics { contentDescription = "음성 통화 시작" },
        ) { Text(if (restart) "다시 통화하기" else "통화 시작", style = MaterialTheme.typography.titleMedium) }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onMuteToggle,
            modifier = big.weight(1f).semantics { contentDescription = if (muted) "음소거 해제" else "음소거" },
        ) { Text(if (muted) "음소거 해제" else "음소거", textAlign = TextAlign.Center) }
        OutlinedButton(
            onClick = onPauseToggle,
            modifier = big.weight(1f).semantics { contentDescription = if (paused) "통화 재개" else "통화 일시정지" },
        ) { Text(if (paused) "재개" else "일시정지", textAlign = TextAlign.Center) }
        Button(
            onClick = onEnd,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            modifier = big.weight(1f).semantics { contentDescription = "통화 종료" },
        ) { Text("종료", textAlign = TextAlign.Center) }
    }
}

private fun statusColor(status: CallStatus): Color = when (status) {
    CallStatus.LISTENING -> Color(0xFF2E7D32)
    CallStatus.THINKING -> Color(0xFFF9A825)
    CallStatus.SPEAKING -> Color(0xFF1565C0)
    CallStatus.PAUSED -> Color(0xFF757575)
    CallStatus.OFFLINE, CallStatus.ERROR -> Color(0xFFC62828)
    CallStatus.READY, CallStatus.ENDED -> Color(0xFF546E7A)
}
