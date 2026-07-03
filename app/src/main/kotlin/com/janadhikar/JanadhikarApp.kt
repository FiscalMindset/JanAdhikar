package com.janadhikar

import android.app.Application
import android.util.Log
import com.janadhikar.engine.EdgeStack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Application entry point. Eagerly warms the whole edge stack (knowledge DB,
 * whisper context, embedder, Gemma session) at process start so the
 * Trigger → Active transition stays under the 400 ms budget.
 */
class JanadhikarApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _edgeStackFlow = MutableStateFlow<EdgeStack?>(null)

    /** Null until warm-up completes; the Trigger screen observes readiness. */
    val edgeStackFlow: StateFlow<EdgeStack?> = _edgeStackFlow.asStateFlow()

    private val _warmupError = MutableStateFlow<String?>(null)

    /** Non-null when the edge stack could not start (e.g. model files missing). */
    val warmupError: StateFlow<String?> = _warmupError.asStateFlow()

    private val _warmupStage = MutableStateFlow("Starting…")

    /** Live warm-up stage text, shown so the load is never a vague spinner. */
    val warmupStage: StateFlow<String> = _warmupStage.asStateFlow()

    val edgeStack: EdgeStack? get() = _edgeStackFlow.value

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            try {
                _edgeStackFlow.value = withTimeout(WARMUP_TIMEOUT_MS) {
                    EdgeStack.create(this@JanadhikarApp) { stage -> _warmupStage.value = stage }
                }
            } catch (t: TimeoutCancellationException) {
                _warmupError.value = "Startup timed out. The search model may be missing — " +
                    "run ./scripts/push_models.sh, then reopen."
                Log.e(TAG, "Edge stack warm-up timed out", t)
            } catch (t: Throwable) {
                // A broken/incomplete install must present as "engine won't
                // start", never as a crash loop or an endless spinner.
                Log.e(TAG, "Edge stack failed to start", t)
                _warmupError.value = t.message ?: t.javaClass.simpleName
            }
        }
    }

    companion object {
        private const val TAG = "JanadhikarApp"

        /** Warm-up should take seconds; past this something is wrong (missing model). */
        private const val WARMUP_TIMEOUT_MS = 90_000L
    }
}
