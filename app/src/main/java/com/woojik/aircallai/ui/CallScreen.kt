package com.woojik.aircallai.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.woojik.aircallai.R
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.call.callStatusOf
import com.woojik.aircallai.conversation.ConversationState
import com.woojik.aircallai.session.SessionStatus

@Composable
fun CallScreen(vm: MainViewModel, onExit: () -> Unit,
    onStartSession: () -> Unit = { vm.onMicTap() }) {
    LaunchedEffect(vm) { vm.refreshProviderReadiness() }
    val state by vm.state.collectAsState()
    val session by vm.sessionStatus.collectAsState()
    val muted by vm.sessionMuted.collectAsState()
    val ready by vm.providerReady.collectAsState()
    val transcript by vm.transcript.collectAsState()
    val active = session == SessionStatus.Running || session == SessionStatus.Paused
    val label = callStatusOf(session, state, muted, ready).label
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Spacer(Modifier.height(12.dp))
        Box(Modifier.size(200.dp).background(Brush.radialGradient(listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.06f),
            androidx.compose.ui.graphics.Color.Transparent)), CircleShape),
            contentAlignment = Alignment.Center) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp) {
                Image(painterResource(R.drawable.aircall_ai_icon), "AirCall AI",
                    Modifier.size(132.dp).clip(CircleShape))
            }
        }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            Text(label, Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        }
        Text(vm.engine.activeProvider.displayName, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        transcript.lastOrNull { it.role == ChatMessage.Role.ASSISTANT }?.let {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AirCall", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(it.content, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        if (!ready) Text("설정에서 AI를 연결해 주세요.", textAlign = TextAlign.Center)
        (state as? ConversationState.Error)?.let {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(it.message)
                    TextButton(onClick = { vm.engine.clearError() }) { Text("닫기") }
                }
            }
        }
        if (active) {
            OutlinedButton(onClick = { if (session == SessionStatus.Paused) vm.resumeSession() else vm.pauseSession() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(if (session == SessionStatus.Paused) "대화 재개" else "일시정지")
            }
            OutlinedButton(onClick = { vm.toggleMute() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(if (muted) "음성 출력 켜기" else "음성 출력 음소거")
            }
            Button(onClick = { vm.endSession(); onExit() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError)) {
                AirCallIcon(UiSymbol.Phone)
                Spacer(Modifier.width(8.dp))
                Text("통화 종료")
            }
        } else {
            Button(onClick = onStartSession, enabled = ready && state !is ConversationState.Processing,
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
                AirCallIcon(UiSymbol.Phone)
                Spacer(Modifier.width(8.dp))
                Text("통화 시작")
            }
        }
    }
}
