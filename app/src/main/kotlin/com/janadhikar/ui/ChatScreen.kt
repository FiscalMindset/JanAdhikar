package com.janadhikar.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.janadhikar.R
import com.janadhikar.engine.Answer
import com.janadhikar.engine.Turn
import com.janadhikar.input.AppLanguage
import com.janadhikar.ui.components.CitationCard
import com.janadhikar.ui.components.MarkdownText
import com.janadhikar.ui.theme.Palette

/**
 * The conversation: an offline legal assistant. Questions and grounded answers
 * accumulate in a scrolling thread; a follow-up appends below, it never
 * replaces the screen. Input (text + mic) sits in a persistent bottom bar.
 */
@Composable
fun ChatScreen(
    turns: List<Turn>,
    engineReady: Boolean,
    onAsk: (String) -> Unit,
    onMic: () -> Unit,
    modifier: Modifier = Modifier,
    warmupStage: String = "",
    warmupFailed: Boolean = false,
    onSettings: () -> Unit = {},
    onNewChat: () -> Unit = {},
    onHistory: () -> Unit = {},
    onOpenPdf: (statuteName: String, page: Int) -> Unit = { _, _ -> },
) {
    val listState = rememberLazyListState()
    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(turns.size - 1)
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopBar(onNewChat = onNewChat, onSettings = onSettings, onHistory = onHistory, showNewChat = turns.isNotEmpty())
        if (turns.isEmpty()) {
            EmptyState(engineReady, warmupStage, warmupFailed, onExample = onAsk, modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(turns, key = { it.id }) { turn -> TurnItem(turn, onOpenPdf) }
            }
        }
        InputBar(engineReady = engineReady, onAsk = onAsk, onMic = onMic)
    }
}

@Composable
private fun TopBar(onNewChat: () -> Unit, onSettings: () -> Unit, onHistory: () -> Unit, showNewChat: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = Palette.DimGray,
        )
        Spacer(Modifier.weight(1f))
        if (showNewChat) {
            Text(
                text = "✎ " + stringResource(R.string.new_chat),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.DirectiveYellow,
                modifier = Modifier.clickable(onClick = onNewChat).testTag("new_chat").padding(6.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = "🕘",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.clickable(onClick = onHistory).testTag("history_button").padding(6.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "⚙",
            style = MaterialTheme.typography.titleLarge,
            color = Palette.DimGray,
            modifier = Modifier.clickable(onClick = onSettings).testTag("settings_button").padding(6.dp),
        )
    }
}

@Composable
private fun EmptyState(
    engineReady: Boolean,
    warmupStage: String,
    warmupFailed: Boolean,
    onExample: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 30.sp),
            color = Palette.DirectiveYellow,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.chat_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = Palette.DimGray,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.size(24.dp))
        if (!engineReady) {
            // Explicit warm-up status instead of a vague spinner: shows the
            // current stage, or a clear, actionable error if a model is missing.
            if (!warmupFailed) {
                CircularProgressIndicator(color = Palette.DirectiveYellow)
                Spacer(Modifier.size(12.dp))
            }
            Text(
                text = warmupStage.ifBlank { stringResource(R.string.warming_up) },
                style = MaterialTheme.typography.bodyMedium,
                color = if (warmupFailed) Palette.DangerRed else Palette.DimGray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.testTag("warmup_status"),
            )
        } else {
            listOf(R.string.example_1, R.string.example_2, R.string.example_3).forEach { ex ->
                val text = stringResource(ex)
                Text(
                    text = "💬  $text",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.DirectiveYellow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(Palette.NearBlack, RoundedCornerShape(10.dp))
                        .clickable { onExample(text) }
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .testTag("example_$ex"),
                )
            }
        }
    }
}

@Composable
private fun TurnItem(turn: Turn, onOpenPdf: (String, Int) -> Unit = { _, _ -> }) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // ── user question (right) ──
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                text = turn.query,
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.White,
                modifier = Modifier
                    .wrapContentWidth()
                    .background(Palette.ChatUser, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        Spacer(Modifier.size(8.dp))
        // ── assistant answer (left) ──
        when (val a = turn.answer) {
            Answer.Thinking -> ThinkingRow()
            Answer.NoStatute -> AnswerBubble {
                Text(
                    text = stringResource(R.string.no_verified_statute),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.DangerRed,
                    modifier = Modifier.testTag("refusal_text"),
                )
            }
            is Answer.Grounded -> GroundedAnswer(a, onOpenPdf)
        }
    }
}

@Composable
private fun GroundedAnswer(a: Answer.Grounded, onOpenPdf: (String, Int) -> Unit = { _, _ -> }) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedMsg = stringResource(R.string.copied)
    AnswerBubble {
        // Streaming explanation (Gemma), selectable. While streaming, show a
        // caret; if nothing has streamed yet, show a small "writing" hint.
        if (a.explanation.text.isBlank() && a.streaming) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Palette.DirectiveYellow, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.writing), style = MaterialTheme.typography.bodyMedium, color = Palette.DimGray)
            }
        } else {
            // Rendered Markdown (bold/bullets) — live during streaming too, so
            // there are never raw **asterisks**; a caret marks streaming.
            MarkdownText(
                markdown = a.explanation.text + if (a.streaming) "  ▋" else "",
                color = if (a.explanation.isVerbatimFallback) Palette.PaperWhite else Palette.White,
                modifier = Modifier.testTag("answer_text"),
            )
        }
        if (!a.streaming && a.explanation.text.isNotBlank()) {
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                var copied by remember(a.explanation.text) { mutableStateOf(false) }
                LaunchedEffect(copied) { if (copied) { kotlinx.coroutines.delay(1500); copied = false } }
                CopyChip(if (copied) "✓ " + stringResource(R.string.copied) else stringResource(R.string.copy)) {
                    // Copy via the platform clipboard directly (reliable across ROMs),
                    // with clear in-UI feedback since some ROMs suppress toasts.
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Janadhikar", a.explanation.text))
                    copied = true
                }
                MetaChip(a)
            }
        }
        if (a.redirectedFromSuperseded) {
            Spacer(Modifier.size(6.dp))
            Text("↻ " + stringResource(R.string.superseded_redirect), style = MaterialTheme.typography.bodyMedium, color = Palette.TickerGreen)
        }
        // Citations come AFTER the answer, and only once it is complete — the
        // "verified record" should support the answer, not precede it. A
        // plain-meaning answer (word lookup) has no citations — skip the block.
        if (!a.streaming && a.citations.isNotEmpty()) {
            Spacer(Modifier.size(12.dp))
            Text(
                text = stringResource(R.string.based_on).uppercase(),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp, letterSpacing = 1.sp),
                color = Palette.DimGray,
            )
            Spacer(Modifier.size(6.dp))
            a.citations.forEach { citation ->
                CitationCard(citation = citation, language = a.explanation.language, onOpenPdf = onOpenPdf)
                Spacer(Modifier.size(8.dp))
            }
            MetaBlock(a)
        }
    }
}

/** Full metadata: engine (+ what it means), section, confidence, time, source. */
@Composable
private fun MetaBlock(a: Answer.Grounded) {
    val primary = a.citations.firstOrNull()
    val engine = a.explanation.modelId
    // Plain-English note on WHERE the answer came from and how trustworthy.
    val engineNote = when {
        engine.contains("curated") ->
            "Hand-verified answer written from the law itself — not AI-generated."
        a.explanation.isVerbatimFallback ->
            "The exact words of the law, shown directly (no AI rephrasing)."
        engine.contains("Gemma") ->
            "Plain-language explanation by the on-device AI, grounded only in the cited law."
        else -> null
    }
    val rows = buildList {
        add("Answer engine" to engine)
        engineNote?.let { add("What this means" to it) }
        primary?.let {
            val unit = if (it.unit == "ARTICLE") "Article" else "Section"
            add("Primary provision" to "$unit ${it.sectionNumber} — ${it.statuteName.substringBefore(",")}")
            add("Page in source" to "p.${it.pageNumber} of ${it.sourceDocument}")
            it.clause?.let { cl -> add("Clause" to cl) }
        }
        add("Provisions cited" to a.citations.joinToString(", ") {
            (if (it.unit == "ARTICLE") "Art. " else "S. ") + it.sectionNumber
        })
        add("Grounding" to "100% offline · verified statute database")
        add("Match confidence" to "%.0f%%".format(a.confidence * 100))
        if (a.explanation.generationMillis > 0) add("Generation time" to "${a.explanation.generationMillis} ms")
        if (a.explanation.approxTokens > 0) add("Approx. tokens" to a.explanation.approxTokens.toString())
        add("Language" to if (a.explanation.language == AppLanguage.HINDI) "Hindi" else "English")
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.NearBlack, RoundedCornerShape(8.dp))
            .border(1.dp, Palette.ChatAssistantEdge, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        rows.forEach { (k, v) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                Text(k, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp), color = Palette.DimGray, modifier = Modifier.width(120.dp))
                Text(v, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp), color = Palette.DimGray.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Inline metadata chip: model · time · confidence. */
@Composable
private fun MetaChip(a: Answer.Grounded) {
    val meta = buildString {
        append(a.explanation.modelId.substringBefore(" (").ifBlank { "verbatim" })
        if (a.explanation.generationMillis > 0) append(" · ${a.explanation.generationMillis / 1000}s")
        append(" · ${"%.0f".format(a.confidence * 100)}%")
    }
    Text(
        text = meta,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
        color = Palette.DimGray,
    )
}

@Composable
private fun AnswerBubble(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Palette.ChatAssistant, RoundedCornerShape(16.dp))
                .border(1.dp, Palette.ChatAssistantEdge, RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            // A small "verified assistant" header so the answer reads as a card.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Palette.Answered, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    color = Palette.Answered,
                )
            }
            Spacer(Modifier.size(10.dp))
            content()
        }
    }
}

@Composable
private fun ThinkingRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            color = Palette.DirectiveYellow,
            strokeWidth = 2.dp,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.thinking), style = MaterialTheme.typography.bodyMedium, color = Palette.DimGray)
    }
}

@Composable
private fun CopyChip(label: String, onClick: () -> Unit) {
    Text(
        text = "⧉ $label",
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
        color = Palette.DimGray,
        modifier = Modifier
            .border(1.dp, Palette.DimGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("copy_chip"),
    )
}

@Composable
private fun InputBar(engineReady: Boolean, onAsk: (String) -> Unit, onMic: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Black)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            enabled = engineReady,
            placeholder = { Text(stringResource(R.string.ask_anything)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                if (typed.isNotBlank()) { onAsk(typed); typed = "" }
            }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Palette.DirectiveYellow,
                unfocusedBorderColor = Palette.DimGray,
            ),
            modifier = Modifier.weight(1f).testTag("chat_input"),
        )
        Spacer(Modifier.width(8.dp))
        MicButton(enabled = engineReady, onClick = onMic)
    }
}

@Composable
private fun MicButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .background(if (enabled) Palette.DirectiveYellow else Palette.NearBlack, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag("mic_button"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = stringResource(R.string.mic_button),
            tint = if (enabled) Palette.InkBlack else Palette.DimGray,
            modifier = Modifier.size(26.dp),
        )
    }
}

/**
 * Full-screen recording overlay while the mic is live: live transcript, big
 * stop target, cancel.
 */
@Composable
fun RecordingOverlay(
    transcript: String,
    elapsedMillis: Long,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulse = rememberInfiniteTransition(label = "rec")
    val alpha by pulse.animateFloat(
        1f, 0.3f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a",
    )
    Column(modifier = modifier.fillMaxSize().background(Palette.Black).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).background(Palette.RecordRed.copy(alpha = alpha), CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(
                "%02d:%02d".format(elapsedMillis / 60000, (elapsedMillis / 1000) % 60),
                style = MaterialTheme.typography.titleLarge, color = Palette.RecordRed,
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.cancel),
                style = MaterialTheme.typography.titleLarge,
                color = Palette.DimGray,
                modifier = Modifier.clickable(onClick = onCancel).testTag("cancel_button"),
            )
        }
        Spacer(Modifier.size(20.dp))
        Text(
            text = transcript.ifBlank { stringResource(R.string.active_listening) },
            style = MaterialTheme.typography.headlineMedium,
            color = if (transcript.isBlank()) Palette.DimGray else Palette.White,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("live_transcript"),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Palette.AccentOrange, RoundedCornerShape(16.dp))
                .clickable(onClick = onStop)
                .padding(vertical = 22.dp)
                .testTag("stop_button"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.tap_to_stop),
                style = MaterialTheme.typography.titleLarge, color = Palette.InkBlack, fontWeight = FontWeight.Bold,
            )
        }
    }
}
