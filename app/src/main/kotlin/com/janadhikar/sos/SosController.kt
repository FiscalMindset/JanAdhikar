package com.janadhikar.sos

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Countdown-then-dispatch for the emergency SMS shown at the bottom of every
 * Resolution screen. Armed automatically on Resolution; the visible countdown
 * plus cancel slider is the user's control — SMS only fires if they let it.
 */
class SosController(
    private val scope: CoroutineScope,
    private val dispatcher: Dispatcher,
    private val countdownSeconds: Int = DEFAULT_COUNTDOWN_SECONDS,
) {

    /** Seam over [SmsDispatcher] for tests. */
    interface Dispatcher {
        fun isConfigured(): Boolean

        /** Sends to the configured contact. Returns false on failure. */
        fun dispatch(): Boolean
    }

    sealed interface SosState {
        data object Disarmed : SosState
        data object NotConfigured : SosState
        data class Counting(val secondsLeft: Int) : SosState
        data object Sent : SosState
        data object Cancelled : SosState
        data object Failed : SosState
    }

    private val _state = MutableStateFlow<SosState>(SosState.Disarmed)
    val state: StateFlow<SosState> = _state.asStateFlow()

    private var countdownJob: Job? = null

    /** Called when the engine reaches a Resolution state. Idempotent. */
    fun arm() {
        if (_state.value !is SosState.Disarmed) return
        if (!dispatcher.isConfigured()) {
            _state.value = SosState.NotConfigured
            return
        }
        countdownJob = scope.launch {
            for (remaining in countdownSeconds downTo 1) {
                _state.value = SosState.Counting(remaining)
                delay(1_000L)
            }
            _state.value = if (dispatcher.dispatch()) SosState.Sent else SosState.Failed
        }
    }

    /** The cancel slider completed. */
    fun cancel() {
        if (_state.value !is SosState.Counting) return
        countdownJob?.cancel()
        countdownJob = null
        _state.value = SosState.Cancelled
    }

    /** Back to Trigger — ready for the next incident. */
    fun disarm() {
        countdownJob?.cancel()
        countdownJob = null
        _state.value = SosState.Disarmed
    }

    companion object {
        const val DEFAULT_COUNTDOWN_SECONDS = 10
    }
}
