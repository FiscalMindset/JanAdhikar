package com.janadhikar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.janadhikar.R
import com.janadhikar.engine.EdgeStack
import com.janadhikar.engine.IncidentState
import com.janadhikar.sos.SosController
import com.janadhikar.ui.theme.JanadhikarTheme
import com.janadhikar.ui.theme.Palette
import kotlinx.coroutines.flow.StateFlow

/**
 * Root composable: observes the engine state machine and renders exactly one
 * of the three UI states. Navigation IS the state machine — there is no
 * NavController to drift out of sync with the engine.
 */
@Composable
fun JanadhikarRoot(
    edgeStackFlow: StateFlow<EdgeStack?>,
    warmupError: StateFlow<String?>,
    sos: SosController,
    onVoiceRequested: () -> Unit,
) {
    JanadhikarTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Palette.Black) {
            // safeDrawingPadding keeps ALL content clear of the status bar (top)
            // and the gesture/navigation bar (bottom) — without it, buttons sit
            // under the nav bar and are untappable, and text runs under the
            // status bar / notch.
            Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                val edgeStack by edgeStackFlow.collectAsState()
                val stack = edgeStack

                if (stack == null) {
                    val error by warmupError.collectAsState()
                    TriggerScreen(
                        engineReady = false,
                        onStartVoice = {},
                        onSubmitText = {},
                        statusMessage = error?.let {
                            stringResource(R.string.engine_assets_missing) + "\n" + it
                        },
                    )
                    return@Box
                }

                val state by stack.engine.state.collectAsState()
                val history by stack.engine.history.collectAsState()

                // Arm/disarm the SOS countdown in lockstep with Resolution.
                LaunchedEffect(state) {
                    when (state) {
                        is IncidentState.Shield, IncidentState.NoStatute -> sos.arm()
                        IncidentState.Idle -> sos.disarm()
                        else -> Unit
                    }
                }

                when (val s = state) {
                    is IncidentState.Idle -> TriggerScreen(
                        engineReady = true,
                        onStartVoice = onVoiceRequested,
                        onSubmitText = stack.engine::submitTypedQuery,
                        history = history,
                        onHistoryClick = stack.engine::showFromHistory,
                    )
                    is IncidentState.Active -> ActiveScreen(
                        transcript = s.transcript,
                        phase = s.phase,
                        elapsedMillis = s.elapsedMillis,
                        onStop = stack.engine::stopAndResolve,
                        onCancel = stack.engine::cancel,
                    )
                    is IncidentState.Shield -> ShieldScreen(
                        shield = s,
                        sos = sos,
                        onDone = stack.engine::cancel,
                    )
                    IncidentState.NoStatute -> NoStatuteScreen(
                        sos = sos,
                        onDone = stack.engine::cancel,
                    )
                    is IncidentState.Failure -> {
                        // Mic/pipeline failure: back to Trigger with the text path
                        // still usable. reasonForLog is never rendered.
                        TriggerScreen(
                            engineReady = true,
                            onStartVoice = onVoiceRequested,
                            onSubmitText = stack.engine::submitTypedQuery,
                            history = history,
                            onHistoryClick = stack.engine::showFromHistory,
                            statusMessage = stringResource(R.string.mic_error),
                        )
                    }
                }
            }
        }
    }
}
