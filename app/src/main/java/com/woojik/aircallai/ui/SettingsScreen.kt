package com.woojik.aircallai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woojik.aircallai.ai.provider.ProviderType

/**
 * Settings skeleton. AI mode selection persists locally; credentials UI arrives in PRD-02/04.
 */
@Composable
fun SettingsScreen() {
    var mode by remember { mutableStateOf(ProviderType.LOCAL) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("설정") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Text("AI Mode", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = mode == ProviderType.LOCAL,
                    onClick = { mode = ProviderType.LOCAL },
                )
                Text("Local")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = mode == ProviderType.CLOUD,
                    onClick = { mode = ProviderType.CLOUD },
                )
                Text("Cloud")
            }
            Text(
                "API Key 입력 UI는 PRD-02(CredentialManager)에서 추가됩니다.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
