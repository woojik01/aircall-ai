package com.woojik.aircallai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** PRD-07: 통화(Call)이 음성 대화의 기본 진입점이다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenCall: () -> Unit,
    onOpenConversation: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("AirCall AI") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(onClick = onOpenCall) { Text("📞 통화") }
            OutlinedButton(
                onClick = onOpenConversation,
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("💬 텍스트 대화") }
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("설정") }
        }
    }
}
