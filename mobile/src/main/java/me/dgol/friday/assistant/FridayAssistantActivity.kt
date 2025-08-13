package me.dgol.friday.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.*
import me.dgol.friday.model.ModelLocator
import me.dgol.friday.prefs.ModelPrefs
import me.dgol.friday.shared.stt.ModelManager
import me.dgol.friday.stt.VoskEngine
import java.io.File

class FridayAssistantActivity : ComponentActivity() {

    private var engine: VoskEngine? = null
    private var engineSeq = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Transparent activity so only the bottom card shows; background stays visible
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MaterialTheme {
                var liveText by remember { mutableStateOf("") }      // <- single line text
                var preparing by remember { mutableStateOf(false) }
                var modelMissing by remember { mutableStateOf<String?>(null) }
                var errorText by remember { mutableStateOf<String?>(null) }
                var listening by remember { mutableStateOf(false) }

                // Re-check permission at render time (keeps it accurate)
                val hasMicPermission by derivedStateOf {
                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                }

                val reqMic = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (!granted) errorText = "Microphone permission is required."
                }

                fun start() {
                    if (engine != null || preparing) return
                    errorText = null

                    if (!hasMicPermission) {
                        reqMic.launch(Manifest.permission.RECORD_AUDIO)
                        return
                    }

                    val lang = ModelPrefs.getSelectedLang(this, ModelManager.defaultLang())
                    preparing = true
                    liveText = ""

                    scope.launch {
                        val modelDir: File? = try {
                            withContext(Dispatchers.IO) {
                                ModelLocator.resolveVoskModelDir(this@FridayAssistantActivity, lang)
                            }
                        } catch (_: Throwable) { null }

                        preparing = false

                        if (modelDir == null || !modelDir.exists()) {
                            modelMissing = "Model not set up for '$lang'. Open Friday → Models to download/select one."
                            listening = false
                            return@launch
                        }
                        modelMissing = null

                        val mySeq = ++engineSeq
                        engine = VoskEngine(
                            appContext = applicationContext,
                            modelDir = modelDir,
                            onPartial = { partial ->
                                if (engineSeq == mySeq) liveText = partial // <- replace instead of append
                            },
                            onFinal = { final ->
                                if (engineSeq == mySeq) liveText = final   // <- replace instead of append
                            },
                            onError = { t ->
                                if (engineSeq == mySeq) errorText = t.message ?: "Unknown error"
                            }
                        ).also {
                            it.start()
                            listening = true
                        }
                    }
                }

                fun stop() {
                    val e = engine ?: return.also { listening = false }
                    try { e.stop() } catch (_: Throwable) {}
                    try { e.release() } catch (_: Throwable) {}
                    engine = null
                    listening = false
                    engineSeq++
                }

                AssistantBottomCard(
                    text = liveText,
                    preparing = preparing,
                    listening = listening,
                    modelMissing = modelMissing,
                    errorText = errorText,
                    onStart = ::start,
                    onStop = ::stop,
                    onClose = { stop(); finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { engine?.stop() } catch (_: Throwable) {}
        try { engine?.release() } catch (_: Throwable) {}
        engine = null
        scope.cancel()
    }
}

@Composable
private fun AssistantBottomCard(
    text: String,
    preparing: Boolean,
    listening: Boolean,
    modelMissing: String?,
    errorText: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit
) {
    Box(Modifier.fillMaxSize().padding(16.dp)) {
        Card(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Friday", style = MaterialTheme.typography.titleLarge)

                when {
                    preparing -> Text("Preparing model…")
                    modelMissing != null -> Text(modelMissing)
                    else -> {
                        if (!errorText.isNullOrBlank()) Text("⚠️ $errorText")
                        val display = if (text.isBlank()) {
                            if (listening) "Listening…" else "Tap start to begin."
                        } else {
                            text
                        }
                        // Single updating line (ellipsizes if long)
                        Text(
                            display,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (listening) {
                        Button(onClick = onStop, modifier = Modifier.weight(1f)) { Text("⏹ Stop") }
                    } else {
                        Button(onClick = onStart, modifier = Modifier.weight(1f)) { Text("🎙 Start voice recognition") }
                    }
                    Button(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Close") }
                }
            }
        }
    }
}
