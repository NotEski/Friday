package me.dgol.friday.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
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

    override fun onCreate() {
        super.onCreate()
        // Ensure the system gives us a visible UI surface.
        setUiEnabled(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionScope.cancel() // avoid leaks
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
                        modelMissing = modelMissingMsg
                    )
                }
            }
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        // No auto-start; user taps Start to begin.
    }

    override fun onHide() {
        super.onHide()
        stopListening()
    }

    // -------- STT handling (local to the session) --------

    private var currentTranscript by mutableStateOf("")
    private var modelMissingMsg by mutableStateOf<String?>(null)
    private var isPreparing by mutableStateOf(false)

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(appCtx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun startListening() {
        if (engine != null || isPreparing) return
        if (!hasMicPermission()) {
            currentTranscript = "Microphone permission required. Open the Friday app to grant access."
            return
        }

        val lang = ModelPrefs.getSelectedLang(appCtx, ModelManager.defaultLang())
        isPreparing = true
        currentTranscript = ""

        sessionScope.launch {
            val modelDir: File? = try {
                // suspend lookup (may touch disk)
                ModelLocator.resolveVoskModelDir(appCtx, lang)
            } catch (t: Throwable) {
                null
            }

            isPreparing = false

            if (modelDir == null || !modelDir.exists()) {
                modelMissingMsg = "Model not set up for '$lang'. Open Friday → Models to download/select one."
                return@launch
            }

            modelMissingMsg = null

            val mySeq = ++sessionSeq
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
                        currentTranscript = buildTranscript(currentTranscript, "⚠️ ${t.message ?: "Unknown error"}")
                    }
                }
            ).also { it.start() }
        }
    }

    private fun stopListening() {
        val e = engine ?: return
        try { e.stop() } catch (_: Throwable) {}
        try { e.release() } catch (_: Throwable) {}
        engine = null
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
    modelMissing: String?
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
