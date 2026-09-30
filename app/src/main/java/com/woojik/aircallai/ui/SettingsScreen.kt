package com.woojik.aircallai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woojik.aircallai.settings.SettingsRepository
import kotlinx.coroutines.launch

/**
 * PRD-04: 설정에서 AI Mode(Local/Cloud)를 고르면 다음 대화부터 적용된다.
 * API Key는 여기서 입력받아 PRD-02 CredentialManager(Keystore 암호화)에만 저장된다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SettingsRepository,
    onSaveApiKey: suspend (String) -> Unit,
    onDeleteApiKey: suspend () -> Unit,
) {
    var mode by remember { mutableStateOf(settings.aiProviderMode()) }
    var apiKeyInput by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
                    selected = mode == SettingsRepository.MODE_LOCAL,
                    onClick = {
                        settings.setAiProviderMode(SettingsRepository.MODE_LOCAL)
                        mode = SettingsRepository.MODE_LOCAL
                    },
                )
                Text("Local")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = mode == SettingsRepository.MODE_CLOUD,
                    onClick = {
                        settings.setAiProviderMode(SettingsRepository.MODE_CLOUD)
                        mode = SettingsRepository.MODE_CLOUD
                    },
                )
                Text("Cloud")
            }
            Text(
                "모드 변경은 다음 대화부터 적용됩니다.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text("Cloud API Key", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("sk-...") },
                singleLine = true,
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            onSaveApiKey(apiKeyInput)
                            apiKeyInput = ""
                            status = "Key가 안전하게 저장되었습니다 (기기 암호화)."
                        }
                    },
                    enabled = apiKeyInput.isNotBlank(),
                ) { Text("저장") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            onDeleteApiKey()
                            status = "Key가 삭제되었습니다."
                        }
                    },
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("삭제") }
            }
            status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Text(
                "Key는 기기의 Android Keystore로 암호화되어 저장되며 서버로 전송되지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
