package com.woojik.aircallai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woojik.aircallai.tools.MemoryEntry
import com.woojik.aircallai.tools.MemoryStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The user and AI edit the same global store; room switches never replace it. */
@Composable
fun MemoryScreen(store: MemoryStore) {
    val entries by store.entries.collectAsState()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var originalText by rememberSaveable { mutableStateOf<String?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var editorError by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<MemoryEntry?>(null) }

    suspend fun reload() {
        loading = true
        error = null
        try { store.refresh() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "기억을 불러오지 못했습니다. 다시 시도해 주세요." }
        finally { loading = false }
    }
    LaunchedEffect(store) { reload() }

    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("모든 채팅방의 AI가 함께 사용하는 기억입니다.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = {
            editId = null; originalText = null; draft = ""; editorError = null; editorOpen = true
        }, enabled = !loading && !busy && error == null, modifier = Modifier.fillMaxWidth()) {
            Text("기억 추가")
        }
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("기억 검색") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { scope.launch { reload() } }, enabled = !loading) { Text("다시 읽기") }
        }
        val visible = entries.asReversed().filter { it.text.contains(query.trim(), ignoreCase = true) }
        if (!loading && error == null && visible.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(if (query.isBlank()) "저장된 기억이 없습니다." else "검색 결과가 없습니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(visible, key = { it.id }) { entry ->
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(entry.text, style = MaterialTheme.typography.bodyLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                editId = entry.id; originalText = entry.text; draft = entry.text
                                editorError = null; editorOpen = true
                            }, enabled = !busy && !loading && error == null) { Text("수정") }
                            TextButton(onClick = { deleting = entry }, enabled = !busy && !loading && error == null) {
                                Text("삭제", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (editorOpen) AlertDialog(
        modifier = Modifier.imePadding(),
        onDismissRequest = { if (!busy) editorOpen = false },
        title = { Text(if (editId == null) "기억 추가" else "기억 수정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = draft, onValueChange = { draft = it; editorError = null },
                    label = { Text("기억할 내용") }, minLines = 3, maxLines = 7,
                    enabled = !busy, isError = editorError != null, modifier = Modifier.fillMaxWidth())
                editorError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && draft.isNotBlank(), onClick = {
                if (draft.trim().toByteArray(Charsets.UTF_8).size > 2048) {
                    editorError = "기억이 너무 깁니다. 내용을 짧게 정리해 주세요."
                } else {
                    busy = true
                    scope.launch {
                        try {
                            store.save(draft, editId, originalText)
                            editorOpen = false
                        } catch (e: CancellationException) { throw e }
                        catch (e: IllegalStateException) {
                            editorError = "기억이 변경되었거나 읽을 수 없습니다. 다시 열어 주세요."
                            try { store.refresh() } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                        } catch (e: IllegalArgumentException) { editorError = e.message ?: "기억 내용을 확인해 주세요." }
                        catch (_: Exception) { editorError = "기억을 저장하지 못했습니다. 다시 시도해 주세요." }
                        finally { busy = false }
                    }
                }
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = { editorOpen = false }, enabled = !busy) { Text("취소") } },
    )

    deleting?.let { entry -> AlertDialog(
        onDismissRequest = { if (!busy) deleting = null },
        title = { Text("기억을 삭제할까요?") },
        text = { Text("모든 채팅방에서 이 기억을 사용하지 않게 됩니다.") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                try { store.delete(entry.id, entry.text); deleting = null }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    deleting = null
                    error = "기억이 변경되었거나 삭제하지 못했습니다. 다시 읽어 주세요."
                } finally { busy = false }
            }
        }) { Text("삭제", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleting = null }, enabled = !busy) { Text("취소") } },
    ) }
}
