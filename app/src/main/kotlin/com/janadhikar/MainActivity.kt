package com.janadhikar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.janadhikar.engine.IncidentPipelineService
import com.janadhikar.engine.IncidentState
import com.janadhikar.sos.SmsDispatcher
import com.janadhikar.sos.SosController
import com.janadhikar.ui.JanadhikarRoot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Single-activity host. Owns the pieces that need an Activity: the runtime
 * mic permission, the hardware volume-key trigger, and starting/stopping the
 * foreground service in lockstep with the engine state.
 */
class MainActivity : ComponentActivity() {

    private val app get() = application as JanadhikarApp

    private lateinit var sos: SosController

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startVoice()
        // Denied: stay on Trigger — the typed-text path needs no permission.
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sos = SosController(
            scope = lifecycleScope,
            dispatcher = SmsDispatcher(applicationContext),
        )

        // Stop the mic foreground service the moment a voice session ends
        // (resolution, idle, or failure). The service is STARTED imperatively
        // in startVoice() — before the mic is opened — so on Android 14+ it is
        // already running when AudioRecord touches the microphone.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.edgeStackFlow
                    .filterNotNull()
                    .flatMapLatest { it.engine.state }
                    .collectLatest { state ->
                        val stillCapturing = state is IncidentState.Active && state.isVoice
                        if (!stillCapturing) {
                            stopService(Intent(this@MainActivity, IncidentPipelineService::class.java))
                        }
                    }
            }
        }

        setContent {
            JanadhikarRoot(
                edgeStackFlow = app.edgeStackFlow,
                warmupError = app.warmupError,
                sos = sos,
                onVoiceRequested = ::requestVoice,
            )
        }
    }

    /**
     * Hardware trigger (discreet, no-look): Volume-Down starts capture from
     * Trigger, and stops-and-resolves from Active. Consumed only when handled,
     * so normal volume behaviour survives everywhere else.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            when (app.edgeStack?.engine?.state?.value) {
                is IncidentState.Idle -> {
                    requestVoice()
                    return true
                }
                is IncidentState.Active -> {
                    app.edgeStack?.engine?.stopAndResolve()
                    return true
                }
                else -> Unit
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun requestVoice() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) startVoice() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startVoice() {
        // Start the foreground microphone service FIRST so it is running before
        // AudioRecord opens the mic (Android 14+ requires this ordering). Never
        // let a service-start failure crash the app.
        runCatching {
            startForegroundService(Intent(this, IncidentPipelineService::class.java))
        }
        app.edgeStack?.engine?.startVoiceCapture()
    }
}
