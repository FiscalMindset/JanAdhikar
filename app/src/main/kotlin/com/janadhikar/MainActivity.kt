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
import com.janadhikar.engine.CaptureState
import com.janadhikar.engine.IncidentPipelineService
import com.janadhikar.engine.EdgeStack
import com.janadhikar.ui.JanadhikarRoot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Single-activity host. Owns the pieces that need an Activity: the runtime mic
 * permission, the hardware volume-key trigger, and the foreground microphone
 * service tied to voice capture.
 */
class MainActivity : ComponentActivity() {

    private val app get() = application as JanadhikarApp

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startVoice()
        // Denied: stay in chat — typing needs no permission.
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Stop the mic foreground service the moment recording ends. It is
        // STARTED imperatively in startVoice() — before the mic opens — so on
        // Android 14+ it is already running when AudioRecord touches the mic.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.edgeStackFlow
                    .filterNotNull()
                    .flatMapLatest { it.engine.capture }
                    .collectLatest { capture ->
                        if (capture !is CaptureState.Recording) {
                            stopService(Intent(this@MainActivity, IncidentPipelineService::class.java))
                        }
                    }
            }
        }

        setContent {
            JanadhikarRoot(
                edgeStackFlow = app.edgeStackFlow,
                warmupError = app.warmupError,
                warmupStage = app.warmupStage,
                onVoiceRequested = ::requestVoice,
            )
        }
    }

    /** Volume-Down starts/stops voice capture (discreet, no-look). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            val engine = app.edgeStack?.engine
            if (engine != null) {
                if (engine.isRecording) engine.stopVoice() else requestVoice()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun requestVoice() {
        // If the voice model is still downloading (fresh install), tell the user
        // with the % instead of silently doing nothing.
        val stack = app.edgeStack
        val dl = stack?.voiceProgress?.value
        if (stack != null && dl != null) {
            android.widget.Toast.makeText(
                this, "Downloading voice model… $dl% — try the mic again shortly.",
                android.widget.Toast.LENGTH_LONG,
            ).show()
            return
        }
        if (stack != null && stack.status.value.voice == EdgeStack.ModelStatus.UNAVAILABLE) {
            android.widget.Toast.makeText(
                this, "Voice needs internet once to download. Connect and reopen. You can type meanwhile.",
                android.widget.Toast.LENGTH_LONG,
            ).show()
            return
        }
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) startVoice() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startVoice() {
        runCatching {
            startForegroundService(Intent(this, IncidentPipelineService::class.java))
        }
        app.edgeStack?.engine?.startVoice()
    }
}
