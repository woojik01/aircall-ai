package com.woojik.aircallai.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.woojik.aircallai.R
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.call.callStatusOf
import com.woojik.aircallai.conversation.ConversationState
import com.woojik.aircallai.session.SessionStatus

@Composable
fun CallScreen(vm: MainViewModel, onExit: () -> Unit) {
    LaunchedEffect(vm) { vm.refreshProviderReadiness() }
    val state by vm.state.collectAsState()
    val session by vm.sessionStatus.collectAsState()
    val muted by vm.sessionMuted.collectAsState()
    val ready by vm.providerReady.collectAsState()
    val transcript by vm.transcript.collectAsState()
    val active = session == SessionStatus.Running || session == SessionStatus.Paused
    val label = callStatusOf(session, state, muted, ready).label
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Spacer(Modifier.height(18.dp))
        Image(painterResource(R.drawable.aircall_ai_icon), "AirCall AI",
            Modifier.size(144.dp).clip(RoundedCornerShape(44.dp)))
        Text(label, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        transcript.lastOrNull { it.role == ChatMessage.Role.ASSISTANT }?.let {
            Text(it.content, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
        if (!ready) Text("설정에서 로컬 모델을 적용하거나 클라우드 API를 연결해 주세요.")
        (state as? ConversationState.Error)?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
        if (active) {
            OutlinedButton(onClick = { if (session == SessionStatus.Paused) vm.resumeSession() else vm.pauseSession() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (session == SessionStatus.Paused) "대화 재개" else "일시정지") }
            OutlinedButton(onClick = { vm.toggleMute() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(if (muted) "음성 출력 켜기" else "음성 출력 음소거")
            }
            Button(onClick = { vm.endSession(); onExit() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("통화 종료") }
        } else {
            Button(onClick = { vm.onMicTap() }, enabled = ready && state !is ConversationState.Processing,
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) { Text("통화 시작") }
        }
        Text("다른 앱을 사용하는 동안에도 알림에서 대화를 제어할 수 있어요.",
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
