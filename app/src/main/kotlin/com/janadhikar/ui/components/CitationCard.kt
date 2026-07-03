package com.janadhikar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import android.widget.Toast
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.janadhikar.R
import com.janadhikar.input.AppLanguage
import com.janadhikar.memory.model.VerifiedCitation
import com.janadhikar.ui.theme.Palette

/**
 * The receipt: a white card of monospace, machine-verified fact. EVERY value
 * rendered here is a typed field from a database row (Rule 2) — this
 * composable takes a [VerifiedCitation] and must never accept free text.
 *
 * Tapping the card expands the exact verbatim statute text (the "source"),
 * so a citizen can read the actual law, not just its citation.
 */
@Composable
fun CitationCard(
    citation: VerifiedCitation,
    language: AppLanguage,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Palette.PaperWhite, RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .padding(20.dp)
            .testTag("citation_card"),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "⚖ " + stringResource(R.string.verified_record),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                ),
                color = Palette.InkBlack,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (expanded) "▲" else "▼ " + stringResource(R.string.read_full_text),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = Palette.InkBlack.copy(alpha = 0.55f),
            )
        }
        DashedDivider()

        val statuteName = when (language) {
            AppLanguage.ENGLISH -> citation.statuteName
            AppLanguage.HINDI -> citation.statuteNameHi
        }
        ReceiptRow(stringResource(R.string.statute_label), statuteName)
        val unitLabel = when (citation.unit) {
            "ARTICLE" -> stringResource(R.string.article_label)
            else -> stringResource(R.string.section_label)
        }
        ReceiptRow(unitLabel, citation.sectionNumber)
        citation.clause?.let { ReceiptRow(stringResource(R.string.clause_label), it) }
        ReceiptRow(stringResource(R.string.page_label), citation.pageNumber.toString())

        // The actual text of the law — a preview always visible (so the user
        // sees what's inside the section), expanding to the full text on tap.
        DashedDivider()
        val fullText = when (language) {
            AppLanguage.ENGLISH -> citation.verbatimTextEn
            AppLanguage.HINDI -> citation.verbatimTextHi
        }
        val preview = if (fullText.length > 200) fullText.take(200).trimEnd() + "…" else fullText
        Text(
            text = if (expanded) fullText else preview,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp, fontSize = 15.sp),
            color = Palette.InkBlack,
            modifier = Modifier.testTag("verbatim_text"),
        )
        Spacer(Modifier.height(6.dp))

        DashedDivider()
        ReceiptRow(stringResource(R.string.source_label), citation.sourceDocument, small = true)
        ReceiptRow(stringResource(R.string.compiled_label), citation.compilationDate, small = true)

        // Official source link — opens the India Code page in a browser so the
        // citizen can verify the exact text themselves. The app itself makes no
        // network call; launching a URL is the user's own action.
        val context = LocalContext.current
        if (citation.sourceUrl.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "🔗 " + stringResource(R.string.open_source),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold, fontSize = 14.sp,
                ),
                color = Color(0xFF0B57D0),
                modifier = Modifier
                    .clickable {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(citation.sourceUrl),
                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                    .padding(vertical = 6.dp)
                    .testTag("source_link"),
            )
        }
    }
}

@Composable
private fun ReceiptRow(label: String, value: String, small: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = if (small) 13.sp else 15.sp,
            ),
            color = Palette.InkBlack.copy(alpha = 0.6f),
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = if (small) FontWeight.Normal else FontWeight.Bold,
                fontSize = if (small) 13.sp else 18.sp,
            ),
            color = Palette.InkBlack,
        )
    }
}

@Composable
private fun DashedDivider() {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(17.dp),
    ) {
        drawLine(
            color = Palette.InkBlack.copy(alpha = 0.35f),
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
        )
    }
}
