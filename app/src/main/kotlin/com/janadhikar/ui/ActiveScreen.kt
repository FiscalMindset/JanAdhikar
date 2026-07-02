package com.janadhikar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.janadhikar.R
import com.janadhikar.engine.AgentPhase
import com.janadhikar.ui.theme.Palette

/**
 * UI State 2 — Active. Auto-scrolling large-font live transcript with the
 * agent status ticker, and one giant stop button.
 */
@Composable
fun ActiveScreen(
    transcript: String,
    phase: AgentPhase,
    elapsedMillis: Long,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    // Auto-scroll: always pin to the newest words.
    LaunchedEffect(transcript) { scroll.animateScrollTo(scroll.maxValue) }

    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {

        // ── REC header ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).background(Palette.RecordRed, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(
                text = "%02d:%02d".format(elapsedMillis / 60000, (elapsedMillis / 1000) % 60),
                style = MaterialTheme.typography.titleLarge,
                color = Palette.RecordRed,
            )
        }
        Spacer(Modifier.height(20.dp))

        // ── Live transcript, huge font ──
        Text(
            text = transcript.ifBlank { "…" },
            style = MaterialTheme.typography.headlineMedium,
            color = Palette.White,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .testTag("live_transcript"),
        )
        Spacer(Modifier.height(16.dp))

        // ── Agent status ticker ──
        val tickerText = when (phase) {
            AgentPhase.LISTENING -> stringResource(R.string.active_listening)
            AgentPhase.TRANSCRIBING -> stringResource(R.string.active_transcribing)
            AgentPhase.SEARCHING -> stringResource(R.string.active_searching)
            AgentPhase.TRANSLATING -> stringResource(R.string.active_translating)
        }
        Text(
            text = "⚙ $tickerText",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TickerGreen,
            modifier = Modifier.testTag("agent_ticker"),
        )
        Spacer(Modifier.height(16.dp))

        // ── Giant stop target ──
        Button(
            onClick = onStop,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Palette.AccentOrange,
                contentColor = Palette.InkBlack,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .testTag("stop_button"),
        ) {
            Text(stringResource(R.string.tap_to_stop), style = MaterialTheme.typography.titleLarge)
        }
    }
}
