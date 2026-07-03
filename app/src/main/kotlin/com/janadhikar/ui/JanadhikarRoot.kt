package com.janadhikar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.janadhikar.engine.CaptureState
import com.janadhikar.engine.EdgeStack
import com.janadhikar.ui.theme.JanadhikarTheme
import com.janadhikar.ui.theme.Palette
import kotlinx.coroutines.flow.StateFlow

/** Which bare-act PDF page the in-app viewer should open. */
private data class PdfTarget(val asset: String, val page: Int, val title: String)

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
            Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
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
                var pdfView by remember { mutableStateOf<PdfTarget?>(null) }

                if (showHistory) {
                    val sessions by stack.engine.sessions.collectAsState()
                    HistoryScreen(
                        sessions = sessions,
                        onOpen = { stack.engine.openSession(it); showHistory = false },
                        onDelete = stack.engine::deleteSession,
                        onBack = { showHistory = false },
                    )
                    return@Box
                }

                if (showSettings) {
                    val status by stack.status.collectAsState()
                    val usage by stack.engine.usageLog.collectAsState()
                    SettingsScreen(
                        models = status.models(),
                        usage = usage,
                        onBack = { showSettings = false },
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

                ChatScreen(
                    turns = turns,
                    engineReady = true,
                    onAsk = stack.engine::ask,
                    onMic = onVoiceRequested,
                    onSettings = { showSettings = true },
                    onNewChat = stack.engine::clear,
                    onHistory = { showHistory = true },
                    onOpenPdf = { statute, page ->
                        pdfAssetFor(statute)?.let { pdfView = PdfTarget(it, page, statute) }
                    },
                )

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
