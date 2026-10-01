package com.woojik.aircallai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woojik.aircallai.ai.local.LocalModelInfo
import com.woojik.aircallai.ai.local.LocalModelRegistry
import com.woojik.aircallai.ai.local.MediaPipeModelAdapter
import com.woojik.aircallai.ai.local.ModelDownloadManager
import com.woojik.aircallai.ai.local.ModelDownloadState
import com.woojik.aircallai.ai.local.downloadPercent
import com.woojik.aircallai.ai.local.formatSizeBytes
import com.woojik.aircallai.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 로컬 모델 갤러리 (Edge AI Gallery 방식).
 * 사용자가 모델을 선택해 다운로드하고 적용한다. 다운로드는 Wi-Fi 환경에서 수행을 권장.
 * 다운로드 상태는 모델별(id)로 관리되므로 한 모델의 진행률이 다른 카드에 표시되지 않는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalModelScreen(
    settings: SettingsRepository,
    adapter: MediaPipeModelAdapter,
    downloadManager: ModelDownloadManager,
    scope: CoroutineScope,
) {
    val states by downloadManager.states.collectAsState()
    var selectedId by remember { mutableStateOf(settings.localModelId()) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("로컬 모델") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "모델을 다운로드하면 인터넷 없이 기기에서만 대화할 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            items(LocalModelRegistry.models) { model ->
                ModelCard(
                    model = model,
                    downloaded = downloadManager.isDownloaded(model),
                    selected = selectedId == model.id,
                    // 모델별 상태만 이 카드에 전달한다.
                    downloadState = states[model.id] ?: ModelDownloadState.Idle,
                    onDownload = {
                        scope.launch { downloadManager.download(model) }
                    },
                    onDelete = { downloadManager.delete(model) },
                    onApply = {
                        settings.setLocalModelId(model.id)
                        selectedId = model.id
                    },
                    onUnapply = {
                        settings.setLocalModelId(null)
                        selectedId = null
                    },
                )
            }
            item {
                Text(
                    "모델 파일은 이 기기에만 저장되며 외부로 전송되지 않습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: LocalModelInfo,
    downloaded: Boolean,
    selected: Boolean,
    downloadState: ModelDownloadState,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onApply: () -> Unit,
    onUnapply: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(model.displayName, style = MaterialTheme.typography.titleMedium)
                if (selected) {
                    Text("적용됨", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                } else if (downloaded) {
                    Text("다운로드 완료", style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(
                model.description,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "크기 약 " + formatSizeBytes(model.sizeBytes) +
                    " · 권장 RAM " + (model.minRamMb / 1024) + "GB 이상",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )

            when (downloadState) {
                is ModelDownloadState.Downloading -> {
                    val percent = downloadPercent(downloadState.downloadedBytes, downloadState.totalBytes)
                    LinearProgressIndicator(
                        progress = percent / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    )
                    Text(
                        "다운로드 중 " + percent + "%",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                is ModelDownloadState.Failed -> {
                    Text(
                        downloadState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                else -> {}
            }

            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!downloaded) {
                    Button(
                        onClick = onDownload,
                        enabled = downloadState !is ModelDownloadState.Downloading,
                    ) {
                        Text("다운로드")
                    }
                } else {
                    if (selected) {
                        OutlinedButton(onClick = onUnapply) { Text("적용 해제") }
                    } else {
                        Button(onClick = onApply) { Text("적용") }
                    }
                    TextButton(onClick = onDelete, enabled = !selected) { Text("삭제") }
                }
            }
        }
    }
}
