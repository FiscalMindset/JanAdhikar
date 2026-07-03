package com.janadhikar.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.janadhikar.R
import com.janadhikar.ui.components.MarkdownText
import com.janadhikar.ui.theme.Palette

/**
 * A one-tap encyclopedic overview of the Constitution of India — dates, the
 * drafting committee, structure (original vs today), the three pillars, sources,
 * and key features. Established historical facts, curated as a reference card.
 */
@Composable
fun ConstitutionOverviewScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Palette.Black),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "‹ " + stringResourceBack(),
                    style = MaterialTheme.typography.titleLarge,
                    color = Palette.DirectiveYellow,
                    modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp),
                )
                Text("The Constitution of India", style = MaterialTheme.typography.titleLarge, color = Palette.White)
            }
        }

        // ── Drafting Committee Chairman (portrait placeholder) ──
        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ambedkar),
                        contentDescription = "Dr. B. R. Ambedkar",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .border(2.dp, Palette.DirectiveYellow, CircleShape),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Dr. B. R. Ambedkar", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = Palette.White)
                        Text("Chairman, Drafting Committee — the chief architect of the Constitution", style = small(), color = Palette.DimGray)
                    }
                }
            }
        }

        item { Section("📅  Key dates", DATES) }
        item { Section("🏛  Nature & making", NATURE) }

        // ── Structure: original vs current comparison table ──
        item {
            Card {
                Header("📊  Structure — 1950 vs today")
                Spacer(Modifier.size(8.dp))
                TableRow("", "At adoption (1950)", "Currently", header = true)
                STRUCTURE.forEach { (k, a, b) -> TableRow(k, a, b) }
            }
        }

        item { Card { Header("⚖️  The three pillars"); MarkdownText(PILLARS, Palette.PaperWhite) } }
        item { Section("🛡  Remedies & amendment", REMEDIES) }
        item { Card { Header("🌍  Sources — borrowed features"); MarkdownText(SOURCES, Palette.PaperWhite) } }
        item { Card { Header("✨  Salient features"); MarkdownText(FEATURES, Palette.PaperWhite) } }
        item {
            Text(
                "Tap ‹ Back to return. Ask the assistant about any article for the exact text.",
                style = small(), color = Palette.DimGray, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            )
        }
    }
}

@Composable private fun stringResourceBack() = androidx.compose.ui.res.stringResource(R.string.back)
@Composable private fun small() = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp)

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(Palette.ChatAssistant, RoundedCornerShape(14.dp))
            .border(1.dp, Palette.ChatAssistantEdge, RoundedCornerShape(14.dp))
            .padding(14.dp),
        content = content,
    )
}

@Composable private fun Header(t: String) =
    Text(t, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = Palette.Answered)

@Composable
private fun Section(title: String, body: String) = Card { Header(title); Spacer(Modifier.size(6.dp)); MarkdownText(body, Palette.PaperWhite) }

@Composable
private fun TableRow(k: String, a: String, b: String, header: Boolean = false) {
    val c = if (header) Palette.DirectiveYellow else Palette.PaperWhite
    val w = if (header) FontWeight.Bold else FontWeight.Normal
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(k, style = small().copy(fontWeight = FontWeight.Bold), color = Palette.DimGray, modifier = Modifier.weight(1.1f))
        Text(a, style = small().copy(fontWeight = w), color = c, modifier = Modifier.weight(1f))
        Text(b, style = small().copy(fontWeight = w), color = c, modifier = Modifier.weight(1f))
    }
}

// ── Curated reference content (established historical fact) ──

private val DATES = """
    - **Adopted:** 26 November 1949 *(Constitution Day / Samvidhan Divas)*
    - **Came into force:** 26 January 1950 *(Republic Day)*
    - **Time taken:** 2 years, 11 months, 18 days
    - **Constituent Assembly President:** Dr. Rajendra Prasad
""".trimIndent()

private val NATURE = """
    - **Nature:** written, the **lengthiest** constitution in the world; federal with a strong unitary bias; partly rigid, partly flexible.
    - **Original languages:** Hindi and English — the original copies were **hand-written & calligraphed** by Prem Behari Narain Raizada.
    - **Original length:** ~**251 pages**, ~145,000 words, signed by 284 members.
""".trimIndent()

private val STRUCTURE = listOf(
    Triple("Articles", "395", "~448"),
    Triple("Parts", "22", "25"),
    Triple("Schedules", "8", "12"),
    Triple("Appendices", "0", "2"),
    Triple("Amendments", "—", "106+"),
)

private val PILLARS = """
    - **Fundamental Rights** — Part III, **Articles 12–35** (six rights, enforceable in court).
    - **Directive Principles of State Policy** — Part IV, **Articles 36–51** (goals for governance; not enforceable).
    - **Fundamental Duties** — Part IVA, **Article 51A** (11 duties; added by the 42nd Amendment, 1976).
""".trimIndent()

private val REMEDIES = """
    - **Right to Constitutional Remedies — Article 32:** go directly to the Supreme Court if a Fundamental Right is violated. Dr. Ambedkar called it the *"heart and soul of the Constitution."* High Courts have similar power under Article 226.
    - **Amending power — Article 368:** Parliament can amend by special majority (some parts also need state ratification), limited by the Supreme Court's **"basic structure" doctrine**.
""".trimIndent()

private val SOURCES = """
    - **Government of India Act, 1935** — federal scheme, office of Governor, judiciary.
    - **United Kingdom** — parliamentary government, rule of law, single citizenship, cabinet system.
    - **United States** — Fundamental Rights, judicial review, independent judiciary, Preamble.
    - **Ireland** — Directive Principles of State Policy.
    - **Canada** — federation with a strong centre, residuary powers.
    - **Australia** — Concurrent List, freedom of trade.
    - **USSR** — Fundamental Duties, ideals of justice in the Preamble.
    - **France** — ideals of liberty, equality, fraternity.
""".trimIndent()

private val FEATURES = """
    - Sovereign, Socialist, Secular, Democratic **Republic** (Preamble).
    - Parliamentary form of government with a bicameral Parliament.
    - Independent judiciary with the power of judicial review.
    - Single citizenship and universal adult franchise.
    - Three-tier government (Centre, State, Panchayat/Municipality).
    - Emergency provisions and a strong yet flexible federal structure.
""".trimIndent()
