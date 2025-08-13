package me.dgol.friday.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import me.dgol.friday.model.ModelLocator
import me.dgol.friday.prefs.ModelPrefs
import me.dgol.friday.shared.stt.ModelManager
import me.dgol.friday.stt.VoskEngine
import java.io.File

private const val TAG = "FridayAssist"

/**
 * Minimal assistant overlay: Start/Stop + live transcript.
 * Appears when the user invokes Friday via the system assistant gesture.
 */
class FridayVoiceInteractionSession(
    private val appCtx: android.content.Context
) : VoiceInteractionSession(appCtx) {

    private var engine: VoskEngine? = null
    private var sessionSeq = 0

    // Coroutine scope that lives with this session window
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // UI state
    private var currentTranscript by mutableStateOf("")
    private var modelMissingMsg by mutableStateOf<String?>(null)
    private var isPreparing by mutableStateOf(false)
    private var lastError by mutableStateOf<String?>(null)

    override fun onCreate() {
        super.onCreate()
        setUiEnabled(true)
        Log.d(TAG, "Session created")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Session destroyed")
        sessionScope.cancel()
        safeCloseEngine()
    }

    override fun onCreateContentView(): View {
        return ComposeView(appCtx).apply {
            setContent {
                MaterialTheme {
                    OverlayUi(
                        isMicGranted = hasMicPermission(),
                        isListening = engine != null,
                        isPreparing = isPreparing,
                        transcriptProvider = { currentTranscript },
                        onStart = { startListening() },
                        onStop = { stopListening() },
                        onDismiss = { hide() },
                        modelMissing = modelMissingMsg,
                        errorText = lastError
                    )
                }
            }
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.d(TAG, "onShow flags=$showFlags args=$args")
        // user will tap Start
    }

    override fun onHide() {
        super.onHide()
        Log.d(TAG, "onHide")
        stopListening()
    }

    // -------- STT handling (local to the session) --------

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(appCtx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun startListening() {
        if (engine != null || isPreparing) {
            Log.d(TAG, "startListening skipped (engine=${engine != null}, preparing=$isPreparing)")
            return
        }
        lastError = null
        if (!hasMicPermission()) {
            currentTranscript = "Microphone permission required. Open the Friday app to grant access."
            Log.w(TAG, "Mic permission missing")
            return
        }

        val lang = ModelPrefs.getSelectedLang(appCtx, ModelManager.defaultLang())
        isPreparing = true
        currentTranscript = ""
        Log.d(TAG, "Resolving model for lang=$lang ...")

        sessionScope.launch {
            val modelDir: File? = try {
                withContext(Dispatchers.IO) {
                    ModelLocator.resolveVoskModelDir(appCtx, lang)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "resolveVoskModelDir failed", t)
                null
            }

            isPreparing = false

            if (modelDir == null || !modelDir.exists()) {
                modelMissingMsg = "Model not set up for '$lang'. Open Friday → Models to download/select one."
                Log.w(TAG, "Model missing for $lang")
                return@launch
            }

            modelMissingMsg = null
            val mySeq = ++sessionSeq

            Log.d(TAG, "Starting VoskEngine with modelDir=${modelDir.absolutePath}")
            val started = runCatching {
                engine = VoskEngine(
                    appContext = appCtx,
                    modelDir = modelDir,
                    onPartial = { partial ->
                        if (sessionSeq == mySeq) {
                            currentTranscript = buildTranscript(currentTranscript, "• $partial")
                        }
                    },
                    onFinal = { final ->
                        if (sessionSeq == mySeq) {
                            currentTranscript = buildTranscript(currentTranscript, final)
                        }
                    },
                    onError = { t ->
                        if (sessionSeq == mySeq) {
                            val msg = t.message ?: "Unknown error"
                            lastError = msg
                            currentTranscript = buildTranscript(currentTranscript, "⚠️ $msg")
                            Log.e(TAG, "Engine error: $msg", t)
                        }
                    }
                ).also { it.start() }
                true
            }.onFailure {
                lastError = it.message ?: it::class.java.simpleName
                Log.e(TAG, "Engine start failed", it)
            }.getOrDefault(false)

            if (!started) {
                // make sure engine is null if start failed
                safeCloseEngine()
            }
        }
    }

    private fun stopListening() {
        Log.d(TAG, "stopListening()")
        safeCloseEngine()
    }

    private fun safeCloseEngine() {
        val e = engine ?: return
        engine = null
        // Bump sequence so late callbacks are ignored
        sessionSeq++
        runCatching { e.stop() }.onFailure { Log.w(TAG, "e.stop() failed", it) }
        runCatching { e.release() }.onFailure { Log.w(TAG, "e.release() failed", it) }
    }

    private fun buildTranscript(existing: String, newLine: String): String =
        if (existing.isBlank()) newLine else "$existing\n$newLine"
}

@Composable
private fun OverlayUi(
    isMicGranted: Boolean,
    isListening: Boolean,
    isPreparing: Boolean,
    transcriptProvider: () -> String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
    modelMissing: String?,
    errorText: String?
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Friday", style = MaterialTheme.typography.titleLarge)

                when {
                    !isMicGranted -> {
                        Text("Microphone permission required. Open the Friday app to grant it in Settings.")
                    }
                    modelMissing != null -> {
                        Text(modelMissing)
                    }
                    isPreparing -> {
                        Text("Preparing model…")
                    }
                    else -> {
                        val transcript = transcriptProvider()
                        if (!errorText.isNullOrBlank()) {
                            Text("⚠️ $errorText")
                        }
                        if (transcript.isBlank()) {
                            Text(if (isListening) "Listening…" else "Tap start to begin.")
                        } else {
                            Text(
                                transcript,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 80.dp, max = 220.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isListening) {
                        Button(
                            onClick = onStop,
                            modifier = Modifier.weight(1f)
                        ) { Text("⏹ Stop") }
                    } else {
                        Button(
                            onClick = onStart,
                            modifier = Modifier.weight(1f)
                        ) { Text("🎙 Start voice recognition") }
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) { Text("Close") }
                }
            }
        }
    }
}
