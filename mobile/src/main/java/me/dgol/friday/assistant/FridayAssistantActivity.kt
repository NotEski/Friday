package me.dgol.friday.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.*
import me.dgol.friday.model.ModelLocator
import me.dgol.friday.prefs.ModelPrefs
import me.dgol.friday.shared.stt.ModelManager
import me.dgol.friday.stt.VoskEngine
import java.io.File

private enum class Role { User, Assistant }
private data class ChatItem(val role: Role, val text: String, val id: Long = System.nanoTime())

class FridayAssistantActivity : ComponentActivity() {

    private var engine: VoskEngine? = null
    private var engineSeq = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Transparent activity so only the bottom card is visible
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MaterialTheme {
                // ---- UI state ----
                val chat = remember { mutableStateListOf<ChatItem>() }
                var input by remember { mutableStateOf("") }
                var preparing by remember { mutableStateOf(false) }
                var modelMissing by remember { mutableStateOf<String?>(null) }
                var errorText by remember { mutableStateOf<String?>(null) }
                var listening by remember { mutableStateOf(false) }

                // Mic permission (live)
                val hasMicPermission by derivedStateOf {
                    ContextCompat.checkSelfPermission(
                        this, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                }

                // Permission launcher
                val reqMic = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted: Boolean ->
                    if (!granted) errorText = "Microphone permission is required."
                }

                // --- sending text (keyboard) ---
                fun processUserMessage(text: String) {
                    val trimmed = text.trim()
                    if (trimmed.isEmpty()) return
                    chat += ChatItem(Role.User, trimmed)
                    input = ""
                    // TODO hook your pipeline; echo for now
                    scope.launch {
                        val reply = "You said: $trimmed"
                        chat += ChatItem(Role.Assistant, reply)
                    }
                }

                fun stopVoice() {
                    val e = engine ?: return.also { listening = false }
                    try { e.stop() } catch (_: Throwable) {}
                    try { e.release() } catch (_: Throwable) {}
                    engine = null
                    listening = false
                    engineSeq++
                }

                fun startVoice() {
                    if (engine != null || preparing) return
                    errorText = null

                    if (!hasMicPermission) {
                        reqMic.launch(Manifest.permission.RECORD_AUDIO)
                        return
                    }

                    val lang = ModelPrefs.getSelectedLang(this, ModelManager.defaultLang())
                    preparing = true

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
                                if (engineSeq == mySeq) runOnUiThread { input = partial }
                            },
                            onFinal = { final ->
                                if (engineSeq == mySeq) runOnUiThread {
                                    input = final
                                    stopVoice()
                                    processUserMessage(final)
                                }
                            },
                            onError = { t ->
                                if (engineSeq == mySeq) runOnUiThread {
                                    errorText = t.message ?: "Unknown error"
                                    stopVoice()
                                }
                            }
                        ).also {
                            it.start()
                            listening = true
                        }
                    }
                }

                AssistantBottomCard(
                    chat = chat,
                    input = input,
                    onInputChange = { s ->
                        // No KeyboardOptions: treat a newline as "Send"
                        val nl = s.indexOf('\n')
                        if (nl >= 0) {
                            val msg = s.substring(0, nl)
                            input = msg
                            processUserMessage(msg)
                        } else {
                            input = s
                        }
                    },
                    preparing = preparing,
                    listening = listening,
                    modelMissing = modelMissing,
                    errorText = errorText,
                    onStartVoice = ::startVoice,
                    onStopVoice = ::stopVoice,
                    onSend = { processUserMessage(input) },
                    onClose = { stopVoice(); finish() }
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
    chat: List<ChatItem>,
    input: String,
    onInputChange: (String) -> Unit,
    preparing: Boolean,
    listening: Boolean,
    modelMissing: String?,
    errorText: String?,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onSend: () -> Unit,
    onClose: () -> Unit
) {
    Box(Modifier.fillMaxSize().padding(16.dp)) {
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                Modifier
                    .padding(16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Friday", style = MaterialTheme.typography.titleLarge)

                // Status
                when {
                    preparing -> Text("Preparing model…")
                    modelMissing != null -> Text(modelMissing)
                    !errorText.isNullOrBlank() -> Text("⚠️ $errorText")
                }

                // Chat history
                ChatList(
                    items = chat,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 360.dp)
                )

                // Composer: text + Send + mic toggle (no KeyboardOptions needed)
                MessageComposer(
                    value = input,
                    onValueChange = onInputChange,
                    listening = listening,
                    onMicToggle = { if (listening) onStopVoice() else onStartVoice() },
                    onSend = onSend
                )

                Row(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier.weight(1f)
                    ) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun ChatList(items: List<ChatItem>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.lastIndex)
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.id }) { msg ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (msg.role == Role.User) Arrangement.End else Arrangement.Start
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (msg.role == Role.User) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 1.dp
                ) {
                    Text(
                        text = msg.text,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = if (msg.role == Role.User) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageComposer(
    value: String,
    onValueChange: (String) -> Unit,
    listening: Boolean,
    onMicToggle: () -> Unit,
    onSend: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange, // newline sends in caller
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("Type a message…") }
        )
        Button(onClick = onSend, enabled = value.isNotBlank()) { Text("Send") }
        OutlinedButton(onClick = onMicToggle) { Text(if (listening) "⏹" else "🎙") }
    }
}
