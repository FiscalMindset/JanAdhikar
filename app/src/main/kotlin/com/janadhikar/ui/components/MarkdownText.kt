package com.janadhikar.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Minimal, dependency-free Markdown renderer for the assistant's answers so
 * they read like a real chatbot, not raw `**asterisks**`. Supports:
 *   **bold**, *italic*, `code`, bullet lists (-, *, •), numbered lists,
 *   ### headings, and paragraph breaks.
 *
 * Selectable (long-press to copy). Deliberately tiny — the model output is
 * short prose, not a document.
 */
@Composable
fun MarkdownText(
    markdown: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val base = LocalTextStyle.current.copy(color = color)
    SelectionContainer(modifier = modifier) {
        Column {
            val lines = markdown.trim().split("\n")
            for ((index, raw) in lines.withIndex()) {
                val line = raw.trimEnd()
                when {
                    line.isBlank() -> Spacer(Modifier.height(6.dp))
                    line.startsWith("### ") -> HeadingLine(line.removePrefix("### "), base)
                    line.startsWith("## ") -> HeadingLine(line.removePrefix("## "), base)
                    BULLET.containsMatchIn(line) -> BulletLine("•", line.replaceFirst(BULLET, ""), base)
                    NUMBERED.containsMatchIn(line) -> {
                        val n = NUMBERED.find(line)!!.groupValues[1]
                        BulletLine("$n.", line.replaceFirst(NUMBERED, ""), base)
                    }
                    else -> Text(inline(line), style = base)
                }
                if (index < lines.size - 1 && line.isNotBlank()) Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun HeadingLine(text: String, base: TextStyle) {
    Text(
        inline(text),
        style = base.copy(fontWeight = FontWeight.Bold, fontSize = base.fontSize * 1.1f),
    )
}

@Composable
private fun BulletLine(marker: String, text: String, base: TextStyle) {
    Row {
        Text(marker, style = base.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.width(8.dp))
        Text(inline(text.trim()), style = base)
    }
}

private val BULLET = Regex("""^\s*[-*•]\s+""")
private val NUMBERED = Regex("""^\s*(\d+)[.)]\s+""")

/** Inline spans: **bold**, *italic*, `code`. */
private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else { append(text[i]); i++ }
            }
            text[i] == '*' && i + 1 < text.length && text[i + 1] != ' ' -> {
                val end = text.indexOf('*', i + 1)
                if (end > 0) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            else -> { append(text[i]); i++ }
        }
    }
}
