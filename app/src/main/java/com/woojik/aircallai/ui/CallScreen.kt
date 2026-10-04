package com.woojik.aircallai.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.call.CallStatus
import com.woojik.aircallai.call.callControlsOf
import com.woojik.aircallai.call.callStatusOf
import com.woojik.aircallai.call.muteLabel
import com.woojik.aircallai.call.pauseLabel
import com.woojik.aircallai.conversation.ConversationState
import com.woojik.aircallai.session.SessionStatus

/**
 * PRD-07 메인 통화 화면.
 * - AI 이름/상태, 음성 입력·출력 상태, 음소거/일시정지/종료 컨트롤
 * - 불필요한 UI 요소는 최소화
 * - 버튼 64dp 이상, 상태는 글리프+텍스트, 접근성 라벨 제공
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreen(vm: MainViewModel, onExit: () -> Unit) {
    LaunchedEffect(vm) { vm.refreshProviderReadiness() }
    val conversation by vm.state.collectAsState()
    val sessionStatus by vm.sessionStatus.collectAsState()
    val muted by vm.sessionMuted.collectAsState()
    val providerReady by vm.providerReady.collectAsState()
    val transcript by vm.transcript.collectAsState()

    val status = callStatusOf(sessionStatus, conversation, muted, providerReady)
    val sessionActive = sessionStatus == SessionStatus.Running || sessionStatus == SessionStatus.Paused
    val controls = callControlsOf(status, sessionActive)
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 대화 상대/AI 이름
            Text(
                text = "AirCall AI",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))

            // AI 상태 표시
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(144.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = status.glyph, fontSize = 56.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            // PRD-07 접근성: 색상이 아니라 글리프+텍스트로 상태를 구분한다.
            Text(
                text = status.glyph + " " + status.label,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )

            // 음성 출력(최근 AI 발화) 요약 — 통화 화면을 채팅 목록으로 만들지 않는다.
            val lastAssistant = transcript.lastOrNull { it.role == ChatMessage.Role.ASSISTANT }?.content
            if (!lastAssistant.isNullOrEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = lastAssistant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .padding(horizontal = 8.dp),
                )
            }

            if (!providerReady) {
                Text("설정에서 로컬 모델을 다운로드·적용하거나 Cloud API Key를 등록해 주세요.")
            }

            if (conversation is ConversationState.Error) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "오류: " + (conversation as ConversationState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = { vm.engine.clearError() }) { Text("오류 닫기") }
            }

            Spacer(Modifier.height(48.dp))

            if (sessionActive) {
                // 음소거 / 일시정지
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { vm.toggleMute() },
                        enabled = controls.canMute,
                        modifier = Modifier
                            .heightIn(min = 64.dp)
                            .semantics { contentDescription = muteLabel(muted) },
                    ) {
                        Text(text = (if (muted) "🔇 " else "🎙 ") + muteLabel(muted))
                    }
                    val paused = sessionStatus == SessionStatus.Paused
                    OutlinedButton(
                        onClick = {
                            if (paused) vm.resumeSession() else vm.pauseSession()
                        },
                        enabled = controls.canPause,
                        modifier = Modifier
                            .heightIn(min = 64.dp)
                            .semantics { contentDescription = pauseLabel(paused) },
                    ) {
                        Text(text = (if (paused) "▶ " else "⏸ ") + pauseLabel(paused))
                    }
                }
                Spacer(Modifier.height(24.dp))
                // 통화 종료
                Button(
                    onClick = {
                        vm.endSession()
                        onExit()
                    },
                    enabled = controls.canEnd,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .heightIn(min = 64.dp)
                        .semantics { contentDescription = "통화 종료" },
                ) {
                    Text(text = "📴 통화 종료")
                }
            } else {
                // 통화 시작 (마이크/알림 권한은 필요한 시점에 요청)
                Button(
                    onClick = { vm.onMicTap() },
                    enabled = providerReady,
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .heightIn(min = 64.dp)
                        .semantics { contentDescription = "통화 시작" },
                ) {
                    Text(text = "📞 통화 시작")
                }
            }
        }

        // PRD-07 플로팅/백그라운드 제어: 오버레이는 선택 사항이며 권한 거부 시 알림 제어로 동작한다.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!AndroidSettings.canDrawOverlays(context)) {
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(
                                AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + context.packageName),
                            ),
                        )
                    },
                ) {
                    Text(
                        text = "백그라운드 중에는 시스템 알림으로 제어합니다. 오버레이 컨트롤 허용(선택)",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Text(
                    text = "백그라운드 제어: 시스템 알림 + 오버레이",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
