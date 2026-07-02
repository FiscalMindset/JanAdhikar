package com.janadhikar

import android.app.Application
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

    val edgeStack: EdgeStack? get() = _edgeStackFlow.value

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            _edgeStackFlow.value = EdgeStack.create(this@JanadhikarApp)
        }
    }
}
