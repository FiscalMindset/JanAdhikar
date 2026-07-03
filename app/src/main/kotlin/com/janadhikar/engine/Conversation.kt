package com.janadhikar.engine

import com.janadhikar.llm.Directive
import com.janadhikar.memory.model.VerifiedCitation

/**
 * One exchange in the conversation: the user's question and the grounded
 * answer. Follow-ups append new turns — the thread scrolls like a chat, it
 * never replaces the screen.
 */
data class Turn(
    val id: Long,
    val query: String,
    val answer: Answer,
)

/** The assistant's reply to one [Turn]. */
sealed interface Answer {

    /** Still retrieving / explaining. */
    data object Thinking : Answer

    /**
     * A grounded answer. [explanation] is the LLM's plain-language meaning +
     * summary (or verbatim law text as the safe fallback). [citations] are the
     * verified sections (facts from the database, never the LLM).
     */
    data class Grounded(
        val explanation: Directive,
        val citations: List<VerifiedCitation>,
        val confidence: Float,
        val redirectedFromSuperseded: Boolean,
        /** True while Gemma is still streaming tokens into [explanation]. */
        val streaming: Boolean = false,
    ) : Answer

    /** The governed refusal (Rule 3) — no verified statute for this question. */
    data object NoStatute : Answer
}

/** Transient microphone-capture overlay, separate from the persistent thread. */
sealed interface CaptureState {
    data object Idle : CaptureState
    data class Recording(val transcript: String, val elapsedMillis: Long) : CaptureState
}
