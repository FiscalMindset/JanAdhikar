package com.janadhikar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.janadhikar.R
import com.janadhikar.engine.IncidentState
import com.janadhikar.input.AppLanguage
import com.janadhikar.sos.SosController
import com.janadhikar.ui.components.CancelSlider
import com.janadhikar.ui.components.CitationCard
import com.janadhikar.ui.theme.Palette

/**
 * UI State 3 — Resolution, match variant: The Legal Shield.
 * Top: the directive, massive yellow/orange. Middle: the receipt. Bottom: SOS.
 */
@Composable
fun ShieldScreen(
    shield: IncidentState.Shield,
    sos: SosController,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // ── TOP: the directive ──
        Text(
            text = shield.directive.text,
            style = MaterialTheme.typography.displayLarge,
            color = if (shield.directive.isVerbatimFallback) Palette.AccentOrange else Palette.DirectiveYellow,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("directive_text"),
        )
        if (shield.directive.isVerbatimFallback) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.verbatim_shown),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.DimGray,
            )
        }
        if (shield.redirectedFromSuperseded) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "↻ " + stringResource(R.string.superseded_redirect),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TickerGreen,
            )
        }
        Spacer(Modifier.height(24.dp))

        // ── MIDDLE: the receipt (typed DB fields only — Rule 2) ──
        CitationCard(citation = shield.citation, language = shield.directive.language)

        if (shield.related.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.related_provisions).uppercase(),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.DimGray,
            )
            shield.related.forEach { related ->
                Spacer(Modifier.height(8.dp))
                CitationCard(citation = related, language = shield.directive.language)
            }
        }
        Spacer(Modifier.height(24.dp))

        // ── BOTTOM: SOS + done ──
        SosPanel(sos)
        Spacer(Modifier.height(16.dp))
        DoneButton(onDone)
    }
}

/**
 * UI State 3 — Resolution, refusal variant (Rule 3). Renders the governed
 * refusal string and NOTHING else: no partial matches, no suggestions.
 */
@Composable
fun NoStatuteScreen(
    sos: SosController,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(3.dp, Palette.DangerRed, RoundedCornerShape(12.dp))
                .padding(24.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.no_verified_statute),
                style = MaterialTheme.typography.displayLarge,
                color = Palette.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("refusal_text"),
            )
        }
        Spacer(Modifier.height(24.dp))
        SosPanel(sos)
        Spacer(Modifier.height(16.dp))
        DoneButton(onDone)
    }
}

/** Emergency SOS SMS status + cancel slider — shared by both Resolution variants. */
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
private fun SosStatusLine(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text = text, style = MaterialTheme.typography.titleLarge, color = color)
}

@Composable
private fun DoneButton(onDone: () -> Unit) {
    Button(
        onClick = onDone,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.NearBlack,
            contentColor = Palette.White,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .testTag("done_button"),
    ) {
        Text(stringResource(R.string.done), style = MaterialTheme.typography.titleLarge)
    }
}
