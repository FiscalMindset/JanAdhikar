package com.janadhikar.engine

import com.janadhikar.input.AppLanguage
import com.janadhikar.llm.Directive
import com.janadhikar.memory.model.VerifiedCitation

/** What the agent status ticker shows during the Active state. */
enum class AgentPhase {
    LISTENING, //     mic open, waiting for speech
    TRANSCRIBING, //  whisper decoding the window
    SEARCHING, //     hybrid retrieval (vector + graph + relational)
    TRANSLATING, //   Gemma rewriting verbatim text as a directive
}

/**
 * The engine's state — maps 1:1 onto the three UI states:
 * [Idle] → Trigger, [Active] → Active, [Shield]/[NoStatute]/[Failure] → Resolution.
 *
 * Transitions are owned exclusively by [IncidentEngine]; the UI only observes.
 */
sealed interface IncidentState {

    /** Trigger screen: pulsing mic + typed-query field. */
    data object Idle : IncidentState

    /**
     * Live capture/processing. [transcript] grows as whisper re-decodes.
     * [isVoice] is true only for a microphone session — the foreground
     * microphone service is tied to this, so a typed query (isVoice = false)
     * never starts it (and never trips the FGS-microphone permission check).
     */
    data class Active(
        val transcript: String,
        val phase: AgentPhase,
        val elapsedMillis: Long,
        val isVoice: Boolean,
    ) : IncidentState

    /** Resolution — verified match. The Legal Shield. */
    data class Shield(
        val directive: Directive,
        val citation: VerifiedCitation,
        val related: List<VerifiedCitation>,
        val confidence: Float,
        val redirectedFromSuperseded: Boolean,
    ) : IncidentState

    /**
     * Resolution — the governed refusal (Rule 3). Renders
     * R.string.no_verified_statute verbatim. Carries NO partial results.
     */
    data object NoStatute : IncidentState

    /** Pipeline failure (mic denied, model missing). [reasonForLog] never rendered. */
    data class Failure(val reasonForLog: String) : IncidentState
}
