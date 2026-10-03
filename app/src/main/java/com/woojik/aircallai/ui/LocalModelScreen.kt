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
import androidx.compose.material3.Switch
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
import com.woojik.aircallai.ai.local.LiteRtModelAdapter
import com.woojik.aircallai.ai.local.ModelDownloadManager
import com.woojik.aircallai.ai.local.ModelDownloadState
import com.woojik.aircallai.ai.local.downloadPercent
import com.woojik.aircallai.ai.local.formatSizeBytes
import com.woojik.aircallai.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import com.woojik.aircallai.ai.provider.AIProviderException
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
    adapter: LiteRtModelAdapter,
    downloadManager: ModelDownloadManager,
    scope: CoroutineScope,
    onModelChanged: () -> Unit,
) {
    val states by downloadManager.states.collectAsState()
    val runtimeStatus by adapter.runtimeStatus.collectAsState()
    var useGpu by remember { mutableStateOf(settings.localUseGpu()) }
    var selectedId by remember { mutableStateOf(settings.localModelId()) }
    var applying by remember { mutableStateOf(false) }
    var hasLegacyFiles by remember { mutableStateOf(downloadManager.hasLegacyFiles()) }
    var status by remember { mutableStateOf<String?>(null) }

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
                    "모델을 다운로드하고 적용하면 AI 응답을 기기에서 생성합니다. 음성 인식·출력의 네트워크 사용 여부는 기기의 음성 서비스에 따라 다릅니다.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("GPU 가속")
                    Switch(checked = useGpu, enabled = !applying, onCheckedChange = { enabled ->
                        applying = true
                        scope.launch {
                            try {
                                adapter.setGpuEnabled(enabled)
                                useGpu = enabled
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: AIProviderException) {
                                status = e.message
                            } finally { applying = false }
                        }
                    })
                }
                Text("GPU 사용에 실패하면 CPU로 자동 전환합니다.", style = MaterialTheme.typography.bodySmall)
                Text(runtimeStatus, style = MaterialTheme.typography.bodySmall)
            }
            item {
                if (hasLegacyFiles) {
                    Text("이전 웹용 모델은 사용할 수 없습니다. 삭제하면 새 모델을 위한 저장 공간을 확보할 수 있습니다.")
                    OutlinedButton(onClick = {
                        val deleted = downloadManager.deleteLegacyFiles()
                        hasLegacyFiles = downloadManager.hasLegacyFiles()
                        status = if (deleted) "이전 모델 파일을 삭제했습니다." else "이전 모델 파일을 삭제하지 못했습니다."
                    }) { Text("이전 모델 파일 삭제") }
                }
                if (applying) Text("모델을 로드하는 중입니다. 잠시 기다려 주세요.")
                status?.let { Text(it) }
                val selected = LocalModelRegistry.byId(selectedId)
                if (selectedId != null && (selected == null || !downloadManager.isDownloaded(selected))) {
                    Text("선택한 모델을 다시 다운로드하고 적용해 주세요. 이전 웹용 모델 파일은 사용할 수 없습니다.")
                }
            }
            items(LocalModelRegistry.models) { model ->
                ModelCard(
                    model = model,
                    downloaded = downloadManager.isDownloaded(model),
                    selected = selectedId == model.id && downloadManager.isDownloaded(model),
                    busy = applying,
                    // 모델별 상태만 이 카드에 전달한다.
                    downloadState = states[model.id] ?: ModelDownloadState.Idle,
                    onDownload = {
                        scope.launch { downloadManager.download(model) }
                    },
                    onDelete = {
                        if (!downloadManager.delete(model)) status = "모델을 삭제하지 못했습니다. 다시 시도해 주세요."
                    },
                    onApply = {
                        applying = true
                        status = null
                        scope.launch {
                            try {
                                adapter.apply(model)
                                selectedId = model.id
                                status = "모델을 로드하고 적용했습니다."
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: AIProviderException) {
                                status = e.message
                            } finally {
                                applying = false
                                onModelChanged()
                            }
                        }
                    },
                    onUnapply = {
                        applying = true
                        scope.launch {
                            try {
                                adapter.unapply()
                                selectedId = null
                                status = null
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: AIProviderException) {
                                status = e.message
                            } finally {
                                applying = false
                                onModelChanged()
                            }
                        }
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
    busy: Boolean,
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
                    " · RAM 검사 기준 " + (model.minRamMb / 1024) + "GiB 이상",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )

            when (downloadState) {
                is ModelDownloadState.Downloading -> {
                    val percent = downloadPercent(downloadState.downloadedBytes, downloadState.totalBytes)
                    LinearProgressIndicator(
                        progress = { percent / 100f },
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
                ModelDownloadState.Verifying -> Text("모델 파일 검증 중…")
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
                        enabled = !busy && downloadState !is ModelDownloadState.Downloading &&
                            downloadState !is ModelDownloadState.Verifying,
                    ) {
                        Text("다운로드")
                    }
                } else {
                    if (selected) {
                        OutlinedButton(onClick = onUnapply, enabled = !busy) { Text("적용 해제") }
                    } else {
                        Button(onClick = onApply, enabled = !busy) { Text("적용") }
                    }
                    TextButton(onClick = onDelete, enabled = !selected && !busy) { Text("삭제") }
                }
            }
        }
    }
}
