package com.woojik.aircallai.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Lightweight, selectable Markdown. Never renders HTML or executes content. */
@Composable
fun ChatMarkdown(content: String) {
    val blocks = remember(content) { content.split("```") }
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            blocks.forEachIndexed { index, block ->
                if (index % 2 == 1) {
                    val code = block.substringAfter('\n', block).trimEnd()
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(code, Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
                            fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                    }
                } else block.trim().lines().forEach { line ->
                    if (line.isNotBlank()) {
                        val heading = line.takeWhile { it == '#' }.length.takeIf { it in 1..6 && line.getOrNull(it) == ' ' }
                        val table = line.trim().startsWith('|') && line.trim().endsWith('|')
                        if (!(table && line.matches(Regex("[| :\\-]+")))) {
                            val text = if (heading != null) line.drop(heading + 1)
                                else line.replace(Regex("^\\s*[-*] "), "• ")
                            Text(inlineMarkdown(text), modifier = if (table) Modifier.horizontalScroll(rememberScrollState()) else Modifier,
                                style = if (heading != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                                fontFamily = if (table) FontFamily.Monospace else null)
                        }
                    }
                }
            }
        }
    }
}

private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var start = 0
    Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`").findAll(text).forEach { match ->
        append(text.substring(start, match.range.first))
        val bold = match.groupValues[1].isNotEmpty()
        withStyle(if (bold) SpanStyle(fontWeight = FontWeight.Bold) else SpanStyle(fontFamily = FontFamily.Monospace)) {
            append(if (bold) match.groupValues[1] else match.groupValues[2])
        }
        start = match.range.last + 1
    }
    append(text.substring(start))
}
