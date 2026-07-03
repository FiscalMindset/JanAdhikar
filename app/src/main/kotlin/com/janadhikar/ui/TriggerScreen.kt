package com.janadhikar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.janadhikar.R
import com.janadhikar.ui.components.PulsingMicButton
import com.janadhikar.ui.theme.Palette

/**
 * UI State 1 — Trigger. The mic owns ~50% of the screen; the typed-query field
 * below it is the equal-citizen text path into the same pipeline.
 */
@Composable
fun TriggerScreen(
    engineReady: Boolean,
    onStartVoice: () -> Unit,
    onSubmitText: (String) -> Unit,
    modifier: Modifier = Modifier,
    history: List<com.janadhikar.engine.IncidentState.Shield> = emptyList(),
    onHistoryClick: (com.janadhikar.engine.IncidentState.Shield) -> Unit = {},
    /** Red status line (warm-up failure, mic error). Never LLM/DB content. */
    statusMessage: String? = null,
) {
    var typed by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.app_name).uppercase(),
            style = MaterialTheme.typography.titleLarge,
            color = Palette.DimGray,
        )

        // ── The mic: ~50% of the screen, one giant target ──
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (engineReady) {
                PulsingMicButton(
                    onPress = onStartVoice,
                    modifier = Modifier.fillMaxSize(0.9f),
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Palette.DirectiveYellow)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.warming_up),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.DimGray,
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.trigger_hint_hold),
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.DimGray,
            textAlign = TextAlign.Center,
        )
        statusMessage?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.DangerRed,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("status_message"),
            )
        }
        Spacer(Modifier.height(20.dp))

        // ── Equal-citizen text path ──
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            enabled = engineReady,
            placeholder = { Text(stringResource(R.string.trigger_type_hint)) },
            textStyle = MaterialTheme.typography.bodyLarge,
            // Single line so the keyboard's Search/Enter key submits the query
            // instead of inserting a newline.
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmitText(typed) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Palette.DirectiveYellow,
                unfocusedBorderColor = Palette.DimGray,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("typed_query_field"),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onSubmitText(typed) },
            enabled = engineReady && typed.isNotBlank(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Palette.DirectiveYellow,
                contentColor = Palette.InkBlack,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp) // massive tap target
                .testTag("submit_text_button"),
        ) {
            Text(stringResource(R.string.trigger_submit), style = MaterialTheme.typography.titleLarge)
        }

        // ── Recent results (history) — tap to reopen without re-querying ──
        if (history.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.recent).uppercase(),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.DimGray,
                modifier = Modifier.align(Alignment.Start),
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history) { shield ->
                    val unit = if (shield.citation.unit == "ARTICLE") "Art." else "Sec."
                    AssistChip(
                        onClick = { onHistoryClick(shield) },
                        label = {
                            Text(
                                "$unit ${shield.citation.sectionNumber}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Palette.NearBlack,
                            labelColor = Palette.DirectiveYellow,
                        ),
                        modifier = Modifier.testTag("history_chip"),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
