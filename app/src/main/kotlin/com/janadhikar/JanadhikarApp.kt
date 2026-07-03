package com.janadhikar

import android.app.Application
import android.util.Log
import com.janadhikar.engine.EdgeStack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    /** Non-null when the edge stack could not start (e.g. model assets missing). */
    val warmupError: StateFlow<String?> = _warmupError.asStateFlow()

    val edgeStack: EdgeStack? get() = _edgeStackFlow.value

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            try {
                _edgeStackFlow.value = EdgeStack.create(this@JanadhikarApp)
            } catch (t: Throwable) {
                // A broken/incomplete install must present as "engine won't
                // start", never as a crash loop.
                Log.e(TAG, "Edge stack failed to start", t)
                _warmupError.value = t.message ?: t.javaClass.simpleName
            }
        }
    }

    companion object {
        private const val TAG = "JanadhikarApp"
    }
}
