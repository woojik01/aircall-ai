package com.woojik.aircallai.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.woojik.aircallai.tools.ToolRequest

/**
 * PRD-06: WRITE Tool 작업 승인 다이얼로그.
 * 어떤 도구가 무엇을 하려는지 도구/액션/인자를 사용자에게 그대로 보여준다.
 */
@Composable
fun ToolApprovalDialog(
    request: ToolRequest,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDeny,
        title = { Text("Tool 작업 승인") },
        text = {
            Text(
                "도구 '" + request.toolName + "'가 '" + request.action + "' 작업을 실행하려 합니다.\n" +
                    "이 작업은 외부 서비스에 변경을 만들 수 있습니다(WRITE).\n" +
                    "인자: " + request.arguments.entries.joinToString(", ") { it.key + "=" + it.value },
            )
        },
        confirmButton = { TextButton(onClick = onApprove) { Text("승인") } },
        dismissButton = { TextButton(onClick = onDeny) { Text("거부") } },
    )
}
