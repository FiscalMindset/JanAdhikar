package com.janadhikar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.janadhikar.R
import com.janadhikar.engine.IncidentState
import com.janadhikar.sos.SosController
import com.janadhikar.ui.components.CitationCard
import com.janadhikar.ui.components.CancelSlider
import com.janadhikar.ui.theme.Palette

/**
 * UI State 3 — Resolution (match). Laid out like a chat answer: the user's
 * question, then the directive, the verified citations, an expandable Details
 * block (which model ran, time, tokens, confidence), a follow-up input so the
 * next question can be asked right here, and the SOS panel.
 */
@Composable
fun ShieldScreen(
    shield: IncidentState.Shield,
    sos: SosController,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onSubmitText: (String) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            QueryBubble(shield.query)
            Spacer(Modifier.height(12.dp))

            // ── The directive — an answer card, not a wall of orange ──
            val accent = if (shield.directive.isVerbatimFallback) Palette.AccentOrange else Palette.DirectiveYellow
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Palette.NearBlack, RoundedCornerShape(14.dp))
                    .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(16.dp),
            ) {
                Text(
                    text = shield.directive.text,
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 24.sp, lineHeight = 32.sp),
                    color = accent,
                    modifier = Modifier.testTag("directive_text"),
                )
                if (shield.redirectedFromSuperseded) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "↻ " + stringResource(R.string.superseded_redirect),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TickerGreen,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            // ── Verified citations (typed DB fields only — Rule 2) ──
            CitationCard(citation = shield.citation, language = shield.directive.language)
            if (shield.related.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.related_provisions).uppercase(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.DimGray,
                )
                shield.related.forEach {
                    Spacer(Modifier.height(8.dp))
                    CitationCard(citation = it, language = shield.directive.language)
                }
            }
            Spacer(Modifier.height(12.dp))

            DetailsBlock(shield)
            Spacer(Modifier.height(14.dp))
            SosPanel(sos)
            Spacer(Modifier.height(12.dp))
        }
        // ── Sticky bottom bar: ask a follow-up, or finish ──
        FollowUpBar(onSubmitText = onSubmitText, onDone = onDone)
    }
}

/**
 * UI State 3 — Resolution, refusal variant (Rule 3). The governed refusal and
 * NOTHING else (no partial matches). A follow-up input lets the user re-ask.
 */
@Composable
fun NoStatuteScreen(
    sos: SosController,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onSubmitText: (String) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.no_verified_statute),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 24.sp, lineHeight = 32.sp),
                color = Palette.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, Palette.DangerRed, RoundedCornerShape(12.dp))
                    .padding(20.dp)
                    .testTag("refusal_text"),
            )
            Spacer(Modifier.height(20.dp))
            SosPanel(sos)
        }
        FollowUpBar(onSubmitText = onSubmitText, onDone = onDone)
    }
}

@Composable
private fun QueryBubble(query: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text = query,
            style = MaterialTheme.typography.bodyLarge,
            color = Palette.White,
            modifier = Modifier
                .wrapContentWidth()
                .background(Color(0xFF1E2A38), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("query_bubble"),
        )
    }
}

/** Expandable "Details" — which model ran, time, tokens, confidence, embedder. */
@Composable
private fun DetailsBlock(shield: IncidentState.Shield) {
    var open by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.NearBlack, RoundedCornerShape(10.dp))
            .clickable { open = !open }
            .padding(12.dp)
            .testTag("details_block"),
    ) {
        Text(
            text = (if (open) "▲ " else "▼ ") + stringResource(R.string.details),
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.DimGray,
        )
        AnimatedVisibility(visible = open) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                DetailRow(stringResource(R.string.detail_model), shield.directive.modelId)
                DetailRow(stringResource(R.string.detail_time), "${shield.directive.generationMillis} ms")
                DetailRow(stringResource(R.string.detail_tokens), shield.directive.approxTokens.toString())
                DetailRow(stringResource(R.string.detail_confidence), "%.0f%%".format(shield.confidence * 100))
                DetailRow(stringResource(R.string.detail_embedder), EMBEDDER_LABEL)
                if (shield.citation.sourceUrl.isNotBlank()) {
                    DetailRow(stringResource(R.string.detail_source_url), shield.citation.sourceUrl)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
            color = Palette.DimGray,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
            color = Palette.White,
        )
    }
}

/** Bottom bar: ask a follow-up question (chat-style) or finish. */
@Composable
private fun FollowUpBar(onSubmitText: (String) -> Unit, onDone: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Black)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            placeholder = { Text(stringResource(R.string.ask_followup)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (typed.isNotBlank()) { onSubmitText(typed); typed = "" }
            }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Palette.DirectiveYellow,
                unfocusedBorderColor = Palette.DimGray,
            ),
            modifier = Modifier.weight(1f).testTag("followup_field"),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.done),
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.DimGray,
            modifier = Modifier
                .clickable(onClick = onDone)
                .padding(8.dp)
                .testTag("done_button"),
        )
    }
}

@Composable
private fun SosPanel(sos: SosController, modifier: Modifier = Modifier) {
    val state by sos.state.collectAsState()
    Column(modifier = modifier.fillMaxWidth().testTag("sos_panel")) {
        when (val s = state) {
            is SosController.SosState.Counting -> {
                Text(
                    text = "🆘 " + stringResource(R.string.sos_countdown, s.secondsLeft),
                    style = MaterialTheme.typography.titleLarge,
                    color = Palette.DangerRed,
                )
                Spacer(Modifier.height(10.dp))
                CancelSlider(onCancelled = sos::cancel)
            }
            SosController.SosState.Sent -> SosStatusLine("✓ " + stringResource(R.string.sos_sent), Palette.TickerGreen)
            SosController.SosState.Cancelled -> SosStatusLine(stringResource(R.string.sos_cancelled), Palette.DimGray)
            SosController.SosState.Failed -> SosStatusLine("✗ " + stringResource(R.string.sos_failed), Palette.DangerRed)
            SosController.SosState.NotConfigured -> SosStatusLine(stringResource(R.string.sos_not_configured), Palette.DimGray)
            SosController.SosState.Disarmed -> Unit
        }
    }
}

@Composable
private fun SosStatusLine(text: String, color: Color) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
}

private const val EMBEDDER_LABEL = "MiniLM-L12-v2 (384-d, on-device)"
