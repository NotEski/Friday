package me.dgol.friday.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import me.dgol.friday.model.ModelLocator
import me.dgol.friday.prefs.ModelPrefs
import me.dgol.friday.shared.stt.ModelManager
import me.dgol.friday.stt.VoskEngine
import java.io.File

data class FridayUiState(
    val isListening: Boolean = false,
    val isThinking: Boolean = false,
    val transcript: String = "",
    val showPermissionRationale: Boolean = false,
    val errorMessage: String? = null,
    // Model selection
    val selectedLang: String = "en-us",
    val models: List<ModelItem> = emptyList(),
    val busyLang: String? = null // shows progress on per-model actions
)

data class ModelItem(
    val lang: String,
    val label: String,
    val approxSizeMb: Int,
    val installed: Boolean
)

class FridayViewModel : ViewModel() {

    private val _ui = MutableStateFlow(FridayUiState())
    val ui: StateFlow<FridayUiState> = _ui

    private var engine: VoskEngine? = null

    // --- Init & model list ---

    fun init(context: Context) {
        if (_ui.value.models.isNotEmpty()) return // already initialized
        val fallback = ModelManager.defaultLang()
        val saved = ModelPrefs.getSelectedLang(context, fallback)
        _ui.update { it.copy(selectedLang = saved) }
        refreshModels(context)
    }

    fun refreshModels(context: Context) {
        val infos = ModelManager.availableModels()
        val items = infos.map { info ->
            ModelItem(
                lang = info.lang,
                label = info.label,
                approxSizeMb = info.approxSizeMb,
                installed = ModelManager.isModelReady(context, info.lang)
            )
        }
        _ui.update { it.copy(models = items) }
    }

    fun selectModel(context: Context, lang: String) {
        ModelPrefs.setSelectedLang(context, lang)
        _ui.update { it.copy(selectedLang = lang) }
        // We don't auto-download on selection; user can tap Download as needed.
    }

    fun downloadModel(context: Context, lang: String) {
        if (_ui.value.busyLang != null) return
        _ui.update { it.copy(busyLang = lang) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { ModelManager.ensureModelFile(context, lang) }
            }.onFailure { t ->
                _ui.update {
                    it.copy(
                        busyLang = null,
                        errorMessage = t.message ?: "Failed to download model"
                    )
                }
            }.onSuccess {
                refreshModels(context)
                _ui.update { it.copy(busyLang = null) }
            }
        }
    }

    fun deleteModel(context: Context, lang: String) {
        if (_ui.value.busyLang != null) return
        _ui.update { it.copy(busyLang = lang) }
        viewModelScope.launch(Dispatchers.IO) {
            ModelManager.clearModel(context, lang)
            withContext(Dispatchers.Main) {
                refreshModels(context)
                _ui.update { it.copy(busyLang = null) }
            }
        }
    }

    // --- STT control ---

    fun startListening(appContext: Context) {
        if (_ui.value.isListening) return

        val lang = _ui.value.selectedLang
        viewModelScope.launch {
            val modelDir = withContext(Dispatchers.IO) {
                ModelLocator.resolveVoskModelDir(appContext, lang)
            }

            if (modelDir == null || !isValidVoskModel(modelDir)) {
                showError("Vosk model not found for '$lang'. Open Models and download it.")
                return@launch
            }

            engine = VoskEngine(
                appContext = appContext,
                modelDir = modelDir,
                onPartial = { partial ->
                    _ui.update { it.copy(transcript = mergeLines(it.transcript, "• $partial")) }
                },
                onFinal = { final ->
                    _ui.update { it.copy(transcript = mergeLines(it.transcript, final)) }
                },
                onError = { t ->
                    _ui.update {
                        it.copy(isListening = false, isThinking = false,
                            errorMessage = t.message ?: "Unknown error")
                    }
                }
            ).also { it.start() }

            _ui.update { it.copy(isListening = true, isThinking = false) }
        }
    }

    fun stopListening() {
        engine?.stop()
        engine = null
        _ui.update { it.copy(isListening = false, isThinking = false) }
    }

    fun appendTranscript(line: String) {
        _ui.update { it.copy(transcript = mergeLines(it.transcript, line)) }
    }

    fun showError(message: String) {
        _ui.update { it.copy(errorMessage = message) }
    }

    fun dismissError() {
        _ui.update { it.copy(errorMessage = null) }
    }

    fun onPermissionDenied() {
        _ui.update { it.copy(showPermissionRationale = true) }
    }

    fun dismissPermissionRationale() {
        _ui.update { it.copy(showPermissionRationale = false) }
    }

    override fun onCleared() {
        super.onCleared()
        try { engine?.release() } catch (_: Throwable) {}
        engine = null
    }

    // --- helpers ---

    private fun mergeLines(existing: String, newLine: String): String =
        if (existing.isBlank()) newLine else "$existing\n$newLine"

    private fun isValidVoskModel(dir: File): Boolean {
        return dir.exists() && dir.isDirectory && (dir.list()?.isNotEmpty() == true)
    }
}
