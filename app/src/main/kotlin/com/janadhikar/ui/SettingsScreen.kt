package com.janadhikar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.janadhikar.R
import com.janadhikar.engine.ChatEngine
import com.janadhikar.engine.EdgeStack
import com.janadhikar.ui.theme.Palette

/**
 * Settings / diagnostics: every model's identity and live state (loaded /
 * loading / missing), plus a usage log of past questions. 100% local.
 */
@Composable
fun SettingsScreen(
    models: List<EdgeStack.ModelInfo>,
    usage: List<ChatEngine.UsageEntry>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    currentModel: com.janadhikar.engine.ModelPreference.Choice =
        com.janadhikar.engine.ModelPreference.Choice.QWEN,
    onSelectModel: (com.janadhikar.engine.ModelPreference.Choice) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "‹ " + stringResource(R.string.back),
                style = MaterialTheme.typography.titleLarge,
                color = Palette.DirectiveYellow,
                modifier = Modifier.clickable(onClick = onBack).testTag("settings_back"),
            )
            Spacer(Modifier.size(16.dp))
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.titleLarge,
                color = Palette.White,
            )
        }
        Spacer(Modifier.size(16.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                SectionLabel("Answer model")
                ModelChoiceRow(
                    title = "Qwen 2.5 1.5B  ·  best for Hindi",
                    subtitle = "Downloads once (~1 GB). Strong multilingual. Slower on old phones.",
                    selected = currentModel == com.janadhikar.engine.ModelPreference.Choice.QWEN,
                    onClick = { onSelectModel(com.janadhikar.engine.ModelPreference.Choice.QWEN) },
                )
                ModelChoiceRow(
                    title = "Gemma 3 (1B / 4B)  ·  faster",
                    subtitle = "Hardware-accelerated, quicker on low-end CPUs. Needs the .task file.",
                    selected = currentModel == com.janadhikar.engine.ModelPreference.Choice.GEMMA,
                    onClick = { onSelectModel(com.janadhikar.engine.ModelPreference.Choice.GEMMA) },
                )
                Text(
                    "Switching takes effect when you reopen the app.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                    color = Palette.DimGray,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
                SectionLabel(stringResource(R.string.models_on_device))
            }
            items(models) { m -> ModelRow(m) }

            item {
                Spacer(Modifier.size(20.dp))
                SectionLabel(stringResource(R.string.usage_log) + " (${usage.size})")
                if (usage.isEmpty()) {
                    Text(
                        stringResource(R.string.no_usage_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.DimGray,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }
            items(usage) { e -> UsageRow(e) }
        }
    }
}

@Composable
private fun ModelChoiceRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (selected) "◉" else "○",
            style = MaterialTheme.typography.titleLarge,
            color = if (selected) Palette.TickerGreen else Palette.DimGray,
            modifier = Modifier.padding(end = 12.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Palette.White)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp), color = Palette.DimGray)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 1.5.sp),
        color = Palette.DimGray,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun ModelRow(m: EdgeStack.ModelInfo) {
    val (dot, color, word) = when (m.status) {
        EdgeStack.ModelStatus.READY -> Triple("●", Palette.TickerGreen, "Loaded")
        EdgeStack.ModelStatus.LOADING -> Triple("◐", Palette.AccentOrange, "Loading")
        EdgeStack.ModelStatus.UNAVAILABLE -> Triple("○", Palette.DangerRed, "Missing")
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .background(Palette.NearBlack, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$dot ", color = color, style = MaterialTheme.typography.bodyLarge)
            Text(m.label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = Palette.White, modifier = Modifier.weight(1f))
            Text(word, style = MaterialTheme.typography.bodyMedium, color = color)
        }
        Text(m.type, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp), color = Palette.DimGray)
        Text(
            "${m.fileName} · ${m.approxSizeMb} MB",
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
            color = Palette.DimGray,
        )
    }
}

@Composable
private fun UsageRow(e: ChatEngine.UsageEntry) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(e.query, style = MaterialTheme.typography.bodyMedium, color = Palette.White)
        Text(
            buildString {
                append(if (e.outcome == "answered") "✓ " else "✗ ")
                append(e.citation)
                append("  ·  ")
                append(e.model)
                append("  ·  ")
                append("${e.elapsedMillis} ms")
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
            color = if (e.outcome == "answered") Palette.TickerGreen else Palette.DimGray,
        )
    }
}
