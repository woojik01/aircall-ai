package com.woojik.aircallai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woojik.aircallai.tools.ToolRequest

@Composable
fun ToolApprovalDialog(request: ToolRequest, onApprove: (ToolRequest) -> Unit, onDeny: () -> Unit) {
    var arguments by remember(request) { mutableStateOf(request.arguments.toMap()) }
    AlertDialog(
        onDismissRequest = onDeny,
        title = { Text("도구 작업 승인") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("실행할 내용을 확인해 주세요.",
                    style = MaterialTheme.typography.bodyMedium)
                Text(com.woojik.aircallai.tools.ToolLabels.action(request.toolName, request.action), style = MaterialTheme.typography.titleSmall)
                HorizontalDivider()
                arguments.forEach { (key, value) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(com.woojik.aircallai.tools.ToolLabels.argument(key), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(value, { arguments = arguments + (key to it) },
                            modifier = Modifier, minLines = if (key in setOf("body", "text")) 3 else 1)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApprove(request.copy(arguments = arguments.toMap())) }) { Text("이 내용으로 실행") } },
        dismissButton = { TextButton(onClick = onDeny) { Text("취소") } },
    )
}
