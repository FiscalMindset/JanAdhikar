package com.janadhikar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.janadhikar.R
import com.janadhikar.engine.Answer
import com.janadhikar.engine.Session
import com.janadhikar.ui.theme.Palette

/**
 * History: every past conversation, newest first. Tap to reopen it in the
 * chat; each row shows the opening question and how many exchanges it held.
 */
@Composable
fun HistoryScreen(
    sessions: List<Session>,
    onOpen: (Session) -> Unit,
    onDelete: (Session) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(Palette.Black)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "‹ " + stringResource(R.string.back),
                style = MaterialTheme.typography.titleLarge,
                color = Palette.DirectiveYellow,
                modifier = Modifier.clickable(onClick = onBack).testTag("history_back"),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.history),
                style = MaterialTheme.typography.titleLarge,
                color = Palette.White,
            )
        }

        if (sessions.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.DimGray,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(sessions, key = { it.id }) { session ->
                    val answered = session.turns.count { it.answer is Answer.Grounded }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Palette.NearBlack, RoundedCornerShape(12.dp))
                            .clickable { onOpen(session) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = session.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Palette.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.padding(2.dp))
                            Text(
                                text = "${session.turns.size} " + stringResource(R.string.messages) +
                                    " · $answered " + stringResource(R.string.answered_count),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                color = Palette.DimGray,
                            )
                        }
                        Text(
                            "🗑",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier
                                .clickable { onDelete(session) }
                                .padding(start = 12.dp)
                                .testTag("delete_session"),
                        )
                    }
                }
            }
        }
    }
}
