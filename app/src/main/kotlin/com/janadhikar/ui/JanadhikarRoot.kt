package com.janadhikar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.janadhikar.engine.Answer
import com.janadhikar.engine.CaptureState
import com.janadhikar.engine.EdgeStack
import com.janadhikar.engine.Session
import com.janadhikar.ui.theme.JanadhikarTheme
import com.janadhikar.ui.theme.Palette
import kotlinx.coroutines.flow.StateFlow

/** Which bare-act PDF page the in-app viewer should open. */
private data class PdfTarget(val asset: String, val page: Int, val title: String)

/** Sentinel id for the live, not-yet-archived conversation shown atop History. */
private const val CURRENT_SESSION_ID = Long.MIN_VALUE

/**
 * Root: the conversational assistant. A scrolling thread of questions and
 * grounded answers, with a recording overlay while the mic is live.
 */
@Composable
fun JanadhikarRoot(
    edgeStackFlow: StateFlow<EdgeStack?>,
    warmupError: StateFlow<String?>,
    warmupStage: StateFlow<String>,
    onVoiceRequested: () -> Unit,
) {
    JanadhikarTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Palette.Black) {
            // Pad for system bars + display cutout only — NOT the IME. The
            // keyboard is handled by imePadding() on the input bar alone, so the
            // keyboard height is never counted twice (which previously shoved the
            // input box into the middle of the screen and hid the conversation).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
            ) {
                val edgeStack by edgeStackFlow.collectAsState()
                val stack = edgeStack

                if (stack == null) {
                    val stage by warmupStage.collectAsState()
                    val error by warmupError.collectAsState()
                    ChatScreen(
                        turns = emptyList(),
                        engineReady = false,
                        onAsk = {},
                        onMic = {},
                        warmupStage = error ?: stage,
                        warmupFailed = error != null,
                    )
                    return@Box
                }

                val turns by stack.engine.conversation.collectAsState()
                val capture by stack.engine.capture.collectAsState()
                var showSettings by remember { mutableStateOf(false) }
                var showHistory by remember { mutableStateOf(false) }
                var showOverview by remember { mutableStateOf(false) }
                var pdfView by remember { mutableStateOf<PdfTarget?>(null) }

                if (showOverview) {
                    ConstitutionOverviewScreen(onBack = { showOverview = false })
                    return@Box
                }

                if (showHistory) {
                    val sessions by stack.engine.sessions.collectAsState()
                    // Show the CURRENT (unarchived) conversation live at the top,
                    // so History reflects it immediately — no need to start a new
                    // chat first for it to appear.
                    val currentSession = if (turns.any { it.answer is Answer.Grounded }) {
                        Session(
                            id = CURRENT_SESSION_ID,
                            title = "🟢 " + turns.firstOrNull()?.query.orEmpty().ifBlank { "Current chat" },
                            turns = turns,
                        )
                    } else {
                        null
                    }
                    HistoryScreen(
                        sessions = listOfNotNull(currentSession) + sessions,
                        onOpen = { s ->
                            // Tapping the current one just returns to it; past ones reopen.
                            if (s.id != CURRENT_SESSION_ID) stack.engine.openSession(s)
                            showHistory = false
                        },
                        onDelete = { s -> if (s.id != CURRENT_SESSION_ID) stack.engine.deleteSession(s) },
                        onBack = { showHistory = false },
                    )
                    return@Box
                }

                if (showSettings) {
                    val status by stack.status.collectAsState()
                    val usage by stack.engine.usageLog.collectAsState()
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    var model by remember { mutableStateOf(com.janadhikar.engine.ModelPreference.get(ctx)) }
                    SettingsScreen(
                        models = status.models(),
                        usage = usage,
                        onBack = { showSettings = false },
                        currentModel = model,
                        onSelectModel = { com.janadhikar.engine.ModelPreference.set(ctx, it); model = it },
                    )
                    return@Box
                }

                pdfView?.let { target ->
                    PdfViewerScreen(
                        assetName = target.asset,
                        targetPage = target.page,
                        title = target.title,
                        onBack = { pdfView = null },
                    )
                    return@Box
                }

                val modelPct by stack.modelProgress.collectAsState()
                ProvideMeaningToolbar(onExplain = stack.engine::explainSelection) {
                    ChatScreen(
                        turns = turns,
                        engineReady = true,
                        modelDownloadPercent = modelPct,
                        onAsk = stack.engine::ask,
                        onMic = onVoiceRequested,
                        onSettings = { showSettings = true },
                        onNewChat = stack.engine::clear,
                        onHistory = { showHistory = true },
                        onOverview = { showOverview = true },
                        onOpenPdf = { statute, page ->
                            pdfAssetFor(statute)?.let { pdfView = PdfTarget(it, page, statute) }
                        },
                    )
                }

                when (val c = capture) {
                    is CaptureState.Recording -> RecordingOverlay(
                        transcript = c.transcript,
                        elapsedMillis = c.elapsedMillis,
                        onStop = stack.engine::stopVoice,
                        onCancel = stack.engine::cancelVoice,
                    )
                    CaptureState.Idle -> Unit
                }
            }
        }
    }
}
